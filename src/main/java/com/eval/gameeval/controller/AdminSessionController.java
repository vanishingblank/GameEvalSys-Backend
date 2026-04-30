package com.eval.gameeval.controller;

import com.eval.gameeval.aspect.LogRecord;
import com.eval.gameeval.models.VO.ResponseVO;
import com.eval.gameeval.models.VO.SessionInfoVO;
import com.eval.gameeval.models.VO.OnlineUserPageVO;
import com.eval.gameeval.models.DTO.User.AdminOnlineUserQueryDTO;
import com.eval.gameeval.security.CurrentUserContext;
import com.eval.gameeval.service.IAuthService;
import jakarta.annotation.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin")
public class AdminSessionController {

    @Resource
    private IAuthService authService;
    @Resource
    private CurrentUserContext currentUserContext;

    @GetMapping("/sessions")
    @LogRecord(value = "查询指定用户会话", module = "AdminSession")
    public ResponseEntity<ResponseVO<List<SessionInfoVO>>> getUserSessions(@RequestParam("userId") Long userId) {
        Long currentUserId = currentUserContext.getCurrentUserId();
        ResponseVO<List<SessionInfoVO>> response = authService.getUserSessions(currentUserId, userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/online-users")
    @LogRecord(value = "查询在线用户列表", module = "AdminSession")
    public ResponseEntity<ResponseVO<OnlineUserPageVO>> getOnlineUsers(AdminOnlineUserQueryDTO query) {
        Long currentUserId = currentUserContext.getCurrentUserId();
        ResponseVO<OnlineUserPageVO> response = authService.getOnlineUsers(currentUserId, query);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/sessions/{sid}/kick")
    @LogRecord(value = "踢指定会话下线", module = "AdminSession")
    public ResponseEntity<ResponseVO<Void>> kickSession(@PathVariable("sid") String sid) {
        Long currentUserId = currentUserContext.getCurrentUserId();
        ResponseVO<Void> response = authService.kickSession(currentUserId, sid);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/users/{userId}/kick-all")
    @LogRecord(value = "踢用户全部会话下线", module = "AdminSession")
    public ResponseEntity<ResponseVO<Void>> kickAll(@PathVariable("userId") Long userId) {
        Long currentUserId = currentUserContext.getCurrentUserId();
        ResponseVO<Void> response = authService.kickAllSessions(currentUserId, userId);
        return ResponseEntity.ok(response);
    }
}
