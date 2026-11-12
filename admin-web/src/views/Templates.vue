<template>
  <div class="page">
    <div class="page-head">
      <h2>周菜单模板</h2>
      <div class="row-gap">
        <el-button @click="fromLast">把上周另存为模板</el-button>
        <el-button type="primary" @click="openEdit()">新建模板</el-button>
      </div>
    </div>
    <p class="muted">应用到某周时写入「计划」，不会把菜单标为已确认。已确认或已开饭的餐次不会被覆盖；已有计划默认也不覆盖，可勾选覆盖计划。</p>

    <el-table :data="templates" v-loading="loading" stripe>
      <el-table-column prop="name" label="名称" width="180" />
      <el-table-column label="内容">
        <template #default="{ row }">{{ preview(row) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="260">
        <template #default="{ row }">
          <el-button link type="primary" @click="openApply(row)">应用</el-button>
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="editDlg" :title="editForm.id ? '编辑模板' : '新建模板'" width="720px">
      <el-form label-width="80px">
        <el-form-item label="名称" required><el-input v-model="editForm.name" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="editForm.note" /></el-form-item>
      </el-form>
      <el-table :data="editForm.items" size="small">
        <el-table-column label="星期" width="120">
          <template #default="{ row }">
            <el-select v-model="row.dayOfWeek">
              <el-option v-for="d in 7" :key="d" :label="DOW[d]" :value="d" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="餐次" width="120">
          <template #default="{ row }">
            <el-select v-model="row.mealType">
              <el-option v-for="(l, k) in MEAL_LABELS" :key="k" :label="l" :value="k" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="菜">
          <template #default="{ row }">
            <el-select v-model="row.dishId" clearable filterable placeholder="菜谱（可空，手写）" style="width: 180px" @change="() => { if (row.dishId) row.customName = '' }">
              <el-option v-for="d in dishes" :key="d.id" :label="d.name" :value="d.id" />
            </el-select>
            <el-input v-model="row.customName" placeholder="手写菜名" style="width: 140px; margin-left: 8px" @input="() => { if (row.customName) row.dishId = null }" />
          </template>
        </el-table-column>
        <el-table-column label="份数" width="100">
          <template #default="{ row }"><el-input-number v-model="row.servings" :min="1" :max="20" size="small" /></template>
        </el-table-column>
        <el-table-column width="60">
          <template #default="{ $index }"><el-button link type="danger" @click="editForm.items.splice($index, 1)">删</el-button></template>
        </el-table-column>
      </el-table>
      <el-button style="margin-top: 8px" @click="editForm.items.push({ dayOfWeek: 1, mealType: 'DINNER', dishId: null, customName: '', servings: 2 })">加一道</el-button>
      <template #footer>
        <el-button @click="editDlg = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="applyDlg" title="应用到某周" width="440px">
      <p>请选择那一周的周一。已确认 / 已开饭的餐次不会被覆盖。</p>
      <el-form label-width="100px">
        <el-form-item label="周起始日">
          <el-date-picker v-model="applyForm.weekStart" type="date" value-format="YYYY-MM-DD" placeholder="周一" />
        </el-form-item>
        <el-form-item label="覆盖已有计划">
          <el-switch v-model="applyForm.overwrite" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="applyDlg = false">取消</el-button>
        <el-button type="primary" @click="doApply">应用</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, MEAL_LABELS } from '../api'

const DOW = { 1: '周一', 2: '周二', 3: '周三', 4: '周四', 5: '周五', 6: '周六', 7: '周日' }
const templates = ref([])
const dishes = ref([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    templates.value = await api.weekTemplates()
    dishes.value = await api.dishes()
  } finally {
    loading.value = false
  }
}
function preview(t) {
  return (t.items || []).slice(0, 8).map((i) => `${DOW[i.dayOfWeek]}${MEAL_LABELS[i.mealType]} ${i.name}`).join('；')
}

const editDlg = ref(false)
const editForm = reactive({ id: null, name: '', note: '', items: [] })
function openEdit(row) {
  if (row) {
    Object.assign(editForm, { id: row.id, name: row.name, note: row.note || '', items: (row.items || []).map((i) => ({
      dayOfWeek: i.dayOfWeek, mealType: i.mealType, dishId: i.dishId || null, customName: i.dishId ? '' : (i.name || ''), servings: i.servings,
    })) })
  } else {
    Object.assign(editForm, { id: null, name: '', note: '', items: [{ dayOfWeek: 1, mealType: 'DINNER', dishId: null, customName: '', servings: 2 }] })
  }
  editDlg.value = true
}
async function save() {
  const body = {
    name: editForm.name,
    note: editForm.note,
    items: editForm.items.filter((i) => i.dishId || (i.customName && i.customName.trim())).map((i) => ({
      dayOfWeek: i.dayOfWeek, mealType: i.mealType, dishId: i.dishId, customName: i.dishId ? null : i.customName, servings: i.servings,
    })),
  }
  if (editForm.id) await api.updateWeekTemplate(editForm.id, body)
  else await api.createWeekTemplate(body)
  ElMessage.success('已保存')
  editDlg.value = false
  load()
}
async function remove(row) {
  await ElMessageBox.confirm(`删除模板「${row.name}」？`, '删除', { type: 'warning' })
  await api.deleteWeekTemplate(row.id)
  load()
}

async function fromLast() {
  const d = new Date()
  d.setDate(d.getDate() - ((d.getDay() + 6) % 7) - 7)
  const weekStart = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
  await ElMessageBox.confirm(`把 ${weekStart} 那一周已确认的日常菜单另存为模板？`, '另存')
  await api.templateFromWeek({ weekStart, name: '上周家常' })
  ElMessage.success('已保存')
  load()
}

const applyDlg = ref(false)
const applyForm = reactive({ id: null, weekStart: '', overwrite: false })
function openApply(row) {
  const d = new Date()
  d.setDate(d.getDate() - ((d.getDay() + 6) % 7) + 7)
  applyForm.id = row.id
  applyForm.weekStart = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
  applyForm.overwrite = false
  applyDlg.value = true
}
async function doApply() {
  const res = await api.applyTemplate(applyForm.id, { weekStart: applyForm.weekStart, overwrite: applyForm.overwrite })
  ElMessage.success(`已写入 ${res.appliedSlots} 个餐次、${res.itemsWritten} 道菜；跳过已确认 ${res.skippedConfirmed}、已有计划 ${res.skippedExistingPlan}`)
  applyDlg.value = false
}

onMounted(load)
</script>
