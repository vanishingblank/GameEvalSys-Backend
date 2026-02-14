package com.eval.gameeval.models.VO;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class LoginResponseVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 访问令牌（Access Token）
     * 有效期：4小时
     */
    private String Token;




    /**
     * Token过期时间（毫秒）
     * 前端可用于倒计时提醒
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expireTime;

    /**
     * 用户信息
     */
    private UserInfoVO userInfo;

    /**
     * 用户信息VO
     */
    @Data
    @Accessors(chain = true)
    public static class UserInfoVO implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 用户ID
         */
        private Long id;

        /**
         * 用户名
         */
        private String username;


        /**
         * 角色
         * super_admin: 超级管理员
         * admin: 管理员
         * scorer: 打分用户
         * normal: 普通用户
         */
        private String role;

        /**
         * 真实姓名
         */
        private String name;
    }
}
