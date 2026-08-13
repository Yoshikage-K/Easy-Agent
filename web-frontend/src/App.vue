<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from "vue";
import {
  ChatMessage,
  CreateUploadPayload,
  DownloadUrlResponse,
  PluginResponse,
  SessionDetail,
  SessionSummary,
  UploadStatus,
  UploadTask,
  createDownloadUrl,
  createSession,
  createUpload,
  deleteSession,
  discoverPlugins,
  directDownloadUrl,
  getSession,
  getUploadStatus,
  listExtensions,
  listPlugins,
  listSessions,
  loadPlugin,
  pluginAction,
  sendMessage,
  uploadAction
} from "./api";

const sessions = ref<SessionSummary[]>([]);
const activeSession = ref<SessionDetail | null>(null);
const draft = ref("");
const isLoading = ref(false);
const error = ref("");
const messageList = ref<HTMLElement | null>(null);
const showConversation = ref(false);
const chatInitialized = ref(false);
const activeLandingPage = ref<"home" | "plugin" | "transfer">("home");
const plugins = ref<PluginResponse[]>([]);
const extensions = ref<string[]>([]);
const selectedPluginId = ref("");
const pluginPath = ref("");
const pluginLoading = ref(false);
const transferMode = ref<"import" | "export">("import");
const uploadForm = ref<CreateUploadPayload>({ sourceUrl: "", bucket: "", objectName: "", headers: {} });
const uploadTask = ref<UploadTask | null>(null);
const uploadStatus = ref<UploadStatus | null>(null);
const downloadBucket = ref("");
const downloadObject = ref("");
const downloadInfo = ref<DownloadUrlResponse | null>(null);
const transferBusy = ref(false);
const toolError = ref("");
let uploadStatusTimer: ReturnType<typeof setInterval> | undefined;

function fract(value: number) {
  return value - Math.floor(value);
}

function pixelNoise(column: number, row: number) {
  return fract(Math.sin(column * 12.9898 + row * 78.233) * 43758.5453);
}

const pixelColumns = 150;
const pixelRows = 80;
const pixelBlobs = [
  { x: 0.08, y: 0.18, radius: 0.12, weight: 0.9 },
  { x: 0.38, y: 0.13, radius: 0.17, weight: 0.72 },
  { x: 0.68, y: 0.2, radius: 0.21, weight: 0.72 },
  { x: 0.96, y: 0.34, radius: 0.1, weight: 0.82 },
  { x: 0.47, y: 0.47, radius: 0.18, weight: 1.02 },
  { x: 0.26, y: 0.76, radius: 0.13, weight: 0.72 },
  { x: 0.58, y: 0.77, radius: 0.16, weight: 0.74 },
  { x: 0.82, y: 0.9, radius: 0.13, weight: 0.46 }
];

type PixelBlock = {
  id: string;
  x: number;
  y: number;
  scale: number;
  hue: "white" | "cyan";
  opacity: number;
  age: number;
  enterDuration: number;
  holdDuration: number;
  leaveDuration: number;
  flowX: number;
  flowY: number;
};

function generateLightBlocks(seed: number): PixelBlock[] {
  const lightBlocks: PixelBlock[] = [];
  for (let row = 0; row < pixelRows; row += 1) {
    for (let column = 0; column < pixelColumns; column += 1) {
      const x = column / pixelColumns;
      const y = row / pixelRows;
      const noise = pixelNoise(column + seed * 17, row + seed * 29);
      const macroNoise = pixelNoise(Math.floor(column / 4) + seed * 11, Math.floor(row / 4) + seed * 7);
      const texture = (
        Math.sin(column * 0.47 + Math.sin(row * 0.21 + seed) * 2.1) +
        Math.cos(row * 0.31 - column * 0.13 + seed * 0.7) +
        Math.sin((column + row + seed * 5) * 0.17)
      ) / 3;
      const density = pixelBlobs.reduce((sum, blob, blobIndex) => {
        const shiftedX = fract(blob.x + Math.sin(seed * 0.71 + blobIndex * 2.4) * 0.2);
        const shiftedY = fract(blob.y + Math.cos(seed * 0.53 + blobIndex * 1.7) * 0.12);
        const distance = Math.hypot((x - shiftedX) / blob.radius, (y - shiftedY) / blob.radius);
        return sum + blob.weight * Math.exp(-(distance * distance));
      }, 0);

      const fill = density * (0.25 + macroNoise * 0.85) + texture * 0.14 + noise * 0.16;
      if (fill < 0.43) continue;

      lightBlocks.push({
        id: `${seed}-${column}-${row}`,
        x: x * 100,
        y: y * 100,
        scale: 0.82 + noise * 0.32,
        hue: noise > 0.84 ? "cyan" : "white",
        opacity: 0,
        age: noise * 3.2,
        enterDuration: 0.35 + noise * 0.3,
        holdDuration: 0.55 + noise * 1.1,
        leaveDuration: 0.3 + noise * 0.35,
        flowX: 0.035 + pixelNoise(column + seed * 3, row + 7) * 0.035,
        flowY: -0.012 + pixelNoise(column + 11, row + seed * 5) * 0.024
      });
    }
  }
  lightBlocks.forEach(updatePixelOpacity);
  return lightBlocks;
}

const lightBlocks = ref(generateLightBlocks(0));
let pixelTimer: ReturnType<typeof setInterval> | undefined;

function wrapUnit(value: number) {
  return ((value % 1) + 1) % 1;
}

function updatePixelOpacity(block: PixelBlock) {
  const fadeInEnd = block.enterDuration;
  const fadeOutStart = fadeInEnd + block.holdDuration;
  const cycleEnd = fadeOutStart + block.leaveDuration;

  if (block.age < fadeInEnd) {
    block.opacity = 0.08 + (block.age / fadeInEnd) * 0.58;
  } else if (block.age < fadeOutStart) {
    block.opacity = 0.58 + Math.sin((block.age - fadeInEnd) * 2.8) * 0.12;
  } else if (block.age < cycleEnd) {
    block.opacity = Math.max(0.02, 0.7 * (1 - (block.age - fadeOutStart) / block.leaveDuration));
  } else {
    block.opacity = 0.02;
  }
}

function advancePixelField() {
  const dt = 0.1;
  lightBlocks.value.forEach((block) => {
    block.age += dt;
    const cycleEnd = block.enterDuration + block.holdDuration + block.leaveDuration;
    if (block.age >= cycleEnd) {
      const jitterX = (Math.random() - 0.5) * 0.05;
      const jitterY = (Math.random() - 0.5) * 0.08;
      block.x = wrapUnit(block.x / 100 + block.flowX + jitterX) * 100;
      block.y = wrapUnit(block.y / 100 + block.flowY + jitterY) * 100;
      block.age = 0;
      block.enterDuration = 0.22 + Math.random() * 0.24;
      block.holdDuration = 0.55 + Math.random() * 1.1;
      block.leaveDuration = 0.28 + Math.random() * 0.4;
      block.flowX = 0.03 + Math.random() * 0.04;
      block.flowY = -0.018 + Math.random() * 0.036;
      block.hue = Math.random() > 0.84 ? "cyan" : "white";
    }
    updatePixelOpacity(block);
  });
}

const activeId = computed(() => activeSession.value?.id || "");
const canSend = computed(() => draft.value.trim().length > 0 && !isLoading.value);
const selectedPlugin = computed(() => plugins.value.find((plugin) => plugin.pluginId === selectedPluginId.value) || null);
const completedParts = computed(() => uploadStatus.value?.parts.filter((part) => part.status === "COMPLETED").length || 0);
const uploadProgress = computed(() => {
  const partCount = uploadStatus.value?.task.partCount || uploadTask.value?.partCount || 0;
  return partCount ? Math.round((completedParts.value / partCount) * 100) : 0;
});

function formatBytes(bytes: number) {
  if (!bytes) return "0 B";
  const units = ["B", "KB", "MB", "GB", "TB"];
  const unitIndex = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
  return `${(bytes / 1024 ** unitIndex).toFixed(unitIndex ? 1 : 0)} ${units[unitIndex]}`;
}

function clearToolError() {
  toolError.value = "";
}

async function loadPluginWorkspace() {
  pluginLoading.value = true;
  clearToolError();
  try {
    const [pluginData, extensionData] = await Promise.all([listPlugins(), listExtensions()]);
    plugins.value = pluginData.plugins;
    extensions.value = extensionData.extensions;
    if (!selectedPluginId.value && plugins.value.length) selectedPluginId.value = plugins.value[0].pluginId;
  } catch (err) {
    toolError.value = err instanceof Error ? err.message : String(err);
  } finally {
    pluginLoading.value = false;
  }
}

async function discoverPluginWorkspace() {
  pluginLoading.value = true;
  clearToolError();
  try {
    const data = await discoverPlugins();
    plugins.value = data.plugins;
    if (plugins.value.length) selectedPluginId.value = plugins.value[0].pluginId;
  } catch (err) {
    toolError.value = err instanceof Error ? err.message : String(err);
  } finally {
    pluginLoading.value = false;
  }
}

async function submitPluginLoad() {
  if (!pluginPath.value.trim()) return;
  pluginLoading.value = true;
  clearToolError();
  try {
    const plugin = await loadPlugin(pluginPath.value.trim());
    plugins.value = [...plugins.value.filter((item) => item.pluginId !== plugin.pluginId), plugin];
    selectedPluginId.value = plugin.pluginId;
    pluginPath.value = "";
  } catch (err) {
    toolError.value = err instanceof Error ? err.message : String(err);
  } finally {
    pluginLoading.value = false;
  }
}

async function runPluginAction(action: "start" | "stop" | "reload" | "unload") {
  if (!selectedPluginId.value) return;
  pluginLoading.value = true;
  clearToolError();
  try {
    const plugin = await pluginAction(selectedPluginId.value, action);
    if (action === "unload") {
      plugins.value = plugins.value.filter((item) => item.pluginId !== plugin.pluginId);
      selectedPluginId.value = plugins.value[0]?.pluginId || "";
    } else {
      plugins.value = plugins.value.map((item) => item.pluginId === plugin.pluginId ? plugin : item);
    }
  } catch (err) {
    toolError.value = err instanceof Error ? err.message : String(err);
  } finally {
    pluginLoading.value = false;
  }
}

async function submitUpload() {
  if (!uploadForm.value.sourceUrl || !uploadForm.value.bucket || !uploadForm.value.objectName) return;
  transferBusy.value = true;
  clearToolError();
  try {
    uploadTask.value = await createUpload(uploadForm.value);
    uploadStatus.value = null;
    startUploadPolling();
  } catch (err) {
    toolError.value = err instanceof Error ? err.message : String(err);
  } finally {
    transferBusy.value = false;
  }
}

async function runUploadAction(action: "start" | "pause" | "resume" | "retry" | "cancel") {
  if (!uploadTask.value) return;
  transferBusy.value = true;
  clearToolError();
  try {
    const task = await uploadAction(uploadTask.value.taskId, action);
    uploadTask.value = task;
    if (action === "cancel") {
      uploadStatus.value = null;
      stopUploadPolling();
    } else {
      startUploadPolling();
    }
  } catch (err) {
    toolError.value = err instanceof Error ? err.message : String(err);
  } finally {
    transferBusy.value = false;
  }
}

async function refreshUploadStatus() {
  if (!uploadTask.value) return;
  try {
    uploadStatus.value = await getUploadStatus(uploadTask.value.taskId);
    uploadTask.value = uploadStatus.value.task;
    if (["COMPLETED", "CANCELLED", "FAILED"].includes(uploadStatus.value.task.status)) stopUploadPolling();
  } catch (err) {
    toolError.value = err instanceof Error ? err.message : String(err);
  }
}

function startUploadPolling() {
  stopUploadPolling();
  uploadStatusTimer = setInterval(refreshUploadStatus, 1000);
  void refreshUploadStatus();
}

function stopUploadPolling() {
  if (uploadStatusTimer) clearInterval(uploadStatusTimer);
  uploadStatusTimer = undefined;
}

async function prepareDownload() {
  if (!downloadBucket.value || !downloadObject.value) return;
  transferBusy.value = true;
  clearToolError();
  try {
    downloadInfo.value = await createDownloadUrl(downloadBucket.value, downloadObject.value);
  } catch (err) {
    toolError.value = err instanceof Error ? err.message : String(err);
  } finally {
    transferBusy.value = false;
  }
}

async function refreshSessions() {
  const data = await listSessions();
  sessions.value = data.sessions;
}

async function selectSession(id: string) {
  error.value = "";
  const data = await getSession(id);
  activeSession.value = data.session;
  await scrollToBottom();
}

async function newSession() {
  error.value = "";
  const data = await createSession();
  const sessionData = await getSession(data.sessionId);
  activeSession.value = sessionData.session;
  await refreshSessions();
  await scrollToBottom();
}

async function removeSession(id: string) {
  error.value = "";
  await deleteSession(id);
  if (activeId.value === id) {
    activeSession.value = null;
  }
  await refreshSessions();
  if (!activeSession.value && sessions.value.length) {
    await selectSession(sessions.value[0].id);
  }
}

async function submit() {
  if (!canSend.value) return;
  if (!activeSession.value) {
    await newSession();
  }
  const session = activeSession.value;
  if (!session) return;
  const content = draft.value.trim();
  draft.value = "";
  error.value = "";
  isLoading.value = true;
  session.messages.push({ id: `local_${Date.now()}`, role: "user", content });
  await scrollToBottom();
  try {
    const data = await sendMessage(session.id, content);
    if (!data.success) {
      throw new Error(data.errorMessage || "Agent request failed");
    }
    const sessionData = await getSession(session.id);
    activeSession.value = sessionData.session;
    await refreshSessions();
  } catch (err) {
    error.value = err instanceof Error ? err.message : String(err);
  } finally {
    isLoading.value = false;
    await scrollToBottom();
  }
}

async function enterConversation() {
  activeLandingPage.value = "home";
  showConversation.value = true;
  if (chatInitialized.value) return;

  chatInitialized.value = true;
  try {
    await refreshSessions();
    if (sessions.value.length) {
      await selectSession(sessions.value[0].id);
    } else {
      await newSession();
    }
  } catch (err) {
    error.value = err instanceof Error ? err.message : String(err);
  }
}

function exitConversation() {
  showConversation.value = false;
  activeLandingPage.value = "home";
}

function openLandingPage(page: "home" | "plugin" | "transfer") {
  activeLandingPage.value = page;
  showConversation.value = false;
  clearToolError();
  if (page === "plugin") void loadPluginWorkspace();
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === "Enter" && !event.shiftKey) {
    event.preventDefault();
    submit();
  }
}

async function scrollToBottom() {
  await nextTick();
  if (messageList.value) {
    messageList.value.scrollTop = messageList.value.scrollHeight;
  }
}

function messageClass(message: ChatMessage) {
  return ["message", message.role === "user" ? "message-user" : "message-assistant"];
}

onMounted(() => {
  pixelTimer = setInterval(advancePixelField, 100);
});

onBeforeUnmount(() => {
  if (pixelTimer) clearInterval(pixelTimer);
  stopUploadPolling();
});

</script>

<template>
  <div v-if="!showConversation" class="landing-shell">
    <div v-if="activeLandingPage === 'home'" class="landing-stage">
      <div class="landing-noise" aria-hidden="true"></div>
      <div class="signal-field" aria-hidden="true">
        <span
          v-for="block in lightBlocks"
          :key="block.id"
          class="signal-block"
          :class="`signal-${block.hue}`"
          :style="{
            left: `${block.x}%`,
            top: `${block.y}%`,
            opacity: block.opacity,
            '--pixel-scale': block.scale
          }"
        />
      </div>
    </div>

    <section v-else-if="activeLandingPage === 'plugin'" class="workspace-page">
      <header class="tool-header">
        <div>
          <div class="tool-page-kicker">EA-Agent / Workspace</div>
          <h1>Plugin</h1>
          <p>Discover, load and control the extensions connected to your agent.</p>
        </div>
        <div class="tool-header-actions">
          <button class="subtle-button" type="button" :disabled="pluginLoading" @click="discoverPluginWorkspace">Discover</button>
          <button class="subtle-button" type="button" @click="openLandingPage('home')">Back</button>
        </div>
      </header>

      <div class="plugin-layout">
        <section class="workspace-panel plugin-list-panel">
          <div class="panel-heading">
            <span>Installed plugins</span>
            <span class="panel-count">{{ plugins.length }}</span>
          </div>
          <div v-if="pluginLoading && !plugins.length" class="panel-empty">Loading plugin registry…</div>
          <button
            v-for="plugin in plugins"
            :key="plugin.pluginId"
            class="plugin-list-item"
            :class="{ selected: plugin.pluginId === selectedPluginId }"
            type="button"
            @click="selectedPluginId = plugin.pluginId"
          >
            <span class="plugin-status-dot" :class="`status-${plugin.state.toLowerCase()}`"></span>
            <span class="plugin-list-copy">
              <strong>{{ plugin.pluginId }}</strong>
              <small>v{{ plugin.version }} · {{ plugin.state }}</small>
            </span>
            <span class="item-arrow">↗</span>
          </button>
          <div v-if="!pluginLoading && !plugins.length" class="panel-empty">No plugins discovered yet.</div>
          <div class="load-plugin-form">
            <input v-model="pluginPath" type="text" placeholder="/path/to/plugin.jar" />
            <button class="accent-button" type="button" :disabled="pluginLoading || !pluginPath.trim()" @click="submitPluginLoad">Load</button>
          </div>
        </section>

        <section class="workspace-panel plugin-detail-panel">
          <div class="panel-heading">Plugin detail</div>
          <div v-if="selectedPlugin" class="plugin-detail-content">
            <div class="detail-title-row">
              <div>
                <h2>{{ selectedPlugin.pluginId }}</h2>
                <p>{{ selectedPlugin.description || 'No description provided.' }}</p>
              </div>
              <span class="state-pill">{{ selectedPlugin.state }}</span>
            </div>
            <dl class="detail-grid">
              <div><dt>Version</dt><dd>{{ selectedPlugin.version }}</dd></div>
              <div><dt>Class</dt><dd>{{ selectedPlugin.pluginClass || '—' }}</dd></div>
              <div class="detail-wide"><dt>Path</dt><dd>{{ selectedPlugin.path }}</dd></div>
            </dl>
            <div class="detail-actions">
              <button class="accent-button" type="button" :disabled="pluginLoading" @click="runPluginAction('start')">Start</button>
              <button class="subtle-button" type="button" :disabled="pluginLoading" @click="runPluginAction('stop')">Stop</button>
              <button class="subtle-button" type="button" :disabled="pluginLoading" @click="runPluginAction('reload')">Reload</button>
              <button class="danger-button" type="button" :disabled="pluginLoading" @click="runPluginAction('unload')">Unload</button>
            </div>
          </div>
          <div v-else class="panel-empty">Select a plugin to inspect its runtime state.</div>
          <div class="extension-strip">
            <div class="panel-heading"><span>Extensions</span><span class="panel-count">{{ extensions.length }}</span></div>
            <div v-if="extensions.length" class="extension-list"><span v-for="extension in extensions" :key="extension">{{ extension }}</span></div>
            <div v-else class="muted-note">No extensions exposed.</div>
          </div>
        </section>
      </div>
      <div v-if="toolError" class="tool-error">{{ toolError }}</div>
    </section>

    <section v-else class="workspace-page transfer-page">
      <header class="tool-header">
        <div>
          <div class="tool-page-kicker">EA-Agent / Workspace</div>
          <h1>Transfer</h1>
          <p>Move remote files into storage or stream stored objects back out.</p>
        </div>
        <button class="subtle-button" type="button" @click="openLandingPage('home')">Back</button>
      </header>

      <div class="transfer-tabs">
        <button :class="{ active: transferMode === 'import' }" type="button" @click="transferMode = 'import'">Import to storage</button>
        <button :class="{ active: transferMode === 'export' }" type="button" @click="transferMode = 'export'">Export from storage</button>
      </div>

      <div class="transfer-layout">
        <section v-if="transferMode === 'import'" class="workspace-panel transfer-form-panel">
          <div class="panel-heading">Remote source</div>
          <label class="field-label">Source URL<input v-model="uploadForm.sourceUrl" type="url" placeholder="https://example.com/file.zip" /></label>
          <div class="field-row">
            <label class="field-label">Bucket<input v-model="uploadForm.bucket" type="text" placeholder="ea-artifacts" /></label>
            <label class="field-label">Object name<input v-model="uploadForm.objectName" type="text" placeholder="releases/file.zip" /></label>
          </div>
          <button class="accent-button wide-button" type="button" :disabled="transferBusy" @click="submitUpload">Create transfer task</button>
          <div class="transfer-hint">The backend imports a remote URL into MinIO using ranged multipart transfer.</div>
        </section>
        <section v-else class="workspace-panel transfer-form-panel">
          <div class="panel-heading">Stored object</div>
          <label class="field-label">Bucket<input v-model="downloadBucket" type="text" placeholder="ea-artifacts" /></label>
          <label class="field-label">Object name<input v-model="downloadObject" type="text" placeholder="releases/file.zip" /></label>
          <button class="accent-button wide-button" type="button" :disabled="transferBusy" @click="prepareDownload">Prepare download</button>
          <a v-if="downloadInfo" class="download-result" :href="downloadInfo.downloadUrl || directDownloadUrl(downloadBucket, downloadObject)" target="_blank" rel="noreferrer">
            <span>{{ downloadInfo.fileName }}</span><small>{{ formatBytes(downloadInfo.fileSize) }} · expires in {{ downloadInfo.expiresInSeconds }}s ↗</small>
          </a>
        </section>

        <section class="workspace-panel transfer-status-panel">
          <div class="panel-heading">Transfer status</div>
          <div v-if="uploadTask" class="transfer-task">
            <div class="task-summary"><div><span class="task-label">Task</span><strong>{{ uploadTask.taskId }}</strong></div><span class="state-pill">{{ uploadStatus?.task.status || uploadTask.status }}</span></div>
            <div class="progress-track"><span :style="{ width: `${uploadProgress}%` }"></span></div>
            <div class="progress-meta"><span>{{ uploadProgress }}% · {{ completedParts }}/{{ uploadTask.partCount }} parts</span><span>{{ formatBytes(uploadTask.fileSize) }}</span></div>
            <div class="detail-actions">
              <button class="accent-button" type="button" :disabled="transferBusy" @click="runUploadAction('start')">Start</button>
              <button class="subtle-button" type="button" :disabled="transferBusy" @click="runUploadAction('pause')">Pause</button>
              <button class="subtle-button" type="button" :disabled="transferBusy" @click="runUploadAction('resume')">Resume</button>
              <button class="subtle-button" type="button" :disabled="transferBusy" @click="runUploadAction('retry')">Retry</button>
              <button class="danger-button" type="button" :disabled="transferBusy" @click="runUploadAction('cancel')">Cancel</button>
            </div>
            <div v-if="uploadStatus" class="part-grid"><span v-for="part in uploadStatus.parts" :key="part.partNumber" :class="`part-${part.status.toLowerCase()}`" :title="`Part ${part.partNumber}: ${part.status}`"></span></div>
          </div>
          <div v-else class="panel-empty">Create an import task to see live part progress here.</div>
        </section>
      </div>
      <div v-if="toolError" class="tool-error">{{ toolError }}</div>
    </section>

    <nav class="landing-nav" aria-label="EA-Agent navigation">
      <button class="landing-log-button" type="button" @click="openLandingPage('home')">
        <span class="landing-log-name">EA-Agent</span>
      </button>
      <button
        class="landing-nav-button"
        :class="{ active: activeLandingPage === 'plugin' }"
        type="button"
        @click="openLandingPage('plugin')"
      >Plugin</button>
      <button
        class="landing-nav-button"
        :class="{ active: activeLandingPage === 'transfer' }"
        type="button"
        @click="openLandingPage('transfer')"
      >Transfer</button>
      <button class="landing-nav-button landing-chat-button" type="button" @click="enterConversation">Chat</button>
    </nav>
  </div>

  <div v-else class="app-shell conversation-view">
    <aside class="sidebar">
      <div class="brand">
        <div class="brand-mark">EA</div>
        <div>
          <div class="brand-name">EA Harness</div>
          <div class="brand-subtitle">Local agent bridge</div>
        </div>
      </div>

      <button class="new-button" type="button" @click="newSession">
        <span>+</span>
        <span>New task</span>
      </button>

      <nav class="session-list">
        <div
          v-for="session in sessions"
          :key="session.id"
          class="session-row"
        >
          <button
            class="session-item"
            :class="{ active: session.id === activeId }"
            type="button"
            @click="selectSession(session.id)"
          >
            <span class="session-title">{{ session.title }}</span>
          </button>
          <button class="delete-button" type="button" @click.stop="removeSession(session.id)">
            ×
          </button>
        </div>
      </nav>
    </aside>

    <main class="workspace">
      <header class="topbar">
        <div>
          <div class="task-title">{{ activeSession?.title || "New task" }}</div>
          <div class="task-meta">Non-streaming model call</div>
        </div>
        <button class="back-home-button" type="button" @click="exitConversation">
          <span aria-hidden="true">↙</span>
          <span>返回主页</span>
        </button>
      </header>

      <section ref="messageList" class="messages">
        <div v-if="!activeSession?.messages.length" class="empty-state">
          <div class="empty-title">Start a task</div>
          <div class="empty-copy">Send a prompt to the local EASY-AGENT runtime.</div>
        </div>

        <article
          v-for="message in activeSession?.messages || []"
          :key="message.id"
          :class="messageClass(message)"
        >
          <div class="message-role">{{ message.role }}</div>
          <div class="message-content">{{ message.content }}</div>
        </article>

        <div v-if="isLoading" class="thinking">Running agent loop...</div>
      </section>

      <div v-if="error" class="error-bar">{{ error }}</div>

      <footer class="composer">
        <textarea
          v-model="draft"
          rows="3"
          placeholder="Ask EA Harness to work on something..."
          @keydown="onKeydown"
        />
        <button type="button" :disabled="!canSend" @click="submit">Send</button>
      </footer>
    </main>
  </div>
</template>
