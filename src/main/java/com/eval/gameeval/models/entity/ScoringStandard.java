package com.eval.gameeval.models.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ScoringStandard {
    private Long id;
    private String name;
    private String description;
    private LocalDateTime createTime;
}
