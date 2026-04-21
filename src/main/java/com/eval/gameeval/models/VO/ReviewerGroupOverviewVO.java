package com.eval.gameeval.models.VO;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@Accessors(chain = true)
public class ReviewerGroupOverviewVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long totalGroups;
    private Long activeGroups;
    private Long totalMembers;
    private BigDecimal avgGroupSize;
}
