package com.eval.gameeval.controller;

import com.eval.gameeval.aspect.LogRecord;
import com.eval.gameeval.models.DTO.User.UserBatchDeleteDTO;
import com.eval.gameeval.models.DTO.User.UserBatchQueryDTO;
import com.eval.gameeval.models.DTO.User.UserBatchStatusDTO;
import com.eval.gameeval.models.DTO.User.UserCreateDTO;
import com.eval.gameeval.models.DTO.User.UserPasswordUpdateDTO;
import com.eval.gameeval.models.DTO.User.UserQueryDTO;
import com.eval.gameeval.models.DTO.User.UserUpdateDTO;
import com.eval.gameeval.models.VO.*;
import com.eval.gameeval.security.CurrentUserContext;
import com.eval.gameeval.service.IUserService;
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
    private CurrentUserContext currentUserContext;

    @Resource
    private IUserService userService;

    /**
     * 批量创建用户
     */
    @PostMapping
    @LogRecord(value = "创建用户", module = "User")
    public ResponseEntity<ResponseVO<List<UserWithGroupVO>>> createUsers(@Valid @RequestBody UserCreateDTO request) {
        Long currentUserId = currentUserContext.getCurrentUserId();
        ResponseVO<List<UserWithGroupVO>> response = userService.createUsers(currentUserId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * 编辑用户
     */
    @PutMapping("/{userId}")
    @LogRecord(value = "编辑用户", module = "User")
    public ResponseEntity<ResponseVO<Void>> updateUser(
            @PathVariable Long userId,
            @Valid @RequestBody UserUpdateDTO request) {
        Long currentUserId = currentUserContext.getCurrentUserId();
        ResponseVO<Void> response = userService.updateUser(currentUserId, userId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * 批量修改用户启用状态
     */
    @PutMapping("/batch-status")
    @LogRecord(value = "批量修改用户状态", module = "User")
    public ResponseEntity<ResponseVO<UserBatchOperationResultVO>> batchUpdateUserStatus(@Valid @RequestBody UserBatchStatusDTO request) {
        Long currentUserId = currentUserContext.getCurrentUserId();
        ResponseVO<UserBatchOperationResultVO> response = userService.batchUpdateUserStatus(currentUserId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * 用户修改自己的密码
     */
    @PutMapping("/me/password")
    @LogRecord(value = "修改密码", module = "User")
    public ResponseEntity<ResponseVO<Void>> updateSelfPassword(@Valid @RequestBody UserPasswordUpdateDTO request) {
        Long currentUserId = currentUserContext.getCurrentUserId();
        ResponseVO<Void> response = userService.updateSelfPassword(currentUserId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * 删除用户
     */
    @DeleteMapping("/{userId}")
    @LogRecord(value = "删除用户", module = "User")
    public ResponseEntity<ResponseVO<Void>> deleteUser(@PathVariable Long userId) {
        Long currentUserId = currentUserContext.getCurrentUserId();
        ResponseVO<Void> response = userService.deleteUser(currentUserId, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * 批量删除用户
     */
    @DeleteMapping("/batch-delete")
    @LogRecord(value = "批量删除用户", module = "User")
    public ResponseEntity<ResponseVO<UserBatchOperationResultVO>> batchDeleteUsers(@Valid @RequestBody UserBatchDeleteDTO request) {
        Long currentUserId = currentUserContext.getCurrentUserId();
        ResponseVO<UserBatchOperationResultVO> response = userService.batchDeleteUsers(currentUserId, request);
        return ResponseEntity.ok(response);
    }


    /**
     * 获取用户列表（分页）
     */
    @GetMapping
    public ResponseEntity<ResponseVO<UserPageVO>> getUserList(UserQueryDTO query) {
        Long currentUserId = currentUserContext.getCurrentUserId();
        ResponseVO<UserPageVO> response = userService.getUserList(currentUserId, query);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/overview")
    public ResponseEntity<ResponseVO<UserOverviewVO>> getUserOverview() {
        Long currentUserId = currentUserContext.getCurrentUserId();
        ResponseVO<UserOverviewVO> response = userService.getUserOverview(currentUserId);
        return ResponseEntity.ok(response);
    }

    /**
     * 批量查询用户详细信息

     * @param authorization 认证Token
     * @param request 批量查询请求
     * @return 用户详细信息列表
     */
    @PostMapping("/batch-query")
    public ResponseEntity<ResponseVO<List<UserDetailVO>>> batchQueryUsers(@Valid @RequestBody UserBatchQueryDTO request) {
        Long currentUserId = currentUserContext.getCurrentUserId();
        ResponseVO<List<UserDetailVO>> response = userService.batchQueryUsers(currentUserId, request);
        return ResponseEntity.ok(response);
    }
}
