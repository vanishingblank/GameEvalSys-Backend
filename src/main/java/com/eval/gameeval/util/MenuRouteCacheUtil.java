package com.eval.gameeval.util;

import com.eval.gameeval.models.VO.RouteNodeVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 菜单路由缓存工具
 */
@Component
public class MenuRouteCacheUtil {

    @Resource
    private RedisBaseUtil redisBaseUtil;

    public List<RouteNodeVO> getCachedRoutes(String roleCode, Long menuVersion) {
        String key = RedisKeyUtil.buildMenuRoutesKey(roleCode, menuVersion);
        Object cache = redisBaseUtil.get(key);
        if (cache == null) {
            return null;
        }
        @SuppressWarnings("unchecked")
        List<RouteNodeVO> routes = (List<RouteNodeVO>) cache;
        return routes;
    }

    public void cacheRoutes(String roleCode, Long menuVersion, List<RouteNodeVO> routes) {
        String key = RedisKeyUtil.buildMenuRoutesKey(roleCode, menuVersion);
        redisBaseUtil.set(key, routes, RedisKeyUtil.MENU_ROUTES_TTL);
    }

    public Long getMenuRoutesVersion() {
        String key = RedisKeyUtil.MENU_ROUTES_VERSION_KEY;
        Object value = redisBaseUtil.get(key);
        if (value == null) {
            redisBaseUtil.setPersistent(key, 1L);
            return 1L;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (Exception e) {
            redisBaseUtil.setPersistent(key, 1L);
            return 1L;
        }
    }

    public Long bumpMenuRoutesVersion() {
        Long value = redisBaseUtil.increment(RedisKeyUtil.MENU_ROUTES_VERSION_KEY);
        if (value == null) {
            redisBaseUtil.setPersistent(RedisKeyUtil.MENU_ROUTES_VERSION_KEY, 1L);
            return 1L;
        }
        return value;
    }
}
