package com.eval.gameeval.models.DTO;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 批量修改用户状态DTO
 */
@Data
@Accessors(chain = true)
public class UserBatchStatusDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 需要批量操作的用户ID列表
     */
    @NotEmpty(message = "用户ID列表不能为空")
    @Size(min = 1, max = 100, message = "一次最多操作100个用户")
    private List<Long> userIds;

    /**
     * 目标启用状态
     */
    @NotNull(message = "目标状态不能为空")
    private Boolean isEnabled;
}
