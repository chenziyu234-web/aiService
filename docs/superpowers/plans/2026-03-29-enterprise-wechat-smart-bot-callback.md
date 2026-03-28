# Enterprise WeChat Smart Bot (智能机器人) URL Callback Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expose a Spring Boot HTTPS callback endpoint that passes enterprise WeChat smart-bot URL verification (GET) and receives/decrypts message pushes (POST), then routes user text into the existing furniture AI chat flow and sends replies via the smart bot’s active-reply API (`response_url`).

**Architecture:** Use configuration properties (from environment variables) for `token`, `encodingAesKey`, and `corpId`. Delegate ciphertext handling to WxJava’s enterprise crypto (`weixin-java-cp` / `WXBizMsgCrypt`–style APIs already on the classpath per `backend/pom.xml`). A small dedicated controller handles GET/POST; a service parses decrypted JSON per [智能机器人 · 接收消息 · 消息推送](https://developer.work.weixin.qq.com/document/path/100719#%E6%B6%88%E6%81%AF%E6%8E%A8%E9%80%81) (`msgtype`: `text`, `mixed`, `voice`, etc.), maps content to `FurnitureChatService.chat(sessionId, message)`, and POSTs the reply payload to `response_url` when present. Stream refresh (`msgtype: stream`) can be acknowledged in a later iteration.

**Tech Stack:** Spring Boot 3.2, Java 17, Jackson, OkHttp (already used), `com.github.binarywang:weixin-java-cp:4.6.0`.

---

## Security (do this before coding)

**You pasted live `TOKEN`, `AES_KEY`, and `CORP_ID` in chat.** Treat them as compromised: rotate **Token** and **EncodingAESKey** in the WeChat Work admin console for this smart bot, then configure the app only via environment variables or a local ignored `application-local.yml`. **Do not** commit secrets or embed them as `private static final` in source.

- [ ] **Step:** In WeChat Work admin, regenerate callback credentials if possible; update the bot’s callback URL after the endpoint is deployed.
- [ ] **Step:** Add to `.gitignore` (if not already): `application-local.yml`, `*.env.local`.
- [ ] **Step:** Document required env vars in a comment block in `application.yml` (values as placeholders only).

---

## File map

| File | Responsibility |
|------|----------------|
| `backend/src/main/resources/application.yml` | `wework.aibot.*` placeholders (no real secrets). |
| `backend/src/main/java/.../furniture/config/WeworkAibotProperties.java` | `@ConfigurationProperties` for token, aesKey, corpId, optional base path. |
| `backend/src/main/java/.../furniture/wecom/WeworkAibotCryptoService.java` | URL verify + encrypt/decrypt for callback body (wraps WxJava crypto). |
| `backend/src/main/java/.../furniture/wecom/WeworkAibotMessageParser.java` | Plain JSON → user text string + metadata (`msgid`, `response_url`, `from.userid`, `chatid`, `chattype`). |
| `backend/src/main/java/.../furniture/wecom/WeworkAibotReplyClient.java` | HTTP POST to `response_url` with encrypted reply (per [被动回复 / 主动回复](https://developer.work.weixin.qq.com/document/path/100719) sibling docs — confirm exact JSON envelope in WxJava or official sample). |
| `backend/src/main/java/.../furniture/controller/WeworkAibotController.java` | `GET` verification; `POST` `application/xml` or form fields → decrypt → delegate. |
| `backend/src/main/java/.../furniture/service/WeworkAibotService.java` | Orchestration: dedupe by `msgid`, parse → `FurnitureChatService.chat` → reply client. |
| `backend/src/test/java/.../WeworkAibotCryptoServiceTest.java` | Crypto round-trip / signature tests with test vectors or mocked WxJava. |
| `backend/src/test/java/.../WeworkAibotMessageParserTest.java` | JSON samples from docs → extracted text. |

---

### Task 1: Configuration properties

**Files:**
- Modify: `backend/src/main/resources/application.yml`
- Create: `backend/src/main/java/com/logistics/backend/furniture/config/WeworkAibotProperties.java`
- Create: `backend/src/main/java/com/logistics/backend/BackendApplication.java` (only if `@EnableConfigurationProperties` is not already on the main class — else add annotation there)

- [ ] **Step 1: Add YAML placeholders**

```yaml
wework:
  aibot:
    token: ${WEWORK_AIBOT_TOKEN:}
    aes-key: ${WEWORK_AIBOT_AES_KEY:}
    corp-id: ${WEWORK_AIBOT_CORP_ID:}
```

- [ ] **Step 2: Add `WeworkAibotProperties`**

```java
package com.logistics.backend.furniture.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wework.aibot")
public record WeworkAibotProperties(String token, String aesKey, String corpId) {}
```

- [ ] **Step 3: Enable binding** on `BackendApplication` (or a `@Configuration` class): `@EnableConfigurationProperties(WeworkAibotProperties.class)`.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/resources/application.yml backend/src/main/java/com/logistics/backend/furniture/config/WeworkAibotProperties.java
git commit -m "feat(wework): add smart bot configuration properties"
```

---

### Task 2: Crypto service (GET verify + POST decrypt)

**Files:**
- Create: `backend/src/main/java/com/logistics/backend/furniture/wecom/WeworkAibotCryptoService.java`
- Test: `backend/src/test/java/com/logistics/backend/furniture/wecom/WeworkAibotCryptoServiceTest.java`

Reference: same encryption family as general callback — [回调和回复的加解密方案](https://developer.work.weixin.qq.com/document/path/100719) (linked from the message push page).

- [ ] **Step 1: Write failing test** — `verifyUrlReturnsEchoWhenSignatureValid` using known `msg_signature`, `timestamp`, `nonce`, `echostr` from [企业微信加解密示例](https://developer.work.weixin.qq.com/document/path/90930) or WxJava unit tests (copy one vector). Expect decrypted echo string equals expected plaintext.

- [ ] **Step 2: Run test**

Run: `mvn -q -pl backend test -Dtest=WeworkAibotCryptoServiceTest`

Expected: FAIL (class/method missing).

- [ ] **Step 3: Implement `WeworkAibotCryptoService`** with constructor `(WeworkAibotProperties p)` and methods:
  - `String verifyUrl(String msgSignature, String timestamp, String nonce, String echoStr)`
  - `String decryptXmlOrBody(String xmlOrRaw)` — depending on whether smart-bot POST is XML wrapper or JSON-in-crypto; **read one real callback capture** from logs in dev and match WeChat’s format (plan for both: try decrypt API that matches official sample).

Use WxJava: e.g. `com.qq.weixin.mp.aes.WXBizMsgCrypt` with `p.corpId()` as `receiveid` if required by the constructor overload you use (align with 智能机器人 doc).

- [ ] **Step 4: Run test** — PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/logistics/backend/furniture/wecom/WeworkAibotCryptoService.java backend/src/test/java/com/logistics/backend/furniture/wecom/WeworkAibotCryptoServiceTest.java
git commit -m "feat(wework): smart bot callback crypto verify and decrypt"
```

---

### Task 3: Message parser (decrypted JSON → user text)

**Files:**
- Create: `backend/src/main/java/com/logistics/backend/furniture/wecom/WeworkAibotMessageParser.java`
- Test: `backend/src/test/java/com/logistics/backend/furniture/wecom/WeworkAibotMessageParserTest.java`

- [ ] **Step 1: Write failing test** for `text` payload (sample from [消息推送 · 文本消息](https://developer.work.weixin.qq.com/document/path/100719#%E6%B6%88%E6%81%AF%E6%8E%A8%E9%80%81)):

```java
@Test
void extractsTextFromTextMsgtype() throws Exception {
    String json = """
        {"msgtype":"text","text":{"content":"@RobotA hello"}}
        """;
    Optional<String> t = parser.extractUserText(objectMapper.readTree(json));
    assertTrue(t.isPresent());
    assertEquals("@RobotA hello", t.get());
}
```

- [ ] **Step 2: Run** `mvn -q -pl backend test -Dtest=WeworkAibotMessageParserTest` — FAIL.

- [ ] **Step 3: Implement** `extractUserText(JsonNode root)` for `text`, `mixed` (concatenate or first `text` item per product need), `voice` (`voice.content`). Return `Optional.empty()` for unsupported types (`image`, `file`, `video` without download — YAGNI unless needed).

- [ ] **Step 4: Run** — PASS.

- [ ] **Step 5: Commit** — `feat(wework): parse smart bot message JSON to user text`

---

### Task 4: Controller — GET + POST skeleton

**Files:**
- Create: `backend/src/main/java/com/logistics/backend/furniture/controller/WeworkAibotController.java`
- Test: `backend/src/test/java/com/logistics/backend/furniture/controller/WeworkAibotControllerTest.java` (MockMvc)

- [ ] **Step 1: Write failing MockMvc test** — GET with `msg_signature`, `timestamp`, `nonce`, `echostr` returns `200` and body equals decrypted echo (mock `WeworkAibotCryptoService` bean).

- [ ] **Step 2: Run** `mvn -q -pl backend test -Dtest=WeworkAibotControllerTest` — FAIL.

- [ ] **Step 3: Implement controller** mapping e.g. `@RequestMapping("/callback/wework/aibot")`:
  - `GET`: pass query params to `verifyUrl`, return plain text echo.
  - `POST`: read body, decrypt, pass string to service (Task 5); return `success` or empty body per WeChat expectation for smart bot (confirm in doc — often `200` with specific string).

- [ ] **Step 4: Run** — PASS.

- [ ] **Step 5: Commit** — `feat(wework): smart bot callback HTTP endpoints`

---

### Task 5: Orchestration + `FurnitureChatService` + active reply

**Files:**
- Create: `backend/src/main/java/com/logistics/backend/furniture/service/WeworkAibotService.java`
- Create: `backend/src/main/java/com/logistics/backend/furniture/wecom/WeworkAibotReplyClient.java`
- Modify: optionally `FurnitureChatService` only if a new method is needed (prefer no change: call existing `chat(sessionId, message)`).

- [ ] **Step 1: Write test** for `WeworkAibotService` — given decrypted JSON with `from.userid`, `msgid`, `response_url`, assert `FurnitureChatService.chat` called with sessionId `wework:{userid}` and that `WeworkAibotReplyClient` receives non-blank reply text (mock HTTP).

- [ ] **Step 2: Run test** — FAIL.

- [ ] **Step 3: Implement deduplication** — `ConcurrentHashMap.newKeySet()` or Caffeine cache for `msgid` (doc warns of duplicate callbacks).

- [ ] **Step 4: Implement `WeworkAibotReplyClient`** — POST to `response_url` using OkHttp; body format per 智能机器人 **主动回复消息** (encrypt with same AES key if required). If encryption helper is not exposed, reuse `WXBizMsgCrypt.encryptMsg` pattern from WxJava samples.

- [ ] **Step 5: Run tests** — PASS.

- [ ] **Step 6: Commit** — `feat(wework): route smart bot messages to furniture chat and reply`

---

### Task 6: Manual integration test

- [ ] **Step 1:** Run backend with env vars set (`WEWORK_AIBOT_TOKEN`, etc.).
- [ ] **Step 2:** Expose URL with HTTPS (WeChat requires public HTTPS) — e.g. reverse proxy or tunnel (ngrok, cloudflare tunnel).
- [ ] **Step 3:** In WeChat Work admin, set callback URL to `https://{host}/callback/wework/aibot`, save; confirm GET verification succeeds.
- [ ] **Step 4:** Send a single chat message to the bot; confirm logs show decrypted JSON and user receives AI reply.
- [ ] **Step 5:** Commit any small fixes from manual test.

---

### Task 7 (optional / phase 2): Stream refresh

**Reference:** [流式消息刷新](https://developer.work.weixin.qq.com/document/path/100719#%E6%B5%81%E5%BC%8F%E6%B6%88%E6%81%AF%E5%88%B7%E6%96%B0) — `msgtype: stream` with `stream.id` for long LLM responses.

- [ ] **Step 1:** Read passive/stream reply section in the same doc tree.
- [ ] **Step 2:** If product needs streaming, extend `WeworkAibotService` to correlate `stream.id` with an in-flight completion and chunk replies; else skip (YAGNI).

---

## Plan review

Optional: have another engineer (or a dedicated review pass) verify: callback URL path matches deployment, `receiveid`/`corpId` choice matches 智能机器人加解密说明, and reply payload matches the latest 主动回复示例.

---

## References

- [智能机器人 · 接收消息 · 消息推送](https://developer.work.weixin.qq.com/document/path/100719#%E6%B6%88%E6%81%AF%E6%8E%A8%E9%80%81)
- [回调和回复的加解密方案](https://developer.work.weixin.qq.com/document/path/100719) (same doc section tree)
- Related skills (if available in your workspace): `@superpowers:subagent-driven-development`, `@superpowers:executing-plans`

---

**Plan complete and saved to `docs/superpowers/plans/2026-03-29-enterprise-wechat-smart-bot-callback.md`. Two execution options:**

**1. Subagent-Driven (recommended)** — Dispatch a fresh subagent per task, review between tasks, fast iteration.

**2. Inline Execution** — Execute tasks in one session using executing-plans, batch execution with checkpoints.

**Which approach?**
