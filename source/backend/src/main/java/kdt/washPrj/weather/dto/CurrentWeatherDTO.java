package kdt.washPrj.weather.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class CurrentWeatherDTO {

    private LocalDate forecastDate;
    private String forecastTime;
    private Integer temperature;
    private String sky;
    private Integer precipitationProbability;
    private String precipitationType;
    private String precipitationAmount;
}
