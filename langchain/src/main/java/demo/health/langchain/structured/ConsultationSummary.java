package demo.health.langchain.structured;

import dev.langchain4j.model.output.structured.Description;

/** Bản Java của kiểu domain cùng tên.  Tóm tắt SOAP cho bác sĩ (F7). */
public record ConsultationSummary(
        @Description("S - Bệnh nhân kể: triệu chứng, thời gian, mức độ") String subjective,
        @Description("O - Thông tin khách quan đã biết: tuổi, bệnh nền, thuốc, dị ứng") String objective,
        @Description("A - Nhận định sơ bộ của hệ thống sàng lọc, ghi rõ là gợi ý") String assessment,
        @Description("P - Kế hoạch: chuyên khoa, thời gian khám, việc cần chuẩn bị") String plan) {}
