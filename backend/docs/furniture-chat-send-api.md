# 家具物流智能客服 — 发送消息接口

## 概述

向智能客服发送一条用户消息，服务端根据会话 ID 维护多轮上下文，返回模型处理后的回复文本（含超区费查询等业务逻辑）。

## 基本信息

| 项 | 说明 |
| --- | --- |
| **方法** | `POST` |
| **路径** | `/api/furniture/chat/send` |
| **Content-Type** | `application/json` |
| **跨域** | 控制器已配置 `Access-Control-Allow-Origin: *` |

### 服务地址示例

- 直连后端（默认端口见 `application.yml`）：`http://localhost:8080/api/furniture/chat/send`
- 本地前端开发：经 Vite 代理时，与页面同源，路径仍为 `/api/furniture/chat/send`（代理到 `http://localhost:8080`）

---

## 请求体（JSON）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `message` | string | 建议必填 | 用户输入的文本内容 |
| `sessionId` | string | 建议必填 | 会话标识；同一用户多轮对话应使用相同值，否则无法关联历史 |

### 请求示例

```http
POST /api/furniture/chat/send HTTP/1.1
Host: localhost:8080
Content-Type: application/json

{
  "message": "帮我查广东省深圳市南山区科技园某小区的超区费",
  "sessionId": "sess_1730000000000_abc123xyz"
}
```

### cURL

```bash
curl -s -X POST "http://localhost:8080/api/furniture/chat/send" \
  -H "Content-Type: application/json" \
  -d "{\"message\":\"你好\",\"sessionId\":\"sess_demo_001\"}"
```

### JavaScript（fetch）

```javascript
const res = await fetch('/api/furniture/chat/send', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    message: '我想查询超区费',
    sessionId: 'sess_demo_001',
  }),
})
const data = await res.json()
console.log(data.response)
```

---

## 响应

### 成功（HTTP 200）

响应体为 JSON 对象，字段如下：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `response` | string | 智能客服返回给用户的展示文案（可能为欢迎语、引导补全地址、超区费结果或繁忙提示等） |

### 响应示例

```json
{
  "response": "（此处为服务端根据业务与模型生成的中文回复文本）"
}
```

### 说明

- 当前接口**未**在控制器层定义统一的错误码结构；业务异常或下游失败时，服务内部可能将友好提示写入 `response` 返回，具体文案由 `ScriptConfigService` 等配置与实现决定。
- 会话历史在服务端内存中维护，进程重启或横向多实例未共享会话时，`sessionId` 维度的上下文可能丢失。

---

## 实现位置

- 控制器：`com.logistics.backend.furniture.controller.FurnitureChatController`
- 请求模型：`com.logistics.backend.furniture.model.FurnitureChatRequest`
