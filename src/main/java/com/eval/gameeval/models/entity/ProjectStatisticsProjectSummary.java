package com.eval.gameeval.models.entity;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class ProjectStatisticsProjectSummary implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long projectId;
    private BigDecimal rawAverageScore;
    private BigDecimal normalizedAverageScore;
    private BigDecimal processedAverageScore;
    private Integer abnormalCount;
    private Integer sampleSize;
    private Integer validSampleSize;
    private LocalDateTime updatedTime;
}
