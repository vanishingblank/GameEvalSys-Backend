package com.eval.gameeval.models.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 评审组成员实体
 */
@Data
@Accessors(chain = true)
public class ReviewerGroupMember implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long groupId;
    private Long userId;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}