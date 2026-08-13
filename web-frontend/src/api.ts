export type Role = "user" | "assistant";

export interface ChatMessage {
  id: string;
  role: Role;
  content: string;
}

export interface SessionSummary {
  id: string;
  title: string;
}

export interface SessionDetail extends SessionSummary {
  messages: ChatMessage[];
}

export interface PluginResponse {
  pluginId: string;
  version: string;
  state: string;
  pluginClass: string;
  description: string;
  path: string;
}

export interface UploadPartStatus {
  partNumber: number;
  start: number;
  end: number;
  status: string;
}

export interface UploadTask {
  taskId: string;
  status: string;
  fileSize: number;
  partSize: number;
  partCount: number;
  bucket: string;
  objectName: string;
}

export interface UploadStatus {
  task: UploadTask;
  parts: UploadPartStatus[];
}

export interface CreateUploadPayload {
  sourceUrl: string;
  bucket: string;
  objectName: string;
  headers?: Record<string, string>;
}

export interface DownloadUrlResponse {
  bucket: string;
  objectName: string;
  fileName: string;
  fileSize: number;
  contentType: string;
  downloadUrl: string;
  expiresInSeconds: number;
}

const jsonHeaders = {
  "Content-Type": "application/json"
};

async function request<T>(url: string, options?: RequestInit): Promise<T> {
  const response = await fetch(url, options);
  if (!response.ok) {
    const body = await response.json().catch(() => ({}));
    throw new Error(body.detail || `Request failed: ${response.status}`);
  }
  return response.json() as Promise<T>;
}

export async function listSessions() {
  return request<{ sessions: SessionSummary[] }>("/api/sessions");
}

export async function createSession() {
  return request<{ sessionId: string }>("/api/sessions", { method: "POST" });
}

export async function getSession(id: string) {
  return request<{ session: SessionDetail }>(`/api/sessions/${id}`);
}

export async function deleteSession(id: string) {
  return request<{ ok: boolean }>(`/api/sessions/${id}`, { method: "DELETE" });
}

export async function sendMessage(id: string, content: string) {
  return request<{ traceId: string; sessionId: string; success: boolean; content: string; errorMessage: string }>(
    `/api/sessions/${id}/messages`,
    {
      method: "POST",
      headers: jsonHeaders,
      body: JSON.stringify({ message: content })
    }
  );
}

export async function listPlugins() {
  return request<{ plugins: PluginResponse[] }>("/api/plugins");
}

export async function discoverPlugins() {
  return request<{ plugins: PluginResponse[] }>("/api/plugins/discover", { method: "POST" });
}

export async function loadPlugin(pluginPath: string) {
  return request<PluginResponse>("/api/plugins/load", {
    method: "POST",
    headers: jsonHeaders,
    body: JSON.stringify({ pluginPath })
  });
}

export async function pluginAction(pluginId: string, action: "start" | "stop" | "reload" | "unload") {
  return request<PluginResponse>(`/api/plugins/${encodeURIComponent(pluginId)}/${action}`, { method: "POST" });
}

export async function listExtensions() {
  return request<{ extensions: string[] }>("/api/plugins/extensions");
}

export async function createUpload(payload: CreateUploadPayload) {
  return request<UploadTask>("/api/uploads", {
    method: "POST",
    headers: jsonHeaders,
    body: JSON.stringify(payload)
  });
}

export async function uploadAction(taskId: string, action: "start" | "pause" | "resume" | "retry" | "cancel") {
  return request<UploadTask>(`/api/uploads/${encodeURIComponent(taskId)}/${action}`, { method: "POST" });
}

export async function getUploadStatus(taskId: string) {
  return request<UploadStatus>(`/api/uploads/${encodeURIComponent(taskId)}`);
}

export async function createDownloadUrl(bucket: string, objectName: string) {
  return request<DownloadUrlResponse>("/api/downloads/url", {
    method: "POST",
    headers: jsonHeaders,
    body: JSON.stringify({ bucket, objectName })
  });
}

export function directDownloadUrl(bucket: string, objectName: string) {
  const params = new URLSearchParams({ bucket, objectName });
  return `/api/downloads?${params.toString()}`;
}
