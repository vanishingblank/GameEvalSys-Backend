package com.eval.gameeval.controller;

import com.eval.gameeval.models.DTO.LoginRequestDTO;
import com.eval.gameeval.models.DTO.UserCreateDTO;
import com.eval.gameeval.models.DTO.UserUpdateDTO;
import com.eval.gameeval.models.VO.LoginResponseVO;
import com.eval.gameeval.models.VO.ResponseVO;
import com.eval.gameeval.models.VO.UserVO;
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
    private IUserService userService;

    @PostMapping("/login")
    public ResponseEntity<ResponseVO<LoginResponseVO>> login(
            @Valid @RequestBody LoginRequestDTO loginRequest){
        ResponseVO<LoginResponseVO> response = userService.login(loginRequest);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<ResponseVO<Void>> logout(
            @RequestHeader("Authorization") String authorization){
        String token = extractToken(authorization);
        ResponseVO<Void> response = userService.logout(token);
        return ResponseEntity.ok(response);
    }

    /**
     * 批量创建用户
     */
    @PostMapping
    public ResponseEntity<ResponseVO<List<UserVO>>> createUsers(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody UserCreateDTO request) {

        String token = extractToken(authorization);
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

        String token = extractToken(authorization);
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

        String token = extractToken(authorization);
        ResponseVO<Void> response = userService.deleteUser(token, userId);
        return ResponseEntity.ok(response);
    }

    private String extractToken(String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7); // "Bearer "长度为7
        }
        return authorization;
    }
}
