package com.eval.gameeval.controller;

import com.eval.gameeval.models.VO.GroupIndicatorStatisticsVO;
import com.eval.gameeval.models.VO.ProjectStatisticsVO;
import com.eval.gameeval.models.VO.ResponseVO;
import com.eval.gameeval.security.CurrentUserContext;
import com.eval.gameeval.service.IProjectStatisticsService;
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
    private CurrentUserContext currentUserContext;

    @Resource
    private IProjectStatisticsService projectStatisticsService;

    /**
     * 获取项目打分统计
     */
    @GetMapping("/{projectId}/statistics")
    public ResponseEntity<ResponseVO<ProjectStatisticsVO>> getProjectStatistics(
            @PathVariable Long projectId) {

        Long currentUserId = currentUserContext.getCurrentUserId();
        ResponseVO<ProjectStatisticsVO> response = projectStatisticsService.getProjectStatistics(currentUserId, projectId);
        return ResponseEntity.ok(response);
    }

    /**
     * 获取项目内指定小组的指标平均得分明细
     */
    @GetMapping("/{projectId}/statistics/groups/{groupId}")
    public ResponseEntity<ResponseVO<GroupIndicatorStatisticsVO>> getGroupIndicatorStatistics(
            @PathVariable Long projectId,
            @PathVariable Long groupId) {

        Long currentUserId = currentUserContext.getCurrentUserId();
        ResponseVO<GroupIndicatorStatisticsVO> response =
            projectStatisticsService.getGroupIndicatorStatistics(currentUserId, projectId, groupId);
        return ResponseEntity.ok(response);
    }

    /**
     * 导出项目打分数据
     */
    @GetMapping("/{projectId}/export")
    public void exportProjectData(
            @PathVariable Long projectId,
            @RequestParam(required = false, defaultValue = "excel") String format,
            HttpServletResponse response) throws IOException {

        Long currentUserId = currentUserContext.getCurrentUserId();
        projectStatisticsService.exportProjectData(currentUserId, projectId, format, response);
    }

    /**
     * 导出项目内各小组评分汇总（指标平均分 + 分类总分平均分）
     */
    @GetMapping("/{projectId}/export/group-indicator-items")
    public void exportProjectGroupIndicatorItemScores(
            @PathVariable Long projectId,
            @RequestParam(required = false, defaultValue = "excel") String format,
            HttpServletResponse response) throws IOException {

        Long currentUserId = currentUserContext.getCurrentUserId();
        projectStatisticsService.exportProjectGroupIndicatorItemScores(currentUserId, projectId, format, response);
    }

    /**
     * 导出项目内被判定为异常的打分记录（方案B）
     */
    @GetMapping("/{projectId}/export/abnormal-scores")
    public void exportAbnormalScoringRecords(
            @PathVariable Long projectId,
            HttpServletResponse response) throws IOException {

        Long currentUserId = currentUserContext.getCurrentUserId();
        projectStatisticsService.exportAbnormalScoringRecords(currentUserId, projectId, response);
    }


}
