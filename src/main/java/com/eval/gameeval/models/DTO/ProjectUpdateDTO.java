package com.eval.gameeval.models.DTO;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

@Data
@Accessors(chain = true)
public class ProjectUpdateDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private String name;

    private String description;

    private LocalDate startDate;

    private LocalDate endDate;

    private Boolean isEnabled;

    private Long standardId;

    private List<Long> groupIds;

    private List<Long> scorerIds;
}
