package com.eval.gameeval.models.VO;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 小组分页VO
 */
@Data
@Accessors(chain = true)
public class GroupPageVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private List<GroupVO> list;
    private Long total;
    private Integer page;
    private Integer size;

    /**
     * 小组VO
     */
    @Data
    @Accessors(chain = true)
    public static class GroupVO implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private Long id;
        private String name;
        private Long projectId;

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime createTime;

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime updateTime;
    }
}