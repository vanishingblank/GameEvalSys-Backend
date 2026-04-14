package com.eval.gameeval.models.VO;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 项目内小组指标平均得分明细VO
 */
@Data
@Accessors(chain = true)
public class GroupIndicatorStatisticsVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long groupId;
    private String groupName;
    private List<IndicatorAverageVO> indicatorAverage;

    @Data
    @Accessors(chain = true)
    public static class IndicatorAverageVO implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private Long indicatorId;
        private String indicatorName;
        private BigDecimal averageScore;
    }
}
