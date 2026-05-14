package com.eval.gameeval.init;

import com.eval.gameeval.mapper.ProjectMapper;
import com.eval.gameeval.mapper.ProjectStatisticsSummaryMapper;
import com.eval.gameeval.service.impl.ProjectStatisticsSummaryRebuildService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
@ConditionalOnProperty(name = "app.statistics.summary.backfill.enabled", havingValue = "true", matchIfMissing = true)
public class ProjectStatisticsSummaryInitializer implements ApplicationRunner {

    @Resource
    private ProjectMapper projectMapper;

    @Resource
    private ProjectStatisticsSummaryMapper summaryMapper;

    @Resource
    private ProjectStatisticsSummaryRebuildService summaryRebuildService;

    @Override
    public void run(ApplicationArguments args) {
        Long projectSummaryCount = summaryMapper.countProjectSummaryRows();
        Long groupSummaryCount = summaryMapper.countGroupSummaryRows();
        Long indicatorSummaryCount = summaryMapper.countIndicatorSummaryRows();
        Long scorerSummaryCount = summaryMapper.countScorerDistributionSummaryRows();

        if (isAnySummaryAlreadyPopulated(projectSummaryCount, groupSummaryCount, indicatorSummaryCount, scorerSummaryCount)) {
            log.info("跳过项目统计汇总回填：summary表已存在数据，project={}, group={}, indicator={}, scorerDist={}",
                    safeCount(projectSummaryCount), safeCount(groupSummaryCount), safeCount(indicatorSummaryCount), safeCount(scorerSummaryCount));
            return;
        }

        List<Long> projectIds = projectMapper.selectAllActiveProjectIds();
        if (projectIds == null || projectIds.isEmpty()) {
            log.info("跳过项目统计汇总回填：当前没有可回填的项目");
            return;
        }

        log.info("开始项目统计汇总回填：projectCount={}", projectIds.size());
        for (Long projectId : projectIds) {
            try {
                summaryRebuildService.rebuildProjectStatisticsSummary(projectId);
            } catch (Exception ex) {
                log.error("项目统计汇总回填失败：projectId={}", projectId, ex);
            }
        }
        log.info("项目统计汇总回填完成：projectCount={}", projectIds.size());
    }

    private boolean isAnySummaryAlreadyPopulated(Long projectSummaryCount,
                                                 Long groupSummaryCount,
                                                 Long indicatorSummaryCount,
                                                 Long scorerSummaryCount) {
        return safeCount(projectSummaryCount) > 0
                || safeCount(groupSummaryCount) > 0
                || safeCount(indicatorSummaryCount) > 0
                || safeCount(scorerSummaryCount) > 0;
    }

    private long safeCount(Long value) {
        return value == null ? 0L : value;
    }
}