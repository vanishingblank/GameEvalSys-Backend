package com.eval.gameeval.service;

import com.eval.gameeval.models.VO.GroupIndicatorStatisticsVO;
import com.eval.gameeval.models.VO.PlatformStatisticsVO;
import com.eval.gameeval.models.VO.ProjectStatisticsVO;
import com.eval.gameeval.models.VO.ResponseVO;
import com.eval.gameeval.models.VO.ScoringOverviewVO;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 项目统计服务接口
 */
public interface IProjectStatisticsService {

    /**
     * 获取项目打分统计
     * @param token 认证Token
     * @param projectId 项目ID
     * @return 统计数据
     */
    ResponseVO<ProjectStatisticsVO> getProjectStatistics(String token, Long projectId);

    /**
     * 获取用户打分概览
     * @param token 认证Token
     * @return 概览统计
     */
    ResponseVO<ScoringOverviewVO> getScoringOverview(String token);

    /**
     * 获取平台全局统计（管理员）
     * @param token 认证Token
     * @return 平台统计
     */
    ResponseVO<PlatformStatisticsVO> getPlatformStatistics(String token);

    /**
     * 获取项目内指定小组的指标平均得分明细
     * @param token 认证Token
     * @param projectId 项目ID
     * @param groupId 小组ID
     * @return 小组指标平均得分明细
     */
    ResponseVO<GroupIndicatorStatisticsVO> getGroupIndicatorStatistics(String token, Long projectId, Long groupId);

    /**
     * 导出项目打分数据
     * @param token 认证Token
     * @param projectId 项目ID
     * @param format 导出格式（excel/csv）
     * @param response HTTP响应
     * @throws IOException IO异常
     */
    void exportProjectData(String token, Long projectId, String format, HttpServletResponse response) throws IOException;

    /**
     * 导出项目内各小组评分汇总（按小组一行，包含指标平均分与分类总分平均分）
     * @param token 认证Token
     * @param projectId 项目ID
     * @param format 导出格式（excel/csv）
     * @param response HTTP响应
     * @throws IOException IO异常
     */
    void exportProjectGroupIndicatorItemScores(String token, Long projectId, String format, HttpServletResponse response) throws IOException;

    /**
     * 导出项目内被判定为异常的打分记录（方案B）
     * @param token 认证Token
     * @param projectId 项目ID
     * @param response HTTP响应
     * @throws IOException IO异常
     */
    void exportAbnormalScoringRecords(String token, Long projectId, HttpServletResponse response) throws IOException;
}
