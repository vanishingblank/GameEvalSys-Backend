package com.eval.gameeval.mapper;

import com.eval.gameeval.models.entity.ProjectGroup;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

/**
 * 项目-小组关联表Mapper
 */
@Mapper
public interface ProjectGroupMapper {

    /**
     * 根据ID查询关联记录
     */
    @Select("SELECT id, project_id AS projectId, group_info_id AS groupInfoId, create_time AS createTime, update_time AS updateTime " +
            "FROM project_group WHERE id = #{id}")
    ProjectGroup selectById(@Param("id") Long id);

    /**
     * 根据项目ID查询所有关联的小组
     */
    @Select("SELECT id, project_id AS projectId, group_info_id AS groupInfoId, create_time AS createTime, update_time AS updateTime " +
            "FROM project_group WHERE project_id = #{projectId} ORDER BY create_time ASC")
    List<ProjectGroup> selectByProjectId(@Param("projectId") Long projectId);

    /**
     * 根据小组ID查询所有关联的项目
     */
    @Select("SELECT id, project_id AS projectId, group_info_id AS groupInfoId, create_time AS createTime, update_time AS updateTime " +
            "FROM project_group WHERE group_info_id = #{groupId}")
    List<ProjectGroup> selectByGroupId(@Param("groupId") Long groupId);

    /**
     * 根据小组ID查询所有关联的项目（别名方法）
     */
    @Select("SELECT id, project_id AS projectId, group_info_id AS groupInfoId, create_time AS createTime, update_time AS updateTime " +
            "FROM project_group WHERE group_info_id = #{groupInfoId}")
    List<ProjectGroup> selectByGroupInfoId(@Param("groupInfoId") Long groupInfoId);

    /**
     * 查询指定小组和项目的关联关系
     */
    @Select("SELECT id, project_id AS projectId, group_info_id AS groupInfoId, create_time AS createTime, update_time AS updateTime " +
            "FROM project_group WHERE group_info_id = #{groupId} AND project_id = #{projectId}")
    ProjectGroup selectByGroupIdAndProjectId(@Param("groupId") Long groupId, @Param("projectId") Long projectId);

    /**
     * 查询项目中的小组列表（关联小组信息表），支持搜索和权限过滤
     */
    @Select("<script>" +
            "SELECT " +
            "  pg.id, " +
            "  pg.project_id AS projectId, " +
            "  pg.group_info_id AS groupInfoId, " +
            "  pgi.name, " +
            "  pgi.description, " +
            "  pgi.is_enabled AS isEnabled, " +
            "  pg.create_time AS createTime, " +
            "  pg.update_time AS updateTime " +
            "FROM project_group pg " +
            "INNER JOIN project_group_info pgi ON pg.group_info_id = pgi.id AND pgi.is_deleted = 0 " +
            "WHERE 1=1 " +
            "<if test='projectIds != null and projectIds.size() > 0'>" +
            "  AND pg.project_id IN " +
            "  <foreach collection='projectIds' item='id' open='(' separator=',' close=')'>" +
            "    #{id}" +
            "  </foreach>" +
            "</if>" +
            "<if test='keyWords != null and keyWords != \"\"'>" +
            "  AND (pgi.name LIKE CONCAT('%', #{keyWords}, '%')) " +
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
     * 统计支持搜索和权限过滤
     */
    @Select("<script>" +
            "SELECT COUNT(*) FROM project_group pg " +
            "INNER JOIN project_group_info pgi ON pg.group_info_id = pgi.id AND pgi.is_deleted = 0 " +
            "WHERE 1=1 " +
            "<if test='projectIds != null and projectIds.size() > 0'>" +
            "  AND pg.project_id IN " +
            "  <foreach collection='projectIds' item='id' open='(' separator=',' close=')'>" +
            "    #{id}" +
            "  </foreach>" +
            "</if>" +
            "<if test='keyWords != null and keyWords != \"\"'>" +
            "  AND (pgi.name LIKE CONCAT('%', #{keyWords}, '%')) " +
            "</if>" +
            "</script>")
    Long countTotalWithProject(
            @Param("keyWords") String keyWords,
            @Param("projectIds") List<Long> projectIds
    );

    /**
     * 批量插入关联记录
     */
    @Insert("<script>" +
            "INSERT INTO project_group(project_id, group_info_id, create_time, update_time) " +
            "VALUES " +
            "<foreach collection='relations' item='item' separator=','>" +
            "  (#{item.projectId}, #{item.groupInfoId}, #{item.createTime}, #{item.updateTime})" +
            "</foreach>" +
            "</script>")
    int insertBatch(@Param("relations") List<ProjectGroup> relations);

    /**
     * 插入关联记录
     */
    @Insert("INSERT INTO project_group(project_id, group_info_id, create_time, update_time) " +
            "VALUES(#{projectId}, #{groupInfoId}, #{createTime}, #{updateTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(ProjectGroup relation);

    /**
     * 删除项目中的所有关联小组
     */
    @Delete("DELETE FROM project_group WHERE project_id = #{projectId}")
    int deleteByProjectId(@Param("projectId") Long projectId);

    /**
     * 删除指定的关联关系
     */
    @Delete("DELETE FROM project_group WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    /**
     * 删除小组的所有项目关联
     */
    @Delete("DELETE FROM project_group WHERE group_info_id = #{groupInfoId}")
    int deleteByGroupInfoId(@Param("groupInfoId") Long groupInfoId);

    /**
     * 检查项目和小组是否已关联
     */
    @Select("SELECT COUNT(*) FROM project_group WHERE project_id = #{projectId} AND group_info_id = #{groupInfoId}")
    Long checkRelation(@Param("projectId") Long projectId, @Param("groupInfoId") Long groupInfoId);
}
