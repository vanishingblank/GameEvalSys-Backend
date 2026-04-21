package com.eval.gameeval.util;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OverviewCacheUtil {

    @Resource
    private RedisBaseUtil redisBaseUtil;

    public void cacheUserOverview(Object value) {
        redisBaseUtil.set(RedisKeyUtil.USER_OVERVIEW_KEY, value, RedisKeyUtil.OVERVIEW_TTL);
    }

    public Object getUserOverviewCache() {
        return redisBaseUtil.get(RedisKeyUtil.USER_OVERVIEW_KEY);
    }

    public void clearUserOverviewCache() {
        redisBaseUtil.delete(RedisKeyUtil.USER_OVERVIEW_KEY);
    }

    public void cacheStandardOverview(Object value) {
        redisBaseUtil.set(RedisKeyUtil.STANDARD_OVERVIEW_KEY, value, RedisKeyUtil.OVERVIEW_TTL);
    }

    public Object getStandardOverviewCache() {
        return redisBaseUtil.get(RedisKeyUtil.STANDARD_OVERVIEW_KEY);
    }

    public void clearStandardOverviewCache() {
        redisBaseUtil.delete(RedisKeyUtil.STANDARD_OVERVIEW_KEY);
    }

    public void cacheProjectOverview(Object value) {
        redisBaseUtil.set(RedisKeyUtil.PROJECT_OVERVIEW_KEY, value, RedisKeyUtil.OVERVIEW_TTL);
    }

    public Object getProjectOverviewCache() {
        return redisBaseUtil.get(RedisKeyUtil.PROJECT_OVERVIEW_KEY);
    }

    public void clearProjectOverviewCache() {
        redisBaseUtil.delete(RedisKeyUtil.PROJECT_OVERVIEW_KEY);
    }

    public void cacheGroupOverview(Object value) {
        redisBaseUtil.set(RedisKeyUtil.GROUP_OVERVIEW_KEY, value, RedisKeyUtil.OVERVIEW_TTL);
    }

    public Object getGroupOverviewCache() {
        return redisBaseUtil.get(RedisKeyUtil.GROUP_OVERVIEW_KEY);
    }

    public void clearGroupOverviewCache() {
        redisBaseUtil.delete(RedisKeyUtil.GROUP_OVERVIEW_KEY);
    }

    public void cacheReviewerGroupOverview(Object value) {
        redisBaseUtil.set(RedisKeyUtil.REVIEWER_GROUP_OVERVIEW_KEY, value, RedisKeyUtil.OVERVIEW_TTL);
    }

    public Object getReviewerGroupOverviewCache() {
        return redisBaseUtil.get(RedisKeyUtil.REVIEWER_GROUP_OVERVIEW_KEY);
    }

    public void clearReviewerGroupOverviewCache() {
        redisBaseUtil.delete(RedisKeyUtil.REVIEWER_GROUP_OVERVIEW_KEY);
    }
}
