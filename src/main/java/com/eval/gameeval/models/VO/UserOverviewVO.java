package com.eval.gameeval.models.VO;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@Accessors(chain = true)
public class UserOverviewVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long totalUsers;
    private Long adminUsers;
    private Long scorerUsers;
    private Long normalUsers;
}
