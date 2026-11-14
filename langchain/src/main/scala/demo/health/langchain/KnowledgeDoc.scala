package demo.health.langchain

/** Một tài liệu mẫu được nạp vào RAG bởi [[KnowledgeBase]]. */
final case class KnowledgeDoc(docType: String, file: String, title: String, text: String)
