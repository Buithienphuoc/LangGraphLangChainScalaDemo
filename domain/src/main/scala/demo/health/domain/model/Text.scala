package demo.health.domain.model

import java.time.LocalDateTime

object Text:
  /** Bỏ dấu tiếng Việt và viết thường, để so khớp từ khoá không phụ thuộc cách gõ. */
  def normalize(s: String): String =
    java.text.Normalizer.normalize(s.toLowerCase, java.text.Normalizer.Form.NFD)
      .replaceAll("\\p{M}", "").replace('đ', 'd')

  private val timeFmt = java.time.format.DateTimeFormatter.ofPattern("EEEE dd/MM/yyyy HH:mm", java.util.Locale.forLanguageTag("vi"))
  def time(t: LocalDateTime): String = t.format(timeFmt)
