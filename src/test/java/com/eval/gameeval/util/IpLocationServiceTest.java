package com.eval.gameeval.util;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitConfig(IpLocationServiceTest.TestConfig.class)
@TestPropertySource(properties = "ip2region.xdb-paths=classpath:/ip2region/ip2region.xdb,classpath:/ip2region/ip2region_v6.xdb")
public class IpLocationServiceTest {

    @Autowired
    private IpLocationService ipLocationService;

    @Test
    void lookupKnownIpv6ShouldReturnRegion() {
        String ip = "2409:893d:2a0d:a736:cc65:67ff:feba:f115";
        String region = ipLocationService.lookup(ip);
        System.out.println("lookup('" + ip + "') -> " + region);
        assertThat(region).as("ip2region should return a non-empty region for known IPv6").isNotBlank();
    }

    @Configuration
    @Import(IpLocationService.class)
    static class TestConfig {
    }
}
