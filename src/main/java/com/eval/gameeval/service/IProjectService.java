package com.eval.gameeval.service;

import com.eval.gameeval.models.DTO.Project.ProjectCreateDTO;
import com.eval.gameeval.models.DTO.Project.ProjectQueryDTO;
import com.eval.gameeval.models.DTO.Project.ProjectUpdateDTO;
import com.eval.gameeval.models.VO.ProjectCreateVO;
import com.eval.gameeval.models.VO.ProjectOverviewVO;
import com.eval.gameeval.models.VO.ProjectPageVO;
import com.eval.gameeval.models.VO.ProjectVO;
import com.eval.gameeval.models.VO.ResponseVO;

public interface IProjectService {
    ResponseVO<ProjectCreateVO> createProject(Long currentUserId, ProjectCreateDTO request);

    ResponseVO<Void> updateProject(Long currentUserId, Long projectId, ProjectUpdateDTO request);

    ResponseVO<Void> endProject(Long currentUserId, Long projectId);

    ResponseVO<ProjectPageVO> getProjectList(Long currentUserId, ProjectQueryDTO query);

    ResponseVO<ProjectVO> getProjectDetail(Long currentUserId, Long projectId);

    ResponseVO<ProjectPageVO> getAuthorizedProjects(Long currentUserId, ProjectQueryDTO query);

    ResponseVO<ProjectOverviewVO> getProjectOverview(Long currentUserId);
}
