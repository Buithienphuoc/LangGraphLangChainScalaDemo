package demo.health.domain.model

/** Tóm tắt SOAP cho bác sĩ (F7). */
final case class ConsultationSummary(subjective: String, objective: String, assessment: String, plan: String)
