package com.eval.gameeval.service;

import com.eval.gameeval.models.VO.SystemMonitorVO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface ISystemMonitorService {

    SystemMonitorVO getDashboard();

    SystemMonitorVO.OverviewVO getOverview();

    SystemMonitorVO.HealthVO getHealth();

    SystemMonitorVO.DataSourceVO getDataSource();

    SystemMonitorVO.RedisVO getRedis();

    SystemMonitorVO.JvmVO getJvm();

    SystemMonitorVO.OsVO getOs();

    SystemMonitorVO.ConfigVO getConfig();

    List<SystemMonitorVO.LogVO> getLogs();

    List<SystemMonitorVO.LogVO> getLogs(int limit);

    SseEmitter openStream();
}
