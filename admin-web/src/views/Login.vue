<template>
  <div class="login-wrap">
    <el-card class="login-card" shadow="always">
      <h1>家庭点餐</h1>
      <p class="muted">管理后台 · 菜谱、成员和点餐规则</p>
      <el-form :model="form" @submit.prevent="submit" label-position="top">
        <el-form-item label="用户名">
          <el-input v-model="form.username" autocomplete="username" placeholder="管理员用户名" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password autocomplete="current-password" />
        </el-form-item>
        <el-button type="primary" native-type="submit" :loading="loading" style="width: 100%">登录</el-button>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api, auth } from '../api'

const router = useRouter()
const form = reactive({ username: '', password: '' })
const loading = ref(false)

async function submit() {
  if (!form.username || !form.password) return ElMessage.warning('请输入用户名和密码')
  loading.value = true
  try {
    const res = await api.login(form.username, form.password)
    const manageable = res.families.filter((f) => f.myRole === 'ADMIN' || f.myRole === 'COOK')
    if (!manageable.length) {
      ElMessage.error('该账号不是任何家庭的管理员或掌厨')
      return
    }
    auth.token = res.token
    auth.familyId = manageable[0].id
    router.replace('/calendar')
  } catch {
    /* 已提示 */
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-wrap { min-height: 100%; display: flex; align-items: center; justify-content: center; background: linear-gradient(135deg, #fff4e6, #ffe0c2); }
.login-card { width: 360px; }
h1 { margin: 0 0 4px; font-size: 24px; }
</style>
