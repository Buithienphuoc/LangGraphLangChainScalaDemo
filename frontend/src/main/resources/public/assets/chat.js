import { api, esc, md, header } from "./common.js";

document.getElementById("header").innerHTML = header("chat");

const $ = (id) => document.getElementById(id);
const messages = $("messages");
const state = { patients: [], patientId: new URLSearchParams(location.search).get("patient") };

const SUGGESTIONS = [
  "Phòng khám mở cửa mấy giờ?", "Người bị tiểu đường type 2 nên ăn gì?", "Tôi bị đau bụng âm ỉ",
  "Tôi muốn đặt lịch khám tim mạch", "Tôi uống thêm ibuprofen được không?", "Tôi bị đau ngực, khó thở",
];

function scroll() { messages.scrollTop = messages.scrollHeight; }

function addBubble(role, text, { sources = [], emergency = false } = {}) {
  const div = document.createElement("div");
  div.className = "flex" + (role === "user" ? " justify-end" : "");
  div.dataset.role = role;
  if (role === "user") {
    div.innerHTML = `<div class="bg-teal-600 text-white rounded-2xl rounded-br-sm px-4 py-2 max-w-[80%]">${esc(text)}</div>`;
  } else {
    const color = emergency ? "bg-red-50 border-red-300 text-red-900" : "bg-slate-100 border-slate-200";
    const src = sources.length ? `<div class="mt-2 text-xs text-slate-500" data-testid="sources">Nguồn: ${sources.map(esc).join("; ")}</div>` : "";
    div.innerHTML = `<div class="border ${color} rounded-2xl rounded-bl-sm px-4 py-2 max-w-[85%] space-y-1" data-testid="assistant-message">${md(text)}${src}</div>`;
  }
  messages.appendChild(div);
  scroll();
}

function addPendingCard(p) {
  document.querySelectorAll(".pending-card").forEach((e) => e.remove());
  const div = document.createElement("div");
  div.className = "pending-card flex";
  div.dataset.testid = "pending-card";
  div.innerHTML = `
    <div class="border-2 border-teal-500 bg-teal-50 rounded-xl p-4 max-w-[85%]">
      <div class="text-xs uppercase tracking-wide text-teal-700 font-semibold mb-1">Graph đang dừng chờ bạn xác nhận (interrupt)</div>
      <div class="font-medium">${esc(p.doctor)}</div>
      <div class="text-sm">${esc(p.time)}</div>
      <div class="text-sm text-slate-600 mb-3">Lý do khám: ${esc(p.reason)}</div>
      <div class="flex gap-2">
        <button data-testid="confirm-booking" class="bg-teal-600 hover:bg-teal-700 text-white rounded-lg px-4 py-1.5 text-sm">Xác nhận đặt lịch</button>
        <button data-testid="cancel-booking" class="bg-white border border-slate-300 hover:bg-slate-100 rounded-lg px-4 py-1.5 text-sm">Huỷ</button>
      </div>
    </div>`;
  div.querySelector("[data-testid=confirm-booking]").onclick = () => decide(true, div);
  div.querySelector("[data-testid=cancel-booking]").onclick = () => decide(false, div);
  messages.appendChild(div);
  scroll();
}

function renderProfile(p) {
  const row = (label, xs) => `<div><span class="text-slate-500">${label}:</span> ${xs.length ? esc(xs.join(", ")) : '<i class="text-slate-400">không có</i>'}</div>`;
  $("profile").innerHTML = `
    <h2 class="font-semibold text-base mb-1">Hồ sơ (memory dài hạn)</h2>
    <div><span class="text-slate-500">Họ tên:</span> ${esc(p.name)}, sinh năm ${p.birthYear}</div>
    ${row("Dị ứng", p.allergies)}${row("Bệnh nền", p.conditions)}${row("Thuốc đang dùng", p.medications)}`;
}

function renderTrace(t) {
  const path = t.path.map((n) => `<span class="inline-block bg-teal-100 text-teal-800 rounded px-2 py-0.5 text-xs font-mono">${esc(n)}</span>`)
    .join('<span class="text-slate-400 mx-1">→</span>');
  const steps = t.steps.map((s) => `<li class="font-mono text-xs break-words">${esc(s)}</li>`).join("");
  const calls = t.modelCalls.map((e) => `<li class="text-xs"><b>${esc(e.title)}</b> <span class="text-slate-500">${esc(e.detail)}</span></li>`).join("");
  $("trace").innerHTML = `
    <h2 class="font-semibold mb-3">Panel quan sát</h2>
    <div class="text-xs uppercase text-slate-500 font-semibold mb-1">LangGraph4j: các node đã chạy</div>
    <div class="mb-3 leading-7" data-testid="trace-path">${path || '<i class="text-sm text-slate-400">không chạy node nào</i>'}</div>
    <div class="text-xs uppercase text-slate-500 font-semibold mb-1">Ghi chú từng node (channel trace)</div>
    <ol class="list-decimal ml-5 space-y-1 mb-3">${steps}</ol>
    <div class="text-xs uppercase text-slate-500 font-semibold mb-1">LangChain4j: lời gọi model (ChatModelListener)</div>
    <ul class="space-y-1" data-testid="trace-model-calls">${calls || '<li class="text-xs text-slate-400">không gọi model</li>'}</ul>`;
}

function busy(on) {
  $("thinking").classList.toggle("hidden", !on);
  $("send").disabled = on;
}

function showTurn({ turn, patient }) {
  addBubble("assistant", turn.reply, { sources: turn.sources, emergency: turn.emergency });
  if (turn.pending) addPendingCard(turn.pending);
  renderTrace(turn);
  renderProfile(patient);
}

async function send(text) {
  addBubble("user", text);
  busy(true);
  try {
    showTurn(await api("/api/chat", { method: "POST", body: JSON.stringify({ patientId: state.patientId, message: text }) }));
  } catch (e) {
    addBubble("assistant", `Xin lỗi, có lỗi: ${e.message}`, { emergency: true });
  } finally { busy(false); }
}

async function decide(confirm, card) {
  card.querySelectorAll("button").forEach((b) => (b.disabled = true));
  busy(true);
  try {
    const res = await api("/api/booking/decision", { method: "POST", body: JSON.stringify({ patientId: state.patientId, confirm }) });
    card.remove();
    showTurn(res);
  } catch (e) {
    addBubble("assistant", `Xin lỗi, có lỗi: ${e.message}`, { emergency: true });
  } finally { busy(false); }
}

async function init() {
  state.patients = await api("/api/patients");
  if (!state.patients.some((p) => p.id === state.patientId)) state.patientId = state.patients[0].id;
  const current = state.patients.find((p) => p.id === state.patientId);

  $("patient").innerHTML = state.patients.map((p) => `<option value="${p.id}" ${p.id === state.patientId ? "selected" : ""}>${esc(p.name)} (${p.id})</option>`).join("");
  $("patient").onchange = (e) => (location.href = `/?patient=${e.target.value}`);

  $("suggestions").innerHTML = SUGGESTIONS.map((q) =>
    `<button type="button" class="text-xs bg-white border border-slate-300 rounded-full px-3 py-1 hover:bg-slate-100">${esc(q)}</button>`).join("");
  $("suggestions").querySelectorAll("button").forEach((b) => (b.onclick = () => { $("msg").value = b.innerText; $("msg").focus(); }));

  renderProfile(current);
  addBubble("assistant", `Xin chào ${current.name}! Mình có thể trả lời thông tin phòng khám, kiến thức sức khoẻ, sàng lọc triệu chứng, đặt lịch khám và kiểm tra tương tác thuốc.`);
  const { pending } = await api(`/api/patients/${state.patientId}/pending`);
  if (pending) addPendingCard(pending);

  $("chat-form").onsubmit = (e) => {
    e.preventDefault();
    const text = $("msg").value.trim();
    if (!text) return;
    $("msg").value = "";
    send(text);
  };
}

init().catch((e) => addBubble("assistant", `Không tải được dữ liệu: ${e.message}`, { emergency: true }));
