package com.eval.gameeval.service.impl;

import com.eval.gameeval.mapper.UserMapper;
import com.eval.gameeval.models.DTO.LoginRequestDTO;
import com.eval.gameeval.models.DTO.UserCreateDTO;
import com.eval.gameeval.models.DTO.UserUpdateDTO;
import com.eval.gameeval.models.VO.LoginResponseVO;
import com.eval.gameeval.models.VO.ResponseVO;
import com.eval.gameeval.models.VO.UserVO;
import com.eval.gameeval.models.entity.User;
import com.eval.gameeval.service.IUserService;
import com.eval.gameeval.util.RedisUtil;
import com.eval.gameeval.util.TokenUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class UserServiceImpl implements IUserService {
    @Resource
    private UserMapper userMapper;
    @Resource
    private RedisUtil redisUtil;
    @Resource
    private PasswordEncoder passwordEncoder;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseVO<List<UserVO>> createUsers(String token, UserCreateDTO request) {
        try {
            // 1. 验证Token并获取当前用户
            Long currentUserId = redisUtil.getUserIdByToken(token);
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
            List<UserVO> createdUsers = new ArrayList<>();
            List<String> errors = new ArrayList<>();

            for (UserCreateDTO.UserDTO userDTO : request.getUsers()) {
                try {
                    // 检查用户名是否已存在
                    Integer count = userMapper.countByUsername(userDTO.getUsername());
                    if (count != null && count > 0) {
                        errors.add("用户名 " + userDTO.getUsername() + " 已存在");
                        continue;
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
                    UserVO userVO = new UserVO();
                    BeanUtils.copyProperties(user, userVO);
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
            Long currentUserId = redisUtil.getUserIdByToken(token);
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
                // 7. 如果禁用用户，记录日志（Token清理可后续扩展）
                if (request.getIsEnabled() != null && !request.getIsEnabled()) {
                    log.info("用户被禁用: userId={}, operator={}", userId, currentUserId);
                    // TODO: 清理该用户所有Token（遍历Redis）
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
            Long currentUserId = redisUtil.getUserIdByToken(token);
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

            // 5. 执行逻辑删除（调用Mapper自定义disable方法）
            int rows = userMapper.disableById(userId, LocalDateTime.now());

            if (rows > 0) {
                log.info("逻辑删除用户成功: userId={}, operator={}", userId, currentUserId);
                // TODO: 清理该用户所有Token（遍历Redis）
                return ResponseVO.<Void>success("删除成功",null);
            } else {
                return ResponseVO.error("删除失败：用户可能已被删除");
            }

        } catch (Exception e) {
            log.error("删除用户异常: userId={}", userId, e);
            return ResponseVO.error("删除失败: " + e.getMessage());
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
