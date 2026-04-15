package com.eval.gameeval.models.DTO.Group;

import jakarta.validation.constraints.NotBlank;
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
     * 是否启用 (0=禁用, 1=启用，默认启用)
     */
    private Integer isEnabled = 1;
}
