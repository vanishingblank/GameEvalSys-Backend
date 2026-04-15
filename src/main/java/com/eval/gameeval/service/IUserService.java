package com.eval.gameeval.service;

import com.eval.gameeval.models.DTO.User.*;
import com.eval.gameeval.models.VO.*;
import jakarta.validation.Valid;

import java.util.List;

public interface IUserService {


    /**
     * 批量创建用户
     * @param token 认证Token
     * @param request 创建用户请求
     * @return 创建成功的用户列表
     */
    ResponseVO<List<UserWithGroupVO>> createUsers(String token, @Valid UserCreateDTO request);

    ResponseVO<Void> updateUser(String token, Long userId, @Valid UserUpdateDTO request);

    ResponseVO<Void> updateSelfPassword(String token, @Valid UserPasswordUpdateDTO request);

    ResponseVO<Void> deleteUser(String token, Long userId);

    ResponseVO<UserBatchOperationResultVO> batchUpdateUserStatus(String token, @Valid UserBatchStatusDTO request);

    ResponseVO<UserBatchOperationResultVO> batchDeleteUsers(String token, @Valid UserBatchDeleteDTO request);

    /**
     * 分页查询用户列表
     * @param token 认证Token
     * @param query 查询参数
     * @return 分页用户列表
     */
    ResponseVO<UserPageVO> getUserList(String token, UserQueryDTO query);
    /**
     * 批量查询用户详细信息
     * @param token 认证Token
     * @param request 批量查询请求
     * @return 用户详细信息列表
     */
    ResponseVO<List<UserDetailVO>> batchQueryUsers(String token, UserBatchQueryDTO request);
}
