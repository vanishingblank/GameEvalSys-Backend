package com.eval.gameeval.models.DTO.User;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RefreshRequestDTO {

    @NotBlank(message = "sid不能为空")
    private String sid;
}
