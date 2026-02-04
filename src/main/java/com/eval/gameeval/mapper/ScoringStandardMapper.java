package com.eval.gameeval.mapper;

import com.eval.gameeval.models.entity.ScoringStandard;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ScoringStandardMapper {
    // ========== 查询 ==========
    @Select("SELECT id, creator_id AS creatorId, create_time AS createTime, update_time AS updateTime " +
            "FROM scoring_standard WHERE id = #{id}")
    ScoringStandard selectById(@Param("id") Long id);

    @Select("SELECT id, creator_id AS creatorId, create_time AS createTime, update_time AS updateTime " +
            "FROM scoring_standard ORDER BY create_time DESC")
    List<ScoringStandard> selectAll();

    // ========== 插入 ==========
    @Insert("INSERT INTO scoring_standard(creator_id, create_time, update_time) " +
            "VALUES(#{creatorId}, #{createTime}, #{updateTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ScoringStandard standard);

    // ========== 删除 ==========
    @Delete("DELETE FROM scoring_standard WHERE id = #{id}")
    int deleteById(@Param("id") Long id);
}
