package com.eval.gameeval.models.VO;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;

@Data
@Accessors(chain = true)
public class SessionInfoVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String sid;
    private String username;
    private String role;
    private String loginAt;
    private String lastActiveAt;
    private String status;
}
