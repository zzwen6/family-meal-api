<template>
  <div class="page">
    <div class="page-head">
      <h2>成员管理</h2>
      <el-button type="primary" @click="openCreate">添加成员账号</el-button>
    </div>
    <p class="muted">家人也可以在小程序里微信登录后输入邀请码加入（邀请码见「家庭规则」）。这里可以给孩子、老人建一个用户名密码账号，在 H5 上使用。</p>
    <el-table :data="members" v-loading="loading" stripe>
      <el-table-column prop="displayName" label="称呼" min-width="110" />
      <el-table-column label="角色" width="140">
        <template #default="{ row }">
          <el-select v-model="row.role" size="small" @change="(v) => changeRole(row, v)">
            <el-option v-for="(l, k) in ROLE_LABELS" :key="k" :label="l" :value="k" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="登录方式" width="150">
        <template #default="{ row }">
          <el-tag v-if="row.wechatBound" size="small" type="success">微信</el-tag>
          <el-tag v-if="row.username" size="small" style="margin-left: 4px">{{ row.username }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="监护人" min-width="110">
        <template #default="{ row }">
          <span v-if="row.role === 'CHILD'">{{ row.guardianName || '未指定' }}</span>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
      <el-table-column prop="allergies" label="过敏" min-width="120" />
      <el-table-column prop="dietaryNotes" label="忌口 / 口味" min-width="140" />
      <el-table-column label="操作" width="140">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">移除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="createDlg" title="添加成员账号" width="440px">
      <el-form :model="createForm" label-width="80px">
        <el-form-item label="称呼" required><el-input v-model="createForm.displayName" placeholder="比如：小宝、奶奶" /></el-form-item>
        <el-form-item label="角色">
          <el-select v-model="createForm.role"><el-option v-for="(l, k) in ROLE_LABELS" :key="k" :label="l" :value="k" /></el-select>
        </el-form-item>
        <el-form-item label="用户名" required><el-input v-model="createForm.username" /></el-form-item>
        <el-form-item label="密码" required><el-input v-model="createForm.password" type="password" show-password placeholder="至少 6 位" /></el-form-item>
        <el-form-item label="过敏"><el-input v-model="createForm.allergies" placeholder="花生、海鲜…" /></el-form-item>
        <el-form-item v-if="createForm.role === 'CHILD'" label="监护人">
          <el-select v-model="createForm.guardianMemberId" clearable placeholder="默认当前管理员">
            <el-option v-for="m in adultMembers" :key="m.id" :label="m.displayName" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="忌口"><el-input v-model="createForm.dietaryNotes" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDlg = false">取消</el-button>
        <el-button type="primary" @click="create">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="editDlg" title="编辑成员" width="440px">
      <el-form :model="editForm" label-width="80px">
        <el-form-item label="称呼"><el-input v-model="editForm.displayName" /></el-form-item>
        <el-form-item label="过敏"><el-input v-model="editForm.allergies" /></el-form-item>
        <el-form-item v-if="editForm.role === 'CHILD'" label="监护人">
          <el-select v-model="editForm.guardianMemberId" placeholder="选择监护人">
            <el-option v-for="m in adultMembers.filter((x) => x.id !== editForm.id)" :key="m.id" :label="m.displayName" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="忌口"><el-input v-model="editForm.dietaryNotes" /></el-form-item>
        <el-form-item label="重设密码"><el-input v-model="editForm.password" type="password" show-password placeholder="不改请留空，至少 6 位" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDlg = false">取消</el-button>
        <el-button type="primary" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, ROLE_LABELS } from '../api'

const members = ref([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    members.value = await api.members()
  } finally {
    loading.value = false
  }
}

async function changeRole(row, role) {
  try {
    await api.updateMember(row.id, { role })
    ElMessage.success('角色已更新')
  } catch {
    load()
  }
}

const createDlg = ref(false)
const adultMembers = computed(() => members.value.filter((m) => m.role !== 'CHILD'))
const createForm = reactive({ displayName: '', role: 'MEMBER', username: '', password: '', allergies: '', dietaryNotes: '', guardianMemberId: null })
function openCreate() {
  Object.assign(createForm, { displayName: '', role: 'MEMBER', username: '', password: '', allergies: '', dietaryNotes: '', guardianMemberId: null })
  createDlg.value = true
}
async function create() {
  await api.createMember({ ...createForm })
  ElMessage.success('已创建')
  createDlg.value = false
  load()
}

const editDlg = ref(false)
const editForm = reactive({ id: null, displayName: '', role: 'MEMBER', allergies: '', dietaryNotes: '', guardianMemberId: null, password: '' })
function openEdit(row) {
  Object.assign(editForm, { id: row.id, displayName: row.displayName, role: row.role, allergies: row.allergies || '', dietaryNotes: row.dietaryNotes || '', guardianMemberId: row.guardianMemberId || null, password: '' })
  editDlg.value = true
}
async function saveEdit() {
  const { id, password, ...rest } = editForm
  const body = { ...rest }
  if (!password) delete body.password
  else body.password = password
  if (body.role !== 'CHILD') delete body.guardianMemberId
  await api.updateMember(id, body)
  ElMessage.success('已保存')
  editDlg.value = false
  load()
}

async function remove(row) {
  await ElMessageBox.confirm(`确定把「${row.displayName}」移出家庭？他的点餐记录会一起删除。`, '移除成员', { type: 'warning' })
  await api.removeMember(row.id)
  load()
}

onMounted(load)
</script>
