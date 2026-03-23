<script setup>
import { ref, nextTick, onMounted } from 'vue'
import axios from 'axios'

const messages = ref([])
const inputText = ref('')
const isLoading = ref(false)
const chatArea = ref(null)
const sessionId = ref('')
const showQuickActions = ref(true)

onMounted(() => {
  sessionId.value = 'sess_' + Date.now() + '_' + Math.random().toString(36).substring(2, 11)
  addMessage('ai', '您好，我是家具物流专属智能客服，您可以直接发送收货地址，我帮您一键查询超区费哦~')
})

function addMessage(sender, text) {
  messages.value.push({
    id: Date.now() + Math.random(),
    sender,
    text,
    time: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  })
  scrollToBottom()
}

async function sendMessage(text) {
  const msg = (text || inputText.value).trim()
  if (!msg || isLoading.value) return

  addMessage('user', msg)
  inputText.value = ''
  isLoading.value = true
  showQuickActions.value = false

  try {
    const res = await axios.post('/api/furniture/chat/send', {
      message: msg,
      sessionId: sessionId.value
    })
    addMessage('ai', res.data.response)
  } catch {
    addMessage('ai', '当前查询系统临时繁忙，您可以稍后再试，或直接联系人工客服咨询~')
  } finally {
    isLoading.value = false
    showQuickActions.value = true
    scrollToBottom()
  }
}

function handleQuickAction(action) {
  if (action === 'human') {
    addMessage('user', '联系人工客服')
    addMessage('ai', '关于超区费以外的问题，您可以联系人工客服为您详细解答哦~')
    return
  }
  sendMessage('我想查询超区费')
}

async function scrollToBottom() {
  await nextTick()
  if (chatArea.value) {
    chatArea.value.scrollTop = chatArea.value.scrollHeight
  }
}

function onInputFocus() {
  setTimeout(scrollToBottom, 300)
}
</script>

<template>
  <div class="furniture-chat">
    <!-- Header -->
    <header class="chat-header">
      <div class="header-avatar">
        <svg viewBox="0 0 24 24" width="22" height="22" fill="currentColor">
          <path d="M20 2H4c-1.1 0-2 .9-2 2v18l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm0 14H6l-2 2V4h16v12z"/>
          <path d="M7 9h2v2H7zm4 0h2v2h-2zm4 0h2v2h-2z"/>
        </svg>
      </div>
      <div class="header-info">
        <h1>家具物流智能客服</h1>
        <span>在线 · 超区费查询</span>
      </div>
    </header>

    <!-- Messages -->
    <div class="chat-messages" ref="chatArea">
      <div class="date-divider">
        <span>{{ new Date().toLocaleDateString('zh-CN', { month: 'long', day: 'numeric' }) }}</span>
      </div>

      <div
        v-for="msg in messages"
        :key="msg.id"
        class="message"
        :class="msg.sender"
      >
        <div class="msg-avatar" v-if="msg.sender === 'ai'">
          <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor">
            <path d="M20 2H4c-1.1 0-2 .9-2 2v18l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm0 14H6l-2 2V4h16v12z"/>
          </svg>
        </div>
        <div class="msg-body">
          <div class="msg-content">{{ msg.text }}</div>
          <div class="msg-time">{{ msg.time }}</div>
        </div>
        <div class="msg-avatar user-avatar" v-if="msg.sender === 'user'">
          <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor">
            <path d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z"/>
          </svg>
        </div>
      </div>

      <!-- Typing indicator -->
      <div v-if="isLoading" class="message ai">
        <div class="msg-avatar">
          <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor">
            <path d="M20 2H4c-1.1 0-2 .9-2 2v18l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm0 14H6l-2 2V4h16v12z"/>
          </svg>
        </div>
        <div class="msg-body">
          <div class="msg-content typing">
            <span class="dot"></span>
            <span class="dot"></span>
            <span class="dot"></span>
          </div>
        </div>
      </div>
    </div>

    <!-- Footer -->
    <div class="chat-footer">
      <div class="quick-actions" v-if="showQuickActions && !isLoading">
        <button class="quick-btn" @click="handleQuickAction('query')">
          <svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor"><path d="M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7zm0 9.5c-1.38 0-2.5-1.12-2.5-2.5s1.12-2.5 2.5-2.5 2.5 1.12 2.5 2.5-1.12 2.5-2.5 2.5z"/></svg>
          查询超区费
        </button>
        <button class="quick-btn" @click="handleQuickAction('human')">
          <svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor"><path d="M20 2H4c-1.1 0-2 .9-2 2v18l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm-2 12H6v-2h12v2zm0-3H6V9h12v2zm0-3H6V6h12v2z"/></svg>
          联系人工客服
        </button>
      </div>
      <div class="chat-input-row">
        <input
          v-model="inputText"
          @keyup.enter="sendMessage()"
          @focus="onInputFocus"
          placeholder="输入收货地址查询超区费..."
          :disabled="isLoading"
          type="text"
          enterkeyhint="send"
        />
        <button
          class="send-btn"
          @click="sendMessage()"
          :disabled="isLoading || !inputText.trim()"
        >
          <svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor">
            <path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z"/>
          </svg>
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.furniture-chat {
  display: flex;
  flex-direction: column;
  height: 100vh;
  height: 100dvh;
  background: #f0f2f5;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC',
    'Hiragino Sans GB', 'Microsoft YaHei', sans-serif;
  color: #333;
  overflow: hidden;
}

/* ========== Header ========== */
.chat-header {
  background: linear-gradient(135deg, #E8723A, #D4612E);
  color: #fff;
  padding: 14px 20px;
  padding-top: calc(14px + env(safe-area-inset-top, 0px));
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
  box-shadow: 0 2px 12px rgba(232, 114, 58, 0.25);
  z-index: 10;
}

.header-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.2);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.header-info h1 {
  font-size: 16px;
  font-weight: 600;
  line-height: 1.3;
}

.header-info span {
  font-size: 12px;
  opacity: 0.85;
}

/* ========== Messages ========== */
.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  -webkit-overflow-scrolling: touch;
  overscroll-behavior: contain;
}

.date-divider {
  text-align: center;
  padding: 4px 0 8px;
}

.date-divider span {
  font-size: 12px;
  color: #999;
  background: #e8e8e8;
  padding: 2px 12px;
  border-radius: 10px;
}

/* ========== Message bubble ========== */
.message {
  display: flex;
  gap: 8px;
  max-width: 85%;
  animation: msgIn 0.3s ease;
}

.message.ai {
  align-self: flex-start;
}

.message.user {
  align-self: flex-end;
  flex-direction: row-reverse;
}

.msg-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: linear-gradient(135deg, #E8723A, #F5A623);
  color: #fff;
}

.msg-avatar.user-avatar {
  background: linear-gradient(135deg, #6DB3F2, #5A9FD4);
}

.msg-body {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.msg-content {
  padding: 10px 14px;
  border-radius: 16px;
  line-height: 1.6;
  font-size: 14px;
  word-break: break-word;
  white-space: pre-wrap;
}

.ai .msg-content {
  background: #fff;
  color: #333;
  border-bottom-left-radius: 4px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

.user .msg-content {
  background: linear-gradient(135deg, #E8723A, #D4612E);
  color: #fff;
  border-bottom-right-radius: 4px;
  box-shadow: 0 1px 4px rgba(232, 114, 58, 0.2);
}

.msg-time {
  font-size: 11px;
  color: #aaa;
  padding: 0 4px;
}

.user .msg-body {
  align-items: flex-end;
}

/* ========== Typing indicator ========== */
.msg-content.typing {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 12px 18px;
}

.dot {
  width: 8px;
  height: 8px;
  background: #ccc;
  border-radius: 50%;
  animation: bounce 1.4s infinite ease-in-out;
}

.dot:nth-child(2) { animation-delay: 0.16s; }
.dot:nth-child(3) { animation-delay: 0.32s; }

@keyframes bounce {
  0%, 80%, 100% { transform: scale(0.6); opacity: 0.4; }
  40% { transform: scale(1); opacity: 1; }
}

/* ========== Footer ========== */
.chat-footer {
  flex-shrink: 0;
  background: #fff;
  border-top: 1px solid #eee;
  padding-bottom: env(safe-area-inset-bottom, 0px);
}

.quick-actions {
  display: flex;
  gap: 8px;
  padding: 10px 16px 0;
  flex-wrap: wrap;
}

.quick-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 6px 14px;
  border: 1px solid #E8723A;
  border-radius: 18px;
  background: #fff;
  color: #E8723A;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
  -webkit-tap-highlight-color: transparent;
}

.quick-btn:active {
  background: #E8723A;
  color: #fff;
}

.chat-input-row {
  display: flex;
  gap: 10px;
  padding: 10px 16px 12px;
  align-items: center;
}

.chat-input-row input {
  flex: 1;
  padding: 10px 16px;
  border: 1px solid #e0e0e0;
  border-radius: 20px;
  outline: none;
  font-size: 14px;
  background: #f5f5f5;
  color: #333;
  transition: border-color 0.2s;
  -webkit-appearance: none;
}

.chat-input-row input:focus {
  border-color: #E8723A;
  background: #fff;
}

.chat-input-row input::placeholder {
  color: #bbb;
}

.send-btn {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  border: none;
  background: #E8723A;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: opacity 0.2s, transform 0.1s;
  flex-shrink: 0;
  -webkit-tap-highlight-color: transparent;
}

.send-btn:active {
  transform: scale(0.92);
}

.send-btn:disabled {
  opacity: 0.4;
  cursor: default;
}

/* ========== Animations ========== */
@keyframes msgIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

/* ========== Scrollbar (PC) ========== */
.chat-messages::-webkit-scrollbar {
  width: 4px;
}

.chat-messages::-webkit-scrollbar-track {
  background: transparent;
}

.chat-messages::-webkit-scrollbar-thumb {
  background: #d0d0d0;
  border-radius: 4px;
}
</style>
