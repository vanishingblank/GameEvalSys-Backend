package com.eval.gameeval.models.VO;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
@Accessors(chain = true)
public class ScoringStandardPageVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 打分标准列表
     */
    private List<ScoringStandardVO> list;

    /**
     * 总记录数
     */
    private Long total;

    /**
     * 当前页码
     */
    private Integer page;

    /**
     * 每页条数
     */
    private Integer size;
}
