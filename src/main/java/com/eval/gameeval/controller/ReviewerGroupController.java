package com.eval.gameeval.controller;

import com.eval.gameeval.aspect.LogRecord;
import com.eval.gameeval.models.DTO.ReviewerGroupCreateDTO;
import com.eval.gameeval.models.DTO.ReviewerGroupQueryDTO;
import com.eval.gameeval.models.DTO.ReviewerGroupUpdateDTO;
import com.eval.gameeval.models.VO.ResponseVO;
import com.eval.gameeval.models.VO.ReviewerGroupVO;
import com.eval.gameeval.service.IReviewerGroupService;
import com.eval.gameeval.util.TokenUtil;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 评审组控制器
 */
@Slf4j
@RestController
@RequestMapping("/reviewer-groups")
public class ReviewerGroupController {

    @Resource
    private IReviewerGroupService reviewerGroupService;

    /**
     * 创建评审组
     */
    @PostMapping
    @LogRecord(value = "创建评审组", module = "ReviewerGroup")
    public ResponseEntity<ResponseVO<ReviewerGroupVO>> createReviewerGroup(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody ReviewerGroupCreateDTO request) {

        String token = TokenUtil.extractToken(authorization);
        ResponseVO<ReviewerGroupVO> response = reviewerGroupService.createReviewerGroup(token, request);
        return ResponseEntity.ok(response);
    }

    /**
     * 获取评审组列表
     */
    @GetMapping
    public ResponseEntity<ResponseVO<List<ReviewerGroupVO>>> getReviewerGroupList(
            @RequestHeader("Authorization") String authorization,
            ReviewerGroupQueryDTO query
    ) {
        String token = TokenUtil.extractToken(authorization);
        ResponseVO<List<ReviewerGroupVO>> response = reviewerGroupService.getReviewerGroupList(token,query);
        return ResponseEntity.ok(response);
    }

    /**
     * 获取评审组详情
     */
    @GetMapping("/{groupId}")
    public ResponseEntity<ResponseVO<ReviewerGroupVO>> getReviewerGroupDetail(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long groupId) {

        String token = TokenUtil.extractToken(authorization);
        ResponseVO<ReviewerGroupVO> response = reviewerGroupService.getReviewerGroupDetail(token, groupId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{groupId}")
    public ResponseEntity<ResponseVO<ReviewerGroupVO>> updateReviewerGroup(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long groupId,
            @Valid @RequestBody ReviewerGroupUpdateDTO request) {

        String token = TokenUtil.extractToken(authorization);
        ResponseVO<ReviewerGroupVO> response = reviewerGroupService.updateReviewerGroup(token, groupId, request);
        return ResponseEntity.ok(response);
    }


}