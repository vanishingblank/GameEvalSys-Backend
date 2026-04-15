package com.eval.gameeval.util;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 用户打分概览缓存工具
 */
@Slf4j
@Component
public class ScoringOverviewCacheUtil {

    @Resource
    private RedisBaseUtil redisBaseUtil;

    public void cacheUserOverview(Long userId, Object value) {
        String key = RedisKeyUtil.buildScoringOverviewKey(userId);
        redisBaseUtil.set(key, value, RedisKeyUtil.SCORING_OVERVIEW_TTL);
    }

    public Object getUserOverviewCache(Long userId) {
        String key = RedisKeyUtil.buildScoringOverviewKey(userId);
        return redisBaseUtil.get(key);
    }

    public void clearUserOverviewCache(Long userId) {
        try {
            String key = RedisKeyUtil.buildScoringOverviewKey(userId);
            redisBaseUtil.delete(key);
            log.info("【缓存清除】清除用户打分概览缓存成功: userId={}", userId);
        } catch (Exception e) {
            log.error("【缓存清除】清除用户打分概览缓存异常: userId={}", userId, e);
        }
    }
}
