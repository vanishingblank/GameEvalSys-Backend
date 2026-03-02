package com.eval.gameeval.mapper;

import com.eval.gameeval.models.entity.ReviewerGroupMember;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ReviewerGroupMemberMapper {

    // ========== 查询 ==========
    @Select("SELECT id, group_id AS groupId, user_id AS userId, create_time AS createTime " +
            "FROM reviewer_group_member WHERE id = #{id}")
    ReviewerGroupMember selectById(@Param("id") Long id);

    @Select("SELECT id, group_id AS groupId, user_id AS userId, create_time AS createTime " +
            "FROM reviewer_group_member WHERE group_id = #{groupId} ORDER BY create_time ASC")
    List<ReviewerGroupMember> selectByGroupId(@Param("groupId") Long groupId);

    @Select("SELECT user_id AS userId FROM reviewer_group_member WHERE group_id = #{groupId}")
    List<Long> selectUserIdsByGroupId(@Param("groupId") Long groupId);

    // ========== 插入 ==========
    @Insert("INSERT INTO reviewer_group_member(group_id, user_id, create_time) " +
            "VALUES(#{groupId}, #{userId}, #{createTime})")
    int insert(ReviewerGroupMember member);

    @Insert("<script>" +
            "INSERT INTO reviewer_group_member(group_id, user_id, create_time) " +
            "VALUES " +
            "<foreach collection='members' item='item' separator=','>" +
            "  (#{item.groupId}, #{item.userId}, #{item.createTime})" +
            "</foreach>" +
            "</script>")
    int insertBatch(@Param("members") List<ReviewerGroupMember> members);

    // ========== 删除 ==========
    @Delete("DELETE FROM reviewer_group_member WHERE group_id = #{groupId}")
    int deleteByGroupId(@Param("groupId") Long groupId);

    @Delete("DELETE FROM reviewer_group_member WHERE id = #{id}")
    int deleteById(@Param("id") Long id);
}