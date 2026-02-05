package com.eval.gameeval.mapper;

import com.eval.gameeval.models.entity.ProjectScorer;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ProjectScorerMapper {
    // ========== 查询 ==========
    @Select("SELECT id, project_id AS projectId, user_id AS userId, create_time AS createTime " +
            "FROM project_scorer WHERE id = #{id}")
    ProjectScorer selectById(@Param("id") Long id);

    @Select("SELECT id, project_id AS projectId, user_id AS userId, create_time AS createTime " +
            "FROM project_scorer WHERE project_id = #{projectId} ORDER BY create_time ASC")
    List<ProjectScorer> selectByProjectId(@Param("projectId") Long projectId);

    // ========== 批量插入 ==========
    @Insert("<script>" +
            "INSERT INTO project_scorer(project_id, user_id, create_time) " +
            "VALUES " +
            "<foreach collection='scorers' item='item' separator=','>" +
            "  (#{item.projectId}, #{item.userId}, #{item.createTime})" +
            "</foreach>" +
            "</script>")
    int insertBatch(@Param("scorers") List<ProjectScorer> scorers);

    // ========== 删除 ==========
    @Delete("DELETE FROM project_scorer WHERE project_id = #{projectId}")
    int deleteByProjectId(@Param("projectId") Long projectId);
}
