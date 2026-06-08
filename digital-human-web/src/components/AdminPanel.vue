<template>
  <div class="admin-panel">
    <h2>数字人配置管理</h2>
    <el-form label-width="120px">
      <el-form-item label="系统 Prompt">
        <el-input v-model="prompt" type="textarea" :rows="4" placeholder="输入系统 Prompt..." />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="savePrompt" :loading="savingPrompt">保存 Prompt</el-button>
      </el-form-item>
      <el-divider />
      <el-form-item label="TTS 音色 ID">
        <el-input v-model="ttsConfig.voice_id" placeholder="音色 ID" />
      </el-form-item>
      <el-form-item label="语速">
        <el-slider v-model="ttsConfig.speed" :min="0.5" :max="2.0" :step="0.1" show-input />
      </el-form-item>
      <el-form-item label="音调">
        <el-slider v-model="ttsConfig.pitch" :min="-20" :max="20" :step="1" show-input />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="saveTtsConfig" :loading="savingTts">保存 TTS 配置</el-button>
      </el-form-item>
      <el-divider />
      <el-form-item label="Live2D 模型路径">
        <el-input v-model="modelPath" placeholder="/models/default.model3.json" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="saveModelPath" :loading="savingModel">保存模型配置</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import axios from 'axios'

const prompt = ref('')
const savingPrompt = ref(false)
const ttsConfig = ref({ voice_id: '', speed: '1.0', pitch: '0' })
const savingTts = ref(false)
const modelPath = ref('')
const savingModel = ref(false)
const api = axios.create({ baseURL: '/api/admin' })

onMounted(async () => {
  try {
    const [promptRes, ttsRes, modelRes] = await Promise.all([
      api.get('/config/prompt'),
      api.get('/config/tts-voice'),
      api.get('/config/model')
    ])
    prompt.value = promptRes.data.data.system_prompt || ''
    ttsConfig.value = {
      voice_id: ttsRes.data.data.voice_id || '',
      speed: parseFloat(ttsRes.data.data.speed) || 1.0,
      pitch: parseInt(ttsRes.data.data.pitch) || 0
    }
    modelPath.value = modelRes.data.data.live2d_model_path || ''
  } catch (e) { /* ignore */ }
})

async function savePrompt() {
  savingPrompt.value = true
  await api.put('/config/prompt', { system_prompt: prompt.value })
  savingPrompt.value = false
}

async function saveTtsConfig() {
  savingTts.value = true
  await api.put('/config/tts-voice', {
    voice_id: ttsConfig.value.voice_id,
    speed: String(ttsConfig.value.speed),
    pitch: String(ttsConfig.value.pitch)
  })
  savingTts.value = false
}

async function saveModelPath() {
  savingModel.value = true
  await api.put('/config/model', { live2d_model_path: modelPath.value })
  savingModel.value = false
}
</script>

<style scoped>
.admin-panel {
  max-width: 800px;
  margin: 40px auto;
  padding: 24px;
  background: white;
  border-radius: 12px;
}
</style>
