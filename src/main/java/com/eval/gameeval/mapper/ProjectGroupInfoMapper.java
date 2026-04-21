package com.eval.gameeval.mapper;

import com.eval.gameeval.models.entity.ProjectGroupInfo;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

/**
 * 小组信息主表Mapper
 */
@Mapper
public interface ProjectGroupInfoMapper {

    /**
     * 根据ID查询小组信息
     */
    @Select("SELECT id, name, description, is_enabled AS isEnabled, create_time AS createTime, update_time AS updateTime " +
            "FROM project_group_info WHERE id = #{id} AND is_deleted = 0")
    ProjectGroupInfo selectById(@Param("id") Long id);

    /**
     * 查询所有启用的小组信息
     */
    @Select("SELECT id, name, description, is_enabled AS isEnabled, create_time AS createTime, update_time AS updateTime " +
            "FROM project_group_info WHERE is_enabled = 1 AND is_deleted = 0 ORDER BY create_time ASC")
    List<ProjectGroupInfo> selectAll();

    /**
     * 按名称搜索小组信息
     */
    @Select("SELECT id, name, description, is_enabled AS isEnabled, create_time AS createTime, update_time AS updateTime " +
            "FROM project_group_info WHERE name LIKE CONCAT('%', #{keyWords}, '%') AND is_enabled = 1 AND is_deleted = 0 " +
            "ORDER BY create_time ASC LIMIT #{offset}, #{limit}")
    List<ProjectGroupInfo> searchByName(
            @Param("keyWords") String keyWords,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    /**
     * 统计小组总数
     */
    @Select("SELECT COUNT(*) FROM project_group_info WHERE is_enabled = 1 AND is_deleted = 0")
    Long countAll();

    /**
     * 插入小组信息
     */
    @Insert("INSERT INTO project_group_info(name, description, is_enabled, is_deleted, create_time, update_time) " +
            "VALUES(#{name}, #{description}, #{isEnabled}, 0, #{createTime}, #{updateTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(ProjectGroupInfo groupInfo);

    /**
     * 更新小组信息
     */
    @Update("UPDATE project_group_info SET name = #{name}, description = #{description}, " +
            "is_enabled = #{isEnabled}, update_time = #{updateTime} WHERE id = #{id} AND is_deleted = 0")
    int update(ProjectGroupInfo groupInfo);

    /**
     * 禁用小组信息（软删除）
     */
    @Update("UPDATE project_group_info SET is_deleted = 1, is_enabled = 0, update_time = #{updateTime} WHERE id = #{id} AND is_deleted = 0")
    int disable(@Param("id") Long id, @Param("updateTime") java.time.LocalDateTime updateTime);

    /**
     * 查询所有小组（分页，可选搜索关键词）
     */
    @Select("<script>" +
            "SELECT id, name, description, is_enabled AS isEnabled, create_time AS createTime, update_time AS updateTime " +
            "FROM project_group_info WHERE is_enabled = 1 AND is_deleted = 0 " +
            "<if test='keyWords != null and keyWords != \"\"'> " +
            "AND name LIKE CONCAT('%', #{keyWords}, '%') " +
            "</if> " +
            "ORDER BY create_time DESC LIMIT #{offset}, #{limit}" +
            "</script>")
    List<ProjectGroupInfo> selectPageWithSearch(
            @Param("offset") int offset,
            @Param("limit") int limit,
            @Param("keyWords") String keyWords
    );

    /**
     * 统计小组总数（可选搜索关键词）
     */
    @Select("<script>" +
            "SELECT COUNT(*) FROM project_group_info WHERE is_enabled = 1 AND is_deleted = 0 " +
            "<if test='keyWords != null and keyWords != \"\"'> " +
            "AND name LIKE CONCAT('%', #{keyWords}, '%') " +
            "</if>" +
            "</script>")
    Long countWithSearch(@Param("keyWords") String keyWords);

        @Select("SELECT " +
                        "  COUNT(*) AS totalGroups, " +
                        "  COALESCE(SUM(CASE WHEN is_enabled = 1 THEN 1 ELSE 0 END), 0) AS activeGroups, " +
                        "  (SELECT COUNT(*) FROM project_group) AS totalMembers " +
                        "FROM project_group_info WHERE is_deleted = 0")
        Map<String, Object> selectGroupOverview();
}
