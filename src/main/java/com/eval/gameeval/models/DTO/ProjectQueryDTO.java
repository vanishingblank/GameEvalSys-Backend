package com.eval.gameeval.models.DTO;

import jakarta.validation.constraints.Min;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;

@Data
@Accessors(chain = true)
public class ProjectQueryDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    @Min(value = 1, message = "页码必须大于0")
    private Integer page = 1;

    @Min(value = 1, message = "每页条数必须大于0")
    private Integer size = 10;

    private String status;

    private Boolean isEnabled;
}
