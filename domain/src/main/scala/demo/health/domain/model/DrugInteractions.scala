package demo.health.domain.model



/** Bảng tương tác thuốc MẪU (F5). Chỉ để demo cách agent dùng tool, không dùng cho tư vấn thật. */
object DrugInteractions:
  final case class Interaction(a: String, b: String, severity: String, note: String)

  val table: List[Interaction] = List(
    Interaction("warfarin", "aspirin", "cao", "Tăng nguy cơ chảy máu."),
    Interaction("warfarin", "ibuprofen", "cao", "Tăng nguy cơ chảy máu, nhất là xuất huyết tiêu hoá."),
    Interaction("aspirin", "ibuprofen", "trung bình", "Ibuprofen có thể làm giảm tác dụng bảo vệ tim của aspirin liều thấp; tăng nguy cơ loét dạ dày."),
    Interaction("simvastatin", "clarithromycin", "cao", "Tăng nồng độ statin, nguy cơ tổn thương cơ."),
    Interaction("sildenafil", "nitroglycerin", "cao", "Có thể gây tụt huyết áp nặng."),
    Interaction("sertraline", "tramadol", "cao", "Nguy cơ hội chứng serotonin và co giật."),
    Interaction("enalapril", "spironolactone", "trung bình", "Nguy cơ tăng kali máu."),
    Interaction("clopidogrel", "omeprazole", "trung bình", "Omeprazole có thể làm giảm tác dụng của clopidogrel."),
    Interaction("levothyroxine", "calcium carbonate", "thấp", "Canxi làm giảm hấp thu levothyroxine; nên uống cách nhau vài giờ."),
    Interaction("ciprofloxacin", "antacid", "trung bình", "Thuốc kháng acid làm giảm hấp thu ciprofloxacin."),
    Interaction("metformin", "thuốc cản quang iod", "trung bình", "Có thể cần tạm ngưng metformin quanh thời điểm chụp có cản quang, theo chỉ định bác sĩ."),
    Interaction("paracetamol", "rượu", "trung bình", "Uống rượu thường xuyên làm tăng nguy cơ tổn thương gan khi dùng paracetamol."),
    Interaction("amlodipine", "simvastatin", "trung bình", "Amlodipine làm tăng nồng độ simvastatin; thường cần giới hạn liều statin."),
    Interaction("methotrexate", "trimethoprim", "cao", "Tăng độc tính trên tuỷ xương.")
  )

  def check(drugs: List[String]): List[Interaction] =
    val names = drugs.map(Text.normalize).map(_.trim).filter(_.nonEmpty).distinct
    def has(n: String) = names.exists(x => x.contains(Text.normalize(n)) || Text.normalize(n).contains(x))
    table.filter(i => has(i.a) && has(i.b))
