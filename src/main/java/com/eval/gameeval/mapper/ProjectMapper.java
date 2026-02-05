package com.eval.gameeval.mapper;

import com.eval.gameeval.models.entity.Project;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ProjectMapper {
    // ========== 查询 ==========
    @Select("SELECT id, name, description, start_date AS startDate, end_date AS endDate, " +
            "status, is_enabled AS isEnabled, standard_id AS standardId, creator_id AS creatorId, " +
            "create_time AS createTime, update_time AS updateTime " +
            "FROM project WHERE id = #{id}")
    Project selectById(@Param("id") Long id);

    @Select("<script>" +
            "SELECT id, name, description, start_date AS startDate, end_date AS endDate, " +
            "status, is_enabled AS isEnabled, standard_id AS standardId, creator_id AS creatorId, " +
            "create_time AS createTime, update_time AS updateTime " +
            "FROM project " +
            "WHERE 1=1 " +
            "<if test='status != null and status != \"\"'>" +
            "  AND status = #{status} " +
            "</if>" +
            "<if test='isEnabled != null'>" +
            "  AND is_enabled = #{isEnabled} " +
            "</if>" +
            "ORDER BY create_time DESC " +
            "LIMIT #{offset}, #{limit}" +
            "</script>")
    List<Project> selectPage(
            @Param("offset") int offset,
            @Param("limit") int limit,
            @Param("status") String status,
            @Param("isEnabled") Boolean isEnabled
    );

    @Select("<script>" +
            "SELECT COUNT(*) FROM project " +
            "WHERE 1=1 " +
            "<if test='status != null and status != \"\"'>" +
            "  AND status = #{status} " +
            "</if>" +
            "<if test='isEnabled != null'>" +
            "  AND is_enabled = #{isEnabled} " +
            "</if>" +
            "</script>")
    Long countTotal(@Param("status") String status, @Param("isEnabled") Boolean isEnabled);

    @Select("SELECT id, name, description, start_date AS startDate, end_date AS endDate, " +
            "status, is_enabled AS isEnabled, standard_id AS standardId, creator_id AS creatorId, " +
            "create_time AS createTime, update_time AS updateTime " +
            "FROM project " +
            "WHERE id IN (" +
            "  SELECT DISTINCT project_id FROM project_scorer WHERE user_id = #{userId}" +
            ") " +
            "ORDER BY create_time DESC")
    List<Project> selectByScorerId(@Param("userId") Long userId);

    // ========== 插入 ==========
    @Insert("INSERT INTO project(name, description, start_date, end_date, status, is_enabled, " +
            "standard_id, creator_id, create_time, update_time) " +
            "VALUES(#{name}, #{description}, #{startDate}, #{endDate}, #{status}, #{isEnabled}, " +
            "#{standardId}, #{creatorId}, #{createTime}, #{updateTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Project project);

    // ========== 更新 ==========
    @Update("UPDATE project " +
            "SET name = #{name}, " +
            "    description = #{description}, " +
            "    start_date = #{startDate}, " +
            "    end_date = #{endDate}, " +
            "    status = #{status}, " +
            "    is_enabled = #{isEnabled}, " +
            "    standard_id = #{standardId}, " +
            "    update_time = #{updateTime} " +
            "WHERE id = #{id}")
    int updateById(Project project);

    @Update("UPDATE project SET status = 'ended', update_time = #{updateTime} WHERE id = #{id}")
    int endProject(@Param("id") Long id, @Param("updateTime") LocalDateTime updateTime);

    // ========== 删除 ==========
    @Delete("DELETE FROM project WHERE id = #{id}")
    int deleteById(@Param("id") Long id);
}
