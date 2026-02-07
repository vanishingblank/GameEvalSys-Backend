package com.eval.gameeval.service;

import com.eval.gameeval.models.VO.ProjectStatisticsVO;
import com.eval.gameeval.models.VO.ResponseVO;

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
     * 导出项目打分数据
     * @param token 认证Token
     * @param projectId 项目ID
     * @param format 导出格式（excel/csv）
     * @param response HTTP响应
     * @throws IOException IO异常
     */
    void exportProjectData(String token, Long projectId, String format, HttpServletResponse response) throws IOException;
}