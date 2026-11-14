package demo.health.domain.model

/** Bảng tương tác thuốc MẪU (F5). Chỉ để demo cách agent dùng tool, không dùng cho tư vấn thật. */
object DrugInteractions:
  val table: List[DrugInteraction] = List(
    DrugInteraction("warfarin", "aspirin", "cao", "Tăng nguy cơ chảy máu."),
    DrugInteraction("warfarin", "ibuprofen", "cao", "Tăng nguy cơ chảy máu, nhất là xuất huyết tiêu hoá."),
    DrugInteraction("aspirin", "ibuprofen", "trung bình", "Ibuprofen có thể làm giảm tác dụng bảo vệ tim của aspirin liều thấp; tăng nguy cơ loét dạ dày."),
    DrugInteraction("simvastatin", "clarithromycin", "cao", "Tăng nồng độ statin, nguy cơ tổn thương cơ."),
    DrugInteraction("sildenafil", "nitroglycerin", "cao", "Có thể gây tụt huyết áp nặng."),
    DrugInteraction("sertraline", "tramadol", "cao", "Nguy cơ hội chứng serotonin và co giật."),
    DrugInteraction("enalapril", "spironolactone", "trung bình", "Nguy cơ tăng kali máu."),
    DrugInteraction("clopidogrel", "omeprazole", "trung bình", "Omeprazole có thể làm giảm tác dụng của clopidogrel."),
    DrugInteraction("levothyroxine", "calcium carbonate", "thấp", "Canxi làm giảm hấp thu levothyroxine; nên uống cách nhau vài giờ."),
    DrugInteraction("ciprofloxacin", "antacid", "trung bình", "Thuốc kháng acid làm giảm hấp thu ciprofloxacin."),
    DrugInteraction("metformin", "thuốc cản quang iod", "trung bình", "Có thể cần tạm ngưng metformin quanh thời điểm chụp có cản quang, theo chỉ định bác sĩ."),
    DrugInteraction("paracetamol", "rượu", "trung bình", "Uống rượu thường xuyên làm tăng nguy cơ tổn thương gan khi dùng paracetamol."),
    DrugInteraction("amlodipine", "simvastatin", "trung bình", "Amlodipine làm tăng nồng độ simvastatin; thường cần giới hạn liều statin."),
    DrugInteraction("methotrexate", "trimethoprim", "cao", "Tăng độc tính trên tuỷ xương.")
  )

  def check(drugs: List[String]): List[DrugInteraction] =
    val names = drugs.map(Text.normalize).map(_.trim).filter(_.nonEmpty).distinct
    def has(n: String) = names.exists(x => x.contains(Text.normalize(n)) || Text.normalize(n).contains(x))
    table.filter(i => has(i.a) && has(i.b))
