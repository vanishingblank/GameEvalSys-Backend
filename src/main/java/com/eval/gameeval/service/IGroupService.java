package com.eval.gameeval.service;

import com.eval.gameeval.models.DTO.Group.GroupAddToProjectDTO;
import com.eval.gameeval.models.DTO.Group.GroupCreateDTO;
import com.eval.gameeval.models.DTO.Group.GroupQueryDTO;
import com.eval.gameeval.models.DTO.Group.GroupUpdateDTO;
import com.eval.gameeval.models.VO.GroupPageVO;
import com.eval.gameeval.models.VO.GroupOverviewVO;
import com.eval.gameeval.models.VO.GroupVO;
import com.eval.gameeval.models.VO.ResponseVO;

import java.util.List;


public interface IGroupService {

    /**
     * 创建小组（仅包含基本信息，不关联项目）
     * @param token 认证Token
     * @param request 创建请求
     * @return 小组详情
     */
    ResponseVO<GroupVO> createGroup(String token, GroupCreateDTO request);

    /**
     * 将小组加入项目
     * @param token 认证Token
     * @param request 请求参数（包含groupId和projectId）
     * @return 小组详情
     */
    ResponseVO<GroupVO> addGroupToProject(String token, GroupAddToProjectDTO request);

    /**
     * 编辑小组信息
     * @param token 认证Token
     * @param request 编辑请求
     * @return 编辑后的小组详情
     */
    ResponseVO<GroupVO> updateGroup(String token, GroupUpdateDTO request);

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

    ResponseVO<GroupOverviewVO> getGroupOverview(String token);
}