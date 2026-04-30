package com.eval.gameeval.service.impl;

import com.eval.gameeval.mapper.UserMapper;
import com.eval.gameeval.models.DTO.User.AdminOnlineUserQueryDTO;
import com.eval.gameeval.models.DTO.User.LoginRequestDTO;
import com.eval.gameeval.models.DTO.User.RefreshRequestDTO;
import com.eval.gameeval.models.VO.LoginResponseVO;
import com.eval.gameeval.models.VO.OnlineUserPageVO;
import com.eval.gameeval.models.VO.OnlineUserVO;
import com.eval.gameeval.models.VO.RefreshResponseVO;
import com.eval.gameeval.models.VO.ResponseVO;
import com.eval.gameeval.models.VO.SessionInfoVO;
import com.eval.gameeval.models.entity.User;
import com.eval.gameeval.security.AuthSessionStore;
import com.eval.gameeval.security.JwtTokenService;
import com.eval.gameeval.service.IAuthService;
import com.eval.gameeval.util.RedisToken;
import com.eval.gameeval.util.TokenUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AuthServiceImpl implements IAuthService{

    @Resource
    private UserMapper userMapper;
    @Resource
    private RedisToken redisToken;
    @Resource
    private TokenUtil tokenUtil;
    @Resource
    private JwtTokenService jwtTokenService;
    @Resource
    private AuthSessionStore authSessionStore;
    @Resource
    private PasswordEncoder passwordEncoder;
    @Override
    public ResponseVO<LoginResponseVO> login(LoginRequestDTO loginRequest){
        try{
            // 1.查询用户
            User user = userMapper.selectByUsername(loginRequest.getUsername());
            if (user == null){
                return ResponseVO.unauthorized("用户名或密码错误");
            }
            // 2. 验证密码
//            if(Objects.equals(loginRequest.getPassword(), user.getPassword()))
//                {
//                    log.info("用户直接匹配登录成功: userId={}, username={}", user.getId(), user.getUsername());
//                }
//            if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {
//                if(Objects.equals(loginRequest.getPassword(), user.getPassword()))
//                {
//                    log.info("用户直接匹配登录成功: userId={}, username={}", user.getId(), user.getUsername());
//                }
//                else{
//                    return ResponseVO.unauthorized("用户名或密码错误");
//                }
//
//            }
            if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {
                return ResponseVO.unauthorized("用户名或密码错误");
            }

                // 3. 生成Access/Refresh Token
                String sid = tokenUtil.generateToken();
                String jti = tokenUtil.generateToken();
                String refreshToken = tokenUtil.generateToken();
                String refreshTokenId = tokenUtil.generateToken();

                long tokenVersion = authSessionStore.getTokenVersion(user.getId());
                Map<String, Object> claims = new HashMap<>();
                claims.put("username", user.getUsername());
                claims.put("role", user.getRole());
                claims.put("sid", sid);
                claims.put("type", "access");
                claims.put("tokenVersion", tokenVersion);
                String accessToken = jwtTokenService.generateAccessToken(
                    claims,
                    String.valueOf(user.getId()),
                    jti
                );

                // 4. 保存会话到Redis
                authSessionStore.saveSession(sid, user.getId(), user.getUsername(), user.getRole());
                authSessionStore.addUserSession(user.getId(), sid);
                authSessionStore.saveRefresh(sid, refreshToken, refreshTokenId);
                authSessionStore.enforceSessionLimit(user.getId());

                // 5. 构建响应
            LoginResponseVO responseVO = new LoginResponseVO();
                responseVO.setToken(accessToken);
                responseVO.setRefreshToken(refreshToken);
                responseVO.setSid(sid);
                responseVO.setExpireTime(jwtTokenService.getAccessExpireTime());

            LoginResponseVO.UserInfoVO userInfoVO = new LoginResponseVO.UserInfoVO();
            BeanUtils.copyProperties(user, userInfoVO);
            responseVO.setUserInfo(userInfoVO);

            log.info("用户登录成功: userId={}, username={}", user.getId(), user.getUsername());

            return ResponseVO.success("登录成功", responseVO);
        } catch (Exception e){
            log.error("登录错误",e);
            return ResponseVO.error("登录错误");
        }
    }

    @Override
    public ResponseVO<Void> logout(String token) {
        try {
            if (token == null || token.trim().isEmpty()) {
                return ResponseVO.badRequest("Token不能为空");
            }

            // 1. 从Token中获取用户ID
            Long userId = redisToken.getUserIdByToken(token);
            if (userId == null) {
                return ResponseVO.unauthorized("Token无效");
            }

            // 2. 删除Redis中的会话并拉黑当前Access Token
            redisToken.deleteToken(token);

            log.info("用户退出成功: userId={}", userId);

            return ResponseVO.success("退出成功", null);

        } catch (Exception e) {
            log.error("退出登录异常", e);
            return ResponseVO.error("退出失败");
        }
    }

    @Override
    public ResponseVO<RefreshResponseVO> refresh(RefreshRequestDTO request, String refreshToken) {
        try {
            if (request == null || request.getSid() == null || refreshToken == null || refreshToken.trim().isEmpty()) {
                return ResponseVO.badRequest("参数不能为空");
            }

            String sid = request.getSid();

            if (!authSessionStore.matchRefreshToken(sid, refreshToken)) {
                authSessionStore.deleteSession(sid);
                authSessionStore.deleteRefresh(sid);
                return ResponseVO.unauthorized("Refresh Token无效");
            }

            Map<Object, Object> session = authSessionStore.getSession(sid);
            if (session == null || session.isEmpty()) {
                return ResponseVO.unauthorized("会话已失效");
            }

            Long userId = Long.parseLong(session.get("userId").toString());
            String username = String.valueOf(session.get("username"));
            String role = String.valueOf(session.get("role"));

            String jti = tokenUtil.generateToken();
            long tokenVersion = authSessionStore.getTokenVersion(userId);
            Map<String, Object> claims = new HashMap<>();
            claims.put("username", username);
            claims.put("role", role);
            claims.put("sid", sid);
            claims.put("type", "access");
            claims.put("tokenVersion", tokenVersion);

            String accessToken = jwtTokenService.generateAccessToken(
                    claims,
                    String.valueOf(userId),
                    jti
            );

            String newRefreshToken = tokenUtil.generateToken();
            String newRefreshTokenId = tokenUtil.generateToken();
            authSessionStore.saveRefresh(sid, newRefreshToken, newRefreshTokenId);
            authSessionStore.refreshSessionTtl(sid);
            authSessionStore.refreshUserSessionsTtl(userId);

            RefreshResponseVO response = new RefreshResponseVO();
            response.setToken(accessToken);
            response.setRefreshToken(newRefreshToken);
            response.setSid(sid);
            response.setExpireTime(jwtTokenService.getAccessExpireTime());

            return ResponseVO.success("刷新成功", response);
        } catch (Exception e) {
            log.error("刷新Token异常", e);
            return ResponseVO.error("刷新失败");
        }
    }

    @Override
    public ResponseVO<List<SessionInfoVO>> getMySessions(Long userId) {
        try {
            if (userId == null) {
                return ResponseVO.unauthorized("未登录");
            }
            Set<String> sids = authSessionStore.getUserSessions(userId);
            List<SessionInfoVO> sessions = buildSessionInfos(sids);

            return ResponseVO.success("查询成功", sessions);
        } catch (Exception e) {
            log.error("查询会话异常", e);
            return ResponseVO.error("查询失败");
        }
    }

    @Override
    public ResponseVO<List<SessionInfoVO>> getUserSessions(Long currentUserId, Long targetUserId) {
        try {
            User currentUser = getCurrentUser(currentUserId);
            if (currentUser == null) {
                return ResponseVO.unauthorized("用户不存在");
            }
            if (!isAdmin(currentUser)) {
                return ResponseVO.forbidden("权限不足");
            }
            User targetUser = userMapper.selectById(targetUserId);
            if (targetUser == null) {
                return ResponseVO.notFound("用户不存在");
            }
            Set<String> sids = authSessionStore.getUserSessions(targetUserId);
            List<SessionInfoVO> sessions = buildSessionInfos(sids);
            return ResponseVO.success("查询成功", sessions);
        } catch (Exception e) {
            log.error("管理员查询会话异常", e);
            return ResponseVO.error("查询失败");
        }
    }

    @Override
    public ResponseVO<Void> kickSession(Long currentUserId, String sid) {
        try {
            User currentUser = getCurrentUser(currentUserId);
            if (currentUser == null) {
                return ResponseVO.unauthorized("用户不存在");
            }
            if (!isAdmin(currentUser)) {
                return ResponseVO.forbidden("权限不足");
            }
            if (sid == null || sid.trim().isEmpty()) {
                return ResponseVO.badRequest("sid不能为空");
            }

            Long targetUserId = authSessionStore.getSessionUserId(sid);
            if (targetUserId == null) {
                return ResponseVO.notFound("会话不存在");
            }

            authSessionStore.deleteSession(sid);
            authSessionStore.deleteRefresh(sid);
            authSessionStore.removeUserSession(targetUserId, sid);

            log.info("管理员踢下线: adminId={}, targetUserId={}, sid={}", currentUserId, targetUserId, sid);
            return ResponseVO.success("踢下线成功", null);
        } catch (Exception e) {
            log.error("管理员踢下线异常", e);
            return ResponseVO.error("踢下线失败");
        }
    }

    @Override
    public ResponseVO<Void> kickAllSessions(Long currentUserId, Long targetUserId) {
        try {
            User currentUser = getCurrentUser(currentUserId);
            if (currentUser == null) {
                return ResponseVO.unauthorized("用户不存在");
            }
            if (!isAdmin(currentUser)) {
                return ResponseVO.forbidden("权限不足");
            }
            User targetUser = userMapper.selectById(targetUserId);
            if (targetUser == null) {
                return ResponseVO.notFound("用户不存在");
            }

            Set<String> sids = authSessionStore.getUserSessions(targetUserId);
            for (String sid : sids) {
                authSessionStore.deleteSession(sid);
                authSessionStore.deleteRefresh(sid);
                authSessionStore.removeUserSession(targetUserId, sid);
            }

            log.info("管理员踢全端: adminId={}, targetUserId={}", currentUserId, targetUserId);
            return ResponseVO.success("踢下线成功", null);
        } catch (Exception e) {
            log.error("管理员踢全端异常", e);
            return ResponseVO.error("踢下线失败");
        }
    }

    @Override
    public ResponseVO<OnlineUserPageVO> getOnlineUsers(Long currentUserId, AdminOnlineUserQueryDTO query) {
        try {
            User currentUser = getCurrentUser(currentUserId);
            if (currentUser == null) {
                return ResponseVO.unauthorized("用户不存在");
            }
            if (!isAdmin(currentUser)) {
                return ResponseVO.forbidden("权限不足");
            }

            int page = query != null && query.getPage() != null ? query.getPage() : 1;
            int size = query != null && query.getSize() != null ? query.getSize() : 10;
            int offset = (page - 1) * size;

            String role = query != null ? query.getRole() : null;
            String keyWords = query != null ? query.getKeyWords() : null;
            Boolean isEnabled = query != null ? query.getIsEnabled() : null;
            Boolean onlineOnly = query != null ? query.getOnlineOnly() : null;

            List<Map<String, Object>> userList = userMapper.selectPageWithGroups(
                    offset,
                    size,
                    role,
                    keyWords,
                    isEnabled
            );
            Long total = userMapper.countTotal(role, keyWords, isEnabled);

            List<OnlineUserVO> onlineUsers = new ArrayList<>();
            for (Map<String, Object> userMap : userList) {
                Long userId = toLong(userMap.get("id"));
                OnlineUserVO vo = new OnlineUserVO();
                vo.setId(userId);
                vo.setUsername((String) userMap.get("username"));
                vo.setName((String) userMap.get("name"));
                vo.setRole((String) userMap.get("role"));
                vo.setIsEnabled(toBoolean(userMap.get("isEnabled")));

                SessionSummary summary = buildSessionSummary(userId);
                vo.setOnlineCount(summary.onlineCount);
                vo.setLastActiveAt(summary.lastActiveAt);
                vo.setLastLoginAt(summary.lastLoginAt);

                if (Boolean.TRUE.equals(onlineOnly) && summary.onlineCount <= 0) {
                    continue;
                }
                onlineUsers.add(vo);
            }

            OnlineUserPageVO pageVO = new OnlineUserPageVO();
            pageVO.setList(onlineUsers);
            pageVO.setTotal(Boolean.TRUE.equals(onlineOnly) ? (long) onlineUsers.size() : total);
            pageVO.setPage(page);
            pageVO.setSize(size);

            return ResponseVO.success("查询成功", pageVO);
        } catch (Exception e) {
            log.error("查询在线用户异常", e);
            return ResponseVO.error("查询失败");
        }
    }

    private List<SessionInfoVO> buildSessionInfos(Set<String> sids) {
        return sids.stream()
            .map(sid -> new java.util.AbstractMap.SimpleEntry<>(sid, authSessionStore.getSession(sid)))
            .filter(entry -> entry.getValue() != null && !entry.getValue().isEmpty())
            .map(entry -> new SessionInfoVO()
                .setSid(entry.getKey())
                .setUsername(String.valueOf(entry.getValue().get("username")))
                .setRole(String.valueOf(entry.getValue().get("role")))
                .setLoginAt(String.valueOf(entry.getValue().get("loginAt")))
                .setLastActiveAt(String.valueOf(entry.getValue().get("lastActiveAt")))
                .setStatus(String.valueOf(entry.getValue().get("status"))))
            .collect(Collectors.toList());
    }

    private User getCurrentUser(Long currentUserId) {
        if (currentUserId == null) {
            return null;
        }
        return userMapper.selectById(currentUserId);
    }

    private SessionSummary buildSessionSummary(Long userId) {
        Set<String> sids = authSessionStore.getUserSessions(userId);
        int onlineCount = 0;
        Instant latestActive = null;
        Instant latestLogin = null;
        String latestActiveText = null;
        String latestLoginText = null;

        for (String sid : sids) {
            Map<Object, Object> session = authSessionStore.getSession(sid);
            if (session == null || session.isEmpty()) {
                continue;
            }
            onlineCount++;
            Instant active = parseInstant(session.get("lastActiveAt"));
            if (active != null && (latestActive == null || active.isAfter(latestActive))) {
                latestActive = active;
                latestActiveText = session.get("lastActiveAt") != null ? session.get("lastActiveAt").toString() : null;
            }
            Instant login = parseInstant(session.get("loginAt"));
            if (login != null && (latestLogin == null || login.isAfter(latestLogin))) {
                latestLogin = login;
                latestLoginText = session.get("loginAt") != null ? session.get("loginAt").toString() : null;
            }
        }

        return new SessionSummary(onlineCount, latestActiveText, latestLoginText);
    }

    private Instant parseInstant(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value.toString());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private boolean isAdmin(User user) {
        if (user == null) {
            return false;
        }
        return "super_admin".equals(user.getRole()) || "admin".equals(user.getRole());
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    private Boolean toBoolean(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        if (value instanceof String) {
            String text = ((String) value).trim();
            if ("1".equals(text)) {
                return true;
            }
            if ("0".equals(text)) {
                return false;
            }
            return Boolean.parseBoolean(text);
        }
        return null;
    }

    private static final class SessionSummary {
        private final int onlineCount;
        private final String lastActiveAt;
        private final String lastLoginAt;

        private SessionSummary(int onlineCount, String lastActiveAt, String lastLoginAt) {
            this.onlineCount = onlineCount;
            this.lastActiveAt = lastActiveAt;
            this.lastLoginAt = lastLoginAt;
        }
    }
}
