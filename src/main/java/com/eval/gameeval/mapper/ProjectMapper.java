package com.eval.gameeval.mapper;

import com.eval.gameeval.models.entity.Project;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface ProjectMapper {
    // ========== 查询 ==========
    @Select("SELECT id, name, description, start_date AS startDate, end_date AS endDate, " +
            "status, is_enabled AS isEnabled, standard_id AS standardId, creator_id AS creatorId, " +
            "create_time AS createTime, update_time AS updateTime " +
            "FROM project WHERE id = #{id} AND is_deleted = 0")
    Project selectById(@Param("id") Long id);

    @Select("SELECT id FROM project " +
            "WHERE status <> CASE " +
            "  WHEN #{now} < start_date THEN 'not_started' " +
            "  WHEN #{now} > end_date THEN 'ended' " +
            "  ELSE 'ongoing' " +
            "END " +
            "AND is_deleted = 0")
    List<Long> selectStatusMismatchProjectIds(@Param("now") LocalDateTime now);

    @Select("<script>" +
            "SELECT id, name, description, start_date AS startDate, end_date AS endDate, " +
            "status, is_enabled AS isEnabled, standard_id AS standardId, creator_id AS creatorId, " +
            "create_time AS createTime, update_time AS updateTime " +
            "FROM project " +
            "WHERE 1=1 AND is_deleted = 0 " +
            "<if test='status != null and status != \"\"'>" +
            "  AND status = #{status} " +
            "</if>" +
            "<if test='isEnabled != null'>" +
            "  AND is_enabled = #{isEnabled} " +
            "</if>" +
            "<if test='keyWords != null and keyWords != \"\"'>" +
            "  AND (name LIKE CONCAT('%', #{keyWords}, '%') OR description LIKE CONCAT('%', #{keyWords}, '%')) " +
            "</if>" +
            "ORDER BY create_time DESC " +
            "LIMIT #{offset}, #{limit}" +
            "</script>")
    List<Project> selectPage(
            @Param("offset") int offset,
            @Param("limit") int limit,
            @Param("status") String status,
            @Param("isEnabled") Boolean isEnabled,
            @Param("keyWords") String keyWords
    );

    @Select("<script>" +
            "SELECT COUNT(*) FROM project " +
            "WHERE 1=1 AND is_deleted = 0 " +
            "<if test='status != null and status != \"\"'>" +
            "  AND status = #{status} " +
            "</if>" +
            "<if test='isEnabled != null'>" +
            "  AND is_enabled = #{isEnabled} " +
            "</if>" +
            "<if test='keyWords != null and keyWords != \"\"'>" +
            "  AND (name LIKE CONCAT('%', #{keyWords}, '%') OR description LIKE CONCAT('%', #{keyWords}, '%')) " +
            "</if>" +
            "</script>")
    Long countTotal(@Param("status") String status, @Param("isEnabled") Boolean isEnabled, @Param("keyWords") String keyWords);

    @Select("SELECT id, name, description, start_date AS startDate, end_date AS endDate, " +
            "status, is_enabled AS isEnabled, standard_id AS standardId, creator_id AS creatorId, " +
            "create_time AS createTime, update_time AS updateTime " +
            "FROM project " +
            "WHERE id IN (" +
            "  SELECT DISTINCT project_id FROM project_scorer WHERE user_id = #{userId}" +
            ") " +
            "AND is_deleted = 0 " +
            "ORDER BY create_time DESC")
    List<Project> selectByScorerId(@Param("userId") Long userId);

    @Select("<script>" +
            "SELECT id, name, description, start_date AS startDate, end_date AS endDate, " +
            "status, is_enabled AS isEnabled, standard_id AS standardId, creator_id AS creatorId, " +
            "create_time AS createTime, update_time AS updateTime " +
            "FROM project " +
            "WHERE id IN (" +
            "  SELECT DISTINCT project_id FROM project_scorer WHERE user_id = #{userId}" +
            ") " +
            "AND is_deleted = 0 " +
            "<if test='status != null and status != \"\"'>" +
            "  AND status = #{status} " +
            "</if>" +
            "<if test='isEnabled != null'>" +
            "  AND is_enabled = #{isEnabled} " +
            "</if>" +
            "<if test='keyWords != null and keyWords != \"\"'>" +
            "  AND (name LIKE CONCAT('%', #{keyWords}, '%') OR description LIKE CONCAT('%', #{keyWords}, '%')) " +
            "</if>" +
            "ORDER BY create_time DESC " +
            "LIMIT #{offset}, #{limit}" +
            "</script>")
    List<Project> selectPageByScorerId(
            @Param("userId") Long userId,
            @Param("offset") int offset,
            @Param("limit") int limit,
            @Param("status") String status,
            @Param("isEnabled") Boolean isEnabled,
            @Param("keyWords") String keyWords
    );

    @Select("<script>" +
            "SELECT COUNT(DISTINCT project_id) FROM project_scorer ps " +
            "JOIN project p ON p.id = ps.project_id " +
            "WHERE ps.user_id = #{userId} " +
            "AND p.is_deleted = 0 " +
            "<if test='status != null and status != \"\"'>" +
            "  AND p.status = #{status} " +
            "</if>" +
            "<if test='isEnabled != null'>" +
            "  AND p.is_enabled = #{isEnabled} " +
            "</if>" +
            "<if test='keyWords != null and keyWords != \"\"'>" +
            "  AND (p.name LIKE CONCAT('%', #{keyWords}, '%') OR p.description LIKE CONCAT('%', #{keyWords}, '%')) " +
            "</if>" +
            "</script>")
    Long countByScorerId(@Param("userId") Long userId, @Param("status") String status, @Param("isEnabled") Boolean isEnabled, @Param("keyWords") String keyWords);

    /**
     * 查询用户打分概览统计
     */
    @Select("SELECT " +
            "  COUNT(1) AS totalProjects, " +
            "  COALESCE(SUM(CASE WHEN p.status = 'ongoing' THEN 1 ELSE 0 END), 0) AS ongoingProjects, " +
            "  COALESCE(SUM(CASE " +
            "    WHEN COALESCE(pg.groupCount, 0) > 0 AND COALESCE(sr.scoredCount, 0) = COALESCE(pg.groupCount, 0) " +
            "    THEN 1 ELSE 0 END), 0) AS completedProjects, " +
            "  COALESCE(SUM(CASE " +
            "    WHEN COALESCE(pg.groupCount, 0) = 0 OR COALESCE(sr.scoredCount, 0) < COALESCE(pg.groupCount, 0) " +
            "    THEN 1 ELSE 0 END), 0) AS pendingProjects " +
            "FROM (" +
            "  SELECT DISTINCT project_id AS projectId " +
            "  FROM project_scorer " +
            "  WHERE user_id = #{userId}" +
            ") ap " +
            "JOIN project p ON p.id = ap.projectId AND p.is_deleted = 0 " +
            "LEFT JOIN (" +
            "  SELECT project_id AS projectId, COUNT(DISTINCT group_info_id) AS groupCount " +
            "  FROM project_group " +
            "  GROUP BY project_id" +
            ") pg ON pg.projectId = ap.projectId " +
            "LEFT JOIN (" +
            "  SELECT project_id AS projectId, COUNT(DISTINCT group_info_id) AS scoredCount " +
            "  FROM scoring_record " +
            "  WHERE user_id = #{userId} " +
            "  GROUP BY project_id" +
            ") sr ON sr.projectId = ap.projectId")
    Map<String, Object> selectScoringOverviewByUserId(@Param("userId") Long userId);

    /**
     * 统计某天之前的累计项目总数（用于构建累计趋势基线）
     */
    @Select("SELECT COUNT(*) FROM project WHERE create_time < #{startTime} AND is_deleted = 0")
    Long countProjectsBefore(@Param("startTime") LocalDateTime startTime);

    /**
     * 查询时间段内每日新增项目数
     */
    @Select("SELECT DATE(create_time) AS statDate, COUNT(*) AS cnt " +
            "FROM project " +
            "WHERE create_time >= #{startTime} AND create_time < #{endTime} AND is_deleted = 0 " +
            "GROUP BY DATE(create_time)")
    List<Map<String, Object>> selectDailyProjectCount(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    /**
     * 查询时间段内每日新增评分数
     */
    @Select("SELECT DATE(create_time) AS statDate, COUNT(*) AS cnt " +
            "FROM scoring_record " +
            "WHERE create_time >= #{startTime} AND create_time < #{endTime} " +
            "GROUP BY DATE(create_time)")
    List<Map<String, Object>> selectDailyScoreCount(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    /**
     * 查询时间段内每日平均得分
     */
    @Select("SELECT DATE(create_time) AS statDate, COALESCE(AVG(total_score), 0) AS avgScore " +
            "FROM scoring_record " +
            "WHERE create_time >= #{startTime} AND create_time < #{endTime} " +
            "GROUP BY DATE(create_time)")
    List<Map<String, Object>> selectDailyAverageScore(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    // ========== 插入 ==========
    @Insert("INSERT INTO project(name, description, start_date, end_date, status, is_enabled, is_deleted, " +
            "standard_id, creator_id, create_time, update_time) " +
            "VALUES(#{name}, #{description}, #{startDate}, #{endDate}, #{status}, #{isEnabled}, 0, " +
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
            "WHERE id = #{id} AND is_deleted = 0")
    int updateById(Project project);

    @Update("UPDATE project SET status = 'ended', update_time = #{updateTime} WHERE id = #{id} AND is_deleted = 0")
    int endProject(@Param("id") Long id, @Param("updateTime") LocalDateTime updateTime);

    @Update("UPDATE project " +
            "SET status = CASE " +
            "  WHEN #{now} < start_date THEN 'not_started' " +
            "  WHEN #{now} > end_date THEN 'ended' " +
            "  ELSE 'ongoing' " +
            "END, " +
            "update_time = #{now} " +
            "WHERE status <> CASE " +
            "  WHEN #{now} < start_date THEN 'not_started' " +
            "  WHEN #{now} > end_date THEN 'ended' " +
            "  ELSE 'ongoing' " +
            "END " +
            "AND is_deleted = 0")
    int syncStatusByNow(@Param("now") LocalDateTime now);

    // ========== 删除 ==========
    @Update("UPDATE project SET is_deleted = 1, is_enabled = 0, update_time = #{updateTime} WHERE id = #{id} AND is_deleted = 0")
    int deleteById(@Param("id") Long id, @Param("updateTime") LocalDateTime updateTime);
}
