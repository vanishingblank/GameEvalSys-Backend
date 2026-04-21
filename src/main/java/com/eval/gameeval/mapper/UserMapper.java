package com.eval.gameeval.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eval.gameeval.models.entity.User;
import org.apache.ibatis.annotations.*;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
@Repository
public interface UserMapper extends BaseMapper<User> {
    /**
     * 根据用户名查询用户（只查询启用的用户）
     *
     * @param username 用户名
     * @return 用户信息
     */
    @Select("SELECT * FROM sys_user WHERE username = #{username} AND is_enabled = 1 AND is_deleted = 0 LIMIT 1")
    User selectByUsername(@Param("username") String username);
    /**
     * 检查用户名是否存在
     */
    @Select("SELECT COUNT(*) FROM sys_user WHERE username = #{username} AND is_deleted = 0 AND delete_token = 0")
    Integer countByUsername(@Param("username") String username);

    @Insert("INSERT INTO sys_user(username, password, name, role, is_enabled, is_deleted, delete_token, deleted_time, create_time, update_time) " +
            "VALUES(#{username}, #{password}, #{name}, #{role}, #{isEnabled}, 0, 0, NULL, #{createTime}, #{updateTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertUser(User user);


    @Select("SELECT * FROM sys_user WHERE id = #{id} AND is_deleted = 0")
    User selectById(@Param("id") Long id);



    @Update("UPDATE sys_user " +
            "SET is_deleted = 1, is_enabled = 0, deleted_time = #{updateTime}, delete_token = id, update_time = #{updateTime} " +
            "WHERE id = #{id} AND is_deleted = 0")
    int deleteById(@Param("id") Long id, @Param("updateTime") LocalDateTime updateTime);

    @Update("UPDATE sys_user " +
            "SET name = #{name}, " +
            "    password = #{password}, " +
            "    role = #{role}, " +
            "    is_enabled = #{isEnabled}, " +
            "    update_time = #{updateTime} " +
            "WHERE id = #{id} AND is_deleted = 0")
    int updateById(User user);

    @Update("UPDATE sys_user SET password = #{password}, update_time = #{updateTime} WHERE id = #{id} AND is_deleted = 0")
    int updatePasswordById(@Param("id") Long id,
                           @Param("password") String password,
                           @Param("updateTime") LocalDateTime updateTime);


    @Update("UPDATE sys_user SET is_enabled = 0, update_time = #{updateTime} WHERE id = #{id} AND is_deleted = 0")
    int disableById(@Param("id") Long id, @Param("updateTime") LocalDateTime updateTime);

    @Update("UPDATE sys_user " +
            "SET is_deleted = 1, is_enabled = 0, deleted_time = #{updateTime}, delete_token = id, update_time = #{updateTime} " +
            "WHERE id = #{id} AND is_deleted = 0")
    int softDeleteById(@Param("id") Long id, @Param("updateTime") LocalDateTime updateTime);


    /**
     * 分页查询用户（带角色筛选）
     */
    @Select("<script>" +
            "SELECT id, username, password, name, role, is_enabled AS isEnabled, " +
            "       create_time AS createTime, update_time AS updateTime " +
            "FROM sys_user " +
            "WHERE is_enabled = 1 AND is_deleted = 0 " +
            "<if test='role != null and role != \"\"'>" +
            "  AND role = #{role} " +
            "</if>" +
            "<if test='keyWords != null and keyWords != \"\"'>" +
            "  AND (username LIKE CONCAT('%', #{keyWords}, '%') " +
            "       OR name LIKE CONCAT('%', #{keyWords}, '%')) " +
            "</if>" +
            "ORDER BY create_time DESC " +
            "LIMIT #{offset}, #{limit}" +
            "</script>")
    List<User> selectPage(
            @Param("offset") int offset,
            @Param("limit") int limit,
            @Param("role") String role,
            @Param("keyWords") String keyWords
    );

    @Select("<script>" +
            "SELECT " +
            "  u.id, " +
            "  u.username, " +
            "  u.password, " +
            "  u.name, " +
            "  u.role, " +
            "  u.is_enabled AS isEnabled, " +
            "  u.create_time AS createTime, " +
            "  u.update_time AS updateTime, " +
            "  GROUP_CONCAT(rgm.group_id) AS reviewerGroupIds " +
            "FROM sys_user u " +
            "LEFT JOIN reviewer_group_member rgm ON u.id = rgm.user_id " +
            "WHERE 1 = 1 " +
            "AND u.is_deleted = 0 " +
            "<choose>" +
            "  <when test='isEnabled != null'>" +
            "    AND u.is_enabled = #{isEnabled} " +
            "  </when>" +
            "  <otherwise>" +
            "    AND u.is_enabled = 1 " +
            "  </otherwise>" +
            "</choose>" +
            "<if test='role != null and role != \"\"'>" +
            "  AND u.role = #{role} " +
            "</if>" +
            "<if test='keyWords != null and keyWords != \"\"'>" +
            "  AND (u.username LIKE CONCAT('%', #{keyWords}, '%') " +
            "       OR u.name LIKE CONCAT('%', #{keyWords}, '%')) " +
            "</if>" +
            "GROUP BY u.id " +
            "ORDER BY u.create_time DESC " +
            "LIMIT #{offset}, #{limit}" +
            "</script>")
            List<Map<String, Object>> selectPageWithGroups(
                                                     @Param("offset") int offset,
                                                     @Param("limit") int limit,
                                                     @Param("role") String role,
                                                     @Param("keyWords") String keyWords,
                                                     @Param("isEnabled") Boolean isEnabled
            );

    /**
     * 统计用户总数（带角色筛选）
     */
    @Select("<script>" +
            "SELECT COUNT(*) FROM sys_user " +
            "WHERE 1 = 1 " +
            "AND is_deleted = 0 " +
            "<choose>" +
            "  <when test='isEnabled != null'>" +
            "    AND is_enabled = #{isEnabled} " +
            "  </when>" +
            "  <otherwise>" +
            "    AND is_enabled = 1 " +
            "  </otherwise>" +
            "</choose>" +
            "<if test='role != null and role != \"\"'>" +
            "  AND role = #{role} " +
            "</if>" +
            "<if test='keyWords != null and keyWords != \"\"'>" +
            "  AND (username LIKE CONCAT('%', #{keyWords}, '%') " +
            "       OR name LIKE CONCAT('%', #{keyWords}, '%')) " +
            "</if>" +
            "</script>")
    Long countTotal(
            @Param("role") String role,
            @Param("keyWords") String keyWords,
            @Param("isEnabled") Boolean isEnabled
    );

    @Select("SELECT " +
            "id, username, password, name, role, " +
            "is_enabled AS isEnabled, " +
            "create_time AS createTime, " +
            "update_time AS updateTime " +
            "FROM sys_user WHERE is_enabled = 1 AND is_deleted = 0 ORDER BY create_time DESC")
    List<User> selectAllEnabled();
}
