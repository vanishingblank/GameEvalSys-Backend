package com.eval.gameeval.models.DTO.ReviewerGroup;

import jakarta.validation.constraints.Min;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;

/**
 * 评审组查询DTO（支持关键词搜索和分页）
 */
@Data
@Accessors(chain = true)
public class ReviewerGroupQueryDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Min(value = 1, message = "页码必须大于0")
    private Integer page = 1;

    @Min(value = 1, message = "每页条数必须大于0")
    private Integer size = 10;

    /**
     * 关键词搜索（匹配名称/描述，非必填）
     * 支持模糊查询：输入"中期"可匹配"中期答辩评审组"
     */
    private String keyWords;
}