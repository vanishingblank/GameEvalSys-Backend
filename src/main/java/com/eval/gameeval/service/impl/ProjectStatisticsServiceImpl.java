package com.eval.gameeval.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.poi.excel.ExcelUtil;
import cn.hutool.poi.excel.ExcelWriter;
import com.eval.gameeval.mapper.*;
import com.eval.gameeval.models.VO.GroupIndicatorStatisticsVO;
import com.eval.gameeval.models.VO.ProjectStatisticsVO;
import com.eval.gameeval.models.VO.ResponseVO;
import com.eval.gameeval.models.VO.ScoringOverviewVO;
import com.eval.gameeval.models.entity.*;
import com.eval.gameeval.service.IProjectStatisticsService;
import com.eval.gameeval.util.RedisToken;
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
    private ScoringStandardMapper standardMapper;

    @Resource
    private RedisToken redisToken;

    @Resource
    private ScoringOverviewCacheUtil scoringOverviewCacheUtil;

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

            String standardName = getScoringStandardName(project);
            List<Long> recordIds = records.stream().map(ScoringRecord::getId).toList();
            List<ScoringRecordDetail> allDetails = detailMapper.selectByRecordIds(recordIds);

            Map<Long, ScoringRecord> recordMap = records.stream()
                    .collect(Collectors.toMap(ScoringRecord::getId, r -> r));
            Map<Long, Map<Long, List<BigDecimal>>> groupIndicatorScoreMap = new HashMap<>();

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
            }

            Map<Long, String> groupNameCache = new HashMap<>();
            List<List<Object>> rows = new ArrayList<>();
            rows.add(Arrays.asList("项目名称", "评分标准", "小组名称", "评分项", "每项得分明细", "平均分", "评分次数"));

            for (ProjectGroup group : projectGroups) {
                String groupName = groupNameCache.computeIfAbsent(group.getGroupInfoId(), groupId -> {
                    ProjectGroupInfo groupInfo = groupInfoMapper.selectById(groupId);
                    return groupInfo != null ? groupInfo.getName() : "未知";
                });

                for (ScoringIndicator indicator : indicators) {
                    List<BigDecimal> scores = groupIndicatorScoreMap
                            .getOrDefault(group.getGroupInfoId(), Collections.emptyMap())
                            .getOrDefault(indicator.getId(), Collections.emptyList());

                    String scoreDetails = scores.isEmpty()
                            ? "-"
                            : scores.stream().map(this::formatDecimal).collect(Collectors.joining(", "));

                    String averageScore = "-";
                    if (!scores.isEmpty()) {
                        BigDecimal sum = scores.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                        averageScore = formatDecimal(sum.divide(BigDecimal.valueOf(scores.size()), 2, RoundingMode.HALF_UP));
                    }

                    rows.add(Arrays.asList(
                            project.getName(),
                            standardName,
                            groupName,
                            indicator.getName(),
                            scoreDetails,
                            averageScore,
                            scores.size()
                    ));
                }
            }

            String fileName = project.getName() + "_小组评分项明细_" + DateUtil.format(new Date(), "yyyyMMddHHmmss");
            if ("csv".equalsIgnoreCase(format)) {
                exportCsv(response, fileName, rows);
            } else {
                exportExcel(response, fileName, rows);
            }

            log.info("导出项目小组评分项明细成功: projectId={}, format={}", projectId, format);
        } catch (Exception e) {
            log.error("导出项目小组评分项明细异常: projectId={}", projectId, e);
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
}
