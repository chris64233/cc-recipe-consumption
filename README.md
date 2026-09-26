# cc-recipe-consumption

生产配方、工单与原料批次管理服务：按配方版本领料、受控替代、退料/调整修复与完工产量核对。

## 开发环境

- JDK 21
- Maven Wrapper 3.9.9
- Spring Boot 4.1.1（Spring Data JPA / Hibernate 7 / H2，Jackson 3）

迁移项目沿用现有 Spring Boot 版本，其他项目使用上述版本。

## 常用命令

运行测试：

    ./mvnw clean test

启动服务：

    ./mvnw spring-boot:run

## 领域模型

| 模型 | 说明 |
| --- | --- |
| `Recipe` / `RecipeVersion` | 配方及其**不可变**版本。版本一经写入不提供修改入口；工单外键绑定具体版本。 |
| `RecipeIngredient` | 版本内的主料行：单位计划产量标准用量。 |
| `IngredientSubstitution` | 受控替代关系：替代料、换算系数、最大替代比例（0,1]。 |
| `MaterialBatch` | 原料批次：可用数量、质量状态（合格/待检/不合格）、有效期。 |
| `ProductionOrder` | 生产工单：不可变配方版本 + 计划产量，状态 OPEN/COMPLETED。 |
| `MaterialIssue` / `Line` / `BatchAllocation` | 领料单、领料行（主料/替代）、逐批次扣减明细。**只追加，不修改**。 |
| `MaterialReturn` / `Line` | 退料单及按原批次的回补明细，只追加。 |
| `InventoryAdjustment` | 带符号库存调整流水（盘盈 +/盘亏 −），可挂工单参与核对。 |
| `CompletionRecord` / `CompletionLine` | 完工记录与逐主料产量核对明细。 |

所有数量统一使用 `BigDecimal`，精度规则集中在 `support/Quantities`：

- 数量 4 位小数、比例 6 位小数，`HALF_UP`；
- 所有换算结果落库前规整；比较一律用 `compareTo`，不依赖 scale；
- 除法固定 scale，避免除不尽抛异常。

## 主要业务规则

1. **配方版本不可变，工单绑定快照**。工单创建后配方口径固定；版本只能新增，不能改写。
2. **一次领料可从多个批次扣减**。仅“合格 + 未过效期 + 有库存”的批次可领，按 FEFO（效期先后）自动分配；待检、不合格、过期批次一律不参与。
3. **替代必须受控**。
   - 只有配方版本中登记的替代关系可用；
   - 折主料量 = 实领替代料数量 × 换算系数；
   - 按“主料 + 具体替代料”做累计校验：历史已替代量（折主料）+ 本次替代量
     ≤ 最大替代比例 × 该主料标准需用量（标准单耗 × 计划产量）。
   - 一次领料允许同一主料同时出现主料行与替代行（部分替代）。
4. **领料原子完成**。所有行的批次分配与校验全部通过后才真正扣库存、写单据；
   任一原料不足或校验失败，整笔事务回滚，不留下部分领料，库存与单据都不变。
5. **幂等**。领料号、退料号、调整号、完工号全局唯一；同号重放返回原单据，
   不重复扣减/回补/完工。唯一约束是并发场景的最终兜底。
6. **并发安全、不允许负库存**。
   - 工单行悲观锁串行化同一工单的领料/退料/调整/完工；
   - 涉及批次按主键排序悲观加锁，跨工单争抢同批次时一方等待、一方按不足回滚；
   - 批次实体校验非负，数据库另有 `available_quantity >= 0` CHECK 约束。
7. **完工必须通过数量核对**。按每个配方主料（折主料口径）配平：

       未说明差异 = 已领总量 − 退料量 − 调整净值(正冲回/负核销)
                    − 申报损耗 − 标准单耗 × 实际产量

   任一主料的未说明差异超出容差（0.0001）即拒绝完工、工单保持 OPEN；
   需先通过退料、库存调整或申报损耗把账做平。
8. **已完工工单冻结**。不能继续领料、退料或做挂工单的调整；只允许查询。
9. **领料错误只追加修复记录**。原始领料记录永不修改：
   - 余料退回 → 退料单（只能退原始领料扣过的批次，累计退料不超过已领）；
   - 盘亏盘盈/无法退料的修复 → 库存调整（盘亏后库存不得为负）。

## 查询能力

- `GET /api/orders/{orderNo}/usage`：工单用料——标准需用、主料/替代已领、退料、调整净值、净耗、替代占比与是否超限。
- `POST /api/orders/{orderNo}/substitutions/preview`：替代计算试算——给出换算后实领数量、试算后累计替代占比与是否超限，不落库。
- `GET /api/batches/{batchNo}/trace`：批次去向——领料扣减、退料回补、调整流水。
- `GET /api/orders/{orderNo}/variance?actualQuantity=`：产量差异——已完工返回完工实绩；
  未完工返回 `provisional=true` 的预估差异（可用 actualQuantity 指定预估实际产量）。

## REST 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/recipes` | 新建配方 |
| POST | `/api/recipes/{recipeCode}/versions` | 新增不可变配方版本（主料、标准用量、替代关系） |
| GET | `/api/recipes/{recipeCode}/versions/{versionNo}` | 查看版本 |
| POST | `/api/batches` | 新建原料批次 |
| GET | `/api/batches/{batchNo}` | 批次详情（含当前是否可用） |
| POST | `/api/orders` | 新建工单（绑定配方版本 + 计划产量） |
| GET | `/api/orders/{orderNo}` | 工单详情 |
| POST | `/api/orders/{orderNo}/issues` | 领料（幂等：issueNo） |
| GET | `/api/issues/{issueNo}` | 领料单详情（含批次明细） |
| POST | `/api/returns` | 退料（幂等：returnNo） |
| GET | `/api/returns/{returnNo}` | 退料单详情 |
| POST | `/api/adjustments` | 库存调整（幂等：adjustmentNo） |
| GET | `/api/adjustments/{adjustmentNo}` | 调整详情 |
| POST | `/api/orders/{orderNo}/completions` | 提交完工与产量核对（幂等：completionNo） |

错误状态码：`404` 对象不存在；`400` 参数校验失败；`409` 编号重复/状态冲突/并发冲突；
`422` 数量关系不成立（库存不足、突破替代比例、完工核对不平衡）。

### 请求示例

新增配方版本（主料 A 单耗 2，允许 B 替代：系数 1.25、最大替代比例 0.5；主料 C 单耗 1）：

```json
{
  "versionNo": "V1",
  "ingredients": [
    {
      "lineNo": 1,
      "materialCode": "A",
      "standardQuantityPerUnit": 2,
      "unit": "kg",
      "substitutions": [
        {
          "substituteMaterialCode": "B",
          "conversionFactor": 1.25,
          "maxSubstitutionRatio": 0.5
        }
      ]
    },
    { "lineNo": 2, "materialCode": "C", "standardQuantityPerUnit": 1, "unit": "kg" }
  ]
}
```

领料（同一工单计划产量 100：用 40 B 折 50 A，再直接领 150 A；领 100 C）：

```json
{
  "issueNo": "IS-1",
  "lines": [
    { "requirementMaterialCode": "A", "pickedMaterialCode": "B", "pickedQuantity": 40 },
    { "requirementMaterialCode": "A", "pickedQuantity": 150 },
    { "requirementMaterialCode": "C", "pickedQuantity": 100 }
  ]
}
```

完工（实际产量 100，数量配平；产量不足时可用退料/调整/申报损耗配平）：

```json
{ "completionNo": "CP-1", "actualQuantity": 100 }
```

## 测试

`./mvnw clean test` 共 50+ 个用例，覆盖：多批次 FEFO 扣减、库存不足整单回滚、
质量状态/效期拦截、替代换算与最大替代比例（单次/累计）、同单主料+替代混合行、
三类业务号幂等、并发领料无负库存、退料回补与不可超退、完工核对（损耗/退料/正负调整配平）、
完工冻结、精度规则，以及 REST 端到端链路与错误状态码。
