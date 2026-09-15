package kdt.washPrj.chatbot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatbotKnowledgeBaseTest {

    private final ChatbotKnowledgeBase knowledgeBase = new ChatbotKnowledgeBase();

    @Test
    void reservationTimeDoesNotRestrictEntry() {
        String answer = knowledgeBase.answer("예약 시간이 아니어도 입차할 수 있나요?")
                .orElseThrow();

        assertTrue(answer.contains("예약 시간과 관계없이"));
        assertTrue(answer.contains("세차를 이용할 수 있습니다"));
    }

    @Test
    void logoutDoesNotAskSocialMemberForPassword() {
        String answer = knowledgeBase.answer("SNS 회원은 어떻게 로그아웃해요?")
                .orElseThrow();

        assertTrue(answer.contains("비밀번호 없이"));
    }

    @Test
    void unknownQuestionIsDelegatedToGptPath() {
        assertEquals(false, knowledgeBase.answer("세차가 환경에 어떤 영향을 주나요?").isPresent());
    }

    @Test
    void paymentRequestExplainsWhereToPayWithoutPerformingPayment() {
        String answer = knowledgeBase.answer("결제하고 싶어요").orElseThrow();

        assertTrue(answer.contains("결제를 직접 처리하지 않습니다"));
        assertTrue(answer.contains("비회원 세차 조회·결제"));
    }

    @Test
    void reservationChangeQuestionExplainsCancelThenReserveFlow() {
        String answer = knowledgeBase.answer("예약을 변경하거나 취소하려면 어떻게 하나요?")
                .orElseThrow();

        assertEquals(
                "회원의 경우 홈페이지 상단의 메뉴에서 세차 예약 선택 -> 내 예약 -> 예약 취소를 통해 "
                        + "예약 취소를 하신 후 새롭게 예약을 잡을 수 있습니다. "
                        + "비회원의 경우 홈화면의 비회원 예약 조회에 들어가신 후 정보를 입력하시고 "
                        + "예약 취소를 눌러 예약취소를 하시고 새로 예약을 잡으실 수 있습니다.",
                answer
        );
    }
}
