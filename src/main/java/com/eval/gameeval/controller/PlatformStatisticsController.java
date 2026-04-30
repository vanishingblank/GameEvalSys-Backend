package com.eval.gameeval.controller;

import com.eval.gameeval.models.VO.PlatformStatisticsVO;
import com.eval.gameeval.models.VO.ResponseVO;
import com.eval.gameeval.security.CurrentUserContext;
import com.eval.gameeval.service.IProjectStatisticsService;
import jakarta.annotation.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 平台统计控制器
 */
@RestController
@RequestMapping("/statistics")
public class PlatformStatisticsController {

    @Resource
    private CurrentUserContext currentUserContext;

    @Resource
    private IProjectStatisticsService projectStatisticsService;

    /**
     * 获取平台全局统计（仅管理员）
     */
    @GetMapping("/platform")
    public ResponseEntity<ResponseVO<PlatformStatisticsVO>> getPlatformStatistics() {
        Long currentUserId = currentUserContext.getCurrentUserId();
        ResponseVO<PlatformStatisticsVO> response = projectStatisticsService.getPlatformStatistics(currentUserId);
        return ResponseEntity.ok(response);
    }
}
