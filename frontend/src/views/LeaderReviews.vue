<template>
  <div class="leader-reviews">
    <header class="page-head">
      <div>
        <h2>评价与回复</h2>
        <p>邻居对你开团商品的评价；差评及时回应，好评真诚致谢</p>
      </div>
    </header>

    <SkeletonList v-if="loading" :rows="4" variant="row" />

    <EmptyState
      v-else-if="reviews.length === 0"
      icon="💬"
      title="还没有收到评价"
      description="邻居完成订单并评价后会出现在这里。"
    />

    <div v-else class="list">
      <article v-for="r in reviews" :key="r.id" class="review-card fc-card">
        <div class="rv-head">
          <div class="avatar">{{ (r.username || '?').charAt(0).toUpperCase() }}</div>
          <div class="who">
            <b>{{ r.username }}</b>
            <span class="fc-muted">
              {{ r.productName || '商品已删除' }} · 订单 {{ r.orderNo }} · {{ fromNow(r.createdAt) }}
            </span>
          </div>
          <div class="scores">
            <span>商品 <el-rate :model-value="r.productScore" disabled size="small" /></span>
            <span>团长 <el-rate :model-value="r.leaderScore" disabled size="small" /></span>
          </div>
        </div>

        <p class="rv-content">{{ r.content || '（未留文字评价）' }}</p>

        <!-- 已有回复：展示 + 修改 -->
        <div v-if="r.leaderReply" class="rv-reply shown">
          <b>我的回复</b>
          <span>{{ r.leaderReply }}</span>
          <button type="button" class="edit-btn" @click="openEditor(r)">修改</button>
        </div>
        <el-button v-else size="small" type="primary" plain @click="openEditor(r)">回复</el-button>

        <!-- 回复编辑器 -->
        <div v-if="editingId === r.id" class="rv-editor">
          <el-input
            v-model="replyText"
            type="textarea"
            :rows="2"
            maxlength="500"
            show-word-limit
            placeholder="回复邻居的评价（ ≤500 字）"
          />
          <div class="rv-editor-ops">
            <el-button size="small" @click="editingId = null">取消</el-button>
            <el-button size="small" type="primary" :loading="submitting" @click="submitReply(r)">
              {{ r.leaderReply ? '更新回复' : '发布回复' }}
            </el-button>
          </div>
        </div>
      </article>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import SkeletonList from '../components/SkeletonList.vue'
import EmptyState from '../components/EmptyState.vue'
import { listLeaderReviews, replyReview } from '../api/review'
import { fromNow } from '../utils/format'

const reviews = ref([])
const loading = ref(true)
const editingId = ref(null)
const replyText = ref('')
const submitting = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await listLeaderReviews()
    reviews.value = res.data || []
  } catch (e) {
    reviews.value = []
  } finally {
    loading.value = false
  }
}

function openEditor(r) {
  editingId.value = r.id
  replyText.value = r.leaderReply || ''
}

async function submitReply(r) {
  if (!replyText.value.trim()) {
    ElMessage.warning('回复内容不能为空')
    return
  }
  submitting.value = true
  try {
    const res = await replyReview(r.id, replyText.value.trim())
    const updated = res.data
    const idx = reviews.value.findIndex((x) => x.id === r.id)
    if (idx >= 0 && updated) reviews.value[idx] = updated
    editingId.value = null
    ElMessage.success(updated.leaderReply && r.leaderReply ? '回复已更新' : '回复已发布')
  } catch (e) {
    // 错误提示已由请求拦截器统一处理
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.leader-reviews {
  max-width: 860px;
  margin: 0 auto;
}

.page-head {
  margin-bottom: 16px;
}

.page-head h2 {
  margin: 0 0 4px;
  font-size: 21px;
  font-weight: 700;
  color: var(--fc-ink-900);
}

.page-head p {
  margin: 0;
  font-size: 13px;
  color: var(--fc-ink-400);
}

.list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.review-card {
  padding: 16px 20px;
}

.rv-head {
  display: flex;
  align-items: center;
  gap: 11px;
}

.avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  flex: none;
  display: grid;
  place-items: center;
  background: linear-gradient(140deg, #7ED0A4, #1E9E5A);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
}

.who {
  flex: 1;
  min-width: 0;
}

.who b {
  display: block;
  font-size: 13.5px;
  color: var(--fc-ink-900);
}

.who span {
  font-size: 11.5px;
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.scores {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: none;
}

.scores span {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: var(--fc-ink-400);
}

.rv-content {
  margin: 10px 0 10px 45px;
  font-size: 13.5px;
  line-height: 1.7;
  color: var(--fc-ink-700);
}

.rv-reply.shown {
  margin-left: 45px;
  padding: 9px 13px;
  background: var(--fc-brand-50);
  border-left: 3px solid var(--fc-brand);
  border-radius: 0 var(--fc-r-sm) var(--fc-r-sm) 0;
  font-size: 12.5px;
  line-height: 1.7;
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.rv-reply.shown b {
  color: var(--fc-brand-700);
  flex: none;
}

.rv-reply.shown span {
  color: var(--fc-ink-700);
  flex: 1;
}

.edit-btn {
  flex: none;
  border: none;
  background: none;
  color: var(--fc-brand-700);
  font-size: 12px;
  cursor: pointer;
  padding: 0;
}

.edit-btn:hover {
  text-decoration: underline;
}

.review-card > .el-button {
  margin-left: 45px;
}

.rv-editor {
  margin: 10px 0 0 45px;
}

.rv-editor-ops {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 8px;
}
</style>
