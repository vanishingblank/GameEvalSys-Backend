package com.eval.gameeval.models.DTO.Group;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;

/**
 * 小组加入项目请求DTO
 */
@Data
@Accessors(chain = true)
public class GroupAddToProjectDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 小组ID
     */
    @NotNull(message = "小组ID不能为空")
    private Long groupId;

    /**
     * 项目ID
     */
    @NotNull(message = "项目ID不能为空")
    private Long projectId;
}
