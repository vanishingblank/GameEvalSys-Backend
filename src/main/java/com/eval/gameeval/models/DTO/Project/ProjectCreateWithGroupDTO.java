package com.eval.gameeval.models.DTO.Project;

import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/**
 * 通过评审组创建项目DTO
 */
@Data
@Accessors(chain = true)
public class ProjectCreateWithGroupDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "项目名称不能为空")
    @Size(max = 200, message = "项目名称长度不能超过200")
    private String name;

    @Size(max = 1000, message = "项目描述长度不能超过1000")
    private String description;

    @NotNull(message = "起始日期不能为空")
    @FutureOrPresent(message = "起始日期必须是今天或未来日期")
    private LocalDate startDate;

    @NotNull(message = "结束日期不能为空")
    @FutureOrPresent(message = "结束日期必须是今天或未来日期")
    private LocalDate endDate;

    private Boolean isEnabled = true;

    @NotNull(message = "打分标准ID不能为空")
    @Min(value = 1, message = "打分标准ID必须大于0")
    private Long standardId;

    @NotEmpty(message = "小组ID列表不能为空")
    @Size(min = 1, message = "至少需要一个小组")
    private List<Long> groupIds;

    @NotNull(message = "评审组ID不能为空")
    @Min(value = 1, message = "评审组ID必须大于0")
    private Long reviewerGroupId;
}