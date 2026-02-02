package com.eval.gameeval.service.impl;

import com.eval.gameeval.mapper.UserMapper;
import com.eval.gameeval.models.DTO.LoginRequestDTO;
import com.eval.gameeval.models.DTO.UserCreateDTO;
import com.eval.gameeval.models.VO.LoginResponseVO;
import com.eval.gameeval.models.VO.ResponseVO;
import com.eval.gameeval.models.VO.UserVO;
import com.eval.gameeval.models.entity.User;
import com.eval.gameeval.models.enums.enums;
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
    private TokenUtil tokenUtil;
    @Resource
    private PasswordEncoder passwordEncoder;

    @Override
    public ResponseVO<LoginResponseVO> login(LoginRequestDTO loginRequest){
        try{
            // 1.查询用户
            User user = userMapper.selectByUsername(loginRequest.getUsername());
            if (user == null){
                return ResponseVO.unauthorized("用户名或密码错误");
            }
            // 2. 验证密码
            if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {
                return ResponseVO.unauthorized("用户名或密码错误");
            }

            // 3. 生成Token
            String token = tokenUtil.generateToken();

            // 4. 保存Token到Redis
            redisUtil.saveToken(token, user.getId());

            // 5. 构建响应
            LoginResponseVO responseVO = new LoginResponseVO();
            responseVO.setAccessToken(token);
            responseVO.setExpireTime(LocalDateTime.now().plusHours(2));

            LoginResponseVO.UserInfoVO userInfoVO = new LoginResponseVO.UserInfoVO();
            BeanUtils.copyProperties(user, userInfoVO);
            responseVO.setUserInfo(userInfoVO);

            log.info("用户登录成功: userId={}, username={}", user.getId(), user.getUsername());

            return ResponseVO.success("登录成功", responseVO);
        } catch (Exception e){
            log.error("登录错误",e);
            return ResponseVO.error("登录错误");
        }
    }

    @Override
    public ResponseVO<Void> logout(String token) {
        try {
            if (token == null || token.trim().isEmpty()) {
                return ResponseVO.badRequest("Token不能为空");
            }

            // 1. 从Token中获取用户ID
            Long userId = redisUtil.getUserIdByToken(token);

            // 2. 删除Redis中的Token
            redisUtil.deleteToken(token);

            log.info("用户退出成功: userId={}", userId);

            return ResponseVO.success("退出成功", null);

        } catch (Exception e) {
            log.error("退出登录异常", e);
            return ResponseVO.error("退出失败");
        }
    }

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
}
