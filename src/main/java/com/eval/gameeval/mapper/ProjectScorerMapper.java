package com.eval.gameeval.mapper;

import com.eval.gameeval.models.entity.ProjectScorer;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

@Mapper
public interface ProjectScorerMapper {
    // ========== 查询 ==========
    @Select("SELECT id, project_id AS projectId, user_id AS userId, create_time AS createTime " +
            "FROM project_scorer WHERE id = #{id}")
    ProjectScorer selectById(@Param("id") Long id);

    @Select("SELECT id, project_id AS projectId, user_id AS userId, create_time AS createTime " +
            "FROM project_scorer WHERE project_id = #{projectId} ORDER BY create_time ASC")
    List<ProjectScorer> selectByProjectId(@Param("projectId") Long projectId);

    /**
     * 批量查询项目关联的打分用户ID
     */
    @Select("<script>" +
            "SELECT project_id AS projectId, user_id AS userId " +
            "FROM project_scorer " +
            "WHERE project_id IN " +
            "<foreach collection='projectIds' item='id' open='(' separator=',' close=')'>" +
            "  #{id} " +
            "</foreach>" +
            "</script>")
    List<Map<String, Object>> selectUserIdsByProjectIds(@Param("projectIds") List<Long> projectIds);

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
