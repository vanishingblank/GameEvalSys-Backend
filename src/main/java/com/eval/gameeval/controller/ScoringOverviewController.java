package com.eval.gameeval.controller;

import com.eval.gameeval.models.VO.ResponseVO;
import com.eval.gameeval.models.VO.ScoringOverviewVO;
import com.eval.gameeval.service.IProjectStatisticsService;
import com.eval.gameeval.util.TokenUtil;
import jakarta.annotation.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/scoring")
public class ScoringOverviewController {

    @Resource
    private IProjectStatisticsService projectStatisticsService;

    @GetMapping("/overview")
    public ResponseEntity<ResponseVO<ScoringOverviewVO>> getScoringOverview(
            @RequestHeader("Authorization") String authorization) {
        String token = TokenUtil.extractToken(authorization);
        ResponseVO<ScoringOverviewVO> response = projectStatisticsService.getScoringOverview(token);
        return ResponseEntity.ok(response);
    }
}
