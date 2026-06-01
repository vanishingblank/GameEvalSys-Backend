package com.eval.gameeval.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import com.eval.gameeval.util.RedisKeyUtil;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.Comparator;
import java.util.stream.Collectors;
import java.util.concurrent.TimeUnit;

@Component
public class AuthSessionStore {

    private static final String SESSION_PREFIX = "auth:session:";
    private static final String USER_SESSIONS_PREFIX = "auth:user:sessions:";
    private static final String REFRESH_PREFIX = "auth:refresh:";
    private static final String BLACKLIST_PREFIX = "auth:blacklist:access:";
    private static final String TOKEN_VERSION_PREFIX = "auth:user:tokenVersion:";
    private static final StringRedisSerializer STRING_SERIALIZER = new StringRedisSerializer();

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Value("${jwt.refresh-seconds:604800}")
    private long refreshSeconds;

    @Value("${jwt.last-active-refresh-seconds:300}")
    private long lastActiveRefreshSeconds;

    @Value("${jwt.online-active-window-seconds:300}")
    private long onlineActiveWindowSeconds;

    @Value("${jwt.max-sessions-per-user:10}")
    private int maxSessionsPerUser;

    public void saveSession(String sid, Long userId, String username, String role, String ip, String device, String loginLocation) {
        String key = SESSION_PREFIX + sid;
        Map<String, Object> values = new HashMap<>();
        values.put("sid", sid);
        values.put("userId", userId);
        values.put("username", username);
        values.put("role", role);
        if (ip != null && !ip.trim().isEmpty()) {
            values.put("ip", ip);
        }
        if (device != null && !device.trim().isEmpty()) {
            values.put("device", device);
        }
        if (loginLocation != null && !loginLocation.trim().isEmpty()) {
            values.put("loginLocation", loginLocation);
        }
        values.put("loginAt", Instant.now().toString());
        values.put("lastActiveAt", Instant.now().toString());
        values.put("status", "active");
        redisTemplate.opsForHash().putAll(key, values);
        redisTemplate.expire(key, refreshSeconds, TimeUnit.SECONDS);
        touchUserOnlineIndex(userId);
    }

    public void updateAccessInfo(String sid, String accessJti, long accessExpEpochSeconds) {
        if (sid == null || sid.trim().isEmpty()) {
            return;
        }
        String key = SESSION_PREFIX + sid;
        if (accessJti != null && !accessJti.trim().isEmpty()) {
            redisTemplate.opsForHash().put(key, "accessJti", accessJti);
        }
        if (accessExpEpochSeconds > 0) {
            redisTemplate.opsForHash().put(key, "accessExp", String.valueOf(accessExpEpochSeconds));
        }
    }

    public Map<Object, Object> getSession(String sid) {
        String key = SESSION_PREFIX + sid;
        return getHashEntries(key);
    }

    public Long getSessionUserId(String sid) {
        Object value = getHashValue(SESSION_PREFIX + sid, "userId");
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
        Set<Object> raw;
        try {
            raw = redisTemplate.opsForSet().members(USER_SESSIONS_PREFIX + userId);
        } catch (SerializationException e) {
            raw = getStringSetMembers(USER_SESSIONS_PREFIX + userId);
        }
        if (raw == null || raw.isEmpty()) {
            return Set.of();
        }
        return raw.stream().map(Object::toString).collect(Collectors.toSet());
    }

    public Map<String, Map<Object, Object>> getSessionsBySids(Collection<String> sids) {
        if (sids == null || sids.isEmpty()) {
            return Map.of();
        }

        List<String> orderedSids = new ArrayList<>();
        for (String sid : sids) {
            if (sid != null && !sid.trim().isEmpty()) {
                orderedSids.add(sid);
            }
        }
        if (orderedSids.isEmpty()) {
            return Map.of();
        }

        List<Object> rawResults = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (String sid : orderedSids) {
                connection.hGetAll(STRING_SERIALIZER.serialize(SESSION_PREFIX + sid));
            }
            return null;
        });

        Map<String, Map<Object, Object>> result = new LinkedHashMap<>();
        for (int i = 0; i < orderedSids.size(); i++) {
            Object rawResult = i < rawResults.size() ? rawResults.get(i) : null;
            Map<Object, Object> session = convertPipelinedHashResult(rawResult);
            if (session != null && !session.isEmpty()) {
                result.put(orderedSids.get(i), session);
            }
        }
        return result;
    }

    public void refreshUserSessionsTtl(Long userId) {
        redisTemplate.expire(USER_SESSIONS_PREFIX + userId, refreshSeconds, TimeUnit.SECONDS);
    }

    public Set<Long> getOnlineUserIds() {
        return getActiveOnlineUserIds();
    }

    public Set<Long> getActiveOnlineUserIds() {
        long cutoffMillis = Instant.now().minusSeconds(Math.max(0, onlineActiveWindowSeconds)).toEpochMilli();
        Set<Object> members = redisTemplate.opsForZSet().rangeByScore(
                RedisKeyUtil.buildOnlineUserIndexKey(),
                cutoffMillis,
                Double.MAX_VALUE
        );
        return convertUserIdSet(members);
    }

    public Set<Long> getLoggedInUserIds() {
        Set<Object> members = redisTemplate.opsForZSet().range(RedisKeyUtil.buildOnlineUserIndexKey(), 0, -1);
        return convertUserIdSet(members);
    }

    public boolean isSessionRecentlyActive(Map<Object, Object> session) {
        if (session == null || session.isEmpty()) {
            return false;
        }
        Instant lastActiveAt = parseInstant(session.get("lastActiveAt"));
        if (lastActiveAt == null) {
            lastActiveAt = parseInstant(session.get("loginAt"));
        }
        return isWithinActiveWindow(lastActiveAt, Instant.now());
    }

    public void removeUserSession(Long userId, String sid) {
        String key = USER_SESSIONS_PREFIX + userId;
        redisTemplate.opsForSet().remove(key, sid);
    }

    public void touchUserOnlineIndex(Long userId) {
        if (userId == null) {
            return;
        }
        markUserOnline(userId, Instant.now());
    }

    public void rebuildUserOnlineIndex(Long userId) {
        if (userId == null) {
            return;
        }

        Set<String> sids = getUserSessions(userId);
        if (sids == null || sids.isEmpty()) {
            removeUserOnlineIndex(userId);
            return;
        }

        Map<String, Map<Object, Object>> sessions = getSessionsBySids(sids);
        if (sessions.isEmpty()) {
            removeUserOnlineIndex(userId);
            return;
        }
        Instant latestActivity = null;
        for (Map<Object, Object> session : sessions.values()) {
            Instant activity = resolveSessionActivity(session);
            if (activity != null && (latestActivity == null || activity.isAfter(latestActivity))) {
                latestActivity = activity;
            }
        }

        if (latestActivity == null) {
            removeUserOnlineIndex(userId);
            return;
        }
        markUserOnline(userId, latestActivity);
    }

    public void enforceSessionLimit(Long userId) {
        if (userId == null || maxSessionsPerUser <= 0) {
            return;
        }
        Set<String> sids = getUserSessions(userId);
        int over = sids.size() - maxSessionsPerUser;
        if (over <= 0) {
            return;
        }

        List<SessionSnapshot> snapshots = new ArrayList<>();
        for (String sid : sids) {
            Map<Object, Object> session = getSession(sid);
            snapshots.add(new SessionSnapshot(sid, parseLoginAt(session)));
        }

        snapshots.sort(Comparator.comparing(snapshot -> snapshot.loginAt));
        for (int i = 0; i < over && i < snapshots.size(); i++) {
            String sid = snapshots.get(i).sid;
            deleteSession(sid);
            deleteRefresh(sid);
            removeUserSession(userId, sid);
        }
        rebuildUserOnlineIndex(userId);
    }

    public void updateLastActiveIfStale(String sid) {
        if (sid == null || sid.trim().isEmpty()) {
            return;
        }
        String key = SESSION_PREFIX + sid;
        Object raw = getHashValue(key, "lastActiveAt");
        Instant now = Instant.now();
        if (raw == null) {
            redisTemplate.opsForHash().put(key, "lastActiveAt", now.toString());
            touchUserOnlineIndex(getSessionUserId(sid));
            return;
        }
        try {
            Instant last = Instant.parse(raw.toString());
            if (now.minusSeconds(lastActiveRefreshSeconds).isAfter(last)) {
                redisTemplate.opsForHash().put(key, "lastActiveAt", now.toString());
                touchUserOnlineIndex(getSessionUserId(sid));
            }
        } catch (DateTimeParseException e) {
            redisTemplate.opsForHash().put(key, "lastActiveAt", now.toString());
            touchUserOnlineIndex(getSessionUserId(sid));
        }
    }

    public long getTokenVersion(Long userId) {
        String key = TOKEN_VERSION_PREFIX + userId;
        Object value;
        try {
            value = redisTemplate.opsForValue().get(key);
        } catch (SerializationException e) {
            redisTemplate.delete(key);
            return 0L;
        }
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
        removeUserOnlineIndex(userId);
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
        return getHashEntries(key);
    }

    public boolean matchRefreshToken(String sid, String refreshToken) {
        Object stored = getHashValue(REFRESH_PREFIX + sid, "tokenHash");
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

    private Instant parseLoginAt(Map<Object, Object> session) {
        if (session == null) {
            return Instant.EPOCH;
        }
        Object raw = session.get("loginAt");
        if (raw == null) {
            return Instant.EPOCH;
        }
        try {
            return Instant.parse(raw.toString());
        } catch (DateTimeParseException e) {
            return Instant.EPOCH;
        }
    }

    private Instant parseInstant(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value.toString());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private void markUserOnline(Long userId, Instant activityTime) {
        if (userId == null) {
            return;
        }
        Instant safeTime = activityTime == null ? Instant.now() : activityTime;
        redisTemplate.opsForZSet().add(
                RedisKeyUtil.buildOnlineUserIndexKey(),
                String.valueOf(userId),
                safeTime.toEpochMilli()
        );
    }

    private void removeUserOnlineIndex(Long userId) {
        if (userId == null) {
            return;
        }
        redisTemplate.opsForZSet().remove(RedisKeyUtil.buildOnlineUserIndexKey(), String.valueOf(userId));
    }

    private Set<Long> convertUserIdSet(Set<Object> members) {
        if (members == null || members.isEmpty()) {
            return Set.of();
        }
        Set<Long> result = new HashSet<>();
        for (Object member : members) {
            Long userId = toLong(member);
            if (userId != null) {
                result.add(userId);
            }
        }
        return result;
    }

    private Map<Object, Object> convertPipelinedHashResult(Object rawResult) {
        if (!(rawResult instanceof Map<?, ?> rawMap)) {
            return Map.of();
        }
        Map<Object, Object> result = new HashMap<>();
        RedisSerializer<Object> hashValueSerializer =
                (RedisSerializer<Object>) redisTemplate.getHashValueSerializer();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            Object key = convertRedisValue(entry.getKey());
            Object value = convertRedisValue(entry.getValue(), hashValueSerializer);
            if (key != null && value != null) {
                result.put(key, value);
            }
        }
        return result;
    }

    private Object convertRedisValue(Object value) {
        return convertRedisValue(value, null);
    }

    private Object convertRedisValue(Object value, RedisSerializer<Object> serializer) {
        if (value == null) {
            return null;
        }
        if (value instanceof byte[] rawBytes) {
            if (serializer != null) {
                try {
                    Object deserialized = serializer.deserialize(rawBytes);
                    if (deserialized != null) {
                        return deserialized;
                    }
                } catch (SerializationException ignored) {
                    // fall through
                }
            }
            return STRING_SERIALIZER.deserialize(rawBytes);
        }
        return value;
    }

    private Instant resolveSessionActivity(Map<Object, Object> session) {
        if (session == null || session.isEmpty()) {
            return null;
        }
        Instant lastActiveAt = parseInstant(session.get("lastActiveAt"));
        if (lastActiveAt != null) {
            return lastActiveAt;
        }
        return parseInstant(session.get("loginAt"));
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isWithinActiveWindow(Instant instant, Instant now) {
        if (instant == null) {
            return false;
        }
        if (onlineActiveWindowSeconds <= 0) {
            return true;
        }
        Instant cutoff = now.minusSeconds(onlineActiveWindowSeconds);
        return !instant.isBefore(cutoff);
    }

    private boolean isWithinActiveWindow(byte[] rawLastActiveAt, RedisSerializer<Object> hashValueSerializer, Instant now) {
        if (rawLastActiveAt == null) {
            return false;
        }
        Object value = deserializeHashValue(hashValueSerializer, rawLastActiveAt);
        Instant lastActiveAt = parseInstant(value);
        return isWithinActiveWindow(lastActiveAt, now);
    }

    private Map<Object, Object> getHashEntries(String key) {
        try {
            return redisTemplate.opsForHash().entries(key);
        } catch (SerializationException e) {
            return redisTemplate.execute((RedisCallback<Map<Object, Object>>) connection -> {
                Map<Object, Object> result = new HashMap<>();
                RedisSerializer<Object> hashValueSerializer =
                    (RedisSerializer<Object>) redisTemplate.getHashValueSerializer();
                Map<byte[], byte[]> rawEntries = connection.hGetAll(STRING_SERIALIZER.serialize(key));
                for (Map.Entry<byte[], byte[]> entry : rawEntries.entrySet()) {
                    String hashKey = STRING_SERIALIZER.deserialize(entry.getKey());
                    Object hashValue = deserializeHashValue(hashValueSerializer, entry.getValue());
                    if (hashKey != null && hashValue != null) {
                        result.put(hashKey, hashValue);
                    }
                }
                return result;
            });
        }
    }

    private Object getHashValue(String key, String field) {
        try {
            return redisTemplate.opsForHash().get(key, field);
        } catch (SerializationException e) {
            return redisTemplate.execute((RedisCallback<Object>) connection -> {
                RedisSerializer<Object> hashValueSerializer =
                    (RedisSerializer<Object>) redisTemplate.getHashValueSerializer();
                byte[] rawValue = connection.hGet(
                    STRING_SERIALIZER.serialize(key),
                    STRING_SERIALIZER.serialize(field)
                );
                return deserializeHashValue(hashValueSerializer, rawValue);
            });
        }
    }

    private Set<Object> getStringSetMembers(String key) {
        return redisTemplate.execute((RedisCallback<Set<Object>>) connection -> {
            Set<byte[]> rawMembers = connection.sMembers(STRING_SERIALIZER.serialize(key));
            Set<Object> result = new HashSet<>();
            for (byte[] rawMember : rawMembers) {
                String member = STRING_SERIALIZER.deserialize(rawMember);
                if (member != null) {
                    result.add(member);
                }
            }
            return result;
        });
    }

    private byte[] serializeHashField(RedisSerializer<Object> hashKeySerializer, String field) {
        try {
            return hashKeySerializer.serialize(field);
        } catch (SerializationException e) {
            return STRING_SERIALIZER.serialize(field);
        }
    }

    private String deserializeKey(byte[] rawKey) {
        try {
            return STRING_SERIALIZER.deserialize(rawKey);
        } catch (SerializationException e) {
            return null;
        }
    }

    private Object deserializeHashValue(RedisSerializer<Object> hashValueSerializer, byte[] rawValue) {
        if (rawValue == null) {
            return null;
        }
        try {
            return hashValueSerializer.deserialize(rawValue);
        } catch (SerializationException e) {
            return STRING_SERIALIZER.deserialize(rawValue);
        }
    }

    private static final class SessionSnapshot {
        private final String sid;
        private final Instant loginAt;

        private SessionSnapshot(String sid, Instant loginAt) {
            this.sid = sid;
            this.loginAt = loginAt;
        }
    }
}
