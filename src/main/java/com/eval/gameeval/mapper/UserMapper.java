package com.eval.gameeval.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eval.gameeval.models.entity.User;
import org.apache.ibatis.annotations.*;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
@Repository
public interface UserMapper extends BaseMapper<User> {
    /**
     * 根据用户名查询用户（只查询启用的用户）
     *
     * @param username 用户名
     * @return 用户信息
     */
    @Select("SELECT * FROM sys_user WHERE username = #{username} AND is_enabled = 1 LIMIT 1")
    User selectByUsername(@Param("username") String username);
    /**
     * 检查用户名是否存在
     */
    @Select("SELECT COUNT(*) FROM sys_user WHERE username = #{username}")
    Integer countByUsername(@Param("username") String username);

    @Insert("INSERT INTO sys_user(username, password, name, role, is_enabled, create_time, update_time) " +
            "VALUES(#{username}, #{password}, #{name}, #{role}, #{isEnabled}, #{createTime}, #{updateTime})")
    int insertUser(User user);


    @Select("SELECT * FROM sys_user WHERE id = #{id}")
    User selectById(@Param("id") Long id);



    @Delete("DELETE FROM sys_user WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    @Update("UPDATE sys_user " +
            "SET name = #{name}, " +
            "    role = #{role}, " +
            "    is_enabled = #{isEnabled}, " +
            "    update_time = #{updateTime} " +
            "WHERE id = #{id}")
    int updateById(User user);


    @Update("UPDATE sys_user SET is_enabled = 0, update_time = #{updateTime} WHERE id = #{id}")
    int disableById(@Param("id") Long id, @Param("updateTime") LocalDateTime updateTime);


    /**
     * 分页查询用户（带角色筛选）
     */
    @Select("<script>" +
            "SELECT " +
            "id, username, password, name, role, " +
            "is_enabled AS isEnabled, " +
            "create_time AS createTime, " +
            "update_time AS updateTime " +
            "FROM sys_user " +
            "WHERE is_enabled = 1 " +
            "<if test='role != null and role != \"\"'>" +
            "  AND role = #{role} " +
            "</if>" +
            "ORDER BY create_time DESC " +
            "LIMIT #{offset}, #{limit}" +
            "</script>")
    List<User> selectPage(
            @Param("offset") int offset,
            @Param("limit") int limit,
            @Param("role") String role
    );

    /**
     * 统计用户总数（带角色筛选）
     */
    @Select("<script>" +
            "SELECT COUNT(*) FROM sys_user " +
            "WHERE is_enabled = 1 " +
            "<if test='role != null and role != \"\"'>" +
            "  AND role = #{role} " +
            "</if>" +
            "</script>")
    Long countTotal(@Param("role") String role);

    @Select("SELECT " +
            "id, username, password, name, role, " +
            "is_enabled AS isEnabled, " +
            "create_time AS createTime, " +
            "update_time AS updateTime " +
            "FROM sys_user WHERE is_enabled = 1 ORDER BY create_time DESC")
    List<User> selectAllEnabled();
}
