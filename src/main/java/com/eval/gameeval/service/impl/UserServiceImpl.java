package com.eval.gameeval.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eval.gameeval.mapper.ReviewerGroupMapper;
import com.eval.gameeval.mapper.ReviewerGroupMemberMapper;
import com.eval.gameeval.mapper.UserMapper;
import com.eval.gameeval.models.DTO.User.UserBatchQueryDTO;
import com.eval.gameeval.models.DTO.User.UserBatchDeleteDTO;
import com.eval.gameeval.models.DTO.User.UserBatchStatusDTO;
import com.eval.gameeval.models.DTO.User.UserCreateDTO;
import com.eval.gameeval.models.DTO.User.UserPasswordUpdateDTO;
import com.eval.gameeval.models.DTO.User.UserQueryDTO;
import com.eval.gameeval.models.DTO.User.UserUpdateDTO;
import com.eval.gameeval.models.VO.*;
import com.eval.gameeval.models.entity.ReviewerGroup;
import com.eval.gameeval.models.entity.ReviewerGroupMember;
import com.eval.gameeval.models.entity.User;
import com.eval.gameeval.service.IUserService;
import com.eval.gameeval.util.OverviewCacheUtil;
import com.eval.gameeval.util.RedisKeyUtil;
import com.eval.gameeval.security.AuthSessionStore;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class UserServiceImpl implements IUserService {
    @Resource
    private UserMapper userMapper;
    @Resource
    private ReviewerGroupMapper reviewerGroupMapper;
    @Resource
    private ReviewerGroupMemberMapper groupMemberMapper;
    @Resource
    private AuthSessionStore authSessionStore;
    @Resource
    private PasswordEncoder passwordEncoder;

    @Resource
    private OverviewCacheUtil overviewCacheUtil;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseVO<List<UserWithGroupVO>> createUsers(Long currentUserId, @Valid UserCreateDTO request) {
        try {
            // 1. 验证登录态
            if (currentUserId == null) {
                return ResponseVO.unauthorized("Token无效，请重新登录");
            }

            User currentUser = userMapper.selectById(currentUserId);
            if (currentUser == null) {
                return ResponseVO.unauthorized("用户不存在");
            }

            // 权限校验：只有super_admin和admin可以创建用户
            if (!"super_admin".equals(currentUser.getRole()) && !"admin".equals(currentUser.getRole())) {
                return ResponseVO.forbidden("权限不足，只有管理员可以创建用户");
            }

            // 2. 批量创建用户
            List<UserWithGroupVO> createdUsers = new ArrayList<>();
            List<String> errors = new ArrayList<>();

            for (UserCreateDTO.UserDTO userDTO : request.getUsers()) {
                try {
                    // 检查用户名是否已存在
                    Integer count = userMapper.countByUsername(userDTO.getUsername());
                    if (count != null && count > 0) {
                        errors.add("用户名 " + userDTO.getUsername() + " 已存在");
                        continue;
                    }

                    // 验证评审组（如果指定了）
                    List<ReviewerGroup> validGroups = new ArrayList<>();
                    if (userDTO.getReviewerGroupIds() != null && !userDTO.getReviewerGroupIds().isEmpty()) {
                        // 去重
                        List<Long> uniqueGroupIds = userDTO.getReviewerGroupIds().stream()
                                .distinct()
                                .collect(Collectors.toList());

                        // 验证每个评审组
                        for (Long groupId : uniqueGroupIds) {
                            ReviewerGroup group = reviewerGroupMapper.selectById(groupId);
                            if (group == null) {
                                errors.add("用户名 " + userDTO.getUsername() + " 创建失败: 评审组ID "
                                        + groupId + " 不存在");
                                continue;
                            }
                            if (!Boolean.TRUE.equals(group.getIsEnabled())) {
                                errors.add("用户名 " + userDTO.getUsername() + " 创建失败: 评审组 \""
                                        + group.getName() + "\" 已禁用");
                                continue;
                            }
                            validGroups.add(group);
                        }

                        // 如果有无效评审组，跳过该用户
                        if (validGroups.size() != uniqueGroupIds.size()) {
                            continue;
                        }
                    }

                    // 创建用户
                    User user = new User();
                    user.setUsername(userDTO.getUsername());
                    user.setPassword(passwordEncoder.encode(userDTO.getPassword()));
                    user.setName(userDTO.getName());
                    user.setRole(userDTO.getRole());
                    user.setIsEnabled(userDTO.getIsEnabled() != null ? userDTO.getIsEnabled() : true);
                    user.setCreateTime(LocalDateTime.now());
                    user.setUpdateTime(LocalDateTime.now());

                    userMapper.insertUser(user);

                    // 转换为VO
                    UserWithGroupVO userVO = new UserWithGroupVO();
                    BeanUtils.copyProperties(user, userVO);
                    // 如果指定了评审组，加入评审组
                    if (!validGroups.isEmpty()) {
                        try {
                            // 批量创建评审组成员关联
                            List<ReviewerGroupMember> members = new ArrayList<>();
                            for (ReviewerGroup group : validGroups) {
                                ReviewerGroupMember member = new ReviewerGroupMember();
                                member.setGroupId(group.getId());
                                member.setUserId(user.getId());
                                member.setCreateTime(LocalDateTime.now());
                                members.add(member);
                            }

                            if (!members.isEmpty()) {
                                groupMemberMapper.insertBatch(members);
                            }

                            // ✅ 构建评审组信息列表
                            List<ReviewerGroupInfoVO> groupVOs = validGroups.stream()
                                    .map(group -> {
                                        ReviewerGroupInfoVO vo = new ReviewerGroupInfoVO();
                                        vo.setId(group.getId());
                                        vo.setName(group.getName());
                                        return vo;
                                    })
                                    .collect(Collectors.toList());

                            userVO.setReviewerGroups(groupVOs);

                            log.info("用户加入多个评审组成功: userId={}, groupIds={}, groupNames={}",
                                    user.getId(),
                                    validGroups.stream().map(ReviewerGroup::getId).collect(Collectors.toList()),
                                    validGroups.stream().map(ReviewerGroup::getName).collect(Collectors.toList()));

                        } catch (Exception e) {
                            log.warn("用户加入评审组失败（用户已创建）: userId={}, error={}",
                                    user.getId(), e.getMessage());
                            errors.add("用户名 " + userDTO.getUsername() + " 创建成功，但加入评审组失败: " + e.getMessage());
                        }
                    } else {
                        // 未指定评审组或评审组列表为空
                        userVO.setReviewerGroups(new ArrayList<>());
                    }
                    createdUsers.add(userVO);

                    log.info("创建用户成功: username={}, role={}", user.getUsername(), user.getRole());

                } catch (Exception e) {
                    errors.add("用户名 " + userDTO.getUsername() + " 创建失败: " + e.getMessage());
                    log.error("创建用户异常: username={}", userDTO.getUsername(), e);
                }
            }

            // 3. 构建响应
            if (createdUsers.isEmpty()) {
                return ResponseVO.badRequest("所有用户创建失败: " + String.join("; ", errors));
            }

            if (!errors.isEmpty()) {
                overviewCacheUtil.clearUserOverviewCache();
                overviewCacheUtil.clearReviewerGroupOverviewCache();
                return ResponseVO.success(
                        "部分用户创建成功，部分失败: " + String.join("; ", errors),
                        createdUsers
                );
            }

            overviewCacheUtil.clearUserOverviewCache();
            overviewCacheUtil.clearReviewerGroupOverviewCache();

            return ResponseVO.success("创建成功", createdUsers);

        } catch (Exception e) {
            log.error("批量创建用户异常", e);
            return ResponseVO.error("创建失败: " + e.getMessage());
        }

    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseVO<Void> updateUser(Long currentUserId, Long userId, UserUpdateDTO request) {
        try {
            // 1. 验证登录态
            if (currentUserId == null) {
                return ResponseVO.unauthorized("Token无效，请重新登录");
            }

            User currentUser = userMapper.selectById(currentUserId);
            if (currentUser == null) {
                return ResponseVO.unauthorized("用户不存在");
            }

            // 2. 权限校验
            if (!"super_admin".equals(currentUser.getRole()) && !"admin".equals(currentUser.getRole())) {
                return ResponseVO.forbidden("权限不足，只有管理员可以编辑用户");
            }

            // 3. 查询目标用户（复用此对象避免重复查询）
            User targetUser = userMapper.selectById(userId);
            if (targetUser == null) {
                return ResponseVO.notFound("用户不存在");
            }

            // 4. 安全限制
            if ("admin".equals(currentUser.getRole()) && "super_admin".equals(targetUser.getRole())) {
                return ResponseVO.forbidden("无权修改超级管理员");
            }
            if (userId.equals(currentUserId) && request.getRole() != null) {
                return ResponseVO.badRequest("不能修改自己的角色");
            }

            // 5. 构建更新对象（未提供的字段使用原值，避免null覆盖）
            User updateUser = new User();
            updateUser.setId(userId);
            updateUser.setUpdateTime(LocalDateTime.now());

            // 名称：请求提供则用新值，否则保留原值
            if (request.getName() != null && !request.getName().trim().isEmpty()) {
                updateUser.setName(request.getName());
            } else {
                updateUser.setName(targetUser.getName());
            }

            boolean roleChanged = false;
            boolean statusChanged = false;
            boolean passwordChanged = false;

            // 角色：请求提供则验证并更新，否则保留原值
            if (request.getRole() != null) {
                if (!isValidRole(request.getRole())) {
                    return ResponseVO.badRequest("无效的角色");
                }
                // 安全限制：普通管理员不能提升为超级管理员
                if ("admin".equals(currentUser.getRole()) && "super_admin".equals(request.getRole())) {
                    return ResponseVO.forbidden("无权设置为超级管理员");
                }
                roleChanged = !request.getRole().equals(targetUser.getRole());
                updateUser.setRole(request.getRole());
            } else {
                updateUser.setRole(targetUser.getRole());
            }

            // 启用状态：请求提供则更新，否则保留原值
            if (request.getIsEnabled() != null) {
                statusChanged = !request.getIsEnabled().equals(targetUser.getIsEnabled());
                updateUser.setIsEnabled(request.getIsEnabled());
            } else {
                updateUser.setIsEnabled(targetUser.getIsEnabled());
            }

            // 密码：管理员可设置新密码，否则保留原值
            if (request.getNewPassword() != null && !request.getNewPassword().trim().isEmpty()) {
                passwordChanged = true;
                updateUser.setPassword(passwordEncoder.encode(request.getNewPassword()));
            } else {
                updateUser.setPassword(targetUser.getPassword());
            }

            // 6. 执行更新
            int rows = userMapper.updateById(updateUser);

            if (rows > 0) {
                if (statusChanged && Boolean.FALSE.equals(request.getIsEnabled())) {
                    log.info("用户被禁用: userId={}, operator={}", userId, currentUserId);
                }
                if (roleChanged || statusChanged || passwordChanged) {
                    revokeUserSessions(userId);
                }
                overviewCacheUtil.clearUserOverviewCache();
                log.info("编辑用户成功: userId={}, operator={}", userId, currentUserId);
                return ResponseVO.<Void>success("编辑成功",null);
            } else {
                return ResponseVO.error("编辑失败：用户可能已被删除");
            }

        } catch (Exception e) {
            log.error("编辑用户异常: userId={}", userId, e);
            return ResponseVO.error("编辑失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseVO<Void> updateSelfPassword(Long currentUserId, @Valid UserPasswordUpdateDTO request) {
        try {
            // 1. 验证登录态
            if (currentUserId == null) {
                return ResponseVO.unauthorized("Token无效，请重新登录");
            }

            User currentUser = userMapper.selectById(currentUserId);
            if (currentUser == null) {
                return ResponseVO.unauthorized("用户不存在");
            }

            // 2. 校验旧密码
            if (!passwordEncoder.matches(request.getOldPassword(), currentUser.getPassword())) {
                return ResponseVO.badRequest("旧密码不正确");
            }

            // 3. 更新密码
            String encodedPassword = passwordEncoder.encode(request.getNewPassword());
            int rows = userMapper.updatePasswordById(currentUserId, encodedPassword, LocalDateTime.now());

            if (rows > 0) {
                revokeUserSessions(currentUserId);
                log.info("用户修改密码成功: userId={}", currentUserId);
                return ResponseVO.<Void>success("修改成功", null);
            } else {
                return ResponseVO.error("修改失败：用户可能已被删除");
            }

        } catch (Exception e) {
            log.error("修改密码异常: userId={}", currentUserId, e);
            return ResponseVO.error("修改失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseVO<Void> deleteUser(Long currentUserId, Long userId) {
        try {
            // 1. 验证登录态
            if (currentUserId == null) {
                return ResponseVO.unauthorized("Token无效，请重新登录");
            }

            User currentUser = userMapper.selectById(currentUserId);
            if (currentUser == null) {
                return ResponseVO.unauthorized("用户不存在");
            }

            // 2. 权限校验
            if (!"super_admin".equals(currentUser.getRole()) && !"admin".equals(currentUser.getRole())) {
                return ResponseVO.forbidden("权限不足，只有管理员可以删除用户");
            }

            // 3. 查询目标用户
            User targetUser = userMapper.selectById(userId);
            if (targetUser == null) {
                return ResponseVO.notFound("用户不存在");
            }

            // 4. 安全限制
            if (userId.equals(currentUserId)) {
                return ResponseVO.badRequest("不能删除自己");
            }
            if ("admin".equals(currentUser.getRole()) && "super_admin".equals(targetUser.getRole())) {
                return ResponseVO.forbidden("无权删除超级管理员");
            }
            if ("admin".equals(currentUser.getRole()) && "admin".equals(targetUser.getRole())) {
                return ResponseVO.forbidden("无权删除其他管理员");
            }

            // 5. 执行逻辑删除
            int rows = userMapper.softDeleteById(userId, LocalDateTime.now());

            if (rows > 0) {
                log.info("逻辑删除用户成功: userId={}, operator={}", userId, currentUserId);
                revokeUserSessions(userId);
                overviewCacheUtil.clearUserOverviewCache();
                return ResponseVO.<Void>success("删除成功",null);
            } else {
                return ResponseVO.error("删除失败：用户可能已被删除");
            }

        } catch (Exception e) {
            log.error("删除用户异常: userId={}", userId, e);
            return ResponseVO.error("删除失败: " + e.getMessage());
        }
    }

    @Override
    public ResponseVO<UserBatchOperationResultVO> batchUpdateUserStatus(Long currentUserId, UserBatchStatusDTO request) {
        try {
            if (currentUserId == null) {
                return ResponseVO.unauthorized("Token无效，请重新登录");
            }

            User currentUser = userMapper.selectById(currentUserId);
            if (currentUser == null) {
                return ResponseVO.unauthorized("用户不存在");
            }

            if (!"super_admin".equals(currentUser.getRole()) && !"admin".equals(currentUser.getRole())) {
                return ResponseVO.forbidden("权限不足，只有管理员可以批量修改用户状态");
            }

            List<Long> userIds = request.getUserIds().stream()
                    .distinct()
                    .collect(Collectors.toList());
            List<Long> failedIds = new ArrayList<>();
            int successCount = 0;

            for (Long userId : userIds) {
                try {
                    User targetUser = userMapper.selectById(userId);
                    String failureReason = validateBatchStatusTarget(currentUser, targetUser);
                    if (failureReason != null) {
                        failedIds.add(userId);
                        log.warn("批量修改用户状态跳过: userId={}, operator={}, reason={}",
                                userId, currentUserId, failureReason);
                        continue;
                    }

                    if (request.getIsEnabled().equals(targetUser.getIsEnabled())) {
                        successCount++;
                        continue;
                    }

                    User updateUser = new User();
                    updateUser.setId(userId);
                    updateUser.setName(targetUser.getName());
                    updateUser.setRole(targetUser.getRole());
                    updateUser.setPassword(targetUser.getPassword());
                    updateUser.setIsEnabled(request.getIsEnabled());
                    updateUser.setUpdateTime(LocalDateTime.now());

                    int rows = userMapper.updateById(updateUser);
                    if (rows > 0) {
                        if (Boolean.FALSE.equals(request.getIsEnabled())) {
                            revokeUserSessions(userId);
                        }
                        successCount++;
                    } else {
                        failedIds.add(userId);
                    }
                } catch (Exception e) {
                    failedIds.add(userId);
                    log.error("批量修改用户状态异常: userId={}, operator={}", userId, currentUserId, e);
                }
            }

            UserBatchOperationResultVO result = new UserBatchOperationResultVO()
                    .setTotalCount(userIds.size())
                    .setSuccessCount(successCount)
                    .setFailCount(failedIds.size())
                    .setFailedIds(failedIds);

            String actionText = Boolean.TRUE.equals(request.getIsEnabled()) ? "启用" : "禁用";
            String message = failedIds.isEmpty()
                    ? "批量" + actionText + "完成"
                    : "批量" + actionText + "完成，部分用户操作失败";
                overviewCacheUtil.clearUserOverviewCache();
            return ResponseVO.success(message, result);
        } catch (Exception e) {
            log.error("批量修改用户状态异常", e);
            return ResponseVO.error("批量修改用户状态失败: " + e.getMessage());
        }
    }

    @Override
    public ResponseVO<UserBatchOperationResultVO> batchDeleteUsers(Long currentUserId, UserBatchDeleteDTO request) {
        try {
            if (currentUserId == null) {
                return ResponseVO.unauthorized("Token无效，请重新登录");
            }

            User currentUser = userMapper.selectById(currentUserId);
            if (currentUser == null) {
                return ResponseVO.unauthorized("用户不存在");
            }

            if (!"super_admin".equals(currentUser.getRole()) && !"admin".equals(currentUser.getRole())) {
                return ResponseVO.forbidden("权限不足，只有管理员可以批量删除用户");
            }

            List<Long> userIds = request.getUserIds().stream()
                    .distinct()
                    .collect(Collectors.toList());
            List<Long> failedIds = new ArrayList<>();
            int successCount = 0;

            for (Long userId : userIds) {
                try {
                    User targetUser = userMapper.selectById(userId);
                    String failureReason = validateBatchDeleteTarget(currentUserId, currentUser, targetUser);
                    if (failureReason != null) {
                        failedIds.add(userId);
                        log.warn("批量删除用户跳过: userId={}, operator={}, reason={}",
                                userId, currentUserId, failureReason);
                        continue;
                    }

                    if (Boolean.TRUE.equals(targetUser.getIsDeleted())) {
                        successCount++;
                        continue;
                    }

                    int rows = userMapper.softDeleteById(userId, LocalDateTime.now());
                    if (rows > 0) {
                        revokeUserSessions(userId);
                        successCount++;
                    } else {
                        failedIds.add(userId);
                    }
                } catch (Exception e) {
                    failedIds.add(userId);
                    log.error("批量删除用户异常: userId={}, operator={}", userId, currentUserId, e);
                }
            }

            UserBatchOperationResultVO result = new UserBatchOperationResultVO()
                    .setTotalCount(userIds.size())
                    .setSuccessCount(successCount)
                    .setFailCount(failedIds.size())
                    .setFailedIds(failedIds);

            String message = failedIds.isEmpty()
                    ? "批量删除完成"
                    : "批量删除完成，部分用户删除失败";
                overviewCacheUtil.clearUserOverviewCache();
            return ResponseVO.success(message, result);
        } catch (Exception e) {
            log.error("批量删除用户异常", e);
            return ResponseVO.error("批量删除用户失败: " + e.getMessage());
        }
    }

    @Override
    public ResponseVO<UserPageVO> getUserList(Long currentUserId, UserQueryDTO query) {
        try {
            // 1. 验证登录态
            if (currentUserId == null) {
                return ResponseVO.unauthorized("Token无效，请重新登录");
            }

            User currentUser = userMapper.selectById(currentUserId);
            if (currentUser == null) {
                return ResponseVO.unauthorized("用户不存在");
            }

            // 2. 权限校验：只有管理员可以查看用户列表
            if (!"super_admin".equals(currentUser.getRole()) && !"admin".equals(currentUser.getRole())) {
                return ResponseVO.forbidden("权限不足，只有管理员可以查看用户列表");
            }

            // 3. 处理分页参数
            int page = query.getPage() != null ? query.getPage() : 1;
            int size = query.getSize() != null ? query.getSize() : 10;

            // 计算偏移量（offset = (page - 1) * size）
            int offset = (page - 1) * size;
            int limit = size;

            // 4. 查询用户列表
            List<Map<String, Object>> userList = userMapper.selectPageWithGroups(
                    offset,
                    size,
                    query.getRole(),
                    query.getKeyWords(),
                    query.getIsEnabled()
            );

            // 5. 查询总记录数
            Long total = userMapper.countTotal(
                    query.getRole(),
                    query.getKeyWords(),
                    query.getIsEnabled()
            );

            // 6. 转换为VO列表
            List<UserPageVO.UserVO> userVOs = new ArrayList<>();
            for (Map<String, Object> userMap : userList) {
                UserPageVO.UserVO vo = new UserPageVO.UserVO();

                // 基本字段
                vo.setId(((Number) userMap.get("id")).longValue());
                vo.setUsername((String) userMap.get("username"));
                vo.setName((String) userMap.get("name"));
                vo.setRole((String) userMap.get("role"));
                vo.setIsEnabled(toBoolean(userMap.get("isEnabled")));
                vo.setCreateTime(toLocalDateTime(userMap.get("createTime")));

                String groupIdsStr = (String) userMap.get("reviewerGroupIds");
                if (groupIdsStr != null && !groupIdsStr.trim().isEmpty()) {
                    // 将逗号分隔的字符串转换为List<Long>
                    List<Long> groupIds = Arrays.stream(groupIdsStr.split(","))
                            .map(Long::parseLong)
                            .collect(Collectors.toList());
                    vo.setReviewerGroupIds(groupIds);
                } else {
                    // 用户不属于任何评审组
                    vo.setReviewerGroupIds(new ArrayList<>());
                }

                userVOs.add(vo);
            }

            // 7. 构建分页响应
            UserPageVO pageVO = new UserPageVO();
            pageVO.setList(userVOs);
            pageVO.setTotal(total);
            pageVO.setPage(page);
            pageVO.setSize(size);

            log.info("查询用户列表成功: operator={}, page={}, size={}, role={}, isEnabled={}",
                    currentUserId, page, size, query.getRole(), query.getIsEnabled());

            return ResponseVO.success("查询成功", pageVO);

        } catch (Exception e) {
            log.error("查询用户列表异常", e);
            return ResponseVO.error("查询失败: " + e.getMessage());
        }
    }

    @Override
    public ResponseVO<UserOverviewVO> getUserOverview(Long currentUserId) {
        try {
            if (currentUserId == null) {
                return ResponseVO.unauthorized("Token无效，请重新登录");
            }

            User currentUser = userMapper.selectById(currentUserId);
            if (currentUser == null) {
                return ResponseVO.unauthorized("用户不存在");
            }

            if (!"super_admin".equals(currentUser.getRole()) && !"admin".equals(currentUser.getRole())) {
                return ResponseVO.forbidden("权限不足，只有管理员可以查看用户概览");
            }

            Object cache = overviewCacheUtil.getUserOverviewCache();
            if (cache != null) {
                UserOverviewVO cachedOverview = (UserOverviewVO) cache;
                log.info("【缓存命中】查询用户概览: totalUsers={}", cachedOverview.getTotalUsers());
                return ResponseVO.success("查询成功", cachedOverview);
            }

            UserOverviewVO overviewVO = loadUserOverview();
            overviewCacheUtil.cacheUserOverview(overviewVO);

            return ResponseVO.success("查询成功", overviewVO);

        } catch (Exception e) {
            log.error("查询用户概览异常", e);
            return ResponseVO.error("查询失败: " + e.getMessage());
        }
    }

    public void warmupUserOverviewCache() {
        try {
            if (overviewCacheUtil.getUserOverviewCache() != null) {
                return;
            }

            UserOverviewVO overviewVO = loadUserOverview();
            overviewCacheUtil.cacheUserOverview(overviewVO);

            log.info("全局概览预热完成: key={}, totalUsers={}",
                    RedisKeyUtil.USER_OVERVIEW_KEY, overviewVO.getTotalUsers());
        } catch (Exception e) {
            log.error("全局概览预热异常: key={}", RedisKeyUtil.USER_OVERVIEW_KEY, e);
        }
    }

    private UserOverviewVO loadUserOverview() {
        Map<String, Object> overviewMap = userMapper.selectUserOverview();
        if (overviewMap == null) {
            overviewMap = Collections.emptyMap();
        }

        return new UserOverviewVO()
                .setTotalUsers(toLong(overviewMap.get("totalUsers")))
                .setAdminUsers(toLong(overviewMap.get("adminUsers")))
                .setScorerUsers(toLong(overviewMap.get("scorerUsers")))
                .setNormalUsers(toLong(overviewMap.get("normalUsers")));
    }

    @Override
    public ResponseVO<List<UserDetailVO>> batchQueryUsers(Long currentUserId, UserBatchQueryDTO request) {


        try {
            // 1. 验证登录态
            if (currentUserId == null) {
                return ResponseVO.unauthorized("Token无效");
            }

            // 2. 查询用户详细信息（批量）
            List<Long> userIds = request.getIds();

            // 3. 构建查询条件
            LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
            wrapper.in(User::getId, userIds);
            wrapper.eq(User::getIsDeleted, false);

            if (!request.getIncludeDisabled()) {
                wrapper.eq(User::getIsEnabled, true);
            }

            List<User> users = userMapper.selectList(wrapper);

            // 4. 转换为VO
            List<UserDetailVO> userVOs = users.stream()
                    .map(user -> {
                        UserDetailVO vo = new UserDetailVO();
                        BeanUtils.copyProperties(user, vo);
                        return vo;
                    })
                    .collect(Collectors.toList());

            log.info("批量查询用户成功: count={}, operator={}", userVOs.size(), currentUserId);

            return ResponseVO.success("查询成功", userVOs);

        } catch (Exception e) {
            log.error("批量查询用户异常", e);
            return ResponseVO.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 验证角色有效性
     */
    private boolean isValidRole(String role) {
        return "super_admin".equals(role) ||
                "admin".equals(role) ||
                "scorer".equals(role) ||
                "normal".equals(role);
    }

    private String validateBatchStatusTarget(User currentUser, User targetUser) {
        if (targetUser == null) {
            return "用户不存在";
        }
        if ("admin".equals(currentUser.getRole()) && "super_admin".equals(targetUser.getRole())) {
            return "无权修改超级管理员";
        }
        return null;
    }

    private String validateBatchDeleteTarget(Long currentUserId, User currentUser, User targetUser) {
        if (targetUser == null) {
            return "用户不存在";
        }
        if (targetUser.getId().equals(currentUserId)) {
            return "不能删除自己";
        }
        if ("super_admin".equals(targetUser.getRole())) {
            return "禁止删除超级管理员";
        }
        if ("admin".equals(currentUser.getRole()) && "admin".equals(targetUser.getRole())) {
            return "无权删除其他管理员";
        }
        return null;
    }

    /**
     * 兼容JDBC返回的时间类型（常见为Timestamp）
     */
    private LocalDateTime toLocalDateTime(Object timeValue) {
        if (timeValue == null) {
            return null;
        }
        if (timeValue instanceof LocalDateTime) {
            return (LocalDateTime) timeValue;
        }
        if (timeValue instanceof Timestamp) {
            return ((Timestamp) timeValue).toLocalDateTime();
        }
        if (timeValue instanceof Date) {
            return LocalDateTime.ofInstant(((Date) timeValue).toInstant(), ZoneId.systemDefault());
        }
        if (timeValue instanceof String) {
            String text = ((String) timeValue).trim();
            if (text.isEmpty()) {
                return null;
            }
            try {
                return LocalDateTime.parse(text);
            } catch (Exception ignore) {
                try {
                    return Timestamp.valueOf(text).toLocalDateTime();
                } catch (Exception e) {
                    log.warn("createTime字符串转换失败: {}", text, e);
                    return null;
                }
            }
        }

        log.warn("不支持的createTime类型: type={}, value={}",
                timeValue.getClass().getName(), timeValue);
        return null;
    }

    /**
     * 兼容JDBC返回的布尔类型（Boolean/Number/String）
     */
    private Boolean toBoolean(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        if (value instanceof String) {
            String text = ((String) value).trim();
            if ("1".equals(text)) {
                return true;
            }
            if ("0".equals(text)) {
                return false;
            }
            return Boolean.parseBoolean(text);
        }

        log.warn("不支持的isEnabled类型: type={}, value={}",
                value.getClass().getName(), value);
        return null;
    }

    private Long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            log.warn("统计字段转换失败: {}", value, e);
            return 0L;
        }
    }

    private void revokeUserSessions(Long userId) {
        if (userId == null) {
            return;
        }
        authSessionStore.bumpTokenVersion(userId);
        authSessionStore.clearUserSessions(userId);
    }
}
