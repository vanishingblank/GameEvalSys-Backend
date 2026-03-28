package com.eval.gameeval.models.DTO;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;

/**
 * 打分记录查询DTO
 */
@Data
@Accessors(chain = true)
public class ScoringRecordQueryDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "项目ID不能为空")
    private Long projectId;

    @NotNull(message = "小组信息ID不能为空")
    private Long groupId;
}
