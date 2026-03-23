<script setup>
import { ref, nextTick } from 'vue'
import axios from 'axios'

const messages = ref([
  { id: 1, text: '您好！我是您的智能物流助手。请问有什么可以帮您？\n(试着问：我的快递1001到哪了？ / 催一下单 / 快递破损了)', sender: 'ai' }
])
const newMessage = ref('')
const isLoading = ref(false)
const chatContainer = ref(null)

const sendMessage = async () => {
  if (!newMessage.value.trim()) return

  // Add User Message
  messages.value.push({
    id: Date.now(),
    text: newMessage.value,
    sender: 'user'
  })

  const userText = newMessage.value
  newMessage.value = ''
  isLoading.value = true
  scrollToBottom()

  try {
    // Call Backend
    const response = await axios.post('/api/chat/send', {
      message: userText,
      userId: 'user_001'
    })

    messages.value.push({
      id: Date.now() + 1,
      text: response.data.response,
      sender: 'ai'
    })
  } catch (error) {
    messages.value.push({
      id: Date.now() + 1,
      text: '系统繁忙，请稍后再试。',
      sender: 'system'
    })
    console.error(error)
  } finally {
    isLoading.value = false
    scrollToBottom()
  }
}

const scrollToBottom = async () => {
  await nextTick()
  if (chatContainer.value) {
    chatContainer.value.scrollTop = chatContainer.value.scrollHeight
  }
}
</script>

<template>
  <div class="chat-window">
    <div class="messages" ref="chatContainer">
      <div 
        v-for="msg in messages" 
        :key="msg.id" 
        class="message-bubble"
        :class="msg.sender"
      >
        <div class="avatar">
          {{ msg.sender === 'user' ? '👤' : (msg.sender === 'ai' ? '🤖' : '👩‍💼') }}
        </div>
        <div class="content">
          {{ msg.text }}
        </div>
      </div>
      <div v-if="isLoading" class="loading">AI 正在思考...</div>
    </div>
    <div class="input-area">
      <input 
        v-model="newMessage" 
        @keyup.enter="sendMessage" 
        placeholder="请输入您的问题..." 
        :disabled="isLoading"
      />
      <button @click="sendMessage" :disabled="isLoading">发送</button>
    </div>
  </div>
</template>

<style scoped>
.chat-window {
  width: 600px;
  height: 500px;
  border: 1px solid #444;
  border-radius: 12px;
  display: flex;
  flex-direction: column;
  background-color: #1a1a1a;
  overflow: hidden;
}

.messages {
  flex: 1;
  padding: 20px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 15px;
}

.message-bubble {
  display: flex;
  gap: 10px;
  max-width: 80%;
}

.message-bubble.user {
  align-self: flex-end;
  flex-direction: row-reverse;
}

.message-bubble.ai, .message-bubble.system, .message-bubble.human {
  align-self: flex-start;
}

.avatar {
  font-size: 24px;
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #333;
  border-radius: 50%;
}

.content {
  padding: 10px 15px;
  border-radius: 12px;
  background: #333;
  color: #fff;
  white-space: pre-wrap;
  text-align: left;
}

.user .content {
  background: #4CAF50;
  color: white;
}

.human .content {
  background: #FF9800; /* Orange for human agent */
  color: white;
}

.loading {
  color: #888;
  font-style: italic;
  font-size: 0.9em;
  align-self: flex-start;
  margin-left: 50px;
}

.input-area {
  padding: 15px;
  border-top: 1px solid #333;
  display: flex;
  gap: 10px;
  background: #242424;
}

input {
  flex: 1;
  padding: 10px;
  border-radius: 6px;
  border: 1px solid #555;
  background: #333;
  color: white;
}

button {
  padding: 10px 20px;
  background: #4CAF50;
  color: white;
  border: none;
  border-radius: 6px;
  cursor: pointer;
}

button:disabled {
  background: #555;
  cursor: not-allowed;
}
</style>
