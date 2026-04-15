package com.eval.gameeval.models.DTO.ReviewerGroup;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 评审组更新DTO
 */
@Data
@Accessors(chain = true)
public class ReviewerGroupUpdateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 评审组名称（可选）
     */
    private String name;

    /**
     * 评审组描述（可选）
     */
    private String description;

    /**
     * 是否启用（可选）
     */
    private Boolean isEnabled;

    /**
     * 评审组成员ID列表（可选，全量替换）
     * 传入null或空列表会清空所有成员
     */
    private List<Long> memberIds;
}