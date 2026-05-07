package com.eval.gameeval.controller;

import com.eval.gameeval.aspect.LogRecord;
import com.eval.gameeval.models.DTO.User.LoginMetaDTO;
import com.eval.gameeval.models.DTO.User.LoginRequestDTO;
import com.eval.gameeval.models.DTO.User.RefreshRequestDTO;
import com.eval.gameeval.models.VO.LoginResponseVO;
import com.eval.gameeval.models.VO.RefreshResponseVO;
import com.eval.gameeval.models.VO.ResponseVO;
import com.eval.gameeval.models.VO.SessionInfoVO;
import com.eval.gameeval.security.CurrentUserContext;
import com.eval.gameeval.service.IAuthService;
import com.eval.gameeval.util.IpLocationService;
import com.eval.gameeval.util.TokenUtil;
import jakarta.servlet.http.HttpServletRequest;
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
    @Resource
    private IpLocationService ipLocationService;

    @PostMapping("/login")
    @LogRecord(value = "用户登录", module = "Auth")
    public ResponseEntity<ResponseVO<LoginResponseVO>> login(
            @Valid @RequestBody LoginRequestDTO loginRequest,
            HttpServletRequest request,
            HttpServletResponse httpServletResponse){
        LoginMetaDTO meta = buildLoginMeta(request);
        ResponseVO<LoginResponseVO> response = authService.login(loginRequest, meta);
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

    private LoginMetaDTO buildLoginMeta(HttpServletRequest request) {
        String ip = extractClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        String device = buildDevice(userAgent);
        String loginLocation = ipLocationService.lookup(ip);
        return new LoginMetaDTO()
                .setIp(ip)
                .setDevice(device)
                .setLoginLocation(loginLocation);
    }

    private String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = extractFirstIp(forwarded);
        if (ip != null) {
            log.info("Extracted client IP from X-Forwarded-For: {}", ip);
            return ip;
        }
        ip = extractFirstIp(request.getHeader("X-Real-IP"));
        if (ip != null) {
            log.info("Extracted client IP from X-Real-IP: {}", ip);
            return ip;
        }
        log.info("Using client IP from request.getRemoteAddr(): {}", request.getRemoteAddr());
        return request.getRemoteAddr();
    }

    private String extractFirstIp(String headerValue) {
        if (headerValue == null) {
            return null;
        }
        String[] parts = headerValue.split(",");
        for (String part : parts) {
            String candidate = part.trim();
            if (!candidate.isEmpty() && !"unknown".equalsIgnoreCase(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private String buildDevice(String userAgent) {
        if (userAgent == null || userAgent.trim().isEmpty()) {
            return null;
        }
        String ua = userAgent.toLowerCase();
        String os;
        if (ua.contains("windows")) {
            os = "Windows";
        } else if (ua.contains("mac os")) {
            os = "Mac";
        } else if (ua.contains("android")) {
            os = "Android";
        } else if (ua.contains("iphone") || ua.contains("ipad") || ua.contains("ios")) {
            os = "iOS";
        } else {
            os = "Other";
        }

        String browser;
        if (ua.contains("edg/")) {
            browser = "Edge";
        } else if (ua.contains("chrome/")) {
            browser = "Chrome";
        } else if (ua.contains("safari/") && !ua.contains("chrome/")) {
            browser = "Safari";
        } else if (ua.contains("firefox/")) {
            browser = "Firefox";
        } else {
            browser = "Other";
        }

        return os + "/" + browser;
    }

//    private String extractToken(String authorization) {
//        if (authorization != null && authorization.startsWith("Bearer ")) {
//            return authorization.substring(7); // "Bearer "长度为7
//        }
//        return authorization;
//    }
}
