package demo.health.domain.port


// Port ra (driven) cho các năng lực AI. Adapter `langchain` cài đặt chúng bằng LangChain4j.
// Phần điều phối (`langgraph`) chỉ biết các trait này, nên có thể test graph bằng cài đặt giả viết tay.

/** Gom tất cả port AI để truyền vào phần điều phối. */
final case class AiPorts(
    intents: IntentClassifier,
    knowledge: KnowledgeAssistant,
    triage: SymptomTriage,
    booking: BookingAssistant,
    medication: MedicationAssistant,
    summarizer: ConsultationSummarizer,
    tracer: TurnTracer
)
