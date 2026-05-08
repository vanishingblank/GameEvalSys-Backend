package com.eval.gameeval.controller;

import com.eval.gameeval.aspect.LogRecord;
import com.eval.gameeval.models.VO.ResponseVO;
import com.eval.gameeval.models.VO.SystemMonitorVO;
import com.eval.gameeval.service.SystemMonitorService;
import jakarta.annotation.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/monitor")
public class SystemMonitorController {

    @Resource
    private SystemMonitorService systemMonitorService;

    @GetMapping("/dashboard")
    @LogRecord(value = "查询服务监控总览", module = "SystemMonitor")
    public ResponseEntity<ResponseVO<SystemMonitorVO>> getDashboard() {
        return ResponseEntity.ok(ResponseVO.success("查询成功", systemMonitorService.getDashboard()));
    }

    @GetMapping("/overview")
    @LogRecord(value = "查询服务监控总览信息", module = "SystemMonitor")
    public ResponseEntity<ResponseVO<SystemMonitorVO.OverviewVO>> getOverview() {
        return ResponseEntity.ok(ResponseVO.success("查询成功", systemMonitorService.getOverview()));
    }

    @GetMapping("/health")
    @LogRecord(value = "查询服务健康状态", module = "SystemMonitor")
    public ResponseEntity<ResponseVO<SystemMonitorVO.HealthVO>> getHealth() {
        return ResponseEntity.ok(ResponseVO.success("查询成功", systemMonitorService.getHealth()));
    }

    @GetMapping("/datasource")
    @LogRecord(value = "查询数据源状态", module = "SystemMonitor")
    public ResponseEntity<ResponseVO<SystemMonitorVO.DataSourceVO>> getDataSource() {
        return ResponseEntity.ok(ResponseVO.success("查询成功", systemMonitorService.getDataSource()));
    }

    @GetMapping("/redis")
    @LogRecord(value = "查询Redis状态", module = "SystemMonitor")
    public ResponseEntity<ResponseVO<SystemMonitorVO.RedisVO>> getRedis() {
        return ResponseEntity.ok(ResponseVO.success("查询成功", systemMonitorService.getRedis()));
    }

    @GetMapping("/jvm")
    @LogRecord(value = "查询JVM状态", module = "SystemMonitor")
    public ResponseEntity<ResponseVO<SystemMonitorVO.JvmVO>> getJvm() {
        return ResponseEntity.ok(ResponseVO.success("查询成功", systemMonitorService.getJvm()));
    }

    @GetMapping("/os")
    @LogRecord(value = "查询主机状态", module = "SystemMonitor")
    public ResponseEntity<ResponseVO<SystemMonitorVO.OsVO>> getOs() {
        return ResponseEntity.ok(ResponseVO.success("查询成功", systemMonitorService.getOs()));
    }

    @GetMapping("/config")
    @LogRecord(value = "查询监控配置摘要", module = "SystemMonitor")
    public ResponseEntity<ResponseVO<SystemMonitorVO.ConfigVO>> getConfig() {
        return ResponseEntity.ok(ResponseVO.success("查询成功", systemMonitorService.getConfig()));
    }
}