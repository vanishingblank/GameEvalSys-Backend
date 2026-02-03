package com.eval.gameeval.models.DTO;

import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@Accessors(chain = true)
public class UserUpdateDTO implements Serializable {
    private static final long serialVersionUID = 1L;


    @Pattern(regexp = "^.{1,50}$", message = "姓名长度必须在1-50之间")
    private String name;


    @Pattern(regexp = "^(super_admin|admin|scorer|normal)$", message = "角色不正确")
    private String role;

    private Boolean isEnabled;
}
