package com.eval.gameeval.service;

import com.eval.gameeval.models.DTO.GroupCreateDTO;
import com.eval.gameeval.models.DTO.GroupQueryDTO;
import com.eval.gameeval.models.VO.GroupPageVO;
import com.eval.gameeval.models.VO.GroupVO;
import com.eval.gameeval.models.VO.ResponseVO;

import java.util.List;


public interface IGroupService {

    /**
     * 创建小组
     * @param token 认证Token
     * @param request 创建请求
     * @return 小组详情
     */
    ResponseVO<GroupVO> createGroup(String token, GroupCreateDTO request);

    /**
     * 获取项目关联的小组列表
     * @param token 认证Token
     * @param projectId 项目ID
     * @return 小组列表
     */
    ResponseVO<List<GroupVO>> getProjectGroups(String token, Long projectId);

    /**
     * 查询所有被打分组（小组）列表
     * @param token 认证Token
     * @param query 查询参数
     * @return 小组分页列表
     */
    ResponseVO<GroupPageVO> getAllGroups(String token, GroupQueryDTO query);
}