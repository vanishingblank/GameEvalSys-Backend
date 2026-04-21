package com.eval.gameeval.service.impl;

import com.eval.gameeval.mapper.ProjectGroupInfoMapper;
import com.eval.gameeval.mapper.ProjectGroupMapper;
import com.eval.gameeval.mapper.ProjectMapper;
import com.eval.gameeval.mapper.ProjectScorerMapper;
import com.eval.gameeval.mapper.UserMapper;
import com.eval.gameeval.models.DTO.Group.GroupAddToProjectDTO;
import com.eval.gameeval.models.DTO.Group.GroupCreateDTO;
import com.eval.gameeval.models.DTO.Group.GroupQueryDTO;
import com.eval.gameeval.models.DTO.Group.GroupUpdateDTO;
import com.eval.gameeval.models.VO.GroupPageVO;
import com.eval.gameeval.models.VO.GroupOverviewVO;
import com.eval.gameeval.models.VO.GroupVO;
import com.eval.gameeval.models.VO.ResponseVO;
import com.eval.gameeval.models.entity.Project;
import com.eval.gameeval.models.entity.ProjectGroup;
import com.eval.gameeval.models.entity.ProjectGroupInfo;
import com.eval.gameeval.models.entity.ProjectScorer;
import com.eval.gameeval.models.entity.User;
import com.eval.gameeval.service.IGroupService;
import com.eval.gameeval.util.OverviewCacheUtil;
import com.eval.gameeval.util.ProjectCacheUtil;
import com.eval.gameeval.util.RedisToken;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class GroupServiceImpl implements IGroupService {
    @Resource
    private ProjectGroupMapper groupMapper;

    @Resource
    private ProjectGroupInfoMapper groupInfoMapper;

    @Resource
    private ProjectMapper projectMapper;

    @Resource
    private ProjectScorerMapper projectScorerMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private RedisToken redisToken;

    @Resource
    private ProjectCacheUtil projectCacheUtil;

    @Resource
    private OverviewCacheUtil overviewCacheUtil;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseVO<GroupVO> createGroup(String token, GroupCreateDTO request) {
        try {
            // 1. 验证Token并获取当前用户
            Long currentUserId = redisToken.getUserIdByToken(token);
            if (currentUserId == null) {
                return ResponseVO.unauthorized("Token无效");
            }

            User currentUser = userMapper.selectById(currentUserId);
            if (currentUser == null) {
                return ResponseVO.unauthorized("用户不存在");
            }

            // 2. 权限校验：只有管理员可以创建小组
            if (!"super_admin".equals(currentUser.getRole()) && !"admin".equals(currentUser.getRole())) {
                return ResponseVO.forbidden("权限不足，只有管理员可以创建小组");
            }

            LocalDateTime now = LocalDateTime.now();

            // 3. 创建小组信息主记录（project_group_info）
            ProjectGroupInfo groupInfo = new ProjectGroupInfo();
            groupInfo.setName(request.getName());
            groupInfo.setDescription(request.getDescription());
            groupInfo.setIsEnabled(request.getIsEnabled() != null ? request.getIsEnabled() : 1);
            groupInfo.setCreateTime(now);
            groupInfo.setUpdateTime(now);

            groupInfoMapper.insert(groupInfo);
            clearGroupOverviewCache("createGroup");
            log.debug("创建小组信息记录: groupInfoId={}, name={}", groupInfo.getId(), request.getName());

            // 4. 构建响应
            GroupVO responseVO = new GroupVO();
            responseVO.setId(groupInfo.getId());
            responseVO.setName(groupInfo.getName());
            responseVO.setDescription(groupInfo.getDescription());
            responseVO.setIsEnabled(groupInfo.getIsEnabled());
            responseVO.setCreateTime(groupInfo.getCreateTime());
            responseVO.setUpdateTime(groupInfo.getUpdateTime());

            log.info("创建小组成功: groupInfoId={}, name={}, creatorId={}",
                    groupInfo.getId(), request.getName(), currentUserId);

            return ResponseVO.success("创建成功", responseVO);

        } catch (Exception e) {
            log.error("创建小组异常", e);
            return ResponseVO.error("创建失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseVO<GroupVO> addGroupToProject(String token, GroupAddToProjectDTO request) {
        try {
            // 1. 验证Token并获取当前用户
            Long currentUserId = redisToken.getUserIdByToken(token);
            if (currentUserId == null) {
                return ResponseVO.unauthorized("Token无效");
            }

            User currentUser = userMapper.selectById(currentUserId);
            if (currentUser == null) {
                return ResponseVO.unauthorized("用户不存在");
            }

            // 2. 权限校验：只有管理员可以关联小组到项目
            if (!"super_admin".equals(currentUser.getRole()) && !"admin".equals(currentUser.getRole())) {
                return ResponseVO.forbidden("权限不足，只有管理员可以关联小组到项目");
            }

            // 3. 验证小组是否存在
            ProjectGroupInfo groupInfo = groupInfoMapper.selectById(request.getGroupId());
            if (groupInfo == null) {
                return ResponseVO.badRequest("小组不存在");
            }

            // 4. 验证项目是否存在
            Project project = projectMapper.selectById(request.getProjectId());
            if (project == null) {
                return ResponseVO.badRequest("项目不存在");
            }

            // 5. 验证项目是否已结束
            if ("ended".equals(project.getStatus())) {
                return ResponseVO.badRequest("项目已结束，无法关联小组");
            }

            // 6. 验证小组是否已经关联到该项目
            ProjectGroup existingRelation = groupMapper.selectByGroupIdAndProjectId(
                    request.getGroupId(), request.getProjectId());
            if (existingRelation != null) {
                return ResponseVO.badRequest("小组已经关联到该项目");
            }

            LocalDateTime now = LocalDateTime.now();

            // 7. 创建项目-小组关联记录（project_group）
            ProjectGroup relation = new ProjectGroup();
            relation.setProjectId(request.getProjectId());
            relation.setGroupInfoId(request.getGroupId());
            relation.setCreateTime(now);
            relation.setUpdateTime(now);

            groupMapper.insert(relation);

            // 8. 重新加载并更新项目级别的缓存（getProjectGroups使用）
            List<ProjectGroup> updatedRelations = groupMapper.selectByProjectId(request.getProjectId());
            List<GroupVO> updatedGroupVOs = new ArrayList<>();
            for (ProjectGroup rel : updatedRelations) {
                ProjectGroupInfo info = groupInfoMapper.selectById(rel.getGroupInfoId());
                if (info != null) {
                    GroupVO vo = new GroupVO();
                    vo.setId(info.getId());
                    vo.setName(info.getName());
                    vo.setDescription(info.getDescription());
                    vo.setProjectId(request.getProjectId());
                    vo.setRelationId(rel.getId());
                    vo.setIsEnabled(info.getIsEnabled());
                    vo.setCreateTime(info.getCreateTime());
                    vo.setUpdateTime(info.getUpdateTime());
                    updatedGroupVOs.add(vo);
                }
            }
            // 只更新项目级别的小组缓存（用于getProjectGroups）
            projectCacheUtil.cacheProjectGroups(request.getProjectId(), updatedGroupVOs);
            log.debug("已更新项目小组缓存: projectId={}, count={}", request.getProjectId(), updatedGroupVOs.size());

            // 9. 清除所有相关用户的授权项目缓存
            List<ProjectScorer> projectScorers = projectScorerMapper.selectByProjectId(request.getProjectId());
            for (ProjectScorer scorer : projectScorers) {
                projectCacheUtil.clearAuthorizedProjectsCache(scorer.getUserId());
            }
            // 10. 构建响应
            GroupVO responseVO = new GroupVO();
            responseVO.setId(groupInfo.getId());
            responseVO.setName(groupInfo.getName());
            responseVO.setDescription(groupInfo.getDescription());
            responseVO.setProjectId(request.getProjectId());
            responseVO.setRelationId(relation.getId());
            responseVO.setIsEnabled(groupInfo.getIsEnabled());
            responseVO.setCreateTime(groupInfo.getCreateTime());
            responseVO.setUpdateTime(groupInfo.getUpdateTime());
            clearGroupOverviewCache("addGroupToProject");

            log.info("将小组关联到项目成功: groupId={}, projectId={}, relationId={}, operatorId={}",
                    request.getGroupId(), request.getProjectId(), relation.getId(), currentUserId);

            return ResponseVO.success("关联成功", responseVO);

        } catch (Exception e) {
            log.error("关联小组到项目异常", e);
            return ResponseVO.error("关联失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseVO<GroupVO> updateGroup(String token, GroupUpdateDTO request) {
        try {
            // 1. 验证Token并获取当前用户
            Long currentUserId = redisToken.getUserIdByToken(token);
            if (currentUserId == null) {
                return ResponseVO.unauthorized("Token无效");
            }

            User currentUser = userMapper.selectById(currentUserId);
            if (currentUser == null) {
                return ResponseVO.unauthorized("用户不存在");
            }

            // 2. 权限校验：只有管理员可以编辑小组
            if (!"super_admin".equals(currentUser.getRole()) && !"admin".equals(currentUser.getRole())) {
                return ResponseVO.forbidden("权限不足，只有管理员可以编辑小组");
            }

            // 3. 验证小组是否存在
            ProjectGroupInfo groupInfo = groupInfoMapper.selectById(request.getId());
            if (groupInfo == null) {
                return ResponseVO.notFound("小组不存在");
            }

            LocalDateTime now = LocalDateTime.now();

            // 4. 更新小组信息
            groupInfo.setName(request.getName());
            groupInfo.setDescription(request.getDescription());
            if (request.getIsEnabled() != null) {
                groupInfo.setIsEnabled(request.getIsEnabled());
            }
            groupInfo.setUpdateTime(now);

            groupInfoMapper.update(groupInfo);
            log.debug("更新小组信息: groupId={}, name={}", request.getId(), request.getName());

            // 5. 更新所有关联项目的缓存
            List<ProjectGroup> relations = groupMapper.selectByGroupId(request.getId());
            for (ProjectGroup rel : relations) {
                // 重新查询该项目的所有小组
                List<ProjectGroup> projectRelations = groupMapper.selectByProjectId(rel.getProjectId());
                List<GroupVO> projectGroupVOs = new ArrayList<>();
                for (ProjectGroup projectRel : projectRelations) {
                    ProjectGroupInfo info = groupInfoMapper.selectById(projectRel.getGroupInfoId());
                    if (info != null) {
                        GroupVO vo = new GroupVO();
                        vo.setId(info.getId());
                        vo.setName(info.getName());
                        vo.setDescription(info.getDescription());
                        vo.setProjectId(rel.getProjectId());
                        vo.setRelationId(projectRel.getId());
                        vo.setIsEnabled(info.getIsEnabled());
                        vo.setCreateTime(info.getCreateTime());
                        vo.setUpdateTime(info.getUpdateTime());
                        projectGroupVOs.add(vo);
                    }
                }
                projectCacheUtil.cacheProjectGroups(rel.getProjectId(), projectGroupVOs);
                log.debug("已更新项目小组缓存: projectId={}", rel.getProjectId());

                // 清除该项目下所有打分人员的缓存
                List<ProjectScorer> scorers = projectScorerMapper.selectByProjectId(rel.getProjectId());
                for (ProjectScorer scorer : scorers) {
                    projectCacheUtil.clearAuthorizedProjectsCache(scorer.getUserId());
                }
            }
            clearGroupOverviewCache("updateGroup");

            // 6. 构建响应
            GroupVO responseVO = new GroupVO();
            responseVO.setId(groupInfo.getId());
            responseVO.setName(groupInfo.getName());
            responseVO.setDescription(groupInfo.getDescription());
            responseVO.setIsEnabled(groupInfo.getIsEnabled());
            responseVO.setCreateTime(groupInfo.getCreateTime());
            responseVO.setUpdateTime(groupInfo.getUpdateTime());

            log.info("编辑小组成功: groupId={}, name={}, operatorId={}",
                    request.getId(), request.getName(), currentUserId);

            return ResponseVO.success("编辑成功", responseVO);

        } catch (Exception e) {
            log.error("编辑小组异常", e);
            return ResponseVO.error("编辑失败: " + e.getMessage());
        }
    }

    @Override
    public ResponseVO<List<GroupVO>> getProjectGroups(String token, Long projectId) {
        try {
            // 1. 验证Token
            Long currentUserId = redisToken.getUserIdByToken(token);
            if (currentUserId == null) {
                return ResponseVO.unauthorized("Token无效");
            }

            User currentUser = userMapper.selectById(currentUserId);
            if (currentUser == null) {
                return ResponseVO.unauthorized("用户不存在");
            }

            // 2. 验证项目是否存在
            Project project = projectMapper.selectById(projectId);
            if (project == null) {
                return ResponseVO.notFound("项目不存在");
            }

            // 3. 新增：检查当前用户是否被授权查看该项目
            // 用户必须在project_scorer中有该项目的授权记录，否则无权访问
            List<ProjectScorer> projectScorers = projectScorerMapper.selectByProjectId(projectId);
            boolean isAuthorized = projectScorers.stream()
                    .anyMatch(scorer -> scorer.getUserId().equals(currentUserId));
            if (!isAuthorized) {
                // 权限校验：只有管理员可以查看非授权小组
                if (!"super_admin".equals(currentUser.getRole()) && !"admin".equals(currentUser.getRole())) {
                    log.warn("用户未授权访问项目的小组列表: userId={}, projectId={}", currentUserId, projectId);
                    return ResponseVO.forbidden("权限不足，只有管理员可以查看非授权的项目小组");
                }
            }

            // 4. 尝试从缓存获取小组列表
            Object cache = projectCacheUtil.getProjectGroupsCache(projectId);
            if (cache != null) {
                @SuppressWarnings("unchecked")
                List<GroupVO> cachedList = (List<GroupVO>) cache;
                log.info("【缓存命中】获取项目小组: projectId={}, count={}", projectId, cachedList.size());
                return ResponseVO.success("查询成功", cachedList);
            }

            // 5. 缓存未命中：查询关联关系
            log.info("【缓存未命中】查询数据库: projectId={}", projectId);
            List<ProjectGroup> relations = groupMapper.selectByProjectId(projectId);

            // 6. 获取小组信息并转换为VO列表
            List<GroupVO> groupVOs = new ArrayList<>();
            for (ProjectGroup relation : relations) {
                ProjectGroupInfo groupInfo = groupInfoMapper.selectById(relation.getGroupInfoId());
                if (groupInfo != null) {
                    GroupVO vo = new GroupVO();
                    vo.setId(groupInfo.getId());
                    vo.setName(groupInfo.getName());
                    vo.setDescription(groupInfo.getDescription());
                    vo.setProjectId(projectId);
                    vo.setRelationId(relation.getId());
                    vo.setIsEnabled(groupInfo.getIsEnabled());
                    vo.setCreateTime(groupInfo.getCreateTime());
                    vo.setUpdateTime(groupInfo.getUpdateTime());
                    groupVOs.add(vo);
                }
            }

            projectCacheUtil.cacheProjectGroups(projectId, groupVOs);
            log.info("查询项目小组列表成功: projectId={}, count={}, userId={}", projectId, groupVOs.size(), currentUserId);

            return ResponseVO.success("查询成功", groupVOs);

        } catch (Exception e) {
            log.error("查询项目小组列表异常: projectId={}", projectId, e);
            return ResponseVO.error("查询失败: " + e.getMessage());
        }
    }

    @Override
    public ResponseVO<GroupPageVO> getAllGroups(String token, GroupQueryDTO query) {
        try {
            // 1. 验证Token
            Long currentUserId = redisToken.getUserIdByToken(token);
            if (currentUserId == null) {
                return ResponseVO.unauthorized("Token无效");
            }

            User currentUser = userMapper.selectById(currentUserId);
            if (currentUser == null) {
                return ResponseVO.unauthorized("用户不存在");
            }

            // 2. 权限校验：小组信息管理面板
            String role = currentUser.getRole();
            boolean isAdmin = "super_admin".equals(role) || "admin".equals(role);
            
            if (!isAdmin && !"scorer".equals(role)) {
                // 普通用户：无权查询小组列表
                return ResponseVO.forbidden("无权查询小组列表");
            }

            log.debug("用户查询小组信息: userId={}, role={}", currentUserId, role);

            // 3. 处理分页参数
            int pageNum = query.getPage() != null ? query.getPage() : 1;
            int pageSize = query.getSize() != null ? query.getSize() : 10;
            int offset = (pageNum - 1) * pageSize;

            // 4. 查询所有小组信息（从project_group_info表，不限制项目）
            List<ProjectGroupInfo> groupInfoList = groupInfoMapper.selectPageWithSearch(
                    offset,
                    pageSize,
                    query.getKeyWords()
            );

            Long total = groupInfoMapper.countWithSearch(query.getKeyWords());

            // 5. 转换为VO
            List<GroupPageVO.GroupVO> groupVOs = new ArrayList<>();
            for (ProjectGroupInfo groupInfo : groupInfoList) {
                GroupPageVO.GroupVO vo = new GroupPageVO.GroupVO();
                vo.setId(groupInfo.getId());
                vo.setName(groupInfo.getName());
                vo.setDescription(groupInfo.getDescription());
                vo.setIsEnabled(groupInfo.getIsEnabled());
                vo.setCreateTime(groupInfo.getCreateTime());
                vo.setUpdateTime(groupInfo.getUpdateTime());
                // 注：不再包含projectId，因为这是全局的小组信息面板
                groupVOs.add(vo);
            }

            // 6. 构建分页响应
            GroupPageVO pageVO = new GroupPageVO();
            pageVO.setList(groupVOs);
            pageVO.setTotal(total);
            pageVO.setPage(pageNum);
            pageVO.setSize(pageSize);

            log.info("查询小组列表成功: userId={}, total={}, page={}, size={}",
                    currentUserId, total, pageNum, pageSize);

            return ResponseVO.success("查询成功", pageVO);

        } catch (Exception e) {
            log.error("查询小组列表异常", e);
            return ResponseVO.error("查询失败: " + e.getMessage());
        }
    }

    @Override
    public ResponseVO<GroupOverviewVO> getGroupOverview(String token) {
        try {
            Long currentUserId = redisToken.getUserIdByToken(token);
            if (currentUserId == null) {
                return ResponseVO.unauthorized("Token无效");
            }

            User currentUser = userMapper.selectById(currentUserId);
            if (currentUser == null) {
                return ResponseVO.unauthorized("用户不存在");
            }

            if (!"super_admin".equals(currentUser.getRole()) && !"admin".equals(currentUser.getRole())) {
                return ResponseVO.forbidden("权限不足，只有管理员可以查看小组概览");
            }

            Object cache = overviewCacheUtil.getGroupOverviewCache();
            if (cache != null) {
                GroupOverviewVO cachedOverview = (GroupOverviewVO) cache;
                log.info("【缓存命中】查询小组概览: totalGroups={}", cachedOverview.getTotalGroups());
                return ResponseVO.success("查询成功", cachedOverview);
            }

            Map<String, Object> overviewMap = groupInfoMapper.selectGroupOverview();
            if (overviewMap == null) {
                overviewMap = Collections.emptyMap();
            }

            Long totalGroups = toLong(overviewMap.get("totalGroups"));
            Long totalMembers = toLong(overviewMap.get("totalMembers"));
            BigDecimal avgGroupSize = BigDecimal.ZERO;
            if (totalGroups != null && totalGroups > 0) {
                avgGroupSize = BigDecimal.valueOf(totalMembers)
                        .divide(BigDecimal.valueOf(totalGroups), 2, RoundingMode.HALF_UP);
            }

            GroupOverviewVO overviewVO = new GroupOverviewVO()
                    .setTotalGroups(totalGroups)
                    .setActiveGroups(toLong(overviewMap.get("activeGroups")))
                    .setTotalMembers(totalMembers)
                    .setAvgGroupSize(avgGroupSize);

                overviewCacheUtil.cacheGroupOverview(overviewVO);

            return ResponseVO.success("查询成功", overviewVO);
        } catch (Exception e) {
            log.error("查询小组概览异常", e);
            return ResponseVO.error("查询失败: " + e.getMessage());
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

    private void clearGroupOverviewCache(String scene) {
        overviewCacheUtil.clearGroupOverviewCache();
        log.debug("已清理小组概览缓存: scene={}", scene);
    }
}
