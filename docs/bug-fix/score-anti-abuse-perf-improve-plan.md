# 项目统计落地方案：只读缓存或汇总表 + 写入链路异步重算恶意标记

## 1. 目标

当前 `GET /projects/{projectId}/statistics` 的瓶颈不是单次 SQL，而是统计时顺带重算恶意标记，导致接口在高数据量项目上会做全量扫描、全量分组、全量回写，再重查一次数据。

本方案的目标是把职责拆开：

- 统计接口只做只读查询，优先读缓存，缓存未命中时读汇总表或轻量聚合查询。
- 恶意标记只在写入链路或异步任务里重算，不再由统计接口触发。
- 保留现有业务口径，保证 rawAverageScore 与 processedAverageScore 可同时输出。
- 让数据量增长时，接口耗时尽量与项目打分记录总量脱钩。

## 2. 现状问题

当前实现的主要问题在 [ProjectStatisticsServiceImpl.java](../../src/main/java/com/eval/gameeval/service/impl/ProjectStatisticsServiceImpl.java) 里：

1. `getProjectStatistics` 先查小组打分明细，再调用 `refreshMaliciousFlags`，然后重新查询一次小组打分明细。
2. 同一个接口同时承担统计计算和数据修正，读请求会触发写库。
3. 统计过程中还要再查指标明细、评委分布、项目基础信息，属于典型的多次大表聚合。

这会带来三个直接后果：

- 项目数据越大，接口越慢。
- 高并发下容易把数据库压力放大到统计接口本身。
- 统计结果与写入修正耦合，后续很难做缓存和异步化。

## 3. 推荐落地方案

建议按“缓存优先、汇总表兜底、异步重算标记”的顺序落地。

### 3.1 统计接口改成只读

接口职责收敛为：

- 先读 Redis 缓存。
- 缓存未命中时，优先读统计汇总表。
- 如果汇总表未建成，才回退到数据库实时聚合，但不允许再刷新恶意标记。

也就是说，`GET /projects/{projectId}/statistics` 以后只负责读结果，不再负责修数据。

### 3.2 恶意标记改到写入链路

恶意标记重算放到这三个入口之一：

- 提交打分时同步判定当前记录是否恶意。
- 修改打分记录时重算当前记录所属项目或小组。
- 管理员修改项目判定策略后，异步重算整个项目。

优先级建议如下：

1. 普通写入和修改先同步判定单条记录。
2. 项目策略切换后使用异步任务补全全量重算。
3. 读接口不再做任何回写。

### 3.3 统计结果优先依赖缓存或汇总表

两种实现方式都可以，推荐分阶段做：

- 第一阶段：Redis 缓存，快速见效。
- 第二阶段：统计汇总表，长期稳定。

Redis 缓存适合接口热点明显、数据刷新频率中等的场景。汇总表适合项目统计长期高频访问、且需要稳定 P95 的场景。

4. 打分新增、修改、策略变更后，恶意标记最终能在异步重算里完成。
5. rawAverageScore 与 processedAverageScore 口径一致且可追溯。

## 12. 任务清单

### 阶段一：先止血，拆掉读接口写操作

- [ ] 从 `getProjectStatistics` 中移除 `refreshMaliciousFlags` 调用，确保统计接口只读。
- [ ] 保留统计明细查询，但禁止在统计接口内执行 `clearMaliciousFlagByProjectId`、`markMaliciousByRecordIds` 这类写操作。
- [ ] 将当前“读取后重新查询”的流程改成单次读取，避免重复扫同一批明细。

### 阶段二：把恶意标记前移到写入链路

- [ ] 在评分提交接口中补齐项目策略判定，新增记录时直接写入 `is_malicious`。
- [ ] 在评分修改接口中同步重算当前记录的恶意标记。
- [ ] 保留项目维度的重算入口，供管理员修改策略后批量修复历史数据。
- [ ] 明确 `AUTO` 与 `THRESHOLD` 两种模式的判定口径和回退规则。

### 阶段三：补项目统计缓存

- [x] 新增 `project:statistics:{projectId}` 缓存键。
- [x] 为项目统计实现缓存读写逻辑，缓存未命中时再回源数据库或汇总表。
- [x] 给项目统计缓存设置 60 到 180 秒 TTL。
- [x] 在评分新增、评分修改、评分删除、策略变更后清理对应项目统计缓存。

### 阶段四：补异步重算任务

- [x] 新增项目恶意标记重算任务，支持按项目触发。
- [x] 支持项目策略变更后异步重算全量记录。
- [x] 支持任务失败重试和任务状态记录，避免重算中断后无法恢复。
- [x] 任务完成后刷新统计缓存。

### 阶段五：补统计汇总表

- [x] 设计项目级、小组级、指标级统计汇总表。
- [x] 为汇总表补齐 rawAverageScore、processedAverageScore、abnormalCount、sampleSize、validSampleSize 等字段。
- [x] 在写入链路或异步任务完成后同步更新汇总表。
- [x] 将统计接口的主回源路径切换到汇总表。

### 阶段六：补回归验证

- [x] 验证大项目下统计接口不再触发批量更新 SQL。
- [x] 验证缓存命中时接口不会访问 scoring_record 明细。
- [x] 验证策略切换后异步重算能够最终更新 `is_malicious`。
- [x] 验证导出、列表、统计三处的恶意判定口径一致。
- [x] 验证异常场景下可以回退到实时聚合兜底。
## 4. 数据模型设计

### 4.1 项目策略字段
### 交付排序建议

1. 先完成阶段一和阶段二，快速降低统计接口压力。
2. 再完成阶段三，先把热点请求挡在缓存层。
3. 然后补阶段四，保证历史数据和策略变更能收敛。
4. 最后完成阶段五和阶段六，把长期稳定性补齐。

### 5.1 缓存键

建议新增以下 Redis key：

- project:statistics:{projectId}
- project:statistics:{projectId}:group:{groupId}

如果要分片存储，也可以把 group、indicator、distribution 拆成多个 key，避免单个 value 过大。

### 5.2 TTL 建议

- 项目统计缓存：60 到 180 秒。
- 小组统计缓存：60 到 180 秒。
- 热点项目可以在发布或项目更新后主动预热。

### 5.3 失效触发

以下事件发生时，应清理对应项目统计缓存：

- 评分新增。
- 评分修改。
- 评分删除。
- 项目策略变更。
- 项目小组关系变更。
- 恶意标记异步重算完成。

这个闭环要和项目缓存、平台统计缓存保持一致，不要只清统计不清关联缓存。

## 6. 异步重算设计

### 6.1 触发时机

建议使用异步任务重算恶意标记的场景：

- 项目从 AUTO 切换到 THRESHOLD。
- THRESHOLD 阈值修改。
- 大批量历史数据修复。
- 管理员手动触发重算。

### 6.2 重算粒度

重算建议按项目维度执行，必要时可进一步按小组分批处理。

推荐流程：

1. 拉取项目下所有评分记录。
2. 按项目策略计算恶意标记。
3. 分批更新 scoring_record 的 is_malicious。
4. 更新统计汇总表。
5. 清理项目统计缓存。

### 6.3 执行方式

可选实现有三种：

- Spring @Async，简单直接，适合中低规模项目。
- 定时任务队列，适合对失败重试有要求的场景。
- 消息队列，适合未来扩展到更高吞吐。

如果当前项目没有 MQ，优先用 @Async + 任务状态表，成本最低。

## 7. 接口改造清单

### 7.1 统计接口

`GET /projects/{projectId}/statistics` 的返回保持兼容，但内部执行方式改为：

- 先查项目统计缓存。
- 缓存未命中时查汇总表。
- 汇总表未命中时才实时聚合，但不刷新恶意标记。

返回值建议保留：

- rawAverageScore。
- processedAverageScore。
- abnormalCount。
- sampleSize。
- validSampleSize。
- maliciousRuleType。
- maliciousThreshold。

### 7.2 项目配置接口

项目创建和编辑接口增加以下参数：

- maliciousRuleType。
- maliciousScoreLower。
- maliciousScoreUpper。

项目详情和列表 VO 需要返回这些配置，方便前端展示当前统计策略。

### 7.3 评分写入接口

评分提交或修改时，直接计算当前记录的恶意状态，并写入 is_malicious。

这样统计接口就不必再做读时刷新标记。

## 8. 计算口径

建议统一使用以下口径：

- rawAverageScore：原始平均值，包含恶意样本。
- processedAverageScore：处理后平均值，只基于非恶意样本。

如果还保留评委标准化，则 processedAverageScore 采用非恶意样本上的标准化后平均。

这样能同时兼顾两点：

- 阈值模式下符合管理员规则。
- 评分公平性更好，减少评委偏严或偏松的影响。

## 9. 落地步骤

### 第一步：移除读接口写操作

把 `getProjectStatistics` 里的 `refreshMaliciousFlags` 调用删掉，统计接口只保留读取逻辑。

### 第二步：补写入链路判定

在打分提交、修改接口里加入恶意判定逻辑，保证新增数据进入库时就带上 is_malicious。

### 第三步：加缓存

为项目统计新增 Redis 缓存，和平台统计缓存采用同样的读写模式。

### 第四步：加异步重算

项目规则变更后，通过异步任务重算整项目恶意标记，并在结束后清理缓存。

### 第五步：补汇总表

当 Redis 缓存已经稳定后，再把统计结果沉到汇总表，减少大项目回源成本。

## 10. 与现有代码的对应关系

当前相关代码位置如下：

- 统计入口：[ProjectStatisticsController.java](../../src/main/java/com/eval/gameeval/controller/ProjectStatisticsController.java)
- 统计实现：[ProjectStatisticsServiceImpl.java](../../src/main/java/com/eval/gameeval/service/impl/ProjectStatisticsServiceImpl.java)
- 恶意重算逻辑：[ProjectStatisticsServiceImpl.java](../../src/main/java/com/eval/gameeval/service/impl/ProjectStatisticsServiceImpl.java)
- 项目统计缓存策略：[cache-strategy-v1.md](../redis/cache-strategy-v1.md)

其中最关键的改动点是：把 [refreshMaliciousFlags](../../src/main/java/com/eval/gameeval/service/impl/ProjectStatisticsServiceImpl.java) 从统计读路径中移除。

## 11. 验收标准

以下条件满足时，可认为方案落地完成：

1. 统计接口不再触发 scoring_record 的批量更新。
2. 统计接口命中缓存时不访问大明细表。
3. 缓存未命中时能稳定回源，不再产生读写混合操作。
4. 打分新增、修改、策略变更后，恶意标记最终能在异步重算里完成。
5. rawAverageScore 与 processedAverageScore 口径一致且可追溯。

## 12. 风险与回退

### 风险

- 异步重算未完成前，统计结果与最新恶意标记可能存在短暂延迟。
- 汇总表和缓存同时存在时，需要明确刷新顺序，否则容易出现脏读。
- 如果历史项目数据很大，首次重算可能耗时较长，需要分批处理。
- 新增汇总表后，历史数据需要一次性回填；如果没有回填入口，空表会一直保持为空。

### 回退方案

- 关闭统计缓存，回退到汇总表直读。
- 关闭异步任务，回退到写入链路同步标记。
- 保留原始明细查询，作为最差情况下的兜底实现。

## 13. 推荐实施顺序

建议按以下顺序推进：

1. 先把读接口里的 `refreshMaliciousFlags` 移走。
2. 再把恶意判定前移到写入链路。
3. 加 `project:statistics:{projectId}` 缓存。
4. 再补异步重算任务。
5. 最后补统计汇总表。

这个顺序的好处是：前两步能最快降低数据库压力，后两步负责把系统稳定性和可扩展性补齐。