package kdt.washPrj.washsite;

import kdt.washPrj.washsite.dto.DrivingRouteResponseDTO;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NaverDirectionsClientTest {

    private final NaverDirectionsClient client = new NaverDirectionsClient(
            JsonMapper.builder().build(),
            "https://example.com/driving",
            "client-id",
            "client-secret"
    );

    @Test
    void parseResponseReturnsSummaryAndRoadPath() throws Exception {
        DrivingRouteResponseDTO result = client.parseResponse("""
                {
                  "code": 0,
                  "message": "Route finding succeeded.",
                  "currentDateTime": "2026-08-25T17:00:00",
                  "route": {
                    "traoptimal": [{
                      "summary": {
                        "distance": 1250,
                        "duration": 180000,
                        "tollFare": 0
                      },
                      "path": [
                        [127.1000, 37.5000],
                        [127.1100, 37.5100]
                      ],
                      "guide": []
                    }]
                  }
                }
                """);

        assertEquals(1250, result.distanceMeters());
        assertEquals(180000, result.durationMillis());
        assertEquals(2, result.path().size());
        assertEquals(37.5000, result.path().get(0).latitude());
        assertEquals(127.1000, result.path().get(0).longitude());
    }

    @Test
    void parseResponseRejectsNoRouteResult() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> client.parseResponse("""
                        {
                          "code": 3,
                          "message": "Unable to provide directions.",
                          "route": {}
                        }
                        """)
        );

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, exception.getStatusCode());
    }
}
