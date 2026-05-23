package com.eval.gameeval.models.VO;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
@Accessors(chain = true)
public class RouteNodeVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String menuCode;
    private String path;
    private String routeName;
    private String title;
    private String icon;
    private Boolean hidden;
    private String componentCode;
    private List<String> permissionCodes = new ArrayList<>();
    private List<RouteNodeVO> children = new ArrayList<>();
}