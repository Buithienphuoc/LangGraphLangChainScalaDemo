package demo.health.langchain

import dev.langchain4j.data.document.{Document, Metadata}
import dev.langchain4j.data.document.splitter.DocumentSplitters
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.rag.content.retriever.{ContentRetriever, EmbeddingStoreContentRetriever}
import dev.langchain4j.store.embedding.{EmbeddingStore, EmbeddingStoreIngestor}
import scala.io.Source
import scala.jdk.CollectionConverters.*

/** RAG (F1): nạp tài liệu mẫu, chia chunk, embed, lưu vào EmbeddingStore và tạo ContentRetriever. */
object KnowledgeBase:
  def loadDocs(): List[KnowledgeDoc] =
    val cl = getClass.getClassLoader
    Source.fromResource("docs/index.txt", cl).getLines().map(_.trim).filter(_.nonEmpty).map { line =>
      val Array(docType, file) = line.split('|')
      val text  = Source.fromResource(s"docs/$file", cl).mkString
      val title = text.linesIterator.find(_.startsWith("# ")).map(_.drop(2).trim).getOrElse(file)
      KnowledgeDoc(docType, file, title, text)
    }.toList

  /** Indexing: Document → chunk ~500 ký tự (chồng lấn 80) → embedding → store. Trả về số chunk. */
  def ingest(store: EmbeddingStore[TextSegment], model: EmbeddingModel): Int =
    val documents = loadDocs().map { d =>
      Document.from(d.text, Metadata.from("source", d.file).put("title", d.title).put("doc_type", d.docType))
    }
    val ingestor = EmbeddingStoreIngestor.builder()
      .documentSplitter(DocumentSplitters.recursive(500, 80))
      .embeddingModel(model)
      .embeddingStore(store)
      .build()
    ingestor.ingest(documents.asJava)
    documents.map(d => DocumentSplitters.recursive(500, 80).split(d).size).sum

  def retriever(store: EmbeddingStore[TextSegment], model: EmbeddingModel): ContentRetriever =
    EmbeddingStoreContentRetriever.builder()
      .embeddingStore(store)
      .embeddingModel(model)
      .maxResults(4)
      .minScore(0.25) // MiniLM là model tiếng Anh nên điểm với tiếng Việt thấp; đổi sang bge-m3 thì nâng lên
      .build()
