# 腾讯 LBS 地理编码接入（接口1）设计说明

**日期**: 2026-03-27  
**状态**: 已确认（用户口头批准）  
**范围**: 超区客户链路中的「地址 → 坐标」环节；接口2（超区费）与话术编排不变。

## 背景

当前 `FurnitureChatService.querySurchargeFlow` 流程为：地址 → `AddressService.resolveAddress`（接口1）→ `AddressService.querySurcharge`（接口2）→ 脚本话术。

`AddressService` 在 `mock=true` 时使用本地规则模拟坐标；在 `mock=false` 时对可配置的 `furniture.address-api.url` 发送 JSON POST。本设计将接口1在真实环境下扩展为可选 **腾讯位置服务 WebService 地理编码**，与现有业务方 HTTP JSON 接口并存、由配置择一。

## 目标与非目标

**目标**

- 使用腾讯地理编码 API 将用户文本地址解析为经纬度，供接口2计算超区费。
- Key 仅通过环境变量或外部配置注入，不进入版本库。
- 失败时统一映射为现有 `AddressResult.fail`，沿用 `address_unclear` 等话术路径。

**非目标**

- 不修改接口2契约与 `SurchargeResult` 逻辑。
- 不引入独立网关/BFF（若未来安全要求升级可再议）。
- 不在本阶段强制启用「低可信度拒绝」策略（可作为可选配置预留）。

## 腾讯接口摘要

- **请求**: `GET https://apis.map.qq.com/ws/geocoder/v1/`
- **必填参数**: `address`（建议含城市；需 URL 编码）、`key`
- **可选参数**: `region`（辅助限定城市）、`output=json`
- **成功**: `status == 0`，坐标位于 `result.location.lat`（纬度）、`result.location.lng`（经度）
- **失败**: `status != 0`，附 `message`；常见码含参数错误、Key 格式错误、来源未授权等（以官方文档为准）
- **质量字段**: `reliability`（1–10，≥7 较可信）、`level`（1–11，≥9 精度较高，可后续作为可选门槛）

控制台需为该 Key **启用 WebService**，并核对 IP/Referer 等白名单与部署环境一致。

## 架构

采用 **独立客户端类 + `AddressService` 编排**（推荐方案）。

| 组件 | 职责 |
|------|------|
| `TencentGeocodeClient`（命名可微调） | 构造 GET 请求、调用腾讯 API、解析 JSON、返回结构化结果或错误信息 |
| `AddressService` | 根据配置在 MOCK / `TENCENT_LBS` / `HTTP_JSON` 之间分发；将客户端结果转为 `AddressResult` |
| `FurnitureChatService` | 无变更 |

## 配置设计

引入解析源枚举，避免 `mock=false` 时与 `addressApiUrl` 语义冲突：

- `furniture.address-resolver`: `MOCK` | `TENCENT_LBS` | `HTTP_JSON`
  - `MOCK`: 保持现有 mock 行为（可与现有 `furniture.api.mock` 对齐或逐步迁移为仅此枚举，实现时二选一需文档化避免重复开关）。
- `furniture.tencent-lbs.key`: 腾讯 Key，**必填**当 `TENCENT_LBS` 时。
- `furniture.tencent-lbs.region`: 可选，默认不传或空；用于提高解析命中率。

**说明**: 若项目仍保留 `furniture.api.mock`，推荐映射关系为：`mock=true` → 强制 MOCK；`mock=false` → 由 `address-resolver` 在 `TENCENT_LBS` 与 `HTTP_JSON` 间选择。具体以实现时 `application.yml` 注释为准。

**可选后续配置**（可先不实现逻辑，仅在文档预留）:

- `furniture.tencent-lbs.min-reliability`（如 7）
- `furniture.tencent-lbs.min-level`（如 9）  
  不满足时返回 fail，提示用户补充更详细地址。

## 数据映射

- 成功: `AddressResult.success(lng, lat, matchedAddress)`，其中 `matchedAddress` 可由 `address_components` 拼接或采用腾讯返回的标准化描述字段（以实际 JSON 为准）。
- 失败: `AddressResult.fail(suggestion)`，`suggestion` 来自腾讯 `message` 或内部友好文案映射；网络异常使用统一「地址解析服务不可用/连接失败」类提示。

## 错误与超时

- HTTP 非 2xx、超时、体解析异常：fail，走现有话术分支。
- `status != 0`：fail，携带可读说明。
- OkHttp 超时与现有 `AddressService` 客户端对齐或略放宽（例如读超时 5–10s），避免长时间阻塞聊天请求。

## 安全

- Key 轮换：曾在非安全渠道暴露的 Key 必须在腾讯控制台作废并重置。
- 日志：禁止打印完整 Key；地址可脱敏或截断按现有日志策略。

## 测试

- 单元测试：Mock HTTP，覆盖成功、`status != 0`、缺字段、超时。
- 手工联调：真实地址跑通「地址 → 坐标 → 超区费 → 话术」全链路。

## 实现顺序建议

1. 新增 `TencentGeocodeClient` 与配置项。
2. 扩展 `AddressService` 分发与映射。
3. 更新 `application.yml` 示例与 README（若已有家具模块说明）。
4. 补充单元测试。

## 参考

- [腾讯位置服务：地理编码（地址解析）](https://lbs.qq.com/webservice_v1/guide-geocoder.html)
