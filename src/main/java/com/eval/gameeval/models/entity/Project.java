package com.eval.gameeval.models.entity;

import com.eval.gameeval.models.enums.enums;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Project {
    private Long id;
    private String name;
    private String description;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private enums.ProjectStatus status;
    private Boolean enabled;
    private Long standardId;
    private Long creatorId;
    private LocalDateTime createTime;
}
