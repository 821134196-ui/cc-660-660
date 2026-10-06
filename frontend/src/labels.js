export const STATUS = {
  IN_STOCK: { label: '在库可领', color: '#16a34a', bg: '#dcfce7' },
  ISSUED: { label: '已领出', color: '#1d4ed8', bg: '#dbeafe' },
  FROZEN: { label: '冻结待检', color: '#b45309', bg: '#fef3c7' },
  RETIRED: { label: '已退役', color: '#b91c1c', bg: '#fee2e2' }
};

export const SEVERITY = {
  MINOR: '轻微',
  MODERATE: '中等',
  SEVERE: '严重',
  UNKNOWN: '程度不明'
};

export const IMPACT_STATUS = {
  PENDING: '待检查',
  IN_RECHECK: '复检中',
  CLOSED: '已结案'
};

export const DECISION = {
  REUSE: '复用',
  RECHECK: '复检',
  RETIRE: '退役'
};

export const PART = {
  OK: '完好',
  DAMAGED: '损伤'
};

export function fmtTime(t) {
  return t ? t.replace('T', ' ').slice(0, 16) : '—';
}
