package com.eval.gameeval.models.DTO.Group;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;

/**
 * 编辑小组请求DTO
 */
@Data
@Accessors(chain = true)
public class GroupUpdateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 小组ID
     */
    @NotNull(message = "小组ID不能为空")
    private Long id;

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
     * 是否启用 (0=禁用, 1=启用)
     */
    private Integer isEnabled;
}
