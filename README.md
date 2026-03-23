# AI 智能物流客服系统

这是一个基于 Java (后端) 和 Vue 3 (前端) 的简易物流服务系统，核心功能是具备意图识别能力的 AI 智能客服。

## 功能特性

1.  **AI 智能对话**：能够识别用户意图（查单、催单、投诉）。
2.  **物流查询**：通过模拟数据库查询订单状态和轨迹（如：我的快递1001到哪了？）。
3.  **智能催单**：识别催单意图，模拟向商家发送 IM 提醒。
4.  **人工介入 (Escalation)**：当 AI 检测到无法处理的问题（如：破损、投诉）时，自动转接人工客服模式。
5.  **架构设计**：采用类似 MCP (Model Context Protocol) 的 Skill 模式，便于扩展更多 AI 能力。

## 技术栈

-   **后端**：Java (原生 JDK HttpServer，无依赖，轻量级)
-   **前端**：Vue 3 + Vite + Axios
-   **AI 逻辑**：基于规则的意图识别 Agent + Skill 调度器

## 快速开始

### 1. 启动后端

```bash
# 编译并运行
javac backend/SimpleBackend.java && java -cp backend SimpleBackend
```

后端服务将启动在 `http://localhost:8080`。

### 2. 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端页面将启动在 `http://localhost:5173`。

## 测试用例

在聊天窗口中尝试以下对话：

-   **查单**：
    -   "我的快递1001到哪了？"
    -   "查一下1002的物流"
-   **催单**：
    -   "太慢了，帮我催一下"
    -   "怎么还没到，快点"
-   **人工介入/投诉**：
    -   "东西破损了我要投诉"
    -   "转人工客服"

## 项目结构

```
├── backend
│   └── SimpleBackend.java  # 核心后端逻辑 (HTTP Server, Agent, Skills)
├── frontend
│   ├── src
│   │   ├── components
│   │   │   └── ChatWindow.vue # 聊天界面组件
│   │   ├── App.vue
│   │   └── main.js
│   └── vite.config.js      # 包含 API 代理配置
└── README.md
```
