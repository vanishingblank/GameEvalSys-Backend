package com.eval.gameeval.util;

import jakarta.annotation.Resource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class RedisUtil {
    @Resource
    private RedisTemplate<String,Object>redisTemplate;

    private static final String TOKEN_PREFIX = "auth:token:";
    private static final Long TOKEN_EXPIRE = 14400L;
    /**
     * 保存Token
     */
    public void saveToken(String token, Long userId) {
        String key = TOKEN_PREFIX + token;
        redisTemplate.opsForValue().set(key, userId, TOKEN_EXPIRE, TimeUnit.SECONDS);
    }

    /**
     * 获取Token对应的用户ID
     */
    public Long getUserIdByToken(String token) {
        String key = TOKEN_PREFIX + token;
        Object value = redisTemplate.opsForValue().get(key);
        return value != null ? Long.parseLong(value.toString()) : null;
    }

    /**
     * 删除Token
     */
    public void deleteToken(String token) {
        String key = TOKEN_PREFIX + token;
        redisTemplate.delete(key);
    }

    /**
     * 验证Token是否存在
     */
    public boolean validateToken(String token) {
        String key = TOKEN_PREFIX + token;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
}
