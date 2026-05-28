package com.eval.gameeval.models.VO;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
@Accessors(chain = true)
public class MenuVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long parentId;
    private String menuCode;
    private String menuType;
    private String title;
    private String path;
    private String routeName;
    private String icon;
    private Boolean hidden;
    private String componentCode;
    private Integer sortNum;
    private Boolean isEnabled;
    private Boolean isDeleted;
    private List<String> roleCodes = new ArrayList<>();
    private List<MenuVO> children = new ArrayList<>();
}