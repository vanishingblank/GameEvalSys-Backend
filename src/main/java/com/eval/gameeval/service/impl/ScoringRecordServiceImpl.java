package com.eval.gameeval.service.impl;

import com.eval.gameeval.mapper.*;
import com.eval.gameeval.models.DTO.Scoring.ScoringRecordCreateDTO;
import com.eval.gameeval.models.DTO.Scoring.ScoringRecordPageQueryDTO;
import com.eval.gameeval.models.DTO.Scoring.ScoringRecordQueryDTO;
import com.eval.gameeval.models.VO.ResponseVO;
import com.eval.gameeval.models.VO.ScoringRecordPageVO;
import com.eval.gameeval.models.VO.ScoringRecordVO;
import com.eval.gameeval.models.entity.*;
import com.eval.gameeval.service.IScoringRecordService;
import com.eval.gameeval.util.RedisKeyUtil;
import com.eval.gameeval.util.ScoringOverviewCacheUtil;
import com.eval.gameeval.util.ScoringRecordCacheUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ScoringRecordServiceImpl implements IScoringRecordService {
    private static final String MALICIOUS_RULE_AUTO = "AUTO";
    private static final String MALICIOUS_RULE_THRESHOLD = "THRESHOLD";

    @Autowired
    private ScoringRecordMapper recordMapper;

    @Autowired
    private ScoringRecordDetailMapper detailMapper;

    @Autowired
    private ProjectMapper projectMapper;

    @Autowired
    private ProjectGroupMapper groupMapper;

    @Autowired
    private ProjectGroupInfoMapper groupInfoMapper;

    @Autowired
    private ProjectScorerMapper scorerMapper;

    @Autowired
    private ScoringIndicatorMapper indicatorMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ScoringRecordCacheUtil scoringRecordCacheUtil;

    @Autowired
    private ScoringOverviewCacheUtil scoringOverviewCacheUtil;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseVO<ScoringRecordVO> submitScore(Long currentUserId, ScoringRecordCreateDTO request) {
        try {
            // 1. 验证登录态
            if (currentUserId == null) {
                return ResponseVO.unauthorized("Token无效");
            }

            User currentUser = userMapper.selectById(currentUserId);
            if (currentUser == null) {
                return ResponseVO.unauthorized("用户不存在");
            }

            // 2. 验证项目是否存在
            Project project = projectMapper.selectById(request.getProjectId());
            if (project == null) {
                return ResponseVO.badRequest("项目不存在");
            }
            // 3. 验证项目状态（必须是进行中）
            if (!"ongoing".equals(project.getStatus())) {
                return ResponseVO.badRequest("项目未开始或已结束，无法打分");
            }
            LocalDateTime now = LocalDateTime.now();
            if (now.isBefore(project.getStartDate())) {
                return ResponseVO.badRequest("项目尚未开始，无法打分");
            }
            if (now.isAfter(project.getEndDate())) {
                return ResponseVO.badRequest("项目已结束，无法打分");
            }
            // 4. 验证小组是否存在（project_group_info）
            ProjectGroupInfo groupInfo = groupInfoMapper.selectById(request.getGroupId());
            if (groupInfo == null) {
                return ResponseVO.badRequest("小组不存在");
            }

            // 5. 验证该小组是否在该项目中关联
            List<ProjectGroup> projectGroups = groupMapper.selectByProjectId(request.getProjectId());
            boolean groupExistsInProject = projectGroups.stream()
                    .anyMatch(pg -> pg.getGroupInfoId().equals(request.getGroupId()));
            if (!groupExistsInProject) {
                return ResponseVO.badRequest("小组不属于该项目");
            }

            // 6. 验证当前用户是否有权限打分（是否是该项目的打分用户）
            boolean isScorer = scorerMapper.selectByProjectId(request.getProjectId()).stream()
                    .anyMatch(scorer -> scorer.getUserId().equals(currentUserId));
            if (!isScorer) {
                return ResponseVO.forbidden("您没有权限为该项目打分");
            }

            // 7. 验证指标是否属于该项目的打分标准
            List<ScoringIndicator> indicators = indicatorMapper.selectByStandardId(project.getStandardId());
            List<Long> validIndicatorIds = indicators.stream()
                    .map(ScoringIndicator::getId)
                    .collect(Collectors.toList());

            BigDecimal totalScore = BigDecimal.ZERO;
            List<ScoringRecordDetail> details = new ArrayList<>();

            for (ScoringRecordCreateDTO.ScoreDTO scoreDTO : request.getScores()) {
                // 验证指标是否存在
                if (!validIndicatorIds.contains(scoreDTO.getIndicatorId())) {
                    return ResponseVO.badRequest("指标ID " + scoreDTO.getIndicatorId() + " 不属于该项目的打分标准");
                }

                // 获取指标详情
                ScoringIndicator indicator = indicators.stream()
                        .filter(i -> i.getId().equals(scoreDTO.getIndicatorId()))
                        .findFirst()
                        .orElse(null);

                if (indicator == null) {
                    return ResponseVO.badRequest("指标ID " + scoreDTO.getIndicatorId() + " 不存在");
                }

                // 验证打分值是否在范围内
                BigDecimal score = scoreDTO.getScore();
                if (score.compareTo(BigDecimal.valueOf(indicator.getMinScore())) < 0 ||
                        score.compareTo(BigDecimal.valueOf(indicator.getMaxScore())) > 0) {
                    return ResponseVO.badRequest(
                            String.format("指标 %s 的打分值 %.2f 超出范围 [%d, %d]",
                                    indicator.getName(), score, indicator.getMinScore(), indicator.getMaxScore())
                    );
                }

                totalScore = totalScore.add(score);

                // 构建明细
                ScoringRecordDetail detail = new ScoringRecordDetail();
                detail.setIndicatorId(scoreDTO.getIndicatorId());
                detail.setScore(score);
                details.add(detail);
            }

            // 8. 检查是否已存在打分记录（修改）
            String maliciousRuleType = normalizeMaliciousRuleType(project.getMaliciousRuleType());
            Integer maliciousFlag = MALICIOUS_RULE_THRESHOLD.equals(maliciousRuleType)
                    ? (isScoreMaliciousByThreshold(totalScore, project.getMaliciousScoreLower(), project.getMaliciousScoreUpper()) ? 1 : 0)
                    : 0;

            ScoringRecord existingRecord = recordMapper.selectByUniqueKey(
                    request.getProjectId(),
                    request.getGroupId(),
                    currentUserId
            );

            ScoringRecord record;
            if (existingRecord != null) {
                // 修改已有记录
                record = existingRecord;
                record.setTotalScore(totalScore);
                record.setIsMalicious(maliciousFlag);
                record.setUpdateTime(LocalDateTime.now());
                recordMapper.updateById(record);

                // 删除旧的明细
                detailMapper.deleteByRecordId(record.getId());
            } else {
                // 创建新记录
                record = new ScoringRecord();
                record.setProjectId(request.getProjectId());
                record.setGroupInfoId(request.getGroupId());
                record.setUserId(currentUserId);
                record.setTotalScore(totalScore);
                record.setIsMalicious(maliciousFlag);
                record.setCreateTime(LocalDateTime.now());
                record.setUpdateTime(LocalDateTime.now());
                recordMapper.insert(record);
            }

            // 9. 保存明细
            for (ScoringRecordDetail detail : details) {
                detail.setRecordId(record.getId());
            }
            if (!details.isEmpty()) {
                detailMapper.insertBatch(details);
            }

            if (MALICIOUS_RULE_AUTO.equals(maliciousRuleType)) {
                refreshMaliciousFlagsForProjectGroup(request.getProjectId(), request.getGroupId());
                ScoringRecord refreshedRecord = recordMapper.selectById(record.getId());
                if (refreshedRecord != null) {
                    record = refreshedRecord;
                }
            }

            // 10. 构建响应
            ScoringRecordVO responseVO = new ScoringRecordVO();
            BeanUtils.copyProperties(record, responseVO);
            responseVO.setGroupId(record.getGroupInfoId());
            responseVO.setScores(details.stream()
                    .map(detail -> {
                        ScoringRecordVO.ScoreVO scoreVO = new ScoringRecordVO.ScoreVO();
                        scoreVO.setIndicatorId(detail.getIndicatorId());
                        scoreVO.setScore(detail.getScore());
                        return scoreVO;
                    })
                    .collect(Collectors.toList()));

            String action = existingRecord != null ? "修改" : "提交";
            log.info("{}打分成功: recordId={}, projectId={}, groupId={}, userId={}",
                    action, record.getId(), request.getProjectId(), request.getGroupId(), currentUserId);

            scoringRecordCacheUtil.clearUserProjectRecordsCache(request.getProjectId(), currentUserId);
            scoringOverviewCacheUtil.clearUserOverviewCache(currentUserId);
            return ResponseVO.success(action + "成功", responseVO);

        } catch (Exception e) {
            log.error("打分异常", e);
            return ResponseVO.error("打分失败: " + e.getMessage());
        }
    }

    @Override
    public ResponseVO<ScoringRecordVO> getScoreRecord(Long currentUserId, ScoringRecordQueryDTO query) {
        try {
            // 1. 验证登录态
            if (currentUserId == null) {
                return ResponseVO.unauthorized("Token无效");
            }

            // 2. 查询打分记录
            ScoringRecord record = recordMapper.selectByUniqueKey(
                    query.getProjectId(),
                    query.getGroupId(),
                    currentUserId
            );

            if (record == null) {
                return ResponseVO.notFound("未找到打分记录");
            }

            // 3. 查询明细
            List<ScoringRecordDetail> details = detailMapper.selectByRecordId(record.getId());

            // 4. 构建响应
            ScoringRecordVO responseVO = new ScoringRecordVO();
            BeanUtils.copyProperties(record, responseVO);
            responseVO.setGroupId(record.getGroupInfoId());
            responseVO.setScores(details.stream()
                    .map(detail -> {
                        ScoringRecordVO.ScoreVO scoreVO = new ScoringRecordVO.ScoreVO();
                        scoreVO.setIndicatorId(detail.getIndicatorId());
                        scoreVO.setScore(detail.getScore());
                        return scoreVO;
                    })
                    .collect(Collectors.toList()));

            log.info("查询打分记录成功: projectId={}, groupId={}, userId={}",
                    query.getProjectId(), query.getGroupId(), currentUserId);

            return ResponseVO.success("查询成功", responseVO);

        } catch (Exception e) {
            log.error("查询打分记录异常", e);
            return ResponseVO.error("查询失败: " + e.getMessage());
        }
    }

    @Override
    public ResponseVO<ScoringRecordPageVO> getUserProjectRecords(Long currentUserId, Long projectId, ScoringRecordPageQueryDTO query) {
        try {
            // 1. 验证登录态
            if (currentUserId == null) {
                return ResponseVO.unauthorized("Token无效");
            }

            // 2. 校验项目
            Project project = projectMapper.selectById(projectId);
            if (project == null) {
                return ResponseVO.notFound("项目不存在");
            }

            // 3. 校验当前用户是否为该项目打分用户
            boolean isScorer = scorerMapper.selectByProjectId(projectId).stream()
                    .anyMatch(scorer -> scorer.getUserId().equals(currentUserId));
            if (!isScorer) {
                return ResponseVO.forbidden("您没有权限查看该项目打分记录");
            }

            // 4. 分页参数
            int page = query != null && query.getPage() != null ? query.getPage() : 1;
            int size = query != null && query.getSize() != null ? query.getSize() : 10;
            Integer isMalicious = query != null ? query.getIsMalicious() : null;
            page = Math.max(page, 1);
            size = Math.max(size, 1);
            int offset = (page - 1) * size;

            // 5. 读取缓存
            String cacheKey = RedisKeyUtil.buildScoringRecordPageKey(projectId, currentUserId, isMalicious, page, size);
            Object cache = scoringRecordCacheUtil.getUserProjectRecordsCache(cacheKey);
            if (cache != null) {
                ScoringRecordPageVO cachedPage = (ScoringRecordPageVO) cache;
                log.info("【缓存命中】获取用户项目打分页记录: projectId={}, userId={}, isMalicious={}, page={}, size={}, total={}",
                        projectId, currentUserId, isMalicious, page, size, cachedPage.getTotal());
                return ResponseVO.success("获取成功", cachedPage);
            }

            // 6. 查询记录与总数
            List<ScoringRecord> records = recordMapper.selectPageByProjectAndUser(projectId, currentUserId, offset, size, isMalicious);
            Long total = recordMapper.countByProjectAndUser(projectId, currentUserId, isMalicious);

            // 7. 批量查询明细并构建VO
            List<Long> recordIds = records.stream().map(ScoringRecord::getId).collect(Collectors.toList());
            List<ScoringRecordDetail> allDetails = recordIds.isEmpty()
                    ? Collections.emptyList()
                    : detailMapper.selectByRecordIds(recordIds);

            Map<Long, List<ScoringRecordDetail>> detailMap = allDetails.stream()
                    .collect(Collectors.groupingBy(ScoringRecordDetail::getRecordId));

            List<ScoringRecordVO> recordVOList = records.stream()
                    .map(record -> buildScoringRecordVO(record, detailMap.getOrDefault(record.getId(), Collections.emptyList())))
                    .collect(Collectors.toList());

            ScoringRecordPageVO pageVO = new ScoringRecordPageVO();
            pageVO.setList(recordVOList);
            pageVO.setTotal(total);
            pageVO.setPage(page);
            pageVO.setSize(size);

            // 8. 写入缓存
            scoringRecordCacheUtil.cacheUserProjectRecords(cacheKey, pageVO);

            log.info("获取用户项目打分页记录成功: projectId={}, userId={}, isMalicious={}, page={}, size={}, count={}, total={}",
                    projectId, currentUserId, isMalicious, page, size, recordVOList.size(), total);
            return ResponseVO.success("获取成功", pageVO);
        } catch (Exception e) {
            log.error("获取用户项目打分页记录异常: projectId={}", projectId, e);
            return ResponseVO.error("获取失败: " + e.getMessage());
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

    private boolean isScoreMaliciousByThreshold(BigDecimal totalScore, BigDecimal scoreLower, BigDecimal scoreUpper) {
        if (totalScore == null || scoreLower == null || scoreUpper == null) {
            return false;
        }
        return totalScore.compareTo(scoreLower) < 0 || totalScore.compareTo(scoreUpper) > 0;
    }

    private void refreshMaliciousFlagsForProjectGroup(Long projectId, Long groupId) {
        List<Map<String, Object>> groupScoreRows = recordMapper.selectGroupScoreDetailsByProjectAndGroup(projectId, groupId);
        recordMapper.clearMaliciousFlagByProjectAndGroup(projectId, groupId);
        if (groupScoreRows == null || groupScoreRows.isEmpty()) {
            return;
        }

        List<BigDecimal> rawScores = groupScoreRows.stream()
                .map(row -> convertToBigDecimal(row.get("totalScore")))
                .collect(Collectors.toList());
        Set<Integer> abnormalIndexes = detectOutlierIndexes(rawScores);
        if (abnormalIndexes.isEmpty()) {
            return;
        }

        List<Long> maliciousRecordIds = abnormalIndexes.stream()
                .filter(index -> index != null && index >= 0 && index < groupScoreRows.size())
                .map(index -> toLong(groupScoreRows.get(index).get("recordId")))
                .filter(recordId -> recordId != null && recordId > 0)
                .distinct()
                .collect(Collectors.toList());
        if (!maliciousRecordIds.isEmpty()) {
            recordMapper.markMaliciousByRecordIds(maliciousRecordIds);
        }
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
        Set<Integer> abnormalIndexes = new java.util.HashSet<>();
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

    private ScoringRecordVO buildScoringRecordVO(ScoringRecord record, List<ScoringRecordDetail> details) {
        ScoringRecordVO responseVO = new ScoringRecordVO();
        BeanUtils.copyProperties(record, responseVO);
        responseVO.setGroupId(record.getGroupInfoId());
        responseVO.setScores(details.stream()
                .map(detail -> {
                    ScoringRecordVO.ScoreVO scoreVO = new ScoringRecordVO.ScoreVO();
                    scoreVO.setIndicatorId(detail.getIndicatorId());
                    scoreVO.setScore(detail.getScore());
                    return scoreVO;
                })
                .collect(Collectors.toList()));
        return responseVO;
    }
}
