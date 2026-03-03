package com.eval.gameeval.interceptor;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class NotFoundFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        filterChain.doFilter(request, response);

        // 如果响应状态是 404，直接返回，不再经过 Security 处理
        if (response.getStatus() == 404) {
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("""
                {
                  "code": 404,
                  "message": "资源不存在",
                  "data": null
                }
            """);
        }
    }
}