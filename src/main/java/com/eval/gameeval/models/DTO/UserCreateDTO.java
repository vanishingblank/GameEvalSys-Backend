package com.eval.gameeval.models.DTO;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.List;

@Data
@Accessors(chain = true)
public class UserCreateDTO implements Serializable {
    @NotEmpty(message = "用户列表不能为空")
    @Valid
    private List<UserDTO> users;

    @Data
    @Accessors(chain = true)
    public static class UserDTO {
        @NotNull(message = "用户名不能为空")
        private String username;

        @NotNull(message = "密码不能为空")
        private String password;

        @NotNull(message = "真实姓名不能为空")
        private String name;

        @NotNull(message = "角色不能为空")
        private String role;

        private Boolean isEnabled = true;
    }
}
