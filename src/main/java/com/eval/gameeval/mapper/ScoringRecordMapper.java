package com.eval.gameeval.mapper;

import com.eval.gameeval.models.entity.ScoringRecord;
import org.apache.ibatis.annotations.*;


import java.util.List;
import java.util.Map;

@Mapper
public interface ScoringRecordMapper {

    // ========== 查询 ==========
    @Select("SELECT id, project_id AS projectId, group_id AS groupId, user_id AS userId, " +
            "total_score AS totalScore, create_time AS createTime, update_time AS updateTime " +
            "FROM scoring_record WHERE id = #{id}")
    ScoringRecord selectById(@Param("id") Long id);

    @Select("SELECT id, project_id AS projectId, group_id AS groupId, user_id AS userId, " +
            "total_score AS totalScore, create_time AS createTime, update_time AS updateTime " +
            "FROM scoring_record WHERE project_id = #{projectId} ORDER BY create_time DESC")
    List<ScoringRecord> selectByProjectId(@Param("projectId") Long projectId);

    @Select("SELECT id, project_id AS projectId, group_id AS groupId, user_id AS userId, " +
            "total_score AS totalScore, create_time AS createTime, update_time AS updateTime " +
            "FROM scoring_record " +
            "WHERE project_id = #{projectId} AND group_id = #{groupId} AND user_id = #{userId} " +
            "LIMIT 1")
    ScoringRecord selectByUniqueKey(
            @Param("projectId") Long projectId,
            @Param("groupId") Long groupId,
            @Param("userId") Long userId
    );

    /**
     * 查询小组平均分
     */
    @Select("SELECT " +
            "  pg.id AS groupId, " +
            "  pg.name AS groupName, " +
            "  AVG(sr.total_score) AS averageScore " +
            "FROM scoring_record sr " +
            "JOIN project_group pg ON sr.group_id = pg.id " +
            "WHERE sr.project_id = #{projectId} " +
            "GROUP BY pg.id, pg.name " +
            "ORDER BY averageScore DESC")
    List<Map<String, Object>> selectGroupAverage(@Param("projectId") Long projectId);

    /**
     * 查询指标平均分
     */
    @Select("SELECT " +
            "  si.id AS indicatorId, " +
            "  si.name AS indicatorName, " +
            "  AVG(srd.score) AS averageScore " +
            "FROM scoring_record_detail srd " +
            "JOIN scoring_indicator si ON srd.indicator_id = si.id " +
            "JOIN scoring_record sr ON srd.record_id = sr.id " +
            "WHERE sr.project_id = #{projectId} " +
            "GROUP BY si.id, si.name " +
            "ORDER BY averageScore DESC")
    List<Map<String, Object>> selectIndicatorAverage(@Param("projectId") Long projectId);

    /**
     * 查询打分用户分布
     */
    @Select("SELECT " +
            "  u.id AS userId, " +
            "  u.name AS userName, " +
            "  CASE " +
            "    WHEN sr.total_score >= 0 AND sr.total_score < 2 THEN '0-2分' " +
            "    WHEN sr.total_score >= 2 AND sr.total_score < 4 THEN '2-4分' " +
            "    WHEN sr.total_score >= 4 AND sr.total_score < 6 THEN '4-6分' " +
            "    WHEN sr.total_score >= 6 AND sr.total_score < 8 THEN '6-8分' " +
            "    WHEN sr.total_score >= 8 AND sr.total_score <= 10 THEN '8-10分' " +
            "    ELSE '其他' " +
            "  END AS scoreRange, " +
            "  COUNT(*) AS count " +
            "FROM scoring_record sr " +
            "JOIN sys_user u ON sr.user_id = u.id " +
            "WHERE sr.project_id = #{projectId} " +
            "GROUP BY u.id, u.name, scoreRange " +
            "ORDER BY u.id, scoreRange")
    List<Map<String, Object>> selectScorerDistribution(@Param("projectId") Long projectId);

    // ========== 插入 ==========
    @Insert("INSERT INTO scoring_record(project_id, group_id, user_id, total_score, create_time, update_time) " +
            "VALUES(#{projectId}, #{groupId}, #{userId}, #{totalScore}, #{createTime}, #{updateTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ScoringRecord record);

    // ========== 更新 ==========
    @Update("UPDATE scoring_record " +
            "SET total_score = #{totalScore}, update_time = #{updateTime} " +
            "WHERE id = #{id}")
    int updateById(ScoringRecord record);

    // ========== 删除 ==========
    @Delete("DELETE FROM scoring_record WHERE id = #{id}")
    int deleteById(@Param("id") Long id);
}