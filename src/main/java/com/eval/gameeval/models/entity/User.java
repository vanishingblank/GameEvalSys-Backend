package com.eval.gameeval.models.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.eval.gameeval.models.VO.ReviewerGroupInfoVO;
import com.eval.gameeval.models.enums.enums;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;


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
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
    @TableField(exist = false)
    private List<ReviewerGroupInfoVO> reviewerGroups;
}
