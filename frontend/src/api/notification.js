import request from '../utils/request'

export function listNotifications() {
  return request.get('/notifications')
}

export function getUnreadCount() {
  return request.get('/notifications/unread-count')
}

export function markNotificationRead(id) {
  return request.post('/notifications/' + id + '/read')
}

export function markAllNotificationsRead() {
  return request.post('/notifications/read-all')
}
