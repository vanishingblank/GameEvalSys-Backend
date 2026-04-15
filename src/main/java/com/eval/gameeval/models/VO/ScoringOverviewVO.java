package com.eval.gameeval.models.VO;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;

@Data
@Accessors(chain = true)
public class ScoringOverviewVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long totalProjects;
    private Long ongoingProjects;
    private Long completedProjects;
    private Long pendingProjects;
}
