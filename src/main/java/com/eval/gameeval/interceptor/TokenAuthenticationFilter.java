package com.eval.gameeval.interceptor;

import com.eval.gameeval.util.RedisToken;
import jakarta.annotation.Resource;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


@Slf4j
@Component
public class TokenAuthenticationFilter extends OncePerRequestFilter {

    @Resource
    private RedisToken redisToken;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

//        String authorization = request.getHeader("Authorization");

        try {
            String authorization = request.getHeader("Authorization");

            if (isValidBearerToken(authorization)) {
                processAuthentication(authorization);
            }

            filterChain.doFilter(request, response);
        }

        //filterChain.doFilter(request, response);
        catch (Exception e) {
            log.error("Token authentication failed", e);
            SecurityContextHolder.clearContext();
            filterChain.doFilter(request, response);
        }

//        if (authorization != null && authorization.startsWith("Bearer ")) {
//            String token = authorization.substring(7);
//
//            if (redisUtil.validateToken(token)) {
//
//                Long userId = redisUtil.getUserIdByToken(token);
//                if (userId != null) {
//
//                    UsernamePasswordAuthenticationToken authentication =
//                            new UsernamePasswordAuthenticationToken(
//                                    userId,
//                                    null,
//                                    List.of()
//                            );
//
//                    SecurityContextHolder.getContext()
//                            .setAuthentication(authentication);
//                }
//            }
//        }
//
//        filterChain.doFilter(request, response);
    }

    private boolean isValidBearerToken(String authorization) {
        return authorization != null && authorization.startsWith("Bearer ");
    }

    private void processAuthentication(String authorization) {
        String token = authorization.substring(7);

        if (redisToken.validateToken(token)) {
            Long userId = redisToken.getUserIdByToken(token);
            String role = redisToken.getRoleByToken(token);
            if (userId != null) {
                setAuthentication(userId, role);
            } else {
                log.warn("User ID not found for token");
            }
        } else {
            log.debug("Invalid token");
        }
    }

    private void setAuthentication(Long userId, String role) {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        if (role != null && !role.trim().isEmpty()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        }
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        userId,
                        null,
                        authorities
                );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        log.debug("Authentication set for user ID: {}", userId);
    }
}
