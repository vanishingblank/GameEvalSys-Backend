package com.eval.gameeval.service;

import com.eval.gameeval.models.DTO.Project.ProjectCreateDTO;
import com.eval.gameeval.models.DTO.Project.ProjectQueryDTO;
import com.eval.gameeval.models.DTO.Project.ProjectUpdateDTO;
import com.eval.gameeval.models.VO.ProjectCreateVO;
import com.eval.gameeval.models.VO.ProjectPageVO;
import com.eval.gameeval.models.VO.ProjectVO;
import com.eval.gameeval.models.VO.ResponseVO;

public interface IProjectService {
    ResponseVO<ProjectCreateVO> createProject(String token, ProjectCreateDTO request);

    ResponseVO<Void> updateProject(String token, Long projectId, ProjectUpdateDTO request);

    ResponseVO<Void> endProject(String token, Long projectId);

    ResponseVO<ProjectPageVO> getProjectList(String token, ProjectQueryDTO query);

    ResponseVO<ProjectVO> getProjectDetail(String token, Long projectId);

    ResponseVO<ProjectPageVO> getAuthorizedProjects(String token, ProjectQueryDTO query);
}
