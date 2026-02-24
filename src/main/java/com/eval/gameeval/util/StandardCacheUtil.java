package com.eval.gameeval.util;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * 打分标准缓存工具
 */
@Component
public class StandardCacheUtil {

    @Resource
    private RedisBaseUtil redisBaseUtil;

    /**
     * 缓存打分标准列表
     */
    public void cacheStandardList(Object value) {
        redisBaseUtil.set(
                RedisKeyUtil.STANDARD_LIST_KEY,
                value,
                RedisKeyUtil.STANDARD_LIST_TTL
        );
    }

    /**
     * 获取缓存的打分标准列表
     */
    public Object getStandardListCache() {
        return redisBaseUtil.get(RedisKeyUtil.STANDARD_LIST_KEY);
    }

    /**
     * 缓存单个打分标准详情
     */
    public void cacheStandardDetail(Long standardId, Object value) {
        String key = RedisKeyUtil.buildStandardDetailKey(standardId);
        redisBaseUtil.set(key, value, RedisKeyUtil.STANDARD_DETAIL_TTL);
    }

    /**
     * 获取缓存的打分标准详情
     */
    public Object getStandardDetailCache(Long standardId) {
        String key = RedisKeyUtil.buildStandardDetailKey(standardId);
        return redisBaseUtil.get(key);
    }

    /**
     * 缓存空值（防穿透）
     */
    public void cacheNull(Long standardId) {
        String key = RedisKeyUtil.buildStandardNullKey(standardId);
        redisBaseUtil.set(key, "null", RedisKeyUtil.NULL_TTL);
    }

    /**
     * 检查是否缓存了空值
     */
    public boolean isNullCached(Long standardId) {
        String key = RedisKeyUtil.buildStandardNullKey(standardId);
        return redisBaseUtil.hasKey(key);
    }

    /**
     * 清除所有打分标准相关缓存
     */
    public void clearStandardCache() {
        redisBaseUtil.delete(RedisKeyUtil.STANDARD_LIST_KEY);
        // 注意：无法直接删除所有 standard:* 前缀的key，需用 scan（生产环境慎用）
    }

    /**
     * 清除单个标准的详情缓存
     */
    public void clearStandardDetailCache(Long standardId) {
        String key = RedisKeyUtil.buildStandardDetailKey(standardId);
        redisBaseUtil.delete(key);
        // 同时清除空值缓存
        redisBaseUtil.delete(RedisKeyUtil.buildStandardNullKey(standardId));
    }
}