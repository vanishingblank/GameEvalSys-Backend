package com.eval.gameeval.models.VO;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@Accessors(chain = true)
public class OnlineUserOverviewVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 系统用户总数（受筛选条件影响后的总量）
     */
    private Long totalUsers;

    /**
     * 当前在线用户数（onlineCount > 0 的用户数，全量非分页）
     */
    private Long onlineUserCount;

    /**
     * 活跃会话总量（所有在线用户 onlineCount 之和，全量非分页）
     */
    private Long activeSessionCount;

    /**
     * 被禁用账号数（isEnabled === false，全量非分页）
     */
    private Long disabledUserCount;
}
