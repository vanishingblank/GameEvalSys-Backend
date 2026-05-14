package com.eval.gameeval.models.entity;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class ProjectStatisticsIndicatorSummary implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long projectId;
    private Long indicatorId;
    private String indicatorName;
    private BigDecimal rawAverageScore;
    private BigDecimal normalizedAverageScore;
    private BigDecimal processedAverageScore;
    private Integer abnormalCount;
    private Integer totalAbnormalCount;
    private Integer sampleSize;
    private Integer validSampleSize;
    private LocalDateTime updatedTime;
}
