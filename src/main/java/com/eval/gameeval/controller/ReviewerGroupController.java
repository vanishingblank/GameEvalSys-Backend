package com.eval.gameeval.controller;

import com.eval.gameeval.aspect.LogRecord;
import com.eval.gameeval.models.DTO.ReviewerGroup.ReviewerGroupCreateDTO;
import com.eval.gameeval.models.DTO.ReviewerGroup.ReviewerGroupQueryDTO;
import com.eval.gameeval.models.DTO.ReviewerGroup.ReviewerGroupUpdateDTO;
import com.eval.gameeval.models.VO.ResponseVO;
import com.eval.gameeval.models.VO.ReviewerGroupOverviewVO;
import com.eval.gameeval.models.VO.ReviewerGroupVO;
import com.eval.gameeval.models.VO.ReviewerGroupPageVO;
import com.eval.gameeval.security.CurrentUserContext;
import com.eval.gameeval.service.IReviewerGroupService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 评审组控制器
 */
@Slf4j
@RestController
@RequestMapping("/reviewer-groups")
public class ReviewerGroupController {
    @Resource
    private CurrentUserContext currentUserContext;

    @Resource
    private IReviewerGroupService reviewerGroupService;

    /**
     * 创建评审组
     */
    @PostMapping
    @LogRecord(value = "创建评审组", module = "ReviewerGroup")
    public ResponseEntity<ResponseVO<ReviewerGroupVO>> createReviewerGroup(@Valid @RequestBody ReviewerGroupCreateDTO request) {
        Long currentUserId = currentUserContext.getCurrentUserId();
        ResponseVO<ReviewerGroupVO> response = reviewerGroupService.createReviewerGroup(currentUserId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * 获取评审组列表
     */
    @GetMapping
    public ResponseEntity<ResponseVO<ReviewerGroupPageVO>> getReviewerGroupList(ReviewerGroupQueryDTO query) {
        Long currentUserId = currentUserContext.getCurrentUserId();
        ResponseVO<ReviewerGroupPageVO> response = reviewerGroupService.getReviewerGroupList(currentUserId, query);
        return ResponseEntity.ok(response);
    }

    /**
     * 获取评审组详情
     */
    @GetMapping("/{groupId}")
    public ResponseEntity<ResponseVO<ReviewerGroupVO>> getReviewerGroupDetail(@PathVariable Long groupId) {
        Long currentUserId = currentUserContext.getCurrentUserId();
        ResponseVO<ReviewerGroupVO> response = reviewerGroupService.getReviewerGroupDetail(currentUserId, groupId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{groupId}")
    public ResponseEntity<ResponseVO<ReviewerGroupVO>> updateReviewerGroup(
            @PathVariable Long groupId,
            @Valid @RequestBody ReviewerGroupUpdateDTO request) {
        Long currentUserId = currentUserContext.getCurrentUserId();
        ResponseVO<ReviewerGroupVO> response = reviewerGroupService.updateReviewerGroup(currentUserId, groupId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/overview")
    public ResponseEntity<ResponseVO<ReviewerGroupOverviewVO>> getReviewerGroupOverview() {
        Long currentUserId = currentUserContext.getCurrentUserId();
        ResponseVO<ReviewerGroupOverviewVO> response = reviewerGroupService.getReviewerGroupOverview(currentUserId);
        return ResponseEntity.ok(response);
    }


}
