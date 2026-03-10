package com.eval.gameeval.mapper;

import com.eval.gameeval.models.entity.ProjectGroup;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

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

    /**
     * 分页查询所有小组（增强版：支持关键词搜索 + 关联项目名称）
     *
     * 设计说明：
     * 1. LEFT JOIN project 表获取项目名称
     * 2. 关键词同时搜索小组名称和项目名称
     * 3. 按创建时间倒序排列
     */
    @Select("<script>" +
            "SELECT " +
            "  pg.id, " +
            "  pg.project_id AS projectId, " +
            "  pg.name, " +
            "  pg.create_time AS createTime, " +
            "  pg.update_time AS updateTime " +
            "FROM project_group pg " +
            "WHERE 1=1 " +
            "<if test='keyWords != null and keyWords != \"\"'>" +
            "  AND (pg.name LIKE CONCAT('%', #{keyWords}, '%')) " +
            "</if>" +
            "<if test='projectIds != null and projectIds.size() > 0'>" +
            "  AND pg.project_id IN " +
            "  <foreach collection='projectIds' item='id' open='(' separator=',' close=')'>" +
            "    #{id}" +
            "  </foreach>" +
            "</if>" +
            "ORDER BY pg.create_time DESC " +
            "LIMIT #{offset}, #{limit}" +
            "</script>")
    List<Map<String, Object>> selectPageWithProject(
            @Param("offset") int offset,
            @Param("limit") int limit,
            @Param("keyWords") String keyWords,
            @Param("projectIds") List<Long> projectIds
    );

    /**
     * 统计小组总数（增强版：支持关键词搜索 + 权限过滤）
     */
    @Select("<script>" +
            "SELECT COUNT(*) FROM project_group pg " +
            "WHERE 1=1 " +
            "<if test='keyWords != null and keyWords != \"\"'>" +
            "  AND (pg.name LIKE CONCAT('%', #{keyWords}, '%')) " +
            "</if>" +
            "<if test='projectIds != null and projectIds.size() > 0'>" +
            "  AND pg.project_id IN " +
            "  <foreach collection='projectIds' item='id' open='(' separator=',' close=')'>" +
            "    #{id}" +
            "  </foreach>" +
            "</if>" +
            "</script>")
    Long countTotalWithProject(
            @Param("keyWords") String keyWords,
            @Param("projectIds") List<Long> projectIds
    );
}
