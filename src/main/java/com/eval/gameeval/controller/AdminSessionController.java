package com.eval.gameeval.controller;

import com.eval.gameeval.models.VO.ResponseVO;
import com.eval.gameeval.models.VO.SessionInfoVO;
import com.eval.gameeval.models.VO.OnlineUserPageVO;
import com.eval.gameeval.models.DTO.User.AdminOnlineUserQueryDTO;
import com.eval.gameeval.service.IAuthService;
import jakarta.annotation.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin")
public class AdminSessionController {

    @Resource
    private IAuthService authService;

    @GetMapping("/sessions")
    public ResponseEntity<ResponseVO<List<SessionInfoVO>>> getUserSessions(@RequestParam("userId") Long userId) {
        Long currentUserId = resolveUserId();
        ResponseVO<List<SessionInfoVO>> response = authService.getUserSessions(currentUserId, userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/online-users")
    public ResponseEntity<ResponseVO<OnlineUserPageVO>> getOnlineUsers(AdminOnlineUserQueryDTO query) {
        Long currentUserId = resolveUserId();
        ResponseVO<OnlineUserPageVO> response = authService.getOnlineUsers(currentUserId, query);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/sessions/{sid}/kick")
    public ResponseEntity<ResponseVO<Void>> kickSession(@PathVariable("sid") String sid) {
        Long currentUserId = resolveUserId();
        ResponseVO<Void> response = authService.kickSession(currentUserId, sid);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/users/{userId}/kick-all")
    public ResponseEntity<ResponseVO<Void>> kickAll(@PathVariable("userId") Long userId) {
        Long currentUserId = resolveUserId();
        ResponseVO<Void> response = authService.kickAllSessions(currentUserId, userId);
        return ResponseEntity.ok(response);
    }

    private Long resolveUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof Long) {
            return (Long) principal;
        }
        if (principal instanceof String) {
            try {
                return Long.parseLong((String) principal);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
     }
 }
