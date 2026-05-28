package com.eval.gameeval.service;

import com.eval.gameeval.mapper.MenuMapper;
import com.eval.gameeval.mapper.UserMapper;
import com.eval.gameeval.models.DTO.Menu.MenuUpsertDTO;
import com.eval.gameeval.models.VO.MenuVO;
import com.eval.gameeval.models.VO.ResponseVO;
import com.eval.gameeval.models.entity.Menu;
import com.eval.gameeval.models.entity.User;
import com.eval.gameeval.service.impl.MenuServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MenuServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private MenuMapper menuMapper;

    @InjectMocks
    private MenuServiceImpl menuService;

    @Test
    void listMenusShouldBuildTreeWithRoles() {
        User currentUser = new User();
        currentUser.setId(1L);
        currentUser.setRole("admin");
        when(userMapper.selectById(1L)).thenReturn(currentUser);
        when(menuMapper.selectAllMenus()).thenReturn(List.of(
                new Menu().setId(1L).setParentId(0L).setMenuCode("home").setMenuType("menu").setTitle("首页").setPath("/home").setRouteName("home").setIcon("HomeFilled").setHidden(false).setComponentCode("normal-home").setSortNum(1).setIsEnabled(true).setIsDeleted(false),
                new Menu().setId(2L).setParentId(0L).setMenuCode("admin").setMenuType("dir").setTitle("管理面板").setPath("/admin").setRouteName("adminRoot").setIcon("Setting").setHidden(false).setSortNum(2).setIsEnabled(true).setIsDeleted(false),
                new Menu().setId(3L).setParentId(2L).setMenuCode("admin-project").setMenuType("menu").setTitle("项目管理").setPath("/admin/project").setRouteName("projectList").setIcon("Management").setHidden(false).setComponentCode("admin-project-list").setSortNum(1).setIsEnabled(true).setIsDeleted(false)
        ));
        when(menuMapper.selectRoleCodesByMenuCode(any())).thenReturn(List.of("admin"));

        ResponseVO<List<MenuVO>> response = menuService.listMenus(1L);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).hasSize(2);
        MenuVO admin = response.getData().stream().filter(item -> "admin".equals(item.getMenuCode())).findFirst().orElseThrow();
        assertThat(admin.getChildren()).extracting(MenuVO::getMenuCode).contains("admin-project");
    }

    @Test
    void createMenuShouldReturnCreatedMenu() {
        User currentUser = new User();
        currentUser.setId(1L);
        currentUser.setRole("super_admin");
        when(userMapper.selectById(1L)).thenReturn(currentUser);
        when(menuMapper.selectByRouteName("userList")).thenReturn(null);
        when(menuMapper.insertMenu(any(Menu.class))).thenReturn(1);
        when(menuMapper.selectByMenuCode("admin-user")).thenReturn(
            null,
            new Menu().setId(9L).setParentId(0L).setMenuCode("admin-user").setMenuType("menu").setTitle("用户管理").setPath("/admin/user").setRouteName("userList").setIcon("User").setHidden(false).setComponentCode("admin-user").setSortNum(1).setIsEnabled(true).setIsDeleted(false)
        );
        when(menuMapper.selectRoleCodesByMenuCode("admin-user")).thenReturn(List.of("admin", "super_admin"));

        MenuUpsertDTO request = new MenuUpsertDTO()
                .setMenuCode("admin-user")
                .setMenuType("menu")
                .setTitle("用户管理")
                .setPath("/admin/user")
                .setRouteName("userList")
                .setIcon("User")
                .setHidden(false)
                .setComponentCode("admin-user")
                .setSortNum(1)
                .setIsEnabled(true)
                .setRoleCodes(List.of("admin", "super_admin"));

        ResponseVO<MenuVO> response = menuService.createMenu(1L, request);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData().getMenuCode()).isEqualTo("admin-user");
        assertThat(response.getData().getRoleCodes()).containsExactly("admin", "super_admin");
    }
}