package com.eval.gameeval.mapper;

import com.eval.gameeval.models.entity.ScoringIndicatorCategory;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ScoringIndicatorCategoryMapper {

    @Select("SELECT id, standard_id AS standardId, name, description, sort, create_time AS createTime, " +
            "update_time AS updateTime FROM scoring_indicator_category WHERE id = #{id}")
    ScoringIndicatorCategory selectById(@Param("id") Long id);

    @Select("SELECT id, standard_id AS standardId, name, description, sort, create_time AS createTime, " +
            "update_time AS updateTime FROM scoring_indicator_category WHERE standard_id = #{standardId} ORDER BY sort ASC, id ASC")
    List<ScoringIndicatorCategory> selectByStandardId(@Param("standardId") Long standardId);

    @Insert("INSERT INTO scoring_indicator_category(standard_id, name, description, sort, create_time, update_time) " +
            "VALUES(#{standardId}, #{name}, #{description}, #{sort}, #{createTime}, #{updateTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ScoringIndicatorCategory category);

    @Update("UPDATE scoring_indicator_category SET name = #{name}, description = #{description}, sort = #{sort}, " +
            "update_time = #{updateTime} WHERE id = #{id}")
    int updateById(ScoringIndicatorCategory category);

    @Delete("DELETE FROM scoring_indicator_category WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    @Delete("DELETE FROM scoring_indicator_category WHERE standard_id = #{standardId}")
    int deleteByStandardId(@Param("standardId") Long standardId);
}
