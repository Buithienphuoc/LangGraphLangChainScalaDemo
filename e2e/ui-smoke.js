// UI-01..UI-05: smoke test giao diện bằng Playwright, chạy trên DevServer (model giả).
//   sbt "backend/Test/runMain demo.health.backend.DevServer" &   # cổng 8080
//   node e2e/ui-smoke.js [http://localhost:8080]
const { chromium } = require("playwright");
const BASE = process.argv[2] || "http://localhost:8081";
const results = [];
async function step(id, name, fn) {
  try { await fn(); results.push([id, name, "PASS", ""]); }
  catch (e) { results.push([id, name, "FAIL", String(e.message).split("\n")[0]]); }
}
const expect = (cond, msg) => { if (!cond) throw new Error(msg); };

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage();
  const apiCalls = []; const consoleErrors = [];
  page.on("response", (r) => { if (r.url().includes("/api/")) apiCalls.push([r.request().method(), new URL(r.url()).pathname, r.status()]); });
  page.on("console", (m) => { if (m.type() === "error" && !m.text().includes("tailwind") && !m.text().includes("ERR_")) consoleErrors.push(m.text()); });
  page.on("pageerror", (e) => consoleErrors.push(e.message));

  const send = async (text) => {
    const before = await page.locator("[data-testid=assistant-message]").count();
    await page.fill("[data-testid=chat-input]", text);
    await page.click("[data-testid=chat-send]");
    await page.waitForFunction((n) => document.querySelectorAll("[data-testid=assistant-message]").length > n, before, { timeout: 60000 });
    return (await page.locator("[data-testid=assistant-message]").last().innerText());
  };

  await step("UI-01", "Trang chủ tải được, có banner an toàn và danh sách bệnh nhân", async () => {
    await page.goto(BASE + "/", { waitUntil: "domcontentloaded" });
    await page.waitForFunction(() => document.querySelectorAll("[data-testid=patient-select] option").length >= 3, null, { timeout: 15000 });
    expect((await page.content()).includes("Không thay thế bác sĩ"), "thiếu banner");
  });

  await step("UI-02", "Hỏi giờ mở cửa: hiện câu trả lời có nguồn và trace path", async () => {
    const t = await send("Phòng khám mở cửa mấy giờ?");
    expect(t.includes("7:30"), "câu trả lời: " + t);
    expect(await page.locator("[data-testid=sources]").count() > 0, "không hiện nguồn");
    const path = await page.locator("[data-testid=trace-path]").innerText();
    expect(path.includes("faq"), "trace-path: " + path);
  });

  await step("UI-03", "Đặt lịch: hiện thẻ xác nhận, bấm Xác nhận thì báo đã đặt", async () => {
    await send("Tôi muốn đặt lịch khám tiêu hoá");
    await page.waitForSelector("[data-testid=pending-card]", { timeout: 15000 });
    const before = await page.locator("[data-testid=assistant-message]").count();
    await page.click("[data-testid=confirm-booking]");
    await page.waitForFunction((n) => document.querySelectorAll("[data-testid=assistant-message]").length > n, before, { timeout: 60000 });
    const t = await page.locator("[data-testid=assistant-message]").last().innerText();
    expect(t.includes("Đã đặt lịch"), "sau xác nhận: " + t);
  });

  await step("UI-04", "Red flag: cảnh báo 115", async () => {
    const t = await send("Tôi bị đau ngực và khó thở");
    expect(t.includes("115"), t);
  });

  await step("UI-05", "Tải lại trang khi đang chờ xác nhận: thẻ xác nhận vẫn còn (checkpoint)", async () => {
    await send("đặt lịch khám");
    await page.waitForSelector("[data-testid=pending-card]", { timeout: 15000 });
    await page.reload({ waitUntil: "domcontentloaded" });
    await page.waitForSelector("[data-testid=pending-card]", { timeout: 15000 });
    await page.click("[data-testid=cancel-booking]");
  });

  await step("UI-06", "Trang bác sĩ hiện lịch hẹn vừa đặt kèm SOAP", async () => {
    await page.goto(BASE + "/doctor", { waitUntil: "domcontentloaded" });
    await page.waitForSelector("[data-testid=appointment-row]", { timeout: 15000 });
    const row = await page.locator("[data-testid=appointment-row]").first().innerText();
    expect(row.length > 10, row);
  });

  await step("UI-07", "Trang sơ đồ graph gọi /api/graph thành công", async () => {
    await page.goto(BASE + "/graph", { waitUntil: "domcontentloaded" });
    await page.waitForTimeout(1500);
    expect(apiCalls.some(([m, p, s]) => p === "/api/graph" && s === 200), "không gọi /api/graph");
    expect((await page.locator("#header").innerText()).includes("Phòng khám"), "header không hiện");
    expect((await page.locator("#source").textContent()).includes("guardrail"), "không hiện sơ đồ/mã Mermaid");
  });

  await step("UI-08", "Mọi lời gọi API từ giao diện thành công, không có lỗi JS", async () => {
    const bad = apiCalls.filter(([, , s]) => s >= 400);
    expect(bad.length === 0, "API lỗi: " + JSON.stringify(bad));
    expect(consoleErrors.length === 0, "lỗi console: " + consoleErrors.join(" | "));
    const used = new Set(apiCalls.map(([m, p]) => m + " " + p.replace(/BN\d+/, "{id}")));
    console.log("Endpoint giao diện đã gọi: " + [...used].sort().join(", "));
  });

  await browser.close();
  for (const r of results) console.log(r.join(" | "));
  process.exit(results.some((r) => r[2] === "FAIL") ? 1 : 0);
})();
