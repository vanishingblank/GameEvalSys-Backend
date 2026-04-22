package com.eval.gameeval.models.DTO.Group;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;

/**
 * 批量创建小组请求DTO
 */
@Data
@Accessors(chain = true)
public class GroupBatchCreateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 小组名称前缀
     */
    @NotBlank(message = "小组名称前缀不能为空")
    private String prefixName;

    /**
     * 小组名称主体
     */
    @NotBlank(message = "小组名称不能为空")
    private String name;

    /**
     * 小组数字开始下标
     */
    @NotBlank(message = "小组数字开始下标不能为空")
    @Pattern(regexp = "^\\d+$", message = "小组数字开始下标必须为非负整数")
    private String startIndex;

    /**
     * 小组数字结束下标
     */
    @Pattern(regexp = "^\\d*$", message = "小组数字结束下标必须为非负整数")
    private String endIndex;

    /**
     * 小组描述
     */
    private String description;

    /**
     * 是否启用 (0=禁用, 1=启用，默认启用)
     */
    @Min(value = 0, message = "是否启用只能为0或1")
    @Max(value = 1, message = "是否启用只能为0或1")
    private Integer isEnabled = 1;
}
