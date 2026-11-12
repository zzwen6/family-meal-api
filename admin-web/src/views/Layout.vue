<template>
  <el-container style="height: 100%">
    <el-aside width="200px" class="aside">
      <div class="brand">🍲 家庭点餐</div>
      <el-menu :default-active="$route.path" router>
        <el-menu-item index="/calendar">日历总览</el-menu-item>
        <el-menu-item index="/dishes">菜谱管理</el-menu-item>
        <el-menu-item index="/members">成员管理</el-menu-item>
        <el-menu-item index="/rules">家庭规则</el-menu-item>
        <el-menu-item index="/inventory">库存与剩菜</el-menu-item>
        <el-menu-item index="/templates">周菜单模板</el-menu-item>
        <el-menu-item index="/stats">统计</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <div>
          <el-select v-model="familyId" style="width: 200px" @change="switchFamily">
            <el-option v-for="f in families" :key="f.id" :label="`${f.name}（${ROLE_LABELS[f.myRole]}）`" :value="f.id" />
          </el-select>
        </div>
        <div class="row-gap">
          <span class="muted">{{ user?.nickname }}</span>
          <el-button link @click="logout">退出</el-button>
        </div>
      </el-header>
      <el-main style="padding: 0">
        <router-view :key="familyId" />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api, auth, ROLE_LABELS } from '../api'

const router = useRouter()
const families = ref([])
const user = ref(null)
const familyId = ref(auth.familyId)

onMounted(async () => {
  const me = await api.me()
  user.value = me.user
  families.value = me.families.filter((f) => f.myRole === 'ADMIN' || f.myRole === 'COOK')
  if (!families.value.some((f) => f.id === familyId.value) && families.value.length) {
    familyId.value = families.value[0].id
    auth.familyId = familyId.value
  }
})

function switchFamily(id) {
  auth.familyId = id
}

function logout() {
  auth.logout()
  router.replace('/login')
}
</script>

<style scoped>
.aside { background: #fff; border-right: 1px solid #eee; }
.brand { height: 60px; display: flex; align-items: center; padding-left: 20px; font-weight: 600; font-size: 17px; }
.header { background: #fff; border-bottom: 1px solid #eee; display: flex; align-items: center; justify-content: space-between; }
</style>
