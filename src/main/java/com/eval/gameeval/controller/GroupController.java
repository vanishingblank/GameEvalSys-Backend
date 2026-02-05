package com.eval.gameeval.controller;

import com.eval.gameeval.models.DTO.GroupCreateDTO;
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
     * 创建小组
     */
    @PostMapping
    public ResponseEntity<ResponseVO<GroupVO>> createGroup(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody GroupCreateDTO request) {

        String token = TokenUtil.extractToken(authorization);
        ResponseVO<GroupVO> response = groupService.createGroup(token, request);
        return ResponseEntity.ok(response);
    }


}