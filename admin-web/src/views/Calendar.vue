<template>
  <div class="page">
    <div class="page-head">
      <h2>日历总览</h2>
      <div class="row-gap">
        <el-button @click="shift(-7)">上一周</el-button>
        <el-button @click="goToday">本周</el-button>
        <el-button @click="shift(7)">下一周</el-button>
      </div>
    </div>
    <p class="muted">{{ days[0]?.date }} 至 {{ days[6]?.date }}。点击格子查看当餐的意愿、菜单和采购清单。</p>
    <div class="grid" v-loading="loading">
      <div class="cell head"></div>
      <div v-for="d in days" :key="d.date" class="cell head" :class="{ today: d.date === todayStr }">
        {{ d.label }}<br /><span class="muted">{{ d.date.slice(5) }}</span>
      </div>
      <template v-for="meal in meals" :key="meal">
        <div class="cell head">{{ MEAL_LABELS[meal] }}</div>
        <div v-for="d in days" :key="d.date + meal" class="cell slot" @click="open(d.date, meal)">
          <template v-if="slot(d.date, meal)">
            <el-tag size="small" :type="STATUS_TYPES[slot(d.date, meal).status]">{{ STATUS_LABELS[slot(d.date, meal).status] }}</el-tag>
            <div class="items">
              <div v-for="it in slot(d.date, meal).items" :key="it.id">· {{ it.name }}</div>
            </div>
            <div class="muted" v-if="!slot(d.date, meal).items.length">{{ slot(d.date, meal).wishCount }} 条意愿</div>
          </template>
          <span v-else class="muted">—</span>
        </div>
      </template>
    </div>

    <el-drawer v-model="drawer" :title="drawerTitle" size="520px">
      <div v-if="board" v-loading="boardLoading">
        <p>
          截止：{{ board.deadline?.replace('T', ' ').slice(0, 16) }}，预计 {{ board.headcount }} 人在家吃
          <el-tag v-if="board.menu" size="small" :type="STATUS_TYPES[board.menu.status]" style="margin-left: 8px">{{ STATUS_LABELS[board.menu.status] }}</el-tag>
        </p>
        <h4>成员</h4>
        <div class="row-gap">
          <el-tag v-for="m in board.members" :key="m.memberId" :type="m.notEatingHome ? 'info' : m.submitted ? 'success' : 'warning'">
            {{ m.displayName }}{{ m.notEatingHome ? '（不在家）' : m.submitted ? '' : '（未点）' }}
          </el-tag>
        </div>
        <h4>按菜汇总</h4>
        <el-table :data="board.votes" size="small" empty-text="还没人点">
          <el-table-column prop="name" label="菜" />
          <el-table-column label="很想吃" width="70"><template #default="{ row }">{{ row.wantCount }}</template></el-table-column>
          <el-table-column label="都可以" width="70"><template #default="{ row }">{{ row.okCount }}</template></el-table-column>
          <el-table-column label="别做" width="60"><template #default="{ row }">{{ row.avoidCount }}</template></el-table-column>
          <el-table-column label="谁想吃"><template #default="{ row }">{{ row.wantBy.join('、') }}</template></el-table-column>
        </el-table>
        <template v-if="board.menu?.items?.length">
          <h4>已确认菜单</h4>
          <ul>
            <li v-for="it in board.menu.items" :key="it.id">
              {{ it.name }} × {{ it.servings }} 份
              <span class="muted" v-if="it.substituteForName">（替代 {{ it.substituteForName }}）</span>
              <span class="muted" v-if="it.note">— {{ it.note }}</span>
            </li>
          </ul>
        </template>
        <template v-if="shopping">
          <h4>采购清单（{{ shopping.purchasedCount }}/{{ shopping.toBuyCount }} 已买）</h4>
          <el-table :data="shopping.buyItems" size="small">
            <el-table-column prop="name" label="食材" />
            <el-table-column label="用量" width="100"><template #default="{ row }">{{ row.amount ?? '适量' }} {{ row.unit || '' }}</template></el-table-column>
            <el-table-column label="已买" width="60"><template #default="{ row }">{{ row.purchased ? '✓' : '' }}</template></el-table-column>
            <el-table-column prop="sourceDishes" label="用于" />
          </el-table>
          <p class="muted" v-if="shopping.stapleItems.length">家里常备（默认不买）：{{ shopping.stapleItems.map((i) => i.name).join('、') }}</p>
        </template>
        <p class="muted">确认菜单、勾选采购请在小程序 / H5 的「掌厨工作台」里操作。</p>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { api, fmtDate, MEAL_LABELS, STATUS_LABELS, STATUS_TYPES } from '../api'

const meals = ['BREAKFAST', 'LUNCH', 'DINNER']
const WEEK = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
const todayStr = fmtDate(new Date())
const start = ref(monday(new Date()))
const menus = ref([])
const loading = ref(false)

const days = computed(() => Array.from({ length: 7 }, (_, i) => {
  const d = new Date(start.value)
  d.setDate(d.getDate() + i)
  return { date: fmtDate(d), label: WEEK[d.getDay()] }
}))

function monday(d) {
  const x = new Date(d)
  x.setDate(x.getDate() - ((x.getDay() + 6) % 7))
  return x
}
function shift(n) {
  const d = new Date(start.value)
  d.setDate(d.getDate() + n)
  start.value = d
  load()
}
function goToday() { start.value = monday(new Date()); load() }
function slot(date, meal) { return menus.value.find((m) => m.mealDate === date && m.mealType === meal) }

async function load() {
  loading.value = true
  try {
    menus.value = await api.calendar(days.value[0].date, days.value[6].date)
  } finally {
    loading.value = false
  }
}

const drawer = ref(false)
const drawerTitle = ref('')
const board = ref(null)
const boardLoading = ref(false)
const shopping = ref(null)

async function open(date, meal) {
  drawer.value = true
  drawerTitle.value = `${date} ${MEAL_LABELS[meal]}`
  board.value = null
  shopping.value = null
  boardLoading.value = true
  try {
    board.value = await api.board(date, meal)
    if (board.value.menu && ['CONFIRMED', 'READY'].includes(board.value.menu.status)) {
      shopping.value = await api.shoppingList(board.value.menu.id).catch(() => null)
    }
  } finally {
    boardLoading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.grid { display: grid; grid-template-columns: 70px repeat(7, 1fr); gap: 6px; }
.cell { background: #fff; border-radius: 6px; padding: 8px; min-height: 40px; font-size: 13px; }
.cell.head { background: transparent; font-weight: 600; text-align: center; min-height: auto; }
.cell.head.today { color: #e6772e; }
.cell.slot { cursor: pointer; min-height: 90px; border: 1px solid #eee; }
.cell.slot:hover { border-color: #e6772e; }
.items { margin-top: 6px; line-height: 1.6; }
</style>
