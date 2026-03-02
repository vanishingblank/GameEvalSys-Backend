package com.eval.gameeval.mapper;

import com.eval.gameeval.models.entity.ReviewerGroup;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ReviewerGroupMapper {

    // ========== 查询 ==========
    @Select("SELECT id, name, description, creator_id AS creatorId, is_enabled AS isEnabled, " +
            "create_time AS createTime, update_time AS updateTime " +
            "FROM reviewer_group WHERE id = #{id}")
    ReviewerGroup selectById(@Param("id") Long id);

    @Select("SELECT id, name, description, creator_id AS creatorId, is_enabled AS isEnabled, " +
            "create_time AS createTime, update_time AS updateTime " +
            "FROM reviewer_group WHERE is_enabled = 1 ORDER BY create_time DESC")
    List<ReviewerGroup> selectAllEnabled();

    // ========== 插入 ==========
    @Insert("INSERT INTO reviewer_group(name, description, creator_id, is_enabled, create_time, update_time) " +
            "VALUES(#{name}, #{description}, #{creatorId}, #{isEnabled}, #{createTime}, #{updateTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ReviewerGroup group);

    // ========== 更新 ==========
    @Update("UPDATE reviewer_group " +
            "SET name = #{name}, " +
            "    description = #{description}, " +
            "    is_enabled = #{isEnabled}, " +
            "    update_time = #{updateTime} " +
            "WHERE id = #{id}")
    int updateById(ReviewerGroup group);

    // ========== 删除 ==========
    @Delete("DELETE FROM reviewer_group WHERE id = #{id}")
    int deleteById(@Param("id") Long id);
}