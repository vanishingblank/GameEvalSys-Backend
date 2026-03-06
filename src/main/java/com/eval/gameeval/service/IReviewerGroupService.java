package com.eval.gameeval.service;

import com.eval.gameeval.models.DTO.ReviewerGroupCreateDTO;
import com.eval.gameeval.models.DTO.ReviewerGroupQueryDTO;
import com.eval.gameeval.models.VO.ResponseVO;
import com.eval.gameeval.models.VO.ReviewerGroupVO;

import java.util.List;

/**
 * 评审组服务接口
 */
public interface IReviewerGroupService {

    /**
     * 创建评审组
     */
    ResponseVO<ReviewerGroupVO> createReviewerGroup(String token, ReviewerGroupCreateDTO request);

    /**
     * 获取所有启用的评审组
     */
    ResponseVO<List<ReviewerGroupVO>> getReviewerGroupList(String token, ReviewerGroupQueryDTO query);

    /**
     * 获取评审组详情
     */
    ResponseVO<ReviewerGroupVO> getReviewerGroupDetail(String token, Long groupId);
}