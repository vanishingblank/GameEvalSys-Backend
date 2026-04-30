package com.eval.gameeval.controller;

import com.eval.gameeval.aspect.LogRecord;
import com.eval.gameeval.models.DTO.User.LoginRequestDTO;
import com.eval.gameeval.models.DTO.User.RefreshRequestDTO;
import com.eval.gameeval.models.VO.LoginResponseVO;
import com.eval.gameeval.models.VO.RefreshResponseVO;
import com.eval.gameeval.models.VO.ResponseVO;
import com.eval.gameeval.models.VO.SessionInfoVO;
import com.eval.gameeval.security.CurrentUserContext;
import com.eval.gameeval.service.IAuthService;
import com.eval.gameeval.util.TokenUtil;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;



@Slf4j
@RestController
@RequestMapping("/auth")
public class AuthController {
    @Value("${auth.refresh-cookie.name:refreshToken}")
    private String refreshCookieName;
    @Value("${auth.refresh-cookie.path:/api/v1/auth/refresh}")
    private String refreshCookiePath;
    @Value("${auth.refresh-cookie.http-only:true}")
    private boolean refreshCookieHttpOnly;
    @Value("${auth.refresh-cookie.secure:true}")
    private boolean refreshCookieSecure;
    @Value("${auth.refresh-cookie.same-site:None}")
    private String refreshCookieSameSite;
    @Value("${auth.refresh-cookie.max-age-seconds:604800}")
    private long refreshCookieMaxAgeSeconds;

    @Resource
    private IAuthService authService;
    @Resource
    private CurrentUserContext currentUserContext;

    @PostMapping("/login")
    @LogRecord(value = "用户登录", module = "Auth")
    public ResponseEntity<ResponseVO<LoginResponseVO>> login(
            @Valid @RequestBody LoginRequestDTO loginRequest,
            HttpServletResponse httpServletResponse){
        ResponseVO<LoginResponseVO> response = authService.login(loginRequest);
        writeRefreshCookie(httpServletResponse, response.getData() != null ? response.getData().getRefreshToken() : null);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    @LogRecord(value = "用户登出", module = "Auth")
    public ResponseEntity<ResponseVO<Void>> logout(
            @RequestHeader("Authorization") String authorization,
            HttpServletResponse httpServletResponse){
        String token = TokenUtil.extractToken(authorization);
        ResponseVO<Void> response = authService.logout(token);
        clearRefreshCookie(httpServletResponse);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    @LogRecord(value = "刷新Token", module = "Auth")
    public ResponseEntity<ResponseVO<RefreshResponseVO>> refresh(
            @Valid @RequestBody RefreshRequestDTO request,
            @CookieValue(value = "${auth.refresh-cookie.name:refreshToken}", required = false) String refreshToken,
            HttpServletResponse httpServletResponse) {
        ResponseVO<RefreshResponseVO> response = authService.refresh(request, refreshToken);
        if (response.getCode() == 200) {
            writeRefreshCookie(httpServletResponse, response.getData() != null ? response.getData().getRefreshToken() : null);
        } else {
            clearRefreshCookie(httpServletResponse);
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/sessions/me")
    public ResponseEntity<ResponseVO<List<SessionInfoVO>>> getMySessions() {
        Long userId = currentUserContext.getCurrentUserId();
        ResponseVO<List<SessionInfoVO>> response = authService.getMySessions(userId);
        return ResponseEntity.ok(response);
    }

    private void writeRefreshCookie(HttpServletResponse response, String refreshToken) {
        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            return;
        }
        ResponseCookie cookie = ResponseCookie.from(refreshCookieName, refreshToken)
                .httpOnly(refreshCookieHttpOnly)
                .secure(refreshCookieSecure)
                .sameSite(refreshCookieSameSite)
                .path(refreshCookiePath)
                .maxAge(refreshCookieMaxAgeSeconds)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(refreshCookieName, "")
                .httpOnly(refreshCookieHttpOnly)
                .secure(refreshCookieSecure)
                .sameSite(refreshCookieSameSite)
                .path(refreshCookiePath)
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

//    private String extractToken(String authorization) {
//        if (authorization != null && authorization.startsWith("Bearer ")) {
//            return authorization.substring(7); // "Bearer "长度为7
//        }
//        return authorization;
//    }
}
