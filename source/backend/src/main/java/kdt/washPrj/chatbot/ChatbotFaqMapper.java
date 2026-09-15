package kdt.washPrj.chatbot;

import kdt.washPrj.chatbot.dto.ChatbotFaqDTO;
import kdt.washPrj.chatbot.dto.ChatbotFaqRequestDTO;
import kdt.washPrj.chatbot.dto.ChatbotUnresolvedQuestionDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ChatbotFaqMapper {

    @Select("SELECT * FROM app_api.get_enabled_chatbot_faqs()")
    List<ChatbotFaqDTO> selectEnabledFaqs();

    @Select("SELECT app_api.record_chatbot_unresolved_question(#{question}, #{reason})")
    Long recordUnresolved(
            @Param("question") String question,
            @Param("reason") String reason
    );

    @Select("SELECT * FROM app_api.get_admin_chatbot_faqs(#{loginId})")
    List<ChatbotFaqDTO> selectAdminFaqs(@Param("loginId") String loginId);

    @Select("""
        SELECT app_api.save_admin_chatbot_faq(
            #{loginId}, #{faqId}, #{request.question}, #{request.answer},
            #{request.keywords}, #{request.enabled}, #{request.displayOrder}
        )
    """)
    Long saveAdminFaq(
            @Param("loginId") String loginId,
            @Param("faqId") Long faqId,
            @Param("request") ChatbotFaqRequestDTO request
    );

    @Select("SELECT app_api.delete_admin_chatbot_faq(#{loginId}, #{faqId})")
    Boolean deleteAdminFaq(
            @Param("loginId") String loginId,
            @Param("faqId") Long faqId
    );

    @Select("SELECT * FROM app_api.get_admin_chatbot_unresolved_questions(#{loginId})")
    List<ChatbotUnresolvedQuestionDTO> selectUnresolved(@Param("loginId") String loginId);

    @Select("SELECT app_api.resolve_admin_chatbot_question(#{loginId}, #{questionId})")
    Boolean resolveUnresolved(
            @Param("loginId") String loginId,
            @Param("questionId") Long questionId
    );
}
