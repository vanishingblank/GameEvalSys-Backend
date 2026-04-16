package com.eval.gameeval.models.DTO.Scoring;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
@Accessors(chain = true)
public class ScoringStandardUpdateDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 打分标准名称（可选）
     */
    private String name;

    /**
     * 指标列表（可选）
     */
    @Valid
    private List<IndicatorDTO> indicators;

    /**
     * 指标DTO
     */
    @Data
    @Accessors(chain = true)
    public static class IndicatorDTO implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 指标ID（必填，用于识别指标）
         */
        @NotNull(message = "指标ID不能为空")
        private Long id;

        /**
         * 指标名称
         */
        @NotNull(message = "指标名称不能为空")
        private String name;

        /**
         * 指标说明
         */
        private String description;

        /**
         * 分值最小值
         */
        @NotNull(message = "分值最小值不能为空")
        private Integer minScore;

        /**
         * 分值最大值
         */
        @NotNull(message = "分值最大值不能为空")
        private Integer maxScore;
    }
}
