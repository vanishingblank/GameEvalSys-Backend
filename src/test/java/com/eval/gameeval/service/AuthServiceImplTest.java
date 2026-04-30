package com.eval.gameeval.service;

import com.eval.gameeval.mapper.UserMapper;
import com.eval.gameeval.models.DTO.User.LoginRequestDTO;
import com.eval.gameeval.models.DTO.User.RefreshRequestDTO;
import com.eval.gameeval.models.VO.LoginResponseVO;
import com.eval.gameeval.models.VO.RefreshResponseVO;
import com.eval.gameeval.models.VO.ResponseVO;
import com.eval.gameeval.models.entity.User;
import com.eval.gameeval.security.AuthSessionStore;
import com.eval.gameeval.security.JwtTokenService;
import com.eval.gameeval.service.impl.AuthServiceImpl;
import com.eval.gameeval.util.RedisToken;
import com.eval.gameeval.util.TokenUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doNothing;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private RedisToken redisToken;

    @Mock
    private JwtTokenService jwtTokenService;

    @Mock
    private AuthSessionStore authSessionStore;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Spy
    private TokenUtil tokenUtil = new TokenUtil();

    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    void loginRefreshLogoutFlow() {
        User user = new User();
        user.setId(1L);
        user.setUsername("tester");
        user.setRole("admin");
        user.setPassword("encoded");

        LoginRequestDTO loginRequest = new LoginRequestDTO();
        loginRequest.setUsername("tester");
        loginRequest.setPassword("secret");

        when(userMapper.selectByUsername("tester")).thenReturn(user);
        when(passwordEncoder.matches("secret", "encoded")).thenReturn(true);
        when(jwtTokenService.generateAccessToken(ArgumentMatchers.anyMap(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
                .thenReturn("access-token");
        when(jwtTokenService.getAccessExpireTime()).thenReturn(LocalDateTime.now().plusHours(4));

        ResponseVO<LoginResponseVO> loginResponse = authService.login(loginRequest);
        assertThat(loginResponse.getCode()).isEqualTo(200);
        assertThat(loginResponse.getData()).isNotNull();
        assertThat(loginResponse.getData().getToken()).isEqualTo("access-token");
        assertThat(loginResponse.getData().getRefreshToken()).isNotBlank();
        assertThat(loginResponse.getData().getSid()).isNotBlank();

        RefreshRequestDTO refreshRequest = new RefreshRequestDTO();
        refreshRequest.setSid(loginResponse.getData().getSid());

        Map<Object, Object> session = new HashMap<>();
        session.put("userId", 1L);
        session.put("username", "tester");
        session.put("role", "admin");

        String refreshToken = loginResponse.getData().getRefreshToken();
        when(authSessionStore.matchRefreshToken(refreshRequest.getSid(), refreshToken)).thenReturn(true);
        when(authSessionStore.getSession(refreshRequest.getSid())).thenReturn(session);
        when(jwtTokenService.generateAccessToken(ArgumentMatchers.anyMap(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
                .thenReturn("new-access-token");
        when(jwtTokenService.getAccessExpireTime()).thenReturn(LocalDateTime.now().plusHours(4));
        doNothing().when(authSessionStore).saveRefresh(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyString());
        doNothing().when(authSessionStore).refreshSessionTtl(ArgumentMatchers.anyString());
        doNothing().when(authSessionStore).refreshUserSessionsTtl(ArgumentMatchers.anyLong());

        ResponseVO<RefreshResponseVO> refreshResponse = authService.refresh(refreshRequest, refreshToken);
        assertThat(refreshResponse.getCode()).isEqualTo(200);
        assertThat(refreshResponse.getData()).isNotNull();
        assertThat(refreshResponse.getData().getToken()).isEqualTo("new-access-token");
        assertThat(refreshResponse.getData().getRefreshToken()).isNotBlank();

        when(redisToken.getUserIdByToken("new-access-token")).thenReturn(1L);
        doNothing().when(redisToken).deleteToken("new-access-token");

        ResponseVO<Void> logoutResponse = authService.logout("new-access-token");
        assertThat(logoutResponse.getCode()).isEqualTo(200);
        verify(redisToken).deleteToken("new-access-token");
    }
}
