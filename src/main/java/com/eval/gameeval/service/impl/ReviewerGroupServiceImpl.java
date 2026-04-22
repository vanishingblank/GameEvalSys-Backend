package com.eval.gameeval.service.impl;

import com.eval.gameeval.mapper.ReviewerGroupMapper;
import com.eval.gameeval.mapper.ReviewerGroupMemberMapper;
import com.eval.gameeval.mapper.UserMapper;
import com.eval.gameeval.models.DTO.ReviewerGroup.ReviewerGroupCreateDTO;
import com.eval.gameeval.models.DTO.ReviewerGroup.ReviewerGroupQueryDTO;
import com.eval.gameeval.models.DTO.ReviewerGroup.ReviewerGroupUpdateDTO;
import com.eval.gameeval.models.VO.ResponseVO;
import com.eval.gameeval.models.VO.ReviewerGroupOverviewVO;
import com.eval.gameeval.models.VO.ReviewerGroupVO;
import com.eval.gameeval.models.VO.ReviewerGroupPageVO;
import com.eval.gameeval.models.entity.ReviewerGroup;
import com.eval.gameeval.models.entity.ReviewerGroupMember;
import com.eval.gameeval.models.entity.User;
import com.eval.gameeval.service.IReviewerGroupService;
import com.eval.gameeval.util.OverviewCacheUtil;
import com.eval.gameeval.util.RedisKeyUtil;
import com.eval.gameeval.util.RedisToken;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
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
public class ReviewerGroupServiceImpl implements IReviewerGroupService {

    @Resource
    private ReviewerGroupMapper groupMapper;

    @Resource
    private ReviewerGroupMemberMapper memberMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private RedisToken redisToken;

    @Resource
    private ReviewerGroupMapper reviewerGroupMapper;

    @Resource
    private OverviewCacheUtil overviewCacheUtil;

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
            overviewCacheUtil.clearReviewerGroupOverviewCache();

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
    public ResponseVO<ReviewerGroupPageVO> getReviewerGroupList(String token, ReviewerGroupQueryDTO query) {
        try {
            // 1. 验证Token
            Long currentUserId = redisToken.getUserIdByToken(token);
            if (currentUserId == null) {
                return ResponseVO.unauthorized("Token无效");
            }

            // 2. 处理分页参数
            int pageNum = query != null && query.getPage() != null ? query.getPage() : 1;
            int pageSize = query != null && query.getSize() != null ? query.getSize() : 10;
            int offset = (pageNum - 1) * pageSize;

            // 3. 获取关键词
            String keyWords = query != null ? query.getKeyWords() : null;

            // 4. 查询分页数据
            List<ReviewerGroup> groups = groupMapper.selectPageWithSearch(offset, pageSize, keyWords);
            Long total = groupMapper.countWithSearch(keyWords);

            // 5. 转换为VO
            List<ReviewerGroupPageVO.ReviewerGroupVO> groupVOs = new ArrayList<>();
            for (ReviewerGroup group : groups) {
                ReviewerGroupPageVO.ReviewerGroupVO vo = new ReviewerGroupPageVO.ReviewerGroupVO();
                vo.setId(group.getId());
                vo.setName(group.getName());
                vo.setDescription(group.getDescription());
                vo.setCreatorId(group.getCreatorId());
                vo.setIsEnabled(group.getIsEnabled());
                vo.setCreateTime(group.getCreateTime());
                vo.setUpdateTime(group.getUpdateTime());

                // 查询成员列表
                List<Long> memberIds = memberMapper.selectUserIdsByGroupId(group.getId());
                vo.setMemberIds(memberIds);

                groupVOs.add(vo);
            }

            // 6. 构建分页响应
            ReviewerGroupPageVO pageVO = new ReviewerGroupPageVO();
            pageVO.setList(groupVOs);
            pageVO.setTotal(total);
            pageVO.setPage(pageNum);
            pageVO.setSize(pageSize);

            log.info("查询评审组列表成功: userId={}, total={}, page={}, size={}",
                    currentUserId, total, pageNum, pageSize);

            return ResponseVO.success("查询成功", pageVO);

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseVO<ReviewerGroupVO> updateReviewerGroup(String token, Long groupId, ReviewerGroupUpdateDTO request) {
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

            // 2. 查询评审组
            ReviewerGroup group = reviewerGroupMapper.selectById(groupId);
            if (group == null) {
                return ResponseVO.notFound("评审组不存在");
            }

            // 3. 权限校验：只有创建者或管理员可编辑
            boolean isAdmin = "super_admin".equals(currentUser.getRole()) || "admin".equals(currentUser.getRole());
            boolean isCreator = group.getCreatorId().equals(currentUserId);

            if (!isAdmin && !isCreator) {
                return ResponseVO.forbidden("无权编辑该评审组（仅创建者或管理员可编辑）");
            }

            // 4. 记录变更内容（用于日志）
            StringBuilder changeLog = new StringBuilder();
            boolean hasChanges = false;

            // 5. 更新基本信息（只更新非空字段）
            ReviewerGroup updateGroup = new ReviewerGroup();
            updateGroup.setId(groupId);
            updateGroup.setUpdateTime(LocalDateTime.now());

            if (request.getName() != null && !request.getName().trim().isEmpty()) {
                if (!request.getName().equals(group.getName())) {
                    changeLog.append("名称: ").append(group.getName()).append(" → ").append(request.getName()).append("; ");
                    hasChanges = true;
                }
                updateGroup.setName(request.getName());
            } else {
                updateGroup.setName(group.getName());
            }

            if (request.getDescription() != null) {
                if (!request.getDescription().equals(group.getDescription())) {
                    changeLog.append("描述: 已修改; ");
                    hasChanges = true;
                }
                updateGroup.setDescription(request.getDescription());
            } else {
                updateGroup.setDescription(group.getDescription());
            }

            if (request.getIsEnabled() != null) {
                if (!request.getIsEnabled().equals(group.getIsEnabled())) {
                    changeLog.append("启用状态: ").append(group.getIsEnabled() ? "启用" : "禁用")
                            .append(" → ").append(request.getIsEnabled() ? "启用" : "禁用").append("; ");
                    hasChanges = true;
                }
                updateGroup.setIsEnabled(request.getIsEnabled());
            } else {
                updateGroup.setIsEnabled(group.getIsEnabled());
            }

            // 6. 更新评审组基本信息
            int rows = reviewerGroupMapper.updateById(updateGroup);
            if (rows == 0) {
                return ResponseVO.error("编辑失败：评审组可能已被删除");
            }

            // 7. 更新成员列表（如果提供了memberIds）
            if (request.getMemberIds() != null) {
                hasChanges = true;

                // 记录成员变更
                List<Long> oldMemberIds = memberMapper.selectUserIdsByGroupId(groupId);
                changeLog.append("成员: ").append(oldMemberIds).append(" → ").append(request.getMemberIds()).append("; ");

                // 删除现有成员
                memberMapper.deleteByGroupId(groupId);

                // 验证并添加新成员
                List<ReviewerGroupMember> newMembers = new ArrayList<>();
                for (Long memberId : request.getMemberIds()) {
                    // 验证用户是否存在
                    User member = userMapper.selectById(memberId);
                    if (member == null) {
                        return ResponseVO.badRequest("用户ID " + memberId + " 不存在");
                    }

                    // 验证用户角色（必须是打分相关角色）
                    if (!"scorer".equals(member.getRole()) &&
                            !"admin".equals(member.getRole()) &&
                            !"super_admin".equals(member.getRole())) {
                        return ResponseVO.badRequest("用户 " + memberId + " (" + member.getName() + ") 不是打分用户，无法加入评审组");
                    }

                    // 创建成员关联
                    ReviewerGroupMember memberRel = new ReviewerGroupMember();
                    memberRel.setGroupId(groupId);
                    memberRel.setUserId(memberId);
                    memberRel.setCreateTime(LocalDateTime.now());
                    newMembers.add(memberRel);
                }

                // 批量插入新成员
                if (!newMembers.isEmpty()) {
                    memberMapper.insertBatch(newMembers);
                }
            }

            // 8. 构建响应
            ReviewerGroupVO responseVO = new ReviewerGroupVO();
            BeanUtils.copyProperties(updateGroup, responseVO);

            // 查询最新成员列表
            List<Long> currentMemberIds = memberMapper.selectUserIdsByGroupId(groupId);
            responseVO.setMemberIds(currentMemberIds);

            // 10. 记录操作日志
            String changes = hasChanges ? changeLog.toString() : "无变更";
            log.info("编辑评审组成功: groupId={}, operator={}, changes={}",
                    groupId, currentUserId, changes);

                overviewCacheUtil.clearReviewerGroupOverviewCache();

            return ResponseVO.success("编辑成功", responseVO);

        } catch (Exception e) {
            log.error("编辑评审组异常: groupId={}", groupId, e);
            return ResponseVO.error("编辑失败: " + e.getMessage());
        }
    }

    @Override
    public ResponseVO<ReviewerGroupOverviewVO> getReviewerGroupOverview(String token) {
        try {
            Long currentUserId = redisToken.getUserIdByToken(token);
            if (currentUserId == null) {
                return ResponseVO.unauthorized("Token无效");
            }

            User currentUser = userMapper.selectById(currentUserId);
            if (currentUser == null) {
                return ResponseVO.unauthorized("用户不存在");
            }

            boolean isAdmin = "super_admin".equals(currentUser.getRole()) || "admin".equals(currentUser.getRole());
            if (!isAdmin) {
                return ResponseVO.forbidden("权限不足，只有管理员可以查看评审组概览");
            }

            Object cache = overviewCacheUtil.getReviewerGroupOverviewCache();
            if (cache != null) {
                ReviewerGroupOverviewVO cachedOverview = (ReviewerGroupOverviewVO) cache;
                log.info("【缓存命中】查询评审组概览: totalGroups={}", cachedOverview.getTotalGroups());
                return ResponseVO.success("查询成功", cachedOverview);
            }

            ReviewerGroupOverviewVO overviewVO = loadReviewerGroupOverview();
            overviewCacheUtil.cacheReviewerGroupOverview(overviewVO);

            return ResponseVO.success("查询成功", overviewVO);

        } catch (Exception e) {
            log.error("查询评审组概览异常", e);
            return ResponseVO.error("查询失败: " + e.getMessage());
        }
    }

    public void warmupReviewerGroupOverviewCache() {
        try {
            if (overviewCacheUtil.getReviewerGroupOverviewCache() != null) {
                return;
            }

            ReviewerGroupOverviewVO overviewVO = loadReviewerGroupOverview();
            overviewCacheUtil.cacheReviewerGroupOverview(overviewVO);

            log.info("全局概览预热完成: key={}, totalGroups={}",
                    RedisKeyUtil.REVIEWER_GROUP_OVERVIEW_KEY, overviewVO.getTotalGroups());
        } catch (Exception e) {
            log.error("全局概览预热异常: key={}", RedisKeyUtil.REVIEWER_GROUP_OVERVIEW_KEY, e);
        }
    }

    private ReviewerGroupOverviewVO loadReviewerGroupOverview() {
        Map<String, Object> overviewMap = reviewerGroupMapper.selectReviewerGroupOverview();
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

        return new ReviewerGroupOverviewVO()
                .setTotalGroups(totalGroups)
                .setActiveGroups(toLong(overviewMap.get("activeGroups")))
                .setTotalMembers(totalMembers)
                .setAvgGroupSize(avgGroupSize);
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