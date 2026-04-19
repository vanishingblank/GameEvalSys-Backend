package com.eval.gameeval.util;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 项目缓存工具
 */
@Slf4j
@Component
public class ProjectCacheUtil {

    @Resource
    private RedisBaseUtil redisBaseUtil;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    // ========== 项目列表缓存 ==========
    public void cacheProjectList(String key, Object value) {
        redisBaseUtil.set(key, value, RedisKeyUtil.PROJECT_LIST_TTL);
    }

    public Object getProjectListCache(String key) {
        return redisBaseUtil.get(key);
    }

    public void clearAllProjectListCache() {
        try {
            // 1. 使用 SCAN 命令遍历匹配的 key（非阻塞）
            Cursor<String> cursor = redisTemplate.scan(
                    ScanOptions.scanOptions()
                            .match("*"+RedisKeyUtil.PROJECT_LIST_KEY_PREFIX + "*")  // 匹配 project:list:*
                            .count(100)  // 每次扫描100个，避免单次压力过大
                            .build()
            );

            // 2. 收集所有匹配的 key
            List<String> keysToDelete = new ArrayList<>();
            while (cursor.hasNext()) {
                keysToDelete.add(cursor.next());
            }
            cursor.close();  // 必须关闭 cursor，释放资源

            // 3. 批量删除
            if (!keysToDelete.isEmpty()) {
                Long deletedCount = redisTemplate.delete(keysToDelete);
                log.info("【缓存清除】清除项目列表缓存成功: prefix={}, count={}",
                        RedisKeyUtil.PROJECT_LIST_KEY_PREFIX, deletedCount);
            } else {
                log.debug("【缓存清除】无项目列表缓存需要清除: prefix={}",
                        RedisKeyUtil.PROJECT_LIST_KEY_PREFIX);
            }

        } catch (Exception e) {
            log.error("【缓存清除】清除项目列表缓存异常", e);
            // 不抛出异常，避免影响主业务流程
        }
    }

    // ========== 项目详情缓存 ==========
    public void cacheProjectDetail(Long projectId, Object value) {
        String key = RedisKeyUtil.buildProjectDetailKey(projectId);
        redisBaseUtil.set(key, value, RedisKeyUtil.PROJECT_DETAIL_TTL);
    }

    public Object getProjectDetailCache(Long projectId) {
        String key = RedisKeyUtil.buildProjectDetailKey(projectId);
        return redisBaseUtil.get(key);
    }

    public void cacheProjectNull(Long projectId) {
        String key = RedisKeyUtil.buildProjectNullKey(projectId);
        redisBaseUtil.set(key, "null", RedisKeyUtil.NULL_TTL);
    }

    public boolean isProjectNullCached(Long projectId) {
        String key = RedisKeyUtil.buildProjectNullKey(projectId);
        return redisBaseUtil.hasKey(key);
    }

    public void clearProjectDetailCache(Long projectId) {
        String key = RedisKeyUtil.buildProjectDetailKey(projectId);
        redisBaseUtil.delete(key);
        redisBaseUtil.delete(RedisKeyUtil.buildProjectNullKey(projectId));
    }

    // ========== 授权项目缓存 ==========
    public void cacheAuthorizedProjects(String key, Object value) {
        redisBaseUtil.set(key, value, RedisKeyUtil.PROJECT_AUTHORIZED_TTL);
    }

    public Object getAuthorizedProjectsCache(String key) {
        return redisBaseUtil.get(key);
    }

    public void clearAuthorizedProjectsCache(Long userId) {
        try {
            String oldKey = RedisKeyUtil.buildAuthorizedProjectsKey(userId);
            redisBaseUtil.delete(oldKey);

            Cursor<String> cursor = redisTemplate.scan(
                    ScanOptions.scanOptions()
                            .match("*" + RedisKeyUtil.buildAuthorizedProjectsPrefix(userId) + "*")
                            .count(100)
                            .build()
            );

            List<String> keysToDelete = new ArrayList<>();
            while (cursor.hasNext()) {
                keysToDelete.add(cursor.next());
            }
            cursor.close();

            if (!keysToDelete.isEmpty()) {
                Long deletedCount = redisTemplate.delete(keysToDelete);
                log.info("【缓存清除】清除授权项目分页缓存成功: userId={}, count={}", userId, deletedCount);
            } else {
                log.debug("【缓存清除】无授权项目分页缓存需要清除: userId={}", userId);
            }
        } catch (Exception e) {
            log.error("【缓存清除】清除授权项目缓存异常: userId={}", userId, e);
        }
    }

    // ========== 项目小组缓存 ==========
    public void cacheProjectGroups(Long projectId, Object value) {
        String key = RedisKeyUtil.buildProjectGroupsKey(projectId);
        boolean result = redisBaseUtil.set(key, value, RedisKeyUtil.PROJECT_GROUPS_TTL);
        if (result) {
            log.info("【项目小组缓存】设置成功: key={}, ttl={}s", key, RedisKeyUtil.PROJECT_GROUPS_TTL);
        } else {
            log.error("【项目小组缓存】设置失败: key={}, projectId={}", key, projectId);
        }
    }

    public Object getProjectGroupsCache(Long projectId) {
        String key = RedisKeyUtil.buildProjectGroupsKey(projectId);
        Object value = redisBaseUtil.get(key);
        if (value != null) {
            log.debug("【项目小组缓存】命中: key={}", key);
        } else {
            log.debug("【项目小组缓存】未命中: key={}", key);
        }
        return value;
    }

    public void clearProjectGroupsCache(Long projectId) {
        String key = RedisKeyUtil.buildProjectGroupsKey(projectId);
        boolean result = redisBaseUtil.delete(key);
        if (result) {
            log.info("【项目小组缓存】清除成功: key={}", key);
        } else {
            log.debug("【项目小组缓存】清除(key不存在或失败): key={}", key);
        }
    }

    public void clearPlatformStatisticsCache() {
        try {
            redisBaseUtil.delete(RedisKeyUtil.PLATFORM_STATISTICS_KEY);
            log.info("【缓存清除】清除平台统计缓存成功: key={}", RedisKeyUtil.PLATFORM_STATISTICS_KEY);
        } catch (Exception e) {
            log.error("【缓存清除】清除平台统计缓存异常", e);
        }
    }
}
