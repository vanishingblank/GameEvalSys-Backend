package com.eval.gameeval.controller;

import com.eval.gameeval.models.DTO.UserCreateDTO;
import com.eval.gameeval.models.DTO.UserQueryDTO;
import com.eval.gameeval.models.DTO.UserUpdateDTO;
import com.eval.gameeval.models.VO.ResponseVO;
import com.eval.gameeval.models.VO.UserPageVO;
import com.eval.gameeval.models.VO.UserVO;
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
    public ResponseEntity<ResponseVO<List<UserVO>>> createUsers(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody UserCreateDTO request) {

        String token = TokenUtil.extractToken(authorization);
//        String token = "";
        ResponseVO<List<UserVO>> response = userService.createUsers(token, request);
        return ResponseEntity.ok(response);
    }

    /**
     * 编辑用户
     */
    @PutMapping("/{userId}")
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
}
