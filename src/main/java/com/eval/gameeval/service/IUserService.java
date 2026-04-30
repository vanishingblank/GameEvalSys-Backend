package com.eval.gameeval.service;

import com.eval.gameeval.models.DTO.User.*;
import com.eval.gameeval.models.VO.*;
import jakarta.validation.Valid;

import java.util.List;

public interface IUserService {


    /**
     * 批量创建用户
     * @param currentUserId 当前登录用户ID
     * @param request 创建用户请求
     * @return 创建成功的用户列表
     */
    ResponseVO<List<UserWithGroupVO>> createUsers(Long currentUserId, @Valid UserCreateDTO request);

    ResponseVO<Void> updateUser(Long currentUserId, Long userId, @Valid UserUpdateDTO request);

    ResponseVO<Void> updateSelfPassword(Long currentUserId, @Valid UserPasswordUpdateDTO request);

    ResponseVO<Void> deleteUser(Long currentUserId, Long userId);

    ResponseVO<UserBatchOperationResultVO> batchUpdateUserStatus(Long currentUserId, @Valid UserBatchStatusDTO request);

    ResponseVO<UserBatchOperationResultVO> batchDeleteUsers(Long currentUserId, @Valid UserBatchDeleteDTO request);

    /**
     * 分页查询用户列表
     * @param currentUserId 当前登录用户ID
     * @param query 查询参数
     * @return 分页用户列表
     */
    ResponseVO<UserPageVO> getUserList(Long currentUserId, UserQueryDTO query);

    ResponseVO<UserOverviewVO> getUserOverview(Long currentUserId);

    /**
     * 批量查询用户详细信息
     * @param currentUserId 当前登录用户ID
     * @param request 批量查询请求
     * @return 用户详细信息列表
     */
    ResponseVO<List<UserDetailVO>> batchQueryUsers(Long currentUserId, UserBatchQueryDTO request);
}
