package com.eval.gameeval.init;

import com.eval.gameeval.mapper.UserMapper;
import com.eval.gameeval.models.entity.User;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Locale;

@Component
@Slf4j
@ConditionalOnProperty(name = "app.admin.init.enabled", havingValue = "true", matchIfMissing = true)
public class AdminInitializer implements ApplicationRunner {

    @Resource
    private UserMapper userMapper;

    @Resource
    private PasswordEncoder passwordEncoder;

    @Value("${app.admin.init.username:}")
    private String username;

    @Value("${app.admin.init.password:}")
    private String rawPassword;

    @Value("${app.admin.init.password-hash:}")
    private String passwordHash;

    @Value("${app.admin.init.name:System Admin}")
    private String name;

    @Value("${app.admin.init.role:super_admin}")
    private String role;

    @Value("${app.admin.init.is-enabled:true}")
    private boolean isEnabled;

    @Override
    public void run(ApplicationArguments args) {
        Long userCount = userMapper.selectCount(null);
        if (userCount != null && userCount > 0) {
            log.info("跳过初始账号创建：sys_user 已有 {} 条记录", userCount);
            return;
        }

        if (!StringUtils.hasText(username)) {
            log.warn("跳过初始账号创建：app.admin.init.username 为空");
            return;
        }

        String encodedPassword = resolvePassword();
        if (!StringUtils.hasText(encodedPassword)) {
            log.warn("跳过初始账号创建：未提供有效密码（app.admin.init.password 或 app.admin.init.password-hash）");
            return;
        }

        User admin = new User();
        LocalDateTime now = LocalDateTime.now();

        admin.setUsername(username.trim());
        admin.setPassword(encodedPassword);
        admin.setName(StringUtils.hasText(name) ? name.trim() : username.trim());
        admin.setRole(normalizeRole(role));
        admin.setIsEnabled(isEnabled);
        admin.setCreateTime(now);
        admin.setUpdateTime(now);

        try {
            userMapper.insertUser(admin);
            log.info("初始账号创建成功：username={}, role={}", admin.getUsername(), admin.getRole());
        } catch (DuplicateKeyException ex) {
            // 多实例并发启动时，可能有其他实例先插入同名账号
            log.info("初始账号已存在（可能由其他实例创建），跳过创建：username={}", admin.getUsername());
        }
    }

    private String resolvePassword() {
        if (StringUtils.hasText(passwordHash)) {
            String hash = passwordHash.trim();
            if (isBcryptHash(hash)) {
                return hash;
            }
            log.warn("app.admin.init.password-hash 不是有效 BCrypt 哈希，格式应以 $2a$/$2b$/$2y$ 开头");
            return null;
        }

        if (StringUtils.hasText(rawPassword)) {
            return passwordEncoder.encode(rawPassword.trim());
        }

        return null;
    }

    private String normalizeRole(String value) {
        if (!StringUtils.hasText(value)) {
            return "super_admin";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isBcryptHash(String value) {
        return value.startsWith("$2a$") || value.startsWith("$2b$") || value.startsWith("$2y$");
    }

}
