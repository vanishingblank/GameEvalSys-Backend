package com.eval.gameeval.service.impl;

import com.eval.gameeval.mapper.ProjectMapper;
import com.eval.gameeval.mapper.ProjectStatisticsSummaryMapper;
import com.eval.gameeval.mapper.ScoringRecordMapper;
import com.eval.gameeval.models.entity.Project;
import com.eval.gameeval.models.entity.ProjectStatisticsProjectSummary;
import com.eval.gameeval.models.entity.ProjectStatisticsGroupSummary;
import com.eval.gameeval.models.entity.ProjectStatisticsIndicatorSummary;
import com.eval.gameeval.models.entity.ProjectStatisticsScorerDistributionSummary;
import com.eval.gameeval.util.ProjectCacheUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ProjectStatisticsSummaryRebuildService {
    @Resource
    private ProjectMapper projectMapper;

    @Resource
    private ScoringRecordMapper recordMapper;

    @Resource
    private ProjectStatisticsSummaryMapper summaryMapper;

    @Resource
    private ProjectCacheUtil projectCacheUtil;

    @Async
    @Transactional(rollbackFor = Exception.class)
    public void rebuildProjectStatisticsSummaryAsync(Long projectId) {
        rebuildProjectStatisticsSummary(projectId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void rebuildProjectStatisticsSummary(Long projectId) {
        try {
            if (projectId == null) {
                return;
            }

            Project project = projectMapper.selectById(projectId);
            if (project == null) {
                return;
            }

            List<Map<String, Object>> groupScoreRows = recordMapper.selectGroupScoreDetails(projectId);
            List<Map<String, Object>> indicatorScoreRows = recordMapper.selectIndicatorScoreDetails(projectId);
            List<Map<String, Object>> scorerDistRows = recordMapper.selectScorerDistribution(projectId);

            ProjectStatisticsProjectSummary projectSummary = buildProjectSummary(projectId, groupScoreRows);
            List<ProjectStatisticsGroupSummary> groupSummaries = buildGroupSummaries(projectId, groupScoreRows);
            List<ProjectStatisticsIndicatorSummary> indicatorSummaries = buildIndicatorSummaries(projectId, indicatorScoreRows);
            List<ProjectStatisticsScorerDistributionSummary> scorerSummaries = buildScorerDistributionSummaries(projectId, scorerDistRows);

            summaryMapper.deleteProjectSummaryByProjectId(projectId);
            summaryMapper.deleteGroupSummaryByProjectId(projectId);
            summaryMapper.deleteIndicatorSummaryByProjectId(projectId);
            summaryMapper.deleteScorerDistributionSummaryByProjectId(projectId);

            if (projectSummary != null) {
                summaryMapper.upsertProjectSummary(projectSummary);
            }
            if (!groupSummaries.isEmpty()) {
                summaryMapper.batchUpsertGroupSummaries(groupSummaries);
            }
            if (!indicatorSummaries.isEmpty()) {
                summaryMapper.batchUpsertIndicatorSummaries(indicatorSummaries);
            }
            if (!scorerSummaries.isEmpty()) {
                summaryMapper.batchUpsertScorerDistributionSummaries(scorerSummaries);
            }

            projectCacheUtil.clearProjectStatisticsCache(projectId);
            log.info("【统计汇总】项目统计汇总重建完成: projectId={}, groupCount={}, indicatorCount={}, scorerDistCount={}",
                    projectId, groupSummaries.size(), indicatorSummaries.size(), scorerSummaries.size());
        } catch (Exception e) {
            log.error("【统计汇总】项目统计汇总重建失败: projectId={}", projectId, e);
            throw e;
        }
    }

    private ProjectStatisticsProjectSummary buildProjectSummary(Long projectId, List<Map<String, Object>> groupScoreRows) {
        if (groupScoreRows == null || groupScoreRows.isEmpty()) {
            return null;
        }

        BigDecimal projectMean = calculateAverage(groupScoreRows.stream()
                .map(row -> convertToBigDecimal(row.get("totalScore")))
                .collect(Collectors.toList()));
        Map<Long, NormalizationStats> scorerStats = buildScorerStats(groupScoreRows, "userId", "totalScore");

        List<ScoreEntry> entries = groupScoreRows.stream()
                .map(row -> {
                    Long groupId = toLong(row.get("groupId"));
                    String groupName = row.get("groupName") != null ? row.get("groupName").toString() : "-";
                    Long userId = toLong(row.get("userId"));
                    BigDecimal rawScore = convertToBigDecimal(row.get("totalScore"));
                    BigDecimal normalizedScore = normalizeScore(rawScore, scorerStats.get(userId), projectMean);
                    boolean abnormal = toInteger(row.get("isMalicious")) == 1;
                    return new ScoreEntry(groupId, groupName, rawScore, normalizedScore, abnormal);
                })
                .collect(Collectors.toList());

        List<BigDecimal> rawScores = entries.stream().map(ScoreEntry::getRawScore).collect(Collectors.toList());
        List<BigDecimal> normalizedScores = entries.stream().map(ScoreEntry::getNormalizedScore).collect(Collectors.toList());
        List<BigDecimal> validScores = entries.stream()
                .filter(entry -> !entry.isAbnormal())
                .map(ScoreEntry::getNormalizedScore)
                .collect(Collectors.toList());
        int abnormalCount = (int) entries.stream().filter(ScoreEntry::isAbnormal).count();

        return new ProjectStatisticsProjectSummary()
            .setProjectId(projectId)
                .setRawAverageScore(scaleScore(calculateAverage(rawScores)))
                .setNormalizedAverageScore(scaleScore(calculateAverage(normalizedScores)))
                .setProcessedAverageScore(scaleScore(validScores.isEmpty() ? calculateAverage(normalizedScores) : calculateAverage(validScores)))
                .setAbnormalCount(abnormalCount)
                .setSampleSize(entries.size())
                .setValidSampleSize(validScores.size())
                .setUpdatedTime(LocalDateTime.now());
    }

    private List<ProjectStatisticsGroupSummary> buildGroupSummaries(Long projectId, List<Map<String, Object>> groupScoreRows) {
        if (groupScoreRows == null || groupScoreRows.isEmpty()) {
            return Collections.emptyList();
        }

        BigDecimal projectMean = calculateAverage(groupScoreRows.stream()
                .map(row -> convertToBigDecimal(row.get("totalScore")))
                .collect(Collectors.toList()));
        Map<Long, NormalizationStats> scorerStats = buildScorerStats(groupScoreRows, "userId", "totalScore");

        Map<Long, List<ScoreEntry>> groupedScores = new LinkedHashMap<>();
        for (Map<String, Object> row : groupScoreRows) {
            Long groupId = toLong(row.get("groupId"));
            String groupName = row.get("groupName") != null ? row.get("groupName").toString() : "-";
            Long userId = toLong(row.get("userId"));
            BigDecimal rawScore = convertToBigDecimal(row.get("totalScore"));
            BigDecimal normalizedScore = normalizeScore(rawScore, scorerStats.get(userId), projectMean);
            boolean abnormal = toInteger(row.get("isMalicious")) == 1;
            groupedScores.computeIfAbsent(groupId, key -> new ArrayList<>())
                    .add(new ScoreEntry(groupId, groupName, rawScore, normalizedScore, abnormal));
        }

        LocalDateTime now = LocalDateTime.now();
        List<ProjectStatisticsGroupSummary> results = new ArrayList<>();
        for (List<ScoreEntry> entries : groupedScores.values()) {
            if (entries == null || entries.isEmpty()) {
                continue;
            }
            ScoreEntry first = entries.get(0);
            List<BigDecimal> rawScores = entries.stream().map(ScoreEntry::getRawScore).collect(Collectors.toList());
            List<BigDecimal> normalizedScores = entries.stream().map(ScoreEntry::getNormalizedScore).collect(Collectors.toList());
            List<BigDecimal> validScores = entries.stream()
                    .filter(entry -> !entry.isAbnormal())
                    .map(ScoreEntry::getNormalizedScore)
                    .collect(Collectors.toList());
            int abnormalCount = (int) entries.stream().filter(ScoreEntry::isAbnormal).count();
            BigDecimal rawAverage = calculateAverage(rawScores);
            BigDecimal normalizedAverage = calculateAverage(normalizedScores);
            BigDecimal processedAverage = validScores.isEmpty() ? normalizedAverage : calculateAverage(validScores);

            results.add(new ProjectStatisticsGroupSummary()
                    .setProjectId(projectId)
                    .setGroupId(first.getSubjectId())
                    .setGroupName(first.getSubjectName())
                    .setRawAverageScore(scaleScore(rawAverage))
                    .setNormalizedAverageScore(scaleScore(normalizedAverage))
                    .setProcessedAverageScore(scaleScore(processedAverage))
                    .setAbnormalCount(abnormalCount)
                    .setSampleSize(entries.size())
                    .setValidSampleSize(validScores.size())
                    .setUpdatedTime(now));
        }
        return results;
    }

    private List<ProjectStatisticsIndicatorSummary> buildIndicatorSummaries(Long projectId, List<Map<String, Object>> indicatorScoreRows) {
        if (indicatorScoreRows == null || indicatorScoreRows.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, List<Map<String, Object>>> indicatorGroups = indicatorScoreRows.stream()
                .collect(Collectors.groupingBy(row -> toLong(row.get("indicatorId")), LinkedHashMap::new, Collectors.toList()));

        LocalDateTime now = LocalDateTime.now();
        List<ProjectStatisticsIndicatorSummary> results = new ArrayList<>();
        for (List<Map<String, Object>> rows : indicatorGroups.values()) {
            if (rows == null || rows.isEmpty()) {
                continue;
            }

            BigDecimal indicatorMean = calculateAverage(rows.stream()
                    .map(row -> convertToBigDecimal(row.get("score")))
                    .collect(Collectors.toList()));
            Map<Long, NormalizationStats> scorerStats = buildScorerStats(rows, "userId", "score");
            List<ScoreEntry> entries = rows.stream()
                    .map(row -> {
                        Long indicatorId = toLong(row.get("indicatorId"));
                        String indicatorName = row.get("indicatorName") != null ? row.get("indicatorName").toString() : "-";
                        Long userId = toLong(row.get("userId"));
                        BigDecimal rawScore = convertToBigDecimal(row.get("score"));
                        BigDecimal normalizedScore = normalizeScore(rawScore, scorerStats.get(userId), indicatorMean);
                        boolean abnormal = toInteger(row.get("isMalicious")) == 1;
                        return new ScoreEntry(indicatorId, indicatorName, rawScore, normalizedScore, abnormal);
                    })
                    .collect(Collectors.toList());

            ScoreEntry first = entries.get(0);
            List<BigDecimal> rawScores = entries.stream().map(ScoreEntry::getRawScore).collect(Collectors.toList());
            List<BigDecimal> normalizedScores = entries.stream().map(ScoreEntry::getNormalizedScore).collect(Collectors.toList());
            List<BigDecimal> validScores = entries.stream()
                    .filter(entry -> !entry.isAbnormal())
                    .map(ScoreEntry::getNormalizedScore)
                    .collect(Collectors.toList());
            int abnormalCount = (int) entries.stream().filter(ScoreEntry::isAbnormal).count();
            BigDecimal rawAverage = calculateAverage(rawScores);
            BigDecimal normalizedAverage = calculateAverage(normalizedScores);
            BigDecimal processedAverage = validScores.isEmpty() ? normalizedAverage : calculateAverage(validScores);

            results.add(new ProjectStatisticsIndicatorSummary()
                    .setProjectId(projectId)
                    .setIndicatorId(first.getSubjectId())
                    .setIndicatorName(first.getSubjectName())
                    .setRawAverageScore(scaleScore(rawAverage))
                    .setNormalizedAverageScore(scaleScore(normalizedAverage))
                    .setProcessedAverageScore(scaleScore(processedAverage))
                    .setAbnormalCount(abnormalCount)
                    .setTotalAbnormalCount(abnormalCount)
                    .setSampleSize(entries.size())
                    .setValidSampleSize(validScores.size())
                    .setUpdatedTime(now));
        }
        return results;
    }

    private List<ProjectStatisticsScorerDistributionSummary> buildScorerDistributionSummaries(Long projectId, List<Map<String, Object>> scorerDistRows) {
        if (scorerDistRows == null || scorerDistRows.isEmpty()) {
            return Collections.emptyList();
        }
        LocalDateTime now = LocalDateTime.now();
        List<ProjectStatisticsScorerDistributionSummary> results = new ArrayList<>();
        for (Map<String, Object> row : scorerDistRows) {
            results.add(new ProjectStatisticsScorerDistributionSummary()
                    .setProjectId(projectId)
                    .setUserId(toLong(row.get("userId")))
                    .setUserName(row.get("userName") != null ? row.get("userName").toString() : "-")
                    .setScoreRange(row.get("scoreRange") != null ? row.get("scoreRange").toString() : "-")
                    .setCount(toInteger(row.get("count")))
                    .setUpdatedTime(now));
        }
        return results;
    }

    private Map<Long, NormalizationStats> buildScorerStats(List<Map<String, Object>> rows, String userKey, String scoreKey) {
        Map<Long, List<BigDecimal>> scorerScoreMap = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Long userId = toLong(row.get(userKey));
            scorerScoreMap.computeIfAbsent(userId, key -> new ArrayList<>())
                    .add(convertToBigDecimal(row.get(scoreKey)));
        }

        Map<Long, NormalizationStats> scorerStats = new HashMap<>();
        for (Map.Entry<Long, List<BigDecimal>> entry : scorerScoreMap.entrySet()) {
            scorerStats.put(entry.getKey(), new NormalizationStats(calculateAverage(entry.getValue()), entry.getValue().size()));
        }
        return scorerStats;
    }

    private BigDecimal normalizeScore(BigDecimal rawScore, NormalizationStats scorerStats, BigDecimal projectMean) {
        if (rawScore == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (scorerStats == null || scorerStats.getSampleSize() < 2 || scorerStats.getMeanScore() == null || projectMean == null) {
            return scaleScore(rawScore);
        }
        return scaleScore(rawScore.subtract(scorerStats.getMeanScore()).add(projectMean));
    }

    private BigDecimal calculateAverage(List<BigDecimal> scores) {
        if (scores == null || scores.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal sum = scores.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(scores.size()), 6, RoundingMode.HALF_UP);
    }

    private BigDecimal scaleScore(BigDecimal score) {
        if (score == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return score.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal convertToBigDecimal(Object obj) {
        if (obj == null) {
            return BigDecimal.ZERO;
        }
        if (obj instanceof BigDecimal) {
            return (BigDecimal) obj;
        }
        if (obj instanceof Number) {
            return BigDecimal.valueOf(((Number) obj).doubleValue());
        }
        return new BigDecimal(obj.toString());
    }

    private Integer toInteger(Object obj) {
        if (obj == null) {
            return 0;
        }
        if (obj instanceof Number) {
            return ((Number) obj).intValue();
        }
        try {
            return Integer.parseInt(obj.toString());
        } catch (Exception e) {
            return 0;
        }
    }

    private Long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            log.warn("统计字段转换失败: {}", value, e);
            return 0L;
        }
    }

    private static class NormalizationStats {
        private final BigDecimal meanScore;
        private final int sampleSize;

        private NormalizationStats(BigDecimal meanScore, int sampleSize) {
            this.meanScore = meanScore;
            this.sampleSize = sampleSize;
        }

        public BigDecimal getMeanScore() {
            return meanScore;
        }

        public int getSampleSize() {
            return sampleSize;
        }
    }

    private static class ScoreEntry {
        private final Long subjectId;
        private final String subjectName;
        private final BigDecimal rawScore;
        private final BigDecimal normalizedScore;
        private final boolean abnormal;

        private ScoreEntry(Long subjectId, String subjectName, BigDecimal rawScore, BigDecimal normalizedScore, boolean abnormal) {
            this.subjectId = subjectId;
            this.subjectName = subjectName;
            this.rawScore = rawScore;
            this.normalizedScore = normalizedScore;
            this.abnormal = abnormal;
        }

        public Long getSubjectId() {
            return subjectId;
        }

        public String getSubjectName() {
            return subjectName;
        }

        public BigDecimal getRawScore() {
            return rawScore;
        }

        public BigDecimal getNormalizedScore() {
            return normalizedScore;
        }

        public boolean isAbnormal() {
            return abnormal;
        }
    }
}
