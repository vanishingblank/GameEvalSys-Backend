package com.eval.gameeval.models.entity;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
@Accessors(chain = true)
public class ScoringRecordDetail implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private Long id;
    private Long recordId;
    private Long indicatorId;
    private BigDecimal score;
}
