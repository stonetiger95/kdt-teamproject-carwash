package kdt.washPrj.gate;

import kdt.washPrj.gate.dto.GateResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GateControllerTest {

    private GateService gateService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        gateService = mock(GateService.class);
        GateController controller = new GateController();
        controller.service = gateService;
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void entryBindsMultipartRequestAndReturnsAssignedDevice()
            throws Exception {
        GateResponseDTO response = new GateResponseDTO();
        response.setBarrierOpen(true);
        response.setReservationMatched(true);
        response.setReservationId(99L);
        response.setReservationStatus("MATCHED");
        response.setReservedDeviceId(2L);
        response.setAssignedDeviceId(8L);
        response.setEventType("ENTRY_OPEN");
        when(gateService.enter(argThat(
                request -> request != null
                        && Integer.valueOf(1).equals(request.getDeviceId())
        ))).thenReturn(response);

        MockMultipartFile image = new MockMultipartFile(
                "image",
                "vehicle.png",
                MediaType.IMAGE_PNG_VALUE,
                new byte[]{1, 2, 3}
        );

        mockMvc.perform(multipart("/api/gates/entry")
                        .file(image)
                        .param("deviceId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.barrierOpen").value(true))
                .andExpect(jsonPath("$.reservationMatched").value(true))
                .andExpect(jsonPath("$.reservationId").value(99))
                .andExpect(jsonPath("$.assignedDeviceId").value(8))
                .andExpect(jsonPath("$.eventType").value("ENTRY_OPEN"));
    }
}
