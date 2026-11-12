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
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../api'

const family = ref(null)
const loading = ref(false)
const form = reactive({ name: '', breakfastDeadline: '', lunchDeadline: '', dinnerDeadline: '' })

async function load() {
  loading.value = true
  try {
    family.value = await api.family()
    Object.assign(form, {
      name: family.value.name,
      breakfastDeadline: family.value.breakfastDeadline,
      lunchDeadline: family.value.lunchDeadline,
      dinnerDeadline: family.value.dinnerDeadline,
    })
  } finally {
    loading.value = false
  }
}

async function save() {
  family.value = await api.updateFamily({ ...form })
  ElMessage.success('规则已保存')
}

async function refresh() {
  await ElMessageBox.confirm('换邀请码后旧的就不能用了，已加入的家人不受影响。', '更换邀请码', { type: 'warning' })
  family.value = await api.refreshInvite()
}

onMounted(load)
</script>
