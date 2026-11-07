// Tiện ích dùng chung cho các trang. Không framework, không bước build: chỉ fetch + DOM.

export async function api(path, options = {}) {
  const res = await fetch(path, {
    headers: { "Content-Type": "application/json" },
    credentials: "same-origin",
    ...options,
  });
  const body = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(body.error || `HTTP ${res.status}`);
  return body;
}

export function esc(s) {
  return String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);
}

/** Markdown tối giản: **đậm**, _nghiêng_, gạch đầu dòng, xuống dòng. */
export function md(s) {
  return esc(s).split("\n").map((line) => {
    const l = line.replace(/\*\*(.+?)\*\*/g, "<b>$1</b>").replace(/(^|\s)_(.+?)_(\s|$)/g, "$1<i>$2</i>$3");
    return l.trim().startsWith("- ") ? `<div class="pl-3">• ${l.trim().slice(2)}</div>` : `<div>${l || "&nbsp;"}</div>`;
  }).join("");
}

export function header(active) {
  const link = (href, label, key) =>
    `<a href="${href}" class="px-3 py-1.5 rounded-md text-sm font-medium ${key === active ? "bg-white text-teal-700" : "text-white hover:bg-teal-600"}">${label}</a>`;
  return `
  <header class="bg-teal-700"><div class="max-w-7xl mx-auto px-4 py-3 flex items-center gap-4 flex-wrap">
    <div class="text-white font-semibold text-lg mr-4">🩺 Phòng khám An Khang
      <span class="text-teal-200 text-sm font-normal">demo LangChain4j + LangGraph4j</span></div>
    <nav class="flex gap-2">${link("/", "Trợ lý", "chat")}${link("/doctor", "Trang bác sĩ", "doctor")}${link("/graph", "Sơ đồ graph", "graph")}</nav>
  </div></header>
  <div class="bg-amber-100 text-amber-900 text-sm text-center py-2 px-4">
    Bản demo kỹ thuật với dữ liệu giả. Không thay thế bác sĩ. Khi khẩn cấp hãy gọi 115.</div>`;
}
