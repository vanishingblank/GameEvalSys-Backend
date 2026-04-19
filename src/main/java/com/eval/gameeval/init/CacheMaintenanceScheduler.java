package com.eval.gameeval.init;

import com.eval.gameeval.service.impl.ProjectServiceImpl;
import com.eval.gameeval.service.impl.ProjectStatisticsServiceImpl;
import com.eval.gameeval.service.impl.ScoringStandardServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CacheMaintenanceScheduler {

    private final ProjectServiceImpl projectService;
    private final ScoringStandardServiceImpl scoringStandardService;
    private final ProjectStatisticsServiceImpl projectStatisticsService;

    public CacheMaintenanceScheduler(ProjectServiceImpl projectService,
                                     ScoringStandardServiceImpl scoringStandardService,
                                     ProjectStatisticsServiceImpl projectStatisticsService) {
        this.projectService = projectService;
        this.scoringStandardService = scoringStandardService;
        this.projectStatisticsService = projectStatisticsService;
    }

    @Scheduled(
            fixedDelayString = "${app.cache.reconcile.fixed-delay-ms:120000}",
            initialDelayString = "${app.cache.reconcile.initial-delay-ms:30000}"
    )
    public void reconcileProjectStatusAndInvalidateCaches() {
        try {
            projectService.reconcileProjectStatusesByScheduler();
        } catch (Exception e) {
            log.error("定时任务异常: 项目状态纠偏", e);
        }
    }

    @Scheduled(
            fixedDelayString = "${app.cache.warmup.fixed-delay-ms:300000}",
            initialDelayString = "${app.cache.warmup.initial-delay-ms:45000}"
    )
    public void warmupGlobalHotCaches() {
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
    }
}
