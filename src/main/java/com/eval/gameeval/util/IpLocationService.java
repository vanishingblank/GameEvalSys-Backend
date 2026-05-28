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

    private static final String IPV6_MAPPED_IPV4_PREFIX = "::ffff:";

    private final ResourceLoader resourceLoader;

    @Value("${ip2region.xdb-paths:classpath:/ip2region/ip2region.xdb,classpath:/ip2region/ip2region_v6.xdb}")
    private String xdbPaths;

    private final List<Searcher> searchers = new ArrayList<>();

    public IpLocationService(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    public void init() {
        searchers.clear();
        for (String path : xdbPaths.split(",")) {
            String trimmedPath = path == null ? null : path.trim();
            if (trimmedPath == null || trimmedPath.isEmpty()) {
                continue;
            }
            try {
                Resource resource = resourceLoader.getResource(trimmedPath);
                if (!resource.exists()) {
                    log.warn("ip2region xdb not found: {}", trimmedPath);
                    continue;
                }
                try (InputStream inputStream = resource.getInputStream()) {
                    byte[] content = inputStream.readAllBytes();
                    searchers.add(Searcher.newWithBuffer(content));
                }
            } catch (Exception e) {
                log.warn("Failed to init ip2region searcher: {}", trimmedPath, e);
            }
        }
        if (searchers.isEmpty()) {
            log.warn("No ip2region searcher initialized, xdbPaths={}", xdbPaths);
        }
    }

    public String lookup(String ip) {
        String normalizedIp = normalizeIp(ip);
        if (normalizedIp == null) {
            return null;
        }
        if (searchers.isEmpty()) {
            return null;
        }

        for (Searcher searcher : searchers) {
            try {
                String region = searcher.search(normalizedIp);
                String normalizedRegion = normalizeRegion(region);
                if (normalizedRegion != null) {
                    return normalizedRegion;
                }
            } catch (Exception e) {
                log.debug("ip2region lookup failed: ip={}", normalizedIp, e);
            }
        }
        return null;
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

        if (text.regionMatches(true, 0, IPV6_MAPPED_IPV4_PREFIX, 0, IPV6_MAPPED_IPV4_PREFIX.length())) {
            String mappedIpv4 = text.substring(IPV6_MAPPED_IPV4_PREFIX.length());
            if (!mappedIpv4.isEmpty()) {
                return mappedIpv4;
            }
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
