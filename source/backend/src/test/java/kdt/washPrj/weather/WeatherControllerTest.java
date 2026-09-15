package kdt.washPrj.weather;

import kdt.washPrj.weather.dto.DailyWeatherDTO;
import kdt.washPrj.weather.dto.CurrentWeatherDTO;
import kdt.washPrj.weather.dto.WeatherResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WeatherControllerTest {

    private WeatherService weatherService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        weatherService = mock(WeatherService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new WeatherController(weatherService)
        ).build();
    }

    @Test
    void getForecastUsesDefaultSeoulGrid() throws Exception {
        when(weatherService.getForecast(
                "서울", 60, 127
        )).thenReturn(response("서울"));

        mockMvc.perform(get("/api/public/weather"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.location").value("서울"))
                .andExpect(jsonPath("$.current.temperature")
                        .value(27))
                .andExpect(jsonPath("$.forecasts[0].sky")
                        .value("맑음"))
                .andExpect(jsonPath(
                        "$.forecasts[0].precipitationProbability"
                ).value(20));

        verify(weatherService).getForecast(
                "서울", 60, 127
        );
    }

    @Test
    void getForecastPassesRequestedGrid() throws Exception {
        when(weatherService.getForecast(
                "강남구", 61, 126
        )).thenReturn(response("강남구"));

        mockMvc.perform(get("/api/public/weather")
                        .param("location", "강남구")
                        .param("nx", "61")
                        .param("ny", "126"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.location")
                        .value("강남구"));

        verify(weatherService).getForecast(
                "강남구", 61, 126
        );
    }

    private WeatherResponseDTO response(String location) {
        DailyWeatherDTO forecast = new DailyWeatherDTO(
                LocalDate.of(2026, 8, 10),
                24,
                31,
                "맑음",
                20,
                "없음",
                "강수없음",
                "세차하기 좋아요"
        );

        WeatherResponseDTO response =
                new WeatherResponseDTO();
        response.setLocation(location);
        response.setBaseDate("20260810");
        response.setBaseTime("1400");
        CurrentWeatherDTO current = new CurrentWeatherDTO();
        current.setForecastDate(LocalDate.of(2026, 8, 10));
        current.setForecastTime("1400");
        current.setTemperature(27);
        current.setSky("맑음");
        current.setPrecipitationProbability(20);
        current.setPrecipitationType("없음");
        current.setPrecipitationAmount("강수없음");
        response.setCurrent(current);
        response.setForecasts(List.of(forecast));
        return response;
    }
}
