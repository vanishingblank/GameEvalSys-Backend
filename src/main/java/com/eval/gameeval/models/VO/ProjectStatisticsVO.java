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
        /**
         * 兼容旧字段，等于处理后平均分
         */
        private BigDecimal averageScore;
        /**
         * 原始平均分
         */
        private BigDecimal rawAverageScore;
        /**
         * 标准化后平均分（异常剔除前）
         */
        private BigDecimal normalizedAverageScore;
        /**
         * 处理后平均分（标准化 + 异常剔除）
         */
        private BigDecimal processedAverageScore;
        /**
         * 判定为异常的评分数
         */
        private Integer abnormalCount;
        /**
         * 总样本数
         */
        private Integer sampleSize;
        /**
         * 有效样本数
         */
        private Integer validSampleSize;
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
        /**
         * 兼容旧字段，等于处理后平均分
         */
        private BigDecimal averageScore;
        /**
         * 原始平均分
         */
        private BigDecimal rawAverageScore;
        /**
         * 标准化后平均分（异常剔除前）
         */
        private BigDecimal normalizedAverageScore;
        /**
         * 处理后平均分（标准化 + 异常剔除）
         */
        private BigDecimal processedAverageScore;
        /**
         * 判定为异常的评分数
         */
        private Integer abnormalCount;
        /**
         * 总样本数
         */
        private Integer sampleSize;
        /**
         * 有效样本数
         */
        private Integer validSampleSize;
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