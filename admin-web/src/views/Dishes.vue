<template>
  <div class="page">
    <div class="page-head">
      <h2>菜谱管理</h2>
      <div class="row-gap">
        <el-input v-model="keyword" placeholder="搜索菜名" clearable style="width: 180px" @change="load" />
        <el-select v-model="category" placeholder="全部分类" clearable style="width: 140px" @change="load">
          <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
        </el-select>
        <el-button type="primary" @click="openEditor()">新增菜品</el-button>
      </div>
    </div>
    <el-table :data="dishes" v-loading="loading" stripe>
      <el-table-column label="封面" width="80">
        <template #default="{ row }">
          <el-image v-if="row.coverUrl" :src="mediaSrc(row.coverUrl)" fit="cover" style="width: 48px; height: 48px; border-radius: 4px" />
        </template>
      </el-table-column>
      <el-table-column prop="name" label="菜名" min-width="140" />
      <el-table-column prop="category" label="分类" width="110" />
      <el-table-column label="难度" width="80"><template #default="{ row }">{{ DIFFICULTY_LABELS[row.difficulty] }}</template></el-table-column>
      <el-table-column label="用时" width="90"><template #default="{ row }">{{ row.durationMinutes }} 分钟</template></el-table-column>
      <el-table-column label="默认份数" width="90"><template #default="{ row }">{{ row.defaultServings }} 人份</template></el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{ row }"><el-tag :type="row.active ? 'success' : 'info'" size="small">{{ row.active ? '上架' : '已下架' }}</el-tag></template>
      </el-table-column>
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEditor(row.id)">编辑</el-button>
          <el-button v-if="row.active" link type="danger" @click="toggle(row)">下架</el-button>
          <el-button v-else link @click="toggle(row)">恢复</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-drawer v-model="editor" :title="form.id ? '编辑菜品' : '新增菜品'" size="760px" :close-on-click-modal="false">
      <el-form :model="form" label-width="90px" v-loading="formLoading">
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="菜名" required><el-input v-model="form.name" maxlength="64" /></el-form-item></el-col>
          <el-col :span="12">
            <el-form-item label="分类" required>
              <el-select v-model="form.category" filterable allow-create default-first-option style="width: 100%">
                <el-option v-for="c in categoryOptions" :key="c" :label="c" :value="c" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="难度">
              <el-select v-model="form.difficulty"><el-option v-for="(l, k) in DIFFICULTY_LABELS" :key="k" :label="l" :value="k" /></el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8"><el-form-item label="用时(分)"><el-input-number v-model="form.durationMinutes" :min="1" :max="1440" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="默认份数"><el-input-number v-model="form.defaultServings" :min="1" :max="100" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="封面">
          <div class="row-gap" style="align-items: flex-start">
            <el-image v-if="form.coverUrl" :src="mediaSrc(form.coverUrl)" fit="cover" style="width: 72px; height: 72px; border-radius: 6px" />
            <el-upload :show-file-list="false" accept="image/jpeg,image/png,image/webp,image/gif" :http-request="(opt) => uploadTo('cover', opt)">
              <el-button>上传图片</el-button>
            </el-upload>
            <el-input v-model="form.coverUrl" placeholder="或粘贴外链（可选）" style="flex: 1" />
          </div>
        </el-form-item>
        <el-form-item label="简介"><el-input v-model="form.description" type="textarea" :rows="2" maxlength="500" /></el-form-item>
        <el-form-item label="小贴士"><el-input v-model="form.tips" type="textarea" :rows="2" maxlength="1000" placeholder="比如：猪脚要提前焯水" /></el-form-item>

        <el-divider content-position="left">主料</el-divider>
        <ingredient-rows v-model="form.ingredients" />
        <el-divider content-position="left">配料与调味</el-divider>
        <ingredient-rows v-model="form.seasonings" />

        <el-divider content-position="left">做法步骤</el-divider>
        <div v-for="(s, i) in form.steps" :key="i" class="step-block">
          <div class="step-row">
            <span class="step-no">{{ i + 1 }}</span>
            <el-input v-model="s.content" type="textarea" :rows="2" placeholder="关键节点：腌多久、火候、几分钟" />
            <el-input-number v-model="s.durationMinutes" :min="0" :max="1440" placeholder="分钟" controls-position="right" style="width: 110px" />
            <el-button link type="danger" @click="form.steps.splice(i, 1)">删除</el-button>
          </div>
          <div class="row-gap" style="margin: 6px 0 12px 30px; flex-wrap: wrap">
            <el-upload :show-file-list="false" accept="image/jpeg,image/png,image/webp,image/gif" :http-request="(opt) => uploadTo('stepImage', opt, i)">
              <el-button size="small">步骤图</el-button>
            </el-upload>
            <el-upload :show-file-list="false" accept="video/mp4,video/webm" :http-request="(opt) => uploadTo('stepVideo', opt, i)">
              <el-button size="small">短视频</el-button>
            </el-upload>
            <el-image v-if="s.imageUrl" :src="mediaSrc(s.imageUrl)" fit="cover" style="width: 80px; height: 60px; border-radius: 4px" />
            <video v-if="s.videoUrl" :src="mediaSrc(s.videoUrl)" :poster="mediaSrc(s.videoCoverUrl)" style="width: 160px; height: 90px" controls />
          </div>
        </div>
        <el-button size="small" @click="form.steps.push({ content: '', durationMinutes: null, imageUrl: '', videoUrl: '', videoCoverUrl: '', videoDurationSeconds: null })">+ 添加步骤</el-button>

        <el-divider content-position="left">营养估算（可选手工覆盖）</el-divider>
        <p class="muted" style="margin: 0 0 8px">默认按用料估算，是约数不是检测或官方认证。开启覆盖后，以你填的整道菜（默认份数）为准。</p>
        <el-form-item label="手工覆盖">
          <el-switch v-model="form.nutritionEnabled" />
        </el-form-item>
        <el-row v-if="form.nutritionEnabled" :gutter="12">
          <el-col :span="6"><el-form-item label="热量(kcal)"><el-input-number v-model="form.nutritionKcal" :min="0" :precision="1" controls-position="right" /></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="蛋白质(g)"><el-input-number v-model="form.nutritionProtein" :min="0" :precision="1" controls-position="right" /></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="脂肪(g)"><el-input-number v-model="form.nutritionFat" :min="0" :precision="1" controls-position="right" /></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="碳水(g)"><el-input-number v-model="form.nutritionCarb" :min="0" :precision="1" controls-position="right" /></el-form-item></el-col>
        </el-row>
        <p v-if="nutritionHint" class="muted">{{ nutritionHint }}</p>
      </el-form>
      <template #footer>
        <el-button @click="editor = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, defineComponent, h, onMounted, ref } from 'vue'
import { ElButton, ElCheckbox, ElInput, ElInputNumber, ElMessage, ElMessageBox } from 'element-plus'
import { api, DIFFICULTY_LABELS, mediaSrc } from '../api'

const DEFAULT_CATEGORIES = ['硬菜', '家常菜', '素菜', '汤', '孩子爱吃', '主食', '早餐', '快手菜']

/** 食材行编辑器：名称 / 用量 / 单位 / 家里常备 / 备注 */
const IngredientRows = defineComponent({
  props: { modelValue: { type: Array, required: true } },
  setup(props) {
    return () => h('div', [
      ...props.modelValue.map((row, i) => h('div', { class: 'ing-row' }, [
        h(ElInput, { modelValue: row.name, 'onUpdate:modelValue': (v) => (row.name = v), placeholder: '名称', style: 'width: 150px' }),
        h(ElInputNumber, { modelValue: row.amount, 'onUpdate:modelValue': (v) => (row.amount = v), min: 0, precision: 2, controlsPosition: 'right', placeholder: '适量', style: 'width: 120px' }),
        h(ElInput, { modelValue: row.unit, 'onUpdate:modelValue': (v) => (row.unit = v), placeholder: '单位', style: 'width: 70px' }),
        h(ElCheckbox, { modelValue: row.staple, 'onUpdate:modelValue': (v) => (row.staple = v) }, () => '家里常备'),
        h(ElInput, { modelValue: row.note, 'onUpdate:modelValue': (v) => (row.note = v), placeholder: '备注', style: 'width: 140px' }),
        h(ElButton, { link: true, type: 'danger', onClick: () => props.modelValue.splice(i, 1) }, () => '删除'),
      ])),
      h(ElButton, { size: 'small', onClick: () => props.modelValue.push({ name: '', amount: null, unit: '', staple: false, note: '' }) }, () => '+ 添加'),
    ])
  },
})

const dishes = ref([])
const categories = ref([])
const keyword = ref('')
const category = ref('')
const loading = ref(false)
const categoryOptions = computed(() => [...new Set([...DEFAULT_CATEGORIES, ...categories.value])])

async function load() {
  loading.value = true
  try {
    const params = {}
    if (keyword.value) params.keyword = keyword.value
    if (category.value) params.category = category.value
    ;[dishes.value, categories.value] = await Promise.all([api.dishes(params), api.categories()])
  } finally {
    loading.value = false
  }
}

const editor = ref(false)
const formLoading = ref(false)
const saving = ref(false)
const empty = () => ({ id: null, name: '', category: '家常菜', difficulty: 'MEDIUM', durationMinutes: 30, defaultServings: 3, coverUrl: '', description: '', tips: '', ingredients: [], seasonings: [], steps: [], nutritionEnabled: false, nutritionKcal: null, nutritionProtein: null, nutritionFat: null, nutritionCarb: null })
const nutritionHint = ref('')
const form = ref(empty())

async function openEditor(id) {
  form.value = empty()
  editor.value = true
  if (!id) return
  formLoading.value = true
  try {
    const d = await api.dish(id)
    const strip = (list) => list.map(({ name, amount, unit, staple, note }) => ({ name, amount, unit: unit || '', staple, note: note || '' }))
    form.value = {
      id: d.id, name: d.name, category: d.category, difficulty: d.difficulty, durationMinutes: d.durationMinutes,
      defaultServings: d.defaultServings, coverUrl: d.coverUrl || '', description: d.description || '', tips: d.tips || '',
      ingredients: strip(d.ingredients), seasonings: strip(d.seasonings),
      steps: d.steps.map((s) => ({ content: s.content, durationMinutes: s.durationMinutes, imageUrl: s.imageUrl || '', videoUrl: s.videoUrl || '', videoCoverUrl: s.videoCoverUrl || '', videoDurationSeconds: s.videoDurationSeconds })),
      nutritionEnabled: !!d.nutritionOverride,
      nutritionKcal: d.nutritionOverride ? d.nutrition?.total?.kcal ?? null : null,
      nutritionProtein: d.nutritionOverride ? d.nutrition?.total?.protein ?? null : null,
      nutritionFat: d.nutritionOverride ? d.nutrition?.total?.fat ?? null : null,
      nutritionCarb: d.nutritionOverride ? d.nutrition?.total?.carb ?? null : null,
    }
    const n = d.nutrition
    nutritionHint.value = n
      ? `当前估算：整道约 ${Math.round(n.total?.kcal || 0)} 千卡` + (n.excluded?.length ? `；未计入：${n.excluded.join('；')}` : '')
      : ''
  } finally {
    formLoading.value = false
  }
}

async function save() {
  const f = form.value
  if (!f.name.trim() || !f.category) return ElMessage.warning('请填写菜名和分类')
  const clean = (list) => list.filter((i) => i.name && i.name.trim()).map((i) => ({ ...i, amount: i.amount ?? null }))
  const body = {
    ...f,
    ingredients: clean(f.ingredients),
    seasonings: clean(f.seasonings),
    steps: f.steps.filter((s) => s.content && s.content.trim()),
    nutrition: f.nutritionEnabled
      ? { enabled: true, kcal: f.nutritionKcal, protein: f.nutritionProtein, fat: f.nutritionFat, carb: f.nutritionCarb }
      : { enabled: false },
  }
  saving.value = true
  try {
    if (f.id) await api.updateDish(f.id, body)
    else await api.createDish(body)
    ElMessage.success('已保存')
    editor.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function toggle(row) {
  if (row.active) {
    await ElMessageBox.confirm(`下架「${row.name}」后家人就点不到了，历史记录会保留。`, '下架菜品', { type: 'warning' })
    await api.deactivateDish(row.id)
  } else {
    await api.restoreDish(row.id)
  }
  load()
}

async function uploadTo(target, opt, index) {
  try {
    const r = await api.uploadMedia(opt.file)
    if (target === 'cover') form.value.coverUrl = r.url
    else if (target === 'stepImage') form.value.steps[index].imageUrl = r.url
    else if (target === 'stepVideo') form.value.steps[index].videoUrl = r.url
    opt.onSuccess?.(r)
  } catch (e) {
    opt.onError?.(e)
  }
}

onMounted(load)
</script>

<style scoped>
.step-block { margin-bottom: 8px; }
.step-row { display: flex; align-items: flex-start; gap: 8px; margin-bottom: 8px; }
.muted { color: #888; font-size: 13px; }
.step-no { width: 22px; height: 22px; line-height: 22px; text-align: center; border-radius: 50%; background: #e6772e; color: #fff; font-size: 12px; margin-top: 6px; flex-shrink: 0; }
:deep(.ing-row) { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
</style>
