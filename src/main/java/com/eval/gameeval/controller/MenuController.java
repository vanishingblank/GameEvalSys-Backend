package com.eval.gameeval.controller;

import com.eval.gameeval.aspect.LogRecord;
import com.eval.gameeval.models.DTO.Menu.MenuUpsertDTO;
import com.eval.gameeval.models.VO.MenuVO;
import com.eval.gameeval.models.VO.ResponseVO;
import com.eval.gameeval.security.CurrentUserContext;
import com.eval.gameeval.service.IMenuService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/menus")
public class MenuController {

    @Resource
    private CurrentUserContext currentUserContext;

    @Resource
    private IMenuService menuService;

    @GetMapping
    @LogRecord(value = "查询菜单树", module = "Menu")
    public ResponseEntity<ResponseVO<List<MenuVO>>> listMenus() {
        Long currentUserId = currentUserContext.getCurrentUserId();
        ResponseVO<List<MenuVO>> response = menuService.listMenus(currentUserId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @LogRecord(value = "查询菜单详情", module = "Menu")
    public ResponseEntity<ResponseVO<MenuVO>> getMenu(@PathVariable Long id) {
        Long currentUserId = currentUserContext.getCurrentUserId();
        ResponseVO<MenuVO> response = menuService.getMenu(currentUserId, id);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    @LogRecord(value = "创建菜单", module = "Menu")
    public ResponseEntity<ResponseVO<MenuVO>> createMenu(@Valid @RequestBody MenuUpsertDTO request) {
        Long currentUserId = currentUserContext.getCurrentUserId();
        ResponseVO<MenuVO> response = menuService.createMenu(currentUserId, request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    @LogRecord(value = "更新菜单", module = "Menu")
    public ResponseEntity<ResponseVO<Void>> updateMenu(@PathVariable Long id, @Valid @RequestBody MenuUpsertDTO request) {
        Long currentUserId = currentUserContext.getCurrentUserId();
        ResponseVO<Void> response = menuService.updateMenu(currentUserId, id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @LogRecord(value = "删除菜单", module = "Menu")
    public ResponseEntity<ResponseVO<Void>> deleteMenu(@PathVariable Long id) {
        Long currentUserId = currentUserContext.getCurrentUserId();
        ResponseVO<Void> response = menuService.deleteMenu(currentUserId, id);
        return ResponseEntity.ok(response);
    }
}