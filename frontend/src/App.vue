<script setup>
import ChatWindow from './components/ChatWindow.vue'
</script>

<template>
  <div class="container">
    <h1>智能物流客服 (AI Logistics Agent)</h1>
    <ChatWindow />
  </div>
</template>

<style scoped>
.container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 90vh;
  width: 100%;
}
</style>
