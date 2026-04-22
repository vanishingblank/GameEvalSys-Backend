package com.eval.gameeval.init;

import com.eval.gameeval.service.impl.GroupServiceImpl;
import com.eval.gameeval.service.impl.ProjectServiceImpl;
import com.eval.gameeval.service.impl.ReviewerGroupServiceImpl;
import com.eval.gameeval.service.impl.ScoringStandardServiceImpl;
import com.eval.gameeval.service.impl.UserServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OverviewWarmupService {

    private final ProjectServiceImpl projectService;
    private final ScoringStandardServiceImpl scoringStandardService;
    private final UserServiceImpl userService;
    private final GroupServiceImpl groupService;
    private final ReviewerGroupServiceImpl reviewerGroupService;

    public OverviewWarmupService(ProjectServiceImpl projectService,
                                 ScoringStandardServiceImpl scoringStandardService,
                                 UserServiceImpl userService,
                                 GroupServiceImpl groupService,
                                 ReviewerGroupServiceImpl reviewerGroupService) {
        this.projectService = projectService;
        this.scoringStandardService = scoringStandardService;
        this.userService = userService;
        this.groupService = groupService;
        this.reviewerGroupService = reviewerGroupService;
    }

    public void warmupOverviewCaches() {
        try {
            projectService.warmupProjectOverviewCache();
        } catch (Exception e) {
            log.error("概览预热异常: 项目概览", e);
        }

        try {
            scoringStandardService.warmupStandardOverviewCache();
        } catch (Exception e) {
            log.error("概览预热异常: 打分标准概览", e);
        }

        try {
            userService.warmupUserOverviewCache();
        } catch (Exception e) {
            log.error("概览预热异常: 用户概览", e);
        }

        try {
            groupService.warmupGroupOverviewCache();
        } catch (Exception e) {
            log.error("概览预热异常: 小组概览", e);
        }

        try {
            reviewerGroupService.warmupReviewerGroupOverviewCache();
        } catch (Exception e) {
            log.error("概览预热异常: 评审组概览", e);
        }
    }
}
