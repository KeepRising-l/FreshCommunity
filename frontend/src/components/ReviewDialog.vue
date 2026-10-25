<template>
  <el-dialog :model-value="visible" title="评价订单" width="440px" @update:model-value="$emit('update:visible', $event)">
    <el-form label-width="80px">
      <el-form-item label="商品评分">
        <el-rate v-model="form.productScore" />
      </el-form-item>
      <el-form-item label="团长评分">
        <el-rate v-model="form.leaderScore" />
      </el-form-item>
      <el-form-item label="评论">
        <el-input
          v-model="form.content"
          type="textarea"
          :rows="3"
          maxlength="500"
          show-word-limit
          placeholder="说点什么吧（选填）"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:visible', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">提交评价</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { submitReview } from '../api/review'

const props = defineProps({
  visible: { type: Boolean, default: false },
  /** 待评价订单（COMPLETED 未评价） */
  order: { type: Object, default: null }
})

const emit = defineEmits(['update:visible', 'submitted'])

const submitting = ref(false)
const form = ref({ productScore: 0, leaderScore: 0, content: '' })

watch(
  () => props.visible,
  (v) => {
    if (v) form.value = { productScore: 0, leaderScore: 0, content: '' }
  }
)

async function submit() {
  const f = form.value
  if (!f.productScore || !f.leaderScore) {
    ElMessage.warning('商品评分与团长评分均为必填')
    return
  }
  submitting.value = true
  try {
    await submitReview({
      orderId: props.order?.id,
      productScore: f.productScore,
      leaderScore: f.leaderScore,
      content: f.content
    })
    ElMessage.success('评价成功')
    emit('update:visible', false)
    emit('submitted')
  } catch (e) {
    // 错误提示已由请求拦截器统一处理
  } finally {
    submitting.value = false
  }
}
</script>
