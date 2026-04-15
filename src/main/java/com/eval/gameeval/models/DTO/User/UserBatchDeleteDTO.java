package com.eval.gameeval.models.DTO.User;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 批量删除用户DTO
 */
@Data
@Accessors(chain = true)
public class UserBatchDeleteDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 需要批量删除的用户ID列表
     */
    @NotEmpty(message = "用户ID列表不能为空")
    @Size(min = 1, max = 100, message = "一次最多操作100个用户")
    private List<Long> userIds;
}
