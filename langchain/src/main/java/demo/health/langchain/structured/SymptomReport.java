package demo.health.langchain.structured;

import dev.langchain4j.model.output.structured.Description;
import java.util.List;

public record SymptomReport(
        @Description("Danh sách triệu chứng chính, ngắn gọn") List<String> symptoms,
        @Description("Đã kéo dài bao lâu, ví dụ '2 ngày'; để trống nếu chưa biết") String duration,
        @Description("Mức độ từ 1 (nhẹ) đến 10 (rất nặng); 0 nếu chưa biết") int severity,
        @Description("Tuổi người bệnh; 0 nếu chưa biết") int age,
        @Description("Những thông tin quan trọng còn thiếu để sàng lọc (tối đa 2), rỗng nếu đã đủ") List<String> missingInfo) {

    public boolean isComplete() {
        return symptoms != null && !symptoms.isEmpty() && (missingInfo == null || missingInfo.isEmpty());
    }
}
