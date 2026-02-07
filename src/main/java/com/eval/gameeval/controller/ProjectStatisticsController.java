package com.eval.gameeval.controller;

import com.eval.gameeval.models.VO.ProjectStatisticsVO;
import com.eval.gameeval.models.VO.ResponseVO;
import com.eval.gameeval.service.IProjectStatisticsService;
import com.eval.gameeval.util.TokenUtil;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

/**
 * 项目统计控制器
 */
@Slf4j
@RestController
@RequestMapping("/projects")
public class ProjectStatisticsController {

    @Resource
    private IProjectStatisticsService projectStatisticsService;

    /**
     * 获取项目打分统计
     */
    @GetMapping("/{projectId}/statistics")
    public ResponseEntity<ResponseVO<ProjectStatisticsVO>> getProjectStatistics(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long projectId) {

        String token = TokenUtil.extractToken(authorization);
        ResponseVO<ProjectStatisticsVO> response = projectStatisticsService.getProjectStatistics(token, projectId);
        return ResponseEntity.ok(response);
    }

    /**
     * 导出项目打分数据
     */
    @GetMapping("/{projectId}/export")
    public void exportProjectData(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long projectId,
            @RequestParam(required = false, defaultValue = "excel") String format,
            HttpServletResponse response) throws IOException {

        String token = TokenUtil.extractToken(authorization);
        projectStatisticsService.exportProjectData(token, projectId, format, response);
    }


}