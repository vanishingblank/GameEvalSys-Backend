package com.eval.gameeval.models.entity;

import lombok.Data;

@Data
public class ScoringRecord {
    private Long id;
    private Long recordId;
    private Long indicatorId;
    private Integer score;
}
