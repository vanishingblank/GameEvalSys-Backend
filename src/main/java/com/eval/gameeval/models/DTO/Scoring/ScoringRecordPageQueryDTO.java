package com.eval.gameeval.models.DTO.Scoring;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;

/**
 * 项目打分页记录查询DTO
 */
@Data
@Accessors(chain = true)
public class ScoringRecordPageQueryDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Min(value = 1, message = "页码必须大于0")
    private Integer page = 1;

    @Min(value = 1, message = "每页条数必须大于0")
    private Integer size = 10;

    @Min(value = 0, message = "恶意标记筛选值只能为0或1")
    @Max(value = 1, message = "恶意标记筛选值只能为0或1")
    private Integer isMalicious;
}
