# Health care demo: LangChain4j + LangGraph4j bằng Scala

Website "phòng khám ảo" có trợ lý AI, dùng để học cách dùng **LangChain4j** (gọi LLM, tool, memory, RAG,
guardrail) và **LangGraph4j** (graph workflow, rẽ nhánh, subgraph, checkpoint, human-in-the-loop) từ Scala 3.

> Dữ liệu (bệnh nhân, bác sĩ, lịch, tài liệu y tế) đều là dữ liệu giả. Đây là demo kỹ thuật, không phải sản phẩm y tế.

## Kiến trúc: Hexagonal, 5 module sbt

```
                 ┌──────────────┐
   frontend ───► │   backend    │  adapter vào: REST API · adapter ra: Postgres · composition root
  (HTML + JS)    └──────┬───────┘
                        │
          ┌─────────────┼──────────────┐
          ▼             ▼              ▼
     langgraph      langchain        domain   ◄── lõi: model + port, không phụ thuộc thư viện nào
   (điều phối)     (adapter AI)
          └──────► domain ◄──────┘
```

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

## Chạy thử

Cần JDK 21 và sbt 1.11.

**1. Không cần API key (model giả, dữ liệu in-memory)** — để xem giao diện và luồng graph:
```bash
sbt "backend/Test/runMain demo.health.backend.DevServer"
# mở http://localhost:8080
```
Model giả trả lời theo luật đơn giản (từ khoá "đặt lịch", "thuốc", "mở cửa", "đau"...), nên câu trả lời không thông minh,
nhưng mọi node, tool, interrupt và panel quan sát đều chạy thật.

**2. Với Claude (in-memory):**
```bash
export ANTHROPIC_API_KEY=sk-ant-...
sbt backend/run
```

**3. Với Ollama (chạy offline):**
```bash
LLM_PROVIDER=ollama OLLAMA_MODEL=qwen2.5:7b EMBEDDING=ollama:bge-m3 sbt backend/run
```

**4. Đầy đủ với Postgres + pgvector (Docker):**
```bash
cp .env.example .env   # điền ANTHROPIC_API_KEY
docker compose up --build
```

| Biến môi trường | Mặc định | Ý nghĩa |
|---|---|---|
| `LLM_PROVIDER` | `anthropic` | `anthropic` hoặc `ollama` |
| `ANTHROPIC_API_KEY`, `ANTHROPIC_MODEL` | , `claude-sonnet-5-5` | |
| `OLLAMA_BASE_URL`, `OLLAMA_MODEL` | `http://localhost:11434`, `qwen2.5:7b` | |
| `EMBEDDING` | `local` | `local` = all-MiniLM-L6-v2 in-process; `ollama:<model>` ví dụ `ollama:bge-m3` |
| `STORAGE` | `memory` | `memory` hoặc `postgres` |
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` | `localhost`, `5432`, `healthcare` × 3 | |
| `PORT` | `8080` | |

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
- UI smoke (Playwright, 8 case): chạy DevServer rồi `node e2e/ui-smoke.js http://localhost:8080`.

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
