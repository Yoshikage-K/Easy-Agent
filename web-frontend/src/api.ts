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
