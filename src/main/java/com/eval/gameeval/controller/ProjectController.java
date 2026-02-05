package com.eval.gameeval.controller;

import com.eval.gameeval.models.DTO.ProjectCreateDTO;
import com.eval.gameeval.models.DTO.ProjectQueryDTO;
import com.eval.gameeval.models.DTO.ProjectUpdateDTO;
import com.eval.gameeval.models.VO.ProjectPageVO;
import com.eval.gameeval.models.VO.ProjectVO;
import com.eval.gameeval.models.VO.ResponseVO;
import com.eval.gameeval.service.IProjectService;
import com.eval.gameeval.util.TokenUtil;
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
    private IProjectService projectService;

    /**
     * 创建项目
     */
    @PostMapping
    public ResponseEntity<ResponseVO<ProjectVO>> createProject(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody ProjectCreateDTO request) {

        String token = TokenUtil.extractToken(authorization);
        ResponseVO<ProjectVO> response = projectService.createProject(token, request);
        return ResponseEntity.ok(response);
    }

    /**
     * 编辑项目
     */
    @PutMapping("/{projectId}")
    public ResponseEntity<ResponseVO<Void>> updateProject(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long projectId,
            @Valid @RequestBody ProjectUpdateDTO request) {

        String token = TokenUtil.extractToken(authorization);
        ResponseVO<Void> response = projectService.updateProject(token, projectId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * 结束项目
     */
    @PostMapping("/{projectId}/end")
    public ResponseEntity<ResponseVO<Void>> endProject(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long projectId) {

        String token = TokenUtil.extractToken(authorization);
        ResponseVO<Void> response = projectService.endProject(token, projectId);
        return ResponseEntity.ok(response);
    }

    /**
     * 获取项目列表
     */
    @GetMapping
    public ResponseEntity<ResponseVO<ProjectPageVO>> getProjectList(
            @RequestHeader("Authorization") String authorization,
            ProjectQueryDTO query) {

        String token = TokenUtil.extractToken(authorization);
        ResponseVO<ProjectPageVO> response = projectService.getProjectList(token, query);
        return ResponseEntity.ok(response);
    }

    /**
     * 获取单个项目详情
     */
    @GetMapping("/{projectId}")
    public ResponseEntity<ResponseVO<ProjectVO>> getProjectDetail(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long projectId) {

        String token = TokenUtil.extractToken(authorization);
        ResponseVO<ProjectVO> response = projectService.getProjectDetail(token, projectId);
        return ResponseEntity.ok(response);
    }

    /**
     * 获取当前用户授权的项目列表
     */
    @GetMapping("/authorized")
    public ResponseEntity<ResponseVO<List<ProjectVO>>> getAuthorizedProjects(
            @RequestHeader("Authorization") String authorization) {

        String token = TokenUtil.extractToken(authorization);
        ResponseVO<List<ProjectVO>> response = projectService.getAuthorizedProjects(token);
        return ResponseEntity.ok(response);
    }


}
