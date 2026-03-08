package com.eval.gameeval.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eval.gameeval.mapper.ReviewerGroupMapper;
import com.eval.gameeval.mapper.ReviewerGroupMemberMapper;
import com.eval.gameeval.mapper.UserMapper;
import com.eval.gameeval.models.DTO.UserBatchQueryDTO;
import com.eval.gameeval.models.DTO.UserCreateDTO;
import com.eval.gameeval.models.DTO.UserQueryDTO;
import com.eval.gameeval.models.DTO.UserUpdateDTO;
import com.eval.gameeval.models.VO.*;
import com.eval.gameeval.models.entity.ReviewerGroup;
import com.eval.gameeval.models.entity.ReviewerGroupMember;
import com.eval.gameeval.models.entity.User;
import com.eval.gameeval.service.IUserService;
import com.eval.gameeval.util.RedisToken;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
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
    private RedisToken redisToken;
    @Resource
    private PasswordEncoder passwordEncoder;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseVO<List<UserWithGroupVO>> createUsers(String token, UserCreateDTO request) {
        try {
            // 1. 验证Token并获取当前用户
            Long currentUserId = redisToken.getUserIdByToken(token);
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
                    ReviewerGroup reviewerGroup = null;
                    if (userDTO.getReviewerGroupId() != null) {
                        reviewerGroup = reviewerGroupMapper.selectById(userDTO.getReviewerGroupId());
                        if (reviewerGroup == null) {
                            errors.add("用户名 " + userDTO.getUsername() + " 创建失败: 评审组ID "
                                    + userDTO.getReviewerGroupId() + " 不存在");
                            continue;
                        }
                        if (!Boolean.TRUE.equals(reviewerGroup.getIsEnabled())) {
                            errors.add("用户名 " + userDTO.getUsername() + " 创建失败: 评审组 \""
                                    + reviewerGroup.getName() + "\" 已禁用");
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
                    if (reviewerGroup != null) {
                        try {
                            // 创建评审组成员关联
                            ReviewerGroupMember member = new ReviewerGroupMember();
                            member.setGroupId(reviewerGroup.getId());
                            member.setUserId(user.getId());
                            member.setCreateTime(LocalDateTime.now());

                            groupMemberMapper.insert(member);

                            // 设置评审组信息到VO
                            userVO.setReviewerGroupId(reviewerGroup.getId());
                            userVO.setReviewerGroupName(reviewerGroup.getName());

                            log.info("用户加入评审组成功: userId={}, groupId={}, groupName={}",
                                    user.getId(), reviewerGroup.getId(), reviewerGroup.getName());

                        } catch (Exception e) {
                            log.warn("用户加入评审组失败（用户已创建）: userId={}, groupId={}, error={}",
                                    user.getId(), reviewerGroup.getId(), e.getMessage());
                            errors.add("用户名 " + userDTO.getUsername() + " 创建成功，但加入评审组 \""
                                    + reviewerGroup.getName() + "\" 失败: " + e.getMessage());
                        }
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
                return ResponseVO.success(
                        "部分用户创建成功，部分失败: " + String.join("; ", errors),
                        createdUsers
                );
            }

            return ResponseVO.success("创建成功", createdUsers);

        } catch (Exception e) {
            log.error("批量创建用户异常", e);
            return ResponseVO.error("创建失败: " + e.getMessage());
        }

    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseVO<Void> updateUser(String token, Long userId, UserUpdateDTO request) {
        try {
            // 1. 验证Token并获取当前用户
            Long currentUserId = redisToken.getUserIdByToken(token);
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

            // 角色：请求提供则验证并更新，否则保留原值
            if (request.getRole() != null) {
                if (!isValidRole(request.getRole())) {
                    return ResponseVO.badRequest("无效的角色");
                }
                // 安全限制：普通管理员不能提升为超级管理员
                if ("admin".equals(currentUser.getRole()) && "super_admin".equals(request.getRole())) {
                    return ResponseVO.forbidden("无权设置为超级管理员");
                }
                updateUser.setRole(request.getRole());
            } else {
                updateUser.setRole(targetUser.getRole());
            }

            // 启用状态：请求提供则更新，否则保留原值
            if (request.getIsEnabled() != null) {
                updateUser.setIsEnabled(request.getIsEnabled());
            } else {
                updateUser.setIsEnabled(targetUser.getIsEnabled());
            }

            // 6. 执行更新
            int rows = userMapper.updateById(updateUser);

            if (rows > 0) {
                // 7. 如果禁用用户，记录日志
                if (request.getIsEnabled() != null && !request.getIsEnabled()) {
                    log.info("用户被禁用: userId={}, operator={}", userId, currentUserId);
//                    redisToken.deleteToken(token);
                }
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
    public ResponseVO<Void> deleteUser(String token, Long userId) {
        try {
            // 1. 验证Token并获取当前用户
            Long currentUserId = redisToken.getUserIdByToken(token);
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
            int rows = userMapper.disableById(userId, LocalDateTime.now());

            if (rows > 0) {
                log.info("逻辑删除用户成功: userId={}, operator={}", userId, currentUserId);
                redisToken.deleteToken(token);
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
    public ResponseVO<UserPageVO> getUserList(String token, UserQueryDTO query) {
        try {
            // 1. 验证Token并获取当前用户
            Long currentUserId = redisToken.getUserIdByToken(token);
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
            List<User> users = userMapper.selectPage(
                    offset,
                    size,
                    query.getRole(),
                    query.getKeyWords()
            );

            // 5. 查询总记录数
            Long total = userMapper.countTotal(query.getRole(), query.getKeyWords());

            // 6. 转换为VO列表
            List<ReviewerGroupInfoVO> emptyList = new ArrayList<>(); // 空列表复用
            if (!users.isEmpty()) {
                // 提取用户ID列表
                List<Long> userIds = users.stream()
                        .map(User::getId)
                        .collect(Collectors.toList());

                // 查询评审组信息
                List<Map<String, Object>> groupMaps = userMapper.selectReviewerGroupsByUserIds(userIds);

                // 按用户ID分组：userId -> List<ReviewerGroupInfoVO>
                Map<Long, List<ReviewerGroupInfoVO>> userGroupsMap = new HashMap<>();
                for (Map<String, Object> groupMap : groupMaps) {
                    Long userId = ((Number) groupMap.get("userId")).longValue();
                    Long groupId = ((Number) groupMap.get("groupId")).longValue();
                    String groupName = (String) groupMap.get("groupName");

                    ReviewerGroupInfoVO groupInfo = new ReviewerGroupInfoVO();
                    groupInfo.setId(groupId);
                    groupInfo.setName(groupName);

                    userGroupsMap.computeIfAbsent(userId, k -> new ArrayList<>()).add(groupInfo);
                }

                // 将评审组信息设置到每个用户VO中
                for (User user : users) {
                    List<ReviewerGroupInfoVO> groups = userGroupsMap.get(user.getId());
                    if (groups == null) {
                        user.setReviewerGroups(emptyList); // 复用空列表节省内存
                    } else {
                        user.setReviewerGroups(groups);
                    }
                }
            }
            List<UserPageVO.UserVO> userVOs = users.stream()
                    .map(user -> {
                        UserPageVO.UserVO vo = new UserPageVO.UserVO();
                        BeanUtils.copyProperties(user, vo);
                        vo.setReviewerGroups(user.getReviewerGroups());
                        return vo;
                    })
                    .collect(Collectors.toList());

            // 7. 构建分页响应
            UserPageVO pageVO = new UserPageVO();
            pageVO.setList(userVOs);
            pageVO.setTotal(total);
            pageVO.setPage(page);
            pageVO.setSize(size);

            log.info("查询用户列表成功: operator={}, page={}, size={}, role={}",
                    currentUserId, page, size, query.getRole());

            return ResponseVO.success("查询成功", pageVO);

        } catch (Exception e) {
            log.error("查询用户列表异常", e);
            return ResponseVO.error("查询失败: " + e.getMessage());
        }
    }

    @Override
    public ResponseVO<List<UserDetailVO>> batchQueryUsers(String token, UserBatchQueryDTO request) {
        try {
            // 1. 验证Token
            Long currentUserId = redisToken.getUserIdByToken(token);
            if (currentUserId == null) {
                return ResponseVO.unauthorized("Token无效");
            }

            // 2. 查询用户详细信息（批量）
            List<Long> userIds = request.getIds();

            // 3. 构建查询条件
            LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
            wrapper.in(User::getId, userIds);

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
}
