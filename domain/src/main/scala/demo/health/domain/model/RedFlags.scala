package demo.health.domain.model

/** Quy tắc nghiệp vụ: phát hiện dấu hiệu khẩn cấp bằng luật (không dùng LLM), nên luôn chạy được và
  * kiểm thử được. Danh sách chỉ phục vụ demo, không phải tiêu chuẩn y khoa.
  */
object RedFlags:
  final case class Hit(label: String)

  // (nhãn hiển thị, các cụm từ đã bỏ dấu)
  private val rules: List[(String, List[String])] = List(
    "Đau ngực"             -> List("dau nguc", "tuc nguc", "dau that nguc", "chest pain"),
    "Khó thở"              -> List("kho tho", "ngat tho", "tho khong noi", "khong tho duoc"),
    "Dấu hiệu đột quỵ"     -> List("yeu nua nguoi", "liet nua nguoi", "meo mieng", "noi ngong", "dot quy"),
    "Ngất, lơ mơ"          -> List("ngat xiu", "bat tinh", "lo mo", "co giat"),
    "Chảy máu nhiều"       -> List("chay mau nhieu", "non ra mau", "oi ra mau", "di ngoai ra mau"),
    "Ý định tự hại"        -> List("tu tu", "muon chet", "tu hai", "ket lieu"),
    "Phản vệ / sốc dị ứng" -> List("sung hong", "sung moi", "phan ve"),
    "Sốt cao ở trẻ nhỏ"    -> List("tre so sinh sot", "be sot cao", "sot 40", "sot tren 40")
  )

  def detect(text: String): List[Hit] =
    val t = Text.normalize(text)
    rules.collect { case (label, keys) if keys.exists(t.contains) => Hit(label) }

  val emergencyMessage: String =
    """⚠️ Những gì bạn mô tả có thể là tình huống khẩn cấp.
      |Hãy gọi ngay 115 hoặc đến cơ sở cấp cứu gần nhất. Không tự lái xe nếu bạn đang thấy không ổn.
      |Trợ lý demo này không thể xử lý trường hợp khẩn cấp.""".stripMargin
