package com.eval.gameeval.models.DTO.ReviewerGroup;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 评审组创建DTO
 */
@Data
@Accessors(chain = true)
public class ReviewerGroupCreateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "评审组名称不能为空")
    private String name;

    private String description;

    private Boolean isEnabled = true;

    @NotNull(message = "成员用户ID列表不能为空")
    private List<Long> memberIds;
}