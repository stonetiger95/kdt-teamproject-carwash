package kdt.washPrj.weather;

import kdt.washPrj.weather.dto.KmaWeatherResponseDTO;
import kdt.washPrj.weather.dto.WeatherResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WeatherServiceTest {

    private static final ZoneId SEOUL_ZONE =
            ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd");

    private WeatherClient weatherClient;
    private WeatherService weatherService;

    @BeforeEach
    void setUp() {
        weatherClient = mock(WeatherClient.class);
        weatherService = new WeatherService(weatherClient);
    }

    @Test
    void getForecastConvertsKmaItemsToDailyForecast() {
        LocalDate today = LocalDate.now(SEOUL_ZONE);
        KmaWeatherResponseDTO response = createKmaResponse(List.of(
                item(today, "1200", "TMN", "24.0"),
                item(today, "1200", "TMX", "31.0"),
                item(today, "1200", "TMP", "27.0"),
                item(today, "1200", "SKY", "1"),
                item(today, "1200", "POP", "20"),
                item(today, "1200", "PTY", "0"),
                item(today, "1200", "PCP", "강수없음")
        ));
        when(weatherClient.getForecast(
                anyString(), anyString(), eq(60), eq(127)
        )).thenReturn(response);

        WeatherResponseDTO result =
                weatherService.getForecast("서울", 60, 127);

        assertEquals("서울", result.getLocation());
        assertEquals(
                Integer.valueOf(27),
                result.getCurrent().getTemperature()
        );
        assertEquals("맑음", result.getCurrent().getSky());
        assertEquals(1, result.getForecasts().size());
        assertEquals(today, result.getForecasts().get(0).getDate());
        assertEquals(
                Integer.valueOf(24),
                result.getForecasts().get(0).getMinTemperature()
        );
        assertEquals(
                Integer.valueOf(31),
                result.getForecasts().get(0).getMaxTemperature()
        );
        assertEquals("맑음", result.getForecasts().get(0).getSky());
        assertEquals(
                Integer.valueOf(20),
                result.getForecasts().get(0)
                        .getPrecipitationProbability()
        );
        assertEquals(
                "없음",
                result.getForecasts().get(0).getPrecipitationType()
        );
        assertEquals(
                "세차하기 좋아요",
                result.getForecasts().get(0).getWashCondition()
        );
        verify(weatherClient).getForecast(
                anyString(), anyString(), eq(60), eq(127)
        );
    }

    @Test
    void getForecastRecommendsPostponingWhenRainIsExpected() {
        LocalDate today = LocalDate.now(SEOUL_ZONE);
        KmaWeatherResponseDTO response = createKmaResponse(List.of(
                item(today, "1200", "TMP", "27"),
                item(today, "1200", "SKY", "4"),
                item(today, "1200", "POP", "70"),
                item(today, "1200", "PTY", "1"),
                item(today, "1200", "PCP", "1mm 미만")
        ));
        when(weatherClient.getForecast(
                anyString(), anyString(), eq(60), eq(127)
        )).thenReturn(response);

        WeatherResponseDTO result =
                weatherService.getForecast("서울", 60, 127);

        assertEquals(
                "비",
                result.getForecasts().get(0).getPrecipitationType()
        );
        assertEquals(
                Integer.valueOf(70),
                result.getForecasts().get(0)
                        .getPrecipitationProbability()
        );
        assertEquals(
                "세차를 미루는 게 좋아요",
                result.getForecasts().get(0).getWashCondition()
        );
    }

    @Test
    void getForecastThrowsBadGatewayWhenItemsAreMissing() {
        KmaWeatherResponseDTO response =
                new KmaWeatherResponseDTO();
        response.setResponse(
                new KmaWeatherResponseDTO.Response()
        );
        when(weatherClient.getForecast(
                anyString(), anyString(), anyInt(), anyInt()
        )).thenReturn(response);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> weatherService.getForecast(
                        "서울", 60, 127
                )
        );

        assertEquals(
                HttpStatus.BAD_GATEWAY,
                exception.getStatusCode()
        );
    }

    private KmaWeatherResponseDTO createKmaResponse(
            List<KmaWeatherResponseDTO.Item> items
    ) {
        KmaWeatherResponseDTO.Items itemContainer =
                new KmaWeatherResponseDTO.Items();
        itemContainer.setItem(items);

        KmaWeatherResponseDTO.Body body =
                new KmaWeatherResponseDTO.Body();
        body.setItems(itemContainer);

        KmaWeatherResponseDTO.Response innerResponse =
                new KmaWeatherResponseDTO.Response();
        innerResponse.setBody(body);

        KmaWeatherResponseDTO response =
                new KmaWeatherResponseDTO();
        response.setResponse(innerResponse);
        return response;
    }

    private KmaWeatherResponseDTO.Item item(
            LocalDate date,
            String time,
            String category,
            String value
    ) {
        KmaWeatherResponseDTO.Item item =
                new KmaWeatherResponseDTO.Item();
        item.setFcstDate(date.format(DATE_FORMATTER));
        item.setFcstTime(time);
        item.setCategory(category);
        item.setFcstValue(value);
        return item;
    }
}
