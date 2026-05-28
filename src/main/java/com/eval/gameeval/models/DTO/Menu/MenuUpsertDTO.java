package com.eval.gameeval.models.DTO.Menu;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
@Accessors(chain = true)
public class MenuUpsertDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long parentId = 0L;

    @NotBlank(message = "菜单编码不能为空")
    private String menuCode;

    @NotBlank(message = "菜单类型不能为空")
    private String menuType;

    @NotBlank(message = "菜单标题不能为空")
    private String title;

    @NotBlank(message = "路由路径不能为空")
    private String path;

    @NotBlank(message = "路由名称不能为空")
    private String routeName;

    private String icon = "";

    private Boolean hidden = false;

    private String componentCode = "";

    private Integer sortNum = 0;

    private Boolean isEnabled = true;

    @Valid
    private List<String> roleCodes = new ArrayList<>();
}