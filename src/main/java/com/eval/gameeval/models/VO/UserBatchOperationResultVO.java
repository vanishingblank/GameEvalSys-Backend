package com.eval.gameeval.models.VO;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户批量操作结果VO
 */
@Data
@Accessors(chain = true)
public class UserBatchOperationResultVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Integer totalCount;

    private Integer successCount;

    private Integer failCount;

    private List<Long> failedIds = new ArrayList<>();
}
