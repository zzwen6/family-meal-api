<template>
  <div class="page" v-loading="loading">
    <div class="page-head">
      <h2>库存与剩菜</h2>
    </div>
    <p class="muted">生成采购清单时会按名称对照库存和未用完剩菜，少买已经有的，但生成时不改库存数字（避免反复生成重复扣）。勾选已买会把购买量增加到库存，取消勾选按入账数量撤回；用掉名称对得上食材的剩菜、以及开饭时会按「对照时家里已有」的量扣库存。也可以随时手工改。</p>

    <el-card>
      <template #header>
        <div class="row-gap" style="justify-content: space-between">
          <span>家庭库存</span>
          <el-button type="primary" size="small" :disabled="!canEdit" @click="openInv()">登记一样</el-button>
        </div>
      </template>
      <el-table :data="inventory" size="small" empty-text="还没有库存">
        <el-table-column prop="name" label="名称" />
        <el-table-column label="数量" width="140">
          <template #default="{ row }">{{ row.amount ?? '适量' }} {{ row.unit || '' }}</template>
        </el-table-column>
        <el-table-column label="常备" width="80">
          <template #default="{ row }">{{ row.staple ? '是' : '' }}</template>
        </el-table-column>
        <el-table-column prop="note" label="备注" />
        <el-table-column v-if="canEdit" label="" width="140">
          <template #default="{ row }">
            <el-button link type="primary" @click="openInv(row)">改</el-button>
            <el-button link type="danger" @click="removeInv(row)">删</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card style="margin-top: 20px">
      <template #header>
        <div class="row-gap" style="justify-content: space-between">
          <span>剩菜</span>
          <div>
            <el-checkbox v-model="showUsed" @change="load">显示已用完的</el-checkbox>
            <el-button type="primary" size="small" :disabled="!canEdit" style="margin-left: 12px" @click="openLo()">登记剩菜</el-button>
          </div>
        </div>
      </template>
      <el-table :data="leftovers" size="small" empty-text="没有剩菜">
        <el-table-column prop="name" label="名称" />
        <el-table-column label="数量" width="140">
          <template #default="{ row }">{{ row.amount ?? '适量' }} {{ row.unit || '' }}</template>
        </el-table-column>
        <el-table-column label="保鲜到" width="130">
          <template #default="{ row }">{{ row.expiresOn || '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.used ? 'info' : 'success'">{{ row.used ? '已用完' : '可先吃' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="note" label="备注" />
        <el-table-column v-if="canEdit" label="" width="180">
          <template #default="{ row }">
            <el-button v-if="!row.used" link type="primary" @click="useLo(row)">用掉</el-button>
            <el-button v-if="!row.used" link type="primary" @click="openLo(row)">改</el-button>
            <el-button link type="danger" @click="removeLo(row)">删</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card style="margin-top: 20px" v-if="ledger.length">
      <template #header>最近变动</template>
      <el-table :data="ledger" size="small">
        <el-table-column label="类型" width="120">
          <template #default="{ row }">{{ LEDGER_LABELS[row.sourceType] || row.sourceType }}</template>
        </el-table-column>
        <el-table-column prop="name" label="名称" />
        <el-table-column label="变动" width="120">
          <template #default="{ row }">{{ row.delta > 0 ? '+' : '' }}{{ row.delta }}{{ row.unit || '' }}</template>
        </el-table-column>
        <el-table-column label="结余" width="120">
          <template #default="{ row }">{{ row.balanceAfter == null ? '—' : row.balanceAfter }}{{ row.unit || '' }}</template>
        </el-table-column>
        <el-table-column prop="note" label="说明" />
        <el-table-column prop="createdAt" label="时间" width="180" />
      </el-table>
    </el-card>

    <el-dialog v-model="invDlg" :title="invForm.id ? '改库存' : '登记库存'" width="420px">
      <el-form :model="invForm" label-width="80px">
        <el-form-item label="名称"><el-input v-model="invForm.name" maxlength="64" /></el-form-item>
        <el-form-item label="数量"><el-input v-model="invForm.amount" placeholder="可留空表示适量" /></el-form-item>
        <el-form-item label="单位"><el-input v-model="invForm.unit" maxlength="16" /></el-form-item>
        <el-form-item label="常备"><el-switch v-model="invForm.staple" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="invForm.note" maxlength="255" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="invDlg = false">取消</el-button>
        <el-button type="primary" @click="saveInv">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="loDlg" :title="loForm.id ? '改剩菜' : '登记剩菜'" width="420px">
      <el-form :model="loForm" label-width="80px">
        <el-form-item label="名称"><el-input v-model="loForm.name" maxlength="64" /></el-form-item>
        <el-form-item label="数量"><el-input v-model="loForm.amount" placeholder="可留空表示适量" /></el-form-item>
        <el-form-item label="单位"><el-input v-model="loForm.unit" maxlength="16" /></el-form-item>
        <el-form-item label="保鲜到"><el-date-picker v-model="loForm.expiresOn" value-format="YYYY-MM-DD" type="date" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="loForm.note" maxlength="255" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="loDlg = false">取消</el-button>
        <el-button type="primary" @click="saveLo">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, LEDGER_LABELS } from '../api'

const loading = ref(false)
const inventory = ref([])
const ledger = ref([])
const leftovers = ref([])
const showUsed = ref(false)
const family = ref(null)
const canEdit = computed(() => family.value && (family.value.myRole === 'ADMIN' || family.value.myRole === 'COOK'))

const invDlg = ref(false)
const invForm = reactive({ id: null, name: '', amount: '', unit: '', staple: false, note: '' })
const loDlg = ref(false)
const loForm = reactive({ id: null, name: '', amount: '', unit: '', expiresOn: '', note: '' })

async function load() {
  loading.value = true
  try {
    family.value = await api.family()
    const [inv, lo, led] = await Promise.all([
      api.inventory(),
      api.leftovers(showUsed.value ? undefined : false),
      api.inventoryLedger(20),
    ])
    inventory.value = inv
    leftovers.value = lo
    ledger.value = led
  } finally {
    loading.value = false
  }
}

function num(v) {
  if (v === '' || v === null || v === undefined) return null
  const n = Number(v)
  return Number.isFinite(n) ? n : null
}

function openInv(row) {
  Object.assign(invForm, row ? { ...row, amount: row.amount ?? '', unit: row.unit || '', note: row.note || '' }
    : { id: null, name: '', amount: '', unit: '', staple: false, note: '' })
  invDlg.value = true
}

async function saveInv() {
  if (!invForm.name.trim()) return
  const body = { name: invForm.name.trim(), amount: num(invForm.amount), unit: invForm.unit || null, staple: invForm.staple, note: invForm.note || null }
  if (invForm.id) await api.updateInventory(invForm.id, body)
  else await api.createInventory(body)
  invDlg.value = false
  ElMessage.success('已保存')
  load()
}

async function removeInv(row) {
  await ElMessageBox.confirm(`删除库存「${row.name}」？`, '删除', { type: 'warning' })
  await api.deleteInventory(row.id)
  load()
}

function openLo(row) {
  Object.assign(loForm, row ? { ...row, amount: row.amount ?? '', unit: row.unit || '', expiresOn: row.expiresOn || '', note: row.note || '' }
    : { id: null, name: '', amount: '', unit: '', expiresOn: '', note: '' })
  loDlg.value = true
}

async function saveLo() {
  if (!loForm.name.trim()) return
  const body = { name: loForm.name.trim(), amount: num(loForm.amount), unit: loForm.unit || null, expiresOn: loForm.expiresOn || null, note: loForm.note || null }
  if (loForm.id) await api.updateLeftover(loForm.id, body)
  else await api.createLeftover(body)
  loDlg.value = false
  ElMessage.success('已保存')
  load()
}

async function useLo(row) {
  await api.useLeftover(row.id)
  ElMessage.success('已标记用完')
  load()
}

async function removeLo(row) {
  await ElMessageBox.confirm(`删除剩菜「${row.name}」？`, '删除', { type: 'warning' })
  await api.deleteLeftover(row.id)
  load()
}

onMounted(load)
</script>
