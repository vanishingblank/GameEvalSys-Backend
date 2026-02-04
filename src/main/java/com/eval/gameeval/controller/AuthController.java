package com.eval.gameeval.controller;

import com.eval.gameeval.models.DTO.LoginRequestDTO;
import com.eval.gameeval.models.VO.LoginResponseVO;
import com.eval.gameeval.models.VO.ResponseVO;
import com.eval.gameeval.service.IAuthService;
import com.eval.gameeval.service.IUserService;
import com.eval.gameeval.util.TokenUtil;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;



@Slf4j
@RestController
@RequestMapping("/auth")
public class AuthController {
    @Resource
    private IAuthService authService;

    @PostMapping("/login")
    public ResponseEntity<ResponseVO<LoginResponseVO>> login(
            @Valid @RequestBody LoginRequestDTO loginRequest){
        ResponseVO<LoginResponseVO> response = authService.login(loginRequest);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<ResponseVO<Void>> logout(
            @RequestHeader("Authorization") String authorization){
        String token = TokenUtil.extractToken(authorization);
        ResponseVO<Void> response = authService.logout(token);
        return ResponseEntity.ok(response);
    }

//    private String extractToken(String authorization) {
//        if (authorization != null && authorization.startsWith("Bearer ")) {
//            return authorization.substring(7); // "Bearer "长度为7
//        }
//        return authorization;
//    }
}
