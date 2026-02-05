package com.eval.gameeval.mapper;

import com.eval.gameeval.models.entity.ProjectGroup;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ProjectGroupMapper {
    // ========== 查询 ==========
    @Select("SELECT id, project_id AS projectId, name, create_time AS createTime, update_time AS updateTime " +
            "FROM project_group WHERE id = #{id}")
    ProjectGroup selectById(@Param("id") Long id);

    @Select("SELECT id, project_id AS projectId, name, create_time AS createTime, update_time AS updateTime " +
            "FROM project_group WHERE project_id = #{projectId} ORDER BY create_time ASC")
    List<ProjectGroup> selectByProjectId(@Param("projectId") Long projectId);

    // ========== 批量插入 ==========
    @Insert("<script>" +
            "INSERT INTO project_group(project_id, name, create_time, update_time) " +
            "VALUES " +
            "<foreach collection='groups' item='item' separator=','>" +
            "  (#{item.projectId}, #{item.name}, #{item.createTime}, #{item.updateTime})" +
            "</foreach>" +
            "</script>")
    int insertBatch(@Param("groups") List<ProjectGroup> groups);

    // ========== 插入 ==========
    @Insert("INSERT INTO project_group(project_id, name, create_time, update_time) " +
            "VALUES(#{projectId}, #{name}, #{createTime}, #{updateTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(ProjectGroup group);

    // ========== 删除 ==========
    @Delete("DELETE FROM project_group WHERE project_id = #{projectId}")
    int deleteByProjectId(@Param("projectId") Long projectId);
}
