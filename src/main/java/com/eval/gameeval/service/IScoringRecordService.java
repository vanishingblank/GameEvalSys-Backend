package com.eval.gameeval.service;

import com.eval.gameeval.models.DTO.Scoring.ScoringRecordCreateDTO;
import com.eval.gameeval.models.DTO.Scoring.ScoringRecordPageQueryDTO;
import com.eval.gameeval.models.DTO.Scoring.ScoringRecordQueryDTO;
import com.eval.gameeval.models.VO.ResponseVO;
import com.eval.gameeval.models.VO.ScoringRecordPageVO;
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

    /**
     * 获取当前用户在项目内所有小组的打分记录（分页）
     * @param token 认证Token
     * @param projectId 项目ID
     * @param query 分页参数
     * @return 打分页列表
     */
    ResponseVO<ScoringRecordPageVO> getUserProjectRecords(String token, Long projectId, ScoringRecordPageQueryDTO query);
}
