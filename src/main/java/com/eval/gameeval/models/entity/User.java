package com.eval.gameeval.models.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.eval.gameeval.models.enums.enums;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;


@Data
@Accessors(chain = true)
@TableName("sys_user")
public class User {
    private Long id;
    private String username;
    private String password;
    private String name;
    private String role;
    private Boolean isEnabled;
    private Boolean isDeleted;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime deletedTime;
    private Long deleteToken;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
