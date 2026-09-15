package kdt.washPrj.address;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JusoAddressControllerTest {

    private static final String POPUP_URL =
            "https://business.juso.go.kr/addrlink/addrLinkUrl.do";

    @Test
    void popupSubmitsServerConfigurationToOfficialJusoEndpoint() {
        JusoAddressController controller = controller(true, "test-key");

        ResponseEntity<String> response = controller.openPopup();
        String body = response.getBody();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(body);
        assertTrue(body.contains("action=\"" + POPUP_URL + "\""));
        assertTrue(body.contains("name=\"confmKey\" value=\"test-key\""));
        assertTrue(body.contains(
                "value=\"http://localhost:8080/api/public/address/juso/callback\""
        ));
        assertTrue(body.contains("name=\"resultType\" value=\"4\""));
    }

    @Test
    void disabledPopupDoesNotExposeApprovalKey() {
        JusoAddressController controller = controller(false, "secret-key");

        ResponseEntity<String> response = controller.openPopup();
        String body = response.getBody();

        assertEquals(
                HttpStatus.SERVICE_UNAVAILABLE,
                response.getStatusCode()
        );
        assertNotNull(body);
        assertFalse(body.contains("secret-key"));
    }

    @Test
    void callbackReturnsOnlyWhitelistedAddressToFrontendOrigin() {
        JusoAddressController controller = controller(true, "test-key");
        MultiValueMap<String, String> parameters =
                new LinkedMultiValueMap<>();
        parameters.add("roadFullAddr", "서울특별시 중구 세종대로 110");
        parameters.add("roadAddrPart1", "서울특별시 중구 세종대로 110");
        parameters.add("roadAddrPart2", "(태평로1가)");
        parameters.add("addrDetail", "3층");
        parameters.add("jibunAddr", "서울특별시 중구 태평로1가 31");
        parameters.add("zipNo", "04524");
        parameters.add("unusedField", "must-not-be-forwarded");

        ResponseEntity<String> response =
                controller.handleCallback(parameters);
        String body = response.getBody();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(body);
        assertTrue(body.contains(
                JusoAddressController.ADDRESS_SELECTED_MESSAGE
        ));
        assertTrue(body.contains("서울특별시 중구 세종대로 110"));
        assertTrue(body.contains("04524"));
        assertTrue(body.contains("http://localhost:5173"));
        assertFalse(body.contains("must-not-be-forwarded"));
    }

    @Test
    void callbackRejectsResultWithoutRoadAddress() {
        JusoAddressController controller = controller(true, "test-key");

        ResponseEntity<String> response = controller.handleCallback(
                new LinkedMultiValueMap<>()
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains(
                JusoAddressController.ADDRESS_ERROR_MESSAGE
        ));
    }

    private JusoAddressController controller(
            boolean enabled,
            String approvalKey
    ) {
        return new JusoAddressController(
                JsonMapper.builder().build(),
                enabled,
                approvalKey,
                POPUP_URL,
                "http://localhost:8080/",
                "http://localhost:5173/customer-signup"
        );
    }
}
