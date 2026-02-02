package com.eval.gameeval.models.DTO;

import java.time.LocalDateTime;
import java.util.List;

public class ProjectCreateDTO {
    public String name;
    public String description;
    public LocalDateTime startDate;
    public LocalDateTime endDate;
    public Long standardId;
    public List<Long> scorerIds;
}
