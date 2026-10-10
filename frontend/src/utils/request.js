import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../stores/user'

const request = axios.create({
  baseURL: '/api',
  timeout: 10000
})

request.interceptors.request.use(config => {
  const store = useUserStore()
  if (store.token) {
    config.headers.Authorization = 'Bearer ' + store.token
  }
  return config
})

request.interceptors.response.use(
  res => {
    const data = res.data
    if (data.code !== 200) {
      ElMessage.error(data.msg || '请求失败')
      return Promise.reject(new Error(data.msg))
    }
    return data
  },
  err => {
    const msg = err.response?.data?.msg || '网络错误'
    ElMessage.error(msg)
    if (err.response?.status === 401) {
      const store = useUserStore()
      store.clearUser()
      window.location.href = '/login'
    }
    return Promise.reject(err)
  }
)

export default request
