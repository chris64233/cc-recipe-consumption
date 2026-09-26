# cc-recipe-consumption

生产配方、工单和原料批次管理服务：按配方领料、受控替代、退料/调整纠错，以及完工时的产量核对。

## 开发环境

- JDK 21
- Maven Wrapper 3.9.9
- Spring Boot 4.1.1
- H2（默认内嵌内存库，可通过数据源配置切换）

## 常用命令

运行测试：

    ./mvnw clean test

启动服务：

    ./mvnw spring-boot:run

## 领域模型

| 模型 | 说明 |
| --- | --- |
| `RecipeVersion` / `RecipeItem` / `RecipeSubstitution` | 配方版本（`recipeCode`+`versionNo` 唯一）含多种原料的**标准用量**和允许的**替代关系**。配方版本创建后不提供修改入口；工单绑定后其内容保持稳定 |
| `ProductionOrder` | 生产工单，绑定一个不可变配方版本和计划产量，状态为 `OPEN` / `COMPLETED` |
| `MaterialBatch` | 原料批次，记录可用数量、质量状态（`AVAILABLE` / `QUARANTINE` / `REJECTED`）和有效期 |
| `IssueRecord` / `IssueLine` / `IssueLineBatch` | 领料单/行/批次扣减明细，创建后**不可变** |
| `ReturnRecord` | 退料记录，只追加，数量加回批次库存 |
| `AdjustmentRecord` | 调整记录，带符号数量同时修正批次库存与工单用量口径 |
| `OrderCompletion` / `OrderCompletionLoss` | 完工记录与逐原料损耗 |

## 主要业务规则

### 统一精度

- 所有数量使用 `BigDecimal`，经 `QuantityMath` 统一处理：
  - 实物数量、折标准当量：**4 位小数，`HALF_UP` 四舍五入**，每次运算后立即归整；
  - 换算系数、替代比例：6 位小数；
  - 数量比较一律用 `compareTo`，不使用 `equals`/`==`。

### 领料

1. **一次领料可从多个批次扣减**，同一领料行不能重复引用同一批次；同一批次在同一次领料中的合计扣减量参与库存校验。
2. **替代换算**：批次原料若与配方原料一致，换算系数为 1；否则必须是配方上声明的允许替代料，按 `标准当量 = 替代料实物数量 × conversionRatio` 折算。
3. **最大替代比例**：按工单**累计口径**校验（历史领料 + 本次），某替代料累计折标准当量不得超过 `配方标准单耗 × 计划产量 × maxRatio`；恰好等于上限放行，超出即拒绝。
4. 只有质量状态 `AVAILABLE` 且未过有效期的批次允许领料。
5. **原子性**：校验、批次扣减、领料明细在同一事务内完成；任一原料不足、批次不可用或替代比例超限，整体回滚，**不会留下部分领料**。
6. **幂等**：领料业务号 `bizNo` 全局唯一。重复提交同一业务号返回首次结果，不重复扣减；业务号用于不同工单返回 409。
7. **并发**：涉及批次先按 id 升序加悲观写锁，再锁工单（全局统一加锁顺序，避免死锁）；库存非负校验在锁内进行，结合实体乐观版本号，并发领料不会造成负库存或突破替代比例。
8. **已完工工单不能领料**。

### 退料与调整（纠错）

- 原始领料记录永不修改；发现领料错误时通过以下方式修复：
  - **退料**：追加退料记录，实物数量加回批次，并按换算系数折标准当量冲减工单用量；退料数量不得超过该工单从该批次领用的净额；同样按 `bizNo` 幂等。
  - **调整**：追加调整记录，`qtyDelta`（带符号）修正批次库存，`equivalentDelta` 必须等于其按规定系数折算的标准当量；负向调整不得使批次库存为负。
- 已完工工单不允许退料或调整。

### 完工与产量核对

完工时逐配方原料核对数量恒等式（全部为折标准原料口径，4 位精度下严格相等）：

```
净领料 = 累计领料 - 累计退料 + 累计调整（带符号）
净领料 = 实际产量 × 配方标准单耗 + 申报损耗
```

- 实际产量必须大于 0，损耗不能为负，每个配方原料都必须申报损耗（无损耗填 0）。
- 任一原料数量关系不成立、缺少损耗行、净领料为负，均拒绝完工（400）。
- 已完工工单不可重复完工，完工后不能再领料、退料或调整。

### 查询

| 查询 | 端点 | 内容 |
| --- | --- | --- |
| 工单用料 | `GET /api/query/orders/{orderNo}/usage` | 逐配方原料的需求、累计领用/退料/调整、净用量，以及各替代料当量与占需求比例、是否超上限 |
| 替代计算 | `POST /api/query/substitution-preview` | 对一笔拟提交的领料请求只读预览：每个替代料的实物量、折标准当量、工单累计、上限与是否放行 |
| 批次去向 | `GET /api/query/batches/{batchNo}/trace` | 批次当前库存与在各工单上的领（`ISSUE`）/退（`RETURN`）/调整（`ADJUSTMENT`）流水 |
| 产量差异 | `GET /api/query/orders/{orderNo}/variance` | 计划/实际产量差异，以及逐原料净领料、标准应耗、申报损耗、未解释差异 |

## API 一览

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/recipes` | 创建不可变配方版本（含标准用量与替代关系） |
| POST | `/api/batches` | 创建原料批次（可用数量、质量状态、有效期） |
| POST | `/api/orders` | 创建工单（绑定配方版本与计划产量） |
| POST | `/api/issues` | 领料（多批次扣减、受控替代，`bizNo` 幂等） |
| POST | `/api/returns` | 退料 |
| POST | `/api/adjustments` | 库存/用量调整 |
| POST | `/api/completions` | 完工并核对实际产量与损耗 |
| GET | `/api/query/orders/{orderNo}/usage` | 工单用料 |
| POST | `/api/query/substitution-preview` | 替代计算预览 |
| GET | `/api/query/batches/{batchNo}/trace` | 批次去向 |
| GET | `/api/query/orders/{orderNo}/variance` | 产量差异 |

错误响应统一为 `{timestamp,status,error,message}`：业务规则不满足返回 400，资源不存在返回 404，幂等业务号冲突返回 409。

### 请求示例

创建配方（1 单位 BREAD 耗 2 单位 FLOUR；允许 `FLOUR-B` 替代，系数 0.8，最大比例 25%）：

```json
{
  "recipeCode": "R-BREAD",
  "versionNo": "v1",
  "productCode": "BREAD",
  "items": [
    {
      "materialCode": "FLOUR",
      "standardQty": 2,
      "substitutions": [
        {"substituteMaterialCode": "FLOUR-B", "conversionRatio": 0.8, "maxRatio": 0.25}
      ]
    },
    {"materialCode": "WATER", "standardQty": 1}
  ]
}
```

领料（标准料与替代料可在同一行从多个批次扣减，`qty` 为批次实物数量）：

```json
{
  "bizNo": "ISSUE-20260927-001",
  "orderNo": "PO-1",
  "lines": [
    {"recipeMaterialCode": "FLOUR", "batches": [
      {"batchNo": "B-F1", "qty": 160},
      {"batchNo": "B-A1", "qty": 50}
    ]},
    {"recipeMaterialCode": "WATER", "batches": [
      {"batchNo": "B-W1", "qty": 100}
    ]}
  ]
}
```

完工（计划产量 100，实际 100；面粉净领 205 时申报损耗 5）：

```json
{
  "orderNo": "PO-1",
  "actualQty": 100,
  "losses": [
    {"materialCode": "FLOUR", "lossQty": 5},
    {"materialCode": "WATER", "lossQty": 0}
  ]
}
```

## 自动化测试

共 42 个测试（`./mvnw test`），覆盖：

- 领料多批次扣减与原子回滚、批次合计校验；
- 替代换算、替代比例单次与工单累计超限拒绝、未授权替代料拒绝；
- 质量状态/有效期校验、`bizNo` 幂等（含不同工单冲突）；
- 8 线程并发领料不产生负库存（恰好按库存容量成功，其余失败回滚）；
- 完工数量恒等式成立/不成立（含损耗、欠产）、缺损耗行、重复完工；
- 退料加回库存与净额上限、退料换算、调整换算一致性与负库存防护、完工后禁止领料/退料/调整；
- 工单用料、替代预览、批次去向、产量差异查询；
- REST 层状态码（201/400/404）与幂等响应；
- `QuantityMath` 精度规则。
