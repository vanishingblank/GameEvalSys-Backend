package com.eval.gameeval.models.VO;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 项目统计VO
 */
@Data
@Accessors(chain = true)
public class ProjectStatisticsVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 小组平均分
     */
    private List<GroupAverageVO> groupAverage;

    /**
     * 指标平均分
     */
    private List<IndicatorAverageVO> indicatorAverage;

    /**
     * 打分用户分布
     */
    private List<ScorerDistributionVO> scorerDistribution;

    /**
     * 小组平均分VO
     */
    @Data
    @Accessors(chain = true)
    public static class GroupAverageVO implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private Long groupId;
        private String groupName;
        private BigDecimal averageScore;
    }

    /**
     * 指标平均分VO
     */
    @Data
    @Accessors(chain = true)
    public static class IndicatorAverageVO implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private Long indicatorId;
        private String indicatorName;
        private BigDecimal averageScore;
    }

    /**
     * 打分用户分布VO
     */
    @Data
    @Accessors(chain = true)
    public static class ScorerDistributionVO implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private Long userId;
        private String userName;
        private String scoreRange;
        private Integer count;
    }
}