package com.eval.gameeval.models.entity;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class ProjectStatisticsScorerDistributionSummary implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long projectId;
    private Long userId;
    private String userName;
    private String scoreRange;
    private Integer count;
    private LocalDateTime updatedTime;
}
