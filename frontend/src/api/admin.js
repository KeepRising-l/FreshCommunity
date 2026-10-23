import request from '../utils/request'

/**
 * 管理看板。
 * data: {
 *   todayOrders, todaySuccessGroups, todayGmv,
 *   totalUsers, ongoingGroupBuys,
 *   orderStatusDistribution: [{ status, count }],
 *   trend7d: [{ date: 'yyyy-MM-dd', orders, gmv }]
 * }
 */
export function getDashboard() {
  return request.get('/admin/dashboard')
}
