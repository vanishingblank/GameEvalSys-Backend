package com.eval.gameeval.service.impl;

import com.eval.gameeval.mapper.ReviewerGroupMapper;
import com.eval.gameeval.mapper.ReviewerGroupMemberMapper;
import com.eval.gameeval.mapper.UserMapper;
import com.eval.gameeval.models.DTO.ReviewerGroupCreateDTO;
import com.eval.gameeval.models.VO.ResponseVO;
import com.eval.gameeval.models.VO.ReviewerGroupVO;
import com.eval.gameeval.models.entity.ReviewerGroup;
import com.eval.gameeval.models.entity.ReviewerGroupMember;
import com.eval.gameeval.models.entity.User;
import com.eval.gameeval.service.IReviewerGroupService;
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
public class ReviewerGroupServiceImpl implements IReviewerGroupService {

    @Resource
    private ReviewerGroupMapper groupMapper;

    @Resource
    private ReviewerGroupMemberMapper memberMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private RedisToken redisToken;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseVO<ReviewerGroupVO> createReviewerGroup(String token, ReviewerGroupCreateDTO request) {
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

            // 2. 验证成员是否存在
            List<Long> invalidMemberIds = new ArrayList<>();
            for (Long memberId : request.getMemberIds()) {
                User member = userMapper.selectById(memberId);
                if (member == null) {
                    invalidMemberIds.add(memberId);
                }
            }
            if (!invalidMemberIds.isEmpty()) {
                return ResponseVO.badRequest("以下用户不存在: " + invalidMemberIds);
            }

            // 3. 创建评审组
            ReviewerGroup group = new ReviewerGroup();
            group.setName(request.getName());
            group.setDescription(request.getDescription() != null ? request.getDescription() : "");
            group.setCreatorId(currentUserId);
            group.setIsEnabled(request.getIsEnabled() != null ? request.getIsEnabled() : true);
            group.setCreateTime(LocalDateTime.now());
            group.setUpdateTime(LocalDateTime.now());

            groupMapper.insert(group);

            // 4. 创建成员关联
            List<ReviewerGroupMember> members = new ArrayList<>();
            for (Long memberId : request.getMemberIds()) {
                ReviewerGroupMember member = new ReviewerGroupMember();
                member.setGroupId(group.getId());
                member.setUserId(memberId);
                member.setCreateTime(LocalDateTime.now());
                members.add(member);
            }
            if (!members.isEmpty()) {
                memberMapper.insertBatch(members);
            }

            // 5. 构建响应
            ReviewerGroupVO responseVO = new ReviewerGroupVO();
            BeanUtils.copyProperties(group, responseVO);
            responseVO.setMemberIds(request.getMemberIds());

            log.info("创建评审组成功: groupId={}, creatorId={}", group.getId(), currentUserId);

            return ResponseVO.success("创建成功", responseVO);

        } catch (Exception e) {
            log.error("创建评审组异常", e);
            return ResponseVO.error("创建失败: " + e.getMessage());
        }
    }

    @Override
    public ResponseVO<List<ReviewerGroupVO>> getReviewerGroupList(String token) {
        try {
            // 1. 验证Token
            Long currentUserId = redisToken.getUserIdByToken(token);
            if (currentUserId == null) {
                return ResponseVO.unauthorized("Token无效");
            }

            // 2. 查询所有启用的评审组
            List<ReviewerGroup> groups = groupMapper.selectAllEnabled();

            // 3. 转换为VO
            List<ReviewerGroupVO> groupVOs = new ArrayList<>();
            for (ReviewerGroup group : groups) {
                ReviewerGroupVO vo = new ReviewerGroupVO();
                BeanUtils.copyProperties(group, vo);

                // 查询成员列表
                List<Long> memberIds = memberMapper.selectUserIdsByGroupId(group.getId());
                vo.setMemberIds(memberIds);

                groupVOs.add(vo);
            }

            log.info("查询评审组列表成功: count={}", groupVOs.size());

            return ResponseVO.success("查询成功", groupVOs);

        } catch (Exception e) {
            log.error("查询评审组列表异常", e);
            return ResponseVO.error("查询失败: " + e.getMessage());
        }
    }

    @Override
    public ResponseVO<ReviewerGroupVO> getReviewerGroupDetail(String token, Long groupId) {
        try {
            // 1. 验证Token
            Long currentUserId = redisToken.getUserIdByToken(token);
            if (currentUserId == null) {
                return ResponseVO.unauthorized("Token无效");
            }

            // 2. 查询评审组
            ReviewerGroup group = groupMapper.selectById(groupId);
            if (group == null) {
                return ResponseVO.notFound("评审组不存在");
            }

            // 3. 构建响应
            ReviewerGroupVO responseVO = new ReviewerGroupVO();
            BeanUtils.copyProperties(group, responseVO);

            // 查询成员列表
            List<Long> memberIds = memberMapper.selectUserIdsByGroupId(groupId);
            responseVO.setMemberIds(memberIds);

            log.info("查询评审组详情成功: groupId={}", groupId);

            return ResponseVO.success("查询成功", responseVO);

        } catch (Exception e) {
            log.error("查询评审组详情异常: groupId={}", groupId, e);
            return ResponseVO.error("查询失败: " + e.getMessage());
        }
    }
}