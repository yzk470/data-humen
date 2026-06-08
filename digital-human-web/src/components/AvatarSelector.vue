<template>
  <div class="avatar-selector">
    <h3>数字人形象</h3>
    <el-radio-group v-model="selectedModel" @change="onSelect">
      <el-radio-button v-for="model in models" :key="model.path" :label="model.path">
        {{ model.name }}
      </el-radio-button>
    </el-radio-group>
    <div class="preview">当前选择：{{ selectedModel }}</div>
  </div>
</template>

<script setup>
import { ref } from 'vue'

defineProps({
  models: {
    type: Array,
    default: () => [
      { name: '默认形象', path: '/models/default.model3.json' },
      { name: '形象 A', path: '/models/avatar_a.model3.json' },
      { name: '形象 B', path: '/models/avatar_b.model3.json' }
    ]
  }
})

const emit = defineEmits(['select'])
const selectedModel = ref('/models/default.model3.json')

function onSelect(path) {
  emit('select', path)
}
</script>

<style scoped>
.avatar-selector { padding: 16px; }
.preview { margin-top: 12px; color: #999; }
</style>
