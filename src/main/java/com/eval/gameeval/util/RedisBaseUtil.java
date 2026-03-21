package com.eval.gameeval.util;

import jakarta.annotation.Resource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Redis 基础操作工具（通用 CRUD）
 */
@Component
public class RedisBaseUtil {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 设置缓存（带过期时间）
     */
    public boolean set(String key, Object value, long expireSeconds) {
        try {
            if (key == null || value == null) {
                System.err.println("【Redis错误】key或value为null: key=" + key + ", value=" + value);
                return false;
            }
            redisTemplate.opsForValue().set(key, value, expireSeconds, TimeUnit.SECONDS);
            System.out.println("【Redis设置成功】key=" + key + ", expireSeconds=" + expireSeconds);
            return true;
        } catch (Exception e) {
            System.err.println("【Redis设置失败】key=" + key + ", 异常信息: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 获取缓存
     */
    public Object get(String key) {
        try {
            if (key == null) {
                System.err.println("【Redis错误】获取时key为null");
                return null;
            }
            Object value = redisTemplate.opsForValue().get(key);
            if (value != null) {
                System.out.println("【Redis获取成功】key=" + key + ", value类型=" + value.getClass().getSimpleName());
            } else {
                System.out.println("【Redis获取】key=" + key + " 不存在");
            }
            return value;
        } catch (Exception e) {
            System.err.println("【Redis获取失败】key=" + key + ", 异常信息: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 删除缓存
     */
    public boolean delete(String key) {
        try {
            if (key == null) {
                System.err.println("【Redis错误】删除时key为null");
                return false;
            }
            Boolean result = redisTemplate.delete(key);
            System.out.println("【Redis删除】key=" + key + ", 结果=" + (result != null && result ? "成功" : "未找到"));
            return result != null && result;
        } catch (Exception e) {
            System.err.println("【Redis删除失败】key=" + key + ", 异常信息: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 检查缓存是否存在
     */
    public boolean hasKey(String key) {
        try {
            if (key == null) {
                System.err.println("【Redis错误】检查时key为null");
                return false;
            }
            Boolean exists = redisTemplate.hasKey(key);
            System.out.println("【Redis检查】key=" + key + ", 存在=" + (exists != null && exists));
            return exists != null && exists;
        } catch (Exception e) {
            System.err.println("【Redis检查失败】key=" + key + ", 异常信息: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}