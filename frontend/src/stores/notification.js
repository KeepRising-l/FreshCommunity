import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getUnreadCount } from '../api/notification'

export const useNotificationStore = defineStore('notification', () => {
  const unread = ref(0)

  async function refresh() {
    try {
      const res = await getUnreadCount()
      unread.value = res.data || 0
    } catch (e) {
      unread.value = 0
    }
  }

  return { unread, refresh }
})
