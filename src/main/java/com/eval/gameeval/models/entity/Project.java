package com.eval.gameeval.models.entity;

import com.eval.gameeval.models.enums.enums;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class Project implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private Long id;
    private String name;
    private String description;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private String status;
    private Boolean isEnabled;
    private Long standardId;
    private Long creatorId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
