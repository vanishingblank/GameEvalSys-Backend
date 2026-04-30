package com.eval.gameeval.controller;

import com.eval.gameeval.aspect.LogRecord;
import com.eval.gameeval.models.DTO.Group.GroupAddToProjectDTO;
import com.eval.gameeval.models.DTO.Group.GroupBatchCreateDTO;
import com.eval.gameeval.models.DTO.Group.GroupCreateDTO;
import com.eval.gameeval.models.DTO.Group.GroupQueryDTO;
import com.eval.gameeval.models.DTO.Group.GroupUpdateDTO;
import com.eval.gameeval.models.VO.GroupBatchCreateVO;
import com.eval.gameeval.models.VO.GroupOverviewVO;
import com.eval.gameeval.models.VO.GroupPageVO;
import com.eval.gameeval.models.VO.GroupVO;
import com.eval.gameeval.models.VO.ResponseVO;
import com.eval.gameeval.security.CurrentUserContext;
import com.eval.gameeval.service.IGroupService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 小组管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/groups")
public class GroupController {
    @Resource
    private CurrentUserContext currentUserContext;

    @Resource
    private IGroupService groupService;

    /**
     * 创建小组（仅包含基本信息，不关联项目）
     */
    @PostMapping
    @LogRecord(value = "创建小组", module = "Group")
    public ResponseEntity<ResponseVO<GroupVO>> createGroup(@Valid @RequestBody GroupCreateDTO request) {
        Long currentUserId = currentUserContext.getCurrentUserId();
        ResponseVO<GroupVO> response = groupService.createGroup(currentUserId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * 批量创建小组（仅包含基本信息，不关联项目）
     */
    @PostMapping("/batch-create")
    @LogRecord(value = "批量创建小组", module = "Group")
    public ResponseEntity<ResponseVO<GroupBatchCreateVO>> batchCreateGroups(@Valid @RequestBody GroupBatchCreateDTO request) {
        Long currentUserId = currentUserContext.getCurrentUserId();
        ResponseVO<GroupBatchCreateVO> response = groupService.batchCreateGroups(currentUserId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * 将小组加入项目
     */
    @PostMapping("/{groupId}/add-to-project")
    @LogRecord(value = "将小组加入项目", module = "Group")
    public ResponseEntity<ResponseVO<GroupVO>> addGroupToProject(@Valid @RequestBody GroupAddToProjectDTO request) {
        Long currentUserId = currentUserContext.getCurrentUserId();
        ResponseVO<GroupVO> response = groupService.addGroupToProject(currentUserId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * 编辑小组信息
     */
    @PutMapping("/{groupId}")
    @LogRecord(value = "编辑小组", module = "Group")
    public ResponseEntity<ResponseVO<GroupVO>> updateGroup(
            @PathVariable Long groupId,
            @Valid @RequestBody GroupUpdateDTO request) {

        // 确保groupId一致
        request.setId(groupId);
        Long currentUserId = currentUserContext.getCurrentUserId();
        ResponseVO<GroupVO> response = groupService.updateGroup(currentUserId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * 查询所有小组（分页）
     */
    @GetMapping
    public ResponseEntity<ResponseVO<GroupPageVO>> getAllGroups(GroupQueryDTO query) {
        Long currentUserId = currentUserContext.getCurrentUserId();
        ResponseVO<GroupPageVO> response = groupService.getAllGroups(currentUserId, query);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/overview")
    public ResponseEntity<ResponseVO<GroupOverviewVO>> getGroupOverview() {
        Long currentUserId = currentUserContext.getCurrentUserId();
        ResponseVO<GroupOverviewVO> response = groupService.getGroupOverview(currentUserId);
        return ResponseEntity.ok(response);
    }
}
