package com.eval.gameeval.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    public PasswordEncoder passwordEncoder(){
        return new BCryptPasswordEncoder();
    }
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 禁用CSRF（前后端分离必须）
                .csrf(AbstractHttpConfigurer::disable)

                // 配置授权规则
                .authorizeHttpRequests(auth -> auth
                        // 放行不需要认证的接口
                        .requestMatchers(
                                "/user/login",
                                "/user/logout"
//                                "/user/register"
                        ).permitAll()

                        // 其他所有接口都需要认证
                        .anyRequest().authenticated()
                )

                // 禁用表单登录（使用Token认证）
                .formLogin(AbstractHttpConfigurer::disable)

                // 禁用HTTP Basic认证
                .httpBasic(AbstractHttpConfigurer::disable);

        return http.build();
    }
}
