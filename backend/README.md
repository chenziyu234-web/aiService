# AI Smart Logistics Service

This is a simple Java Spring Boot backend for an AI-powered logistics service.

## Features

1.  **AI Intelligent Customer Service**:
    -   Integrated with LLM (OpenAI-compatible).
    -   Can understand user intents: Check Order, Urge Delivery, Report Issues.
    -   **Tool/Skill Calling**: The AI decides which backend service to call.
    -   **Human Intervention**: Automatically detects complaints or requests for human help and escalates.

2.  **Logistics Capabilities**:
    -   Order Tracking (Mock Data).
    -   Urging Orders (Simulates sending IM to merchant).
    -   Issue Reporting.

## Configuration

Update `src/main/resources/application.yml`:

```yaml
ai:
  model:
    api-key: sk-be9878a8096e411a92924244536c4df0  # Qwen API Key
    base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
    model-name: qwen-plus
```

## How to Run

1.  Build the project:
    ```bash
    mvn clean package
    ```
2.  Run the application:
    ```bash
    java -jar target/backend-0.0.1-SNAPSHOT.jar
    ```

## API Usage

**Endpoint**: `POST /api/chat`

**Request**:
```json
{
  "userId": "user123",
  "message": "帮我查一下订单1001在哪"
}
```

**Response**:
```json
{
  "response": "订单1001目前在Hangzhou，状态为SHIPPED..."
}
```

## Mock Data

-   **Order 1001**: Shipped, Location: Hangzhou
-   **Order 1002**: Pending, Location: Warehouse A

## AI Logic (Skill/MCP)

The `AIService` implements a simplified **Tool Calling** mechanism. It prompts the AI with available tools (`GET_ORDER`, `URGE_ORDER`, `REPORT_ISSUE`) and parses the AI's output to execute the corresponding Java method. This mimics the behavior of MCP (Model Context Protocol) or Semantic Kernel Skills but in a lightweight manner suitable for this demo.
