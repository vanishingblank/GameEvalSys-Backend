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
 * 打分记录缓存工具
 */
@Slf4j
@Component
public class ScoringRecordCacheUtil {

    @Resource
    private RedisBaseUtil redisBaseUtil;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    public void cacheUserProjectRecords(String key, Object value) {
        redisBaseUtil.set(key, value, RedisKeyUtil.SCORING_RECORD_PAGE_TTL);
    }

    public Object getUserProjectRecordsCache(String key) {
        return redisBaseUtil.get(key);
    }

    public void clearUserProjectRecordsCache(Long projectId, Long userId) {
        try {
            Cursor<String> cursor = redisTemplate.scan(
                    ScanOptions.scanOptions()
                            .match("*" + RedisKeyUtil.buildScoringRecordPagePrefix(projectId, userId) + "*")
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
                log.info("【缓存清除】清除用户项目打分页缓存成功: projectId={}, userId={}, count={}",
                        projectId, userId, deletedCount);
            } else {
                log.debug("【缓存清除】无用户项目打分页缓存需要清除: projectId={}, userId={}",
                        projectId, userId);
            }
        } catch (Exception e) {
            log.error("【缓存清除】清除用户项目打分页缓存异常: projectId={}, userId={}",
                    projectId, userId, e);
        }
    }
}
