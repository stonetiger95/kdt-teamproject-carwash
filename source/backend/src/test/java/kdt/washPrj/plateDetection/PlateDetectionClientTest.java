package kdt.washPrj.plateDetection;

import kdt.washPrj.plateDetection.dto.PlateDetectionRequestDTO;
import kdt.washPrj.plateDetection.dto.PlateDetectionResultDTO;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlateDetectionClientTest {
    @Test
    void detectionConvertsUnreadableUploadToFailureWithoutHttpCall() throws Exception {
        RestClient restClient = mock(RestClient.class);
        MultipartFile image = mock(MultipartFile.class);
        when(image.getBytes()).thenThrow(new IOException("broken upload"));
        PlateDetectionRequestDTO request = new PlateDetectionRequestDTO();
        request.setImage(image);
        PlateDetectionClient client = new PlateDetectionClient();
        client.restClient = restClient;

        PlateDetectionResultDTO result = client.detection(request);

        assertFalse(result.getSuccess());
        assertNull(result.getDetectedPlateNumber());
        assertTrue(result.getMessage().contains("broken upload"));
        verifyNoInteractions(restClient);
    }
}
