package com.eval.gameeval.models.entity;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class ProjectGroup implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 关联ID
     */
    private Long id;

    /**
     * 项目ID
     */
    private Long projectId;

    /**
     * 小组信息ID（关联 project_group_info）
     */
    private Long groupInfoId;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
