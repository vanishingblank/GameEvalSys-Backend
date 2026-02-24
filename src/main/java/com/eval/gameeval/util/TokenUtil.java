package com.eval.gameeval.util;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class TokenUtil {
    private static final String BEARER_PREFIX = "Bearer ";
    private static final int BEARER_PREFIX_LENGTH = BEARER_PREFIX.length();

    public String generateToken(){
        return UUID.randomUUID().toString().replace("-","");
    }

    /**
     * 从Authorization header中提取token
     * @param authorization Authorization header值
     * @return 提取的token
     */
    public static String extractToken(String authorization) {
        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            return authorization.substring(BEARER_PREFIX_LENGTH);
        }
        return authorization;
    }

    /**
     * 验证是否为有效的Bearer token格式
     * @param authorization Authorization header值
     * @return 是否为有效的Bearer token
     */
    public static boolean isValidBearerToken(String authorization) {
        return authorization != null && authorization.startsWith(BEARER_PREFIX);
    }

    /**
     * 获取完整的Bearer token字符串
     * @param token 纯token值
     * @return Bearer {token}格式
     */
    public static String getBearerToken(String token) {
        return BEARER_PREFIX + token;
    }


}
