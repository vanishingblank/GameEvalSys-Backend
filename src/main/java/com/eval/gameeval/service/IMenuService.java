package com.eval.gameeval.service;

import com.eval.gameeval.models.DTO.Menu.MenuUpsertDTO;
import com.eval.gameeval.models.VO.MenuVO;
import com.eval.gameeval.models.VO.ResponseVO;

import java.util.List;

public interface IMenuService {
    ResponseVO<List<MenuVO>> listMenus(Long currentUserId);

    ResponseVO<MenuVO> getMenu(Long currentUserId, Long id);

    ResponseVO<MenuVO> createMenu(Long currentUserId, MenuUpsertDTO request);

    ResponseVO<Void> updateMenu(Long currentUserId, Long id, MenuUpsertDTO request);

    ResponseVO<Void> deleteMenu(Long currentUserId, Long id);
}