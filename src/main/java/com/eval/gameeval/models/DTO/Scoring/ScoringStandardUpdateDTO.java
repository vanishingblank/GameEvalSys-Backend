package com.eval.gameeval.models.DTO.Scoring;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
     * 分类列表（可选）
     */
    @Valid
    private List<CategoryDTO> categories;

    /**
     * 分类DTO
     */
    @Data
    @Accessors(chain = true)
    public static class CategoryDTO implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 分类ID（可选，存在则更新，不存在则新增）
         */
        private Long id;

        /**
         * 分类名称
         */
        @NotBlank(message = "分类名称不能为空")
        private String name;

        /**
         * 分类说明
         */
        private String description;

        /**
         * 分类下指标
         */
        @Valid
        private List<IndicatorDTO> indicators;
    }

    /**
     * 指标DTO
     */
    @Data
    @Accessors(chain = true)
    public static class IndicatorDTO implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 指标ID（可选，存在则更新，不存在则新增）
         */
        private Long id;

        /**
         * 指标名称
         */
        @NotBlank(message = "指标名称不能为空")
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
