package com.eval.gameeval.models.DTO.Project;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Accessors(chain = true)
public class ProjectUpdateDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private String name;

    private String description;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime startDate;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime endDate;

    private Boolean isEnabled;

    private Long standardId;

    private List<Long> groupIds;

    private List<Long> scorerIds;
}
