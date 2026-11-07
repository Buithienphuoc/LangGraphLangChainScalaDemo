package demo.health.langchain.structured;

/** Ý định của người dùng, do IntentRouter (LLM) phân loại. Enum Java để LangChain4j sinh JSON schema. */
public enum Intent {
    FAQ,          // hỏi thông tin phòng khám hoặc kiến thức sức khoẻ
    TRIAGE,       // mô tả triệu chứng, muốn biết nên làm gì
    BOOKING,      // đặt, xem, huỷ lịch khám
    MEDICATION,   // hỏi về thuốc, tương tác thuốc, cập nhật thuốc đang dùng
    OTHER         // ngoài phạm vi y tế
}
