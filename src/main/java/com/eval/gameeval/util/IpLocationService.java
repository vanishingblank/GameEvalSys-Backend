package com.eval.gameeval.util;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.lionsoul.ip2region.service.ConfigBuilder;
import org.lionsoul.ip2region.service.Config;
import org.lionsoul.ip2region.service.Ip2Region;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class IpLocationService {

    private final ResourceLoader resourceLoader;

    @Value("${ip2region.xdb-paths:classpath:/ip2region/ip2region.xdb,classpath:/ip2region/ip2region_v6.xdb}")
    private String xdbPaths;

    private Ip2Region ip2Region;

    public IpLocationService(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    public void init() {
        String[] paths = splitXdbPaths(xdbPaths);
        String v4Path = paths.length > 0 ? paths[0] : null;
        String v6Path = paths.length > 1 ? paths[1] : null;

        log.info("Initializing ip2region, v4XdbPath={}, v6XdbPath={}", v4Path, v6Path);

        try (InputStream v4Stream = openXdbStream(v4Path);
             InputStream v6Stream = openXdbStream(v6Path)) {
            Config v4Config = buildConfig(v4Stream, false);
            Config v6Config = buildConfig(v6Stream, true);

            if (v4Config == null && v6Config == null) {
                log.warn("No ip2region config initialized, xdbPaths={}", xdbPaths);
                ip2Region = null;
                return;
            }

            ip2Region = Ip2Region.create(v4Config, v6Config);
            log.info("ip2region initialized, v4Loaded={}, v6Loaded={}, v4XdbPath={}, v6XdbPath={}",
                    v4Config != null, v6Config != null, v4Path, v6Path);
        } catch (Exception e) {
            log.warn("Failed to init ip2region service, xdbPaths={}", xdbPaths, e);
            ip2Region = null;
        }
    }

    public String lookup(String ip) {
        String normalizedIp = normalizeIp(ip);
        if (normalizedIp == null) {
            return null;
        }
        if (ip2Region == null) {
            return null;
        }

        try {
            String region = ip2Region.search(normalizedIp);
            return normalizeRegion(region);
        } catch (Exception e) {
            log.debug("ip2region lookup failed: ip={}", normalizedIp, e);
            return null;
        }
    }

    private String[] splitXdbPaths(String paths) {
        if (paths == null || paths.trim().isEmpty()) {
            return new String[0];
        }
        String[] rawPaths = paths.split(",");
        List<String> result = new ArrayList<>();
        for (String path : rawPaths) {
            if (path == null) {
                continue;
            }
            String trimmed = path.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result.toArray(new String[0]);
    }

    private InputStream openXdbStream(String xdbPath) throws IOException {
        if (xdbPath == null || xdbPath.trim().isEmpty()) {
            return null;
        }
        Resource resource = resourceLoader.getResource(xdbPath.trim());
        if (!resource.exists()) {
            log.warn("ip2region xdb not found: {}", xdbPath);
            return null;
        }
        return resource.getInputStream();
    }

    private Config buildConfig(InputStream inputStream, boolean ipv6) {
        if (inputStream == null) {
            return null;
        }
        ConfigBuilder configBuilder = Config.custom()
                .setCachePolicy(Config.BufferCache)
                .setXdbInputStream(inputStream);
        try {
            return ipv6 ? configBuilder.asV6() : configBuilder.asV4();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build ip2region config", e);
        }
    }

    private String normalizeIp(String ip) {
        if (ip == null) {
            return null;
        }
        String text = ip.trim();
        if (text.isEmpty()) {
            return null;
        }
        if (text.startsWith("[") && text.endsWith("]") && text.length() > 2) {
            text = text.substring(1, text.length() - 1);
        }

        int zoneIndex = text.indexOf('%');
        if (zoneIndex > 0) {
            text = text.substring(0, zoneIndex);
        }

        return text;
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
