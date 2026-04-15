package com.eval.gameeval.service;

import com.eval.gameeval.models.DTO.User.LoginRequestDTO;
import com.eval.gameeval.models.VO.LoginResponseVO;
import com.eval.gameeval.models.VO.ResponseVO;
import jakarta.validation.Valid;

public interface IAuthService {
    ResponseVO<LoginResponseVO> login(@Valid LoginRequestDTO loginRequest);

    ResponseVO<Void> logout(String token);
}
