package kdt.washPrj.weather;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "weather.kma")
public class WeatherProperties {

    private boolean enabled;
    private String apiUrl;
    private String serviceKey;
}