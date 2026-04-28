package com.eval.gameeval.util;


import com.eval.gameeval.security.AuthSessionStore;
import com.eval.gameeval.security.JwtTokenService;
import io.jsonwebtoken.Claims;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

@Component
public class RedisToken {
    @Resource
    private JwtTokenService jwtTokenService;

    @Resource
    private AuthSessionStore authSessionStore;



    /**
     * 保存Token
     */
    public void saveToken(String token, Long userId) {
        // Deprecated: legacy UUID token storage is no longer used.
    }

    /**
     * 获取Token对应的用户ID
     */
    public Long getUserIdByToken(String token) {
        Claims claims = parseClaims(token);
        if (claims == null) {
            return null;
        }
        String sid = claims.get("sid", String.class);
        String jti = claims.getId();
        if (authSessionStore.isBlacklisted(jti)) {
            return null;
        }
        Long userId = authSessionStore.getSessionUserId(sid);
        if (userId == null) {
            return null;
        }
        if (!String.valueOf(userId).equals(claims.getSubject())) {
            return null;
        }
        long tokenVersion = readTokenVersion(claims);
        long currentVersion = authSessionStore.getTokenVersion(userId);
        if (tokenVersion != currentVersion) {
            return null;
        }
        return userId;
    }

    /**
     * 删除Token
     */
    public void deleteToken(String token) {
        Claims claims = parseClaims(token);
        if (claims == null) {
            return;
        }
        String sid = claims.get("sid", String.class);
        String jti = claims.getId();
        String subject = claims.getSubject();
        if (sid != null) {
            authSessionStore.deleteSession(sid);
            authSessionStore.deleteRefresh(sid);
            if (subject != null) {
                try {
                    authSessionStore.removeUserSession(Long.parseLong(subject), sid);
                } catch (NumberFormatException ignored) {
                    // Ignore invalid subject format
                }
            }
        }
        long ttlSeconds = Math.max(0, claims.getExpiration().toInstant().getEpochSecond()
                - java.time.Instant.now().getEpochSecond());
        authSessionStore.blacklistAccess(jti, ttlSeconds);
    }

    /**
     * 验证Token是否存在
     */
    public boolean validateToken(String token) {
        return getUserIdByToken(token) != null;
    }

    private Claims parseClaims(String token) {
        if (token == null || token.trim().isEmpty()) {
            return null;
        }
        try {
            Claims claims = jwtTokenService.parseAccessClaims(token);
            String type = claims.get("type", String.class);
            if (!"access".equals(type)) {
                return null;
            }
            return claims;
        } catch (Exception e) {
            return null;
        }
    }

    private long readTokenVersion(Claims claims) {
        Object value = claims.get("tokenVersion");
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }


}
