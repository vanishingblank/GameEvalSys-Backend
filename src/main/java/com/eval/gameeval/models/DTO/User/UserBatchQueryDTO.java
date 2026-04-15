package com.eval.gameeval.models.DTO.User;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 批量查询用户DTO
 */
@Data
@Accessors(chain = true)
public class UserBatchQueryDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户ID列表（限制一次最多查询100个）
     */
    @NotEmpty(message = "用户ID列表不能为空")
    @Size(min = 1, max = 100, message = "一次最多查询100个用户")
    private List<Long> ids;

    /**
     * 是否包含已禁用用户（默认：只查询启用的）
     */
    private Boolean includeDisabled = false;
}