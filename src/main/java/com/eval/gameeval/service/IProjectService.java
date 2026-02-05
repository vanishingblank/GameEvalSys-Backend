package com.eval.gameeval.service;

import com.eval.gameeval.models.DTO.ProjectCreateDTO;
import com.eval.gameeval.models.DTO.ProjectQueryDTO;
import com.eval.gameeval.models.DTO.ProjectUpdateDTO;
import com.eval.gameeval.models.VO.ProjectPageVO;
import com.eval.gameeval.models.VO.ProjectVO;
import com.eval.gameeval.models.VO.ResponseVO;

import java.util.List;

public interface IProjectService {
    ResponseVO<ProjectVO> createProject(String token, ProjectCreateDTO request);

    ResponseVO<Void> updateProject(String token, Long projectId, ProjectUpdateDTO request);

    ResponseVO<Void> endProject(String token, Long projectId);

    ResponseVO<ProjectPageVO> getProjectList(String token, ProjectQueryDTO query);

    ResponseVO<ProjectVO> getProjectDetail(String token, Long projectId);

    ResponseVO<List<ProjectVO>> getAuthorizedProjects(String token);
}
