package com.eval.gameeval.service.impl;

import com.eval.gameeval.mapper.ScoringIndicatorMapper;
import com.eval.gameeval.mapper.ScoringStandardMapper;
import com.eval.gameeval.mapper.UserMapper;
import com.eval.gameeval.models.DTO.Scoring.ScoringStandardCreateDTO;
import com.eval.gameeval.models.DTO.Scoring.ScoringStandardQueryDTO;
import com.eval.gameeval.models.DTO.Scoring.ScoringStandardUpdateDTO;
import com.eval.gameeval.models.VO.ResponseVO;
import com.eval.gameeval.models.VO.ScoringStandardPageVO;
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
import java.util.*;
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

            ScoringStandard existingStandard = standardMapper.selectByName(request.getName());
            if (existingStandard != null) {
                return ResponseVO.badRequest("打分标准名称 \"" + request.getName() + "\" 已存在");
            }

            // 3. 创建打分标准主表
            ScoringStandard standard = new ScoringStandard();
            standard.setCreatorId(currentUserId);
            standard.setName(request.getName());
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
            responseVO.setName(standard.getName());
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
    public ResponseVO<ScoringStandardPageVO> getStandardList(String token, ScoringStandardQueryDTO query) {
        try {
            // 1. 验证Token
            Long currentUserId = redisToken.getUserIdByToken(token);
            if (currentUserId == null) {
                return ResponseVO.unauthorized("Token无效");
            }

            int page = query != null && query.getPage() != null ? query.getPage() : 1;
            int size = query != null && query.getSize() != null ? query.getSize() : 10;
            String keyWords = query != null && query.getKeyWords() != null ? query.getKeyWords().trim() : null;

            // 2. 先尝试从Redis获取缓存
            Object cache = standardCacheUtil.getStandardListCache();
            List<ScoringStandardVO> standardVOs;
            if (cache != null) {
                // 缓存命中：直接返回（类型转换）
                @SuppressWarnings("unchecked")
                List<ScoringStandardVO> cachedList = (List<ScoringStandardVO>) cache;
                log.info("【缓存命中】获取打分标准列表: count={}", cachedList.size());
                standardVOs = cachedList;
            } else {
                // 3. 缓存未命中：查询数据库
                log.info("【缓存未命中】查询数据库获取打分标准列表");
                List<ScoringStandard> standards = standardMapper.selectAll();

                // 4. 转换为VO列表
                standardVOs = new ArrayList<>();

                for (ScoringStandard standard : standards) {
                    ScoringStandardVO vo = new ScoringStandardVO();
                    vo.setId(standard.getId());
                    vo.setName(standard.getName());
                    vo.setCreateTime(standard.getCreateTime());

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
            }

            List<ScoringStandardVO> filteredStandards = standardVOs;
            if (keyWords != null && !keyWords.isEmpty()) {
                String normalizedKeyWords = keyWords.toLowerCase(Locale.ROOT);
                filteredStandards = standardVOs.stream()
                        .filter(standard -> standard.getName() != null
                                && standard.getName().toLowerCase(Locale.ROOT).contains(normalizedKeyWords))
                        .collect(Collectors.toList());
            }

            int fromIndex = Math.min((page - 1) * size, filteredStandards.size());
            int toIndex = Math.min(fromIndex + size, filteredStandards.size());
            List<ScoringStandardVO> pageList = filteredStandards.subList(fromIndex, toIndex);
            long total = filteredStandards.size();

            ScoringStandardPageVO pageVO = new ScoringStandardPageVO();
            pageVO.setList(pageList);
            pageVO.setTotal(total);
            pageVO.setPage(page);
            pageVO.setSize(size);

            log.info("查询打分标准列表成功: operator={}, keyWords={}, page={}, size={}, count={}, total={}",
                    currentUserId, keyWords, page, size, pageList.size(), total);

            return ResponseVO.success("查询成功", pageVO);

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
            responseVO.setName(standard.getName());
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseVO<Void> updateStandard(String token, Long standardId, ScoringStandardUpdateDTO request) {
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

            // 2. 权限校验：只有管理员可以编辑打分标准
            if (!"super_admin".equals(currentUser.getRole()) && !"admin".equals(currentUser.getRole())) {
                return ResponseVO.forbidden("权限不足，只有管理员可以编辑打分标准");
            }

            // 3. 检查标准是否存在
            ScoringStandard standard = standardMapper.selectById(standardId);
            if (standard == null) {
                return ResponseVO.notFound("打分标准不存在");
            }

            // 4. 更新标准名称（如果提供了新名称）
            if (request.getName() != null && !request.getName().isEmpty()) {
                String newName = request.getName().trim();
                // 检查名称是否被其他标准占用
                ScoringStandard existingStandard = standardMapper.selectByName(newName);
                if (existingStandard != null && !existingStandard.getId().equals(standardId)) {
                    return ResponseVO.badRequest("打分标准名称 \"" + newName + "\" 已存在");
                }
                standard.setName(newName);
            }

            standard.setUpdateTime(LocalDateTime.now());
            standardMapper.updateById(standard);
            log.info("更新打分标准名称成功: standardId={}, newName={}", standardId, standard.getName());

            // 5. 处理指标列表（如果提供了新指标）
            if (request.getIndicators() != null && !request.getIndicators().isEmpty()) {
                // 获取现有所有指标
                List<ScoringIndicator> existingIndicators = indicatorMapper.selectByStandardId(standardId);
                Map<Long, ScoringIndicator> existingIndicatorMap = new HashMap<>();
                for (ScoringIndicator indicator : existingIndicators) {
                    existingIndicatorMap.put(indicator.getId(), indicator);
                }

                // 获取请求中的指标ID集合
                Set<Long> requestIndicatorIds = new HashSet<>();
                for (ScoringStandardUpdateDTO.IndicatorDTO indicatorDTO : request.getIndicators()) {
                    requestIndicatorIds.add(indicatorDTO.getId());
                }

                // 删除未在请求中的指标
                for (ScoringIndicator existingIndicator : existingIndicators) {
                    if (!requestIndicatorIds.contains(existingIndicator.getId())) {
                        indicatorMapper.deleteById(existingIndicator.getId());
                        log.info("删除指标成功: indicatorId={}, standardId={}", existingIndicator.getId(), standardId);
                    }
                }

                // 更新或新增指标
                int sort = 0;
                for (ScoringStandardUpdateDTO.IndicatorDTO indicatorDTO : request.getIndicators()) {
                    if (existingIndicatorMap.containsKey(indicatorDTO.getId())) {
                        // 更新现有指标
                        ScoringIndicator indicator = existingIndicatorMap.get(indicatorDTO.getId());
                        indicator.setName(indicatorDTO.getName());
                        indicator.setDescription(indicatorDTO.getDescription() != null ? indicatorDTO.getDescription() : "");
                        indicator.setMinScore(indicatorDTO.getMinScore());
                        indicator.setMaxScore(indicatorDTO.getMaxScore());
                        indicator.setSort(sort);
                        indicatorMapper.updateById(indicator);
                        log.info("更新指标成功: indicatorId={}, standardId={}", indicator.getId(), standardId);
                    } else {
                        // 新增指标（请求中的ID为0或负数表示新增）
                        ScoringIndicator newIndicator = new ScoringIndicator();
                        newIndicator.setStandardId(standardId);
                        newIndicator.setName(indicatorDTO.getName());
                        newIndicator.setDescription(indicatorDTO.getDescription() != null ? indicatorDTO.getDescription() : "");
                        newIndicator.setMinScore(indicatorDTO.getMinScore());
                        newIndicator.setMaxScore(indicatorDTO.getMaxScore());
                        newIndicator.setSort(sort);
                        newIndicator.setCreateTime(LocalDateTime.now());
                        indicatorMapper.insert(newIndicator);
                        log.info("新增指标成功: indicatorId={}, standardId={}", newIndicator.getId(), standardId);
                    }
                    sort++;
                }
            }

            // 6. 清除缓存
            standardCacheUtil.clearStandardCache();
            standardCacheUtil.clearStandardDetailCache(standardId);
            log.info("编辑打分标准成功: standardId={}, operatorId={}", standardId, currentUserId);

            return ResponseVO.success("编辑成功", null);

        } catch (Exception e) {
            log.error("编辑打分标准异常: standardId={}", standardId, e);
            return ResponseVO.error("编辑失败: " + e.getMessage());
        }
    }
}
