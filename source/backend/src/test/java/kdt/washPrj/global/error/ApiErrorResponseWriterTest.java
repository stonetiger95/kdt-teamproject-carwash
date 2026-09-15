package kdt.washPrj.global.error;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApiErrorResponseWriterTest {

    @Test
    void writeSetsStatusEncodingContentTypeAndJsonBody()
            throws Exception {
        JsonMapper jsonMapper = mock(JsonMapper.class);
        when(jsonMapper.writeValueAsString(any(ApiErrorResponse.class)))
                .thenReturn(
                        "{\"code\":\"UNAUTHORIZED\","
                                + "\"message\":\"로그인이 필요합니다.\","
                                + "\"fieldErrors\":{}}"
                );

        ApiErrorResponseWriter writer =
                new ApiErrorResponseWriter(jsonMapper);
        MockHttpServletResponse response =
                new MockHttpServletResponse();

        writer.write(
                response,
                HttpStatus.UNAUTHORIZED,
                "UNAUTHORIZED",
                "로그인이 필요합니다."
        );

        assertEquals(401, response.getStatus());
        assertEquals("UTF-8", response.getCharacterEncoding());
        assertTrue(response.getContentType().startsWith(
                MediaType.APPLICATION_JSON_VALUE
        ));
        assertEquals(
                "{\"code\":\"UNAUTHORIZED\","
                        + "\"message\":\"로그인이 필요합니다.\","
                        + "\"fieldErrors\":{}}",
                response.getContentAsString()
        );
    }
}
