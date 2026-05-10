package com.eval.gameeval.service.impl;

import com.eval.gameeval.models.VO.SystemMonitorVO;
import com.eval.gameeval.service.ISystemMonitorService;
import com.eval.gameeval.logging.RecentWarnErrorLogStore;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.sql.DataSource;
import java.io.File;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SystemMonitorServiceImpl implements ISystemMonitorService {

    private static final long SSE_TIMEOUT_MS = 0L;
    private static final long DEFAULT_MONITOR_REFRESH_INTERVAL_MS = 2000L;
    private static final long MIN_MONITOR_REFRESH_INTERVAL_MS = 1000L;
    private static final int DEFAULT_LOG_LIMIT = 5;
    private static final int MAX_LOG_LIMIT = 20;

    private final ScheduledExecutorService sseExecutor = Executors.newScheduledThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "system-monitor-sse");
        thread.setDaemon(true);
        return thread;
    });

    private static final Pattern JDBC_URL_PATTERN = Pattern.compile("jdbc:(?<type>[^:]+)://(?<host>[^:/?]+)(:(?<port>\\d+))?/(?<database>[^?]+)");

    @Resource
    private DataSource dataSource;

    @Resource
    private RedisConnectionFactory redisConnectionFactory;

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${spring.datasource.username:}")
    private String datasourceUsername;

    @Value("${spring.datasource.driver-class-name:}")
    private String datasourceDriverClassName;

    @Value("${spring.datasource.hikari.maximum-pool-size:20}")
    private Integer datasourceMaximumPoolSize;

    @Value("${spring.datasource.hikari.minimum-idle:5}")
    private Integer datasourceMinimumIdle;

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private Integer redisPort;

    @Value("${spring.data.redis.database:0}")
    private Integer redisDatabase;

    @Value("${spring.data.redis.timeout:5000ms}")
    private String redisTimeout;

    @Value("${spring.data.redis.lettuce.pool.max-active:8}")
    private Integer redisPoolMaxActive;

    @Value("${spring.data.redis.lettuce.pool.max-idle:8}")
    private Integer redisPoolMaxIdle;

    @Value("${spring.data.redis.lettuce.pool.min-idle:0}")
    private Integer redisPoolMinIdle;

    @Value("${spring.data.redis.lettuce.pool.max-wait:-1ms}")
    private String redisPoolMaxWait;

    @Value("${server.port:8080}")
    private Integer serverPort;

    @Value("${app.time-zone:Asia/Shanghai}")
    private String appTimeZone;

    @Value("${app.version:0.0.1-SNAPSHOT}")
    private String appVersion;

    @Value("${app.monitor.refresh-interval-ms:2000}")
    private Long monitorRefreshIntervalMs;

    @Value("${app.monitor.refresh-min-interval-ms:1000}")
    private Long monitorRefreshMinIntervalMs;

    @Value("${app.cache.scheduler.enabled:true}")
    private Boolean cacheSchedulerEnabled;

    @Override
    public SystemMonitorVO getDashboard() {
        LocalDateTime now = LocalDateTime.now(getZoneId());
        SystemMonitorVO dashboard = new SystemMonitorVO();
        dashboard.setGeneratedAt(now)
                .setOverview(buildOverview())
                .setHealth(buildHealth())
                .setDatasource(buildDataSource())
                .setRedis(buildRedis())
                .setJvm(buildJvm())
                .setOs(buildOs())
                .setConfig(buildConfig())
                .setLogs(getLogs(DEFAULT_LOG_LIMIT));
        return dashboard;
    }

    @Override
    public SystemMonitorVO.OverviewVO getOverview() {
        return buildOverview();
    }

    @Override
    public SystemMonitorVO.HealthVO getHealth() {
        return buildHealth();
    }

    @Override
    public SystemMonitorVO.DataSourceVO getDataSource() {
        return buildDataSource();
    }

    @Override
    public SystemMonitorVO.RedisVO getRedis() {
        return buildRedis();
    }

    @Override
    public SystemMonitorVO.JvmVO getJvm() {
        return buildJvm();
    }

    @Override
    public SystemMonitorVO.OsVO getOs() {
        return buildOs();
    }

    @Override
    public SystemMonitorVO.ConfigVO getConfig() {
        return buildConfig();
    }

    @Override
    public List<SystemMonitorVO.LogVO> getLogs() {
        return getLogs(DEFAULT_LOG_LIMIT);
    }

    @Override
    public List<SystemMonitorVO.LogVO> getLogs(int limit) {
        int safeLimit = normalizeLogLimit(limit);
        return RecentWarnErrorLogStore.snapshot(safeLimit).stream()
                .map(this::toLogVo)
                .toList();
    }

    @Override
    public SseEmitter openStream() {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        AtomicLong tickCounter = new AtomicLong(0L);
        AtomicLong heartbeatCounter = new AtomicLong(0L);
        ScheduledFuture<?>[] futureHolder = new ScheduledFuture<?>[1];

        Runnable pushTask = () -> {
            try {
                long tick = tickCounter.incrementAndGet();
                if (tick == 1L) {
                    sendEvent(emitter, "dashboard", getDashboard());
                    sendEvent(emitter, "overview", getOverview());
                    sendEvent(emitter, "health", getHealth());
                    sendEvent(emitter, "datasource", getDataSource());
                    sendEvent(emitter, "redis", getRedis());
                    sendEvent(emitter, "jvm", getJvm());
                    sendEvent(emitter, "os", getOs());
                    sendEvent(emitter, "config", getConfig());
                    sendEvent(emitter, "logs", getLogs());
                    sendHeartbeat(emitter, heartbeatCounter.incrementAndGet());
                    return;
                }

                sendEvent(emitter, "health", getHealth());
                sendEvent(emitter, "jvm", getJvm());
                sendEvent(emitter, "os", getOs());
                sendHeartbeat(emitter, heartbeatCounter.incrementAndGet());

                if (tick % 6L == 0L) {
                    sendEvent(emitter, "datasource", getDataSource());
                    sendEvent(emitter, "redis", getRedis());
                    sendEvent(emitter, "config", getConfig());
                    sendEvent(emitter, "overview", getOverview());
                    sendEvent(emitter, "logs", getLogs());
                    sendEvent(emitter, "dashboard", getDashboard());
                }
            } catch (Exception ex) {
                cancelStream(futureHolder);
                try {
                    emitter.completeWithError(ex);
                } catch (Exception ignore) {
                }
            }
        };

        ScheduledFuture<?> future = sseExecutor.scheduleAtFixedRate(
                pushTask,
                0L,
                resolveMonitorRefreshIntervalMillis(),
                TimeUnit.MILLISECONDS
        );
        futureHolder[0] = future;

        emitter.onCompletion(() -> cancelStream(futureHolder));
        emitter.onTimeout(() -> {
            cancelStream(futureHolder);
            emitter.complete();
        });
        emitter.onError(throwable -> cancelStream(futureHolder));

        return emitter;
    }

    private SystemMonitorVO.OverviewVO buildOverview() {
        RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        return new SystemMonitorVO.OverviewVO()
                .setHostName(resolveHostName())
                .setHostAddress(resolveHostAddress())
                .setServerPort(serverPort)
                .setTimeZone(appTimeZone)
                .setAppVersion(appVersion)
                .setJavaVersion(System.getProperty("java.version"))
                .setJavaVendor(System.getProperty("java.vendor"))
                .setStartTime(Instant.ofEpochMilli(runtimeMXBean.getStartTime()).atZone(getZoneId()).toLocalDateTime())
                .setUptimeMillis(runtimeMXBean.getUptime())
                .setUptimeText(formatUptime(runtimeMXBean.getUptime()));
    }

    private SystemMonitorVO.HealthVO buildHealth() {
        SystemMonitorVO.DataSourceVO dataSourceVO = buildDataSource();
        SystemMonitorVO.RedisVO redisVO = buildRedis();
        boolean dataSourceUp = "UP".equals(dataSourceVO.getStatus());
        boolean redisUp = "UP".equals(redisVO.getStatus());
        String overallStatus = dataSourceUp && redisUp ? "UP" : (dataSourceUp || redisUp ? "DEGRADED" : "DOWN");
        String message = dataSourceUp && redisUp
                ? "数据库和 Redis 均正常"
                : "数据库或 Redis 存在异常";

        return new SystemMonitorVO.HealthVO()
                .setOverallStatus(overallStatus)
                .setDatasourceStatus(dataSourceVO.getStatus())
                .setRedisStatus(redisVO.getStatus())
                .setMessage(message);
    }

    private SystemMonitorVO.DataSourceVO buildDataSource() {
        SystemMonitorVO.DataSourceVO vo = new SystemMonitorVO.DataSourceVO();
        ParsedJdbcUrl parsedJdbcUrl = parseJdbcUrl(datasourceUrl);
        vo.setJdbcType(parsedJdbcUrl.type())
                .setJdbcUrlMasked(maskJdbcUrl(parsedJdbcUrl))
                .setDriverClassName(datasourceDriverClassName)
                .setUsernameMasked(maskValue(datasourceUsername))
                .setMaximumPoolSize(datasourceMaximumPoolSize)
                .setMinimumIdle(datasourceMinimumIdle);

        try (Connection connection = dataSource.getConnection()) {
            boolean valid = connection.isValid(2);
            vo.setStatus(valid ? "UP" : "DOWN");
            vo.setMessage(valid ? "连接正常" : "连接校验失败");

            if (dataSource instanceof HikariDataSource hikariDataSource) {
                HikariPoolMXBean poolMXBean = hikariDataSource.getHikariPoolMXBean();
                if (poolMXBean != null) {
                    vo.setActiveConnections(poolMXBean.getActiveConnections())
                            .setIdleConnections(poolMXBean.getIdleConnections())
                            .setTotalConnections(poolMXBean.getTotalConnections())
                            .setThreadsAwaitingConnection(poolMXBean.getThreadsAwaitingConnection());
                }
            }
        } catch (SQLException ex) {
            vo.setStatus("DOWN");
            vo.setMessage(limitMessage(ex.getMessage()));
        }

        return vo;
    }

    private SystemMonitorVO.RedisVO buildRedis() {
        SystemMonitorVO.RedisVO vo = new SystemMonitorVO.RedisVO();
        vo.setHost(redisHost)
                .setPort(redisPort)
                .setDatabase(redisDatabase)
                .setTimeoutMillis(parseDurationMillis(redisTimeout))
                .setPoolMaxActive(redisPoolMaxActive)
                .setPoolMaxIdle(redisPoolMaxIdle)
                .setPoolMinIdle(redisPoolMinIdle)
                .setPoolMaxWaitMillis(parseDurationMillis(redisPoolMaxWait));

        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            String ping = connection.ping();
            boolean up = !isBlank(ping);
            vo.setStatus(up ? "UP" : "DOWN")
                    .setMessage(up ? "连接正常" : "Ping 返回空值")
                    .setPing(ping);
        } catch (Exception ex) {
            vo.setStatus("DOWN");
            vo.setMessage(limitMessage(ex.getMessage()));
        }

        return vo;
    }

    private SystemMonitorVO.JvmVO buildJvm() {
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();

        MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
        MemoryUsage nonHeapUsage = memoryMXBean.getNonHeapMemoryUsage();

        long gcCount = 0L;
        long gcTime = 0L;
        for (GarbageCollectorMXBean gcBean : gcBeans) {
            long count = gcBean.getCollectionCount();
            long time = gcBean.getCollectionTime();
            if (count > 0) {
                gcCount += count;
            }
            if (time > 0) {
                gcTime += time;
            }
        }

        return new SystemMonitorVO.JvmVO()
                .setHeapUsedBytes(heapUsage.getUsed())
                .setHeapCommittedBytes(heapUsage.getCommitted())
                .setHeapMaxBytes(heapUsage.getMax())
                .setNonHeapUsedBytes(nonHeapUsage.getUsed())
                .setNonHeapCommittedBytes(nonHeapUsage.getCommitted())
                .setThreadCount(threadMXBean.getThreadCount())
                .setDaemonThreadCount(threadMXBean.getDaemonThreadCount())
                .setPeakThreadCount(threadMXBean.getPeakThreadCount())
                .setGcCount(gcCount)
                .setGcTimeMillis(gcTime);
    }

    private SystemMonitorVO.OsVO buildOs() {
        java.lang.management.OperatingSystemMXBean baseBean = ManagementFactory.getOperatingSystemMXBean();
        SystemMonitorVO.OsVO vo = new SystemMonitorVO.OsVO();

        vo.setOsName(baseBean.getName())
                .setOsVersion(baseBean.getVersion())
                .setOsArch(baseBean.getArch())
                .setAvailableProcessors(baseBean.getAvailableProcessors());

        if (baseBean instanceof com.sun.management.OperatingSystemMXBean osBean) {
            long totalPhysicalMemory = osBean.getTotalPhysicalMemorySize();
            long freePhysicalMemory = osBean.getFreePhysicalMemorySize();
            long usedPhysicalMemory = Math.max(totalPhysicalMemory - freePhysicalMemory, 0L);

            long diskTotal = 0L;
            long diskFree = 0L;
            for (File root : File.listRoots()) {
                diskTotal += root.getTotalSpace();
                diskFree += root.getFreeSpace();
            }
            long diskUsed = Math.max(diskTotal - diskFree, 0L);

            vo.setSystemCpuLoadPercent(percent(osBean.getSystemCpuLoad()))
                    .setProcessCpuLoadPercent(percent(osBean.getProcessCpuLoad()))
                    .setTotalPhysicalMemoryBytes(totalPhysicalMemory)
                    .setFreePhysicalMemoryBytes(freePhysicalMemory)
                    .setUsedPhysicalMemoryBytes(usedPhysicalMemory)
                    .setMemoryUsagePercent(percent(totalPhysicalMemory == 0 ? -1 : (double) usedPhysicalMemory / totalPhysicalMemory))
                    .setDiskTotalBytes(diskTotal)
                    .setDiskFreeBytes(diskFree)
                    .setDiskUsedBytes(diskUsed)
                    .setDiskUsagePercent(percent(diskTotal == 0 ? -1 : (double) diskUsed / diskTotal));
        }

        return vo;
    }

    private SystemMonitorVO.ConfigVO buildConfig() {
        ParsedJdbcUrl parsedJdbcUrl = parseJdbcUrl(datasourceUrl);
        return new SystemMonitorVO.ConfigVO()
                .setDatasourceUrlMasked(maskJdbcUrl(parsedJdbcUrl))
                .setDatasourceHost(parsedJdbcUrl.host())
                .setDatasourcePort(parsedJdbcUrl.port())
                .setDatasourceDatabase(parsedJdbcUrl.database())
                .setDatasourceDriverClassName(datasourceDriverClassName)
                .setDatasourceUsernameMasked(maskValue(datasourceUsername))
                .setRedisHost(redisHost)
                .setRedisPort(redisPort)
                .setRedisDatabase(redisDatabase)
                .setRedisTimeoutMillis(parseDurationMillis(redisTimeout))
                .setRedisPoolMaxActive(redisPoolMaxActive)
                .setRedisPoolMaxIdle(redisPoolMaxIdle)
                .setRedisPoolMinIdle(redisPoolMinIdle)
                .setRedisPoolMaxWaitMillis(parseDurationMillis(redisPoolMaxWait))
                .setServerPort(serverPort)
                .setTimeZone(appTimeZone)
                .setCacheSchedulerEnabled(cacheSchedulerEnabled);
    }

    private ZoneId getZoneId() {
        try {
            return ZoneId.of(appTimeZone);
        } catch (Exception ex) {
            return ZoneOffset.ofHours(8);
        }
    }

    private String resolveHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException ex) {
            return "unknown";
        }
    }

    private String resolveHostAddress() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException ex) {
            return "unknown";
        }
    }

    private String formatUptime(long uptimeMillis) {
        long seconds = uptimeMillis / 1000;
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long remainingSeconds = seconds % 60;
        return String.format(Locale.ROOT, "%dd %dh %dm %ds", days, hours, minutes, remainingSeconds);
    }

    private String maskValue(String value) {
        if (isBlank(value)) {
            return "";
        }
        int length = value.length();
        if (length <= 2) {
            return "**";
        }
        StringBuilder builder = new StringBuilder();
        builder.append(value.charAt(0));
        builder.append("***");
        builder.append(value.charAt(length - 1));
        return builder.toString();
    }

    private String maskJdbcUrl(ParsedJdbcUrl parsedJdbcUrl) {
        if (parsedJdbcUrl.host() == null) {
            return datasourceUrl;
        }
        StringBuilder builder = new StringBuilder();
        builder.append("jdbc:");
        builder.append(parsedJdbcUrl.type());
        builder.append("://");
        builder.append(parsedJdbcUrl.host());
        if (parsedJdbcUrl.port() != null) {
            builder.append(":");
            builder.append(parsedJdbcUrl.port());
        }
        builder.append("/");
        builder.append(parsedJdbcUrl.database());
        return builder.toString();
    }

    private ParsedJdbcUrl parseJdbcUrl(String jdbcUrl) {
        if (isBlank(jdbcUrl)) {
            return new ParsedJdbcUrl("unknown", null, null, null);
        }
        Matcher matcher = JDBC_URL_PATTERN.matcher(jdbcUrl);
        if (!matcher.find()) {
            return new ParsedJdbcUrl("unknown", null, null, null);
        }
        String type = matcher.group("type");
        String host = matcher.group("host");
        Integer port = matcher.group("port") == null ? null : Integer.parseInt(matcher.group("port"));
        String database = matcher.group("database");
        return new ParsedJdbcUrl(type, host, port, database);
    }

    private Long parseDurationMillis(String duration) {
        if (isBlank(duration)) {
            return null;
        }
        String normalized = duration.trim().toLowerCase(Locale.ROOT);
        try {
            if (normalized.endsWith("ms")) {
                return Long.parseLong(normalized.substring(0, normalized.length() - 2));
            }
            if (normalized.endsWith("s")) {
                return Long.parseLong(normalized.substring(0, normalized.length() - 1)) * 1000L;
            }
            if (normalized.endsWith("m")) {
                return Long.parseLong(normalized.substring(0, normalized.length() - 1)) * 60_000L;
            }
            return Long.parseLong(normalized);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private BigDecimal percent(double value) {
        if (value < 0) {
            return null;
        }
        return BigDecimal.valueOf(value * 100.0d).setScale(2, RoundingMode.HALF_UP);
    }

    private String limitMessage(String message) {
        if (isBlank(message)) {
            return "未知错误";
        }
        return message.length() > 120 ? message.substring(0, 120) : message;
    }

    private void sendEvent(SseEmitter emitter, String eventName, Object data) throws Exception {
        emitter.send(SseEmitter.event().name(eventName).data(data));
    }

    private void sendHeartbeat(SseEmitter emitter, long sequence) throws Exception {
        Map<String, Object> heartbeat = new HashMap<>();
        heartbeat.put("sequence", sequence);
        heartbeat.put("timestamp", LocalDateTime.now(getZoneId()).toString());
        emitter.send(SseEmitter.event()
                .name("heartbeat")
                .data(heartbeat));
    }

    private void cancelStream(ScheduledFuture<?>[] futureHolder) {
        if (futureHolder != null && futureHolder.length > 0 && futureHolder[0] != null) {
            futureHolder[0].cancel(true);
        }
    }

    private int normalizeLogLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LOG_LIMIT;
        }
        return Math.min(limit, MAX_LOG_LIMIT);
    }

    private long resolveMonitorRefreshIntervalMillis() {
        long configured = monitorRefreshIntervalMs == null ? DEFAULT_MONITOR_REFRESH_INTERVAL_MS : monitorRefreshIntervalMs;
        long configuredMin = monitorRefreshMinIntervalMs == null ? MIN_MONITOR_REFRESH_INTERVAL_MS : monitorRefreshMinIntervalMs;
        long floor = Math.max(configuredMin, MIN_MONITOR_REFRESH_INTERVAL_MS);
        return Math.max(configured, floor);
    }

    private SystemMonitorVO.LogVO toLogVo(RecentWarnErrorLogStore.LogEntry entry) {
        return new SystemMonitorVO.LogVO()
                .setTime(Instant.ofEpochMilli(entry.timestampMillis()).atZone(getZoneId()).toLocalDateTime())
                .setLevel(entry.level())
                .setContent(entry.content());
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static final class ParsedJdbcUrl {
        private final String type;
        private final String host;
        private final Integer port;
        private final String database;

        private ParsedJdbcUrl(String type, String host, Integer port, String database) {
            this.type = type;
            this.host = host;
            this.port = port;
            this.database = database;
        }

        private String type() {
            return type;
        }

        private String host() {
            return host;
        }

        private Integer port() {
            return port;
        }

        private String database() {
            return database;
        }
    }
}