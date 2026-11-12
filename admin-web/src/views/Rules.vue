<template>
  <div class="page" v-loading="loading">
    <div class="page-head"><h2>家庭规则</h2></div>
    <el-card v-if="family" style="max-width: 620px">
      <el-form :model="form" label-width="120px">
        <el-form-item label="家庭名称"><el-input v-model="form.name" maxlength="64" /></el-form-item>
        <el-form-item label="早餐截止">
          <el-time-select v-model="form.breakfastDeadline" start="05:00" step="00:30" end="23:30" />
          <span class="muted" style="margin-left: 8px">前一天的这个时间</span>
        </el-form-item>
        <el-form-item label="午餐截止">
          <el-time-select v-model="form.lunchDeadline" start="05:00" step="00:30" end="14:00" />
          <span class="muted" style="margin-left: 8px">当天</span>
        </el-form-item>
        <el-form-item label="晚餐截止">
          <el-time-select v-model="form.dinnerDeadline" start="08:00" step="00:30" end="21:00" />
          <span class="muted" style="margin-left: 8px">当天</span>
        </el-form-item>
        <el-form-item label="吃腻窗口">
          <el-select v-model="form.fatigueWindowDays" style="width: 140px">
            <el-option :value="7" label="最近 7 天" />
            <el-option :value="14" label="最近 14 天" />
            <el-option :value="21" label="最近 21 天" />
          </el-select>
        </el-form-item>
        <el-form-item label="吃腻阈值">
          <el-input-number v-model="form.fatigueThreshold" :min="1" :max="20" />
          <span class="muted" style="margin-left: 8px">同一道菜出现这么多次就提示可能吃腻了，默认 2 次</span>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :disabled="family.myRole !== 'ADMIN'" @click="save">保存规则</el-button>
          <span v-if="family.myRole !== 'ADMIN'" class="muted" style="margin-left: 8px">只有管理员可以修改</span>
        </el-form-item>
        <el-divider />
        <el-form-item label="邀请码">
          <el-tag size="large" style="font-size: 18px; letter-spacing: 2px">{{ family.inviteCode }}</el-tag>
          <el-button v-if="family.myRole === 'ADMIN'" link type="primary" style="margin-left: 12px" @click="refresh">换一个</el-button>
        </el-form-item>
        <p class="muted" style="padding-left: 120px">家人在小程序「我的」或 H5 注册时输入邀请码即可加入。截止后成员不能再改，掌厨可以在工作台里重新开放。</p>
      </el-form>
    </el-card>

    <el-card v-if="family" style="max-width: 720px; margin-top: 20px" v-loading="rotLoading">
      <template #header>掌厨轮换</template>
      <p class="muted">参与轮换的人不需要先改成「掌厨」角色。生成安排时按谁做得少、较久没做、再按下面的顺序填空档，不会覆盖已经手工指定的。</p>
      <el-form label-width="120px">
        <el-form-item label="启用自动安排">
          <el-switch v-model="rotation.enabled" :disabled="family.myRole !== 'ADMIN'" />
        </el-form-item>
        <el-form-item label="适用餐次">
          <el-checkbox-group v-model="rotation.mealTypes" :disabled="family.myRole !== 'ADMIN'">
            <el-checkbox label="BREAKFAST">早餐</el-checkbox>
            <el-checkbox label="LUNCH">午餐</el-checkbox>
            <el-checkbox label="DINNER">晚餐</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="参与成员">
          <el-select v-model="rotation.memberIds" multiple placeholder="按选择顺序排班" style="width: 100%" :disabled="family.myRole !== 'ADMIN'">
            <el-option v-for="m in members" :key="m.id" :label="`${m.displayName}（${ROLE_LABELS[m.role]}）`" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :disabled="family.myRole !== 'ADMIN'" @click="saveRotation">保存轮换设置</el-button>
          <el-button :disabled="family.myRole !== 'ADMIN' && family.myRole !== 'COOK'" @click="generateWeek">生成本周安排</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, ROLE_LABELS } from '../api'

const family = ref(null)
const members = ref([])
const loading = ref(false)
const rotLoading = ref(false)
const form = reactive({ name: '', breakfastDeadline: '', lunchDeadline: '', dinnerDeadline: '', fatigueWindowDays: 14, fatigueThreshold: 2 })
const rotation = reactive({ enabled: false, mealTypes: ['DINNER'], memberIds: [] })

async function load() {
  loading.value = true
  try {
    family.value = await api.family()
    members.value = await api.members()
    Object.assign(form, {
      name: family.value.name,
      breakfastDeadline: family.value.breakfastDeadline,
      lunchDeadline: family.value.lunchDeadline,
      dinnerDeadline: family.value.dinnerDeadline,
      fatigueWindowDays: family.value.fatigueWindowDays ?? 14,
      fatigueThreshold: family.value.fatigueThreshold ?? 2,
    })
    const r = await api.cookRotation()
    rotation.enabled = r.enabled
    rotation.mealTypes = r.mealTypes?.length ? [...r.mealTypes] : ['DINNER']
    rotation.memberIds = (r.members || []).map((m) => m.memberId)
  } finally {
    loading.value = false
  }
}

async function save() {
  family.value = await api.updateFamily({ ...form })
  ElMessage.success('规则已保存')
}

async function saveRotation() {
  rotLoading.value = true
  try {
    const r = await api.updateCookRotation({
      enabled: rotation.enabled,
      mealTypes: rotation.mealTypes,
      memberIds: rotation.memberIds,
    })
    rotation.enabled = r.enabled
    rotation.mealTypes = [...r.mealTypes]
    rotation.memberIds = (r.members || []).map((m) => m.memberId)
    ElMessage.success('轮换设置已保存')
  } finally {
    rotLoading.value = false
  }
}

async function generateWeek() {
  rotLoading.value = true
  try {
    const list = await api.generateAssignments({ days: 7 })
    ElMessage.success(`已生成本周 ${list.length} 个安排，可在日历总览里查看`)
  } finally {
    rotLoading.value = false
  }
}

async function refresh() {
  await ElMessageBox.confirm('换邀请码后旧的就不能用了，已加入的家人不受影响。', '更换邀请码', { type: 'warning' })
  family.value = await api.refreshInvite()
}

onMounted(load)
</script>
