package com.eval.gameeval.util;

/**
 * Redis 缓存键定
 */
public final class RedisKeyUtil {

    // ========== Token 相关 ==========
    public static final String TOKEN_PREFIX = "auth:token:";
    public static final long TOKEN_EXPIRE_SECONDS = 14400; // 4小时

    // ========== 打分标准缓存 ==========
    public static final String STANDARD_LIST_KEY = "scoring:standard:list";
    public static final String STANDARD_DETAIL_KEY_PREFIX = "scoring:standard:";
    public static final String STANDARD_NULL_SUFFIX = ":null";

    public static final long STANDARD_LIST_TTL = 1800;   // 30分钟
    public static final long STANDARD_DETAIL_TTL = 3600; // 60分钟
    public static final long NULL_TTL = 300;             // 5分钟

    // ========== 私有构造函数（工具类不可实例化）==========
    private RedisKeyUtil() {
        throw new UnsupportedOperationException("工具类不可实例化");
    }

    /**
     * 构建标准详情缓存键
     */
    public static String buildStandardDetailKey(Long standardId) {
        return STANDARD_DETAIL_KEY_PREFIX + standardId;
    }

    /**
     * 构建空值缓存键
     */
    public static String buildStandardNullKey(Long standardId) {
        return STANDARD_DETAIL_KEY_PREFIX + standardId + STANDARD_NULL_SUFFIX;
    }

    /**
     * 构建 Token 缓存键
     */
    public static String buildTokenKey(String token) {
        return TOKEN_PREFIX + token;
    }
}