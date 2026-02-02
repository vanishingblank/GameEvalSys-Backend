package com.eval.gameeval.models.entity;

import com.eval.gameeval.models.enums.enums;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;


@Data
public class User {
    private Long id;
    private String username;
    private String password;
    private String name;
    private String role;
    private Boolean isEnabled;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
