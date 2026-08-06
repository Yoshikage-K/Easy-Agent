<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from "vue";
import {
  ChatMessage,
  SessionDetail,
  SessionSummary,
  createSession,
  deleteSession,
  getSession,
  listSessions,
  sendMessage
} from "./api";

const sessions = ref<SessionSummary[]>([]);
const activeSession = ref<SessionDetail | null>(null);
const draft = ref("");
const isLoading = ref(false);
const error = ref("");
const messageList = ref<HTMLElement | null>(null);

const activeId = computed(() => activeSession.value?.id || "");
const canSend = computed(() => draft.value.trim().length > 0 && !isLoading.value);

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

onMounted(async () => {
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
});
</script>

<template>
  <div class="app-shell">
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
