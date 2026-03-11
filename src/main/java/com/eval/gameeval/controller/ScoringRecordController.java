package com.eval.gameeval.controller;

import com.eval.gameeval.models.DTO.ScoringRecordCreateDTO;
import com.eval.gameeval.models.DTO.ScoringRecordQueryDTO;
import com.eval.gameeval.models.VO.ResponseVO;
import com.eval.gameeval.models.VO.ScoringRecordVO;
import com.eval.gameeval.service.IScoringRecordService;
import com.eval.gameeval.util.TokenUtil;
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
    private IScoringRecordService scoringRecordService;

    /**
     * 提交/修改打分
     */
    @PostMapping
    public ResponseEntity<ResponseVO<ScoringRecordVO>> submitScore(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody ScoringRecordCreateDTO request) {

        String token = TokenUtil.extractToken(authorization);
        ResponseVO<ScoringRecordVO> response = scoringRecordService.submitScore(token, request);
        return ResponseEntity.ok(response);
    }

    /**
     * 获取用户对指定小组的打分记录
     */
    @GetMapping
    public ResponseEntity<ResponseVO<ScoringRecordVO>> getScoreRecord(
            @RequestHeader("Authorization") String authorization,
            ScoringRecordQueryDTO query) {

        String token = TokenUtil.extractToken(authorization);
        ResponseVO<ScoringRecordVO> response = scoringRecordService.getScoreRecord(token, query);
        return ResponseEntity.ok(response);
    }

}