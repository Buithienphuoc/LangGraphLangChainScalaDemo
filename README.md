# Health care demo: LangChain4j + LangGraph4j bằng Scala

Website "phòng khám ảo" có trợ lý AI, dùng để học cách dùng **LangChain4j** (gọi LLM, tool, memory, RAG,
guardrail) và **LangGraph4j** (graph workflow, rẽ nhánh, subgraph, checkpoint, human-in-the-loop) từ Scala 3.

> Dữ liệu (bệnh nhân, bác sĩ, lịch, tài liệu y tế) đều là dữ liệu giả. Đây là demo kỹ thuật, không phải sản phẩm y tế.

## Cài đặt và chạy

### 1. Cài đặt (làm 1 lần)

| Cần có | Ghi chú |
|---|---|
| JDK 21 | Java 22+ vẫn chạy được nhưng in thêm cảnh báo `sun.misc.Unsafe`, bỏ qua được. |
| sbt 1.11 | Lần chạy đầu sbt tải thư viện và model embedding (~vài trăm MB), mất vài phút. |
| IntelliJ IDEA + plugin **Scala** | Không bắt buộc, chỉ cần nếu chạy bằng IDE (mục 4). |
| Postgres / Docker | **Không cần.** Mặc định dữ liệu nằm trong RAM. |

```bash
git clone https://github.com/Buithienphuoc/LangGraphLangChainScalaExperiment.git
cd LangGraphLangChainScalaExperiment
sbt compile        # kiểm tra build, tải thư viện
```

Ứng dụng chạy ở **http://localhost:8081**. Frontend do backend phục vụ luôn, không cần start riêng.
Đổi cổng bằng biến `PORT` (ví dụ cổng 8081 đang bị chiếm).

**Chọn model bằng `LLM_PROVIDER`.** Mọi model đều start cùng một cách (`sbt backend/run` hoặc Run Configuration `Main`),
chỉ khác giá trị biến môi trường. Model được tạo trong `Models.chatModel` (`langchain/.../Models.scala`):

| `LLM_PROVIDER` | Model | Cần |
|---|---|---|
| `fake` | `FakeChatModel` (model tự viết, trả lời theo luật) | không cần gì |
| `anthropic` (mặc định nếu không set) | Claude qua `AnthropicChatModel` | `ANTHROPIC_API_KEY` |
| `ollama` | model chạy offline qua `OllamaChatModel` | cài Ollama |

### 2. Chạy với model giả `LLM_PROVIDER=fake` (không cần API key)

Không cần API key, không database. Chỉ cần đặt `LLM_PROVIDER=fake` rồi chạy như với model thật:

macOS / Linux / Git Bash:
```bash
LLM_PROVIDER=fake sbt backend/run
```
Windows PowerShell:
```powershell
$env:LLM_PROVIDER="fake"; sbt backend/run
```
Hoặc dùng file `.env` như mục 3: file `.env.example` mặc định đã là `LLM_PROVIDER=fake`.

Đợi log `Mở trình duyệt: http://localhost:8081` rồi mở trình duyệt.

**Model giả là gì?** Không phải LLM nào cả: đó là class tự viết (chính là `FakeChatModel.scala`, ở
`langchain/src/main/scala/demo/health/langchain/FakeChatModel.scala`) implement interface `ChatModel` của LangChain4j,
nên với phần còn lại của app nó trông như Claude hay Ollama. Nó đại diện cho các model tự customize: muốn thêm model
khác, viết một class implement `ChatModel`, thêm một giá trị vào `LlmProvider` và một case trong `Models.chatModel`.
Nó đọc system message để biết agent nào đang gọi rồi trả lời theo luật:

| Agent | Model giả (`FakeChatModel.scala`) trả về |
|---|---|
| Router | JSON intent theo từ khoá: "đặt lịch" → `BOOKING`, "thuốc" → `MEDICATION`, "mở cửa" → `FAQ`, "đau" → `TRIAGE`, còn lại `OTHER` |
| Triage | Câu có chữ "ngày" → triệu chứng đầy đủ; không có → hỏi lại ("Bạn bị bao lâu rồi?") để luồng nhiều lượt chạy |
| Đặt lịch | Giả lập tool calling thật: `findDoctors` → `getAvailableSlots` → `proposeBooking`, rồi graph dừng ở `interruptBefore` chờ bấm xác nhận |
| Thuốc | Gọi tool `checkInteractions`; lần đầu cố tình nêu liều ("400 mg") để output guardrail bắt và reprompt |
| Tóm tắt SOAP | JSON cố định |
| FAQ / câu khác | Luôn "Phòng khám mở cửa từ 7:30 đến 17:00…" |

Chỉ có LLM là giả: graph LangGraph4j, tool, memory, guardrail và RAG đều chạy thật (RAG vẫn dùng embedding thật
all-MiniLM-L6-v2 chạy in-process qua ONNX). Mỗi lần gọi model giả được tính 100 token vào và 20 token ra để panel quan sát có số liệu.
Vì vậy câu trả lời không thông minh, nhưng mọi node, tool, interrupt và panel quan sát đều chạy thật. Thử các câu:
`Phòng khám mở cửa mấy giờ?` · `Tôi bị đau đầu 2 ngày` · `Tôi muốn đặt lịch khám` · `Tôi uống aspirin với ibuprofen được không?`

> Lỗi `Thiếu ANTHROPIC_API_KEY` nghĩa là `LLM_PROVIDER` chưa được set (mặc định là `anthropic`). Đặt `LLM_PROVIDER=fake` như trên.
> Lỗi `Address already in use` nghĩa là cổng đang bị chiếm: tắt app cũ hoặc đặt `PORT=8082`.
> Chữ tiếng Việt trong log bị lỗi font (`─æ├ú index`) trên Windows: console đang dùng code page 437. Chạy `chcp 65001`
> trước lệnh sbt (hoặc dùng Windows Terminal với font hỗ trợ Unicode). App vẫn ghi log đúng UTF-8.
> Cảnh báo `sun.misc.Unsafe` in ra từ `sbt-launch.jar` là của chính sbt khi chạy trên Java 23+, vô hại; dùng JDK 21 thì hết.

### 3. Chạy với API thật bằng command line + file `.env`

**Bước 1: tạo file `.env`** ở thư mục gốc project (file này đã nằm trong `.gitignore`, không bị commit):
```bash
cp .env.example .env          # Windows: copy .env.example .env
```
Sửa `.env`, đổi `LLM_PROVIDER` sang model thật, ví dụ với Claude:
```dotenv
LLM_PROVIDER=anthropic
ANTHROPIC_API_KEY=sk-ant-...        # lấy ở https://console.anthropic.com → Settings → API Keys
ANTHROPIC_MODEL=claude-sonnet-5-5   # muốn rẻ hơn: claude-haiku-5-5
# PORT=8081
# STORAGE=memory
```
Hoặc với Ollama (offline, không cần key): `LLM_PROVIDER=ollama`, `OLLAMA_MODEL=qwen2.5:7b` (chạy `ollama pull qwen2.5:7b` trước).

Ghi chú về cú pháp: mỗi dòng một `KEY=value`, không dấu cách quanh `=`, không bọc giá trị trong ngoặc kép.

**Bước 2: nạp `.env` rồi chạy.** App đọc cấu hình từ biến môi trường, nên cần nạp file `.env` vào terminal trước:

macOS / Linux / Git Bash:
```bash
set -a; source .env; set +a
sbt backend/run
```

Windows PowerShell:
```powershell
Get-Content .env | Where-Object { $_ -match '^\s*[^#\s][^=]*=' } | ForEach-Object {
  $k, $v = $_ -split '=', 2
  [Environment]::SetEnvironmentVariable($k.Trim(), ($v -replace '\s+#.*$', '').Trim(), 'Process')
}
sbt backend/run
```

Biến chỉ tồn tại trong cửa sổ terminal đó; mở terminal mới thì nạp lại. Kiểm tra nhanh: `echo $ANTHROPIC_API_KEY`
(PowerShell: `echo $env:ANTHROPIC_API_KEY`).

Chạy bộ test với LLM thật cũng dùng cách này: nạp `.env` rồi `sbt "backend/testOnly *LiveChatbotSpec"`.

### 4. Chạy bằng IntelliJ Run Configuration + file `.env`

**Mở project:** File → Open → chọn thư mục project → IntelliJ nhận ra `build.sbt` và import (chọn JDK 21 ở
File → Project Structure → Project SDK nếu được hỏi). Đợi thanh "sbt sync" chạy xong.

**Cài plugin EnvFile** (để Run Configuration đọc file `.env`): Settings → Plugins → Marketplace → tìm **EnvFile** → Install → restart IDE.

**Tạo Run Configuration** (một cấu hình dùng cho mọi model): Run → Edit Configurations… → `+` → **Application**

| Ô | Giá trị |
|---|---|
| Name | `Backend` |
| JDK / JRE | 21 |
| Module (`-cp`) | `backend` |
| Main class | `demo.health.backend.Main` |
| Working directory | `$PROJECT_DIR$` |
| Tab **EnvFile** | tick **Enable EnvFile** → `+` → **.env file** → chọn `.env` ở thư mục gốc |

Muốn đổi model thì chỉ sửa `LLM_PROVIDER` trong `.env` (`fake`, `anthropic`, `ollama`) rồi chạy lại, không cần tạo cấu hình khác.

Bấm ▶ (Run) hoặc 🐞 (Debug, đặt breakpoint được trong node của graph hay trong tool) rồi mở http://localhost:8081.

Không muốn cài plugin? Ở ô **Environment variables** của Run Configuration, bấm biểu tượng 📄 rồi dán nội dung
`.env` (các dòng `KEY=value`) vào; IntelliJ tự tách thành từng biến. Nhược điểm là key nằm trong `.idea/workspace.xml`
thay vì `.env`.

### 5. Chạy với Postgres + pgvector (Docker, tuỳ chọn)

```bash
cp .env.example .env   # điền ANTHROPIC_API_KEY; docker compose tự đọc .env
docker compose up --build
```
Schema (`backend/src/main/resources/schema.sql`) tự chạy khi app khởi động với `STORAGE=postgres`, và dữ liệu giả được seed nếu DB còn trống.
Lưu ý: `docker-compose.yml` và `Dockerfile` đang map cổng 8080, nên khi chạy Docker thì đặt `PORT=8080` trong `.env` hoặc sửa hai file đó.

### Biến môi trường

| Biến môi trường | Mặc định | Ý nghĩa |
|---|---|---|
| `LLM_PROVIDER` | `anthropic` | `fake`, `anthropic` hoặc `ollama` |
| `ANTHROPIC_API_KEY`, `ANTHROPIC_MODEL` | , `claude-sonnet-5-5` | |
| `OLLAMA_BASE_URL`, `OLLAMA_MODEL` | `http://localhost:11434`, `qwen2.5:7b` | |
| `EMBEDDING` | `local` | `local` = all-MiniLM-L6-v2 in-process; `ollama:<model>` ví dụ `ollama:bge-m3` |
| `STORAGE` | `memory` | `memory` hoặc `postgres` |
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` | `localhost`, `5432`, `healthcare` × 3 | |
| `PORT` | `8081` | |

## Kiến trúc: Hexagonal, 5 module sbt

![Sơ đồ Hexagonal Architecture của Health care demo](docs/hexagonal-architecture.svg)

Cách đọc sơ đồ (mũi tên là chiều gọi):

- **Lõi (lục giác) chỉ có `domain`**: model, quy tắc (RedFlags, DrugInteractions) và các port (trait). `domain` không khai
  báo `dependsOn` nào và không có thư viện ngoài, nên không thể import LangGraph4j, LangChain4j, http4s hay JDBC.
- **Port vào (bên trái)**: `ConversationUseCase`. Frontend gọi REST vào `backend/http` (`ApiRoutes`), rồi `ApiRoutes` gọi
  port này. Test e2e gọi vào cùng port.
- **`langgraph` là adapter bên ngoài lõi (phía dưới)**: `GraphConversationService` cài đặt port `ConversationUseCase` bằng
  một graph LangGraph4j, và graph gọi ra các port ra của domain. Module này chỉ `dependsOn(domain)`, không phụ thuộc
  `langchain`, nên test được bằng port AI giả viết tay.
- **Port ra (bên phải)**: 7 port AI (gom trong `AiPorts`) do module `langchain` cài đặt bằng LangChain4j, và `ClinicRepository`
  có hai cách cài đặt (in-memory hoặc Postgres). Tool `@Tool` của `langchain` cũng đi qua `ClinicRepository`.
- **`backend/Wiring.scala`** là composition root, nơi duy nhất chọn adapter nào cắm vào port nào. Đổi Claude sang Ollama,
  hay in-memory sang Postgres, chỉ là đổi cấu hình ở đây.

Sơ đồ (file SVG) được vẽ theo code hiện tại; nếu thêm port hay adapter mới thì nhớ cập nhật `docs/hexagonal-architecture.svg`.

| Module | Vai trò | Thư viện chính |
|---|---|---|
| `domain` | Entity (Patient, Doctor, Appointment...), quy tắc (RedFlags, DrugInteractions), **port**: `ClinicRepository`, các port AI (`IntentClassifier`, `KnowledgeAssistant`, `SymptomTriage`, `BookingAssistant`, `MedicationAssistant`, `ConsultationSummarizer`, `TurnTracer`), use case `ConversationUseCase`. Kèm adapter in-memory để test. | không có |
| `langchain` | Cài đặt các port AI bằng LangChain4j: `AiServices`, `@Tool`, `ChatMemory`, RAG, guardrail, structured output (record Java), `ChatModelListener`. | LangChain4j 1.23 |
| `langgraph` | Cài đặt `ConversationUseCase` bằng LangGraph4j. **Không phụ thuộc `langchain`**: graph chỉ gọi port của domain, nên test được bằng port giả viết tay. | LangGraph4j 1.9.8 |
| `backend` | REST API JSON (http4s + circe), adapter Postgres (JDBC), đọc cấu hình, lắp ráp mọi thứ (`Wiring`), phục vụ file của frontend. | http4s, circe |
| `frontend` | 3 trang HTML + JS thuần (Tailwind qua CDN), chỉ gọi REST API. Không có code Scala. | không có |

## Tính năng và chỗ trong code

| # | Tính năng | LangChain4j | LangGraph4j |
|---|---|---|---|
| F1 | Hỏi đáp có trích nguồn (RAG) | `KnowledgeBase`, `FaqService` + `contentRetriever` | node `faq` |
| F2 | Sàng lọc triệu chứng nhiều lượt | structured output `SymptomReport`, `TriageResult` | subgraph `triage` (extract → ask/classify) |
| F3 | Đặt lịch, chờ xác nhận | `BookingTools` (`@Tool`, `@ToolMemoryId`) | `interruptBefore("confirm_booking")`, `updateState` + `resume` |
| F4 | Memory | `MessageWindowChatMemory`, `JdbcChatMemoryStore`, hồ sơ trong system message | checkpoint theo threadId (`MemorySaver` / `PostgresSaver`) |
| F5 | Tương tác thuốc | `MedicationTools`, `ProfileTools` | node `medication` |
| F6 | Supervisor | mỗi agent là một AiService | node `router` + conditional edges |
| F7 | Tóm tắt SOAP cho bác sĩ | structured output `ConsultationSummary` | node `summarize` |
| F8 | Panel quan sát | `ChatModelListener` (`ModelCallTracer`) | `stream()` theo node, channel `trace`, sơ đồ Mermaid tự sinh |
| F9 | Guardrail | `RedFlagInputGuardrail`, `MedicalSafetyOutputGuardrail` (reprompt) | node `guardrail` chạy trước mọi LLM |

## REST API

Session theo cookie `sid` (server tự gắn). Mỗi (session, bệnh nhân) là một thread của graph.

| Method | Path | Body | Trả về |
|---|---|---|---|
| GET | `/api/health` | | `{"status":"ok"}` |
| GET | `/api/patients` | | `[Patient]` |
| GET | `/api/patients/{id}` | | `Patient` hoặc 404 |
| GET | `/api/patients/{id}/pending` | | `{"pending": PendingBooking \| null}` |
| POST | `/api/chat` | `{"patientId":"BN001","message":"..."}` | `{"turn": TurnResult, "patient": Patient}`; 400 nếu body sai hoặc message rỗng; 404 nếu sai bệnh nhân |
| POST | `/api/booking/decision` | `{"patientId":"BN001","confirm":true}` | `{"turn": TurnResult, "patient": Patient}` |
| GET | `/api/appointments` | | `[AppointmentView]` kèm `summary` SOAP |
| GET | `/api/graph` | | `{"mermaid": "..."}` |

`TurnResult` = `reply`, `sources`, `emergency`, `pending`, `path` (các node đã chạy), `steps` (ghi chú từng node), `modelCalls`.

## Test

```bash
sbt test
```
- `domain`: red flag, tương tác thuốc.
- `langchain`: adapter LangChain4j với `FakeChatModel` (model giả có tool calling) + embedding thật: structured output, RAG, tool, guardrail, tracer.
- `langgraph`: graph với port AI giả viết tay: red flag, router, triage nhiều lượt, interrupt/resume, huỷ, tách thread.
- `backend` (automation test end-to-end, xem test specification):
  - `e2e/ApiSpec`: 19 case gọi mọi endpoint REST qua app thật (model giả, in-memory). `API-99` đọc route khai báo trong
    `ApiRoutes.scala`/`StaticRoutes.scala` và fail nếu có route chưa được gọi hoặc có phản hồi 5xx.
  - `e2e/ChatbotSpec`: 29 case gửi prompt qua `/api/chat`: router, RAG có trích dẫn, triage nhiều lượt, red flag,
    guardrail, đặt lịch interrupt/resume, thuốc, tách thread, SOAP, trace.
  - `e2e/LiveChatbotSpec`: 8 case với Claude thật, tự bỏ qua khi không có `ANTHROPIC_API_KEY`:
    `ANTHROPIC_API_KEY=sk-ant-... sbt "backend/testOnly *LiveChatbotSpec"`.
- UI smoke (Playwright, 8 case): chạy app với `LLM_PROVIDER=fake` rồi `node e2e/ui-smoke.js http://localhost:8081`.

## Những điều rút ra khi dùng 2 thư viện Java từ Scala

1. **Tên tham số của tool**: Scala 3 không ghi tên tham số vào bytecode, LangChain4j sẽ thấy `arg0`. Luôn viết `@P(name = "doctorId", value = "...")`.
2. **Annotation Java nhiều thuộc tính**: viết rõ `@Tool(value = Array(...))`, `@SystemMessage(value = Array(...))`; nếu không Scala có thể gán nhầm vào thuộc tính khác.
3. **Structured output**: dùng record Java (thư mục `langchain/src/main/java`) vì LangChain4j dùng Jackson; adapter chuyển sang case class của domain.
4. **Checkpoint của LangGraph4j 1.9**: mặc định xoá checkpoint khi graph chạy xong. Cần `releaseThread(false)` để giữ; khi đó lần `invoke` sau trên cùng thread kế thừa state cũ, nên phải tự đặt lại các khoá chỉ dùng trong một lượt (`PerTurnReset`).
5. **Node chạy trên thread khác**: muốn gắn lời gọi LLM vào đúng lượt chat thì truyền `turnId` qua state và gắn ThreadLocal trong từng node (`TurnTracer.inTurn`).
6. **Input guardrail chạy sau RAG**: tin nhắn đã bị nối thêm tài liệu, nên guardrail phải tách phần người dùng viết ra trước khi kiểm tra.

## Giới hạn hiện tại

- Embedding mặc định (all-MiniLM-L6-v2) được huấn luyện cho tiếng Anh, nên RAG tiếng Việt chỉ ở mức tạm. Dùng `EMBEDDING=ollama:bge-m3` để tốt hơn.
- Chưa chạy thử với Claude thật trong môi trường build (không có API key); mọi test dùng model giả.
- Adapter Postgres chưa có test tự động (môi trường build không có Docker).
