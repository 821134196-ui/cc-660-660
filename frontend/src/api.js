const base = '/api';

async function req(path, opts = {}) {
  const res = await fetch(base + path, opts);
  if (!res.ok) {
    let msg = `请求失败（${res.status}）`;
    try {
      const j = await res.json();
      if (j.error) msg = j.error;
    } catch { /* 非 JSON 响应 */ }
    throw new Error(msg);
  }
  if (res.status === 204) return null;
  return res.json();
}

function json(method, path, body) {
  return req(path, {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  });
}

export const api = {
  scan: (code) => req(`/scan/${encodeURIComponent(code)}`),
  helmets: (status) => req('/helmets' + (status ? `?status=${status}` : '')),
  helmet: (id) => req(`/helmets/${id}`),
  register: (data) => json('POST', '/helmets', data),
  issue: (id, workerName) => json('POST', `/helmets/${id}/issue`, { workerName }),
  returnHelmet: (id) => req(`/helmets/${id}/return`, { method: 'POST' }),
  reportImpact: (id, data) => json('POST', `/helmets/${id}/impacts`, data),
  impacts: (status) => req('/impacts' + (status ? `?status=${status}` : '')),
  impact: (id) => req(`/impacts/${id}`),
  inspect: (eventId, formData) => req(`/impacts/${eventId}/inspections`, { method: 'POST', body: formData }),
  retirements: () => req('/retirements'),
  attachmentUrl: (id) => `${base}/attachments/${id}/download`
};
