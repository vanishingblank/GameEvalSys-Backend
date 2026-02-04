package com.eval.gameeval.models.DTO;

import jakarta.validation.constraints.Min;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;

@Data
@Accessors(chain = true)
public class UserQueryDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 页码（默认1）
     */
    @Min(value = 1, message = "页码必须大于0")
    private Integer page = 1;

    /**
     * 每页条数（默认10）
     */
    @Min(value = 1, message = "每页条数必须大于0")
    private Integer size = 10;

    /**
     * 角色
     */
    private String role;
}
