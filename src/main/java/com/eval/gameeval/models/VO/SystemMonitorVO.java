package com.eval.gameeval.models.VO;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Accessors(chain = true)
public class SystemMonitorVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime generatedAt;

    private OverviewVO overview;
    private HealthVO health;
    private DataSourceVO datasource;
    private RedisVO redis;
    private JvmVO jvm;
    private OsVO os;
    private ConfigVO config;
    private List<LogVO> logs;

    @Data
    @Accessors(chain = true)
    public static class OverviewVO implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private String hostName;
        private String hostAddress;
        private Integer serverPort;
        private String timeZone;
        private String appVersion;
        private String javaVersion;
        private String javaVendor;

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime startTime;

        private Long uptimeMillis;
        private String uptimeText;
    }

    @Data
    @Accessors(chain = true)
    public static class HealthVO implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private String overallStatus;
        private String datasourceStatus;
        private String redisStatus;
        private String message;
    }

    @Data
    @Accessors(chain = true)
    public static class DataSourceVO implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private String status;
        private String message;
        private String jdbcType;
        private String jdbcUrlMasked;
        private String driverClassName;
        private String usernameMasked;
        private Integer activeConnections;
        private Integer idleConnections;
        private Integer totalConnections;
        private Integer maximumPoolSize;
        private Integer minimumIdle;
        private Integer threadsAwaitingConnection;
    }

    @Data
    @Accessors(chain = true)
    public static class RedisVO implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private String status;
        private String message;
        private String host;
        private Integer port;
        private Integer database;
        private Long timeoutMillis;
        private Integer poolMaxActive;
        private Integer poolMaxIdle;
        private Integer poolMinIdle;
        private Long poolMaxWaitMillis;
        private String ping;
    }

    @Data
    @Accessors(chain = true)
    public static class JvmVO implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private Long heapUsedBytes;
        private Long heapCommittedBytes;
        private Long heapMaxBytes;
        private Long nonHeapUsedBytes;
        private Long nonHeapCommittedBytes;
        private Integer threadCount;
        private Integer daemonThreadCount;
        private Integer peakThreadCount;
        private Long gcCount;
        private Long gcTimeMillis;
    }

    @Data
    @Accessors(chain = true)
    public static class OsVO implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private BigDecimal systemCpuLoadPercent;
        private BigDecimal processCpuLoadPercent;
        private String osName;
        private String osVersion;
        private String osArch;
        private Integer availableProcessors;
        private Long totalPhysicalMemoryBytes;
        private Long freePhysicalMemoryBytes;
        private Long usedPhysicalMemoryBytes;
        private BigDecimal memoryUsagePercent;
        private Long diskTotalBytes;
        private Long diskFreeBytes;
        private Long diskUsedBytes;
        private BigDecimal diskUsagePercent;
    }

    @Data
    @Accessors(chain = true)
    public static class ConfigVO implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private String datasourceUrlMasked;
        private String datasourceHost;
        private Integer datasourcePort;
        private String datasourceDatabase;
        private String datasourceDriverClassName;
        private String datasourceUsernameMasked;
        private String redisHost;
        private Integer redisPort;
        private Integer redisDatabase;
        private Long redisTimeoutMillis;
        private Integer redisPoolMaxActive;
        private Integer redisPoolMaxIdle;
        private Integer redisPoolMinIdle;
        private Long redisPoolMaxWaitMillis;
        private Integer serverPort;
        private String timeZone;
        private Boolean cacheSchedulerEnabled;
    }

    @Data
    @Accessors(chain = true)
    public static class LogVO implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime time;
        private String level;
        private String content;
    }
}
