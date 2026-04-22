package com.eval.gameeval.models.VO;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 批量创建小组响应VO
 */
@Data
@Accessors(chain = true)
public class GroupBatchCreateVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private List<GroupItemVO> list;

    @Data
    @Accessors(chain = true)
    public static class GroupItemVO implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private Long id;

        private String name;

        private String description;

        private Integer isEnabled;

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime createTime;

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime updateTime;
    }
}
