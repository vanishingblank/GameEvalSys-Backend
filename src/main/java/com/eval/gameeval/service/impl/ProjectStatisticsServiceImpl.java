package com.eval.gameeval.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.poi.excel.ExcelUtil;
import cn.hutool.poi.excel.ExcelWriter;
import com.eval.gameeval.mapper.*;
import com.eval.gameeval.models.VO.GroupIndicatorStatisticsVO;
import com.eval.gameeval.models.VO.PlatformStatisticsVO;
import com.eval.gameeval.models.VO.ProjectStatisticsVO;
import com.eval.gameeval.models.VO.ResponseVO;
import com.eval.gameeval.models.VO.ScoringOverviewVO;
import com.eval.gameeval.models.entity.*;
import com.eval.gameeval.service.IProjectStatisticsService;
import com.eval.gameeval.util.RedisToken;
import com.eval.gameeval.util.RedisBaseUtil;
import com.eval.gameeval.util.RedisKeyUtil;
import com.eval.gameeval.util.ScoringOverviewCacheUtil;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ProjectStatisticsServiceImpl implements IProjectStatisticsService {
    private static final String MALICIOUS_RULE_AUTO = "AUTO";
    private static final String MALICIOUS_RULE_THRESHOLD = "THRESHOLD";

    @Resource
    private ScoringRecordMapper recordMapper;

    @Resource
    private ScoringRecordDetailMapper detailMapper;

    @Resource
    private ProjectGroupMapper groupMapper;

    @Resource
    private ProjectGroupInfoMapper groupInfoMapper;

    @Resource
    private ProjectMapper projectMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private ScoringIndicatorMapper indicatorMapper;

    @Resource
    private ScoringIndicatorCategoryMapper categoryMapper;

    @Resource
    private ScoringStandardMapper standardMapper;

    @Resource
    private RedisToken redisToken;

    @Resource
    private ScoringOverviewCacheUtil scoringOverviewCacheUtil;

    @Resource
    private RedisBaseUtil redisBaseUtil;

    @Override
    public ResponseVO<ProjectStatisticsVO> getProjectStatistics(String token, Long projectId) {
        try {
            // 1. 验证Token
            Long currentUserId = redisToken.getUserIdByToken(token);
            if (currentUserId == null) {
                return ResponseVO.unauthorized("Token无效");
            }

            // 2. 验证项目是否存在
            Project project = projectMapper.selectById(projectId);
            if (project == null) {
                return ResponseVO.notFound("项目不存在");
            }

            // 3. 查询小组评分明细，并在服务层完成评委标准化与异常检测
            List<Map<String, Object>> groupScoreList = recordMapper.selectGroupScoreDetails(projectId);
            refreshMaliciousFlags(project, groupScoreList);
            groupScoreList = recordMapper.selectGroupScoreDetails(projectId);
            List<ProjectStatisticsVO.GroupAverageVO> groupAverage = buildGroupAverageStatistics(groupScoreList);

            // 4. 查询指标评分明细，并在服务层完成评委标准化与异常检测
            List<Map<String, Object>> indicatorScoreList = recordMapper.selectIndicatorScoreDetails(projectId);
            List<ProjectStatisticsVO.IndicatorAverageVO> indicatorAverage = buildIndicatorAverageStatistics(indicatorScoreList);

            // 5. 查询打分用户分布

            List<Map<String, Object>> scorerDistList = recordMapper.selectScorerDistribution(projectId);
            List<ProjectStatisticsVO.ScorerDistributionVO> scorerDistribution = scorerDistList.stream()
                    .map(map -> {
                        ProjectStatisticsVO.ScorerDistributionVO vo = new ProjectStatisticsVO.ScorerDistributionVO();
                        vo.setUserId(((Number) map.get("userId")).longValue());
                        vo.setUserName((String) map.get("userName"));
                        vo.setScoreRange((String) map.get("scoreRange"));
                        vo.setCount(((Number) map.get("count")).intValue());
                        return vo;
                    })
                    .collect(Collectors.toList());

            // 6. 构建响应
            ProjectStatisticsVO statisticsVO = new ProjectStatisticsVO();
            statisticsVO.setGroupAverage(groupAverage);
            statisticsVO.setIndicatorAverage(indicatorAverage);
            statisticsVO.setScorerDistribution(scorerDistribution);

            log.info("查询项目统计成功: projectId={}", projectId);

            return ResponseVO.success("查询成功", statisticsVO);

        } catch (Exception e) {
            log.error("查询项目统计异常: projectId={}", projectId, e);
            return ResponseVO.error("查询失败: " + e.getMessage());
        }
    }

    @Override
    public ResponseVO<ScoringOverviewVO> getScoringOverview(String token) {
        try {
            Long currentUserId = redisToken.getUserIdByToken(token);
            if (currentUserId == null) {
                return ResponseVO.unauthorized("Token无效");
            }

            Object cache = scoringOverviewCacheUtil.getUserOverviewCache(currentUserId);
            if (cache != null) {
                ScoringOverviewVO cachedOverview = (ScoringOverviewVO) cache;
                log.info("【缓存命中】获取用户打分概览: userId={}, total={}",
                        currentUserId, cachedOverview.getTotalProjects());
                return ResponseVO.success("获取成功", cachedOverview);
            }

            Map<String, Object> overviewMap = projectMapper.selectScoringOverviewByUserId(currentUserId);
            if (overviewMap == null) {
                overviewMap = Collections.emptyMap();
            }
            ScoringOverviewVO overviewVO = new ScoringOverviewVO()
                    .setTotalProjects(toLong(overviewMap.get("totalProjects")))
                    .setOngoingProjects(toLong(overviewMap.get("ongoingProjects")))
                    .setCompletedProjects(toLong(overviewMap.get("completedProjects")))
                    .setPendingProjects(toLong(overviewMap.get("pendingProjects")));

            scoringOverviewCacheUtil.cacheUserOverview(currentUserId, overviewVO);
            log.info("获取用户打分概览成功: userId={}, total={}",
                    currentUserId, overviewVO.getTotalProjects());
            return ResponseVO.success("获取成功", overviewVO);
        } catch (Exception e) {
            log.error("获取用户打分概览异常", e);
            return ResponseVO.error("获取失败: " + e.getMessage());
        }
    }

    @Override
    public ResponseVO<PlatformStatisticsVO> getPlatformStatistics(String token) {
        try {
            Long currentUserId = redisToken.getUserIdByToken(token);
            if (currentUserId == null) {
                return ResponseVO.unauthorized("Token无效");
            }

            User currentUser = userMapper.selectById(currentUserId);
            if (currentUser == null ||
                    (!"super_admin".equals(currentUser.getRole()) && !"admin".equals(currentUser.getRole()))) {
                return ResponseVO.forbidden("权限不足");
            }

            String cacheKey = RedisKeyUtil.PLATFORM_STATISTICS_KEY;
            Object cache = redisBaseUtil.get(cacheKey);
            if (cache != null) {
                PlatformStatisticsVO cached = (PlatformStatisticsVO) cache;
                log.info("【缓存命中】获取平台趋势统计: days={}, projectPoints={}, scorePoints={}, avgPoints={}",
                        cached.getDates() != null ? cached.getDates().size() : 0,
                        cached.getProjectTrend() != null ? cached.getProjectTrend().size() : 0,
                        cached.getScoreTrend() != null ? cached.getScoreTrend().size() : 0,
                        cached.getAverageScoreTrend() != null ? cached.getAverageScoreTrend().size() : 0);
                return ResponseVO.success("查询成功", cached);
            }

            final int days = 30;
            LocalDate today = LocalDate.now();
            LocalDate startDate = today.minusDays(days - 1L);
            LocalDateTime startTime = startDate.atStartOfDay();
            LocalDateTime endTime = today.plusDays(1L).atStartOfDay();

            Long baseProjects = projectMapper.countProjectsBefore(startTime);
            Map<LocalDate, Long> dailyProjectMap = toDailyLongMap(
                    projectMapper.selectDailyProjectCount(startTime, endTime), "cnt");
            Map<LocalDate, Long> dailyScoreMap = toDailyLongMap(
                    projectMapper.selectDailyScoreCount(startTime, endTime), "cnt");
            Map<LocalDate, BigDecimal> dailyAverageScoreMap = toDailyBigDecimalMap(
                    projectMapper.selectDailyAverageScore(startTime, endTime), "avgScore");

            List<String> dateAxis = new ArrayList<>(days);
            List<Long> projectTrend = new ArrayList<>(days);
            List<Long> scoreTrend = new ArrayList<>(days);
            List<BigDecimal> averageScoreTrend = new ArrayList<>(days);

            long runningProjects = baseProjects != null ? baseProjects : 0L;
            for (int i = 0; i < days; i++) {
                LocalDate date = startDate.plusDays(i);
                dateAxis.add(date.toString());

                long todayProjectIncrease = dailyProjectMap.getOrDefault(date, 0L);
                runningProjects += todayProjectIncrease;
                projectTrend.add(runningProjects);

                scoreTrend.add(dailyScoreMap.getOrDefault(date, 0L));
                averageScoreTrend.add(dailyAverageScoreMap.getOrDefault(
                        date, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)));
            }

            PlatformStatisticsVO vo = new PlatformStatisticsVO()
                    .setDates(dateAxis)
                    .setProjectTrend(projectTrend)
                    .setScoreTrend(scoreTrend)
                    .setAverageScoreTrend(averageScoreTrend);

            redisBaseUtil.set(cacheKey, vo, RedisKeyUtil.PLATFORM_STATISTICS_TTL);
            log.info("获取平台趋势统计成功: startDate={}, endDate={}, points={}",
                    startDate, today, days);
            return ResponseVO.success("查询成功", vo);
        } catch (Exception e) {
            log.error("获取平台全局统计异常", e);
            return ResponseVO.error("查询失败: " + e.getMessage());
        }
    }

    @Override
    public ResponseVO<GroupIndicatorStatisticsVO> getGroupIndicatorStatistics(String token, Long projectId, Long groupId) {
        try {
            Long currentUserId = redisToken.getUserIdByToken(token);
            if (currentUserId == null) {
                return ResponseVO.unauthorized("Token无效");
            }

            Project project = projectMapper.selectById(projectId);
            if (project == null) {
                return ResponseVO.notFound("项目不存在");
            }

            ProjectGroup relation = groupMapper.selectByGroupIdAndProjectId(groupId, projectId);
            if (relation == null) {
                return ResponseVO.notFound("小组不在该项目内");
            }

            ProjectGroupInfo groupInfo = groupInfoMapper.selectById(groupId);
            if (groupInfo == null) {
                return ResponseVO.notFound("小组不存在");
            }

            List<Map<String, Object>> indicatorAvgList =
                    recordMapper.selectIndicatorAverageByProjectAndGroup(projectId, groupId);

            List<GroupIndicatorStatisticsVO.IndicatorAverageVO> indicatorAverage = indicatorAvgList.stream()
                    .map(map -> new GroupIndicatorStatisticsVO.IndicatorAverageVO()
                            .setIndicatorId(((Number) map.get("indicatorId")).longValue())
                            .setIndicatorName((String) map.get("indicatorName"))
                            .setAverageScore(convertToBigDecimal(map.get("averageScore"))))
                    .collect(Collectors.toList());

            GroupIndicatorStatisticsVO result = new GroupIndicatorStatisticsVO()
                    .setGroupId(groupId)
                    .setGroupName(groupInfo.getName())
                    .setIndicatorAverage(indicatorAverage);

            log.info("查询项目小组指标平均分成功: projectId={}, groupId={}", projectId, groupId);
            return ResponseVO.success("查询成功", result);
        } catch (Exception e) {
            log.error("查询项目小组指标平均分异常: projectId={}, groupId={}", projectId, groupId, e);
            return ResponseVO.error("查询失败: " + e.getMessage());
        }
    }

    @Override
    public void exportProjectData(String token, Long projectId, String format, HttpServletResponse response) throws IOException {
        try {
            // 1. 验证Token
            Long currentUserId = redisToken.getUserIdByToken(token);
            if (currentUserId == null) {
                writeExportError(response, HttpServletResponse.SC_UNAUTHORIZED, "Token无效");
                return;
            }

            // 2. 验证项目是否存在
            Project project = projectMapper.selectById(projectId);
            if (project == null) {
                writeExportError(response, HttpServletResponse.SC_NOT_FOUND, "项目不存在");
                return;
            }

            // 3. 查询所有打分记录
            List<ScoringRecord> records = recordMapper.selectByProjectId(projectId);
            if (records == null || records.isEmpty()) {
                writeExportError(response, HttpServletResponse.SC_NOT_FOUND, "该项目暂无打分数据");
                return;
            }

            // 4. 查询所有明细（批量）
            List<Long> recordIds = records.stream().map(ScoringRecord::getId).toList();
            List<ScoringRecordDetail> allDetails = detailMapper.selectByRecordIds(recordIds);

            // 5. 根据项目评分标准动态获取指标列，避免固定3列导致错位
            List<ScoringIndicator> indicators = getProjectIndicators(project);
            if (indicators.isEmpty() && allDetails != null && !allDetails.isEmpty()) {
                LinkedHashSet<Long> indicatorIds = allDetails.stream()
                        .map(ScoringRecordDetail::getIndicatorId)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
                if (!indicatorIds.isEmpty()) {
                    indicators = indicatorMapper.selectByIds(new ArrayList<>(indicatorIds)).stream()
                            .sorted(Comparator.comparing(ScoringIndicator::getId))
                            .collect(Collectors.toList());
                }
            }

            Map<Long, Map<Long, BigDecimal>> detailScoreMap = new HashMap<>();
            if (allDetails != null) {
                for (ScoringRecordDetail detail : allDetails) {
                    detailScoreMap
                            .computeIfAbsent(detail.getRecordId(), k -> new HashMap<>())
                            .put(detail.getIndicatorId(), detail.getScore());
                }
            }

            // 6. 构建导出数据
            List<List<Object>> rows = new ArrayList<>();

            // 表头
            List<Object> header = new ArrayList<>();
            header.add("项目名称");
            header.add("小组名称");
            header.add("打分用户");
            header.add("打分时间");
            for (ScoringIndicator indicator : indicators) {
                header.add(indicator.getName());
            }
            header.add("总分");
            rows.add(header);

            Map<Long, String> groupNameCache = new HashMap<>();
            Map<Long, String> userNameCache = new HashMap<>();

            // 数据行（按动态指标列填充）
            for (ScoringRecord record : records) {
                List<Object> row = new ArrayList<>();

                // 项目名称
                row.add(project.getName());

                // 小组名称（从project_group_info获取）
                String groupName = groupNameCache.computeIfAbsent(record.getGroupInfoId(), groupId -> {
                    ProjectGroupInfo groupInfo = groupInfoMapper.selectById(groupId);
                    return groupInfo != null ? groupInfo.getName() : "未知";
                });
                row.add(groupName);

                // 打分用户
                String userName = userNameCache.computeIfAbsent(record.getUserId(), userId -> {
                    User user = userMapper.selectById(userId);
                    return user != null ? user.getName() : "未知";
                });
                row.add(userName);

                // 打分时间
                row.add(DateUtil.format(record.getCreateTime(), "yyyy-MM-dd HH:mm:ss"));

                // 指标得分（按项目指标顺序）
                Map<Long, BigDecimal> scoreByIndicator = detailScoreMap.getOrDefault(record.getId(), Collections.emptyMap());
                for (ScoringIndicator indicator : indicators) {
                    BigDecimal score = scoreByIndicator.get(indicator.getId());
                    row.add(score != null ? score : "-");
                }

                // 总分
                row.add(record.getTotalScore());

                rows.add(row);
            }

            // 7. 导出文件
            String fileName = project.getName() + "_打分数据_" + DateUtil.format(new Date(), "yyyyMMddHHmmss");

            if ("csv".equalsIgnoreCase(format)) {
                // CSV格式
                exportCsv(response, fileName, rows);
            } else {
                // Excel格式（默认）
                exportExcel(response, fileName, rows);
            }

            log.info("导出项目数据成功: projectId={}, format={}", projectId, format);

        } catch (Exception e) {
            log.error("导出项目数据异常: projectId={}", projectId, e);
            writeExportError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "导出失败: " + e.getMessage());
        }
    }

    @Override
    public void exportProjectGroupIndicatorItemScores(String token, Long projectId, String format, HttpServletResponse response) throws IOException {
        try {
            Long currentUserId = redisToken.getUserIdByToken(token);
            if (currentUserId == null) {
                writeExportError(response, HttpServletResponse.SC_UNAUTHORIZED, "Token无效");
                return;
            }

            Project project = projectMapper.selectById(projectId);
            if (project == null) {
                writeExportError(response, HttpServletResponse.SC_NOT_FOUND, "项目不存在");
                return;
            }

            List<ProjectGroup> projectGroups = groupMapper.selectByProjectId(projectId);
            if (projectGroups == null || projectGroups.isEmpty()) {
                writeExportError(response, HttpServletResponse.SC_NOT_FOUND, "该项目暂无小组数据");
                return;
            }

            List<ScoringRecord> records = recordMapper.selectByProjectId(projectId);
            if (records == null || records.isEmpty()) {
                writeExportError(response, HttpServletResponse.SC_NOT_FOUND, "该项目暂无打分数据");
                return;
            }

            List<ScoringIndicator> indicators = getProjectIndicators(project);
            if (indicators.isEmpty()) {
                writeExportError(response, HttpServletResponse.SC_NOT_FOUND, "该项目未配置评分项");
                return;
            }

            List<ScoringIndicatorCategory> categories = project.getStandardId() == null
                    ? Collections.emptyList()
                    : categoryMapper.selectByStandardId(project.getStandardId());

            List<Long> recordIds = records.stream().map(ScoringRecord::getId).toList();
            List<ScoringRecordDetail> allDetails = detailMapper.selectByRecordIds(recordIds);

            if (allDetails == null) {
                allDetails = Collections.emptyList();
            }

            Map<Long, ScoringRecord> recordMap = records.stream()
                    .collect(Collectors.toMap(ScoringRecord::getId, r -> r));

            Map<Long, List<ScoringRecord>> groupRecordMap = records.stream()
                    .collect(Collectors.groupingBy(ScoringRecord::getGroupInfoId));

            Map<Long, Map<Long, List<BigDecimal>>> groupIndicatorScoreMap = new HashMap<>();
            Map<Long, Map<Long, BigDecimal>> recordIndicatorScoreMap = new HashMap<>();

            for (ScoringRecordDetail detail : allDetails) {
                ScoringRecord record = recordMap.get(detail.getRecordId());
                if (record == null) {
                    continue;
                }
                Long groupId = record.getGroupInfoId();
                Long indicatorId = detail.getIndicatorId();
                groupIndicatorScoreMap
                        .computeIfAbsent(groupId, k -> new HashMap<>())
                        .computeIfAbsent(indicatorId, k -> new ArrayList<>())
                        .add(detail.getScore());

                recordIndicatorScoreMap
                        .computeIfAbsent(detail.getRecordId(), k -> new HashMap<>())
                        .put(indicatorId, detail.getScore());
            }

            Map<Long, List<Long>> categoryIndicatorIds = new LinkedHashMap<>();
            for (ScoringIndicatorCategory category : categories) {
                List<Long> indicatorIds = indicators.stream()
                        .filter(indicator -> category.getId().equals(indicator.getCategoryId()))
                        .map(ScoringIndicator::getId)
                        .collect(Collectors.toList());
                categoryIndicatorIds.put(category.getId(), indicatorIds);
            }

            Map<Long, String> groupNameCache = new HashMap<>();
            List<List<Object>> rows = new ArrayList<>();

            List<Object> header = new ArrayList<>();
            header.add("小组ID");
            header.add("小组名称");
            for (ScoringIndicator indicator : indicators) {
                header.add(indicator.getName() + "_平均分");
            }
            for (ScoringIndicatorCategory category : categories) {
                header.add(category.getName() + "_总分平均分");
            }
            rows.add(header);

            for (ProjectGroup group : projectGroups) {
                String groupName = groupNameCache.computeIfAbsent(group.getGroupInfoId(), groupId -> {
                    ProjectGroupInfo groupInfo = groupInfoMapper.selectById(groupId);
                    return groupInfo != null ? groupInfo.getName() : "未知";
                });

                List<Object> row = new ArrayList<>();
                row.add(group.getGroupInfoId());
                row.add(groupName);

                for (ScoringIndicator indicator : indicators) {
                    List<BigDecimal> scores = groupIndicatorScoreMap
                            .getOrDefault(group.getGroupInfoId(), Collections.emptyMap())
                            .getOrDefault(indicator.getId(), Collections.emptyList());

                    if (scores.isEmpty()) {
                        row.add("-");
                    } else {
                        BigDecimal sum = scores.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                        BigDecimal avg = sum.divide(BigDecimal.valueOf(scores.size()), 2, RoundingMode.HALF_UP);
                        row.add(formatDecimal(avg));
                    }
                }

                List<ScoringRecord> groupRecords = groupRecordMap.getOrDefault(group.getGroupInfoId(), Collections.emptyList());
                for (ScoringIndicatorCategory category : categories) {
                    List<Long> categoryIndicators = categoryIndicatorIds.getOrDefault(category.getId(), Collections.emptyList());
                    if (categoryIndicators.isEmpty()) {
                        row.add("-");
                        continue;
                    }

                    BigDecimal categoryTotalSum = BigDecimal.ZERO;
                    int validRecordCount = 0;

                    for (ScoringRecord groupRecord : groupRecords) {
                        Map<Long, BigDecimal> indicatorScoreMap = recordIndicatorScoreMap.get(groupRecord.getId());
                        if (indicatorScoreMap == null || indicatorScoreMap.isEmpty()) {
                            continue;
                        }

                        BigDecimal recordCategorySum = BigDecimal.ZERO;
                        boolean hasScore = false;
                        for (Long indicatorId : categoryIndicators) {
                            BigDecimal score = indicatorScoreMap.get(indicatorId);
                            if (score != null) {
                                recordCategorySum = recordCategorySum.add(score);
                                hasScore = true;
                            }
                        }

                        if (hasScore) {
                            categoryTotalSum = categoryTotalSum.add(recordCategorySum);
                            validRecordCount++;
                        }
                    }

                    if (validRecordCount == 0) {
                        row.add("-");
                    } else {
                        BigDecimal categoryAvg = categoryTotalSum
                                .divide(BigDecimal.valueOf(validRecordCount), 2, RoundingMode.HALF_UP);
                        row.add(formatDecimal(categoryAvg));
                    }
                }

                rows.add(row);
            }

            String fileName = project.getName() + "_小组评分汇总_" + DateUtil.format(new Date(), "yyyyMMddHHmmss");
            if ("csv".equalsIgnoreCase(format)) {
                exportCsv(response, fileName, rows);
            } else {
                exportExcel(response, fileName, rows);
            }

            log.info("导出项目小组评分汇总成功: projectId={}, format={}", projectId, format);
        } catch (Exception e) {
            log.error("导出项目小组评分汇总异常: projectId={}", projectId, e);
            writeExportError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "导出失败: " + e.getMessage());
        }
    }

    @Override
    public void exportAbnormalScoringRecords(String token, Long projectId, HttpServletResponse response) throws IOException {
        try {
            Long currentUserId = redisToken.getUserIdByToken(token);
            if (currentUserId == null) {
                writeExportError(response, HttpServletResponse.SC_UNAUTHORIZED, "Token无效");
                return;
            }

            Project project = projectMapper.selectById(projectId);
            if (project == null) {
                writeExportError(response, HttpServletResponse.SC_NOT_FOUND, "项目不存在");
                return;
            }

            List<Map<String, Object>> groupScoreRows = recordMapper.selectGroupScoreDetails(projectId);
            if (groupScoreRows == null || groupScoreRows.isEmpty()) {
                writeExportError(response, HttpServletResponse.SC_NOT_FOUND, "该项目暂无打分数据");
                return;
            }
            refreshMaliciousFlags(project, groupScoreRows);
            groupScoreRows = recordMapper.selectGroupScoreDetails(projectId);

            BigDecimal projectMean = calculateAverage(groupScoreRows.stream()
                    .map(row -> convertToBigDecimal(row.get("totalScore")))
                    .collect(Collectors.toList()));
            Map<Long, NormalizationStats> scorerStats = buildScorerStats(groupScoreRows, "userId", "totalScore");

            Map<Long, List<NormalizedScoreEntry>> groupedScores = new LinkedHashMap<>();
            for (Map<String, Object> row : groupScoreRows) {
                Long recordId = toLong(row.get("recordId"));
                Long groupId = toLong(row.get("groupId"));
                String groupName = row.get("groupName") != null ? row.get("groupName").toString() : "-";
                Long userId = toLong(row.get("userId"));
                BigDecimal rawScore = convertToBigDecimal(row.get("totalScore"));
                BigDecimal normalizedScore = normalizeScore(rawScore, scorerStats.get(userId), projectMean);
                LocalDateTime scoreTime = parseToLocalDateTime(row.get("scoreTime"));
                groupedScores.computeIfAbsent(groupId, key -> new ArrayList<>())
                        .add(new NormalizedScoreEntry(recordId, groupId, groupName, userId, rawScore, normalizedScore, scoreTime));
            }

            String maliciousRuleType = normalizeMaliciousRuleType(project.getMaliciousRuleType());
            String thresholdRule = buildThresholdRuleDesc(project);
            Map<Long, BigDecimal> autoDeviationMap = new HashMap<>();
            Map<Long, BigDecimal> autoThresholdMap = new HashMap<>();
            if (MALICIOUS_RULE_AUTO.equals(maliciousRuleType)) {
                for (List<NormalizedScoreEntry> entries : groupedScores.values()) {
                    if (entries == null || entries.isEmpty()) {
                        continue;
                    }
                    List<BigDecimal> rawScores = entries.stream()
                            .map(NormalizedScoreEntry::getRawScore)
                            .collect(Collectors.toList());
                    OutlierDetectionResult detectionResult = detectOutlierResult(rawScores);
                    for (Integer abnormalIndex : detectionResult.getAbnormalIndexes()) {
                        if (abnormalIndex == null || abnormalIndex < 0 || abnormalIndex >= entries.size()) {
                            continue;
                        }
                        NormalizedScoreEntry entry = entries.get(abnormalIndex);
                        BigDecimal deviation = detectionResult.getMedian().subtract(entry.getRawScore()).abs();
                        autoDeviationMap.put(entry.getRecordId(), scaleScore(deviation));
                        autoThresholdMap.put(entry.getRecordId(), scaleScore(detectionResult.getThreshold()));
                    }
                }
            }
            Map<Long, Integer> maliciousMap = groupScoreRows.stream()
                    .collect(Collectors.toMap(
                            row -> toLong(row.get("recordId")),
                            row -> {
                                Integer value = toInteger(row.get("isMalicious"));
                                return value == null ? 0 : value;
                            },
                            (a, b) -> b
                    ));
            List<AbnormalExportEntry> abnormalEntries = groupedScores.values().stream()
                    .flatMap(Collection::stream)
                    .filter(entry -> Objects.equals(maliciousMap.getOrDefault(entry.getRecordId(), 0), 1))
                    .map(entry -> new AbnormalExportEntry(
                            entry.getRecordId(),
                            entry.getSubjectId(),
                            entry.getSubjectName(),
                            entry.getScorerId(),
                            entry.getRawScore(),
                            entry.getNormalizedScore(),
                            autoDeviationMap.get(entry.getRecordId()),
                            autoThresholdMap.get(entry.getRecordId()),
                            entry.getScoreTime(),
                            MALICIOUS_RULE_THRESHOLD.equals(maliciousRuleType) ? thresholdRule : "x < median - 3*1.4826*MAD"
                    ))
                    .collect(Collectors.toList());

            if (abnormalEntries.isEmpty()) {
                writeExportError(response, HttpServletResponse.SC_NOT_FOUND,
                        "当前项目暂无被标记为异常的打分记录");
                return;
            }

            abnormalEntries.sort(Comparator.comparing(AbnormalExportEntry::getGroupId)
                    .thenComparing(AbnormalExportEntry::getScoreTime, Comparator.nullsLast(LocalDateTime::compareTo)));

            Map<Long, String> userNameCache = new HashMap<>();
            List<List<Object>> rows = new ArrayList<>();

            List<Object> header = new ArrayList<>();
            header.add("项目名称");
            header.add("记录ID");
            header.add("小组ID");
            header.add("小组名称");
            header.add("评委ID");
            header.add("评委姓名");
            header.add("原始总分");
            header.add("标准化后分数");
            header.add("偏差绝对值");
            header.add("异常阈值");
            header.add("打分时间");
            header.add("异常规则");
            rows.add(header);

            for (AbnormalExportEntry entry : abnormalEntries) {
                List<Object> row = new ArrayList<>();
                row.add(project.getName());
                row.add(entry.getRecordId());
                row.add(entry.getGroupId());
                row.add(entry.getGroupName());
                row.add(entry.getUserId());
                String userName = userNameCache.computeIfAbsent(entry.getUserId(), userId -> {
                    User user = userMapper.selectById(userId);
                    return user != null ? user.getName() : "未知";
                });
                row.add(userName);
                row.add(formatDecimal(entry.getRawScore()));
                row.add(formatDecimal(entry.getNormalizedScore()));
                row.add(formatDecimal(entry.getDeviation()));
                row.add(formatDecimal(entry.getThreshold()));
                row.add(entry.getScoreTime() != null ? DateUtil.format(entry.getScoreTime(), "yyyy-MM-dd HH:mm:ss") : "-");
                row.add(entry.getRuleDesc());
                rows.add(row);
            }

            String fileName = project.getName() + "_异常打分记录_" + DateUtil.format(new Date(), "yyyyMMddHHmmss");
            exportExcel(response, fileName, rows);

            log.info("导出项目异常打分记录成功: projectId={}, abnormalCount={}", projectId, abnormalEntries.size());
        } catch (Exception e) {
            log.error("导出项目异常打分记录异常: projectId={}", projectId, e);
            writeExportError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "导出失败: " + e.getMessage());
        }
    }

    private void writeExportError(HttpServletResponse response, int status, String message) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.resetBuffer();
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":" + status + ",\"message\":\""
                + escapeJson(message) + "\",\"data\":null}");
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '"' || ch == '\\') {
                builder.append('\\');
            }
            if (ch == '\n') {
                builder.append("\\n");
                continue;
            }
            if (ch == '\r') {
                builder.append("\\r");
                continue;
            }
            if (ch == '\t') {
                builder.append("\\t");
                continue;
            }
            builder.append(ch);
        }
        return builder.toString();
    }

    /**
     * 导出Excel
     */
    private void exportExcel(HttpServletResponse response, String fileName, List<List<Object>> rows) throws IOException {
        // 设置响应头
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setHeader("Content-Disposition", "attachment; filename=" + encodedFileName + ".xlsx");

        // 使用Hutool导出
        ExcelWriter writer = ExcelUtil.getWriter(true);

        // 写入数据
        writer.write(rows);

        // 输出到浏览器
        writer.flush(response.getOutputStream());
        writer.close();
    }

    /**
     * 导出CSV
     */
    private void exportCsv(HttpServletResponse response, String fileName, List<List<Object>> rows) throws IOException {
        // 设置响应头
        response.setContentType("text/csv");
        response.setCharacterEncoding("utf-8");
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setHeader("Content-Disposition", "attachment; filename=" + encodedFileName + ".csv");

        // 构建CSV内容
        StringBuilder csv = new StringBuilder();

        // 写入数据
        for (List<Object> row : rows) {
            csv.append(row.stream()
                            .map(cell -> {
                                if (cell == null) return "";
                                String str = cell.toString();
                                // 如果包含逗号或引号，用引号包裹
                                if (str.contains(",") || str.contains("\"")) {
                                    return "\"" + str.replace("\"", "\"\"") + "\"";
                                }
                                return str;
                            })
                            .collect(Collectors.joining(",")))
                    .append("\n");
        }

        // 输出到浏览器
        response.getWriter().write(csv.toString());
    }

    private List<ProjectStatisticsVO.GroupAverageVO> buildGroupAverageStatistics(List<Map<String, Object>> groupScoreRows) {
        if (groupScoreRows == null || groupScoreRows.isEmpty()) {
            return Collections.emptyList();
        }

        BigDecimal projectMean = calculateAverage(groupScoreRows.stream()
                .map(row -> convertToBigDecimal(row.get("totalScore")))
                .collect(Collectors.toList()));
        Map<Long, NormalizationStats> scorerStats = buildScorerStats(groupScoreRows, "userId", "totalScore");

        Map<Long, List<NormalizedScoreEntry>> groupedScores = new LinkedHashMap<>();
        for (Map<String, Object> row : groupScoreRows) {
            Long groupId = toLong(row.get("groupId"));
            String groupName = row.get("groupName") != null ? row.get("groupName").toString() : "-";
            Long userId = toLong(row.get("userId"));
            BigDecimal rawScore = convertToBigDecimal(row.get("totalScore"));
            BigDecimal normalizedScore = normalizeScore(rawScore, scorerStats.get(userId), projectMean);
            Integer isMalicious = toInteger(row.get("isMalicious"));
            groupedScores.computeIfAbsent(groupId, key -> new ArrayList<>())
                    .add(new NormalizedScoreEntry(groupId, groupName, userId, rawScore, normalizedScore)
                            .setAbnormal(isMalicious != null && isMalicious == 1));
        }

        return groupedScores.values().stream()
                .map(entries -> {
                    NormalizedScoreEntry first = entries.get(0);
                    RobustScoreSummary summary = summarizeScores(entries);
                    return new ProjectStatisticsVO.GroupAverageVO()
                            .setGroupId(first.getSubjectId())
                            .setGroupName(first.getSubjectName())
                            .setAverageScore(summary.getProcessedAverageScore())
                            .setRawAverageScore(summary.getRawAverageScore())
                            .setNormalizedAverageScore(summary.getNormalizedAverageScore())
                            .setProcessedAverageScore(summary.getProcessedAverageScore())
                            .setAbnormalCount(summary.getAbnormalCount())
                            .setSampleSize(summary.getSampleSize())
                            .setValidSampleSize(summary.getValidSampleSize());
                })
                .sorted(Comparator.comparing(ProjectStatisticsVO.GroupAverageVO::getAverageScore,
                        Comparator.nullsLast(BigDecimal::compareTo)).reversed())
                .collect(Collectors.toList());
    }

    private List<ProjectStatisticsVO.IndicatorAverageVO> buildIndicatorAverageStatistics(List<Map<String, Object>> indicatorScoreRows) {
        if (indicatorScoreRows == null || indicatorScoreRows.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, List<Map<String, Object>>> indicatorGroups = indicatorScoreRows.stream()
                .collect(Collectors.groupingBy(row -> toLong(row.get("indicatorId")), LinkedHashMap::new, Collectors.toList()));

        return indicatorGroups.values().stream()
                .map((List<Map<String, Object>> rows) -> {
                    BigDecimal indicatorMean = calculateAverage(rows.stream()
                            .map(row -> convertToBigDecimal(row.get("score")))
                            .collect(Collectors.toList()));
                    Map<Long, NormalizationStats> scorerStats = buildScorerStats(rows, "userId", "score");
                    List<NormalizedScoreEntry> entries = rows.stream()
                            .map(row -> {
                                Long indicatorId = toLong(row.get("indicatorId"));
                                String indicatorName = row.get("indicatorName") != null ? row.get("indicatorName").toString() : "-";
                                Long userId = toLong(row.get("userId"));
                                BigDecimal rawScore = convertToBigDecimal(row.get("score"));
                                BigDecimal normalizedScore = normalizeScore(rawScore, scorerStats.get(userId), indicatorMean);
                                Integer isMalicious = toInteger(row.get("isMalicious"));
                                return new NormalizedScoreEntry(indicatorId, indicatorName, userId, rawScore, normalizedScore)
                                        .setAbnormal(isMalicious != null && isMalicious == 1);
                            })
                            .collect(Collectors.toList());

                    RobustScoreSummary summary = summarizeScores(entries);
                int indicatorAbnormalCount = summary.getAbnormalCount();
                BigDecimal indicatorProcessedAverage = summary.getProcessedAverageScore();
                int indicatorValidSampleSize = summary.getValidSampleSize();
                    NormalizedScoreEntry first = entries.get(0);
                    return new ProjectStatisticsVO.IndicatorAverageVO()
                            .setIndicatorId(first.getSubjectId())
                            .setIndicatorName(first.getSubjectName())
                            .setAverageScore(indicatorProcessedAverage)
                            .setRawAverageScore(summary.getRawAverageScore())
                            .setNormalizedAverageScore(summary.getNormalizedAverageScore())
                            .setProcessedAverageScore(indicatorProcessedAverage)
                            .setAbnormalCount(indicatorAbnormalCount)
                            .setTotalAbnormalCount(summary.getAbnormalCount())
                            .setSampleSize(summary.getSampleSize())
                            .setValidSampleSize(indicatorValidSampleSize);
                })
                .sorted(Comparator.comparing(ProjectStatisticsVO.IndicatorAverageVO::getAverageScore,
                        Comparator.nullsLast(BigDecimal::compareTo)).reversed())
                .collect(Collectors.toList());
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

    private RobustScoreSummary summarizeScores(List<NormalizedScoreEntry> entries) {
        List<BigDecimal> rawScores = entries.stream()
                .map(NormalizedScoreEntry::getRawScore)
                .collect(Collectors.toList());
        List<BigDecimal> normalizedScores = entries.stream()
                .map(NormalizedScoreEntry::getNormalizedScore)
                .collect(Collectors.toList());

        List<BigDecimal> validScores = entries.stream()
                .filter(entry -> !entry.isAbnormal())
                .map(NormalizedScoreEntry::getNormalizedScore)
                .collect(Collectors.toList());
        int abnormalCount = (int) entries.stream().filter(NormalizedScoreEntry::isAbnormal).count();

        BigDecimal rawAverage = calculateAverage(rawScores);
        BigDecimal normalizedAverage = calculateAverage(normalizedScores);
        BigDecimal processedAverage = validScores.isEmpty() ? normalizedAverage : calculateAverage(validScores);

        return new RobustScoreSummary(
                scaleScore(rawAverage),
                scaleScore(normalizedAverage),
                scaleScore(processedAverage),
                abnormalCount,
                entries.size(),
                validScores.size()
        );
    }

    private void refreshMaliciousFlags(Project project, List<Map<String, Object>> groupScoreRows) {
        if (project == null || project.getId() == null) {
            return;
        }
        String maliciousRuleType = normalizeMaliciousRuleType(project.getMaliciousRuleType());
        Long projectId = project.getId();
        if (MALICIOUS_RULE_THRESHOLD.equals(maliciousRuleType)) {
            BigDecimal lower = project.getMaliciousScoreLower();
            BigDecimal upper = project.getMaliciousScoreUpper();
            if (lower == null || upper == null || lower.compareTo(upper) > 0) {
                log.warn("项目阈值模式配置异常，回退AUTO算法: projectId={}, lower={}, upper={}",
                        projectId, lower, upper);
            } else {
                recordMapper.markMaliciousByThreshold(projectId, lower, upper);
                return;
            }
        }

        recordMapper.clearMaliciousFlagByProjectId(projectId);
        if (groupScoreRows == null || groupScoreRows.isEmpty()) {
            return;
        }

        Map<Long, List<Map<String, Object>>> groupRowsMap = groupScoreRows.stream()
                .collect(Collectors.groupingBy(row -> toLong(row.get("groupId")), LinkedHashMap::new, Collectors.toList()));

        Set<Long> maliciousRecordIds = new LinkedHashSet<>();
        for (List<Map<String, Object>> rows : groupRowsMap.values()) {
            if (rows == null || rows.isEmpty()) {
                continue;
            }
            List<BigDecimal> rawScores = rows.stream()
                    .map(row -> convertToBigDecimal(row.get("totalScore")))
                    .collect(Collectors.toList());
            OutlierDetectionResult detectionResult = detectOutlierResult(rawScores);
            for (Integer abnormalIndex : detectionResult.getAbnormalIndexes()) {
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
    }

    private String normalizeMaliciousRuleType(String ruleType) {
        if (ruleType == null || ruleType.isBlank()) {
            return MALICIOUS_RULE_AUTO;
        }
        String normalized = ruleType.trim().toUpperCase();
        if (!MALICIOUS_RULE_THRESHOLD.equals(normalized)) {
            return MALICIOUS_RULE_AUTO;
        }
        return normalized;
    }

    private OutlierDetectionResult detectOutlierResult(List<BigDecimal> scores) {
        if (scores == null || scores.size() < 4) {
            return new OutlierDetectionResult(Collections.emptySet(), BigDecimal.ZERO, BigDecimal.ZERO);
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

        // 低分单侧异常：优先识别恶意压分，避免高分或中间分被对称规则误伤
        BigDecimal lowerBound = median.subtract(threshold);
        Set<Integer> abnormalIndexes = new HashSet<>();
        for (int i = 0; i < scores.size(); i++) {
            if (scores.get(i).compareTo(lowerBound) < 0) {
                abnormalIndexes.add(i);
            }
        }
        return new OutlierDetectionResult(abnormalIndexes, median, threshold);
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

    private BigDecimal calculateMedian(List<BigDecimal> scores) {
        if (scores == null || scores.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        List<BigDecimal> sortedScores = scores.stream()
                .sorted(BigDecimal::compareTo)
                .collect(Collectors.toList());
        int middle = sortedScores.size() / 2;
        if (sortedScores.size() % 2 == 0) {
            return sortedScores.get(middle - 1)
                    .add(sortedScores.get(middle))
                    .divide(BigDecimal.valueOf(2L), 6, RoundingMode.HALF_UP);
        }
        return sortedScores.get(middle);
    }

    private BigDecimal scaleScore(BigDecimal score) {
        if (score == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return score.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 转换为BigDecimal
     */
    private BigDecimal convertToBigDecimal(Object obj) {
        if (obj == null) return BigDecimal.ZERO;
        if (obj instanceof BigDecimal) return (BigDecimal) obj;
        if (obj instanceof Number) return BigDecimal.valueOf(((Number) obj).doubleValue());
        return new BigDecimal(obj.toString());
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

    private static class NormalizedScoreEntry {
        private final Long recordId;
        private final Long subjectId;
        private final String subjectName;
        private final Long scorerId;
        private final BigDecimal rawScore;
        private final BigDecimal normalizedScore;
        private final LocalDateTime scoreTime;
        private boolean abnormal;

        private NormalizedScoreEntry(Long subjectId, String subjectName, Long scorerId, BigDecimal rawScore, BigDecimal normalizedScore) {
            this.recordId = null;
            this.subjectId = subjectId;
            this.subjectName = subjectName;
            this.scorerId = scorerId;
            this.rawScore = rawScore;
            this.normalizedScore = normalizedScore;
            this.scoreTime = null;
        }

        private NormalizedScoreEntry(Long recordId, Long subjectId, String subjectName, Long scorerId,
                                     BigDecimal rawScore, BigDecimal normalizedScore, LocalDateTime scoreTime) {
            this.recordId = recordId;
            this.subjectId = subjectId;
            this.subjectName = subjectName;
            this.scorerId = scorerId;
            this.rawScore = rawScore;
            this.normalizedScore = normalizedScore;
            this.scoreTime = scoreTime;
        }

        public Long getRecordId() {
            return recordId;
        }

        public Long getSubjectId() {
            return subjectId;
        }

        public String getSubjectName() {
            return subjectName;
        }

        public Long getScorerId() {
            return scorerId;
        }

        public BigDecimal getRawScore() {
            return rawScore;
        }

        public BigDecimal getNormalizedScore() {
            return normalizedScore;
        }

        public LocalDateTime getScoreTime() {
            return scoreTime;
        }

        public boolean isAbnormal() {
            return abnormal;
        }

        public NormalizedScoreEntry setAbnormal(boolean abnormal) {
            this.abnormal = abnormal;
            return this;
        }
    }

    private static class RobustScoreSummary {
        private final BigDecimal rawAverageScore;
        private final BigDecimal normalizedAverageScore;
        private final BigDecimal processedAverageScore;
        private final int abnormalCount;
        private final int sampleSize;
        private final int validSampleSize;

        private RobustScoreSummary(BigDecimal rawAverageScore, BigDecimal normalizedAverageScore,
                                   BigDecimal processedAverageScore, int abnormalCount,
                                   int sampleSize, int validSampleSize) {
            this.rawAverageScore = rawAverageScore;
            this.normalizedAverageScore = normalizedAverageScore;
            this.processedAverageScore = processedAverageScore;
            this.abnormalCount = abnormalCount;
            this.sampleSize = sampleSize;
            this.validSampleSize = validSampleSize;
        }

        public BigDecimal getRawAverageScore() {
            return rawAverageScore;
        }

        public BigDecimal getNormalizedAverageScore() {
            return normalizedAverageScore;
        }

        public BigDecimal getProcessedAverageScore() {
            return processedAverageScore;
        }

        public int getAbnormalCount() {
            return abnormalCount;
        }

        public int getSampleSize() {
            return sampleSize;
        }

        public int getValidSampleSize() {
            return validSampleSize;
        }
    }

    private static class OutlierDetectionResult {
        private final Set<Integer> abnormalIndexes;
        private final BigDecimal median;
        private final BigDecimal threshold;

        private OutlierDetectionResult(Set<Integer> abnormalIndexes, BigDecimal median, BigDecimal threshold) {
            this.abnormalIndexes = abnormalIndexes;
            this.median = median;
            this.threshold = threshold;
        }

        public Set<Integer> getAbnormalIndexes() {
            return abnormalIndexes;
        }

        public BigDecimal getMedian() {
            return median;
        }

        public BigDecimal getThreshold() {
            return threshold;
        }
    }

    private static class AbnormalExportEntry {
        private final Long recordId;
        private final Long groupId;
        private final String groupName;
        private final Long userId;
        private final BigDecimal rawScore;
        private final BigDecimal normalizedScore;
        private final BigDecimal deviation;
        private final BigDecimal threshold;
        private final LocalDateTime scoreTime;
        private final String ruleDesc;

        private AbnormalExportEntry(Long recordId, Long groupId, String groupName, Long userId,
                                    BigDecimal rawScore, BigDecimal normalizedScore,
                                    BigDecimal deviation, BigDecimal threshold, LocalDateTime scoreTime,
                                    String ruleDesc) {
            this.recordId = recordId;
            this.groupId = groupId;
            this.groupName = groupName;
            this.userId = userId;
            this.rawScore = rawScore;
            this.normalizedScore = normalizedScore;
            this.deviation = deviation;
            this.threshold = threshold;
            this.scoreTime = scoreTime;
            this.ruleDesc = ruleDesc;
        }

        public Long getRecordId() {
            return recordId;
        }

        public Long getGroupId() {
            return groupId;
        }

        public String getGroupName() {
            return groupName;
        }

        public Long getUserId() {
            return userId;
        }

        public BigDecimal getRawScore() {
            return rawScore;
        }

        public BigDecimal getNormalizedScore() {
            return normalizedScore;
        }

        public BigDecimal getDeviation() {
            return deviation;
        }

        public BigDecimal getThreshold() {
            return threshold;
        }

        public LocalDateTime getScoreTime() {
            return scoreTime;
        }

        public String getRuleDesc() {
            return ruleDesc;
        }
    }


    private List<ScoringIndicator> getProjectIndicators(Project project) {
        if (project.getStandardId() == null) {
            return Collections.emptyList();
        }
        List<ScoringIndicator> indicators = indicatorMapper.selectByStandardId(project.getStandardId());
        return indicators != null ? indicators : Collections.emptyList();
    }

    private String getScoringStandardName(Project project) {
        if (project.getStandardId() == null) {
            return "-";
        }
        ScoringStandard standard = standardMapper.selectById(project.getStandardId());
        return standard != null ? standard.getName() : "-";
    }

    private String formatDecimal(BigDecimal value) {
        if (value == null) {
            return "-";
        }
        BigDecimal normalized = value.stripTrailingZeros();
        return normalized.scale() < 0 ? normalized.setScale(0).toPlainString() : normalized.toPlainString();
    }

    private String buildThresholdRuleDesc(Project project) {
        if (project == null) {
            return "阈值模式";
        }
        if (project.getMaliciousScoreLower() == null || project.getMaliciousScoreUpper() == null) {
            return "阈值模式";
        }
        return "x < " + formatDecimal(project.getMaliciousScoreLower()) +
                " 或 x > " + formatDecimal(project.getMaliciousScoreUpper());
    }

    private Long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    private Integer toInteger(Object value) {
        log.info("TRANSFORMing {}",value);
        if (value == null) {
            return 0;
        }
        if (value instanceof Boolean bool) {
            return bool ? 1 : 0; // true=1，false=0
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(value.toString());
    }

    private BigDecimal toScaledBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal decimalValue;
        if (value instanceof BigDecimal bigDecimal) {
            decimalValue = bigDecimal;
        } else if (value instanceof Number number) {
            decimalValue = BigDecimal.valueOf(number.doubleValue());
        } else {
            decimalValue = new BigDecimal(value.toString());
        }
        return decimalValue.setScale(2, RoundingMode.HALF_UP);
    }

    public void warmupPlatformStatisticsCache() {
        try {
            String cacheKey = RedisKeyUtil.PLATFORM_STATISTICS_KEY;
            if (redisBaseUtil.get(cacheKey) != null) {
                return;
            }

            final int days = 30;
            LocalDate today = LocalDate.now();
            LocalDate startDate = today.minusDays(days - 1L);
            LocalDateTime startTime = startDate.atStartOfDay();
            LocalDateTime endTime = today.plusDays(1L).atStartOfDay();

            Long baseProjects = projectMapper.countProjectsBefore(startTime);
            Map<LocalDate, Long> dailyProjectMap = toDailyLongMap(
                    projectMapper.selectDailyProjectCount(startTime, endTime), "cnt");
            Map<LocalDate, Long> dailyScoreMap = toDailyLongMap(
                    projectMapper.selectDailyScoreCount(startTime, endTime), "cnt");
            Map<LocalDate, BigDecimal> dailyAverageScoreMap = toDailyBigDecimalMap(
                    projectMapper.selectDailyAverageScore(startTime, endTime), "avgScore");

            List<String> dateAxis = new ArrayList<>(days);
            List<Long> projectTrend = new ArrayList<>(days);
            List<Long> scoreTrend = new ArrayList<>(days);
            List<BigDecimal> averageScoreTrend = new ArrayList<>(days);

            long runningProjects = baseProjects != null ? baseProjects : 0L;
            for (int i = 0; i < days; i++) {
                LocalDate date = startDate.plusDays(i);
                dateAxis.add(date.toString());

                long todayProjectIncrease = dailyProjectMap.getOrDefault(date, 0L);
                runningProjects += todayProjectIncrease;
                projectTrend.add(runningProjects);

                scoreTrend.add(dailyScoreMap.getOrDefault(date, 0L));
                averageScoreTrend.add(dailyAverageScoreMap.getOrDefault(
                        date, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)));
            }

            PlatformStatisticsVO vo = new PlatformStatisticsVO()
                    .setDates(dateAxis)
                    .setProjectTrend(projectTrend)
                    .setScoreTrend(scoreTrend)
                    .setAverageScoreTrend(averageScoreTrend);

            redisBaseUtil.set(cacheKey, vo, RedisKeyUtil.PLATFORM_STATISTICS_TTL);
            log.info("全局热键预热完成: key={}, points={}", cacheKey, days);
        } catch (Exception e) {
            log.error("全局热键预热异常: key={}", RedisKeyUtil.PLATFORM_STATISTICS_KEY, e);
        }
    }

    private Map<LocalDate, Long> toDailyLongMap(List<Map<String, Object>> rows, String valueKey) {
        Map<LocalDate, Long> result = new HashMap<>();
        if (rows == null || rows.isEmpty()) {
            return result;
        }
        for (Map<String, Object> row : rows) {
            LocalDate date = parseStatDate(row.get("statDate"));
            if (date == null) {
                continue;
            }
            result.put(date, toLong(row.get(valueKey)));
        }
        return result;
    }

    private Map<LocalDate, BigDecimal> toDailyBigDecimalMap(List<Map<String, Object>> rows, String valueKey) {
        Map<LocalDate, BigDecimal> result = new HashMap<>();
        if (rows == null || rows.isEmpty()) {
            return result;
        }
        for (Map<String, Object> row : rows) {
            LocalDate date = parseStatDate(row.get("statDate"));
            if (date == null) {
                continue;
            }
            result.put(date, toScaledBigDecimal(row.get(valueKey)));
        }
        return result;
    }

    private LocalDate parseStatDate(Object dateObj) {
        if (dateObj == null) {
            return null;
        }
        if (dateObj instanceof LocalDate localDate) {
            return localDate;
        }
        if (dateObj instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        if (dateObj instanceof java.util.Date utilDate) {
            return utilDate.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        }
        return LocalDate.parse(dateObj.toString());
    }

    private LocalDateTime parseToLocalDateTime(Object dateObj) {
        if (dateObj == null) {
            return null;
        }
        if (dateObj instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (dateObj instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (dateObj instanceof java.util.Date utilDate) {
            return utilDate.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
        }
        return LocalDateTime.parse(dateObj.toString().replace(" ", "T"));
    }
}
