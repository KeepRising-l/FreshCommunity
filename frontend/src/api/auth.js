import request from '../utils/request'

export function login(data) {
  return request.post('/auth/login', data)
}

export function register(data) {
  return request.post('/auth/register', data)
}

export function getMe() {
  return request.get('/auth/me')
}

/**
 * 修改密码（旧密码校验 + 6-32 位新密码）。成功后当前 token 仍有效，请牢记新密码。
 * @param {{oldPassword: string, newPassword: string}} data
 */
export function changePassword(data) {
  return request.post('/auth/change-password', data)
}

/**
 * 维护个人资料。当前仅团长可改提货点名称/地址，普通用户提交会被后端拒绝。
 * @param {{pickupPointName?: string, pickupPointAddress?: string}} data
 */
export function updateProfile(data) {
  return request.put('/auth/profile', data)
}
