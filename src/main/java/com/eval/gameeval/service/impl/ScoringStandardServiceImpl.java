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
import com.eval.gameeval.util.RedisToken;
import com.eval.gameeval.util.StandardCacheUtil;
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
    private RedisToken redisToken;

    @Resource
    private StandardCacheUtil standardCacheUtil;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseVO<ScoringStandardVO> createStandard(String token, ScoringStandardCreateDTO request) {
        try {
            // 1. 验证Token并获取当前用户
            Long currentUserId = redisToken.getUserIdByToken(token);
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

            // 8.清除缓存
            standardCacheUtil.clearStandardCache();
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
            Long currentUserId = redisToken.getUserIdByToken(token);
            if (currentUserId == null) {
                return ResponseVO.unauthorized("Token无效");
            }

            // 2. 先尝试从Redis获取缓存
            Object cache = standardCacheUtil.getStandardListCache();
            if (cache != null) {
                // 缓存命中：直接返回（类型转换）
                @SuppressWarnings("unchecked")
                List<ScoringStandardVO> cachedList = (List<ScoringStandardVO>) cache;
                log.info("【缓存命中】获取打分标准列表: count={}", cachedList.size());
                return ResponseVO.success("查询成功", cachedList);
            }


            // 3. 缓存未命中：查询数据库
            log.info("【缓存未命中】查询数据库获取打分标准列表");
            List<ScoringStandard> standards = standardMapper.selectAll();

            // 4. 转换为VO列表
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

            // 5. 写入缓存
            standardCacheUtil.cacheStandardList(standardVOs);
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
            Long currentUserId = redisToken.getUserIdByToken(token);
            if (currentUserId == null) {
                return ResponseVO.unauthorized("Token无效");
            }

            // 2. 缓存穿透防护 - 先检查空值缓存
            // 防止恶意查询不存在的ID导致数据库被击穿
            if (standardCacheUtil.isNullCached(standardId)) {
                log.warn("【缓存穿透防护】查询空值缓存命中: standardId={}", standardId);
                return ResponseVO.notFound("打分标准不存在");
            }

            // 3. 尝试从Redis获取详情缓存
            Object cache = standardCacheUtil.getStandardDetailCache(standardId);
            if (cache != null) {
                // 缓存命中
                ScoringStandardVO cachedVO = (ScoringStandardVO) cache;
                log.info("【缓存命中】获取打分标准详情: standardId={}", standardId);
                return ResponseVO.success("查询成功", cachedVO);
            }

            // 4. 缓存未命中：查询数据库
            log.info("【缓存未命中】查询数据库获取打分标准详情: standardId={}", standardId);
            ScoringStandard standard = standardMapper.selectById(standardId);

            // 5. 缓存穿透防护 - 数据库也查不到，写入空值缓存
            if (standard == null) {
                // 写入空值缓存（短时间有效，防止频繁查询）
                standardCacheUtil.cacheNull(standardId);
                log.warn("【缓存穿透防护】写入空值缓存: standardId={}", standardId);
                return ResponseVO.notFound("打分标准不存在");
            }
            //  查询标准详情
            // ScoringStandard standard = standardMapper.selectById(standardId);
//            if (standard == null) {
//                return ResponseVO.notFound("打分标准不存在");
//            }

            // 6. 查询指标列表
            List<ScoringIndicator> indicators = indicatorMapper.selectByStandardId(standardId);

            // 7. 构建响应
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

            // 8. 将详情写入缓存
            standardCacheUtil.cacheStandardDetail(standardId, responseVO);
            log.info("查询打分标准详情成功: standardId={}", standardId);

            return ResponseVO.success("查询成功", responseVO);

        } catch (Exception e) {
            log.error("查询打分标准详情异常: standardId={}", standardId, e);
            return ResponseVO.error("查询失败: " + e.getMessage());
        }
    }
}
