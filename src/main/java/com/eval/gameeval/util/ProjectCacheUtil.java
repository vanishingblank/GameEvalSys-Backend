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
                            .match(RedisKeyUtil.PROJECT_LIST_KEY_PREFIX + "*")  // 匹配 project:list:*
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
    public void cacheAuthorizedProjects(Long userId, Object value) {
        String key = RedisKeyUtil.buildAuthorizedProjectsKey(userId);
        redisBaseUtil.set(key, value, RedisKeyUtil.PROJECT_AUTHORIZED_TTL);
    }

    public Object getAuthorizedProjectsCache(Long userId) {
        String key = RedisKeyUtil.buildAuthorizedProjectsKey(userId);
        return redisBaseUtil.get(key);
    }

    public void clearAuthorizedProjectsCache(Long userId) {
        String key = RedisKeyUtil.buildAuthorizedProjectsKey(userId);
        redisBaseUtil.delete(key);
    }

    // ========== 项目小组缓存 ==========
    public void cacheProjectGroups(Long projectId, Object value) {
        String key = RedisKeyUtil.buildProjectGroupsKey(projectId);
        redisBaseUtil.set(key, value, RedisKeyUtil.PROJECT_GROUPS_TTL);
    }

    public Object getProjectGroupsCache(Long projectId) {
        String key = RedisKeyUtil.buildProjectGroupsKey(projectId);
        return redisBaseUtil.get(key);
    }

    public void clearProjectGroupsCache(Long projectId) {
        String key = RedisKeyUtil.buildProjectGroupsKey(projectId);
        redisBaseUtil.delete(key);
    }
}