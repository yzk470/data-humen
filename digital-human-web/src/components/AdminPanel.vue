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
      <h3>音色管理</h3>
      <div v-for="(item, index) in preferenceConfig.voiceOptions" :key="`voice-${index}`" class="option-row">
        <el-input v-model="item.label" placeholder="显示名称" style="width: 180px" />
        <el-input v-model="item.value" placeholder="voice id" style="width: 240px" />
        <el-button type="danger" @click="removeVoice(index)">删除</el-button>
      </div>
      <el-form-item>
        <el-button @click="addVoice">新增音色</el-button>
      </el-form-item>
      <el-form-item label="默认音色">
        <el-select v-model="preferenceConfig.defaultVoiceId" style="width: 300px">
          <el-option v-for="item in preferenceConfig.voiceOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="savePreferenceConfig" :loading="savingPreferences">保存偏好配置</el-button>
      </el-form-item>

      <el-divider />
      <h3>形象管理</h3>
      <div v-for="(item, index) in preferenceConfig.modelOptions" :key="`model-${index}`" class="option-row">
        <el-select v-model="item.value" style="width: 320px">
          <el-option v-for="avatar in avatarStore.avatars" :key="avatar.modelPath" :label="avatar.name" :value="avatar.modelPath" />
        </el-select>
        <el-input v-model="item.label" placeholder="显示名称" style="width: 180px" />
        <el-button type="danger" @click="removeModel(index)">删除</el-button>
      </div>
      <el-form-item>
        <el-button @click="addModel">新增形象</el-button>
      </el-form-item>
      <el-form-item label="默认形象">
        <el-select v-model="preferenceConfig.defaultModelPath" style="width: 300px">
          <el-option v-for="item in preferenceConfig.modelOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>

      <el-divider />
      <el-form-item label="TTS 音色 ID (旧)">
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
      <el-form-item label="Live2D 模型路径 (旧)">
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
      <el-form-item v-if="selectedImage" label="AI 辅助">
        <el-button
          type="warning"
          @click="aiDetectFace"
          :loading="aiDetecting"
          :disabled="!selectedImage"
        >
          🤖 AI 识别人脸
        </el-button>
        <span v-if="aiDetected" style="color: #67C23A; margin-left: 8px;">✓ 已识别，可微调</span>
      </el-form-item>
      <el-form-item v-if="selectedImage" label="框选面部">
        <ImageCropper
          :imageFile="selectedImage"
          :presetCrop="presetCrop"
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
              :disabled="avatarStore.avatars.length <= 1"
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
import ImageCropper from './ImageCropper.vue'
import { useAvatarStore } from '../stores/avatar'
import { adminApi, detectFace } from '../services/api'
import { getAdminPreferences, updateAdminPreferences } from '../services/preferences'
import { ElMessage } from 'element-plus'

const prompt = ref('')
const savingPrompt = ref(false)
const ttsConfig = ref({ voice_id: '', speed: '1.0', pitch: '0' })
const savingTts = ref(false)
const modelPath = ref('')
const savingModel = ref(false)
const savingPreferences = ref(false)

const avatarStore = useAvatarStore()
const avatarName = ref('')
const selectedImage = ref(null)
const cropData = ref(null)
const aiDetecting = ref(false)
const aiDetected = ref(false)
const presetCrop = ref(null)

const preferenceConfig = ref({
  voiceOptions: [],
  modelOptions: [],
  defaultVoiceId: '',
  defaultModelPath: ''
})

onMounted(async () => {
  try {
    const [promptRes, ttsRes, modelRes] = await Promise.all([
      adminApi.get('/config/prompt'),
      adminApi.get('/config/tts-voice'),
      adminApi.get('/config/model')
    ])
    prompt.value = promptRes.data.data.system_prompt || ''
    ttsConfig.value = {
      voice_id: ttsRes.data.data.voice_id || '',
      speed: parseFloat(ttsRes.data.data.speed) || 1.0,
      pitch: parseInt(ttsRes.data.data.pitch) || 0
    }
    modelPath.value = modelRes.data.data.live2d_model_path || ''
  } catch (e) { /* ignore */ }
  await avatarStore.loadAdminAvatars()
  await loadPreferenceConfig()
})

async function loadPreferenceConfig() {
  try {
    const { data } = await getAdminPreferences()
    if (data.code === 200) {
      preferenceConfig.value = data.data
    }
  } catch (e) { /* ignore */ }
}

function addVoice() {
  preferenceConfig.value.voiceOptions.push({ label: '', value: '' })
}

function removeVoice(index) {
  preferenceConfig.value.voiceOptions.splice(index, 1)
}

function addModel() {
  const first = avatarStore.avatars[0]
  if (!first) return
  preferenceConfig.value.modelOptions.push({ label: first.name, value: first.modelPath })
}

function removeModel(index) {
  preferenceConfig.value.modelOptions.splice(index, 1)
}

async function savePreferenceConfig() {
  savingPreferences.value = true
  try {
    const { data } = await updateAdminPreferences(preferenceConfig.value)
    if (data.code === 200) {
      ElMessage.success('偏好配置已保存')
    } else {
      ElMessage.error(data.message || '保存失败')
    }
  } catch (e) {
    ElMessage.error('保存偏好配置失败')
  } finally {
    savingPreferences.value = false
  }
}

function onImageSelected(uploadFile) {
  selectedImage.value = uploadFile.raw
  cropData.value = null
  aiDetected.value = false
  presetCrop.value = null
}

async function aiDetectFace() {
  if (!selectedImage.value) return
  aiDetecting.value = true
  aiDetected.value = false
  try {
    const { data } = await detectFace(selectedImage.value)
    if (data.code === 200) {
      presetCrop.value = data.data
      aiDetected.value = true
      ElMessage.success('AI 已识别面部区域，可手动微调')
    } else {
      ElMessage.warning(data.message || 'AI 识别失败')
    }
  } catch (e) {
    ElMessage.error('AI 识别失败，请手动框选')
  } finally {
    aiDetecting.value = false
  }
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
  await adminApi.put('/config/prompt', { system_prompt: prompt.value })
  savingPrompt.value = false
}

async function saveTtsConfig() {
  savingTts.value = true
  await adminApi.put('/config/tts-voice', {
    voice_id: ttsConfig.value.voice_id,
    speed: String(ttsConfig.value.speed),
    pitch: String(ttsConfig.value.pitch)
  })
  savingTts.value = false
}

async function saveModelPath() {
  savingModel.value = true
  await adminApi.put('/config/model', { live2d_model_path: modelPath.value })
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
.option-row {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
  align-items: center;
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
