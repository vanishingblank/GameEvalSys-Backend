package com.eval.gameeval.service;

import com.eval.gameeval.models.DTO.User.LoginRequestDTO;
import com.eval.gameeval.models.DTO.User.RefreshRequestDTO;
import com.eval.gameeval.models.VO.LoginResponseVO;
import com.eval.gameeval.models.VO.RefreshResponseVO;
import com.eval.gameeval.models.VO.ResponseVO;
import com.eval.gameeval.models.VO.SessionInfoVO;
import jakarta.validation.Valid;

import java.util.List;

public interface IAuthService {
    ResponseVO<LoginResponseVO> login(@Valid LoginRequestDTO loginRequest);

    ResponseVO<Void> logout(String token);

    ResponseVO<RefreshResponseVO> refresh(@Valid RefreshRequestDTO request);

    ResponseVO<List<SessionInfoVO>> getMySessions(Long userId);
}
