package com.eval.gameeval.service.impl;

import com.eval.gameeval.mapper.MenuMapper;
import com.eval.gameeval.mapper.UserMapper;
import com.eval.gameeval.models.DTO.Menu.MenuUpsertDTO;
import com.eval.gameeval.models.VO.MenuSqlVO;
import com.eval.gameeval.models.VO.MenuVO;
import com.eval.gameeval.models.VO.ResponseVO;
import com.eval.gameeval.models.entity.Menu;
import com.eval.gameeval.models.entity.RoleMenu;
import com.eval.gameeval.models.entity.User;
import com.eval.gameeval.service.IMenuService;
import com.eval.gameeval.util.MenuRouteCacheUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MenuServiceImpl implements IMenuService {

    @Resource
    private UserMapper userMapper;

    @Resource
    private MenuMapper menuMapper;

    @Resource
    private MenuRouteCacheUtil menuRouteCacheUtil;

    @Override
    public ResponseVO<List<MenuVO>> listMenus(Long currentUserId) {
        try {
            User currentUser = getCurrentUser(currentUserId);
            if (!isAdmin(currentUser)) {
                return ResponseVO.forbidden("权限不足");
            }
            List<Menu> menus = menuMapper.selectAllMenus();
            return ResponseVO.success("查询成功", buildTreeWithRoles(menus));
        } catch (Exception e) {
            log.error("查询菜单树异常", e);
            return ResponseVO.error("查询失败");
        }
    }

    @Override
    public ResponseVO<MenuVO> getMenu(Long currentUserId, Long id) {
        try {
            User currentUser = getCurrentUser(currentUserId);
            if (!isAdmin(currentUser)) {
                return ResponseVO.forbidden("权限不足");
            }
            if (id == null) {
                return ResponseVO.badRequest("菜单ID不能为空");
            }
            Menu menu = menuMapper.selectById(id);
            if (menu == null) {
                return ResponseVO.notFound("菜单不存在");
            }
            return ResponseVO.success("查询成功", enrichMenuVO(menu));
        } catch (Exception e) {
            log.error("查询菜单详情异常", e);
            return ResponseVO.error("查询失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseVO<MenuSqlVO> createMenu(Long currentUserId, MenuUpsertDTO request) {
        try {
            User currentUser = getCurrentUser(currentUserId);
            if (!isAdmin(currentUser)) {
                return ResponseVO.forbidden("权限不足");
            }
            String validationError = validateRequest(request, null);
            if (validationError != null) {
                return ResponseVO.badRequest(validationError);
            }
            if (menuMapper.selectByMenuCode(request.getMenuCode()) != null) {
                return ResponseVO.badRequest("菜单编码已存在");
            }
            if (menuMapper.selectByRouteName(request.getRouteName()) != null) {
                return ResponseVO.badRequest("路由名称已存在");
            }

            LocalDateTime now = LocalDateTime.now();
            Menu menu = toMenuEntity(request, now);
            menu.setIsDeleted(false);
            menuMapper.insertMenu(menu);
            replaceRoleBindings(menu.getMenuCode(), request.getRoleCodes());
            invalidateMenuRoutesCache();

            Menu created = menuMapper.selectByMenuCode(menu.getMenuCode());
            return ResponseVO.success("创建成功", buildMenuSqlVO(created, request.getRoleCodes(), true));
        } catch (Exception e) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            log.error("创建菜单异常", e);
            return ResponseVO.error("创建失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseVO<MenuSqlVO> updateMenu(Long currentUserId, Long id, MenuUpsertDTO request) {
        try {
            User currentUser = getCurrentUser(currentUserId);
            if (!isAdmin(currentUser)) {
                return ResponseVO.forbidden("权限不足");
            }
            if (id == null) {
                return ResponseVO.badRequest("菜单ID不能为空");
            }
            String validationError = validateRequest(request, id);
            if (validationError != null) {
                return ResponseVO.badRequest(validationError);
            }

            Menu existing = menuMapper.selectById(id);
            if (existing == null) {
                return ResponseVO.notFound("菜单不存在");
            }
            if (!Objects.equals(existing.getMenuCode(), request.getMenuCode())) {
                return ResponseVO.badRequest("菜单编码不可修改");
            }
            Menu duplicateCode = menuMapper.selectByMenuCode(request.getMenuCode());
            if (duplicateCode != null && !Objects.equals(duplicateCode.getId(), id)) {
                return ResponseVO.badRequest("菜单编码已存在");
            }
            Menu duplicateRoute = menuMapper.selectByRouteName(request.getRouteName());
            if (duplicateRoute != null && !Objects.equals(duplicateRoute.getId(), id)) {
                return ResponseVO.badRequest("路由名称已存在");
            }

            Menu menu = toMenuEntity(request, LocalDateTime.now());
            menu.setId(id);
            menu.setIsDeleted(existing.getIsDeleted());
            menuMapper.updateMenu(menu);
            replaceRoleBindings(menu.getMenuCode(), request.getRoleCodes());
            invalidateMenuRoutesCache();

            Menu updated = menuMapper.selectById(id);
            return ResponseVO.success("更新成功", buildMenuSqlVO(updated, request.getRoleCodes(), false));
        } catch (Exception e) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            log.error("更新菜单异常", e);
            return ResponseVO.error("更新失败");
        }
    }

    @Override
    public ResponseVO<MenuSqlVO> getMenuSql(Long currentUserId) {
        try {
            User currentUser = getCurrentUser(currentUserId);
            if (!isAdmin(currentUser)) {
                return ResponseVO.forbidden("权限不足");
            }

            List<Menu> menus = menuMapper.selectAllMenus();
            List<RoleMenu> roleMenus = menuMapper.selectAllRoleMenus();
            return ResponseVO.success("查询成功", buildAllMenuSqlVO(menus, roleMenus));
        } catch (Exception e) {
            log.error("查询全部菜单SQL异常", e);
            return ResponseVO.error("查询失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseVO<Void> deleteMenu(Long currentUserId, Long id) {
        try {
            User currentUser = getCurrentUser(currentUserId);
            if (!isAdmin(currentUser)) {
                return ResponseVO.forbidden("权限不足");
            }
            if (id == null) {
                return ResponseVO.badRequest("菜单ID不能为空");
            }

            Menu root = menuMapper.selectById(id);
            if (root == null) {
                return ResponseVO.notFound("菜单不存在");
            }

            List<Menu> allMenus = menuMapper.selectAllMenus();
            List<Long> idsToDelete = collectDescendantIds(id, allMenus);
            List<String> menuCodesToDelete = allMenus.stream()
                    .filter(menu -> idsToDelete.contains(menu.getId()))
                    .map(Menu::getMenuCode)
                    .filter(code -> code != null && !code.trim().isEmpty())
                    .toList();

            menuMapper.deleteRoleMenusByMenuCodes(menuCodesToDelete);
            menuMapper.softDeleteMenuByIds(idsToDelete, LocalDateTime.now());
            invalidateMenuRoutesCache();

            return ResponseVO.success("删除成功", null);
        } catch (Exception e) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            log.error("删除菜单异常", e);
            return ResponseVO.error("删除失败");
        }
    }

    private User getCurrentUser(Long currentUserId) {
        if (currentUserId == null) {
            return null;
        }
        return userMapper.selectById(currentUserId);
    }

    private boolean isAdmin(User user) {
        return user != null && ("super_admin".equals(user.getRole()) || "admin".equals(user.getRole()));
    }

    private String validateRequest(MenuUpsertDTO request, Long currentId) {
        if (request == null) {
            return "请求不能为空";
        }
        if (request.getParentId() == null) {
            request.setParentId(0L);
        }
        if (request.getMenuType() == null) {
            return "菜单类型不能为空";
        }
        if ("menu".equalsIgnoreCase(request.getMenuType()) && (request.getComponentCode() == null || request.getComponentCode().trim().isEmpty())) {
            return "菜单类型为menu时，componentCode不能为空";
        }
        if (currentId != null && Objects.equals(request.getParentId(), currentId)) {
            return "父级菜单不能是自身";
        }
        if (request.getParentId() != null && request.getParentId() != 0L && menuMapper.selectById(request.getParentId()) == null) {
            return "父级菜单不存在";
        }
        return null;
    }

    private Menu toMenuEntity(MenuUpsertDTO request, LocalDateTime now) {
        return new Menu()
                .setParentId(request.getParentId() == null ? 0L : request.getParentId())
                .setMenuCode(request.getMenuCode().trim())
                .setMenuType(request.getMenuType().trim())
                .setTitle(request.getTitle().trim())
                .setPath(request.getPath().trim())
                .setRouteName(request.getRouteName().trim())
                .setIcon(request.getIcon() == null ? "" : request.getIcon().trim())
                .setHidden(Boolean.TRUE.equals(request.getHidden()))
                .setComponentCode(request.getComponentCode() == null ? "" : request.getComponentCode().trim())
                .setSortNum(request.getSortNum() == null ? 0 : request.getSortNum())
                .setIsEnabled(request.getIsEnabled() == null || request.getIsEnabled())
                .setIsDeleted(false)
                .setCreateTime(now)
                .setUpdateTime(now);
    }

    private void replaceRoleBindings(String menuCode, List<String> roleCodes) {
        menuMapper.deleteRoleMenusByMenuCode(menuCode);
        if (roleCodes == null || roleCodes.isEmpty()) {
            return;
        }
        List<String> normalized = roleCodes.stream()
                .filter(code -> code != null && !code.trim().isEmpty())
                .map(String::trim)
                .distinct()
                .collect(Collectors.toList());
        if (!normalized.isEmpty()) {
            menuMapper.batchInsertRoleMenus(menuCode, normalized);
        }
    }

    private void invalidateMenuRoutesCache() {
        try {
            Long version = menuRouteCacheUtil.bumpMenuRoutesVersion();
            log.info("菜单路由缓存版本已更新: version={}", version);
        } catch (Exception e) {
            log.warn("菜单路由缓存版本更新失败，缓存将依赖TTL兜底", e);
        }
    }

    private MenuSqlVO buildMenuSqlVO(Menu menu, List<String> roleCodes, boolean isCreate) {
        MenuSqlVO result = new MenuSqlVO();
        if (menu == null) {
            return result;
        }

        String sql = isCreate
                ? buildCreateMenuSql(menu, roleCodes)
                : buildUpdateMenuSql(menu, roleCodes);
        result.setId(menu.getId())
                .setSql(sql)
                .setFullSql(sql);
        return result;
    }

    private MenuSqlVO buildAllMenuSqlVO(List<Menu> menus, List<RoleMenu> roleMenus) {
        MenuSqlVO result = new MenuSqlVO();
        int menuCount = menus == null ? 0 : menus.size();
        String sql = buildAllMenuBackupSql(menus, roleMenus);
        result.setMenuCount(menuCount)
                .setGeneratedAt(LocalDateTime.now())
                .setSql(sql)
                .setFullSql(sql);
        return result;
    }

    private String buildCreateMenuSql(Menu menu, List<String> roleCodes) {
        List<String> statements = new ArrayList<>();
        statements.add("-- 自动生成的菜单保存 SQL");
        statements.add("START TRANSACTION;");
        statements.add(buildMenuInsertSql(menu));
        String roleInsertSql = buildRoleMenuInsertSql(menu.getMenuCode(), roleCodes, true);
        if (!roleInsertSql.isBlank()) {
            statements.add(roleInsertSql);
        }
        statements.add("COMMIT;");
        return String.join("\n\n", statements);
    }

    private String buildUpdateMenuSql(Menu menu, List<String> roleCodes) {
        List<String> statements = new ArrayList<>();
        statements.add("-- 自动生成的菜单保存 SQL");
        statements.add("START TRANSACTION;");
        statements.add(buildMenuUpdateSql(menu));
        statements.add(buildRoleMenuDeleteSql(menu.getMenuCode()));
        String roleInsertSql = buildRoleMenuInsertSql(menu.getMenuCode(), roleCodes, true);
        if (!roleInsertSql.isBlank()) {
            statements.add(roleInsertSql);
        }
        statements.add("COMMIT;");
        return String.join("\n\n", statements);
    }

    private String buildAllMenuBackupSql(List<Menu> menus, List<RoleMenu> roleMenus) {
        List<String> statements = new ArrayList<>();
        statements.add("-- 自动生成的全部菜单备份 SQL");
        statements.add("START TRANSACTION;");
        String menuInsertSql = buildBulkMenuInsertSql(menus);
        if (!menuInsertSql.isBlank()) {
            statements.add(menuInsertSql);
        }
        String roleInsertSql = buildBulkRoleMenuInsertSql(roleMenus);
        if (!roleInsertSql.isBlank()) {
            statements.add(roleInsertSql);
        }
        statements.add("COMMIT;");
        return String.join("\n\n", statements);
    }

    private String buildMenuInsertSql(Menu menu) {
        if (menu == null) {
            return "";
        }
        return "INSERT INTO sys_menu "
                + "(id, parent_id, menu_code, menu_type, title, path, route_name, icon, hidden, component_code, sort_num, is_enabled, is_deleted, create_time, update_time) VALUES "
                + "(" + formatSqlValue(menu.getId()) + ", "
                + formatSqlValue(menu.getParentId()) + ", "
                + formatSqlValue(menu.getMenuCode()) + ", "
                + formatSqlValue(menu.getMenuType()) + ", "
                + formatSqlValue(menu.getTitle()) + ", "
                + formatSqlValue(menu.getPath()) + ", "
                + formatSqlValue(menu.getRouteName()) + ", "
                + formatSqlValue(menu.getIcon()) + ", "
                + formatSqlValue(menu.getHidden()) + ", "
                + formatSqlValue(menu.getComponentCode()) + ", "
                + formatSqlValue(menu.getSortNum()) + ", "
                + formatSqlValue(menu.getIsEnabled()) + ", "
                + formatSqlValue(menu.getIsDeleted()) + ", "
                + formatSqlValue(menu.getCreateTime()) + ", "
                + formatSqlValue(menu.getUpdateTime()) + ");";
    }

    private String buildBulkMenuInsertSql(List<Menu> menus) {
        if (menus == null || menus.isEmpty()) {
            return "";
        }

        String values = menus.stream()
                .filter(Objects::nonNull)
                .map(menu -> "("
                        + formatSqlValue(menu.getId()) + ", "
                        + formatSqlValue(menu.getParentId()) + ", "
                        + formatSqlValue(menu.getMenuCode()) + ", "
                        + formatSqlValue(menu.getMenuType()) + ", "
                        + formatSqlValue(menu.getTitle()) + ", "
                        + formatSqlValue(menu.getPath()) + ", "
                        + formatSqlValue(menu.getRouteName()) + ", "
                        + formatSqlValue(menu.getIcon()) + ", "
                        + formatSqlValue(menu.getHidden()) + ", "
                        + formatSqlValue(menu.getComponentCode()) + ", "
                        + formatSqlValue(menu.getSortNum()) + ", "
                        + formatSqlValue(menu.getIsEnabled()) + ", "
                        + formatSqlValue(menu.getIsDeleted()) + ", "
                        + formatSqlValue(menu.getCreateTime()) + ", "
                        + formatSqlValue(menu.getUpdateTime()) + ")")
                .collect(Collectors.joining(",\n"));

        if (values.isBlank()) {
            return "";
        }

        return "INSERT INTO sys_menu "
                + "(id, parent_id, menu_code, menu_type, title, path, route_name, icon, hidden, component_code, sort_num, is_enabled, is_deleted, create_time, update_time) VALUES\n"
                + values
                + ";";
    }

    private String buildRoleMenuDeleteSql(String menuCode) {
        return "DELETE FROM sys_role_menu WHERE menu_code = " + formatSqlValue(menuCode) + ";";
    }

    private String buildRoleMenuInsertSql(String menuCode, List<String> roleCodes, boolean useNow) {
        List<String> normalized = normalizeRoleCodes(roleCodes);
        if (normalized.isEmpty()) {
            return "";
        }

        String values = normalized.stream()
                .map(roleCode -> useNow
                        ? "(" + formatSqlValue(roleCode) + ", " + formatSqlValue(menuCode) + ", NOW())"
                        : "(" + formatSqlValue(roleCode) + ", " + formatSqlValue(menuCode) + ", " + formatSqlValue(LocalDateTime.now()) + ")")
                .collect(Collectors.joining(",\n"));

        return "INSERT INTO sys_role_menu (role_code, menu_code, create_time) VALUES\n" + values + ";";
    }

    private String buildBulkRoleMenuInsertSql(List<RoleMenu> roleMenus) {
        if (roleMenus == null || roleMenus.isEmpty()) {
            return "";
        }

        String values = roleMenus.stream()
                .filter(Objects::nonNull)
                .map(roleMenu -> "("
                        + formatSqlValue(roleMenu.getRoleCode()) + ", "
                        + formatSqlValue(roleMenu.getMenuCode()) + ", "
                        + formatSqlValue(roleMenu.getCreateTime()) + ")")
                .collect(Collectors.joining(",\n"));

        if (values.isBlank()) {
            return "";
        }

        return "INSERT INTO sys_role_menu (role_code, menu_code, create_time) VALUES\n" + values + ";";
    }

    private String buildMenuUpdateSql(Menu menu) {
        if (menu == null) {
            return "";
        }
        return "UPDATE sys_menu SET "
                + "parent_id = " + formatSqlValue(menu.getParentId()) + ", "
                + "menu_type = " + formatSqlValue(menu.getMenuType()) + ", "
                + "title = " + formatSqlValue(menu.getTitle()) + ", "
                + "path = " + formatSqlValue(menu.getPath()) + ", "
                + "route_name = " + formatSqlValue(menu.getRouteName()) + ", "
                + "icon = " + formatSqlValue(menu.getIcon()) + ", "
                + "hidden = " + formatSqlValue(menu.getHidden()) + ", "
                + "component_code = " + formatSqlValue(menu.getComponentCode()) + ", "
                + "sort_num = " + formatSqlValue(menu.getSortNum()) + ", "
                + "is_enabled = " + formatSqlValue(menu.getIsEnabled()) + ", "
                + "update_time = " + formatSqlValue(menu.getUpdateTime())
                + " WHERE id = " + formatSqlValue(menu.getId()) + " AND is_deleted = 0;";
    }

    private List<String> normalizeRoleCodes(List<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return List.of();
        }

        return roleCodes.stream()
                .filter(code -> code != null && !code.trim().isEmpty())
                .map(String::trim)
                .distinct()
                .collect(Collectors.toList());
    }

    private String formatSqlValue(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Number || value instanceof Boolean) {
            if (value instanceof Boolean bool) {
                return bool ? "1" : "0";
            }
            return value.toString();
        }
        if (value instanceof LocalDateTime localDateTime) {
            return "'" + localDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "'";
        }
        String text = String.valueOf(value).replace("'", "''");
        return "'" + text + "'";
    }

    private List<Long> collectDescendantIds(Long rootId, List<Menu> allMenus) {
        Map<Long, List<Long>> childrenMap = new LinkedHashMap<>();
        for (Menu menu : allMenus) {
            if (menu == null || menu.getId() == null) {
                continue;
            }
            Long parentId = menu.getParentId() == null ? 0L : menu.getParentId();
            childrenMap.computeIfAbsent(parentId, key -> new ArrayList<>()).add(menu.getId());
        }

        List<Long> ids = new ArrayList<>();
        collectRecursive(rootId, childrenMap, ids);
        return ids;
    }

    private void collectRecursive(Long currentId, Map<Long, List<Long>> childrenMap, List<Long> ids) {
        if (currentId == null || ids.contains(currentId)) {
            return;
        }
        ids.add(currentId);
        List<Long> children = childrenMap.get(currentId);
        if (children == null) {
            return;
        }
        for (Long childId : children) {
            collectRecursive(childId, childrenMap, ids);
        }
    }

    private List<MenuVO> buildTreeWithRoles(List<Menu> menus) {
        if (menus == null || menus.isEmpty()) {
            return List.of();
        }

        Map<Long, MenuVO> nodeMap = new LinkedHashMap<>();
        for (Menu menu : menus) {
            if (menu == null || menu.getId() == null) {
                continue;
            }
            nodeMap.put(menu.getId(), enrichMenuVO(menu));
        }

        List<MenuVO> roots = new ArrayList<>();
        for (Menu menu : menus) {
            if (menu == null || menu.getId() == null) {
                continue;
            }
            MenuVO current = nodeMap.get(menu.getId());
            if (current == null) {
                continue;
            }
            Long parentId = menu.getParentId();
            if (parentId == null || parentId == 0L || !nodeMap.containsKey(parentId)) {
                roots.add(current);
            } else {
                MenuVO parent = nodeMap.get(parentId);
                parent.getChildren().add(current);
            }
        }

        return roots;
    }

    private MenuVO enrichMenuVO(Menu menu) {
        if (menu == null) {
            return null;
        }
        return new MenuVO()
                .setId(menu.getId())
                .setParentId(menu.getParentId())
                .setMenuCode(menu.getMenuCode())
                .setMenuType(menu.getMenuType())
                .setTitle(menu.getTitle())
                .setPath(menu.getPath())
                .setRouteName(menu.getRouteName())
                .setIcon(menu.getIcon())
                .setHidden(menu.getHidden())
                .setComponentCode(menu.getComponentCode())
                .setSortNum(menu.getSortNum())
                .setIsEnabled(menu.getIsEnabled())
                .setIsDeleted(menu.getIsDeleted())
                .setRoleCodes(menuMapper.selectRoleCodesByMenuCode(menu.getMenuCode()))
                .setChildren(new ArrayList<>());
    }
}
