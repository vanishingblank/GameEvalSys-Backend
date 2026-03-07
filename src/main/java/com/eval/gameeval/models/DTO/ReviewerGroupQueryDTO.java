package com.eval.gameeval.models.DTO;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;

/**
 * 评审组查询DTO（支持关键词搜索）
 */
@Data
@Accessors(chain = true)
public class ReviewerGroupQueryDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 关键词搜索（匹配名称/描述，非必填）
     * 支持模糊查询：输入"中期"可匹配"中期答辩评审组"
     */
    private String keyWords;
}