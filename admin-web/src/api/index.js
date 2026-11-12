import { ElMessage } from 'element-plus'

const TOKEN_KEY = 'fm_admin_token'
const FAMILY_KEY = 'fm_admin_family'

export const auth = {
  get token() { return localStorage.getItem(TOKEN_KEY) },
  set token(v) { v ? localStorage.setItem(TOKEN_KEY, v) : localStorage.removeItem(TOKEN_KEY) },
  get familyId() { return Number(localStorage.getItem(FAMILY_KEY)) || null },
  set familyId(v) { v ? localStorage.setItem(FAMILY_KEY, String(v)) : localStorage.removeItem(FAMILY_KEY) },
  logout() { this.token = null; this.familyId = null },
}

/** 统一请求：带 JWT，错误按后端 { code, message } 提示。 */
export async function request(method, url, body, { silent = false } = {}) {
  const headers = { Accept: 'application/json' }
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (auth.token) headers.Authorization = `Bearer ${auth.token}`
  let res
  try {
    res = await fetch(url, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) })
  } catch (e) {
    if (!silent) ElMessage.error('网络错误，无法连接服务器')
    throw e
  }
  const text = await res.text()
  const data = text ? JSON.parse(text) : null
  if (!res.ok) {
    if (res.status === 401 && !url.startsWith('/api/auth/login')) {
      auth.logout()
      location.hash = '#/login'
    }
    const msg = data?.message || `请求失败（${res.status}）`
    if (!silent) ElMessage.error(msg)
    const err = new Error(msg)
    err.status = res.status
    err.code = data?.code
    throw err
  }
  return data
}

const f = () => `/api/families/${auth.familyId}`

export const api = {
  login: (username, password) => request('POST', '/api/auth/login', { username, password }),
  me: () => request('GET', '/api/auth/me'),
  family: () => request('GET', f()),
  updateFamily: (body) => request('PUT', f(), body),
  refreshInvite: () => request('POST', `${f()}/invite-code/refresh`),
  members: () => request('GET', `${f()}/members`),
  createMember: (body) => request('POST', `${f()}/members`, body),
  updateMember: (id, body) => request('PUT', `${f()}/members/${id}`, body),
  removeMember: (id) => request('DELETE', `${f()}/members/${id}`),
  dishes: (params = {}) => request('GET', `${f()}/dishes?` + new URLSearchParams({ includeInactive: 'true', ...params })),
  dish: (id) => request('GET', `${f()}/dishes/${id}`),
  categories: () => request('GET', `${f()}/dishes/categories`),
  createDish: (body) => request('POST', `${f()}/dishes`, body),
  updateDish: (id, body) => request('PUT', `${f()}/dishes/${id}`, body),
  deactivateDish: (id) => request('DELETE', `${f()}/dishes/${id}`),
  restoreDish: (id) => request('POST', `${f()}/dishes/${id}/restore`),
  calendar: (from, to) => request('GET', `${f()}/menus?from=${from}&to=${to}`),
  /** 临时聚餐在家庭日历上的占位（只有标题、发起人，不含聚餐详情） */
  gatheringSlots: (from, to) => request('GET', `${f()}/gatherings?from=${from}&to=${to}`),
  board: (date, mealType) => request('GET', `${f()}/menus/board?date=${date}&mealType=${mealType}`),
  shoppingList: (menuId) => request('GET', `${f()}/menus/${menuId}/shopping-list`, undefined, { silent: true }),
  cookRotation: () => request('GET', `${f()}/cook-rotation`),
  updateCookRotation: (body) => request('PUT', `${f()}/cook-rotation`, body),
  cookAssignments: (from, to) => request('GET', `${f()}/cook-assignments?from=${from}&to=${to}`),
  assignCook: (body) => request('PUT', `${f()}/cook-assignments`, body),
  generateAssignments: (body) => request('POST', `${f()}/cook-assignments/generate`, body),
  inventory: () => request('GET', `${f()}/inventory`),
  createInventory: (body) => request('POST', `${f()}/inventory`, body),
  updateInventory: (id, body) => request('PUT', `${f()}/inventory/${id}`, body),
  deleteInventory: (id) => request('DELETE', `${f()}/inventory/${id}`),
  leftovers: (used) => request('GET', `${f()}/leftovers` + (used === undefined ? '' : `?used=${used}`)),
  createLeftover: (body) => request('POST', `${f()}/leftovers`, body),
  updateLeftover: (id, body) => request('PUT', `${f()}/leftovers/${id}`, body),
  useLeftover: (id, menuId) => request('POST', `${f()}/leftovers/${id}/use`, { menuId }),
  deleteLeftover: (id) => request('DELETE', `${f()}/leftovers/${id}`),
}

export const ROLE_LABELS = { ADMIN: '管理员', COOK: '掌厨', MEMBER: '成员', CHILD: '儿童' }
export const MEAL_LABELS = { BREAKFAST: '早餐', LUNCH: '午餐', DINNER: '晚餐' }
export const STATUS_LABELS = { OPEN: '点餐中', LOCKED: '已截止', CONFIRMED: '已确认', READY: '已开饭' }
export const STATUS_TYPES = { OPEN: 'info', LOCKED: 'warning', CONFIRMED: 'success', READY: '' }
export const DIFFICULTY_LABELS = { EASY: '简单', MEDIUM: '中等', HARD: '有难度' }
export const GATHERING_STATUS_LABELS = { OPEN: '邀请中', CONFIRMED: '菜单已定', CLOSED: '已散场' }
export const INTENSITY_LABELS = { WANT: '很想吃', OK: '都可以', AVOID: '别做这个' }

export function fmtDate(d) {
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}
