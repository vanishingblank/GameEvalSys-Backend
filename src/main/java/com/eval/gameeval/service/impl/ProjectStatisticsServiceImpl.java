package com.eval.gameeval.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.poi.excel.ExcelUtil;
import cn.hutool.poi.excel.ExcelWriter;
import com.eval.gameeval.mapper.*;
import com.eval.gameeval.models.VO.ProjectStatisticsVO;
import com.eval.gameeval.models.VO.ResponseVO;
import com.eval.gameeval.models.entity.*;
import com.eval.gameeval.service.IProjectStatisticsService;
import com.eval.gameeval.util.RedisToken;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
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
    private ProjectMapper projectMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private RedisToken redisToken;

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

            // 4. 查询所有明细
            List<ScoringRecordDetail> allDetails = new ArrayList<>();
            for (ScoringRecord record : records) {
                List<ScoringRecordDetail> details = detailMapper.selectByRecordId(record.getId());
                allDetails.addAll(details);
            }

            // 5. 构建Excel数据
            List<List<Object>> rows = new ArrayList<>();

            // 表头
            List<Object> header = Arrays.asList(
                    "项目名称", "小组名称", "打分用户", "打分时间",
                    "指标1", "指标2", "指标3", "总分"
            );
            rows.add(header);

            // 数据行
            for (ScoringRecord record : records) {
                List<Object> row = new ArrayList<>();

                // 项目名称
                row.add(project.getName());

                // 小组名称
                ProjectGroup group = groupMapper.selectById(record.getGroupId());
                row.add(group != null ? group.getName() : "未知");

                // 打分用户
                User user = userMapper.selectById(record.getUserId());
                row.add(user != null ? user.getName() : "未知");

                // 打分时间
                row.add(DateUtil.format(record.getCreateTime(), "yyyy-MM-dd HH:mm:ss"));

                // 指标得分（假设最多3个指标）
                List<ScoringRecordDetail> details = allDetails.stream()
                        .filter(d -> d.getRecordId().equals(record.getId()))
                        .sorted(Comparator.comparing(ScoringRecordDetail::getIndicatorId))
                        .toList();

                for (int i = 0; i < 3; i++) {
                    if (i < details.size()) {
                        row.add(details.get(i).getScore());
                    } else {
                        row.add("-");
                    }
                }

                // 总分
                row.add(record.getTotalScore());

                rows.add(row);
            }

            // 6. 导出文件
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
}