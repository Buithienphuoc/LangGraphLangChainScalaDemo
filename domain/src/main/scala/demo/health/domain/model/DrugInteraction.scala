package demo.health.domain.model

/** Một dòng trong bảng tương tác thuốc mẫu của [[DrugInteractions]]. */
final case class DrugInteraction(a: String, b: String, severity: String, note: String)
