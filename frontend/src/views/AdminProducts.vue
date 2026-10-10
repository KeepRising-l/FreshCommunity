<template>
  <div>
    <h2>商品管理</h2>
    <el-button type="primary" @click="openDialog()">新增商品</el-button>
    <el-table :data="products" style="margin-top:16px;">
      <el-table-column prop="name" label="名称" />
      <el-table-column prop="price" label="价格" />
      <el-table-column prop="stock" label="库存" />
      <el-table-column label="图片">
        <template #default="scope">
          <el-image v-if="scope.row.imageUrl" :src="scope.row.imageUrl" style="width:60px;height:60px;" />
        </template>
      </el-table-column>
      <el-table-column label="操作">
        <template #default="scope">
          <el-button size="small" @click="openDialog(scope.row)">编辑</el-button>
          <el-button size="small" type="danger" @click="remove(scope.row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" title="商品">
      <el-form :model="form">
        <el-form-item label="名称">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="价格">
          <el-input-number v-model="form.price" :min="0" />
        </el-form-item>
        <el-form-item label="库存">
          <el-input-number v-model="form.stock" :min="0" />
        </el-form-item>
        <el-form-item label="图片">
          <el-input v-model="form.imageUrl" placeholder="图片URL" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible=false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, reactive } from 'vue'
import { listProducts, createProduct, updateProduct, deleteProduct } from '../api/product'
import { ElMessage, ElMessageBox } from 'element-plus'

const products = ref([])
const dialogVisible = ref(false)
const form = reactive({ id: null, name: '', price: 0, stock: 0, imageUrl: '' })

async function load() {
  const res = await listProducts()
  products.value = res.data
}

function openDialog(row) {
  if (row) {
    Object.assign(form, row)
  } else {
    Object.assign(form, { id: null, name: '', price: 0, stock: 0, imageUrl: '' })
  }
  dialogVisible.value = true
}

async function save() {
  if (form.id) {
    await updateProduct(form.id, form)
  } else {
    await createProduct(form)
  }
  dialogVisible.value = false
  load()
}

async function remove(id) {
  await ElMessageBox.confirm('确定删除？')
  await deleteProduct(id)
  load()
}

onMounted(load)
</script>
