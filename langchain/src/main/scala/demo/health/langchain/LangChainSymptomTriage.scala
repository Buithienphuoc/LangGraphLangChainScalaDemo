package demo.health.langchain

import demo.health.domain.model.*
import demo.health.domain.port.SymptomTriage
import demo.health.langchain.structured as js
import scala.jdk.CollectionConverters.*

/** Cài đặt port SymptomTriage bằng hai AiService của LangChain4j (trích triệu chứng và phân loại mức độ). */
final class LangChainSymptomTriage(extractor: SymptomExtractorService, classifier: TriageClassifierService)
    extends SymptomTriage:

  def extract(conversation: String): SymptomReport =
    val r = extractor.extract(conversation)
    SymptomReport(list(r.symptoms()), Option(r.duration()).getOrElse(""), r.severity(), r.age(), list(r.missingInfo()))

  def classify(specialties: List[String], report: String): TriageResult =
    val r = classifier.classify(specialties.mkString(", "), report)
    val urgency = r.urgency() match
      case js.Urgency.SELF_CARE       => Urgency.SelfCare
      case js.Urgency.VISIT_THIS_WEEK => Urgency.VisitThisWeek
      case _                          => Urgency.VisitSoon
    TriageResult(urgency, r.specialty(), Option(r.explanation()).getOrElse(""), list(r.advice()))

  private def list[A](xs: java.util.List[A]): List[A] = Option(xs).map(_.asScala.toList).getOrElse(Nil)
