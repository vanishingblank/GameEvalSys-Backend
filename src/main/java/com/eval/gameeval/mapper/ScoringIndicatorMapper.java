package com.eval.gameeval.mapper;

import com.eval.gameeval.models.entity.ScoringIndicator;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ScoringIndicatorMapper {
    // ========== 查询 ==========
    @Select("SELECT id, standard_id AS standardId, name, description, min_score AS minScore, " +
            "max_score AS maxScore, sort, create_time AS createTime " +
            "FROM scoring_indicator WHERE id = #{id}")
    ScoringIndicator selectById(@Param("id") Long id);

    @Select("SELECT id, standard_id AS standardId, name, description, min_score AS minScore, " +
            "max_score AS maxScore, sort, create_time AS createTime " +
            "FROM scoring_indicator WHERE standard_id = #{standardId} ORDER BY sort ASC, id ASC")
    List<ScoringIndicator> selectByStandardId(@Param("standardId") Long standardId);

    @Select("SELECT id, standard_id AS standardId, name, description, min_score AS minScore, " +
            "max_score AS maxScore, sort, create_time AS createTime " +
            "FROM scoring_indicator WHERE id IN " +
            "<foreach collection='indicatorIds' item='id' open='(' separator=',' close=')'>" +
            "  #{id}" +
            "</foreach>")
    List<ScoringIndicator> selectByIds(@Param("indicatorIds") List<Long> indicatorIds);

    // ========== 插入 ==========
    @Insert("INSERT INTO scoring_indicator(standard_id, name, description, min_score, max_score, sort, create_time) " +
            "VALUES(#{standardId}, #{name}, #{description}, #{minScore}, #{maxScore}, #{sort}, #{createTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ScoringIndicator indicator);

    // ========== 批量插入 ==========
    @Insert("<script>" +
            "INSERT INTO scoring_indicator(standard_id, name, description, min_score, max_score, sort, create_time) " +
            "VALUES " +
            "<foreach collection='indicators' item='item' separator=','>" +
            "  (#{item.standardId}, #{item.name}, #{item.description}, #{item.minScore}, #{item.maxScore}, #{item.sort}, #{item.createTime})" +
            "</foreach>" +
            "</script>")
    int insertBatch(@Param("indicators") List<ScoringIndicator> indicators);

    // ========== 删除 ==========
    @Delete("DELETE FROM scoring_indicator WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    @Delete("DELETE FROM scoring_indicator WHERE standard_id = #{standardId}")
    int deleteByStandardId(@Param("standardId") Long standardId);
}
