package com.eval.gameeval.service.impl;

import com.eval.gameeval.mapper.ScoringIndicatorMapper;
import com.eval.gameeval.mapper.ScoringStandardMapper;
import com.eval.gameeval.mapper.UserMapper;
import com.eval.gameeval.models.DTO.ScoringStandardCreateDTO;
import com.eval.gameeval.models.VO.ResponseVO;
import com.eval.gameeval.models.VO.ScoringStandardVO;
import com.eval.gameeval.models.entity.ScoringIndicator;
import com.eval.gameeval.models.entity.ScoringStandard;
import com.eval.gameeval.models.entity.User;
import com.eval.gameeval.service.IScoringStandardService;
import com.eval.gameeval.util.RedisUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ScoringStandardServiceImpl implements IScoringStandardService {
    @Resource
    private ScoringStandardMapper standardMapper;

    @Resource
    private ScoringIndicatorMapper indicatorMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private RedisUtil redisUtil;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseVO<ScoringStandardVO> createStandard(String token, ScoringStandardCreateDTO request) {
        try {
            // 1. 验证Token并获取当前用户
            Long currentUserId = redisUtil.getUserIdByToken(token);
            if (currentUserId == null) {
                return ResponseVO.unauthorized("Token无效，请重新登录");
            }

            User currentUser = userMapper.selectById(currentUserId);
            if (currentUser == null) {
                return ResponseVO.unauthorized("用户不存在");
            }

            // 2. 权限校验：只有管理员可以创建打分标准
            if (!"super_admin".equals(currentUser.getRole()) && !"admin".equals(currentUser.getRole())) {
                return ResponseVO.forbidden("权限不足，只有管理员可以创建打分标准");
            }

            // 3. 创建打分标准主表
            ScoringStandard standard = new ScoringStandard();
            standard.setCreatorId(currentUserId);
            standard.setCreateTime(LocalDateTime.now());
            standard.setUpdateTime(LocalDateTime.now());

            standardMapper.insert(standard); // 插入后会自动生成ID

            // 4. 创建指标列表
            List<ScoringIndicator> indicators = new ArrayList<>();
            int sort = 0;

            for (ScoringStandardCreateDTO.IndicatorDTO indicatorDTO : request.getIndicators()) {
                ScoringIndicator indicator = new ScoringIndicator();
                indicator.setStandardId(standard.getId());
                indicator.setName(indicatorDTO.getName());
                indicator.setDescription(indicatorDTO.getDescription() != null ? indicatorDTO.getDescription() : "");
                indicator.setMinScore(indicatorDTO.getMinScore());
                indicator.setMaxScore(indicatorDTO.getMaxScore());
                indicator.setSort(sort++);
                indicator.setCreateTime(LocalDateTime.now());

                indicators.add(indicator);
            }

            // 5. 批量插入指标
            if (!indicators.isEmpty()) {
                indicatorMapper.insertBatch(indicators);
            }

            // 6. 构建响应
            ScoringStandardVO responseVO = new ScoringStandardVO();
            responseVO.setId(standard.getId());
            responseVO.setCreateTime(standard.getCreateTime());

            // 7. 查询刚插入的指标列表
            List<ScoringIndicator> savedIndicators = indicatorMapper.selectByStandardId(standard.getId());
            List<ScoringStandardVO.IndicatorVO> indicatorVOs = savedIndicators.stream()
                    .map(indicator -> {
                        ScoringStandardVO.IndicatorVO vo = new ScoringStandardVO.IndicatorVO();
                        BeanUtils.copyProperties(indicator, vo);
                        return vo;
                    })
                    .collect(Collectors.toList());

            responseVO.setIndicators(indicatorVOs);

            log.info("创建打分标准成功: standardId={}, creatorId={}", standard.getId(), currentUserId);

            return ResponseVO.success("创建成功", responseVO);

        } catch (Exception e) {
            log.error("创建打分标准异常", e);
            return ResponseVO.error("创建失败: " + e.getMessage());
        }
    }

    @Override
    public ResponseVO<List<ScoringStandardVO>> getStandardList(String token) {
        try {
            // 1. 验证Token
            Long currentUserId = redisUtil.getUserIdByToken(token);
            if (currentUserId == null) {
                return ResponseVO.unauthorized("Token无效");
            }

            // 2. 查询所有打分标准
            List<ScoringStandard> standards = standardMapper.selectAll();

            // 3. 转换为VO列表
            List<ScoringStandardVO> standardVOs = new ArrayList<>();

            for (ScoringStandard standard : standards) {
                ScoringStandardVO vo = new ScoringStandardVO();
                vo.setId(standard.getId());
                vo.setCreateTime(standard.getCreateTime());

                // 4. 查询每个标准的指标列表
                List<ScoringIndicator> indicators = indicatorMapper.selectByStandardId(standard.getId());
                List<ScoringStandardVO.IndicatorVO> indicatorVOs = indicators.stream()
                        .map(indicator -> {
                            ScoringStandardVO.IndicatorVO indicatorVO = new ScoringStandardVO.IndicatorVO();
                            BeanUtils.copyProperties(indicator, indicatorVO);
                            return indicatorVO;
                        })
                        .collect(Collectors.toList());

                vo.setIndicators(indicatorVOs);
                standardVOs.add(vo);
            }

            log.info("查询打分标准列表成功: count={}", standardVOs.size());

            return ResponseVO.success("查询成功", standardVOs);

        } catch (Exception e) {
            log.error("查询打分标准列表异常", e);
            return ResponseVO.error("查询失败: " + e.getMessage());
        }
    }

    @Override
    public ResponseVO<ScoringStandardVO> getStandardDetail(String token, Long standardId) {
        try {
            // 1. 验证Token
            Long currentUserId = redisUtil.getUserIdByToken(token);
            if (currentUserId == null) {
                return ResponseVO.unauthorized("Token无效");
            }

            // 2. 查询标准详情
            ScoringStandard standard = standardMapper.selectById(standardId);
            if (standard == null) {
                return ResponseVO.notFound("打分标准不存在");
            }

            // 3. 查询指标列表
            List<ScoringIndicator> indicators = indicatorMapper.selectByStandardId(standardId);

            // 4. 构建响应
            ScoringStandardVO responseVO = new ScoringStandardVO();
            responseVO.setId(standard.getId());
            responseVO.setCreateTime(standard.getCreateTime());

            List<ScoringStandardVO.IndicatorVO> indicatorVOs = indicators.stream()
                    .map(indicator -> {
                        ScoringStandardVO.IndicatorVO vo = new ScoringStandardVO.IndicatorVO();
                        BeanUtils.copyProperties(indicator, vo);
                        return vo;
                    })
                    .collect(Collectors.toList());

            responseVO.setIndicators(indicatorVOs);

            log.info("查询打分标准详情成功: standardId={}", standardId);

            return ResponseVO.success("查询成功", responseVO);

        } catch (Exception e) {
            log.error("查询打分标准详情异常: standardId={}", standardId, e);
            return ResponseVO.error("查询失败: " + e.getMessage());
        }
    }
}
