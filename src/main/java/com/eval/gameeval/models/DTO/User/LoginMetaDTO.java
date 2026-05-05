package com.eval.gameeval.models.DTO.User;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;

@Data
@Accessors(chain = true)
public class LoginMetaDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String ip;
    private String device;
    private String loginLocation;
}
