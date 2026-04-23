package com.eval.gameeval.service.impl;

import com.eval.gameeval.mapper.*;
import com.eval.gameeval.models.DTO.Project.ProjectCreateDTO;
import com.eval.gameeval.models.DTO.Project.ProjectQueryDTO;
import com.eval.gameeval.models.DTO.Project.ProjectUpdateDTO;
import com.eval.gameeval.models.VO.ProjectCreateVO;
import com.eval.gameeval.models.VO.ProjectOverviewVO;
import com.eval.gameeval.models.VO.ProjectPageVO;
import com.eval.gameeval.models.VO.ProjectVO;
import com.eval.gameeval.models.VO.ResponseVO;
import com.eval.gameeval.models.entity.*;
import com.eval.gameeval.service.IProjectService;
import com.eval.gameeval.util.OverviewCacheUtil;
import com.eval.gameeval.util.ProjectCacheUtil;
import com.eval.gameeval.util.RedisKeyUtil;
import com.eval.gameeval.util.RedisToken;
import com.eval.gameeval.util.ScoringOverviewCacheUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;


import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;


@Slf4j
@Service
public class ProjectServiceImpl implements IProjectService {
    private static final String MALICIOUS_RULE_AUTO = "AUTO";
    private static final String MALICIOUS_RULE_THRESHOLD = "THRESHOLD";

    @Resource
    private ProjectMapper projectMapper;

    @Resource
    private ProjectGroupMapper groupMapper;

    @Resource
    private ProjectGroupInfoMapper groupInfoMapper;

    @Resource
    private ProjectScorerMapper scorerMapper;

    @Resource
    private ScoringStandardMapper standardMapper;

    @Resource
    private ScoringRecordMapper recordMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private ReviewerGroupMemberMapper reviewerGroupMemberMapper;

    @Resource
    private ReviewerGroupMapper reviewerGroupMapper;

    @Resource
    private RedisToken redisToken;

    @Resource
    private ProjectCacheUtil projectCacheUtil;

    @Resource
    private ScoringOverviewCacheUtil scoringOverviewCacheUtil;

    @Resource
    private OverviewCacheUtil overviewCacheUtil;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseVO<ProjectCreateVO> createProject(String token, ProjectCreateDTO request) {
        try {
            // 1. 验证Token
            Long currentUserId = redisToken.getUserIdByToken(token);
            if (currentUserId == null) {
                return ResponseVO.unauthorized("Token无效");
            }

            User currentUser = userMapper.selectById(currentUserId);
            if (currentUser == null || (!"super_admin".equals(currentUser.getRole()) && !"admin".equals(currentUser.getRole()))) {
                return ResponseVO.forbidden("权限不足");
            }

            // 2. 验证打分标准是否存在
            ScoringStandard standard = standardMapper.selectById(request.getStandardId());
            if (standard == null) {
                return ResponseVO.badRequest("打分标准不存在");
            }

            // 3. 验证日期
            if (request.getStartDate().isAfter(request.getEndDate())) {
                return ResponseVO.badRequest("起始日期不能晚于结束日期");
            }

            String maliciousRuleType = normalizeMaliciousRuleType(request.getMaliciousRuleType());
            if (maliciousRuleType == null) {
                return ResponseVO.badRequest("恶意判定规则类型不正确，仅支持 AUTO 或 THRESHOLD");
            }
            BigDecimal maliciousScoreLower = request.getMaliciousScoreLower();
            BigDecimal maliciousScoreUpper = request.getMaliciousScoreUpper();
            if (MALICIOUS_RULE_THRESHOLD.equals(maliciousRuleType)) {
                if (maliciousScoreLower == null || maliciousScoreUpper == null) {
                    return ResponseVO.badRequest("阈值模式下必须同时设置最低分和最高分");
                }
                if (maliciousScoreLower.compareTo(maliciousScoreUpper) > 0) {
                    return ResponseVO.badRequest("最低分阈值不能大于最高分阈值");
                }
            } else {
                maliciousScoreLower = null;
                maliciousScoreUpper = null;
            }

            // 4. 解析小组来源（仅支持 groupIds）
            List<Long> resolvedGroupInfoIds = new ArrayList<>();
            List<Long> distinctGroupIds = request.getGroupIds().stream().distinct().collect(Collectors.toList());
            for (Long groupInfoId : distinctGroupIds) {
                if (groupInfoId == null || groupInfoId <= 0) {
                    return ResponseVO.badRequest("小组ID必须大于0");
                }
                ProjectGroupInfo groupInfo = groupInfoMapper.selectById(groupInfoId);
                if (groupInfo == null) {
                    return ResponseVO.badRequest("小组ID " + groupInfoId + " 不存在");
                }
                resolvedGroupInfoIds.add(groupInfoId);
            }

            // 5. 通过评审组解析打分用户
            ReviewerGroup reviewerGroup = reviewerGroupMapper.selectById(request.getReviewerGroupId());
            if (reviewerGroup == null) {
                return ResponseVO.badRequest("评审组不存在");
            }
            List<Long> resolvedScorerIds = reviewerGroupMemberMapper.selectUserIdsByGroupId(request.getReviewerGroupId());
            if (resolvedScorerIds == null || resolvedScorerIds.isEmpty()) {
                return ResponseVO.badRequest("评审组没有成员");
            }
            resolvedScorerIds = resolvedScorerIds.stream().distinct().collect(Collectors.toList());

            // 5.1 预先校验打分用户，避免项目创建后再失败
            List<Long> validatedScorerIds = new ArrayList<>();
            for (Long scorerId : resolvedScorerIds) {
                if (scorerId == null || scorerId <= 0) {
                    return ResponseVO.badRequest("打分用户ID必须大于0");
                }
                User scorer = userMapper.selectById(scorerId);
                if (scorer == null) {
                    return ResponseVO.badRequest("打分用户ID " + scorerId + " 不存在");
                }
                if (!"scorer".equals(scorer.getRole()) && !"admin".equals(scorer.getRole()) && !"super_admin".equals(scorer.getRole())) {
                    return ResponseVO.badRequest("用户 " + scorerId + " 不是打分用户");
                }
                validatedScorerIds.add(scorerId);
            }

            // 6. 创建项目
            Project project = new Project();
            project.setName(request.getName());
            project.setDescription(request.getDescription() != null ? request.getDescription() : "");

            LocalDateTime startDateTime = request.getStartDate();
            LocalDateTime endDateTime = request.getEndDate();
            log.info("项目开始时间"+startDateTime+"end"+endDateTime);
            project.setStartDate(startDateTime);
            project.setEndDate(endDateTime);

// 根据当前时间与项目时间范围的关系设置状态
            LocalDateTime now = LocalDateTime.now();
            String status;
            if (now.isBefore(startDateTime)) {
                status = "not_started";      // 未开始
            } else if (now.isAfter(endDateTime)) {
                status = "ended";            // 已结束
            } else {
                status = "ongoing";      // 进行中
            }
            project.setStatus(status);
            log.info("项目状态 "+status);
            project.setIsEnabled(request.getIsEnabled() != null ? request.getIsEnabled() : true);
            project.setStandardId(request.getStandardId());
            project.setMaliciousRuleType(maliciousRuleType);
            project.setMaliciousScoreLower(maliciousScoreLower);
            project.setMaliciousScoreUpper(maliciousScoreUpper);
            project.setCreatorId(currentUserId);
            project.setCreateTime(LocalDateTime.now());
            project.setUpdateTime(LocalDateTime.now());

            projectMapper.insert(project);

            // 7. 创建小组关联
            List<ProjectGroup> groups = new ArrayList<>();
            for (Long groupInfoId : resolvedGroupInfoIds) {
                ProjectGroup group = new ProjectGroup();
                group.setProjectId(project.getId());
                group.setGroupInfoId(groupInfoId);
                group.setCreateTime(LocalDateTime.now());
                group.setUpdateTime(LocalDateTime.now());
                groups.add(group);
            }
            if (!groups.isEmpty()) {
                groupMapper.insertBatch(groups);
            }

            // 8. 创建打分用户关联
            List<ProjectScorer> scorers = new ArrayList<>();
            for (Long scorerId : validatedScorerIds) {
                ProjectScorer scorerRel = new ProjectScorer();
                scorerRel.setProjectId(project.getId());
                scorerRel.setUserId(scorerId);
                scorerRel.setCreateTime(LocalDateTime.now());
                scorers.add(scorerRel);
            }
            if (!scorers.isEmpty()) {
                scorerMapper.insertBatch(scorers);
            }


            // 9. 清除缓存
            projectCacheUtil.clearAllProjectListCache(); // 清除全局项目列表缓存
            projectCacheUtil.clearPlatformStatisticsCache(); // 清除平台统计缓存
            overviewCacheUtil.clearProjectOverviewCache();
            overviewCacheUtil.clearStandardOverviewCache();
            overviewCacheUtil.clearGroupOverviewCache();
            
            // 清除所有打分用户的授权项目缓存，确保他们能立即看到新项目
            clearUserProjectCaches(validatedScorerIds, project.getId(), "createProject");
            log.info("创建项目成功并触发缓存清除: projectId={}", project.getId());

            // 10. 构建响应
            ProjectCreateVO responseVO = new ProjectCreateVO();
            BeanUtils.copyProperties(project, responseVO);
            responseVO.setGroupIds(resolvedGroupInfoIds);
            responseVO.setScorerIds(validatedScorerIds);
            responseVO.setReviewerGroupId(request.getReviewerGroupId());


            log.info("创建项目成功: projectId={}, creatorId={}", project.getId(), currentUserId);

            return ResponseVO.success("创建成功", responseVO);

        } catch (Exception e) {
            log.error("创建项目异常", e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return ResponseVO.error("创建失败: " + e.getMessage());
        }

    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseVO<Void> updateProject(String token, Long projectId, ProjectUpdateDTO request) {
        try {
            // 1. 验证Token
            Long currentUserId = redisToken.getUserIdByToken(token);
            if (currentUserId == null) {
                return ResponseVO.unauthorized("Token无效");
            }

            User currentUser = userMapper.selectById(currentUserId);
            if (currentUser == null || (!"super_admin".equals(currentUser.getRole()) && !"admin".equals(currentUser.getRole()))) {
                return ResponseVO.forbidden("权限不足");
            }

            // 2. 查询项目
            Project project = projectMapper.selectById(projectId);
            if (project == null) {
                return ResponseVO.notFound("项目不存在");
            }

            List<Long> oldScorerIds = scorerMapper.selectByProjectId(projectId).stream()
                    .map(ProjectScorer::getUserId)
                    .collect(Collectors.toList());

            // 3. 构建更新对象（保留原值）
            Project updateProject = new Project();
            updateProject.setId(projectId);
            updateProject.setUpdateTime(LocalDateTime.now());

            updateProject.setName(request.getName() != null ? request.getName() : project.getName());
            updateProject.setDescription(request.getDescription() != null ? request.getDescription() : project.getDescription());
            LocalDateTime effectiveStartDate = request.getStartDate() != null ? request.getStartDate() : project.getStartDate();
            LocalDateTime effectiveEndDate = request.getEndDate() != null ? request.getEndDate() : project.getEndDate();
            if (effectiveStartDate.isAfter(effectiveEndDate)) {
                return ResponseVO.badRequest("起始日期不能晚于结束日期");
            }
            updateProject.setStartDate(effectiveStartDate);
            updateProject.setEndDate(effectiveEndDate);
            updateProject.setIsEnabled(request.getIsEnabled() != null ? request.getIsEnabled() : project.getIsEnabled());
            updateProject.setStandardId(request.getStandardId() != null ? request.getStandardId() : project.getStandardId());
            String effectiveRuleType = normalizeMaliciousRuleType(
                    request.getMaliciousRuleType() != null ? request.getMaliciousRuleType() : project.getMaliciousRuleType()
            );
            if (effectiveRuleType == null) {
                return ResponseVO.badRequest("恶意判定规则类型不正确，仅支持 AUTO 或 THRESHOLD");
            }
            BigDecimal effectiveScoreLower = request.getMaliciousScoreLower() != null
                    ? request.getMaliciousScoreLower()
                    : project.getMaliciousScoreLower();
            BigDecimal effectiveScoreUpper = request.getMaliciousScoreUpper() != null
                    ? request.getMaliciousScoreUpper()
                    : project.getMaliciousScoreUpper();
            if (MALICIOUS_RULE_THRESHOLD.equals(effectiveRuleType)) {
                if (effectiveScoreLower == null || effectiveScoreUpper == null) {
                    return ResponseVO.badRequest("阈值模式下必须同时设置最低分和最高分");
                }
                if (effectiveScoreLower.compareTo(effectiveScoreUpper) > 0) {
                    return ResponseVO.badRequest("最低分阈值不能大于最高分阈值");
                }
            } else {
                effectiveScoreLower = null;
                effectiveScoreUpper = null;
            }
            updateProject.setMaliciousRuleType(effectiveRuleType);
            updateProject.setMaliciousScoreLower(effectiveScoreLower);
            updateProject.setMaliciousScoreUpper(effectiveScoreUpper);
            // 根据当前时间与项目时间范围的关系设置状态

            LocalDateTime now = LocalDateTime.now();
            LocalDateTime startDateTime = effectiveStartDate;
            LocalDateTime endDateTime = effectiveEndDate;
            String status;
            if (now.isBefore(startDateTime)) {
                status = "not_started";      // 未开始
            } else if (now.isAfter(endDateTime)) {
                status = "ended";            // 已结束
            } else {
                status = "ongoing";      // 进行中
            }
            updateProject.setStatus(status); // 状态

            // 4. 更新项目
            projectMapper.updateById(updateProject);

            // 5. 更新关联数据（先删除再插入）
            if (request.getGroupIds() != null ) {
                groupMapper.deleteByProjectId(projectId);
                List<ProjectGroup> groups = new ArrayList<>();
                for (Long groupInfoId : request.getGroupIds()) {
                    // 验证小组信息是否存在
                    ProjectGroupInfo groupInfo = groupInfoMapper.selectById(groupInfoId);
                    if (groupInfo == null) {
                        return ResponseVO.badRequest("小组ID " + groupInfoId + " 不存在");
                    }
                    
                    ProjectGroup group = new ProjectGroup();
                    group.setProjectId(projectId);
                    group.setGroupInfoId(groupInfoId);
                    group.setCreateTime(LocalDateTime.now());
                    group.setUpdateTime(LocalDateTime.now());
                    groups.add(group);
                }
                if (!groups.isEmpty()) {
                    groupMapper.insertBatch(groups);
                }
            }

            if (request.getScorerIds() != null ) {
                scorerMapper.deleteByProjectId(projectId);
                List<ProjectScorer> scorers = new ArrayList<>();
                for (Long scorerId : request.getScorerIds()) {
                    ProjectScorer scorer = new ProjectScorer();
                    scorer.setProjectId(projectId);
                    scorer.setUserId(scorerId);
                    scorer.setCreateTime(LocalDateTime.now());
                    scorers.add(scorer);
                }
                if (!scorers.isEmpty()) {
                    scorerMapper.insertBatch(scorers);
                }
            }

            if (!Objects.equals(project.getMaliciousRuleType(), effectiveRuleType)
                    || !Objects.equals(project.getMaliciousScoreLower(), effectiveScoreLower)
                    || !Objects.equals(project.getMaliciousScoreUpper(), effectiveScoreUpper)) {
                refreshProjectMaliciousFlags(projectId, effectiveRuleType, effectiveScoreLower, effectiveScoreUpper);
            }

            projectCacheUtil.clearProjectDetailCache(projectId);      // 清除详情缓存
            projectCacheUtil.clearAllProjectListCache();              // 清除列表缓存
            projectCacheUtil.clearProjectGroupsCache(projectId);      // 清除小组缓存
            projectCacheUtil.clearPlatformStatisticsCache();          // 清除平台统计缓存
                overviewCacheUtil.clearProjectOverviewCache();
                overviewCacheUtil.clearStandardOverviewCache();
                overviewCacheUtil.clearGroupOverviewCache();

            List<Long> currentScorerIds = scorerMapper.selectByProjectId(projectId).stream()
                    .map(ProjectScorer::getUserId)
                    .collect(Collectors.toList());
            List<Long> affectedScorerIds = mergeDistinctUserIds(oldScorerIds, currentScorerIds);
            clearUserProjectCaches(affectedScorerIds, projectId, "updateProject");
            
            log.info("编辑项目成功: projectId={}, operator={}", projectId, currentUserId);

            return ResponseVO.<Void>success("编辑成功",null);

        } catch (Exception e) {
            log.error("编辑项目异常: projectId={}", projectId, e);
            return ResponseVO.error("编辑失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseVO<Void> endProject(String token, Long projectId) {
        try {
            // 1. 验证Token
            Long currentUserId = redisToken.getUserIdByToken(token);
            if (currentUserId == null) {
                return ResponseVO.unauthorized("Token无效");
            }

            User currentUser = userMapper.selectById(currentUserId);
            if (currentUser == null || (!"super_admin".equals(currentUser.getRole()) && !"admin".equals(currentUser.getRole()))) {
                return ResponseVO.forbidden("权限不足");
            }

            // 2. 查询项目
            Project project = projectMapper.selectById(projectId);
            if (project == null) {
                return ResponseVO.notFound("项目不存在");
            }

            // 3. 检查项目状态
            if ("ended".equals(project.getStatus())) {
                return ResponseVO.badRequest("项目已结束");
            }

            // 4. 结束项目
            projectMapper.endProject(projectId, LocalDateTime.now());

            projectCacheUtil.clearProjectDetailCache(projectId);
            projectCacheUtil.clearAllProjectListCache();
            projectCacheUtil.clearPlatformStatisticsCache();
            overviewCacheUtil.clearProjectOverviewCache();
            
            // 清除所有相关用户的授权项目缓存
            List<ProjectScorer> projectScorers = scorerMapper.selectByProjectId(projectId);
            List<Long> scorerIds = projectScorers.stream().map(ProjectScorer::getUserId).collect(Collectors.toList());
            clearUserProjectCaches(scorerIds, projectId, "endProject");
            
            log.info("结束项目成功: projectId={}, operator={}", projectId, currentUserId);

            return ResponseVO.<Void>success("项目已结束",null);

        } catch (Exception e) {
            log.error("结束项目异常: projectId={}", projectId, e);
            return ResponseVO.error("结束失败: " + e.getMessage());
        }
    }

    @Override
    public ResponseVO<ProjectPageVO> getProjectList(String token, ProjectQueryDTO query) {
        try {
            // 1. 验证Token
            Long currentUserId = redisToken.getUserIdByToken(token);
            if (currentUserId == null) {
                return ResponseVO.unauthorized("Token无效");
            }

            // 1.1 兜底纠偏项目状态并触发缓存失效闭环
            reconcileProjectStatuses("getProjectList");

            // 2. 处理分页
            int page = query.getPage() != null ? query.getPage() : 1;
            int size = query.getSize() != null ? query.getSize() : 10;
            int offset = (page - 1) * size;

            // 3. 构建分页缓存键
            String cacheKey = RedisKeyUtil.buildProjectListKey(
                    query.getStatus(),
                    query.getIsEnabled(),
                    query.getKeyWords(),
                    page,
                    size
            );

            // 4. 尝试从缓存获取
            Object cache = projectCacheUtil.getProjectListCache(cacheKey);
            if (cache != null) {
                @SuppressWarnings("unchecked")
                ProjectPageVO cachedPage = (ProjectPageVO) cache;
                List<Long> staleProjectIds = collectStaleProjectIdsFromPage(cachedPage);
                if (!staleProjectIds.isEmpty()) {
                    invalidateStatusChangeCaches(staleProjectIds, "getProjectList:cacheHitStale");
                } else {
                log.info("【缓存命中】获取项目列表: key={}, total={}", cacheKey, cachedPage.getTotal());
                return ResponseVO.success("查询成功", cachedPage);
                }
            }
            // 5. 查询项目列表
            log.info("【缓存未命中】查询数据库: key={}", cacheKey);
            List<Project> projects = projectMapper.selectPage(offset, size, query.getStatus(), query.getIsEnabled(), query.getKeyWords());
            Long total = projectMapper.countTotal(query.getStatus(), query.getIsEnabled(), query.getKeyWords());

            // 6. 转换为VO
            List<ProjectVO> projectVOs = new ArrayList<>();
            for (Project project : projects) {
                ProjectVO vo = new ProjectVO();
                BeanUtils.copyProperties(project, vo);

                // 查询关联的小组
                List<ProjectGroup> groups = groupMapper.selectByProjectId(project.getId());
                List<Long> groupIds = groups.stream().map(ProjectGroup::getGroupInfoId).collect(Collectors.toList());
                vo.setGroupIds(groupIds);

                // 查询关联的打分用户
                List<ProjectScorer> scorers = scorerMapper.selectByProjectId(project.getId());
                List<Long> scorerIds = scorers.stream().map(ProjectScorer::getUserId).collect(Collectors.toList());
                vo.setScorerIds(scorerIds);

                projectVOs.add(vo);
            }

            // 7. 构建分页响应
            ProjectPageVO pageVO = new ProjectPageVO();
            pageVO.setList(projectVOs);
            pageVO.setTotal(total);
            pageVO.setPage(page);
            pageVO.setSize(size);

            projectCacheUtil.cacheProjectList(cacheKey, pageVO);
            log.info("查询项目列表成功: count={}", projectVOs.size());

            return ResponseVO.success("查询成功", pageVO);

        } catch (Exception e) {
            log.error("查询项目列表异常", e);
            return ResponseVO.error("查询失败: " + e.getMessage());
        }
    }

    @Override
    public ResponseVO<ProjectVO> getProjectDetail(String token, Long projectId) {
        try {
            // 1. 验证Token
            Long currentUserId = redisToken.getUserIdByToken(token);
            if (currentUserId == null) {
                return ResponseVO.unauthorized("Token无效");
            }

            // 1.1 兜底纠偏项目状态并触发缓存失效闭环
            reconcileProjectStatuses("getProjectDetail");

            // 2. 缓存穿透防护 - 检查空值缓存
            if (projectCacheUtil.isProjectNullCached(projectId)) {
                log.warn("【缓存穿透防护】空值缓存命中: projectId={}", projectId);
                return ResponseVO.notFound("项目不存在");
            }

            // 3. 尝试从缓存获取详情
            Object cache = projectCacheUtil.getProjectDetailCache(projectId);
            if (cache != null) {
                ProjectVO cachedVO = (ProjectVO) cache;
                if (!isStatusFresh(cachedVO.getStatus(), cachedVO.getStartDate(), cachedVO.getEndDate())) {
                    invalidateStatusChangeCaches(Collections.singletonList(projectId), "getProjectDetail:cacheHitStale");
                } else {
                log.info("【缓存命中】获取项目详情: projectId={}", projectId);
                return ResponseVO.success("查询成功", cachedVO);
                }
            }

            // 4. 缓存未命中：查询数据库
            log.info("【缓存未命中】查询数据库: projectId={}", projectId);
            Project project = projectMapper.selectById(projectId);
            if (project == null) {
                projectCacheUtil.cacheProjectNull(projectId);
                log.warn("【缓存穿透防护】写入空值缓存: projectId={}", projectId);
                return ResponseVO.notFound("项目不存在");
            }

            // 5. 构建响应
            ProjectVO responseVO = new ProjectVO();
            BeanUtils.copyProperties(project, responseVO);

            // 查询关联的小组
            List<ProjectGroup> groups = groupMapper.selectByProjectId(projectId);
            List<Long> groupIds = groups.stream().map(ProjectGroup::getGroupInfoId).collect(Collectors.toList());
            responseVO.setGroupIds(groupIds);

            // 查询关联的打分用户
            List<ProjectScorer> scorers = scorerMapper.selectByProjectId(projectId);
            List<Long> scorerIds = scorers.stream().map(ProjectScorer::getUserId).collect(Collectors.toList());
            responseVO.setScorerIds(scorerIds);

            projectCacheUtil.cacheProjectDetail(projectId, responseVO);
            log.info("查询项目详情成功: projectId={}", projectId);

            return ResponseVO.success("查询成功", responseVO);

        } catch (Exception e) {
            log.error("查询项目详情异常: projectId={}", projectId, e);
            return ResponseVO.error("查询失败: " + e.getMessage());
        }
    }

    @Override
    public ResponseVO<ProjectPageVO> getAuthorizedProjects(String token, ProjectQueryDTO query) {
        Long currentUserId = null;
        try {
            // 1. 验证Token
            currentUserId = redisToken.getUserIdByToken(token);
            if (currentUserId == null) {
                return ResponseVO.unauthorized("Token无效");
            }

            // 1.1 兜底纠偏项目状态并触发缓存失效闭环
            reconcileProjectStatuses("getAuthorizedProjects");

            ProjectQueryDTO safeQuery = query != null ? query : new ProjectQueryDTO();
            int page = safeQuery.getPage() != null ? safeQuery.getPage() : 1;
            int size = safeQuery.getSize() != null ? safeQuery.getSize() : 10;
            page = Math.max(page, 1);
            size = Math.max(size, 1);
            int offset = (page - 1) * size;

            // 2. 构建缓存键（包含查询条件）
            String cacheKey = RedisKeyUtil.buildAuthorizedProjectsKey(
                    currentUserId,
                    safeQuery.getStatus(),
                    safeQuery.getIsEnabled(),
                    safeQuery.getKeyWords(),
                    page,
                    size
            );

            // 3. 尝试从缓存获取
            Object cache = projectCacheUtil.getAuthorizedProjectsCache(cacheKey);
            if (cache != null) {
                ProjectPageVO cachedPage = (ProjectPageVO) cache;
                List<Long> staleProjectIds = collectStaleProjectIdsFromPage(cachedPage);
                if (!staleProjectIds.isEmpty()) {
                    invalidateStatusChangeCaches(staleProjectIds, "getAuthorizedProjects:cacheHitStale");
                } else {
                log.info("【缓存命中】获取授权项目: userId={}, status={}, isEnabled={}, keyWords={}, page={}, size={}, total={}",
                        currentUserId, safeQuery.getStatus(), safeQuery.getIsEnabled(), safeQuery.getKeyWords(), page, size, cachedPage.getTotal());
                return ResponseVO.success("查询成功", cachedPage);
                }
            }

            // 4. 缓存未命中：查询数据库
            log.info("【缓存未命中】查询数据库: userId={}, status={}, isEnabled={}, keyWords={}, page={}, size={}",
                    currentUserId, safeQuery.getStatus(), safeQuery.getIsEnabled(), safeQuery.getKeyWords(), page, size);
            List<Project> projects = projectMapper.selectPageByScorerId(
                    currentUserId,
                    offset,
                    size,
                    safeQuery.getStatus(),
                    safeQuery.getIsEnabled(),
                    safeQuery.getKeyWords()
            );
            Long total = projectMapper.countByScorerId(
                    currentUserId,
                    safeQuery.getStatus(),
                    safeQuery.getIsEnabled(),
                    safeQuery.getKeyWords()
            );

            // 5. 转换为VO
            List<ProjectVO> projectVOs = new ArrayList<>();
            for (Project project : projects) {
                ProjectVO vo = new ProjectVO();
                BeanUtils.copyProperties(project, vo);

                // 查询关联的小组
                List<ProjectGroup> groups = groupMapper.selectByProjectId(project.getId());
                List<Long> groupIds = groups.stream().map(ProjectGroup::getGroupInfoId).collect(Collectors.toList());
                vo.setGroupIds(groupIds);

                // 查询关联的打分用户
                List<ProjectScorer> scorers = scorerMapper.selectByProjectId(project.getId());
                List<Long> scorerIds = scorers.stream().map(ProjectScorer::getUserId).collect(Collectors.toList());
                vo.setScorerIds(scorerIds);

                projectVOs.add(vo);
            }

            ProjectPageVO pageVO = new ProjectPageVO();
            pageVO.setList(projectVOs);
            pageVO.setTotal(total);
            pageVO.setPage(page);
            pageVO.setSize(size);

            projectCacheUtil.cacheAuthorizedProjects(cacheKey, pageVO);
            log.info("查询授权项目列表成功: userId={}, status={}, isEnabled={}, keyWords={}, page={}, size={}, count={}, total={}",
                    currentUserId, safeQuery.getStatus(), safeQuery.getIsEnabled(), safeQuery.getKeyWords(), page, size, projectVOs.size(), total);

            return ResponseVO.success("查询成功", pageVO);

        } catch (Exception e) {
            log.error("查询授权项目列表异常: userId={}", currentUserId, e);
            return ResponseVO.error("查询失败: " + e.getMessage());
        }
    }

    @Override
    public ResponseVO<ProjectOverviewVO> getProjectOverview(String token) {
        try {
            Long currentUserId = redisToken.getUserIdByToken(token);
            if (currentUserId == null) {
                return ResponseVO.unauthorized("Token无效");
            }

            User currentUser = userMapper.selectById(currentUserId);
            if (currentUser == null || (!"super_admin".equals(currentUser.getRole()) && !"admin".equals(currentUser.getRole()))) {
                return ResponseVO.forbidden("权限不足");
            }

            reconcileProjectStatuses("getProjectOverview");

            Object cache = overviewCacheUtil.getProjectOverviewCache();
            if (cache != null) {
                ProjectOverviewVO cachedOverview = (ProjectOverviewVO) cache;
                log.info("【缓存命中】查询项目概览: totalProjects={}", cachedOverview.getTotalProjects());
                return ResponseVO.success("查询成功", cachedOverview);
            }

            ProjectOverviewVO overviewVO = loadProjectOverview();
            overviewCacheUtil.cacheProjectOverview(overviewVO);

            return ResponseVO.success("查询成功", overviewVO);

        } catch (Exception e) {
            log.error("查询项目概览异常", e);
            return ResponseVO.error("查询失败: " + e.getMessage());
        }
    }

    private void clearUserProjectCaches(List<Long> userIds, Long projectId, String scene) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        Set<Long> distinctUserIds = new HashSet<>(userIds);
        for (Long userId : distinctUserIds) {
            if (userId == null) {
                continue;
            }
            projectCacheUtil.clearAuthorizedProjectsCache(userId);
            scoringOverviewCacheUtil.clearUserOverviewCache(userId);
            log.debug("清除用户项目相关缓存: scene={}, userId={}, projectId={}", scene, userId, projectId);
        }
    }

    private List<Long> mergeDistinctUserIds(List<Long> userIds1, List<Long> userIds2) {
        Set<Long> result = new HashSet<>();
        if (userIds1 != null) {
            result.addAll(userIds1);
        }
        if (userIds2 != null) {
            result.addAll(userIds2);
        }
        return new ArrayList<>(result);
    }

    public void reconcileProjectStatusesByScheduler() {
        reconcileProjectStatuses("scheduler");
    }

    public void warmupDefaultProjectListCache() {
        try {
            reconcileProjectStatuses("warmupDefaultProjectListCache");

            int page = 1;
            int size = 10;
            int offset = 0;
            String cacheKey = RedisKeyUtil.buildProjectListKey(null, true, null, page, size);
            if (projectCacheUtil.getProjectListCache(cacheKey) != null) {
                return;
            }

            List<Project> projects = projectMapper.selectPage(offset, size, null, true, null);
            Long total = projectMapper.countTotal(null, true, null);

            List<ProjectVO> projectVOs = new ArrayList<>();
            for (Project project : projects) {
                ProjectVO vo = new ProjectVO();
                BeanUtils.copyProperties(project, vo);

                List<ProjectGroup> groups = groupMapper.selectByProjectId(project.getId());
                List<Long> groupIds = groups.stream().map(ProjectGroup::getGroupInfoId).collect(Collectors.toList());
                vo.setGroupIds(groupIds);

                List<ProjectScorer> scorers = scorerMapper.selectByProjectId(project.getId());
                List<Long> scorerIds = scorers.stream().map(ProjectScorer::getUserId).collect(Collectors.toList());
                vo.setScorerIds(scorerIds);

                projectVOs.add(vo);
            }

            ProjectPageVO pageVO = new ProjectPageVO();
            pageVO.setList(projectVOs);
            pageVO.setTotal(total);
            pageVO.setPage(page);
            pageVO.setSize(size);

            projectCacheUtil.cacheProjectList(cacheKey, pageVO);
            log.info("全局热键预热完成: key={}, count={}, total={}", cacheKey, projectVOs.size(), total);
        } catch (Exception e) {
            log.error("全局热键预热异常: project:list 默认首页", e);
        }
    }

    public void warmupProjectOverviewCache() {
        try {
            if (overviewCacheUtil.getProjectOverviewCache() != null) {
                return;
            }

            ProjectOverviewVO overviewVO = loadProjectOverview();
            overviewCacheUtil.cacheProjectOverview(overviewVO);

            log.info("全局概览预热完成: key={}, totalProjects={}",
                    RedisKeyUtil.PROJECT_OVERVIEW_KEY, overviewVO.getTotalProjects());
        } catch (Exception e) {
            log.error("全局概览预热异常: key={}", RedisKeyUtil.PROJECT_OVERVIEW_KEY, e);
        }
    }

    private ProjectOverviewVO loadProjectOverview() {
        Map<String, Object> overviewMap = projectMapper.selectProjectOverview();
        if (overviewMap == null) {
            overviewMap = Collections.emptyMap();
        }

        return new ProjectOverviewVO()
                .setTotalProjects(toLong(overviewMap.get("totalProjects")))
                .setNotStartedProjects(toLong(overviewMap.get("notStartedProjects")))
                .setOngoingProjects(toLong(overviewMap.get("ongoingProjects")))
                .setEndedProjects(toLong(overviewMap.get("endedProjects")));
    }

    /**
     * 兜底同步项目状态并补齐缓存失效闭环：project list/detail、authorized、overview、platform。
     */
    private void reconcileProjectStatuses(String scene) {


        try {
            LocalDateTime now = LocalDateTime.now();
            List<Long> mismatchProjectIds = projectMapper.selectStatusMismatchProjectIds(now);
            if (mismatchProjectIds == null || mismatchProjectIds.isEmpty()) {
                return;
            }

            int updatedCount = projectMapper.syncStatusByNow(now);
            if (updatedCount <= 0) {
                return;
            }

            invalidateStatusChangeCaches(mismatchProjectIds, "reconcileProjectStatuses:" + scene);
            log.info("项目状态兜底同步完成: scene={}, updatedCount={}, affectedProjectCount={}",
                    scene, updatedCount, mismatchProjectIds.size());
        } catch (Exception e) {
            log.error("项目状态兜底同步异常: scene={}", scene, e);
        }
    }

    /**
     * 统一失效入口，一次清全链路缓存，确保数据一致性和完整的失效闭环，避免遗漏导致的脏数据风险。
     * @param projectIds
     * @param scene
     */
    private void invalidateStatusChangeCaches(List<Long> projectIds, String scene) {
        if (projectIds == null || projectIds.isEmpty()) {
            return;
        }

        Set<Long> distinctProjectIds = new HashSet<>(projectIds);
        projectCacheUtil.clearAllProjectListCache();
        projectCacheUtil.clearPlatformStatisticsCache();
        overviewCacheUtil.clearProjectOverviewCache();

        Set<Long> affectedUserIds = new HashSet<>();
        for (Long projectId : distinctProjectIds) {
            if (projectId == null) {
                continue;
            }
                projectCacheUtil.clearProjectDetailCache(projectId);
                List<ProjectScorer> scorers = scorerMapper.selectByProjectId(projectId);
                for (ProjectScorer scorer : scorers) {
                    if (scorer.getUserId() != null) {
                        affectedUserIds.add(scorer.getUserId());
                    }
                }
        }

        clearUserProjectCaches(new ArrayList<>(affectedUserIds), null, scene);
        log.info("项目状态缓存失效完成: scene={}, affectedProjectCount={}, affectedUserCount={}",
                scene, distinctProjectIds.size(), affectedUserIds.size());
    }

    private List<Long> collectStaleProjectIdsFromPage(ProjectPageVO pageVO) {
        if (pageVO == null || pageVO.getList() == null || pageVO.getList().isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> staleProjectIds = new ArrayList<>();
        for (ProjectVO projectVO : pageVO.getList()) {
            if (projectVO == null || projectVO.getId() == null) {
                continue;
            }
            if (!isStatusFresh(projectVO.getStatus(), projectVO.getStartDate(), projectVO.getEndDate())) {
                staleProjectIds.add(projectVO.getId());
            }
        }
        return staleProjectIds;
    }

    private boolean isStatusFresh(String cachedStatus, LocalDateTime startDate, LocalDateTime endDate) {
        if (cachedStatus == null || startDate == null || endDate == null) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        String expectedStatus;
        if (now.isBefore(startDate)) {
            expectedStatus = "not_started";
        } else if (now.isAfter(endDate)) {
            expectedStatus = "ended";
        } else {
            expectedStatus = "ongoing";
        }
        return expectedStatus.equals(cachedStatus);
    }

    private String normalizeMaliciousRuleType(String ruleType) {
        if (ruleType == null || ruleType.isBlank()) {
            return MALICIOUS_RULE_AUTO;
        }
        String normalized = ruleType.trim().toUpperCase();
        if (!MALICIOUS_RULE_AUTO.equals(normalized) && !MALICIOUS_RULE_THRESHOLD.equals(normalized)) {
            return null;
        }
        return normalized;
    }

    private void refreshProjectMaliciousFlags(Long projectId, String ruleType, BigDecimal scoreLower, BigDecimal scoreUpper) {
        if (projectId == null) {
            return;
        }
        if (MALICIOUS_RULE_THRESHOLD.equals(ruleType)) {
            if (scoreLower == null || scoreUpper == null) {
                log.warn("阈值模式配置不完整，跳过恶意标记重算: projectId={}", projectId);
                return;
            }
            recordMapper.markMaliciousByThreshold(projectId, scoreLower, scoreUpper);
            return;
        }

        List<Map<String, Object>> groupScoreRows = recordMapper.selectGroupScoreDetails(projectId);
        recordMapper.clearMaliciousFlagByProjectId(projectId);
        if (groupScoreRows == null || groupScoreRows.isEmpty()) {
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
                    .map(row -> new BigDecimal(String.valueOf(row.get("totalScore"))))
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
}
