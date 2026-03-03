package com.eval.gameeval.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.io.IOException;
import java.util.List;

@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Autowired(required = false)
    private List<HandlerMapping> handlerMappings;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {

        // 检查请求的路由是否存在
        if (!isRouteExists(request)) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("""
                {
                  "code": 404,
                  "message": "资源不存在",
                  "data": null
                }
            """);
            return;
        }

        // 如果路由存在但认证失败，返回401
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");

        response.getWriter().write("""
            {
              "code": 401,
              "message": "未登录或登录已过期",
              "data": null
            }
        """);
    }

    /**
     * 检查请求的路由是否存在
     */
    private boolean isRouteExists(HttpServletRequest request) {
        if (handlerMappings == null || handlerMappings.isEmpty()) {
            return true; // 如果没有找到HandlerMappings，默认认为路由存在
        }

        try {
            for (HandlerMapping handlerMapping : handlerMappings) {
                // 只跳过父级的默认HandlerMapping（比如ResourceHandlerMapping）
                if (!(handlerMapping instanceof RequestMappingHandlerMapping)) {
                    continue;
                }

                try {
                    Object handler = handlerMapping.getHandler(request);
                    if (handler != null) {
                        return true; // 找到了匹配的handler，路由存在
                    }
                } catch (Exception e) {
                    // 继续检查其他HandlerMapping
                }
            }
            return false; // 没有找到任何匹配的handler，路由不存在
        } catch (Exception e) {
            return true; // 发生异常时，默认认为路由存在
        }
    }
}

