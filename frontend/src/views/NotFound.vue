<template>
  <div class="not-found">
    <div class="code">
      <span>4</span>
      <span class="leaf">🥬</span>
      <span>4</span>
    </div>
    <h2>这片菜地还没开垦</h2>
    <p>你访问的页面不存在或已被移走，回首页继续逛逛吧。</p>
    <div class="actions">
      <el-button type="primary" @click="router.push('/dashboard')">回首页</el-button>
      <el-button @click="router.back()">返回上一页</el-button>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<style scoped>
.not-found {
  min-height: 60vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 40px 20px;
}

.code {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 76px;
  font-weight: 800;
  color: var(--fc-brand-100);
  line-height: 1;
}

.code .leaf {
  font-size: 58px;
  filter: drop-shadow(0 6px 14px rgba(30, 158, 90, .25));
}

h2 {
  margin: 22px 0 8px;
  font-size: 19px;
  font-weight: 700;
  color: var(--fc-ink-900);
}

p {
  margin: 0 0 26px;
  font-size: 13.5px;
  color: var(--fc-ink-400);
}

.actions {
  display: flex;
  gap: 10px;
}
</style>
