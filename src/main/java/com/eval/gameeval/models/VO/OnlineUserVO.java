package com.eval.gameeval.models.VO;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;

@Data
@Accessors(chain = true)
public class OnlineUserVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String username;
    private String name;
    private String role;
    private Boolean isEnabled;
    private Integer onlineCount;
    private String lastActiveAt;
    private String lastLoginAt;
}
