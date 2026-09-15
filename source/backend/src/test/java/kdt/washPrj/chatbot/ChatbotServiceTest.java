package kdt.washPrj.chatbot;

import kdt.washPrj.chatbot.dto.ChatbotRequestDTO;
import kdt.washPrj.chatbot.dto.ChatbotResponseDTO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatbotServiceTest {

    private final ChatbotKnowledgeBase knowledgeBase = new ChatbotKnowledgeBase();
    private final OpenAiChatbotClient openAiClient = mock(OpenAiChatbotClient.class);
    private final ChatbotToolService toolService = mock(ChatbotToolService.class);
    private final ChatbotFaqService faqService = mock(ChatbotFaqService.class);
    private final ChatbotMetrics metrics = mock(ChatbotMetrics.class);
    private final ChatbotService service = new ChatbotService(
            knowledgeBase,
            openAiClient,
            toolService,
            faqService,
            metrics
    );

    @Test
    void policyQuestionDoesNotCallOpenAi() {
        ChatbotResponseDTO response = service.reply(new ChatbotRequestDTO(
                "예약 시간이 아니어도 입차할 수 있나요?",
                List.of()
        ));

        assertEquals("TEMPLATE", response.source());
        verify(openAiClient, never()).respondWithTools(anyString(), any(), anyString(), any(), any());
    }

    @Test
    void anonymousReservationHowToQuestionShowsMemberAndGuestOptions() {
        ChatbotResponseDTO response = service.reply(new ChatbotRequestDTO(
                "예약은 어떻게 할 수 있어?",
                List.of()
        ));

        assertEquals("TEMPLATE", response.source());
        assertEquals(
                "예약은 회원가입 후 예약을 하시거나 비회원의 경우 화면의 비회원 예약을 클릭하여 이용하실 수 있습니다.",
                response.answer()
        );
        verify(openAiClient, never()).respondWithTools(anyString(), any(), anyString(), any(), any());
    }

    @Test
    void anonymousReservationChangeQuestionIsNotTreatedAsNewReservationGuide() {
        ChatbotResponseDTO response = service.reply(new ChatbotRequestDTO(
                "예약을 변경하거나 취소하려면 어떻게 하나요?",
                List.of()
        ));

        assertEquals("TEMPLATE", response.source());
        assertEquals(
                "회원의 경우 홈페이지 상단의 메뉴에서 세차 예약 선택 -> 내 예약 -> 예약 취소를 통해 "
                        + "예약 취소를 하신 후 새롭게 예약을 잡을 수 있습니다. "
                        + "비회원의 경우 홈화면의 비회원 예약 조회에 들어가신 후 정보를 입력하시고 "
                        + "예약 취소를 눌러 예약취소를 하시고 새로 예약을 잡으실 수 있습니다.",
                response.answer()
        );
        verify(openAiClient, never()).respondWithTools(anyString(), any(), anyString(), any(), any());
    }

    @Test
    void unknownQuestionUsesGeneratedAnswer() {
        when(openAiClient.respondWithTools(anyString(), any(), anyString(), any(), any()))
                .thenReturn(Optional.of(new OpenAiChatbotResult(
                        "안내 답변", 10, 5, 15, List.of()
                )));

        ChatbotResponseDTO response = service.reply(new ChatbotRequestDTO(
                "세차가 환경에 어떤 영향을 주나요?",
                List.of()
        ));

        assertEquals("GPT", response.source());
        assertEquals("안내 답변", response.answer());
    }

    @Test
    void personalIdentifiersAreRedactedBeforeModelCall() {
        String redacted = service.redact(
                "전화 010-1234-5678, email@example.com, 차량 12가3456"
        );

        assertFalse(redacted.contains("010-1234-5678"));
        assertFalse(redacted.contains("email@example.com"));
        assertFalse(redacted.contains("12가3456"));
    }

    @Test
    void anonymousCustomerDataQuestionRequiresLogin() {
        when(toolService.detectTool("내 예약 알려줘"))
                .thenReturn(Optional.of(ChatbotToolService.GET_MY_RESERVATIONS));

        ChatbotResponseDTO response = service.reply(new ChatbotRequestDTO(
                "내 예약 알려줘", List.of()
        ));

        assertEquals("TOOL", response.source());
        assertEquals(true, response.answer().contains("로그인"));
        verify(toolService, never()).execute(anyString(), any());
    }

    @Test
    void nearbyWashSiteQuestionPassesBrowserLocationToPublicTool() {
        when(toolService.answerPublicQuestion("근처 세차장", 35.1521, 129.0594))
                .thenReturn(Optional.of(new ChatbotToolResult(
                        ChatbotToolService.GET_NEARBY_WASH_SITES,
                        "세차장 목록",
                        "세차장 목록"
                )));

        ChatbotResponseDTO response = service.reply(new ChatbotRequestDTO(
                "근처 세차장",
                List.of(),
                35.1521,
                129.0594
        ));

        assertEquals("WASH_SITE", response.source());
        assertEquals("세차장 목록", response.answer());
        verify(toolService).answerPublicQuestion("근처 세차장", 35.1521, 129.0594);
    }
}
