package com.eval.gameeval.controller;

import com.eval.gameeval.aspect.LogRecord;
import com.eval.gameeval.models.DTO.GroupAddToProjectDTO;
import com.eval.gameeval.models.DTO.GroupCreateDTO;
import com.eval.gameeval.models.DTO.GroupQueryDTO;
import com.eval.gameeval.models.DTO.GroupUpdateDTO;
import com.eval.gameeval.models.VO.GroupPageVO;
import com.eval.gameeval.models.VO.GroupVO;
import com.eval.gameeval.models.VO.ResponseVO;
import com.eval.gameeval.service.IGroupService;
import com.eval.gameeval.util.TokenUtil;
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
    private IGroupService groupService;

    /**
     * 创建小组（仅包含基本信息，不关联项目）
     */
    @PostMapping
    @LogRecord(value = "创建小组", module = "Group")
    public ResponseEntity<ResponseVO<GroupVO>> createGroup(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody GroupCreateDTO request) {

        String token = TokenUtil.extractToken(authorization);
        ResponseVO<GroupVO> response = groupService.createGroup(token, request);
        return ResponseEntity.ok(response);
    }

    /**
     * 将小组加入项目
     */
    @PostMapping("/{groupId}/add-to-project")
    @LogRecord(value = "将小组加入项目", module = "Group")
    public ResponseEntity<ResponseVO<GroupVO>> addGroupToProject(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody GroupAddToProjectDTO request) {

        String token = TokenUtil.extractToken(authorization);
        ResponseVO<GroupVO> response = groupService.addGroupToProject(token, request);
        return ResponseEntity.ok(response);
    }

    /**
     * 编辑小组信息
     */
    @PutMapping("/{groupId}")
    @LogRecord(value = "编辑小组", module = "Group")
    public ResponseEntity<ResponseVO<GroupVO>> updateGroup(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long groupId,
            @Valid @RequestBody GroupUpdateDTO request) {

        String token = TokenUtil.extractToken(authorization);
        // 确保groupId一致
        request.setId(groupId);
        ResponseVO<GroupVO> response = groupService.updateGroup(token, request);
        return ResponseEntity.ok(response);
    }

    /**
     * 查询所有小组（分页）
     */
    @GetMapping
    public ResponseEntity<ResponseVO<GroupPageVO>> getAllGroups(
            @RequestHeader("Authorization") String authorization,
            GroupQueryDTO query) {

        String token = TokenUtil.extractToken(authorization);
        ResponseVO<GroupPageVO> response = groupService.getAllGroups(token, query);
        return ResponseEntity.ok(response);
    }
}