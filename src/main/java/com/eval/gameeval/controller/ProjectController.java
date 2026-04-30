package com.eval.gameeval.controller;

import com.eval.gameeval.aspect.LogRecord;
import com.eval.gameeval.models.DTO.Project.ProjectCreateDTO;
import com.eval.gameeval.models.DTO.Project.ProjectQueryDTO;
import com.eval.gameeval.models.DTO.Project.ProjectUpdateDTO;
import com.eval.gameeval.models.DTO.Scoring.ScoringRecordPageQueryDTO;
import com.eval.gameeval.models.VO.*;
import com.eval.gameeval.security.CurrentUserContext;
import com.eval.gameeval.service.IGroupService;
import com.eval.gameeval.service.IProjectService;
import com.eval.gameeval.service.IScoringRecordService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/projects")
public class ProjectController {
    @Resource
    private CurrentUserContext currentUserContext;

    @Resource
    private IProjectService projectService;

    @Resource
    private IGroupService groupService;

    @Resource
    private IScoringRecordService scoringRecordService;

    /**
     * 创建项目
     */
    @PostMapping
    @LogRecord(value = "创建项目", module = "Project")
    public ResponseEntity<ResponseVO<ProjectCreateVO>> createProject(
            @Valid @RequestBody ProjectCreateDTO request) {
        Long currentUserId = currentUserContext.getCurrentUserId();
        ResponseVO<ProjectCreateVO> response = projectService.createProject(currentUserId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * 编辑项目
     */
    @PutMapping("/{projectId}")
    @LogRecord(value = "编辑项目", module = "Project")
    public ResponseEntity<ResponseVO<Void>> updateProject(
            @PathVariable Long projectId,
            @Valid @RequestBody ProjectUpdateDTO request) {
        Long currentUserId = currentUserContext.getCurrentUserId();
        ResponseVO<Void> response = projectService.updateProject(currentUserId, projectId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * 结束项目
     */
    @PostMapping("/{projectId}/end")
    @LogRecord(value = "结束项目", module = "Project")
    public ResponseEntity<ResponseVO<Void>> endProject(
            @PathVariable Long projectId) {
        Long currentUserId = currentUserContext.getCurrentUserId();
        ResponseVO<Void> response = projectService.endProject(currentUserId, projectId);
        return ResponseEntity.ok(response);
    }

    /**
     * 获取项目列表
     */
    @GetMapping
    public ResponseEntity<ResponseVO<ProjectPageVO>> getProjectList(
            ProjectQueryDTO query) {
        Long currentUserId = currentUserContext.getCurrentUserId();
        ResponseVO<ProjectPageVO> response = projectService.getProjectList(currentUserId, query);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/overview")
    public ResponseEntity<ResponseVO<ProjectOverviewVO>> getProjectOverview() {
        Long currentUserId = currentUserContext.getCurrentUserId();
        ResponseVO<ProjectOverviewVO> response = projectService.getProjectOverview(currentUserId);
        return ResponseEntity.ok(response);
    }

    /**
     * 获取单个项目详情
     */
    @GetMapping("/{projectId}")
    public ResponseEntity<ResponseVO<ProjectVO>> getProjectDetail(
            @PathVariable Long projectId) {
        Long currentUserId = currentUserContext.getCurrentUserId();
        ResponseVO<ProjectVO> response = projectService.getProjectDetail(currentUserId, projectId);
        return ResponseEntity.ok(response);
    }

    /**
     * 获取当前用户授权的项目列表
     */
    @GetMapping("/authorized")
    public ResponseEntity<ResponseVO<ProjectPageVO>> getAuthorizedProjects(
            ProjectQueryDTO query) {
        Long currentUserId = currentUserContext.getCurrentUserId();
        ResponseVO<ProjectPageVO> response = projectService.getAuthorizedProjects(currentUserId, query);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{projectId}/groups")
    public ResponseEntity<ResponseVO<List<GroupVO>>> getProjectGroups(
            @PathVariable Long projectId) {
        Long currentUserId = currentUserContext.getCurrentUserId();
        ResponseVO<List<GroupVO>> response = groupService.getProjectGroups(currentUserId, projectId);
        return ResponseEntity.ok(response);
    }

    /**
     * 获取当前用户在项目内所有小组的打分记录（分页）
     */
    @GetMapping("/{projectId}/records")
    public ResponseEntity<ResponseVO<ScoringRecordPageVO>> getUserProjectRecords(
            @PathVariable Long projectId,
            @Valid ScoringRecordPageQueryDTO query) {
        Long currentUserId = currentUserContext.getCurrentUserId();
        ResponseVO<ScoringRecordPageVO> response = scoringRecordService.getUserProjectRecords(currentUserId, projectId, query);
        return ResponseEntity.ok(response);
    }

}
