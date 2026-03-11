package com.eval.gameeval.models.VO;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 打分记录VO
 */
@Data
@Accessors(chain = true)
public class ScoringRecordVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long projectId;
    private Long groupId;
    private Long userId;

    private List<ScoreVO> scores;
    private BigDecimal totalScore;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    @Data
    @Accessors(chain = true)
    public static class ScoreVO implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private Long indicatorId;
        private BigDecimal score;
    }
}