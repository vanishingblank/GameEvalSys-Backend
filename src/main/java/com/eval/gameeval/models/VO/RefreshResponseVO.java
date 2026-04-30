package com.eval.gameeval.models.VO;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class RefreshResponseVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 访问令牌（Access Token）
     */
    private String token;

    /**
     * 刷新令牌（仅服务端用于写入Cookie，不下发到响应体）
     */
    @JsonIgnore
    private String refreshToken;

    /**
     * 会话ID
     */
    private String sid;

    /**
     * Token过期时间（毫秒）
     * 前端可用于倒计时提醒
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expireTime;
}
