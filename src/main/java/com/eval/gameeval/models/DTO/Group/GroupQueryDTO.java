package com.eval.gameeval.models.DTO.Group;

import jakarta.validation.constraints.Min;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;

/**
 * 小组查询DTO
 */
@Data
@Accessors(chain = true)
public class GroupQueryDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Min(value = 1, message = "页码必须大于0")
    private Integer page = 1;

    @Min(value = 1, message = "每页条数必须大于0")
    private Integer size = 10;

    /**
     * 关键词搜索（匹配小组名称/项目名称）
     */
    private String keyWords;
}