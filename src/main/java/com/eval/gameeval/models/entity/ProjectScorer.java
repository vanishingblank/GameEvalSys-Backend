package com.eval.gameeval.models.entity;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class ProjectScorer implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private Long id;
    private Long projectId;
    private Long userId;
    private LocalDateTime createTime;
}
