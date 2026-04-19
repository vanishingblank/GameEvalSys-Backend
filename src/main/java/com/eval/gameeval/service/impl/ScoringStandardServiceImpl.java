package com.eval.gameeval.service.impl;

import com.eval.gameeval.mapper.ScoringIndicatorCategoryMapper;
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
import com.eval.gameeval.models.entity.ScoringIndicatorCategory;
import com.eval.gameeval.models.entity.ScoringStandard;
import com.eval.gameeval.models.entity.User;
import com.eval.gameeval.service.IScoringStandardService;
import com.eval.gameeval.util.RedisToken;
import com.eval.gameeval.util.StandardCacheUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ScoringStandardServiceImpl implements IScoringStandardService {
    @Resource
    private ScoringStandardMapper standardMapper;

    @Resource
    private ScoringIndicatorMapper indicatorMapper;

    @Resource
    private ScoringIndicatorCategoryMapper categoryMapper;

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
            Long currentUserId = redisToken.getUserIdByToken(token);
            if (currentUserId == null) {
                return ResponseVO.unauthorized("Token无效，请重新登录");
            }

            User currentUser = userMapper.selectById(currentUserId);
            if (currentUser == null) {
                return ResponseVO.unauthorized("用户不存在");
            }
            if (!isAdmin(currentUser)) {
                return ResponseVO.forbidden("权限不足，只有管理员可以创建打分标准");
            }

            String standardName = request.getName() == null ? null : request.getName().trim();
            ScoringStandard existingStandard = standardMapper.selectByName(standardName);
            if (existingStandard != null) {
                return ResponseVO.badRequest("打分标准名称 \"" + standardName + "\" 已存在");
            }

            List<ScoringStandardCreateDTO.CategoryDTO> requestCategories = buildCreateCategories(request);
            if (requestCategories.isEmpty()) {
                return ResponseVO.badRequest("分类与指标不能为空");
            }
            for (ScoringStandardCreateDTO.CategoryDTO categoryDTO : requestCategories) {
                if (categoryDTO.getIndicators() == null || categoryDTO.getIndicators().isEmpty()) {
                    return ResponseVO.badRequest("每个分类至少需要包含一个指标");
                }
            }

            LocalDateTime now = LocalDateTime.now();
            ScoringStandard standard = new ScoringStandard();
            standard.setCreatorId(currentUserId);
            standard.setName(standardName);
            standard.setCreateTime(now);
            standard.setUpdateTime(now);
            standardMapper.insert(standard);

            int categorySort = 0;
            int indicatorSort = 0;
            for (ScoringStandardCreateDTO.CategoryDTO categoryDTO : requestCategories) {
                if (categoryDTO.getName() == null || categoryDTO.getName().trim().isEmpty()) {
                    return ResponseVO.badRequest("分类名称不能为空");
                }
                ScoringIndicatorCategory category = new ScoringIndicatorCategory();
                category.setStandardId(standard.getId());
                category.setName(categoryDTO.getName().trim());
                category.setDescription(normalizeDescription(categoryDTO.getDescription()));
                category.setSort(categorySort++);
                category.setCreateTime(now);
                category.setUpdateTime(now);
                categoryMapper.insert(category);

                List<ScoringStandardCreateDTO.IndicatorDTO> indicators = categoryDTO.getIndicators() == null
                        ? new ArrayList<>() : categoryDTO.getIndicators();
                for (ScoringStandardCreateDTO.IndicatorDTO indicatorDTO : indicators) {
                    if (indicatorDTO.getName() == null || indicatorDTO.getName().trim().isEmpty()) {
                        return ResponseVO.badRequest("指标名称不能为空");
                    }
                    ScoringIndicator indicator = new ScoringIndicator();
                    indicator.setStandardId(standard.getId());
                    indicator.setCategoryId(category.getId());
                    indicator.setName(indicatorDTO.getName().trim());
                    indicator.setDescription(normalizeDescription(indicatorDTO.getDescription()));
                    indicator.setMinScore(indicatorDTO.getMinScore());
                    indicator.setMaxScore(indicatorDTO.getMaxScore());
                    indicator.setSort(indicatorSort++);
                    indicator.setCreateTime(now);
                    indicatorMapper.insert(indicator);
                }
            }

            List<ScoringIndicatorCategory> savedCategories = categoryMapper.selectByStandardId(standard.getId());
            List<ScoringIndicator> savedIndicators = indicatorMapper.selectByStandardId(standard.getId());
            ScoringStandardVO responseVO = buildStandardVO(standard, savedCategories, savedIndicators);

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
            Long currentUserId = redisToken.getUserIdByToken(token);
            if (currentUserId == null) {
                return ResponseVO.unauthorized("Token无效");
            }

            int page = query != null && query.getPage() != null ? query.getPage() : 1;
            int size = query != null && query.getSize() != null ? query.getSize() : 10;
            String keyWords = query != null && query.getKeyWords() != null ? query.getKeyWords().trim() : null;

            Object cache = standardCacheUtil.getStandardListCache();
            List<ScoringStandardVO> standardVOs;
            if (cache != null) {
                @SuppressWarnings("unchecked")
                List<ScoringStandardVO> cachedList = (List<ScoringStandardVO>) cache;
                standardVOs = cachedList;
                log.info("【缓存命中】获取打分标准列表: count={}", cachedList.size());
            } else {
                List<ScoringStandard> standards = standardMapper.selectAll();
                standardVOs = standards.stream().map(standard -> {
                    List<ScoringIndicatorCategory> categories = categoryMapper.selectByStandardId(standard.getId());
                    List<ScoringIndicator> indicators = indicatorMapper.selectByStandardId(standard.getId());
                    return buildStandardVO(standard, categories, indicators);
                }).collect(Collectors.toList());

                standardCacheUtil.cacheStandardList(standardVOs);
                log.info("【缓存未命中】查询数据库获取打分标准列表");
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

            ScoringStandardPageVO pageVO = new ScoringStandardPageVO();
            pageVO.setList(pageList);
            pageVO.setTotal((long) filteredStandards.size());
            pageVO.setPage(page);
            pageVO.setSize(size);

            log.info("查询打分标准列表成功: operator={}, keyWords={}, page={}, size={}, count={}, total={}",
                    currentUserId, keyWords, page, size, pageList.size(), filteredStandards.size());

            return ResponseVO.success("查询成功", pageVO);
        } catch (Exception e) {
            log.error("查询打分标准列表异常", e);
            return ResponseVO.error("查询失败: " + e.getMessage());
        }
    }

    @Override
    public ResponseVO<ScoringStandardVO> getStandardDetail(String token, Long standardId) {
        try {
            Long currentUserId = redisToken.getUserIdByToken(token);
            if (currentUserId == null) {
                return ResponseVO.unauthorized("Token无效");
            }

            if (standardCacheUtil.isNullCached(standardId)) {
                log.warn("【缓存穿透防护】查询空值缓存命中: standardId={}", standardId);
                return ResponseVO.notFound("打分标准不存在");
            }

            Object cache = standardCacheUtil.getStandardDetailCache(standardId);
            if (cache != null) {
                ScoringStandardVO cachedVO = (ScoringStandardVO) cache;
                log.info("【缓存命中】获取打分标准详情: standardId={}", standardId);
                return ResponseVO.success("查询成功", cachedVO);
            }

            ScoringStandard standard = standardMapper.selectById(standardId);
            if (standard == null) {
                standardCacheUtil.cacheNull(standardId);
                log.warn("【缓存穿透防护】写入空值缓存: standardId={}", standardId);
                return ResponseVO.notFound("打分标准不存在");
            }

            List<ScoringIndicatorCategory> categories = categoryMapper.selectByStandardId(standardId);
            List<ScoringIndicator> indicators = indicatorMapper.selectByStandardId(standardId);
            ScoringStandardVO responseVO = buildStandardVO(standard, categories, indicators);

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
            Long currentUserId = redisToken.getUserIdByToken(token);
            if (currentUserId == null) {
                return ResponseVO.unauthorized("Token无效，请重新登录");
            }

            User currentUser = userMapper.selectById(currentUserId);
            if (currentUser == null) {
                return ResponseVO.unauthorized("用户不存在");
            }
            if (!isAdmin(currentUser)) {
                return ResponseVO.forbidden("权限不足，只有管理员可以编辑打分标准");
            }

            ScoringStandard standard = standardMapper.selectById(standardId);
            if (standard == null) {
                return ResponseVO.notFound("打分标准不存在");
            }

            if (request.getName() != null && !request.getName().trim().isEmpty()) {
                String newName = request.getName().trim();
                ScoringStandard existingStandard = standardMapper.selectByName(newName);
                if (existingStandard != null && !existingStandard.getId().equals(standardId)) {
                    return ResponseVO.badRequest("打分标准名称 \"" + newName + "\" 已存在");
                }
                standard.setName(newName);
            }
            standard.setUpdateTime(LocalDateTime.now());
            standardMapper.updateById(standard);

            if (request.getCategories() != null) {
                List<ScoringIndicatorCategory> existingCategories = categoryMapper.selectByStandardId(standardId);
                List<ScoringIndicator> existingIndicators = indicatorMapper.selectByStandardId(standardId);

                Map<Long, ScoringIndicatorCategory> categoryMap = existingCategories.stream()
                        .collect(Collectors.toMap(ScoringIndicatorCategory::getId, item -> item));
                Map<Long, ScoringIndicator> indicatorMap = existingIndicators.stream()
                        .collect(Collectors.toMap(ScoringIndicator::getId, item -> item));

                Set<Long> keepCategoryIds = new HashSet<>();
                Set<Long> keepIndicatorIds = new HashSet<>();

                int categorySort = 0;
                int indicatorSort = 0;
                LocalDateTime now = LocalDateTime.now();

                for (ScoringStandardUpdateDTO.CategoryDTO categoryDTO : request.getCategories()) {
                    if (categoryDTO.getName() == null || categoryDTO.getName().trim().isEmpty()) {
                        return ResponseVO.badRequest("分类名称不能为空");
                    }

                    Long categoryId = categoryDTO.getId();
                    ScoringIndicatorCategory category;
                    if (categoryId != null && categoryId > 0) {
                        if (!categoryMap.containsKey(categoryId)) {
                            return ResponseVO.badRequest("分类ID " + categoryId + " 不属于当前打分标准");
                        }
                        category = categoryMap.get(categoryId);
                        category.setName(categoryDTO.getName().trim());
                        category.setDescription(normalizeDescription(categoryDTO.getDescription()));
                        category.setSort(categorySort);
                        category.setUpdateTime(now);
                        categoryMapper.updateById(category);
                    } else {
                        category = new ScoringIndicatorCategory();
                        category.setStandardId(standardId);
                        category.setName(categoryDTO.getName().trim());
                        category.setDescription(normalizeDescription(categoryDTO.getDescription()));
                        category.setSort(categorySort);
                        category.setCreateTime(now);
                        category.setUpdateTime(now);
                        categoryMapper.insert(category);
                    }
                    keepCategoryIds.add(category.getId());
                    categorySort++;

                    List<ScoringStandardUpdateDTO.IndicatorDTO> requestIndicators = categoryDTO.getIndicators() == null
                            ? new ArrayList<>() : categoryDTO.getIndicators();

                    for (ScoringStandardUpdateDTO.IndicatorDTO indicatorDTO : requestIndicators) {
                        if (indicatorDTO.getName() == null || indicatorDTO.getName().trim().isEmpty()) {
                            return ResponseVO.badRequest("指标名称不能为空");
                        }

                        Long indicatorId = indicatorDTO.getId();
                        if (indicatorId != null && indicatorId > 0) {
                            if (!indicatorMap.containsKey(indicatorId)) {
                                return ResponseVO.badRequest("指标ID " + indicatorId + " 不属于当前打分标准");
                            }
                            ScoringIndicator indicator = indicatorMap.get(indicatorId);
                            indicator.setName(indicatorDTO.getName().trim());
                            indicator.setDescription(normalizeDescription(indicatorDTO.getDescription()));
                            indicator.setMinScore(indicatorDTO.getMinScore());
                            indicator.setMaxScore(indicatorDTO.getMaxScore());
                            indicator.setCategoryId(category.getId());
                            indicator.setSort(indicatorSort);
                            indicatorMapper.updateById(indicator);
                            keepIndicatorIds.add(indicatorId);
                        } else {
                            ScoringIndicator newIndicator = new ScoringIndicator();
                            newIndicator.setStandardId(standardId);
                            newIndicator.setCategoryId(category.getId());
                            newIndicator.setName(indicatorDTO.getName().trim());
                            newIndicator.setDescription(normalizeDescription(indicatorDTO.getDescription()));
                            newIndicator.setMinScore(indicatorDTO.getMinScore());
                            newIndicator.setMaxScore(indicatorDTO.getMaxScore());
                            newIndicator.setSort(indicatorSort);
                            newIndicator.setCreateTime(now);
                            indicatorMapper.insert(newIndicator);
                        }
                        indicatorSort++;
                    }
                }

                for (ScoringIndicator existingIndicator : existingIndicators) {
                    if (!keepIndicatorIds.contains(existingIndicator.getId())) {
                        indicatorMapper.deleteById(existingIndicator.getId());
                    }
                }

                for (ScoringIndicatorCategory existingCategory : existingCategories) {
                    if (!keepCategoryIds.contains(existingCategory.getId())) {
                        categoryMapper.deleteById(existingCategory.getId());
                    }
                }
            }

            standardCacheUtil.clearStandardCache();
            standardCacheUtil.clearStandardDetailCache(standardId);
            log.info("编辑打分标准成功: standardId={}, operatorId={}", standardId, currentUserId);

            return ResponseVO.success("编辑成功", null);
        } catch (Exception e) {
            log.error("编辑打分标准异常: standardId={}", standardId, e);
            return ResponseVO.error("编辑失败: " + e.getMessage());
        }
    }

    private List<ScoringStandardCreateDTO.CategoryDTO> buildCreateCategories(ScoringStandardCreateDTO request) {
        if (request.getCategories() != null && !request.getCategories().isEmpty()) {
            return request.getCategories();
        }
        if (request.getIndicators() == null || request.getIndicators().isEmpty()) {
            return new ArrayList<>();
        }

        ScoringStandardCreateDTO.CategoryDTO category = new ScoringStandardCreateDTO.CategoryDTO();
        category.setName("默认分类");
        category.setDescription("");
        category.setIndicators(request.getIndicators());
        List<ScoringStandardCreateDTO.CategoryDTO> fallbackCategories = new ArrayList<>();
        fallbackCategories.add(category);
        return fallbackCategories;
    }

    private ScoringStandardVO buildStandardVO(ScoringStandard standard,
                                              List<ScoringIndicatorCategory> categories,
                                              List<ScoringIndicator> indicators) {
        ScoringStandardVO vo = new ScoringStandardVO();
        vo.setId(standard.getId());
        vo.setName(standard.getName());
        vo.setCreateTime(standard.getCreateTime());

        List<ScoringStandardVO.IndicatorVO> indicatorVOs = indicators.stream().map(indicator -> {
            ScoringStandardVO.IndicatorVO indicatorVO = new ScoringStandardVO.IndicatorVO();
            indicatorVO.setId(indicator.getId());
            indicatorVO.setCategoryId(indicator.getCategoryId());
            indicatorVO.setName(indicator.getName());
            indicatorVO.setDescription(indicator.getDescription());
            indicatorVO.setMinScore(indicator.getMinScore());
            indicatorVO.setMaxScore(indicator.getMaxScore());
            return indicatorVO;
        }).collect(Collectors.toList());
        vo.setIndicators(indicatorVOs);

        Map<Long, List<ScoringStandardVO.IndicatorVO>> indicatorsByCategory = new HashMap<>();
        List<ScoringStandardVO.IndicatorVO> uncategorized = new ArrayList<>();
        for (ScoringStandardVO.IndicatorVO indicatorVO : indicatorVOs) {
            if (indicatorVO.getCategoryId() == null) {
                uncategorized.add(indicatorVO);
            } else {
                indicatorsByCategory.computeIfAbsent(indicatorVO.getCategoryId(), key -> new ArrayList<>())
                        .add(indicatorVO);
            }
        }

        List<ScoringStandardVO.CategoryVO> categoryVOs = new ArrayList<>();
        for (ScoringIndicatorCategory category : categories) {
            ScoringStandardVO.CategoryVO categoryVO = new ScoringStandardVO.CategoryVO();
            categoryVO.setId(category.getId());
            categoryVO.setName(category.getName());
            categoryVO.setDescription(category.getDescription());
            categoryVO.setSort(category.getSort());
            categoryVO.setIndicators(indicatorsByCategory.getOrDefault(category.getId(), new ArrayList<>()));
            categoryVOs.add(categoryVO);
        }

        if (!uncategorized.isEmpty()) {
            ScoringStandardVO.CategoryVO uncategorizedVO = new ScoringStandardVO.CategoryVO();
            uncategorizedVO.setId(null);
            uncategorizedVO.setName("未分类");
            uncategorizedVO.setDescription("");
            uncategorizedVO.setSort(Integer.MAX_VALUE);
            uncategorizedVO.setIndicators(uncategorized);
            categoryVOs.add(uncategorizedVO);
        }

        vo.setCategories(categoryVOs);
        return vo;
    }

    private String normalizeDescription(String description) {
        return description == null ? "" : description.trim();
    }

    private boolean isAdmin(User user) {
        return "super_admin".equals(user.getRole()) || "admin".equals(user.getRole());
    }

    public void warmupStandardListCache() {
        try {
            if (standardCacheUtil.getStandardListCache() != null) {
                return;
            }

            List<ScoringStandard> standards = standardMapper.selectAll();
            List<ScoringStandardVO> standardVOs = standards.stream().map(standard -> {
                List<ScoringIndicatorCategory> categories = categoryMapper.selectByStandardId(standard.getId());
                List<ScoringIndicator> indicators = indicatorMapper.selectByStandardId(standard.getId());
                return buildStandardVO(standard, categories, indicators);
            }).collect(Collectors.toList());

            standardCacheUtil.cacheStandardList(standardVOs);
            log.info("全局热键预热完成: key={}, count={}", "scoring:standard:list", standardVOs.size());
        } catch (Exception e) {
            log.error("全局热键预热异常: scoring:standard:list", e);
        }
    }
}
