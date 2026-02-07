package com.eval.gameeval.mapper;

import com.eval.gameeval.models.entity.ScoringRecordDetail;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 打分明细Mapper
 */
@Mapper
public interface ScoringRecordDetailMapper {

    // ========== 查询 ==========
    /**
     * 根据ID查询明细
     */
    @Select("SELECT id, record_id AS recordId, indicator_id AS indicatorId, score " +
            "FROM scoring_record_detail WHERE id = #{id}")
    ScoringRecordDetail selectById(@Param("id") Long id);

    /**
     * 根据记录ID查询所有明细
     */
    @Select("SELECT id, record_id AS recordId, indicator_id AS indicatorId, score " +
            "FROM scoring_record_detail WHERE record_id = #{recordId} ORDER BY indicator_id ASC")
    List<ScoringRecordDetail> selectByRecordId(@Param("recordId") Long recordId);

    // ========== 插入 ==========
    /**
     * 插入单个明细
     */
    @Insert("INSERT INTO scoring_record_detail(record_id, indicator_id, score) " +
            "VALUES(#{recordId}, #{indicatorId}, #{score})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ScoringRecordDetail detail);

    /**
     * 批量插入明细
     */
    @Insert("<script>" +
            "INSERT INTO scoring_record_detail(record_id, indicator_id, score) " +
            "VALUES " +
            "<foreach collection='details' item='item' separator=','>" +
            "  (#{item.recordId}, #{item.indicatorId}, #{item.score})" +
            "</foreach>" +
            "</script>")
    int insertBatch(@Param("details") List<ScoringRecordDetail> details);

    // ========== 删除 ==========
    /**
     * 根据记录ID删除明细
     */
    @Delete("DELETE FROM scoring_record_detail WHERE record_id = #{recordId}")
    int deleteByRecordId(@Param("recordId") Long recordId);

    /**
     * 根据ID删除明细
     */
    @Delete("DELETE FROM scoring_record_detail WHERE id = #{id}")
    int deleteById(@Param("id") Long id);
}