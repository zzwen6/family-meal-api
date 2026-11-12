<template>
  <div class="page">
    <div class="page-head">
      <h2>统计</h2>
      <div class="row-gap">
        <el-date-picker v-model="range" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始" end-placeholder="结束" @change="load" />
        <el-button @click="useWeek">本周</el-button>
        <el-button @click="use30">最近 30 天</el-button>
      </div>
    </div>
    <p class="muted">只统计本家庭已确认 / 已开饭的日常菜单和点餐意愿，不含任何临时聚餐。餐后评价在开饭后提交，计入常做菜的平均分。</p>

    <el-card shadow="never" style="margin-bottom: 16px">
      <template #header>每天吃了什么</template>
      <el-table :data="data?.days || []" size="small" empty-text="这一段还没有菜单">
        <el-table-column label="日期" width="120"><template #default="{ row }">{{ row.date }}</template></el-table-column>
        <el-table-column label="餐次" width="80"><template #default="{ row }">{{ MEAL_LABELS[row.mealType] }}</template></el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">{{ row.plannedOnly ? '计划' : STATUS_LABELS[row.status] }}</template>
        </el-table-column>
        <el-table-column label="菜">
          <template #default="{ row }">{{ row.items.map((i) => i.name).join('、') || '—' }}</template>
        </el-table-column>
        <el-table-column label="安排掌厨" width="110"><template #default="{ row }">{{ row.assignedMemberName || '—' }}</template></el-table-column>
        <el-table-column label="实际确认" width="110"><template #default="{ row }">{{ row.confirmedByName || '—' }}</template></el-table-column>
        <el-table-column label="平均分" width="90">
          <template #default="{ row }">{{ row.averageScore != null ? row.averageScore.toFixed(1) : '—' }}</template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-row :gutter="16">
      <el-col :span="12">
        <el-card shadow="never" style="margin-bottom: 16px">
          <template #header>常做的菜</template>
          <el-table :data="data?.frequentDishes || []" size="small" empty-text="暂无">
            <el-table-column prop="name" label="菜" />
            <el-table-column prop="times" label="次数" width="70" />
            <el-table-column prop="lastCooked" label="上次" width="110" />
            <el-table-column label="平均分" width="90">
              <template #default="{ row }">{{ row.averageScore != null ? row.averageScore.toFixed(1) : '—' }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never" style="margin-bottom: 16px">
          <template #header>常点的菜（WANT 次数与选中比例）</template>
          <el-table :data="data?.frequentWishes || []" size="small" empty-text="暂无">
            <el-table-column prop="name" label="菜" />
            <el-table-column prop="wantCount" label="想吃" width="70" />
            <el-table-column prop="selectedCount" label="选中" width="70" />
            <el-table-column label="比例" min-width="140">
              <template #default="{ row }">
                <el-progress :percentage="Math.round(row.selectedRate * 100)" :stroke-width="10" />
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" style="margin-bottom: 16px">
      <template #header>心愿公平与掌厨次数</template>
      <el-table :data="data?.fairness || []" size="small">
        <el-table-column prop="displayName" label="成员" width="120" />
        <el-table-column label="WANT 兑现" width="140">
          <template #default="{ row }">{{ row.fulfilledCount }} / {{ row.wantCount }}</template>
        </el-table-column>
        <el-table-column label="兑现比例" min-width="180">
          <template #default="{ row }">
            <el-progress :percentage="Math.round(row.fulfillmentRate * 100)" :stroke-width="10" />
          </template>
        </el-table-column>
        <el-table-column prop="assignedCookCount" label="安排掌厨" width="100" />
        <el-table-column prop="confirmedCookCount" label="实际确认" width="100" />
      </el-table>
    </el-card>

    <el-card v-if="data?.fatigue?.length" shadow="never">
      <template #header>吃腻（窗口 {{ data.fatigue[0].windowDays }} 天，阈值 {{ data.fatigue[0].threshold }} 次）</template>
      <el-table :data="data.fatigue" size="small">
        <el-table-column prop="name" label="菜" />
        <el-table-column prop="times" label="次数" width="80" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { api, fmtDate, MEAL_LABELS, STATUS_LABELS } from '../api'

const data = ref(null)
const range = ref([])

function monday(d) {
  const x = new Date(d)
  x.setDate(x.getDate() - ((x.getDay() + 6) % 7))
  return x
}
function useWeek() {
  const s = monday(new Date())
  const e = new Date(s); e.setDate(e.getDate() + 6)
  range.value = [fmtDate(s), fmtDate(e)]
  load()
}
function use30() {
  const e = new Date()
  const s = new Date(); s.setDate(s.getDate() - 29)
  range.value = [fmtDate(s), fmtDate(e)]
  load()
}
async function load() {
  if (!range.value?.length) return
  data.value = await api.stats(range.value[0], range.value[1])
}
onMounted(useWeek)
</script>
