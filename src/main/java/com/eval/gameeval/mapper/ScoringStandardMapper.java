package com.eval.gameeval.mapper;

import com.eval.gameeval.models.entity.ScoringStandard;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

@Mapper
public interface ScoringStandardMapper {

    // ========== 查询 ==========
    @Select("SELECT id, creator_id AS creatorId, name, create_time AS createTime, update_time AS updateTime " +
            "FROM scoring_standard WHERE id = #{id}")
    ScoringStandard selectById(@Param("id") Long id);

    @Select("SELECT id, creator_id AS creatorId, name, create_time AS createTime, update_time AS updateTime " +
            "FROM scoring_standard ORDER BY create_time DESC")
    List<ScoringStandard> selectAll();

    // ========== 插入 ==========
    @Insert("INSERT INTO scoring_standard(creator_id, name, create_time, update_time) " +
            "VALUES(#{creatorId}, #{name}, #{createTime}, #{updateTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ScoringStandard standard);

    // ========== 更新 ==========
    @Update("UPDATE scoring_standard " +
            "SET name = #{name}, " +
            "    update_time = #{updateTime} " +
            "WHERE id = #{id}")
    int updateById(ScoringStandard standard);

    // ========== 删除 ==========
    @Delete("DELETE FROM scoring_standard WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    // ========== 新增：按名称查询 ==========
    @Select("SELECT id, creator_id AS creatorId, name, create_time AS createTime, update_time AS updateTime " +
            "FROM scoring_standard WHERE name = #{name} LIMIT 1")
    ScoringStandard selectByName(@Param("name") String name);

    @Select("SELECT " +
            "  (SELECT COUNT(*) FROM scoring_standard) AS totalStandards, " +
            "  (SELECT COUNT(DISTINCT standard_id) FROM project WHERE is_deleted = 0 AND is_enabled = 1) AS enabledStandards")
    Map<String, Object> selectStandardOverview();
}