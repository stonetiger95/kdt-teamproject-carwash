package kdt.washPrj.weather.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class DailyWeatherDTO {

    private LocalDate date;
    private Integer minTemperature;
    private Integer maxTemperature;
    private String sky;
    private Integer precipitationProbability;
    private String precipitationType;
    private String precipitationAmount;
    private String washCondition;

    public DailyWeatherDTO() {
    }

    public DailyWeatherDTO(
            LocalDate date,
            Integer minTemperature,
            Integer maxTemperature,
            String sky,
            Integer precipitationProbability,
            String precipitationType,
            String precipitationAmount,
            String washCondition
    ) {
        this.date = date;
        this.minTemperature = minTemperature;
        this.maxTemperature = maxTemperature;
        this.sky = sky;
        this.precipitationProbability = precipitationProbability;
        this.precipitationType = precipitationType;
        this.precipitationAmount = precipitationAmount;
        this.washCondition = washCondition;
    }

}
