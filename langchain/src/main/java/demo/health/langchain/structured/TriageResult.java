package demo.health.langchain.structured;

import dev.langchain4j.model.output.structured.Description;

public record TriageResult(
        @Description("Mức độ ưu tiên") Urgency urgency,
        @Description("Chuyên khoa phù hợp nhất, chọn một trong danh sách được cung cấp") String specialty,
        @Description("Giải thích ngắn gọn bằng tiếng Việt cho bệnh nhân, không chẩn đoán khẳng định") String explanation,
        @Description("1 đến 3 lời khuyên chăm sóc an toàn trong lúc chờ khám") java.util.List<String> advice) {}
