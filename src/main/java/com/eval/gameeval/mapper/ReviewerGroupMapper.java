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

    /**
     * 查询所有启用的评审组（支持关键词搜索）
     */
    @Select("<script>" +
            "SELECT id, name, description, creator_id AS creatorId, is_enabled AS isEnabled, " +
            "       create_time AS createTime, update_time AS updateTime " +
            "FROM reviewer_group " +
            "WHERE is_enabled = 1 " +
            "<if test='keyWords != null and keyWords != \"\"'>" +
            "  AND (name LIKE CONCAT('%', #{keyWords}, '%') " +
            "       OR description LIKE CONCAT('%', #{keyWords}, '%')) " +
            "</if>" +
            "ORDER BY create_time DESC" +
            "</script>")
    List<ReviewerGroup> selectAllEnabledWithKeywords(@Param("keyWords") String keyWords);

    /**
     * 查询分页评审组（支持关键词搜索）
     */
    @Select("<script>" +
            "SELECT id, name, description, creator_id AS creatorId, is_enabled AS isEnabled, " +
            "       create_time AS createTime, update_time AS updateTime " +
            "FROM reviewer_group " +
            "WHERE is_enabled = 1 " +
            "<if test='keyWords != null and keyWords != \"\"'>" +
            "  AND (name LIKE CONCAT('%', #{keyWords}, '%') " +
            "       OR description LIKE CONCAT('%', #{keyWords}, '%')) " +
            "</if>" +
            "ORDER BY create_time DESC " +
            "LIMIT #{offset}, #{pageSize}" +
            "</script>")
    List<ReviewerGroup> selectPageWithSearch(
            @Param("offset") int offset,
            @Param("pageSize") int pageSize,
            @Param("keyWords") String keyWords);

    /**
     * 统计启用的评审组数量（支持关键词搜索）
     */
    @Select("<script>" +
            "SELECT COUNT(*) FROM reviewer_group " +
            "WHERE is_enabled = 1 " +
            "<if test='keyWords != null and keyWords != \"\"'>" +
            "  AND (name LIKE CONCAT('%', #{keyWords}, '%') " +
            "       OR description LIKE CONCAT('%', #{keyWords}, '%')) " +
            "</if>" +
            "</script>")
    Long countWithSearch(@Param("keyWords") String keyWords);

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