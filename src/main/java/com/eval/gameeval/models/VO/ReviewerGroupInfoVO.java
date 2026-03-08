package com.eval.gameeval.models.VO;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;

/**
 * 评审组信息VO（用于用户列表响应）
 */
@Data
@Accessors(chain = true)
public class ReviewerGroupInfoVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 评审组ID
     */
    private Long id;

    /**
     * 评审组名称
     */
    private String name;
}