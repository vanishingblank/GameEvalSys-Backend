package com.eval.gameeval.controller;

import com.eval.gameeval.models.DTO.Scoring.ScoringRecordCreateDTO;
import com.eval.gameeval.models.DTO.Scoring.ScoringRecordQueryDTO;
import com.eval.gameeval.models.VO.ResponseVO;
import com.eval.gameeval.models.VO.ScoringRecordVO;
import com.eval.gameeval.security.CurrentUserContext;
import com.eval.gameeval.service.IScoringRecordService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 打分记录控制器
 */
@Slf4j
@RestController
@RequestMapping("/scoring/records")
public class ScoringRecordController {
    @Resource
    private CurrentUserContext currentUserContext;

    @Resource
    private IScoringRecordService scoringRecordService;

    /**
     * 提交/修改打分
     */
    @PostMapping
    public ResponseEntity<ResponseVO<ScoringRecordVO>> submitScore(@Valid @RequestBody ScoringRecordCreateDTO request) {
        Long currentUserId = currentUserContext.getCurrentUserId();
        ResponseVO<ScoringRecordVO> response = scoringRecordService.submitScore(currentUserId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * 获取用户对指定小组的打分记录
     */
    @GetMapping
    public ResponseEntity<ResponseVO<ScoringRecordVO>> getScoreRecord(ScoringRecordQueryDTO query) {
        Long currentUserId = currentUserContext.getCurrentUserId();
        ResponseVO<ScoringRecordVO> response = scoringRecordService.getScoreRecord(currentUserId, query);
        return ResponseEntity.ok(response);
    }

}
