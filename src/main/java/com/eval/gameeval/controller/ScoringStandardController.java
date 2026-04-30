package com.eval.gameeval.controller;

import com.eval.gameeval.aspect.LogRecord;
import com.eval.gameeval.models.DTO.Scoring.ScoringStandardCreateDTO;
import com.eval.gameeval.models.DTO.Scoring.ScoringStandardQueryDTO;
import com.eval.gameeval.models.DTO.Scoring.ScoringStandardUpdateDTO;
import com.eval.gameeval.models.VO.ResponseVO;
import com.eval.gameeval.models.VO.ScoringStandardOverviewVO;
import com.eval.gameeval.models.VO.ScoringStandardPageVO;
import com.eval.gameeval.models.VO.ScoringStandardVO;
import com.eval.gameeval.security.CurrentUserContext;
import com.eval.gameeval.service.IScoringStandardService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/scoring-standards")
public class ScoringStandardController {
    @Resource
    private CurrentUserContext currentUserContext;

    @Resource
    private IScoringStandardService scoringStandardService;

    /**
     * 创建打分标准
     */
    @PostMapping
    @LogRecord(value = "创建打分标准", module = "ScoringStandard")
    public ResponseEntity<ResponseVO<ScoringStandardVO>> createStandard(@Valid @RequestBody ScoringStandardCreateDTO request) {
        Long currentUserId = currentUserContext.getCurrentUserId();
        ResponseVO<ScoringStandardVO> response = scoringStandardService.createStandard(currentUserId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * 获取打分标准列表
     */
    @GetMapping
    public ResponseEntity<ResponseVO<ScoringStandardPageVO>> getStandardList(ScoringStandardQueryDTO query) {
        Long currentUserId = currentUserContext.getCurrentUserId();
        ResponseVO<ScoringStandardPageVO> response = scoringStandardService.getStandardList(currentUserId, query);
        return ResponseEntity.ok(response);
    }

    /**
     * 获取单个打分标准详情
     */
    @GetMapping("/{standardId}")
    public ResponseEntity<ResponseVO<ScoringStandardVO>> getStandardDetail(@PathVariable Long standardId) {
        Long currentUserId = currentUserContext.getCurrentUserId();
        ResponseVO<ScoringStandardVO> response = scoringStandardService.getStandardDetail(currentUserId, standardId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/overview")
    public ResponseEntity<ResponseVO<ScoringStandardOverviewVO>> getStandardOverview() {
        Long currentUserId = currentUserContext.getCurrentUserId();
        ResponseVO<ScoringStandardOverviewVO> response = scoringStandardService.getStandardOverview(currentUserId);
        return ResponseEntity.ok(response);
    }

    /**
     * 编辑打分标准
     */
    @PutMapping("/{standardId}")
    @LogRecord(value = "编辑打分标准", module = "ScoringStandard")
    public ResponseEntity<ResponseVO<Void>> updateStandard(
            @PathVariable Long standardId,
            @Valid @RequestBody ScoringStandardUpdateDTO request) {
        Long currentUserId = currentUserContext.getCurrentUserId();
        ResponseVO<Void> response = scoringStandardService.updateStandard(currentUserId, standardId, request);
        return ResponseEntity.ok(response);
    }
}
