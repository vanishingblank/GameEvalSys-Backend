package com.eval.gameeval.service.impl;

import com.eval.gameeval.mapper.ScoringRecordMapper;
import com.eval.gameeval.util.ProjectCacheUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ProjectMaliciousRebuildTaskService {
    private static final String MALICIOUS_RULE_AUTO = "AUTO";
    private static final String MALICIOUS_RULE_THRESHOLD = "THRESHOLD";

    @Resource
    private ScoringRecordMapper recordMapper;

    @Resource
    private ProjectCacheUtil projectCacheUtil;

    @Resource
    private ProjectStatisticsSummaryRebuildService projectStatisticsSummaryRebuildService;

    @Async
    public void rebuildProjectMaliciousFlagsAsync(Long projectId, String ruleType, BigDecimal scoreLower, BigDecimal scoreUpper) {
        updateTaskStatus(projectId, "RUNNING", "项目恶意标记重算中", 0);
        Exception lastError = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                rebuildProjectMaliciousFlagsOnce(projectId, ruleType, scoreLower, scoreUpper);
                updateTaskStatus(projectId, "SUCCESS", "项目恶意标记重算完成", attempt);
                return;
            } catch (Exception e) {
                lastError = e;
                log.warn("【异步重算】项目恶意标记重算失败，准备重试: projectId={}, attempt={}", projectId, attempt, e);
            }
        }
        updateTaskStatus(projectId, "FAILED", lastError != null ? lastError.getMessage() : "未知错误", 2);
    }

    private void rebuildProjectMaliciousFlagsOnce(Long projectId, String ruleType, BigDecimal scoreLower, BigDecimal scoreUpper) {
        try {
            if (projectId == null) {
                return;
            }

            String normalizedRuleType = normalizeMaliciousRuleType(ruleType);
            if (MALICIOUS_RULE_THRESHOLD.equals(normalizedRuleType)) {
                if (scoreLower != null && scoreUpper != null && scoreLower.compareTo(scoreUpper) <= 0) {
                    recordMapper.markMaliciousByThreshold(projectId, scoreLower, scoreUpper);
                    projectCacheUtil.clearProjectStatisticsCache(projectId);
                    log.info("【异步重算】项目阈值恶意标记完成: projectId={}", projectId);
                    return;
                }
                log.warn("【异步重算】阈值配置无效，回退AUTO算法: projectId={}, lower={}, upper={}",
                        projectId, scoreLower, scoreUpper);
            }

            List<Map<String, Object>> groupScoreRows = recordMapper.selectGroupScoreDetails(projectId);
            recordMapper.clearMaliciousFlagByProjectId(projectId);
            if (groupScoreRows == null || groupScoreRows.isEmpty()) {
                projectCacheUtil.clearProjectStatisticsCache(projectId);
                log.info("【异步重算】项目无评分数据，已清空恶意标记: projectId={}", projectId);
                return;
            }

            Map<Long, List<Map<String, Object>>> groupRowsMap = groupScoreRows.stream()
                    .collect(Collectors.groupingBy(row -> toLong(row.get("groupId")), LinkedHashMap::new, Collectors.toList()));

            Set<Long> maliciousRecordIds = new HashSet<>();
            for (List<Map<String, Object>> rows : groupRowsMap.values()) {
                if (rows == null || rows.isEmpty()) {
                    continue;
                }
                List<BigDecimal> rawScores = rows.stream()
                        .map(row -> convertToBigDecimal(row.get("totalScore")))
                        .collect(Collectors.toList());
                Set<Integer> abnormalIndexes = detectOutlierIndexes(rawScores);
                for (Integer abnormalIndex : abnormalIndexes) {
                    if (abnormalIndex == null || abnormalIndex < 0 || abnormalIndex >= rows.size()) {
                        continue;
                    }
                    Long recordId = toLong(rows.get(abnormalIndex).get("recordId"));
                    if (recordId != null && recordId > 0) {
                        maliciousRecordIds.add(recordId);
                    }
                }
            }

            if (!maliciousRecordIds.isEmpty()) {
                recordMapper.markMaliciousByRecordIds(new ArrayList<>(maliciousRecordIds));
            }
            projectCacheUtil.clearProjectStatisticsCache(projectId);
            projectStatisticsSummaryRebuildService.rebuildProjectStatisticsSummaryAsync(projectId);
            log.info("【异步重算】项目恶意标记完成: projectId={}, maliciousCount={}", projectId, maliciousRecordIds.size());
        } catch (Exception e) {
            log.error("【异步重算】项目恶意标记失败: projectId={}", projectId, e);
            throw e;
        }
    }

    private void updateTaskStatus(Long projectId, String status, String message, int retryCount) {
        if (projectId == null) {
            return;
        }
        Map<String, Object> statusInfo = new LinkedHashMap<>();
        statusInfo.put("projectId", projectId);
        statusInfo.put("status", status);
        statusInfo.put("message", message);
        statusInfo.put("retryCount", retryCount);
        statusInfo.put("updatedAt", LocalDateTime.now());
        projectCacheUtil.cacheProjectMaliciousRebuildTaskStatus(projectId, statusInfo);
    }

    private String normalizeMaliciousRuleType(String ruleType) {
        if (ruleType == null || ruleType.trim().isEmpty()) {
            return MALICIOUS_RULE_AUTO;
        }
        String normalized = ruleType.trim().toUpperCase();
        if (!MALICIOUS_RULE_AUTO.equals(normalized) && !MALICIOUS_RULE_THRESHOLD.equals(normalized)) {
            return MALICIOUS_RULE_AUTO;
        }
        return normalized;
    }

    private Set<Integer> detectOutlierIndexes(List<BigDecimal> scores) {
        if (scores == null || scores.size() < 4) {
            return Collections.emptySet();
        }
        BigDecimal median = calculateMedian(scores);
        List<BigDecimal> deviations = scores.stream()
                .map(score -> score.subtract(median).abs())
                .collect(Collectors.toList());
        BigDecimal mad = calculateMedian(deviations);

        BigDecimal threshold = mad.multiply(new BigDecimal("1.4826")).multiply(BigDecimal.valueOf(3L));
        BigDecimal thresholdFloor = median.abs().multiply(new BigDecimal("0.05"));
        if (thresholdFloor.compareTo(new BigDecimal("0.50")) < 0) {
            thresholdFloor = new BigDecimal("0.50");
        }
        if (threshold.compareTo(thresholdFloor) < 0) {
            threshold = thresholdFloor;
        }

        BigDecimal lowerBound = median.subtract(threshold);
        Set<Integer> abnormalIndexes = new HashSet<>();
        for (int i = 0; i < scores.size(); i++) {
            if (scores.get(i).compareTo(lowerBound) < 0) {
                abnormalIndexes.add(i);
            }
        }
        return abnormalIndexes;
    }

    private BigDecimal calculateMedian(List<BigDecimal> scores) {
        if (scores == null || scores.isEmpty()) {
            return BigDecimal.ZERO;
        }
        List<BigDecimal> sortedScores = scores.stream()
                .sorted(BigDecimal::compareTo)
                .collect(Collectors.toList());
        int middle = sortedScores.size() / 2;
        if (sortedScores.size() % 2 == 0) {
            return sortedScores.get(middle - 1)
                    .add(sortedScores.get(middle))
                    .divide(BigDecimal.valueOf(2L), 6, java.math.RoundingMode.HALF_UP);
        }
        return sortedScores.get(middle);
    }

    private BigDecimal convertToBigDecimal(Object obj) {
        if (obj == null) {
            return BigDecimal.ZERO;
        }
        if (obj instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (obj instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return new BigDecimal(obj.toString());
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }
}
