package kdt.washPrj.weather.dto;

import lombok.Data;

import java.util.List;

@Data
public class WeatherResponseDTO {

    private String location;
    private String baseDate;
    private String baseTime;
    private CurrentWeatherDTO current;
    private List<DailyWeatherDTO> forecasts;
}
