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
      <el-divider />
      <h3>数字人形象管理</h3>

      <el-form-item label="形象名称">
        <el-input v-model="avatarName" placeholder="例如：知性女助理" style="width: 260px" />
      </el-form-item>
      <el-form-item label="选择图片">
        <el-upload
          :auto-upload="false"
          :show-file-list="false"
          :on-change="onImageSelected"
          accept="image/png,image/jpeg"
        >
          <el-button type="primary" :disabled="avatarStore.uploading">
            选择二次元图片
          </el-button>
        </el-upload>
      </el-form-item>
      <el-form-item v-if="selectedImage" label="框选面部">
        <ImageCropper
          :imageFile="selectedImage"
          @crop-change="onCropChange"
        />
      </el-form-item>
      <el-form-item v-if="cropData">
        <el-button
          type="success"
          @click="generateAvatar"
          :loading="avatarStore.uploading"
          :disabled="!avatarName.trim()"
        >
          开始生成形象
        </el-button>
      </el-form-item>

      <el-divider />
      <h4>形象库（共 {{ avatarStore.avatars.length }} 个）</h4>
      <el-form-item>
        <div class="avatar-gallery">
          <div
            v-for="av in avatarStore.avatars"
            :key="av.id"
            class="avatar-card"
          >
            <img :src="av.thumbnailPath" class="avatar-thumb" />
            <div class="avatar-name">{{ av.name }}</div>
            <el-button
              size="small"
              type="danger"
              @click="deleteAvatar(av.id)"
              :disabled="av.id === avatarStore.defaultId"
            >
              删除
            </el-button>
          </div>
          <div v-if="avatarStore.avatars.length === 0" class="no-avatar">
            暂无形象，请上传生成
          </div>
        </div>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import axios from 'axios'
import ImageCropper from './ImageCropper.vue'
import { useAvatarStore } from '../stores/avatar'
import { ElMessage } from 'element-plus'

const prompt = ref('')
const savingPrompt = ref(false)
const ttsConfig = ref({ voice_id: '', speed: '1.0', pitch: '0' })
const savingTts = ref(false)
const modelPath = ref('')
const savingModel = ref(false)
const api = axios.create({ baseURL: '/api/admin' })

const avatarStore = useAvatarStore()
const avatarName = ref('')
const selectedImage = ref(null)
const cropData = ref(null)

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
  avatarStore.loadAdminAvatars()
})

function onImageSelected(uploadFile) {
  selectedImage.value = uploadFile.raw
  cropData.value = null
}

function onCropChange(data) {
  cropData.value = data
}

async function generateAvatar() {
  if (!cropData.value || !avatarName.value.trim()) return
  try {
    await avatarStore.uploadAvatar(
      selectedImage.value,
      avatarName.value.trim(),
      cropData.value.x,
      cropData.value.y,
      cropData.value.width,
      cropData.value.height
    )
    selectedImage.value = null
    cropData.value = null
    avatarName.value = ''
    ElMessage.success('形象生成成功！')
  } catch (e) {
    ElMessage.error('生成失败: ' + (e.message || '未知错误'))
  }
}

async function deleteAvatar(id) {
  try {
    await avatarStore.deleteAvatar(id)
    ElMessage.success('已删除')
  } catch (e) {
    ElMessage.error('删除失败')
  }
}

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
.avatar-gallery {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
}
.avatar-card {
  width: 140px;
  text-align: center;
  border: 1px solid #eee;
  border-radius: 8px;
  padding: 8px;
}
.avatar-thumb {
  width: 120px;
  height: 120px;
  object-fit: cover;
  border-radius: 4px;
  background: #f5f5f5;
}
.avatar-name {
  font-size: 13px;
  margin: 6px 0;
  color: #333;
}
.no-avatar {
  color: #999;
  font-size: 14px;
}
</style>
