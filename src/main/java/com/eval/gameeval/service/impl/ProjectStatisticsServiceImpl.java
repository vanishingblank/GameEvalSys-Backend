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

            // 3. 查询小组平均分
            List<Map<String, Object>> groupAvgList = recordMapper.selectGroupAverage(projectId);
            List<ProjectStatisticsVO.GroupAverageVO> groupAverage = groupAvgList.stream()
                    .map(map -> {
                        ProjectStatisticsVO.GroupAverageVO vo = new ProjectStatisticsVO.GroupAverageVO();
                        vo.setGroupId(((Number) map.get("groupId")).longValue());
                        vo.setGroupName((String) map.get("groupName"));
                        vo.setAverageScore(convertToBigDecimal(map.get("averageScore")));
                        return vo;
                    })
                    .collect(Collectors.toList());

            // 4. 查询指标平均分
            List<Map<String, Object>> indicatorAvgList = recordMapper.selectIndicatorAverage(projectId);
            List<ProjectStatisticsVO.IndicatorAverageVO> indicatorAverage = indicatorAvgList.stream()
                    .map(map -> {
                        ProjectStatisticsVO.IndicatorAverageVO vo = new ProjectStatisticsVO.IndicatorAverageVO();
                        vo.setIndicatorId(((Number) map.get("indicatorId")).longValue());
                        vo.setIndicatorName((String) map.get("indicatorName"));
                        vo.setAverageScore(convertToBigDecimal(map.get("averageScore")));
                        return vo;
                    })
                    .collect(Collectors.toList());

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
                throw new RuntimeException("Token无效");
            }

            // 2. 验证项目是否存在
            Project project = projectMapper.selectById(projectId);
            if (project == null) {
                throw new RuntimeException("项目不存在");
            }

            // 3. 查询所有打分记录
            List<ScoringRecord> records = recordMapper.selectByProjectId(projectId);
            if (records == null || records.isEmpty()) {
                throw new RuntimeException("该项目暂无打分数据");
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
            throw new IOException("导出失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void exportProjectGroupIndicatorItemScores(String token, Long projectId, String format, HttpServletResponse response) throws IOException {
        try {
            Long currentUserId = redisToken.getUserIdByToken(token);
            if (currentUserId == null) {
                throw new RuntimeException("Token无效");
            }

            Project project = projectMapper.selectById(projectId);
            if (project == null) {
                throw new RuntimeException("项目不存在");
            }

            List<ProjectGroup> projectGroups = groupMapper.selectByProjectId(projectId);
            if (projectGroups == null || projectGroups.isEmpty()) {
                throw new RuntimeException("该项目暂无小组数据");
            }

            List<ScoringRecord> records = recordMapper.selectByProjectId(projectId);
            if (records == null || records.isEmpty()) {
                throw new RuntimeException("该项目暂无打分数据");
            }

            List<ScoringIndicator> indicators = getProjectIndicators(project);
            if (indicators.isEmpty()) {
                throw new RuntimeException("该项目未配置评分项");
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
            throw new IOException("导出失败: " + e.getMessage(), e);
        }
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

    /**
     * 转换为BigDecimal
     */
    private BigDecimal convertToBigDecimal(Object obj) {
        if (obj == null) return BigDecimal.ZERO;
        if (obj instanceof BigDecimal) return (BigDecimal) obj;
        if (obj instanceof Number) return BigDecimal.valueOf(((Number) obj).doubleValue());
        return new BigDecimal(obj.toString());
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

    private Long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
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
}
