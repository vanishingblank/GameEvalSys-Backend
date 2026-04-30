package com.eval.gameeval.controller;

import com.eval.gameeval.models.VO.ResponseVO;
import com.eval.gameeval.models.VO.ScoringOverviewVO;
import com.eval.gameeval.security.CurrentUserContext;
import com.eval.gameeval.service.IProjectStatisticsService;
import jakarta.annotation.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/scoring")
public class ScoringOverviewController {

    @Resource
    private CurrentUserContext currentUserContext;

    @Resource
    private IProjectStatisticsService projectStatisticsService;

    @GetMapping("/overview")
    public ResponseEntity<ResponseVO<ScoringOverviewVO>> getScoringOverview() {
        Long currentUserId = currentUserContext.getCurrentUserId();
        ResponseVO<ScoringOverviewVO> response = projectStatisticsService.getScoringOverview(currentUserId);
        return ResponseEntity.ok(response);
    }
}
