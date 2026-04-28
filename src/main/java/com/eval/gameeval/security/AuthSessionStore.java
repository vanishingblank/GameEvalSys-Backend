package com.eval.gameeval.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.TimeUnit;

@Component
public class AuthSessionStore {

    private static final String SESSION_PREFIX = "auth:session:";
    private static final String USER_SESSIONS_PREFIX = "auth:user:sessions:";
    private static final String REFRESH_PREFIX = "auth:refresh:";
    private static final String BLACKLIST_PREFIX = "auth:blacklist:access:";
    private static final String TOKEN_VERSION_PREFIX = "auth:user:tokenVersion:";

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Value("${jwt.refresh-seconds:604800}")
    private long refreshSeconds;

    public void saveSession(String sid, Long userId, String username, String role) {
        String key = SESSION_PREFIX + sid;
        Map<String, Object> values = new HashMap<>();
        values.put("sid", sid);
        values.put("userId", userId);
        values.put("username", username);
        values.put("role", role);
        values.put("loginAt", Instant.now().toString());
        values.put("lastActiveAt", Instant.now().toString());
        values.put("status", "active");
        redisTemplate.opsForHash().putAll(key, values);
        redisTemplate.expire(key, refreshSeconds, TimeUnit.SECONDS);
    }

    public Map<Object, Object> getSession(String sid) {
        String key = SESSION_PREFIX + sid;
        return redisTemplate.opsForHash().entries(key);
    }

    public Long getSessionUserId(String sid) {
        Object value = redisTemplate.opsForHash().get(SESSION_PREFIX + sid, "userId");
        if (value == null) {
            return null;
        }
        return Long.parseLong(value.toString());
    }

    public void deleteSession(String sid) {
        redisTemplate.delete(SESSION_PREFIX + sid);
    }

    public void refreshSessionTtl(String sid) {
        redisTemplate.expire(SESSION_PREFIX + sid, refreshSeconds, TimeUnit.SECONDS);
    }

    public void addUserSession(Long userId, String sid) {
        String key = USER_SESSIONS_PREFIX + userId;
        redisTemplate.opsForSet().add(key, sid);
        redisTemplate.expire(key, refreshSeconds, TimeUnit.SECONDS);
    }

    public Set<String> getUserSessions(Long userId) {
        Set<Object> raw = redisTemplate.opsForSet().members(USER_SESSIONS_PREFIX + userId);
        if (raw == null || raw.isEmpty()) {
            return Set.of();
        }
        return raw.stream().map(Object::toString).collect(Collectors.toSet());
    }

    public void refreshUserSessionsTtl(Long userId) {
        redisTemplate.expire(USER_SESSIONS_PREFIX + userId, refreshSeconds, TimeUnit.SECONDS);
    }

    public void removeUserSession(Long userId, String sid) {
        String key = USER_SESSIONS_PREFIX + userId;
        redisTemplate.opsForSet().remove(key, sid);
    }

    public long getTokenVersion(Long userId) {
        Object value = redisTemplate.opsForValue().get(TOKEN_VERSION_PREFIX + userId);
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

    public long bumpTokenVersion(Long userId) {
        Long updated = redisTemplate.opsForValue().increment(TOKEN_VERSION_PREFIX + userId);
        return updated == null ? getTokenVersion(userId) : updated;
    }

    public void clearUserSessions(Long userId) {
        Set<String> sids = getUserSessions(userId);
        for (String sid : sids) {
            deleteSession(sid);
            deleteRefresh(sid);
        }
        redisTemplate.delete(USER_SESSIONS_PREFIX + userId);
    }

    public void saveRefresh(String sid, String refreshToken, String tokenId) {
        String key = REFRESH_PREFIX + sid;
        Map<String, Object> values = new HashMap<>();
        values.put("tokenHash", hashToken(refreshToken));
        values.put("tokenId", tokenId);
        redisTemplate.opsForHash().putAll(key, values);
        redisTemplate.expire(key, refreshSeconds, TimeUnit.SECONDS);
    }

    public Map<Object, Object> getRefreshInfo(String sid) {
        String key = REFRESH_PREFIX + sid;
        return redisTemplate.opsForHash().entries(key);
    }

    public boolean matchRefreshToken(String sid, String refreshToken) {
        Object stored = redisTemplate.opsForHash().get(REFRESH_PREFIX + sid, "tokenHash");
        if (stored == null) {
            return false;
        }
        return stored.toString().equals(hashToken(refreshToken));
    }

    public void deleteRefresh(String sid) {
        redisTemplate.delete(REFRESH_PREFIX + sid);
    }

    public void blacklistAccess(String jti, long ttlSeconds) {
        if (jti == null || ttlSeconds <= 0) {
            return;
        }
        String key = BLACKLIST_PREFIX + jti;
        redisTemplate.opsForValue().set(key, 1, ttlSeconds, TimeUnit.SECONDS);
    }

    public boolean isBlacklisted(String jti) {
        if (jti == null) {
            return false;
        }
        return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + jti));
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash refresh token", e);
        }
    }
}
