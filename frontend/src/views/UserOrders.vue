<template>
  <div>
    <h2>我的订单</h2>
    <el-table :data="orders">
      <el-table-column prop="orderNo" label="订单号" />
      <el-table-column prop="quantity" label="数量" />
      <el-table-column prop="amount" label="金额" />
      <el-table-column prop="status" label="状态" />
      <el-table-column prop="pickupCode" label="取货码" />
      <el-table-column label="操作">
        <template #default="scope">
          <el-button v-if="scope.row.status === 'PENDING_PAYMENT'" size="small" type="primary" @click="pay(scope.row.id)">支付</el-button>
          <el-button v-if="canCancel(scope.row.status)" size="small" @click="cancel(scope.row.id)">取消</el-button>
          <el-button v-if="canReview(scope.row)" size="small" type="success" @click="openReview(scope.row)">去评价</el-button>
          <span v-if="scope.row.status === 'COMPLETED' && scope.row.reviewed" class="reviewed-tip">已评价</span>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="reviewVisible" title="评价订单" width="440px">
      <el-form label-width="80px">
        <el-form-item label="商品评分">
          <el-rate v-model="reviewForm.productScore" />
        </el-form-item>
        <el-form-item label="团长评分">
          <el-rate v-model="reviewForm.leaderScore" />
        </el-form-item>
        <el-form-item label="评论">
          <el-input v-model="reviewForm.content" type="textarea" :rows="3" maxlength="500" show-word-limit
                    placeholder="说点什么吧（选填）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reviewVisible = false">取消</el-button>
        <el-button type="primary" @click="submitReviewForm">提交评价</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { listMyOrders, payOrder, cancelOrder } from '../api/order'
import { submitReview } from '../api/review'
import { ElMessage } from 'element-plus'

const orders = ref([])
const reviewVisible = ref(false)
const reviewForm = reactive({ orderId: null, productScore: 0, leaderScore: 0, content: '' })

async function load() {
  const res = await listMyOrders()
  orders.value = res.data
}

function canCancel(status) {
  return status === 'PENDING_PAYMENT' || status === 'PENDING_GROUP'
}

function canReview(row) {
  return row.status === 'COMPLETED' && !row.reviewed
}

async function pay(id) {
  await payOrder(id)
  ElMessage.success('支付成功')
  load()
}

async function cancel(id) {
  await cancelOrder(id)
  ElMessage.success('取消成功')
  load()
}

function openReview(row) {
  reviewForm.orderId = row.id
  reviewForm.productScore = 0
  reviewForm.leaderScore = 0
  reviewForm.content = ''
  reviewVisible.value = true
}

async function submitReviewForm() {
  if (!reviewForm.productScore || !reviewForm.leaderScore) {
    ElMessage.warning('商品评分与团长评分均为必填')
    return
  }
  await submitReview({
    orderId: reviewForm.orderId,
    productScore: reviewForm.productScore,
    leaderScore: reviewForm.leaderScore,
    content: reviewForm.content
  })
  ElMessage.success('评价成功')
  reviewVisible.value = false
  load()
}

onMounted(load)
</script>

<style scoped>
.reviewed-tip {
  color: #909399;
  font-size: 12px;
}
</style>
