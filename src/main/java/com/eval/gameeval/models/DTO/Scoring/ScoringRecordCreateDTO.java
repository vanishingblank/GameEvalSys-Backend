package com.eval.gameeval.models.DTO.Scoring;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 打分记录创建DTO
 */
@Data
@Accessors(chain = true)
public class ScoringRecordCreateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "项目ID不能为空")
    private Long projectId;

    @NotNull(message = "小组信息ID不能为空")
    private Long groupId;

    @Valid
    @NotNull(message = "打分列表不能为空")
    private List<ScoreDTO> scores;

    @Data
    @Accessors(chain = true)
    public static class ScoreDTO implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        @NotNull(message = "指标ID不能为空")
        private Long indicatorId;

        @NotNull(message = "打分值不能为空")
        private BigDecimal score;
    }
}
