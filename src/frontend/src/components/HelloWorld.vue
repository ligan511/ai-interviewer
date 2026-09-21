<script setup lang="ts">
const props = defineProps<{ msg?: string }>()
</script>

<template>
  <div class="hello">
    <h1>{{ props.msg }}</h1>
    <p>AI 智能面试官系统 - 前端初始化页面</p>
  </div>
</template>

<style scoped>
.hello { text-align: center; margin-top: 40px; }
h1 { color: #409eff; }
</style>
