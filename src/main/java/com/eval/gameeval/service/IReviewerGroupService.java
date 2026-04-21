package com.eval.gameeval.service;

import com.eval.gameeval.models.DTO.ReviewerGroup.ReviewerGroupCreateDTO;
import com.eval.gameeval.models.DTO.ReviewerGroup.ReviewerGroupQueryDTO;
import com.eval.gameeval.models.DTO.ReviewerGroup.ReviewerGroupUpdateDTO;
import com.eval.gameeval.models.VO.ResponseVO;
import com.eval.gameeval.models.VO.ReviewerGroupOverviewVO;
import com.eval.gameeval.models.VO.ReviewerGroupVO;
import com.eval.gameeval.models.VO.ReviewerGroupPageVO;

/**
 * 评审组服务接口
 */
public interface IReviewerGroupService {

    /**
     * 创建评审组
     */
    ResponseVO<ReviewerGroupVO> createReviewerGroup(String token, ReviewerGroupCreateDTO request);

    /**
     * 获取所有启用的评审组（支持分页）
     */
    ResponseVO<ReviewerGroupPageVO> getReviewerGroupList(String token, ReviewerGroupQueryDTO query);

    /**
     * 获取评审组详情
     */
    ResponseVO<ReviewerGroupVO> getReviewerGroupDetail(String token, Long groupId);

    /**
     * 编辑评审组
     * @param token 认证Token
     * @param groupId 评审组ID
     * @param request 更新请求
     * @return 更新后的评审组详情
     */
    ResponseVO<ReviewerGroupVO> updateReviewerGroup(String token, Long groupId, ReviewerGroupUpdateDTO request);

    ResponseVO<ReviewerGroupOverviewVO> getReviewerGroupOverview(String token);
}