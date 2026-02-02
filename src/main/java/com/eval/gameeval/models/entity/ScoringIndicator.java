package com.eval.gameeval.models.entity;

import lombok.Data;

@Data
public class ScoringIndicator {
    private Long id;
    private Long standardId;
    private String name;
    private String description;
    private Integer minScore;
    private Integer maxScore;
}
