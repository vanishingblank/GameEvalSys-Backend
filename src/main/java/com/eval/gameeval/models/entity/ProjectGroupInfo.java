package com.eval.gameeval.models.entity;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 小组信息主表实体类
 * 存储小组的基本信息，与项目无关。可以被多个项目使用
 */
@Data
@Accessors(chain = true)
public class ProjectGroupInfo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 小组ID
     */
    private Long id;

    /**
     * 小组名称
     */
    private String name;

    /**
     * 小组描述
     */
    private String description;

    /**
     * 是否启用：1-是 0-否
     */
    private Integer isEnabled;
    /**
     * 是否软删除：1-是 0-否
     */
    private Integer isDeleted;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
