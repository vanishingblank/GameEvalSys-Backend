package com.eval.gameeval.util;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.lionsoul.ip2region.xdb.Searcher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class IpLocationService {

    private final ResourceLoader resourceLoader;

    @Value("${ip2region.xdb-path:classpath:ip2region.xdb}")
    private String xdbPath;

    private Searcher searcher;

    public IpLocationService(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    public void init() {
        try {
            Resource resource = resourceLoader.getResource(xdbPath);
            if (!resource.exists()) {
                log.warn("ip2region xdb not found: {}", xdbPath);
                return;
            }
            try (InputStream inputStream = resource.getInputStream()) {
                byte[] content = inputStream.readAllBytes();
                this.searcher = Searcher.newWithBuffer(content);
            }
        } catch (Exception e) {
            log.warn("Failed to init ip2region searcher", e);
            this.searcher = null;
        }
    }

    public String lookup(String ip) {
        if (ip == null || ip.trim().isEmpty()) {
            return null;
        }
        if (searcher == null) {
            return null;
        }
        try {
            String region = searcher.search(ip.trim());
            return normalizeRegion(region);
        } catch (Exception e) {
            log.debug("ip2region lookup failed: ip={}", ip, e);
            return null;
        }
    }

    private String normalizeRegion(String region) {
        if (region == null || region.trim().isEmpty()) {
            return null;
        }
        String[] parts = region.split("\\|");
        List<String> valid = new ArrayList<>();
        for (String part : parts) {
            if (part == null) {
                continue;
            }
            String text = part.trim();
            if (text.isEmpty() || "0".equals(text)) {
                continue;
            }
            valid.add(text);
        }
        if (valid.isEmpty()) {
            return null;
        }
        return String.join("/", valid);
    }
}
