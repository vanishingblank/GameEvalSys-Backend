package com.eval.gameeval.service;

import com.eval.gameeval.models.DTO.ScoringStandardCreateDTO;
import com.eval.gameeval.models.VO.ResponseVO;
import com.eval.gameeval.models.VO.ScoringStandardVO;

import java.util.List;

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
     * @return 打分标准列表
     */
    ResponseVO<List<ScoringStandardVO>> getStandardList(String token);

    /**
     * 获取单个打分标准详情
     * @param token 认证Token
     * @param standardId 标准ID
     * @return 打分标准详情
     */
    ResponseVO<ScoringStandardVO> getStandardDetail(String token, Long standardId);
}
