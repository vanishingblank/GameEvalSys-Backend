package com.eval.gameeval.service;

import com.eval.gameeval.models.DTO.ScoringRecordCreateDTO;
import com.eval.gameeval.models.DTO.ScoringRecordQueryDTO;
import com.eval.gameeval.models.VO.ResponseVO;
import com.eval.gameeval.models.VO.ScoringRecordVO;

/**
 * 打分记录服务接口
 */
public interface IScoringRecordService {

    /**
     * 提交/修改打分
     * @param token 认证Token
     * @param request 打分请求
     * @return 打分记录详情
     */
    ResponseVO<ScoringRecordVO> submitScore(String token, ScoringRecordCreateDTO request);

    /**
     * 获取用户对指定小组的打分记录
     * @param token 认证Token
     * @param query 查询参数
     * @return 打分记录
     */
    ResponseVO<ScoringRecordVO> getScoreRecord(String token, ScoringRecordQueryDTO query);
}