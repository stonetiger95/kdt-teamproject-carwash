package kdt.washPrj.weather;

import kdt.washPrj.weather.dto.WeatherResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/weather")
@RequiredArgsConstructor
public class WeatherController {

    private final WeatherService weatherService;

    @GetMapping
    public WeatherResponseDTO getForecast(
            @RequestParam(defaultValue = "서울")
            String location,

            @RequestParam(defaultValue = "60")
            int nx,

            @RequestParam(defaultValue = "127")
            int ny
    ) {
        return weatherService.getForecast(
                location,
                nx,
                ny
        );
    }
}