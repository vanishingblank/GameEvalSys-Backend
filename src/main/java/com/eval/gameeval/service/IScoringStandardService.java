package com.eval.gameeval.service;

import com.eval.gameeval.models.DTO.Scoring.ScoringStandardCreateDTO;
import com.eval.gameeval.models.DTO.Scoring.ScoringStandardQueryDTO;
import com.eval.gameeval.models.DTO.Scoring.ScoringStandardUpdateDTO;
import com.eval.gameeval.models.VO.ResponseVO;
import com.eval.gameeval.models.VO.ScoringStandardOverviewVO;
import com.eval.gameeval.models.VO.ScoringStandardPageVO;
import com.eval.gameeval.models.VO.ScoringStandardVO;

public interface IScoringStandardService {
    /**
     * 创建打分标准
     * @param currentUserId 当前登录用户ID
     * @param request 创建请求
     * @return 打分标准详情
     */
    ResponseVO<ScoringStandardVO> createStandard(Long currentUserId, ScoringStandardCreateDTO request);

    /**
     * 获取打分标准列表
     * @param currentUserId 当前登录用户ID
     * @param query 查询参数
     * @return 打分标准列表
     */
    ResponseVO<ScoringStandardPageVO> getStandardList(Long currentUserId, ScoringStandardQueryDTO query);

    /**
     * 获取单个打分标准详情
     * @param currentUserId 当前登录用户ID
     * @param standardId 标准ID
     * @return 打分标准详情
     */
    ResponseVO<ScoringStandardVO> getStandardDetail(Long currentUserId, Long standardId);

    ResponseVO<ScoringStandardOverviewVO> getStandardOverview(Long currentUserId);

    /**
     * 编辑打分标准
     * @param currentUserId 当前登录用户ID
     * @param standardId 标准ID
     * @param request 编辑请求
     * @return 编辑结果
     */
    ResponseVO<Void> updateStandard(Long currentUserId, Long standardId, ScoringStandardUpdateDTO request);
}
