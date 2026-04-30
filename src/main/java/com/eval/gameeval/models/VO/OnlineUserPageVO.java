package com.eval.gameeval.models.VO;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
@Accessors(chain = true)
public class OnlineUserPageVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private List<OnlineUserVO> list;
    private Long total;
    private Integer page;
    private Integer size;
}
