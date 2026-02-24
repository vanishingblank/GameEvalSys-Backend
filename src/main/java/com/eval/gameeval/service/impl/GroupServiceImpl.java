package com.eval.gameeval.service.impl;

import com.eval.gameeval.mapper.ProjectGroupMapper;
import com.eval.gameeval.mapper.ProjectMapper;
import com.eval.gameeval.mapper.UserMapper;
import com.eval.gameeval.models.DTO.GroupCreateDTO;
import com.eval.gameeval.models.VO.GroupVO;
import com.eval.gameeval.models.VO.ResponseVO;
import com.eval.gameeval.models.entity.Project;
import com.eval.gameeval.models.entity.ProjectGroup;
import com.eval.gameeval.models.entity.User;
import com.eval.gameeval.service.IGroupService;
import com.eval.gameeval.util.RedisToken;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class GroupServiceImpl implements IGroupService {
    @Resource
    private ProjectGroupMapper groupMapper;

    @Resource
    private ProjectMapper projectMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private RedisToken redisToken;

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

            // 5. 创建小组
            ProjectGroup group = new ProjectGroup();
            group.setProjectId(request.getProjectId());
            group.setName(request.getName());
            group.setCreateTime(LocalDateTime.now());
            group.setUpdateTime(LocalDateTime.now());

            groupMapper.insert(group);

            // 6. 构建响应
            GroupVO responseVO = new GroupVO();
            BeanUtils.copyProperties(group, responseVO);

            log.info("创建小组成功: groupId={}, projectId={}, creatorId={}",
                    group.getId(), request.getProjectId(), currentUserId);

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

            // 3. 查询项目关联的小组列表
            List<ProjectGroup> groups = groupMapper.selectByProjectId(projectId);

            // 4. 转换为VO列表
            List<GroupVO> groupVOs = groups.stream()
                    .map(group -> {
                        GroupVO vo = new GroupVO();
                        BeanUtils.copyProperties(group, vo);
                        return vo;
                    })
                    .collect(Collectors.toList());

            log.info("查询项目小组列表成功: projectId={}, count={}", projectId, groupVOs.size());

            return ResponseVO.success("查询成功", groupVOs);

        } catch (Exception e) {
            log.error("查询项目小组列表异常: projectId={}", projectId, e);
            return ResponseVO.error("查询失败: " + e.getMessage());
        }
    }
}
