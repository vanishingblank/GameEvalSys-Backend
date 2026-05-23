package com.eval.gameeval.models.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("sys_menu")
public class Menu {
    private Long id;
    private Long parentId;
    private String menuCode;
    private String menuType;
    private String title;
    private String path;
    private String routeName;
    private String icon;
    private Boolean hidden;
    private String componentCode;
    private Integer sortNum;
    private Boolean isEnabled;
    private Boolean isDeleted;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}