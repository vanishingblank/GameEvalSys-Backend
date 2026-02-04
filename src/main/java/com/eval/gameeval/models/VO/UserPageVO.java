package com.eval.gameeval.models.VO;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Accessors(chain = true)
public class UserPageVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    /**
     * 用户列表
     */
    private List<UserVO> list;

    /**
     * 总记录数
     */
    private Long total;

    /**
     * 当前页码
     */
    private Integer page;

    /**
     * 每页条数
     */
    private Integer size;

    /**
     * 用户VO
     */
    @Data
    @Accessors(chain = true)
    public static class UserVO implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private Long id;
        private String username;
        private String name;
        private String role;
        private Boolean isEnabled;

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime createTime;
    }
}
