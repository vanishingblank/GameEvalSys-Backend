package com.eval.gameeval.controller;

import com.eval.gameeval.aspect.LogRecord;
import com.eval.gameeval.models.DTO.UserBatchQueryDTO;
import com.eval.gameeval.models.DTO.UserCreateDTO;
import com.eval.gameeval.models.DTO.UserQueryDTO;
import com.eval.gameeval.models.DTO.UserUpdateDTO;
import com.eval.gameeval.models.VO.*;
import com.eval.gameeval.service.IUserService;
import com.eval.gameeval.util.TokenUtil;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/users")
public class UserController {

    @Resource
    private IUserService userService;

    /**
     * 批量创建用户
     */
    @PostMapping
    @LogRecord(value = "创建用户", module = "User")
    public ResponseEntity<ResponseVO<List<UserWithGroupVO>>> createUsers(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody UserCreateDTO request) {

        String token = TokenUtil.extractToken(authorization);
//        String token = "";
        ResponseVO<List<UserWithGroupVO>> response = userService.createUsers(token, request);
        return ResponseEntity.ok(response);
    }

    /**
     * 编辑用户
     */
    @PutMapping("/{userId}")
    @LogRecord(value = "编辑用户", module = "User")
    public ResponseEntity<ResponseVO<Void>> updateUser(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long userId,
            @Valid @RequestBody UserUpdateDTO request) {

        String token = TokenUtil.extractToken(authorization);
        ResponseVO<Void> response = userService.updateUser(token, userId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * 删除用户
     */
    @DeleteMapping("/{userId}")
    @LogRecord(value = "删除用户", module = "User")
    public ResponseEntity<ResponseVO<Void>> deleteUser(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long userId) {

        String token = TokenUtil.extractToken(authorization);
        ResponseVO<Void> response = userService.deleteUser(token, userId);
        return ResponseEntity.ok(response);
    }


    /**
     * 获取用户列表（分页）
     */
    @GetMapping
    public ResponseEntity<ResponseVO<UserPageVO>> getUserList(
            @RequestHeader("Authorization") String authorization,
            UserQueryDTO query) {

        String token = TokenUtil.extractToken(authorization);
        ResponseVO<UserPageVO> response = userService.getUserList(token, query);
        return ResponseEntity.ok(response);
    }
    /**
     * 批量查询用户详细信息

     * @param authorization 认证Token
     * @param request 批量查询请求
     * @return 用户详细信息列表
     */
    @PostMapping("/batch-query")
    public ResponseEntity<ResponseVO<List<UserDetailVO>>> batchQueryUsers(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody UserBatchQueryDTO request) {

        String token = TokenUtil.extractToken(authorization);
        ResponseVO<List<UserDetailVO>> response = userService.batchQueryUsers(token, request);
        return ResponseEntity.ok(response);
    }
}
