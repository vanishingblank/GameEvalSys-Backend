package com.eval.gameeval.models.VO;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 评审组分页VO
 */
@Data
@Accessors(chain = true)
public class ReviewerGroupPageVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private List<ReviewerGroupVO> list;
    private Long total;
    private Integer page;
    private Integer size;

    /**
     * 评审组VO（嵌入在PageVO中）
     */
    @Data
    @Accessors(chain = true)
    public static class ReviewerGroupVO implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private Long id;
        private String name;
        private String description;
        private Long creatorId;
        private Boolean isEnabled;

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime createTime;

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime updateTime;

        private List<Long> memberIds;  // 评审组成员列表
    }
}
