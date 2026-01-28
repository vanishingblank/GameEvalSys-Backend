package com.eval.gameeval.models.entity;

import com.eval.gameeval.models.enums.enums;
import lombok.Data;

import java.time.LocalDateTime;


@Data
public class User {
    private Long id;
    private String username;
    private String password;
    private enums.UserRole role;
    private Boolean enabled;
    private LocalDateTime createTime;
}
