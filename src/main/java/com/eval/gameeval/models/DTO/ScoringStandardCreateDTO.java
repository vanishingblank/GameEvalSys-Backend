package com.eval.gameeval.models.DTO;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
@Accessors(chain = true)
public class ScoringStandardCreateDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    @NotEmpty(message = "指标列表不能为空")
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
