package com.eval.gameeval.init;

import com.eval.gameeval.service.impl.ProjectServiceImpl;
import com.eval.gameeval.service.impl.ProjectStatisticsServiceImpl;
import com.eval.gameeval.service.impl.ScoringStandardServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;


@Slf4j
@Component
public class CacheMaintenanceScheduler {

    private final ProjectServiceImpl projectService;
    private final ScoringStandardServiceImpl scoringStandardService;
    private final ProjectStatisticsServiceImpl projectStatisticsService;
    private final OverviewWarmupService overviewWarmupService;

    @Value("${app.cache.scheduler.enabled:true}")
    private boolean schedulerEnabled;

    @Value("${app.cache.scheduler.reconcile.enabled:true}")
    private boolean reconcileEnabled;

    @Value("${app.cache.scheduler.warmup.enabled:true}")
    private boolean warmupEnabled;

    public CacheMaintenanceScheduler(ProjectServiceImpl projectService,
                                     ScoringStandardServiceImpl scoringStandardService,
                                     ProjectStatisticsServiceImpl projectStatisticsService,
                                     OverviewWarmupService overviewWarmupService) {
        this.projectService = projectService;
        this.scoringStandardService = scoringStandardService;
        this.projectStatisticsService = projectStatisticsService;
        this.overviewWarmupService = overviewWarmupService;
    }


    @Scheduled(
            fixedDelayString = "${app.cache.scheduler.reconcile.fixed-delay-ms:120000}",
            initialDelayString = "${app.cache.scheduler.reconcile.initial-delay-ms:30000}"
    )
    public void reconcileProjectStatusAndInvalidateCaches() {
        if (!schedulerEnabled || !reconcileEnabled) {
            return;
        }
        try {
            projectService.reconcileProjectStatusesByScheduler();
        } catch (Exception e) {
            log.error("定时任务异常: 项目状态纠偏", e);
        }
    }

    @Scheduled(
            fixedDelayString = "${app.cache.scheduler.warmup.fixed-delay-ms:300000}",
            initialDelayString = "${app.cache.scheduler.warmup.initial-delay-ms:45000}"
    )
    public void warmupGlobalHotCaches() {
        if (!schedulerEnabled || !warmupEnabled) {
            return;
        }
        try {
            projectService.warmupDefaultProjectListCache();
        } catch (Exception e) {
            log.error("定时任务异常: 预热项目默认列表缓存", e);
        }

        try {
            scoringStandardService.warmupStandardListCache();
        } catch (Exception e) {
            log.error("定时任务异常: 预热打分标准列表缓存", e);
        }

        try {
            projectStatisticsService.warmupPlatformStatisticsCache();
        } catch (Exception e) {
            log.error("定时任务异常: 预热平台统计缓存", e);
        }

        try {
            overviewWarmupService.warmupOverviewCaches();
        } catch (Exception e) {
            log.error("定时任务异常: 预热概览缓存", e);
        }
    }
}


