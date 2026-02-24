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

    // ========== 项目缓存 ==========
    public static final String PROJECT_LIST_KEY_PREFIX = "project:list:";
    public static final String PROJECT_DETAIL_KEY_PREFIX = "project:detail:";
    public static final String PROJECT_AUTHORIZED_KEY_PREFIX = "project:authorized:";
    public static final String PROJECT_GROUPS_KEY_PREFIX = "project:groups:";

    public static final long PROJECT_LIST_TTL = 300;      // 5分钟（分页数据变化频繁）
    public static final long PROJECT_DETAIL_TTL = 3600;   // 60分钟
    public static final long PROJECT_AUTHORIZED_TTL = 600; // 10分钟（用户权限可能变化）
    public static final long PROJECT_GROUPS_TTL = 1800;   // 30分钟

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

    // ========== 项目键构建==========
    /**
     * 构建项目列表缓存键（含分页参数）
     * 格式: project:list:{status}:{isEnabled}:{page}:{size}
     */
    public static String buildProjectListKey(String status, Boolean isEnabled, int page, int size) {
        StringBuilder key = new StringBuilder(PROJECT_LIST_KEY_PREFIX);
        key.append(status != null ? status : "all").append(":");
        key.append(isEnabled != null ? isEnabled : "all").append(":");
        key.append(page).append(":").append(size);
        return key.toString();
    }

    /**
     * 构建项目详情缓存键
     */
    public static String buildProjectDetailKey(Long projectId) {
        return PROJECT_DETAIL_KEY_PREFIX + projectId;
    }

    /**
     * 构建项目空值缓存键
     */
    public static String buildProjectNullKey(Long projectId) {
        return PROJECT_DETAIL_KEY_PREFIX + projectId + ":null";
    }

    /**
     * 构建用户授权项目缓存键
     */
    public static String buildAuthorizedProjectsKey(Long userId) {
        return PROJECT_AUTHORIZED_KEY_PREFIX + userId;
    }

    /**
     * 构建项目小组列表缓存键
     */
    public static String buildProjectGroupsKey(Long projectId) {
        return PROJECT_GROUPS_KEY_PREFIX + projectId;
    }
}