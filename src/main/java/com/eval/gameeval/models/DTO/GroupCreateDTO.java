package com.eval.gameeval.models.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;

/**
 * 创建小组请求DTO
 */
@Data
@Accessors(chain = true)
public class GroupCreateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 小组名称
     */
    @NotBlank(message = "小组名称不能为空")
    private String name;

    /**
     * 小组描述
     */
    private String description;

    /**
     * 项目ID
     */
    @NotNull(message = "项目ID不能为空")
    private Long projectId;
}
