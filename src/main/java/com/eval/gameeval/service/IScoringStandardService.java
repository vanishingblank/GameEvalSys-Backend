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
     * @param token 认证Token
     * @param request 创建请求
     * @return 打分标准详情
     */
    ResponseVO<ScoringStandardVO> createStandard(String token, ScoringStandardCreateDTO request);

    /**
     * 获取打分标准列表
     * @param token 认证Token
     * @param query 查询参数
     * @return 打分标准列表
     */
    ResponseVO<ScoringStandardPageVO> getStandardList(String token, ScoringStandardQueryDTO query);

    /**
     * 获取单个打分标准详情
     * @param token 认证Token
     * @param standardId 标准ID
     * @return 打分标准详情
     */
    ResponseVO<ScoringStandardVO> getStandardDetail(String token, Long standardId);

    ResponseVO<ScoringStandardOverviewVO> getStandardOverview(String token);

    /**
     * 编辑打分标准
     * @param token 认证Token
     * @param standardId 标准ID
     * @param request 编辑请求
     * @return 编辑结果
     */
    ResponseVO<Void> updateStandard(String token, Long standardId, ScoringStandardUpdateDTO request);
}
