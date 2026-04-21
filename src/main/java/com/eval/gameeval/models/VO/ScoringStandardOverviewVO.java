package com.eval.gameeval.models.VO;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@Accessors(chain = true)
public class ScoringStandardOverviewVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long totalStandards;
    private Long enabledStandards;
}
