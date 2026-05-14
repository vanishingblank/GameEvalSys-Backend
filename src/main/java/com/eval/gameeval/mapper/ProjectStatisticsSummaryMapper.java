package com.eval.gameeval.mapper;

import com.eval.gameeval.models.entity.ProjectStatisticsGroupSummary;
import com.eval.gameeval.models.entity.ProjectStatisticsIndicatorSummary;
import com.eval.gameeval.models.entity.ProjectStatisticsProjectSummary;
import com.eval.gameeval.models.entity.ProjectStatisticsScorerDistributionSummary;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ProjectStatisticsSummaryMapper {

    @Delete("DELETE FROM project_statistics_group_summary WHERE project_id = #{projectId}")
    int deleteGroupSummaryByProjectId(@Param("projectId") Long projectId);

        @Delete("DELETE FROM project_statistics_project_summary WHERE project_id = #{projectId}")
        int deleteProjectSummaryByProjectId(@Param("projectId") Long projectId);

    @Delete("DELETE FROM project_statistics_indicator_summary WHERE project_id = #{projectId}")
    int deleteIndicatorSummaryByProjectId(@Param("projectId") Long projectId);

    @Delete("DELETE FROM project_statistics_scorer_distribution_summary WHERE project_id = #{projectId}")
    int deleteScorerDistributionSummaryByProjectId(@Param("projectId") Long projectId);

        @Select("SELECT COUNT(*) FROM project_statistics_project_summary")
        Long countProjectSummaryRows();

        @Select("SELECT COUNT(*) FROM project_statistics_group_summary")
        Long countGroupSummaryRows();

        @Select("SELECT COUNT(*) FROM project_statistics_indicator_summary")
        Long countIndicatorSummaryRows();

        @Select("SELECT COUNT(*) FROM project_statistics_scorer_distribution_summary")
        Long countScorerDistributionSummaryRows();

    @Insert("<script>"
            + "INSERT INTO project_statistics_project_summary "
            + "(project_id, raw_average_score, normalized_average_score, processed_average_score, abnormal_count, sample_size, valid_sample_size, updated_time) VALUES "
            + "(#{item.projectId}, #{item.rawAverageScore}, #{item.normalizedAverageScore}, #{item.processedAverageScore}, #{item.abnormalCount}, #{item.sampleSize}, #{item.validSampleSize}, #{item.updatedTime})"
            + " ON DUPLICATE KEY UPDATE "
            + "raw_average_score = VALUES(raw_average_score), "
            + "normalized_average_score = VALUES(normalized_average_score), "
            + "processed_average_score = VALUES(processed_average_score), "
            + "abnormal_count = VALUES(abnormal_count), "
            + "sample_size = VALUES(sample_size), "
            + "valid_sample_size = VALUES(valid_sample_size), "
            + "updated_time = VALUES(updated_time)"
            + "</script>")
    int upsertProjectSummary(@Param("item") ProjectStatisticsProjectSummary item);

    @Insert("<script>"
            + "INSERT INTO project_statistics_group_summary "
            + "(project_id, group_id, group_name, raw_average_score, normalized_average_score, processed_average_score, abnormal_count, sample_size, valid_sample_size, updated_time) VALUES "
            + "<foreach collection='items' item='item' separator=','>"
            + "(#{item.projectId}, #{item.groupId}, #{item.groupName}, #{item.rawAverageScore}, #{item.normalizedAverageScore}, #{item.processedAverageScore}, #{item.abnormalCount}, #{item.sampleSize}, #{item.validSampleSize}, #{item.updatedTime})"
            + "</foreach>"
            + " ON DUPLICATE KEY UPDATE "
            + "group_name = VALUES(group_name), "
            + "raw_average_score = VALUES(raw_average_score), "
            + "normalized_average_score = VALUES(normalized_average_score), "
            + "processed_average_score = VALUES(processed_average_score), "
            + "abnormal_count = VALUES(abnormal_count), "
            + "sample_size = VALUES(sample_size), "
            + "valid_sample_size = VALUES(valid_sample_size), "
            + "updated_time = VALUES(updated_time)"
            + "</script>")
    int batchUpsertGroupSummaries(@Param("items") List<ProjectStatisticsGroupSummary> items);

    @Insert("<script>"
            + "INSERT INTO project_statistics_indicator_summary "
            + "(project_id, indicator_id, indicator_name, raw_average_score, normalized_average_score, processed_average_score, abnormal_count, total_abnormal_count, sample_size, valid_sample_size, updated_time) VALUES "
            + "<foreach collection='items' item='item' separator=','>"
            + "(#{item.projectId}, #{item.indicatorId}, #{item.indicatorName}, #{item.rawAverageScore}, #{item.normalizedAverageScore}, #{item.processedAverageScore}, #{item.abnormalCount}, #{item.totalAbnormalCount}, #{item.sampleSize}, #{item.validSampleSize}, #{item.updatedTime})"
            + "</foreach>"
            + " ON DUPLICATE KEY UPDATE "
            + "indicator_name = VALUES(indicator_name), "
            + "raw_average_score = VALUES(raw_average_score), "
            + "normalized_average_score = VALUES(normalized_average_score), "
            + "processed_average_score = VALUES(processed_average_score), "
            + "abnormal_count = VALUES(abnormal_count), "
            + "total_abnormal_count = VALUES(total_abnormal_count), "
            + "sample_size = VALUES(sample_size), "
            + "valid_sample_size = VALUES(valid_sample_size), "
            + "updated_time = VALUES(updated_time)"
            + "</script>")
    int batchUpsertIndicatorSummaries(@Param("items") List<ProjectStatisticsIndicatorSummary> items);

    @Insert("<script>"
            + "INSERT INTO project_statistics_scorer_distribution_summary "
            + "(project_id, user_id, user_name, score_range, count, updated_time) VALUES "
            + "<foreach collection='items' item='item' separator=','>"
            + "(#{item.projectId}, #{item.userId}, #{item.userName}, #{item.scoreRange}, #{item.count}, #{item.updatedTime})"
            + "</foreach>"
            + " ON DUPLICATE KEY UPDATE "
            + "user_name = VALUES(user_name), "
            + "count = VALUES(count), "
            + "updated_time = VALUES(updated_time)"
            + "</script>")
    int batchUpsertScorerDistributionSummaries(@Param("items") List<ProjectStatisticsScorerDistributionSummary> items);

    @Select("SELECT project_id AS projectId, group_id AS groupId, group_name AS groupName, raw_average_score AS rawAverageScore, "
            + "normalized_average_score AS normalizedAverageScore, processed_average_score AS processedAverageScore, "
            + "abnormal_count AS abnormalCount, sample_size AS sampleSize, valid_sample_size AS validSampleSize, updated_time AS updatedTime "
            + "FROM project_statistics_group_summary WHERE project_id = #{projectId} ORDER BY group_id")
    List<ProjectStatisticsGroupSummary> selectGroupSummaryByProjectId(@Param("projectId") Long projectId);

    @Select("SELECT project_id AS projectId, raw_average_score AS rawAverageScore, normalized_average_score AS normalizedAverageScore, processed_average_score AS processedAverageScore, "
            + "abnormal_count AS abnormalCount, sample_size AS sampleSize, valid_sample_size AS validSampleSize, updated_time AS updatedTime "
            + "FROM project_statistics_project_summary WHERE project_id = #{projectId}")
    ProjectStatisticsProjectSummary selectProjectSummaryByProjectId(@Param("projectId") Long projectId);

    @Select("SELECT project_id AS projectId, indicator_id AS indicatorId, indicator_name AS indicatorName, raw_average_score AS rawAverageScore, "
            + "normalized_average_score AS normalizedAverageScore, processed_average_score AS processedAverageScore, "
            + "abnormal_count AS abnormalCount, total_abnormal_count AS totalAbnormalCount, sample_size AS sampleSize, valid_sample_size AS validSampleSize, updated_time AS updatedTime "
            + "FROM project_statistics_indicator_summary WHERE project_id = #{projectId} ORDER BY indicator_id")
    List<ProjectStatisticsIndicatorSummary> selectIndicatorSummaryByProjectId(@Param("projectId") Long projectId);

    @Select("SELECT project_id AS projectId, user_id AS userId, user_name AS userName, score_range AS scoreRange, count, updated_time AS updatedTime "
            + "FROM project_statistics_scorer_distribution_summary WHERE project_id = #{projectId} ORDER BY user_id, score_range")
    List<ProjectStatisticsScorerDistributionSummary> selectScorerDistributionSummaryByProjectId(@Param("projectId") Long projectId);
}
