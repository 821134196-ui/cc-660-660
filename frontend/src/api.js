// 后端 API 封装。统一处理 JSON 与错误消息。
const BASE = '/api';

async function request(path, options = {}) {
  const res = await fetch(BASE + path, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
    body: options.body ? JSON.stringify(options.body) : undefined
  });
  const text = await res.text();
  const data = text ? JSON.parse(text) : null;
  if (!res.ok) {
    const message = data?.error || `请求失败（${res.status}）`;
    const err = new Error(message);
    err.status = res.status;
    throw err;
  }
  return data;
}

export const api = {
  listHelmets: (status) => request(`/helmets${status ? `?status=${encodeURIComponent(status)}` : ''}`),
  getHelmet: (code) => request(`/helmets?code=${encodeURIComponent(code)}`),
  getDetail: (code) => `/helmets/${encodeURIComponent(code)}`,
  detail: (code) => request(`/helmets/${encodeURIComponent(code)}`),
  register: (body) => request('/helmets', { method: 'POST', body }),
  update: (code, body) => request(`/helmets/${encodeURIComponent(code)}`, { method: 'PUT', body }),
  impact: (code, body) => request(`/helmets/${encodeURIComponent(code)}/impacts`, { method: 'POST', body }),
  checkout: (code, body) => request(`/helmets/${encodeURIComponent(code)}/checkout`, { method: 'POST', body }),
  return: (code, body) => request(`/helmets/${encodeURIComponent(code)}/return`, { method: 'POST', body }),
  inspect: (code, body) => request(`/helmets/${encodeURIComponent(code)}/inspections`, { method: 'POST', body })
};

export function fileToBase64(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result).split(',')[1]);
    reader.onerror = reject;
    reader.readAsDataURL(file);
  });
}

export function fmtDateTime(s) {
  if (!s) return '—';
  return s.replace('T', ' ').slice(0, 16);
}
export function fmtDate(s) {
  return s || '—';
}
