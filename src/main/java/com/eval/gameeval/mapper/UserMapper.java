package com.eval.gameeval.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eval.gameeval.models.entity.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Repository;

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
}
