# AI 客服管理后台 — 设计规格

## 概述

基于 RuoYi-Vue3（前后端分离单体版）搭建 AI 智能客服管理后台，将现有 aiService 代码迁入 RuoYi 框架，新增话术库、客服坐席、聊天记录三大业务模块。

## 技术选型

| 层面 | 技术 |
|------|------|
| 后端框架 | RuoYi-Vue3 (Spring Boot 3.x + MyBatis) |
| 前端框架 | Vue 3 + Element Plus + Vite |
| 数据库 | MySQL 8.0 |
| 缓存 | Redis |
| 认证 | Spring Security + JWT (RuoYi 自带) |
| AI 模型 | 阿里 DashScope (通义千问 qwen-plus) |

## 项目结构

```
ruoyi-ai/
├── ruoyi-admin/                    # 启动模块
├── ruoyi-system/                   # RuoYi 系统模块 (用户/角色/菜单/字典)
├── ruoyi-framework/                # RuoYi 框架模块 (Security/JWT/拦截器)
├── ruoyi-common/                   # RuoYi 通用模块
├── ruoyi-quartz/                   # 定时任务 (可选)
├── ruoyi-generator/                # 代码生成器
├── ruoyi-customer-service/         # ★ 业务模块
│   └── src/main/java/.../customerservice/
│       ├── script/                 # 话术库 (controller/service/mapper/domain)
│       ├── agent/                  # 客服坐席
│       ├── chat/                   # 聊天记录
│       ├── ai/                     # AI 对话引擎 (迁移自 aiService)
│       ├── furniture/              # 超区费业务 (迁移自 aiService)
│       └── wework/                 # 企微对接 (迁移自 aiService)
└── ruoyi-ui/                       # 前端
    └── src/views/customerservice/
        ├── script/                 # 话术管理页面
        ├── agent/                  # 坐席管理页面
        └── chat/                   # 聊天记录页面
```

## 数据库设计

### 话术库

**cs_script_category** — 话术分类表

| 字段 | 类型 | 说明 |
|------|------|------|
| category_id | bigint PK | 分类 ID |
| parent_id | bigint | 父分类 ID (支持多级树形) |
| category_name | varchar(100) | 分类名称 |
| sort_order | int | 排序号 |
| status | char(1) | 状态 (0 正常 1 停用) |
| create_by / create_time / update_by / update_time / remark | — | RuoYi 标准字段 |

**cs_script** — 话术内容表

| 字段 | 类型 | 说明 |
|------|------|------|
| script_id | bigint PK | 话术 ID |
| category_id | bigint FK | 所属分类 |
| title | varchar(200) | 话术标题 |
| content | text | 话术内容 |
| keywords | varchar(500) | 关键词 (逗号分隔) |
| script_type | char(1) | 类型 (字典：欢迎语/常见问答/引导语/兜底话术) |
| priority | int | 优先级 (数值越大越优先) |
| status | char(1) | 状态 (0 正常 1 停用) |
| create_by / create_time / update_by / update_time / remark | — | RuoYi 标准字段 |

### 客服坐席

**cs_skill_group** — 技能组表

| 字段 | 类型 | 说明 |
|------|------|------|
| group_id | bigint PK | 技能组 ID |
| group_name | varchar(100) | 名称 (超区费/物流查单/投诉处理) |
| description | varchar(500) | 描述 |
| status | char(1) | 状态 |
| create_by / create_time / update_by / update_time / remark | — | RuoYi 标准字段 |

**cs_agent** — 客服坐席表

| 字段 | 类型 | 说明 |
|------|------|------|
| agent_id | bigint PK | 坐席 ID |
| user_id | bigint FK | 关联 sys_user |
| agent_code | varchar(50) | 工号 |
| nickname | varchar(100) | 客服昵称 (对外显示) |
| group_id | bigint FK | 所属技能组 |
| online_status | char(1) | 在线状态 (0 离线 1 在线 2 忙碌) |
| max_concurrent | int | 最大同时接待数 (预留) |
| status | char(1) | 账号状态 (0 正常 1 停用) |
| create_by / create_time / update_by / update_time / remark | — | RuoYi 标准字段 |

### 聊天记录

**cs_conversation** — 会话记录表

| 字段 | 类型 | 说明 |
|------|------|------|
| conversation_id | bigint PK | 会话 ID |
| session_id | varchar(64) | 会话唯一标识 (UUID) |
| customer_id | varchar(100) | 客户标识 (手机号/会员号/openid) |
| customer_name | varchar(100) | 客户名称 |
| agent_id | bigint FK | 接待客服 (NULL=纯 AI) |
| channel | char(2) | 来源渠道 (字典：H5/小程序/企微) |
| conversation_type | char(1) | 类型 (0 纯AI / 1 AI+转人工 / 2 纯人工) |
| business_type | varchar(50) | 业务类型 (超区费/查单/催单/投诉) |
| status | char(1) | 状态 (0 进行中 / 1 已结束 / 2 已转人工) |
| start_time | datetime | 开始时间 |
| end_time | datetime | 结束时间 |
| create_by / create_time / update_by / update_time / remark | — | RuoYi 标准字段 |

**cs_message** — 消息记录表

| 字段 | 类型 | 说明 |
|------|------|------|
| message_id | bigint PK | 消息 ID |
| conversation_id | bigint FK | 所属会话 |
| sender_type | char(1) | 发送者类型 (0 用户 / 1 AI / 2 人工客服 / 3 系统) |
| sender_id | varchar(100) | 发送者标识 |
| content_type | char(1) | 内容类型 (0 文本 / 1 图片 / 2 文件 / 3 系统提示) |
| content | text | 消息内容 |
| send_time | datetime | 发送时间 |
| create_time | datetime | 入库时间 |

## 菜单结构

```
系统管理 (RuoYi 自带)
├── 用户管理
├── 角色管理
├── 菜单管理
├── 字典管理
└── 操作日志

客服管理 (新增)
├── 话术库管理
│   ├── 话术分类
│   └── 话术列表
├── 坐席管理
│   ├── 技能组管理
│   └── 坐席列表
└── 聊天记录
    ├── 会话列表
    └── 消息查询
```

## API 接口

### 话术管理
- `GET /api/customerservice/script/category/list` — 分类树
- `POST/PUT/DELETE /api/customerservice/script/category` — 分类 CRUD
- `GET /api/customerservice/script/list` — 话术列表 (分页)
- `POST/PUT/DELETE /api/customerservice/script` — 话术 CRUD
- `PUT /api/customerservice/script/changeStatus` — 启用/停用

### 坐席管理
- `GET /api/customerservice/agent/group/list` — 技能组列表
- `POST/PUT/DELETE /api/customerservice/agent/group` — 技能组 CRUD
- `GET /api/customerservice/agent/list` — 坐席列表 (分页)
- `POST/PUT/DELETE /api/customerservice/agent` — 坐席 CRUD

### 聊天记录
- `GET /api/customerservice/chat/conversation/list` — 会话列表 (分页，支持渠道/类型/时间筛选)
- `GET /api/customerservice/chat/conversation/{id}` — 会话详情
- `GET /api/customerservice/chat/message/list` — 消息列表 (按会话 ID)
- `POST /api/customerservice/chat/conversation/export` — 导出

### AI 对话 (从 aiService 迁移，安全白名单放行)
- `POST /api/chat/send` — 物流 AI 对话
- `POST /api/furniture/chat/send` — 超区费对话
- `POST /api/furniture/chat/stream` — 超区费流式对话 (SSE)
- `POST /callback/wework/aibot` — 企微回调

## 整合策略

1. **克隆 RuoYi-Vue3** 作为新项目基础
2. **新增 `ruoyi-customer-service` Maven 模块**，在 `ruoyi-admin` 中引入依赖
3. **迁移现有代码**：将 aiService 的 controller/service/model 迁入对应 package
4. **数据库持久化**：将内存存储替换为 MyBatis + MySQL
5. **安全配置**：管理接口走 RuoYi 权限，AI 对话接口加入白名单
6. **前端**：管理页面使用 RuoYi-UI (Vue 3 + Element Plus)，用户端 H5 保持独立

## 字典数据

| 字典类型 | 字典值 |
|---------|--------|
| cs_script_type | 0=欢迎语, 1=常见问答, 2=引导语, 3=兜底话术 |
| cs_channel | 01=H5, 02=小程序, 03=企业微信 |
| cs_conversation_type | 0=纯AI, 1=AI+转人工, 2=纯人工 |
| cs_conversation_status | 0=进行中, 1=已结束, 2=已转人工 |
| cs_online_status | 0=离线, 1=在线, 2=忙碌 |
| cs_sender_type | 0=用户, 1=AI, 2=人工客服, 3=系统 |
| cs_content_type | 0=文本, 1=图片, 2=文件, 3=系统提示 |
