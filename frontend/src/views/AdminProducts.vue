<template>
  <div class="admin-products">
    <!-- 页头 -->
    <header class="page-head">
      <div>
        <h2>商品管理</h2>
        <p>维护商品信息与上下架状态</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openDialog()">新增商品</el-button>
    </header>

    <!-- 筛选 -->
    <div class="toolbar fc-card">
      <el-input
        v-model="keyword"
        placeholder="按名称搜索…"
        clearable
        class="kw"
        @keyup.enter="search"
        @clear="search"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-select v-model="statusFilter" class="st" @change="search">
        <el-option label="全部状态" :value="undefined" />
        <el-option label="上架中" :value="1" />
        <el-option label="已下架" :value="0" />
      </el-select>
      <el-button @click="search">查询</el-button>
    </div>

    <!-- 表格 -->
    <div class="fc-card table-wrap" v-loading="loading">
      <el-table :data="products" style="width: 100%">
        <el-table-column label="商品" min-width="230">
          <template #default="{ row }">
            <div class="product-cell">
              <div class="thumb" :class="visualOf(row).gradient">
                <img v-if="row.imageUrl" :src="row.imageUrl" :alt="row.name" />
                <span v-else>{{ visualOf(row).emoji }}</span>
              </div>
              <div class="product-text">
                <b>{{ row.name }}</b>
                <span class="fc-muted">{{ row.category || '其他' }}</span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="零售价" width="110">
          <template #default="{ row }">
            <span class="fc-price"><i>¥</i>{{ formatMoney(row.price) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="stock" label="库存" width="90" />
        <el-table-column label="上架" width="90">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 1"
              :loading="row._switching"
              @change="(v) => toggleStatus(row, v)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="fc-muted">{{ row.description || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openDialog(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <EmptyState
            icon="📦"
            title="没有找到商品"
            :description="hasFilter ? '换个关键词或状态试试。' : '点击右上角「新增商品」创建第一个商品。'"
          />
        </template>
      </el-table>
    </div>

    <!-- 分页 -->
    <div v-if="total > 0" class="pager">
      <el-pagination
        layout="total, prev, pager, next"
        :total="total"
        :current-page="page"
        :page-size="size"
        background
        @current-change="changePage"
      />
    </div>

    <!-- 新增 / 编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑商品' : '新增商品'" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="88px">
        <el-form-item label="商品名称" prop="name">
          <el-input v-model="form.name" maxlength="128" show-word-limit placeholder="如：红颜草莓" />
        </el-form-item>
        <el-form-item label="分类" prop="category">
          <el-select v-model="form.category" filterable allow-create default-first-option placeholder="选择或输入分类">
            <el-option v-for="c in categoryOptions" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="零售价" prop="price">
          <el-input-number v-model="form.price" :min="0.01" :precision="2" :step="1" controls-position="right" />
          <span class="fc-muted tip">用户参团时的拼团价由团长开团时设定</span>
        </el-form-item>
        <el-form-item label="库存" prop="stock">
          <el-input-number v-model="form.stock" :min="0" :step="10" controls-position="right" />
        </el-form-item>
        <el-form-item label="商品描述" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="2"
            maxlength="500"
            show-word-limit
            placeholder="一句话介绍，展示在商品详情页"
          />
        </el-form-item>
        <el-form-item label="商品图片">
          <div class="upload-row">
            <div class="preview" :class="visualOf(form).gradient">
              <img v-if="form.imageUrl" :src="form.imageUrl" alt="预览" />
              <span v-else>{{ visualOf(form).emoji }}</span>
            </div>
            <div class="upload-ops">
              <el-upload :show-file-list="false" accept="image/*" :http-request="doUpload">
                <el-button :loading="uploading">{{ uploading ? '上传中…' : '上传图片' }}</el-button>
              </el-upload>
              <el-input
                v-model="form.imageUrl"
                placeholder="或直接填图片地址"
                class="url-input"
                clearable
              />
              <p class="fc-muted">支持 jpg / png / gif / webp，不超过 5MB</p>
            </div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import {
  adminListProducts,
  createProduct,
  updateProduct,
  deleteProduct,
  listCategories
} from '../api/product'
import { uploadFile } from '../api/upload'
import EmptyState from '../components/EmptyState.vue'
import { productVisual } from '../utils/visual'
import { formatMoney } from '../utils/format'

/** 分类建议项：与 db/seed.sql 的分类口径一致，也可自由输入新分类 */
const COMMON_CATEGORIES = ['水果', '蔬菜', '肉禽蛋', '水产', '蛋奶', '粮油调味', '其他']

const products = ref([])
const total = ref(0)
const loading = ref(true)
const page = ref(1)
const size = 10

const keyword = ref('')
const statusFilter = ref(undefined)
const appliedKeyword = ref('')
const appliedStatus = ref(undefined)
const hasFilter = computed(() => !!appliedKeyword.value || appliedStatus.value !== undefined)

const dialogVisible = ref(false)
const saving = ref(false)
const uploading = ref(false)
const formRef = ref()
const categoryOptions = ref([...COMMON_CATEGORIES])

const form = reactive({
  id: null,
  name: '',
  category: '',
  price: 0,
  stock: 0,
  description: '',
  imageUrl: ''
})

const rules = {
  name: [
    { required: true, message: '商品名称不能为空', trigger: 'blur' },
    { max: 128, message: '不能超过 128 字', trigger: 'blur' }
  ],
  category: [{ required: true, message: '请选择或输入分类', trigger: 'change' }],
  price: [{ required: true, message: '零售价不能为空', trigger: 'blur' }],
  stock: [{ required: true, message: '库存不能为空', trigger: 'blur' }]
}

function visualOf(row) {
  return productVisual(row?.name)
}

async function load() {
  loading.value = true
  try {
    const res = await adminListProducts({
      keyword: appliedKeyword.value || undefined,
      status: appliedStatus.value,
      page: page.value,
      size
    })
    products.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (e) {
    products.value = []
  } finally {
    loading.value = false
  }
}

function search() {
  appliedKeyword.value = keyword.value.trim()
  appliedStatus.value = statusFilter.value
  page.value = 1
  load()
}

function changePage(p) {
  page.value = p
  load()
}

function openDialog(row) {
  if (row) {
    Object.assign(form, {
      id: row.id,
      name: row.name,
      category: row.category || '',
      price: Number(row.price),
      stock: row.stock,
      description: row.description || '',
      imageUrl: row.imageUrl || ''
    })
  } else {
    Object.assign(form, { id: null, name: '', category: '', price: 0, stock: 0, description: '', imageUrl: '' })
  }
  dialogVisible.value = true
  // 分类下拉建议 = 平台已有分类 ∪ 常用分类
  listCategories()
    .then((res) => {
      const merged = [...new Set([...(res.data || []), ...COMMON_CATEGORIES])]
      categoryOptions.value = merged
    })
    .catch(() => {})
}

async function save() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const payload = {
      name: form.name.trim(),
      category: form.category,
      price: form.price,
      stock: form.stock,
      description: form.description || '',
      imageUrl: form.imageUrl || ''
    }
    if (form.id) {
      await updateProduct(form.id, payload)
    } else {
      await createProduct(payload)
    }
    ElMessage.success(form.id ? '已保存' : '已创建')
    dialogVisible.value = false
    load()
  } catch (e) {
    // 错误提示已由请求拦截器统一处理
  } finally {
    saving.value = false
  }
}

/** 上下架开关：直接以当前行数据调 PUT（后端全量更新非空字段） */
async function toggleStatus(row, on) {
  row._switching = true
  try {
    await updateProduct(row.id, {
      name: row.name,
      category: row.category,
      price: row.price,
      stock: row.stock,
      description: row.description || '',
      imageUrl: row.imageUrl || '',
      status: on ? 1 : 0
    })
    row.status = on ? 1 : 0
    ElMessage.success(on ? '已上架' : '已下架')
  } catch (e) {
    // 失败时 el-switch 依赖 model-value 重新渲染回原状态
  } finally {
    row._switching = false
  }
}

async function remove(row) {
  const ok = await ElMessageBox.confirm(
    `确定删除「${row.name}」吗？删除后用户侧立即不可见。`,
    '删除商品',
    { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
  ).catch(() => false)
  if (!ok) return
  await deleteProduct(row.id)
  ElMessage.success('已删除')
  load()
}

/** 走统一上传接口（ADMIN + 图片白名单校验在服务端） */
async function doUpload({ file }) {
  uploading.value = true
  try {
    const res = await uploadFile(file)
    form.imageUrl = res.data
    ElMessage.success('图片已上传')
  } catch (e) {
    // 错误提示已由请求拦截器统一处理
  } finally {
    uploading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.admin-products {
  max-width: 1080px;
  margin: 0 auto;
}

.page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
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

.toolbar {
  display: flex;
  gap: 10px;
  padding: 13px 16px;
  margin-bottom: 14px;
}

.toolbar .kw {
  width: 260px;
}

.toolbar .st {
  width: 130px;
}

.table-wrap {
  padding: 6px 10px 10px;
  min-height: 200px;
}

.product-cell {
  display: flex;
  align-items: center;
  gap: 11px;
}

.thumb {
  width: 46px;
  height: 46px;
  border-radius: 11px;
  flex: none;
  display: grid;
  place-items: center;
  font-size: 22px;
  overflow: hidden;
}

.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.product-text {
  min-width: 0;
}

.product-text b {
  display: block;
  font-size: 13.5px;
  color: var(--fc-ink-900);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-text span {
  font-size: 11.5px;
}

.pager {
  display: flex;
  justify-content: center;
  margin-top: 18px;
}

/* ---------- 弹窗内的上传区 ---------- */
.upload-row {
  display: flex;
  gap: 14px;
  width: 100%;
}

.preview {
  width: 84px;
  height: 84px;
  border-radius: var(--fc-r);
  flex: none;
  display: grid;
  place-items: center;
  font-size: 34px;
  overflow: hidden;
  border: 1px solid var(--fc-line);
}

.preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.upload-ops {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 9px;
  align-items: flex-start;
}

.url-input {
  width: 100%;
}

.tip {
  margin-left: 10px;
}
</style>
