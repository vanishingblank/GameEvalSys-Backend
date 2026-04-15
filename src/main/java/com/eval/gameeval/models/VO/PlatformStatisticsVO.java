package com.eval.gameeval.models.VO;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 平台全局统计
 */
@Data
@Accessors(chain = true)
public class PlatformStatisticsVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * x轴日期（过去30天）
     */
    private List<String> dates;

    /**
     * 过去30天累计项目数趋势
     */
    private List<Long> projectTrend;

    /**
     * 过去30天每日新增评分数趋势
     */
    private List<Long> scoreTrend;

    /**
     * 过去30天每日平均得分趋势
     */
    private List<BigDecimal> averageScoreTrend;
}
