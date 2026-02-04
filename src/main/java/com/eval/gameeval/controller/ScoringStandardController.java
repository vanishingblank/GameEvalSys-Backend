package com.eval.gameeval.controller;

import com.eval.gameeval.models.DTO.ScoringStandardCreateDTO;
import com.eval.gameeval.models.VO.ResponseVO;
import com.eval.gameeval.models.VO.ScoringStandardVO;
import com.eval.gameeval.service.IScoringStandardService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/scoring-standards")
public class ScoringStandardController {
    @Resource
    private IScoringStandardService scoringStandardService;

    /**
     * 创建打分标准
     */
    @PostMapping
    public ResponseEntity<ResponseVO<ScoringStandardVO>> createStandard(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody ScoringStandardCreateDTO request) {

        String token = extractToken(authorization);
        ResponseVO<ScoringStandardVO> response = scoringStandardService.createStandard(token, request);
        return ResponseEntity.ok(response);
    }

    /**
     * 获取打分标准列表
     */
    @GetMapping
    public ResponseEntity<ResponseVO<List<ScoringStandardVO>>> getStandardList(
            @RequestHeader("Authorization") String authorization) {

        String token = extractToken(authorization);
        ResponseVO<List<ScoringStandardVO>> response = scoringStandardService.getStandardList(token);
        return ResponseEntity.ok(response);
    }

    /**
     * 获取单个打分标准详情
     */
    @GetMapping("/{standardId}")
    public ResponseEntity<ResponseVO<ScoringStandardVO>> getStandardDetail(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long standardId) {

        String token = extractToken(authorization);
        ResponseVO<ScoringStandardVO> response = scoringStandardService.getStandardDetail(token, standardId);
        return ResponseEntity.ok(response);
    }

    private String extractToken(String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        return authorization;
    }
}
