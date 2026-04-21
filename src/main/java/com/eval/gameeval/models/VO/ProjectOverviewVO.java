package com.eval.gameeval.models.VO;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@Accessors(chain = true)
public class ProjectOverviewVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long totalProjects;
    private Long notStartedProjects;
    private Long ongoingProjects;
    private Long endedProjects;
}
