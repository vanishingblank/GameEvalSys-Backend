package com.eval.gameeval.service;

import com.eval.gameeval.models.DTO.LoginRequestDTO;
import com.eval.gameeval.models.DTO.UserCreateDTO;
import com.eval.gameeval.models.DTO.UserUpdateDTO;
import com.eval.gameeval.models.VO.LoginResponseVO;
import com.eval.gameeval.models.VO.ResponseVO;
import com.eval.gameeval.models.VO.UserVO;
import jakarta.validation.Valid;

import java.util.List;

public interface IUserService {


    /**
     * 批量创建用户
     * @param token 认证Token
     * @param request 创建用户请求
     * @return 创建成功的用户列表
     */
    ResponseVO<List<UserVO>> createUsers(String token, @Valid UserCreateDTO request);

    ResponseVO<Void> updateUser(String token, Long userId, @Valid UserUpdateDTO request);

    ResponseVO<Void> deleteUser(String token, Long userId);
}
