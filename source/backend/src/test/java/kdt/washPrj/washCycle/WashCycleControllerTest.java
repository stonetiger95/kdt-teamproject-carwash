package kdt.washPrj.washCycle;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WashCycleControllerTest {

    private WashCycleService washCycleService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        washCycleService = mock(WashCycleService.class);
        WashCycleController controller = new WashCycleController();
        controller.service = washCycleService;
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void detectBindsMultipartRequestAndReturnsReservationResult()
            throws Exception {
        WashCycleResponseDTO response = new WashCycleResponseDTO();
        response.setAccepted(false);
        response.setReservationMatched(true);
        response.setReservationId(99L);
        response.setReservationStatus("MATCHED");
        response.setReservedDeviceId(2L);
        response.setAssignedDeviceId(8L);
        response.setDeviceId(2);
        response.setEventType("WASH_REDIRECT");
        response.setMessage("안내된 세차기로 이동해 주세요.");
        when(washCycleService.processWashCamera(argThat(
                request -> request != null
                        && Integer.valueOf(2).equals(request.getDeviceId())
        ))).thenReturn(response);

        MockMultipartFile image = new MockMultipartFile(
                "image",
                "vehicle.png",
                MediaType.IMAGE_PNG_VALUE,
                new byte[]{1, 2, 3}
        );

        mockMvc.perform(multipart("/api/wash-cycles/detect")
                        .file(image)
                        .param("deviceId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(false))
                .andExpect(jsonPath("$.reservationMatched").value(true))
                .andExpect(jsonPath("$.reservationId").value(99))
                .andExpect(jsonPath("$.assignedDeviceId").value(8))
                .andExpect(jsonPath("$.eventType").value("WASH_REDIRECT"));

        verify(washCycleService).processWashCamera(argThat(
                request -> "vehicle.png".equals(
                        request.getImage().getOriginalFilename()
                )
        ));
    }
}
