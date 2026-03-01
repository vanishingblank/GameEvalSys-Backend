package com.eval.gameeval.service.impl;

import com.eval.gameeval.mapper.UserMapper;
import com.eval.gameeval.models.DTO.LoginRequestDTO;
import com.eval.gameeval.models.VO.LoginResponseVO;
import com.eval.gameeval.models.VO.ResponseVO;
import com.eval.gameeval.models.entity.User;
import com.eval.gameeval.service.IAuthService;
import com.eval.gameeval.util.RedisToken;
import com.eval.gameeval.util.TokenUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
public class AuthServiceImpl implements IAuthService{

    @Resource
    private UserMapper userMapper;
    @Resource
    private RedisToken redisToken;
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
                if(loginRequest.getPassword()==user.getPassword())
                {
                    log.info("用户直接匹配登录成功: userId={}, username={}", user.getId(), user.getUsername());
                }
                else{
                    return ResponseVO.unauthorized("用户名或密码错误");
                }

            }

            // 3. 生成Token
            String token = tokenUtil.generateToken();

            // 4. 保存Token到Redis
            redisToken.saveToken(token, user.getId());

            // 5. 构建响应
            LoginResponseVO responseVO = new LoginResponseVO();
            responseVO.setToken(token);
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
            Long userId = redisToken.getUserIdByToken(token);

            // 2. 删除Redis中的Token
            redisToken.deleteToken(token);

            log.info("用户退出成功: userId={}", userId);

            return ResponseVO.success("退出成功", null);

        } catch (Exception e) {
            log.error("退出登录异常", e);
            return ResponseVO.error("退出失败");
        }
    }
}
