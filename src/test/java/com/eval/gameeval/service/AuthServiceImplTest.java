package com.eval.gameeval.service;

import com.eval.gameeval.mapper.UserMapper;
import com.eval.gameeval.mapper.MenuMapper;
import com.eval.gameeval.models.DTO.User.LoginMetaDTO;
import com.eval.gameeval.models.DTO.User.LoginRequestDTO;
import com.eval.gameeval.models.DTO.User.RefreshRequestDTO;
import com.eval.gameeval.models.VO.AuthProfileVO;
import com.eval.gameeval.models.VO.LoginResponseVO;
import com.eval.gameeval.models.VO.OnlineUserPageVO;
import com.eval.gameeval.models.VO.RouteNodeVO;
import com.eval.gameeval.models.VO.RefreshResponseVO;
import com.eval.gameeval.models.VO.ResponseVO;
import com.eval.gameeval.models.VO.SessionInfoVO;
import com.eval.gameeval.models.entity.User;
import com.eval.gameeval.models.entity.Menu;
import com.eval.gameeval.security.AuthSessionStore;
import com.eval.gameeval.security.JwtTokenService;
import com.eval.gameeval.service.impl.AuthServiceImpl;
import com.eval.gameeval.util.RedisBaseUtil;
import com.eval.gameeval.util.RedisKeyUtil;
import com.eval.gameeval.util.RedisToken;
import com.eval.gameeval.util.TokenUtil;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserMapper userMapper;

        @Mock
        private MenuMapper menuMapper;

    @Mock
    private RedisToken redisToken;

    @Mock
    private JwtTokenService jwtTokenService;

    @Mock
    private AuthSessionStore authSessionStore;

    @Mock
    private RedisBaseUtil redisBaseUtil;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Spy
    private TokenUtil tokenUtil = new TokenUtil();

    @InjectMocks
    private AuthServiceImpl authService;

    private LoginMetaDTO buildLoginMeta() {
        String ip = "127.0.0.1";
        String device ="Windows";
        String loginLocation ="localhost";
        return new LoginMetaDTO()
                .setIp(ip)
                .setDevice(device)
                .setLoginLocation(loginLocation);
    }
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

        LoginMetaDTO meta = buildLoginMeta();

        ResponseVO<LoginResponseVO> loginResponse = authService.login(loginRequest,meta);
        assertThat(loginResponse.getCode()).isEqualTo(200);
        assertThat(loginResponse.getData()).isNotNull();
        assertThat(loginResponse.getData().getToken()).isEqualTo("access-token");
        assertThat(loginResponse.getData().getRefreshToken()).isNotBlank();
        assertThat(loginResponse.getData().getSid()).isNotBlank();
        verify(authSessionStore).touchUserOnlineIndex(1L);

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
        verify(authSessionStore, times(2)).touchUserOnlineIndex(1L);

        when(redisToken.getUserIdByToken("new-access-token")).thenReturn(1L);
        doNothing().when(redisToken).deleteToken("new-access-token");

        ResponseVO<Void> logoutResponse = authService.logout("new-access-token");
        assertThat(logoutResponse.getCode()).isEqualTo(200);
        verify(redisToken).deleteToken("new-access-token");
        verify(authSessionStore).rebuildUserOnlineIndex(1L);
        verify(redisBaseUtil, times(2)).delete(RedisKeyUtil.buildOnlineUserSummaryKey(1L));
    }

    @Test
    void getCurrentUserProfileShouldReturnRoleAndPermissions() {
        User user = new User();
        user.setId(2L);
        user.setUsername("admin");
        user.setName("系统管理员");
        user.setRole("super_admin");

        when(userMapper.selectById(2L)).thenReturn(user);
                when(menuMapper.selectAccessibleMenusByRoleCode("super_admin")).thenReturn(buildSuperAdminMenus());

        ResponseVO<AuthProfileVO> response = authService.getCurrentUserProfile(2L);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).isNotNull();
        assertThat(response.getData().getUsername()).isEqualTo("admin");
        assertThat(response.getData().getRoles()).containsExactly("super_admin");
        assertThat(response.getData().getPermissions())
                .contains("menu:home:view", "menu:admin:view", "menu:admin:project:view", "menu:admin:monitor:view");
    }

    @Test
    void getCurrentUserRoutesShouldReturnAdminTree() {
        User user = new User();
        user.setId(3L);
        user.setUsername("admin2");
        user.setName("管理员");
        user.setRole("admin");

        when(userMapper.selectById(3L)).thenReturn(user);
                when(menuMapper.selectAccessibleMenusByRoleCode("admin")).thenReturn(buildAdminMenus());

        ResponseVO<java.util.List<RouteNodeVO>> response = authService.getCurrentUserRoutes(3L);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).isNotNull();
        assertThat(response.getData()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(response.getData().get(0).getMenuCode()).isEqualTo("home");
        assertThat(response.getData().stream().anyMatch(route -> "admin".equals(route.getMenuCode()))).isTrue();
        RouteNodeVO adminRoute = response.getData().stream()
                .filter(route -> "admin".equals(route.getMenuCode()))
                .findFirst()
                .orElseThrow();
        assertThat(adminRoute.getChildren())
                .extracting(RouteNodeVO::getMenuCode)
                .contains("admin-project", "admin-reviewer-group", "admin-user", "admin-statistic");
    }

    @Test
    void getCurrentUserRoutesShouldAttachMenusToAncestorDirs() {
        User user = new User();
        user.setId(4L);
        user.setUsername("root");
        user.setName("超级管理员");
        user.setRole("super_admin");

        when(userMapper.selectById(4L)).thenReturn(user);
        when(menuMapper.selectAccessibleMenusByRoleCode("super_admin")).thenReturn(buildRoutesWithMissingAncestors());
        when(menuMapper.selectAllMenus()).thenReturn(buildRoutesWithAllAncestors());

        ResponseVO<java.util.List<RouteNodeVO>> response = authService.getCurrentUserRoutes(4L);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).isNotNull();
        assertThat(response.getData().stream().map(RouteNodeVO::getMenuCode))
                .contains("home", "scoring-list", "admin", "statistic", "super-admin");

        RouteNodeVO statisticRoute = response.getData().stream()
                .filter(route -> "statistic".equals(route.getMenuCode()))
                .findFirst()
                .orElseThrow();
        assertThat(statisticRoute.getChildren())
                .extracting(RouteNodeVO::getMenuCode)
                .contains("admin-statistic", "admin-project-statistic");

        RouteNodeVO superAdminRoute = response.getData().stream()
                .filter(route -> "super-admin".equals(route.getMenuCode()))
                .findFirst()
                .orElseThrow();
        assertThat(superAdminRoute.getChildren())
                .extracting(RouteNodeVO::getMenuCode)
                .contains("super-monitor-online", "super-monitor-server", "super-admin-menu-management");
    }

    @Test
    void getOnlineUsersShouldUseBatchSessionLookupAndSummaryCache() {
        User currentUser = new User();
        currentUser.setId(99L);
        currentUser.setRole("admin");
        when(userMapper.selectById(99L)).thenReturn(currentUser);

        when(authSessionStore.getActiveOnlineUserIds()).thenReturn(Set.of(1L));
        Map<String, Object> userRow = new LinkedHashMap<>();
        userRow.put("id", 1L);
        userRow.put("username", "tester");
        userRow.put("name", "测试用户");
        userRow.put("role", "admin");
        userRow.put("isEnabled", true);

        when(userMapper.selectPageWithGroupsByIds(
                ArgumentMatchers.anyList(),
                ArgumentMatchers.nullable(String.class),
                ArgumentMatchers.nullable(String.class),
                ArgumentMatchers.nullable(Boolean.class),
                ArgumentMatchers.anyInt(),
                ArgumentMatchers.anyInt()))
                .thenReturn(List.of(userRow));
        when(userMapper.countTotalByIds(
                ArgumentMatchers.anyList(),
                ArgumentMatchers.nullable(String.class),
                ArgumentMatchers.nullable(String.class),
                ArgumentMatchers.nullable(Boolean.class)))
                .thenReturn(1L);
        when(redisBaseUtil.get(ArgumentMatchers.anyString())).thenReturn(null);

        Map<Object, Object> session = new HashMap<>();
        session.put("sid", "sid-1");
        session.put("userId", 1L);
        session.put("username", "tester");
        session.put("role", "admin");
        session.put("ip", "127.0.0.1");
        session.put("device", "Windows");
        session.put("loginLocation", "localhost");
        session.put("loginAt", Instant.now().minusSeconds(10).toString());
        session.put("lastActiveAt", Instant.now().toString());
        session.put("status", "active");

        when(authSessionStore.getUserSessions(1L)).thenReturn(Set.of("sid-1"));
        when(authSessionStore.getSessionsBySids(ArgumentMatchers.anyCollection()))
                .thenReturn(Map.of("sid-1", session));

        ResponseVO<OnlineUserPageVO> response = authService.getOnlineUsers(99L, null);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).isNotNull();
        assertThat(response.getData().getList()).hasSize(1);
        assertThat(response.getData().getList().get(0).getOnlineCount()).isEqualTo(1);
        assertThat(response.getData().getList().get(0).getLoginLocation()).isEqualTo("localhost");
        verify(authSessionStore).getSessionsBySids(ArgumentMatchers.anyCollection());
        verify(authSessionStore, never()).getSession(ArgumentMatchers.anyString());
    }

    @Test
    void getUserSessionsShouldUseBatchLookupAndFilterMissingSession() {
        User currentUser = new User();
        currentUser.setId(88L);
        currentUser.setRole("admin");
        when(userMapper.selectById(88L)).thenReturn(currentUser);

        User targetUser = new User();
        targetUser.setId(2L);
        targetUser.setUsername("target");
        when(userMapper.selectById(2L)).thenReturn(targetUser);

        Map<Object, Object> session = new HashMap<>();
        session.put("sid", "sid-1");
        session.put("username", "target");
        session.put("role", "admin");
        session.put("ip", "127.0.0.1");
        session.put("device", "Windows");
        session.put("loginLocation", "localhost");
        session.put("loginAt", Instant.now().minusSeconds(10).toString());
        session.put("lastActiveAt", Instant.now().toString());
        session.put("status", "active");

        when(authSessionStore.getUserSessions(2L)).thenReturn(Set.of("sid-1", "sid-missing"));
        when(authSessionStore.getSessionsBySids(ArgumentMatchers.anyCollection()))
                .thenReturn(Map.of("sid-1", session));

        ResponseVO<java.util.List<SessionInfoVO>> response = authService.getUserSessions(88L, 2L);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).hasSize(1);
        assertThat(response.getData().get(0).getSid()).isEqualTo("sid-1");
        verify(authSessionStore).getSessionsBySids(ArgumentMatchers.anyCollection());
        verify(authSessionStore, never()).getSession(ArgumentMatchers.anyString());
    }

        private java.util.List<Menu> buildAdminMenus() {
                java.util.List<Menu> menus = new java.util.ArrayList<>();
                menus.add(new Menu().setId(1L).setParentId(0L).setMenuCode("home").setMenuType("menu").setTitle("首页").setPath("/home").setRouteName("home").setIcon("HomeFilled").setHidden(false).setComponentCode("normal-home").setSortNum(1).setIsEnabled(true).setIsDeleted(false));
                menus.add(new Menu().setId(2L).setParentId(0L).setMenuCode("admin").setMenuType("dir").setTitle("管理面板").setPath("/admin").setRouteName("adminRoot").setIcon("Setting").setHidden(false).setSortNum(2).setIsEnabled(true).setIsDeleted(false));
                menus.add(new Menu().setId(3L).setParentId(2L).setMenuCode("admin-project").setMenuType("menu").setTitle("项目管理").setPath("/admin/project").setRouteName("projectList").setIcon("Management").setHidden(false).setComponentCode("admin-project-list").setSortNum(1).setIsEnabled(true).setIsDeleted(false));
                menus.add(new Menu().setId(4L).setParentId(2L).setMenuCode("admin-reviewer-group").setMenuType("menu").setTitle("评分组管理").setPath("/admin/reviewer-group").setRouteName("reviewerGroupList").setIcon("UserFilled").setHidden(false).setComponentCode("admin-reviewer-group").setSortNum(2).setIsEnabled(true).setIsDeleted(false));
                menus.add(new Menu().setId(5L).setParentId(2L).setMenuCode("admin-user").setMenuType("menu").setTitle("用户管理").setPath("/admin/user").setRouteName("userList").setIcon("User").setHidden(false).setComponentCode("admin-user").setSortNum(3).setIsEnabled(true).setIsDeleted(false));
                menus.add(new Menu().setId(6L).setParentId(2L).setMenuCode("admin-statistic").setMenuType("menu").setTitle("统计面板").setPath("/admin/statistic").setRouteName("adminStatistic").setIcon("Histogram").setHidden(false).setComponentCode("admin-statistic").setSortNum(4).setIsEnabled(true).setIsDeleted(false));
                menus.sort(java.util.Comparator.comparing(Menu::getSortNum));
                return menus;
        }

        private java.util.List<Menu> buildSuperAdminMenus() {
                java.util.List<Menu> menus = buildAdminMenus();
                menus.add(new Menu().setId(7L).setParentId(2L).setMenuCode("super-monitor").setMenuType("dir").setTitle("系统监控").setPath("/admin/monitor").setRouteName("monitorRoot").setIcon("Monitor").setHidden(false).setSortNum(5).setIsEnabled(true).setIsDeleted(false));
                menus.add(new Menu().setId(8L).setParentId(7L).setMenuCode("super-monitor-server").setMenuType("menu").setTitle("服务监控").setPath("/admin/monitor/server").setRouteName("monitorServer").setIcon("DataLine").setHidden(false).setComponentCode("super-monitor-server").setSortNum(1).setIsEnabled(true).setIsDeleted(false));
                menus.sort(java.util.Comparator.comparing(Menu::getSortNum));
                return menus;
        }

        private java.util.List<Menu> buildRoutesWithMissingAncestors() {
                java.util.List<Menu> menus = new java.util.ArrayList<>();
                menus.add(new Menu().setId(1L).setParentId(0L).setMenuCode("home").setMenuType("menu").setTitle("首页").setPath("/home").setRouteName("home").setIcon("HomeFilled").setHidden(false).setComponentCode("normal-home").setSortNum(1).setIsEnabled(true).setIsDeleted(false));
                menus.add(new Menu().setId(2L).setParentId(0L).setMenuCode("scoring-list").setMenuType("menu").setTitle("打分项目列表").setPath("/scoring").setRouteName("scoringRoot").setIcon("Edit").setHidden(false).setComponentCode("normal-scoring-list").setSortNum(2).setIsEnabled(true).setIsDeleted(false));
                menus.add(new Menu().setId(3L).setParentId(2L).setMenuCode("scoring-project").setMenuType("menu").setTitle("项目打分").setPath("/scoring/:projectId").setRouteName("projectScoring").setIcon("").setHidden(true).setComponentCode("normal-project-scoring").setSortNum(2).setIsEnabled(true).setIsDeleted(false));

                menus.add(new Menu().setId(6L).setParentId(0L).setMenuCode("admin").setMenuType("dir").setTitle("管理面板").setPath("/admin").setRouteName("adminRoot").setIcon("Setting").setHidden(false).setSortNum(3).setIsEnabled(true).setIsDeleted(false));
                menus.add(new Menu().setId(7L).setParentId(6L).setMenuCode("admin-project").setMenuType("menu").setTitle("项目管理").setPath("/admin/project").setRouteName("projectList").setIcon("Management").setHidden(false).setComponentCode("admin-project-list").setSortNum(5).setIsEnabled(true).setIsDeleted(false));
                menus.add(new Menu().setId(20L).setParentId(6L).setMenuCode("admin-project-group").setMenuType("menu").setTitle("项目受审队伍管理").setPath("/admin/project-groups").setRouteName("projectGroupList").setIcon("User").setHidden(false).setComponentCode("admin-project-group").setSortNum(4).setIsEnabled(true).setIsDeleted(false));
                menus.add(new Menu().setId(11L).setParentId(6L).setMenuCode("admin-reviewer-group").setMenuType("menu").setTitle("评审队伍管理").setPath("/admin/reviewer-group").setRouteName("reviewerGroupList").setIcon("UserFilled").setHidden(false).setComponentCode("admin-reviewer-group").setSortNum(1).setIsEnabled(true).setIsDeleted(false));
                menus.add(new Menu().setId(14L).setParentId(6L).setMenuCode("admin-scoring-stds").setMenuType("menu").setTitle("打分标准").setPath("/admin/scoring-stds").setRouteName("scoringStdList").setIcon("Checked").setHidden(false).setComponentCode("admin-scoring-stds").setSortNum(3).setIsEnabled(true).setIsDeleted(false));
                menus.add(new Menu().setId(15L).setParentId(6L).setMenuCode("admin-user").setMenuType("menu").setTitle("用户管理").setPath("/admin/user").setRouteName("userList").setIcon("User").setHidden(false).setComponentCode("admin-user").setSortNum(2).setIsEnabled(true).setIsDeleted(false));

                menus.add(new Menu().setId(21L).setParentId(0L).setMenuCode("statistic").setMenuType("dir").setTitle("数据统计").setPath("/admin/statistic").setRouteName("statisticRoot").setIcon("Histogram").setHidden(false).setSortNum(4).setIsEnabled(true).setIsDeleted(false));
                menus.add(new Menu().setId(16L).setParentId(21L).setMenuCode("admin-statistic").setMenuType("menu").setTitle("平台统计").setPath("/admin/statistic/platform").setRouteName("adminStatistic").setIcon("Histogram").setHidden(false).setComponentCode("admin-statistic").setSortNum(1).setIsEnabled(true).setIsDeleted(false));
                menus.add(new Menu().setId(9L).setParentId(21L).setMenuCode("admin-project-statistic").setMenuType("menu").setTitle("项目打分统计").setPath("/admin/project/statistic").setRouteName("projectStatisticList").setIcon("DataAnalysis").setHidden(false).setComponentCode("admin-project-statistic").setSortNum(2).setIsEnabled(true).setIsDeleted(false));
                menus.add(new Menu().setId(10L).setParentId(9L).setMenuCode("admin-project-statistic-detail").setMenuType("menu").setTitle("打分统计详情").setPath("/admin/project/statistic/:projectId").setRouteName("projectStatisticDetail").setIcon("DataAnalysis").setHidden(true).setComponentCode("admin-project-statistic-detail").setSortNum(1).setIsEnabled(true).setIsDeleted(false));

                menus.add(new Menu().setId(17L).setParentId(0L).setMenuCode("super-admin").setMenuType("dir").setTitle("后台管理").setPath("/super-admin").setRouteName("/super-admin").setIcon("Grid").setHidden(false).setSortNum(5).setIsEnabled(true).setIsDeleted(false));
                menus.add(new Menu().setId(18L).setParentId(17L).setMenuCode("super-monitor-online").setMenuType("menu").setTitle("用户在线管理").setPath("/super-admin/monitor/online").setRouteName("monitorOnline").setIcon("UserFilled").setHidden(false).setComponentCode("super-monitor-online").setSortNum(3).setIsEnabled(true).setIsDeleted(false));
                menus.add(new Menu().setId(19L).setParentId(17L).setMenuCode("super-monitor-server").setMenuType("menu").setTitle("服务器面板").setPath("/super-admin/monitor/server").setRouteName("monitorServer").setIcon("DataLine").setHidden(false).setComponentCode("super-monitor-server").setSortNum(2).setIsEnabled(true).setIsDeleted(false));
                menus.add(new Menu().setId(23L).setParentId(17L).setMenuCode("super-admin-menu-management").setMenuType("menu").setTitle("菜单管理").setPath("/super-admin/menu").setRouteName("menuManagement").setIcon("Grid").setHidden(false).setComponentCode("super-menu-management").setSortNum(1).setIsEnabled(true).setIsDeleted(false));

                menus.sort(java.util.Comparator.comparing(Menu::getSortNum).thenComparing(Menu::getId));
                return menus;
        }

        private java.util.List<Menu> buildRoutesWithAllAncestors() {
                java.util.List<Menu> menus = new java.util.ArrayList<>();
                menus.add(new Menu().setId(1L).setParentId(0L).setMenuCode("home").setMenuType("menu").setTitle("首页").setPath("/home").setRouteName("home").setIcon("HomeFilled").setHidden(false).setComponentCode("normal-home").setSortNum(1).setIsEnabled(true).setIsDeleted(false));
                menus.add(new Menu().setId(2L).setParentId(0L).setMenuCode("scoring-list").setMenuType("menu").setTitle("打分项目列表").setPath("/scoring").setRouteName("scoringRoot").setIcon("Edit").setHidden(false).setComponentCode("normal-scoring-list").setSortNum(2).setIsEnabled(true).setIsDeleted(false));
                menus.add(new Menu().setId(3L).setParentId(2L).setMenuCode("scoring-project").setMenuType("menu").setTitle("项目打分").setPath("/scoring/:projectId").setRouteName("projectScoring").setIcon("").setHidden(true).setComponentCode("normal-project-scoring").setSortNum(2).setIsEnabled(true).setIsDeleted(false));

                menus.add(new Menu().setId(6L).setParentId(0L).setMenuCode("admin").setMenuType("dir").setTitle("管理面板").setPath("/admin").setRouteName("adminRoot").setIcon("Setting").setHidden(false).setSortNum(3).setIsEnabled(true).setIsDeleted(false));
                menus.add(new Menu().setId(7L).setParentId(6L).setMenuCode("admin-project").setMenuType("menu").setTitle("项目管理").setPath("/admin/project").setRouteName("projectList").setIcon("Management").setHidden(false).setComponentCode("admin-project-list").setSortNum(5).setIsEnabled(true).setIsDeleted(false));
                menus.add(new Menu().setId(20L).setParentId(6L).setMenuCode("admin-project-group").setMenuType("menu").setTitle("项目受审队伍管理").setPath("/admin/project-groups").setRouteName("projectGroupList").setIcon("User").setHidden(false).setComponentCode("admin-project-group").setSortNum(4).setIsEnabled(true).setIsDeleted(false));
                menus.add(new Menu().setId(11L).setParentId(6L).setMenuCode("admin-reviewer-group").setMenuType("menu").setTitle("评审队伍管理").setPath("/admin/reviewer-group").setRouteName("reviewerGroupList").setIcon("UserFilled").setHidden(false).setComponentCode("admin-reviewer-group").setSortNum(1).setIsEnabled(true).setIsDeleted(false));
                menus.add(new Menu().setId(14L).setParentId(6L).setMenuCode("admin-scoring-stds").setMenuType("menu").setTitle("打分标准").setPath("/admin/scoring-stds").setRouteName("scoringStdList").setIcon("Checked").setHidden(false).setComponentCode("admin-scoring-stds").setSortNum(3).setIsEnabled(true).setIsDeleted(false));
                menus.add(new Menu().setId(15L).setParentId(6L).setMenuCode("admin-user").setMenuType("menu").setTitle("用户管理").setPath("/admin/user").setRouteName("userList").setIcon("User").setHidden(false).setComponentCode("admin-user").setSortNum(2).setIsEnabled(true).setIsDeleted(false));

                menus.add(new Menu().setId(21L).setParentId(0L).setMenuCode("statistic").setMenuType("dir").setTitle("数据统计").setPath("/admin/statistic").setRouteName("statisticRoot").setIcon("Histogram").setHidden(false).setSortNum(4).setIsEnabled(true).setIsDeleted(false));
                menus.add(new Menu().setId(16L).setParentId(21L).setMenuCode("admin-statistic").setMenuType("menu").setTitle("平台统计").setPath("/admin/statistic/platform").setRouteName("adminStatistic").setIcon("Histogram").setHidden(false).setComponentCode("admin-statistic").setSortNum(1).setIsEnabled(true).setIsDeleted(false));
                menus.add(new Menu().setId(9L).setParentId(21L).setMenuCode("admin-project-statistic").setMenuType("menu").setTitle("项目打分统计").setPath("/admin/project/statistic").setRouteName("projectStatisticList").setIcon("DataAnalysis").setHidden(false).setComponentCode("admin-project-statistic").setSortNum(2).setIsEnabled(true).setIsDeleted(false));
                menus.add(new Menu().setId(10L).setParentId(9L).setMenuCode("admin-project-statistic-detail").setMenuType("menu").setTitle("打分统计详情").setPath("/admin/project/statistic/:projectId").setRouteName("projectStatisticDetail").setIcon("DataAnalysis").setHidden(true).setComponentCode("admin-project-statistic-detail").setSortNum(1).setIsEnabled(true).setIsDeleted(false));

                menus.add(new Menu().setId(17L).setParentId(0L).setMenuCode("super-admin").setMenuType("dir").setTitle("后台管理").setPath("/super-admin").setRouteName("/super-admin").setIcon("Grid").setHidden(false).setSortNum(5).setIsEnabled(true).setIsDeleted(false));
                menus.add(new Menu().setId(18L).setParentId(17L).setMenuCode("super-monitor-online").setMenuType("menu").setTitle("用户在线管理").setPath("/super-admin/monitor/online").setRouteName("monitorOnline").setIcon("UserFilled").setHidden(false).setComponentCode("super-monitor-online").setSortNum(3).setIsEnabled(true).setIsDeleted(false));
                menus.add(new Menu().setId(19L).setParentId(17L).setMenuCode("super-monitor-server").setMenuType("menu").setTitle("服务器面板").setPath("/super-admin/monitor/server").setRouteName("monitorServer").setIcon("DataLine").setHidden(false).setComponentCode("super-monitor-server").setSortNum(2).setIsEnabled(true).setIsDeleted(false));
                menus.add(new Menu().setId(23L).setParentId(17L).setMenuCode("super-admin-menu-management").setMenuType("menu").setTitle("菜单管理").setPath("/super-admin/menu").setRouteName("menuManagement").setIcon("Grid").setHidden(false).setComponentCode("super-menu-management").setSortNum(1).setIsEnabled(true).setIsDeleted(false));

                menus.sort(java.util.Comparator.comparing(Menu::getSortNum).thenComparing(Menu::getId));
                return menus;
        }
}
