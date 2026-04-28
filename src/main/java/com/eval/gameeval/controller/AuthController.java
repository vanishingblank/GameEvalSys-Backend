package com.eval.gameeval.controller;

import com.eval.gameeval.aspect.LogRecord;
import com.eval.gameeval.models.DTO.User.LoginRequestDTO;
import com.eval.gameeval.models.DTO.User.RefreshRequestDTO;
import com.eval.gameeval.models.VO.LoginResponseVO;
import com.eval.gameeval.models.VO.RefreshResponseVO;
import com.eval.gameeval.models.VO.ResponseVO;
import com.eval.gameeval.models.VO.SessionInfoVO;
import com.eval.gameeval.service.IAuthService;
import com.eval.gameeval.util.TokenUtil;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;



@Slf4j
@RestController
@RequestMapping("/auth")
public class AuthController {
    @Resource
    private IAuthService authService;

    @PostMapping("/login")
    @LogRecord(value = "用户登录", module = "Auth")
    public ResponseEntity<ResponseVO<LoginResponseVO>> login(
            @Valid @RequestBody LoginRequestDTO loginRequest){
        ResponseVO<LoginResponseVO> response = authService.login(loginRequest);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    @LogRecord(value = "用户登出", module = "Auth")
    public ResponseEntity<ResponseVO<Void>> logout(
            @RequestHeader("Authorization") String authorization){
        String token = TokenUtil.extractToken(authorization);
        ResponseVO<Void> response = authService.logout(token);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    @LogRecord(value = "刷新Token", module = "Auth")
    public ResponseEntity<ResponseVO<RefreshResponseVO>> refresh(
            @Valid @RequestBody RefreshRequestDTO request) {
        ResponseVO<RefreshResponseVO> response = authService.refresh(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/sessions/me")
    public ResponseEntity<ResponseVO<List<SessionInfoVO>>> getMySessions() {
        Long userId = resolveUserId();
        ResponseVO<List<SessionInfoVO>> response = authService.getMySessions(userId);
        return ResponseEntity.ok(response);
    }

    private Long resolveUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof Long) {
            return (Long) principal;
        }
        if (principal instanceof String) {
            try {
                return Long.parseLong((String) principal);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

//    private String extractToken(String authorization) {
//        if (authorization != null && authorization.startsWith("Bearer ")) {
//            return authorization.substring(7); // "Bearer "长度为7
//        }
//        return authorization;
//    }
}
