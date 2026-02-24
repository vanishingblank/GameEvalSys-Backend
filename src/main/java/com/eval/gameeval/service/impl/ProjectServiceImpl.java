package com.eval.gameeval.service.impl;

import com.eval.gameeval.mapper.*;
import com.eval.gameeval.models.DTO.ProjectCreateDTO;
import com.eval.gameeval.models.DTO.ProjectQueryDTO;
import com.eval.gameeval.models.DTO.ProjectUpdateDTO;
import com.eval.gameeval.models.VO.ProjectPageVO;
import com.eval.gameeval.models.VO.ProjectVO;
import com.eval.gameeval.models.VO.ResponseVO;
import com.eval.gameeval.models.entity.*;
import com.eval.gameeval.service.IProjectService;
import com.eval.gameeval.util.RedisToken;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ProjectServiceImpl implements IProjectService {
    @Resource
    private ProjectMapper projectMapper;

    @Resource
    private ProjectGroupMapper groupMapper;

    @Resource
    private ProjectScorerMapper scorerMapper;

    @Resource
    private ScoringStandardMapper standardMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private RedisToken redisToken;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseVO<ProjectVO> createProject(String token, ProjectCreateDTO request) {
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

            // 4. 创建项目
            Project project = new Project();
            project.setName(request.getName());
            project.setDescription(request.getDescription() != null ? request.getDescription() : "");
            project.setStartDate(request.getStartDate().atStartOfDay());
            project.setEndDate(request.getEndDate().atStartOfDay());
            project.setStatus("not_started");
            project.setIsEnabled(request.getIsEnabled() != null ? request.getIsEnabled() : true);
            project.setStandardId(request.getStandardId());
            project.setCreatorId(currentUserId);
            project.setCreateTime(LocalDateTime.now());
            project.setUpdateTime(LocalDateTime.now());

            projectMapper.insert(project);

            // 5. 创建小组关联
            List<ProjectGroup> groups = new ArrayList<>();
            for (Long groupId : request.getGroupIds()) {
                ProjectGroup group = new ProjectGroup();
                group.setProjectId(project.getId());
                group.setName("小组" + groupId); // 实际应从数据库获取小组名称
                group.setCreateTime(LocalDateTime.now());
                group.setUpdateTime(LocalDateTime.now());
                groups.add(group);
            }
            if (!groups.isEmpty()) {
                groupMapper.insertBatch(groups);
            }

            // 6. 创建打分用户关联
            List<ProjectScorer> scorers = new ArrayList<>();
            for (Long scorerId : request.getScorerIds()) {
                // 验证打分用户是否存在
                User scorer = userMapper.selectById(scorerId);
                if (scorer == null) {
                    return ResponseVO.badRequest("打分用户ID " + scorerId + " 不存在");
                }
                if (!"scorer".equals(scorer.getRole()) && !"admin".equals(scorer.getRole()) && !"super_admin".equals(scorer.getRole())) {
                    return ResponseVO.badRequest("用户 " + scorerId + " 不是打分用户");
                }

                ProjectScorer scorerRel = new ProjectScorer();
                scorerRel.setProjectId(project.getId());
                scorerRel.setUserId(scorerId);
                scorerRel.setCreateTime(LocalDateTime.now());
                scorers.add(scorerRel);
            }
            if (!scorers.isEmpty()) {
                scorerMapper.insertBatch(scorers);
            }

            // 7. 构建响应
            ProjectVO responseVO = new ProjectVO();
            BeanUtils.copyProperties(project, responseVO);
            responseVO.setGroupIds(request.getGroupIds());
            responseVO.setScorerIds(request.getScorerIds());

            log.info("创建项目成功: projectId={}, creatorId={}", project.getId(), currentUserId);

            return ResponseVO.success("创建成功", responseVO);

        } catch (Exception e) {
            log.error("创建项目异常", e);
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

            // 3. 构建更新对象（保留原值）
            Project updateProject = new Project();
            updateProject.setId(projectId);
            updateProject.setUpdateTime(LocalDateTime.now());

            updateProject.setName(request.getName() != null ? request.getName() : project.getName());
            updateProject.setDescription(request.getDescription() != null ? request.getDescription() : project.getDescription());
            updateProject.setStartDate(request.getStartDate() != null ? request.getStartDate().atStartOfDay() : project.getStartDate());
            updateProject.setEndDate(request.getEndDate() != null ? request.getEndDate().atStartOfDay() : project.getEndDate());
            updateProject.setIsEnabled(request.getIsEnabled() != null ? request.getIsEnabled() : project.getIsEnabled());
            updateProject.setStandardId(request.getStandardId() != null ? request.getStandardId() : project.getStandardId());
            updateProject.setStatus(project.getStatus()); // 状态不变

            // 4. 更新项目
            projectMapper.updateById(updateProject);

            // 5. 更新关联数据（先删除再插入）
            if (request.getGroupIds() != null) {
                groupMapper.deleteByProjectId(projectId);
                List<ProjectGroup> groups = new ArrayList<>();
                for (Long groupId : request.getGroupIds()) {
                    ProjectGroup group = new ProjectGroup();
                    group.setProjectId(projectId);
                    group.setName("小组" + groupId);
                    group.setCreateTime(LocalDateTime.now());
                    group.setUpdateTime(LocalDateTime.now());
                    groups.add(group);
                }
                if (!groups.isEmpty()) {
                    groupMapper.insertBatch(groups);
                }
            }

            if (request.getScorerIds() != null) {
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

            // 2. 处理分页
            int page = query.getPage() != null ? query.getPage() : 1;
            int size = query.getSize() != null ? query.getSize() : 10;
            int offset = (page - 1) * size;

            // 3. 查询项目列表
            List<Project> projects = projectMapper.selectPage(offset, size, query.getStatus(), query.getIsEnabled());
            Long total = projectMapper.countTotal(query.getStatus(), query.getIsEnabled());

            // 4. 转换为VO
            List<ProjectVO> projectVOs = new ArrayList<>();
            for (Project project : projects) {
                ProjectVO vo = new ProjectVO();
                BeanUtils.copyProperties(project, vo);

                // 查询关联的小组
                List<ProjectGroup> groups = groupMapper.selectByProjectId(project.getId());
                List<Long> groupIds = groups.stream().map(ProjectGroup::getId).collect(Collectors.toList());
                vo.setGroupIds(groupIds);

                // 查询关联的打分用户
                List<ProjectScorer> scorers = scorerMapper.selectByProjectId(project.getId());
                List<Long> scorerIds = scorers.stream().map(ProjectScorer::getUserId).collect(Collectors.toList());
                vo.setScorerIds(scorerIds);

                projectVOs.add(vo);
            }

            // 5. 构建分页响应
            ProjectPageVO pageVO = new ProjectPageVO();
            pageVO.setList(projectVOs);
            pageVO.setTotal(total);
            pageVO.setPage(page);
            pageVO.setSize(size);

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

            // 2. 查询项目
            Project project = projectMapper.selectById(projectId);
            if (project == null) {
                return ResponseVO.notFound("项目不存在");
            }

            // 3. 构建响应
            ProjectVO responseVO = new ProjectVO();
            BeanUtils.copyProperties(project, responseVO);

            // 查询关联的小组
            List<ProjectGroup> groups = groupMapper.selectByProjectId(projectId);
            List<Long> groupIds = groups.stream().map(ProjectGroup::getId).collect(Collectors.toList());
            responseVO.setGroupIds(groupIds);

            // 查询关联的打分用户
            List<ProjectScorer> scorers = scorerMapper.selectByProjectId(projectId);
            List<Long> scorerIds = scorers.stream().map(ProjectScorer::getUserId).collect(Collectors.toList());
            responseVO.setScorerIds(scorerIds);

            log.info("查询项目详情成功: projectId={}", projectId);

            return ResponseVO.success("查询成功", responseVO);

        } catch (Exception e) {
            log.error("查询项目详情异常: projectId={}", projectId, e);
            return ResponseVO.error("查询失败: " + e.getMessage());
        }
    }

    @Override
    public ResponseVO<List<ProjectVO>> getAuthorizedProjects(String token) {
        Long currentUserId = null;
        try {
            // 1. 验证Token
            currentUserId = redisToken.getUserIdByToken(token);
            if (currentUserId == null) {
                return ResponseVO.unauthorized("Token无效");
            }

            // 2. 查询当前用户有权限的项目（作为打分用户）
            List<Project> projects = projectMapper.selectByScorerId(currentUserId);

            // 3. 转换为VO
            List<ProjectVO> projectVOs = new ArrayList<>();
            for (Project project : projects) {
                ProjectVO vo = new ProjectVO();
                BeanUtils.copyProperties(project, vo);

                // 查询关联的小组
                List<ProjectGroup> groups = groupMapper.selectByProjectId(project.getId());
                List<Long> groupIds = groups.stream().map(ProjectGroup::getId).collect(Collectors.toList());
                vo.setGroupIds(groupIds);

                // 查询关联的打分用户
                List<ProjectScorer> scorers = scorerMapper.selectByProjectId(project.getId());
                List<Long> scorerIds = scorers.stream().map(ProjectScorer::getUserId).collect(Collectors.toList());
                vo.setScorerIds(scorerIds);

                projectVOs.add(vo);
            }

            log.info("查询授权项目列表成功: userId={}, count={}", currentUserId, projectVOs.size());

            return ResponseVO.success("查询成功", projectVOs);

        } catch (Exception e) {
            log.error("查询授权项目列表异常: userId={}", currentUserId, e);
            return ResponseVO.error("查询失败: " + e.getMessage());
        }
    }
}
