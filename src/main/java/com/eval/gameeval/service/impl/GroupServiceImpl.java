package com.eval.gameeval.service.impl;

import com.eval.gameeval.mapper.ProjectGroupInfoMapper;
import com.eval.gameeval.mapper.ProjectGroupMapper;
import com.eval.gameeval.mapper.ProjectMapper;
import com.eval.gameeval.mapper.UserMapper;
import com.eval.gameeval.models.DTO.GroupCreateDTO;
import com.eval.gameeval.models.DTO.GroupQueryDTO;
import com.eval.gameeval.models.VO.GroupPageVO;
import com.eval.gameeval.models.VO.GroupVO;
import com.eval.gameeval.models.VO.ResponseVO;
import com.eval.gameeval.models.entity.Project;
import com.eval.gameeval.models.entity.ProjectGroup;
import com.eval.gameeval.models.entity.ProjectGroupInfo;
import com.eval.gameeval.models.entity.User;
import com.eval.gameeval.service.IGroupService;
import com.eval.gameeval.util.ProjectCacheUtil;
import com.eval.gameeval.util.RedisToken;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    private UserMapper userMapper;

    @Resource
    private RedisToken redisToken;

    @Resource
    private ProjectCacheUtil projectCacheUtil;

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

            // 3. 验证项目是否存在
            Project project = projectMapper.selectById(request.getProjectId());
            if (project == null) {
                return ResponseVO.badRequest("项目不存在");
            }

            // 4. 验证项目是否已结束
            if ("ended".equals(project.getStatus())) {
                return ResponseVO.badRequest("项目已结束，无法创建小组");
            }

            LocalDateTime now = LocalDateTime.now();

            // 5. 先创建小组信息主记录（project_group_info）
            ProjectGroupInfo groupInfo = new ProjectGroupInfo();
            groupInfo.setName(request.getName());
            groupInfo.setDescription(request.getDescription());
            groupInfo.setIsEnabled(1);
            groupInfo.setCreateTime(now);
            groupInfo.setUpdateTime(now);

            groupInfoMapper.insert(groupInfo);
            log.debug("创建小组信息记录: groupInfoId={}, name={}", groupInfo.getId(), request.getName());

            // 6. 再创建项目-小组关联记录（project_group）
            ProjectGroup relation = new ProjectGroup();
            relation.setProjectId(request.getProjectId());
            relation.setGroupInfoId(groupInfo.getId());
            relation.setCreateTime(now);
            relation.setUpdateTime(now);

            groupMapper.insert(relation);
            projectCacheUtil.clearProjectGroupsCache(request.getProjectId());

            // 7. 构建响应
            GroupVO responseVO = new GroupVO();
            responseVO.setId(groupInfo.getId());
            responseVO.setName(groupInfo.getName());
            responseVO.setDescription(groupInfo.getDescription());
            responseVO.setProjectId(request.getProjectId());
            responseVO.setRelationId(relation.getId());
            responseVO.setIsEnabled(groupInfo.getIsEnabled());
            responseVO.setCreateTime(groupInfo.getCreateTime());
            responseVO.setUpdateTime(groupInfo.getUpdateTime());

            log.info("创建小组成功: groupInfoId={}, relationId={}, projectId={}, creatorId={}",
                    groupInfo.getId(), relation.getId(), request.getProjectId(), currentUserId);

            return ResponseVO.success("创建成功", responseVO);

        } catch (Exception e) {
            log.error("创建小组异常", e);
            return ResponseVO.error("创建失败: " + e.getMessage());
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

            // 2. 验证项目是否存在
            Project project = projectMapper.selectById(projectId);
            if (project == null) {
                return ResponseVO.notFound("项目不存在");
            }

            // 3. 尝试从缓存获取小组列表
            Object cache = projectCacheUtil.getProjectGroupsCache(projectId);
            if (cache != null) {
                @SuppressWarnings("unchecked")
                List<GroupVO> cachedList = (List<GroupVO>) cache;
                log.info("【缓存命中】获取项目小组: projectId={}, count={}", projectId, cachedList.size());
                return ResponseVO.success("查询成功", cachedList);
            }

            // 4. 缓存未命中：查询关联关系
            log.info("【缓存未命中】查询数据库: projectId={}", projectId);
            List<ProjectGroup> relations = groupMapper.selectByProjectId(projectId);

            // 5. 获取小组信息并转换为VO列表
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
            log.info("查询项目小组列表成功: projectId={}, count={}", projectId, groupVOs.size());

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

            // 2. 权限校验：确定可查询的项目范围
            List<Long> allowedProjectIds = null;

            boolean isAdmin = "super_admin".equals(currentUser.getRole()) || "admin".equals(currentUser.getRole());
            if (isAdmin) {
                // 管理员：可查询所有小组（不限制项目）
                log.debug("管理员查询所有小组: userId={}", currentUserId);
            } else if ("scorer".equals(currentUser.getRole())) {
                // 打分用户：仅可查询自己有权限打分的项目中的小组
                List<Project> projects = projectMapper.selectByScorerId(currentUserId);
                allowedProjectIds = projects.stream()
                        .map(Project::getId)
                        .collect(Collectors.toList());

                if (allowedProjectIds.isEmpty()) {
                    log.warn("打分用户无权限查询任何小组: userId={}", currentUserId);
                    GroupPageVO emptyPage = new GroupPageVO();
                    emptyPage.setList(new ArrayList<>());
                    emptyPage.setTotal(0L);
                    emptyPage.setPage(query.getPage());
                    emptyPage.setSize(query.getSize());
                    return ResponseVO.success("查询成功", emptyPage);
                }
                log.debug("打分用户查询有权限的小组: userId={}, projectCount={}",
                        currentUserId, allowedProjectIds.size());
            } else {
                // 普通用户：无权查询
                return ResponseVO.forbidden("无权查询小组列表");
            }

            // 3. 处理分页参数
            Integer pageNum = query.getPage() != null ? query.getPage() : 1;
            Integer pageSize = query.getSize() != null ? query.getSize() : 10;
            int page = pageNum;
            int size = pageSize;
            int offset = (page - 1) * size;

            // 4. 查询小组列表（带小组信息）
            List<Map<String, Object>> groupMaps = groupMapper.selectPageWithProject(
                    offset,
                    size,
                    query.getKeyWords(),
                    allowedProjectIds
            );

            Long total = groupMapper.countTotalWithProject(
                    query.getKeyWords(),
                    allowedProjectIds
            );

            // 5. 转换为VO
            List<GroupPageVO.GroupVO> groupVOs = new ArrayList<>();
            for (Map<String, Object> groupMap : groupMaps) {
                GroupPageVO.GroupVO vo = new GroupPageVO.GroupVO();
                vo.setId(((Number) groupMap.get("groupInfoId")).longValue());
                vo.setName((String) groupMap.get("name"));
                vo.setDescription((String) groupMap.get("description"));
                vo.setProjectId(((Number) groupMap.get("projectId")).longValue());
                
                // 处理isEnabled字段：可能是Boolean或Integer
                Object isEnabledValue = groupMap.get("isEnabled");
                Integer isEnabledInt = null;
                if (isEnabledValue instanceof Boolean) {
                    isEnabledInt = (Boolean) isEnabledValue ? 1 : 0;
                } else if (isEnabledValue instanceof Number) {
                    isEnabledInt = ((Number) isEnabledValue).intValue();
                }
                vo.setIsEnabled(isEnabledInt);
                
                vo.setCreateTime((LocalDateTime) groupMap.get("createTime"));
                vo.setUpdateTime((LocalDateTime) groupMap.get("updateTime"));
                groupVOs.add(vo);
            }

            // 6. 构建分页响应
            GroupPageVO pageVO = new GroupPageVO();
            pageVO.setList(groupVOs);
            pageVO.setTotal(total);
            pageVO.setPage(page);
            pageVO.setSize(size);

            log.info("查询小组列表成功: userId={}, total={}, page={}, size={}",
                    currentUserId, total, page, size);

            return ResponseVO.success("查询成功", pageVO);

        } catch (Exception e) {
            log.error("查询小组列表异常", e);
            return ResponseVO.error("查询失败: " + e.getMessage());
        }
    }
}
