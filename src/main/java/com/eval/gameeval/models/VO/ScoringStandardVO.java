package com.eval.gameeval.models.VO;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Accessors(chain = true)
public class ScoringStandardVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    /**
     * 标准ID
     */
    private Long id;

    /**
     * 指标列表
     */
    private List<IndicatorVO> indicators;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /**
     * 指标VO
     */
    @Data
    @Accessors(chain = true)
    public static class IndicatorVO implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 指标ID
         */
        private Long id;

        /**
         * 指标名称
         */
        private String name;

        /**
         * 指标说明
         */
        private String description;

        /**
         * 分值最小值
         */
        private Integer minScore;

        /**
         * 分值最大值
         */
        private Integer maxScore;
    }
}
