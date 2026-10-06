import { writable } from 'svelte/store';

// 全局轻提示：error / ok，3 秒自动消失
export const toast = writable(null);
let timer;

export function showToast(message, kind = 'error') {
  toast.set({ message, kind });
  clearTimeout(timer);
  timer = setTimeout(() => toast.set(null), 3200);
}
