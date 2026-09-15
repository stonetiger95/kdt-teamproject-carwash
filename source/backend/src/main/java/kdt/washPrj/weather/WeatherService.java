package kdt.washPrj.weather;

import kdt.washPrj.weather.dto.DailyWeatherDTO;
import kdt.washPrj.weather.dto.CurrentWeatherDTO;
import kdt.washPrj.weather.dto.KmaWeatherResponseDTO;
import kdt.washPrj.weather.dto.WeatherResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WeatherService {

    private final WeatherClient weatherClient;

    private static final List<LocalTime> BASE_TIMES = List.of(
            LocalTime.of(2, 0),
            LocalTime.of(5, 0),
            LocalTime.of(8, 0),
            LocalTime.of(11, 0),
            LocalTime.of(14, 0),
            LocalTime.of(17, 0),
            LocalTime.of(20, 0),
            LocalTime.of(23, 0)
    );
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd");

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HHmm");
    private static final DateTimeFormatter FORECAST_DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    public WeatherResponseDTO getForecast(
            String location,
            int nx,
            int ny
    ) {
        LocalDateTime baseDateTime = resolveBaseDateTime();

        String baseDate = baseDateTime.format(DATE_FORMATTER);
        String baseTime = baseDateTime.format(TIME_FORMATTER);

        KmaWeatherResponseDTO kmaResponse =
                weatherClient.getForecast(
                        baseDate,
                        baseTime,
                        nx,
                        ny
                );

        List<KmaWeatherResponseDTO.Item> items =
                extractItems(kmaResponse);

        Map<LocalDate, List<KmaWeatherResponseDTO.Item>> itemsByDate =
                items.stream()
                        .collect(Collectors.groupingBy(
                                item -> LocalDate.parse(
                                        item.getFcstDate(),
                                        DATE_FORMATTER
                                ),
                                TreeMap::new,
                                Collectors.toList()
                        ));
        LocalDate today = LocalDate.now(
                ZoneId.of("Asia/Seoul")
        );

        List<DailyWeatherDTO> forecasts = itemsByDate.entrySet()
                .stream()
                .filter(entry -> !entry.getKey().isBefore(today))
                .limit(3)
                .map(entry -> createDailyWeather(
                        entry.getKey(),
                        entry.getValue()
                ))
                .toList();

        WeatherResponseDTO result = new WeatherResponseDTO();
        result.setLocation(location);
        result.setBaseDate(baseDate);
        result.setBaseTime(baseTime);
        result.setCurrent(createCurrentWeather(
                items,
                LocalDateTime.now(ZoneId.of("Asia/Seoul"))
        ));
        result.setForecasts(forecasts);

        return result;
    }

    private CurrentWeatherDTO createCurrentWeather(
            List<KmaWeatherResponseDTO.Item> items,
            LocalDateTime now
    ) {
        Optional<LocalDateTime> closestForecastTime = items.stream()
                .filter(item -> "TMP".equals(item.getCategory()))
                .map(this::parseForecastDateTime)
                .flatMap(Optional::stream)
                .min(Comparator.comparingLong(forecastTime ->
                        Math.abs(Duration.between(now, forecastTime).toMinutes())
                ));

        if (closestForecastTime.isEmpty()) {
            return null;
        }

        LocalDateTime selectedTime = closestForecastTime.get();
        String selectedDate = selectedTime.format(DATE_FORMATTER);
        String selectedClock = selectedTime.format(TIME_FORMATTER);

        Integer temperature = null;
        Integer precipitationProbability = null;
        String skyCode = null;
        String precipitationTypeCode = "0";
        String precipitationAmount = "강수없음";

        for (KmaWeatherResponseDTO.Item item : items) {
            if (!selectedDate.equals(item.getFcstDate())
                    || !selectedClock.equals(item.getFcstTime())) {
                continue;
            }

            String category = item.getCategory();
            String value = item.getFcstValue();
            if (category == null || value == null) {
                continue;
            }

            switch (category) {
                case "TMP" -> temperature = parseNumber(value);
                case "SKY" -> skyCode = value;
                case "POP" -> precipitationProbability = parseNumber(value);
                case "PTY" -> precipitationTypeCode = value;
                case "PCP" -> precipitationAmount = value;
            }
        }

        CurrentWeatherDTO current = new CurrentWeatherDTO();
        current.setForecastDate(selectedTime.toLocalDate());
        current.setForecastTime(selectedClock);
        current.setTemperature(temperature);
        current.setSky(convertSky(skyCode));
        current.setPrecipitationProbability(
                precipitationProbability == null ? 0 : precipitationProbability
        );
        current.setPrecipitationType(
                convertPrecipitationType(precipitationTypeCode)
        );
        current.setPrecipitationAmount(precipitationAmount);
        return current;
    }

    private Optional<LocalDateTime> parseForecastDateTime(
            KmaWeatherResponseDTO.Item item
    ) {
        if (item.getFcstDate() == null || item.getFcstTime() == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(LocalDateTime.parse(
                    item.getFcstDate() + item.getFcstTime(),
                    FORECAST_DATE_TIME_FORMATTER
            ));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private List<KmaWeatherResponseDTO.Item> extractItems(
            KmaWeatherResponseDTO response
    ) {
        if (response == null
                || response.getResponse() == null
                || response.getResponse().getBody() == null
                || response.getResponse().getBody().getItems() == null
                || response.getResponse().getBody().getItems().getItem() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "기상청 예보 데이터가 비어 있습니다."
            );
        }

        return response.getResponse()
                .getBody()
                .getItems()
                .getItem();
    }

    private LocalDateTime resolveBaseDateTime() {
        LocalDateTime availableTime = LocalDateTime
                .now(ZoneId.of("Asia/Seoul"))
                .minusMinutes(10);

        LocalTime selectedTime = null;

        for (LocalTime baseTime : BASE_TIMES) {
            if (!baseTime.isAfter(availableTime.toLocalTime())) {
                selectedTime = baseTime;
            }
        }

        if (selectedTime == null) {
            return availableTime
                    .toLocalDate()
                    .minusDays(1)
                    .atTime(23, 0);
        }

        return availableTime
                .toLocalDate()
                .atTime(selectedTime);
    }
    private DailyWeatherDTO createDailyWeather(
            LocalDate date,
            List<KmaWeatherResponseDTO.Item> items
    ) {
        Integer minTemperature = null;
        Integer maxTemperature = null;

        Integer hourlyMinTemperature = null;
        Integer hourlyMaxTemperature = null;

        int precipitationProbability = 0;

        String skyCode = null;
        Integer closestSkyTimeDistance = Integer.MAX_VALUE;

        String precipitationTypeCode = "0";
        String precipitationAmount = "강수없음";

        for (KmaWeatherResponseDTO.Item item : items) {
            String category = item.getCategory();
            String value = item.getFcstValue();

            if (category == null || value == null) {
                continue;
            }

            switch (category) {
                case "TMN" -> minTemperature =
                        parseNumber(value);

                case "TMX" -> maxTemperature =
                        parseNumber(value);

                case "TMP" -> {
                    Integer temperature = parseNumber(value);

                    if (temperature != null) {
                        if (hourlyMinTemperature == null
                                || temperature < hourlyMinTemperature) {
                            hourlyMinTemperature = temperature;
                        }

                        if (hourlyMaxTemperature == null
                                || temperature > hourlyMaxTemperature) {
                            hourlyMaxTemperature = temperature;
                        }
                    }
                }

                case "SKY" -> {
                    Integer distance = calculateTimeDistance(
                            item.getFcstTime(),
                            1200
                    );

                    if (distance < closestSkyTimeDistance) {
                        closestSkyTimeDistance = distance;
                        skyCode = value;
                    }
                }

                case "POP" -> {
                    Integer probability = parseNumber(value);

                    if (probability != null
                            && probability > precipitationProbability) {
                        precipitationProbability = probability;
                    }
                }

                case "PTY" -> {
                    if ("0".equals(precipitationTypeCode)
                            && !"0".equals(value)) {
                        precipitationTypeCode = value;
                    }
                }

                case "PCP" -> {
                    if ("강수없음".equals(precipitationAmount)
                            && !value.isBlank()
                            && !"강수없음".equals(value)) {
                        precipitationAmount = value;
                    }
                }
            }
        }

        // TMN 또는 TMX가 없으면 시간별 TMP의 최저·최고값 사용
        if (minTemperature == null) {
            minTemperature = hourlyMinTemperature;
        }

        if (maxTemperature == null) {
            maxTemperature = hourlyMaxTemperature;
        }

        String sky = convertSky(skyCode);
        String precipitationType =
                convertPrecipitationType(precipitationTypeCode);

        String washCondition = determineWashCondition(
                precipitationProbability,
                precipitationTypeCode
        );

        return new DailyWeatherDTO(
                date,
                minTemperature,
                maxTemperature,
                sky,
                precipitationProbability,
                precipitationType,
                precipitationAmount,
                washCondition
        );
    }
    private Integer parseNumber(String value) {
        try {
            return (int) Math.round(Double.parseDouble(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Integer calculateTimeDistance(
            String forecastTime,
            int targetTime
    ) {
        try {
            return Math.abs(
                    Integer.parseInt(forecastTime) - targetTime
            );
        } catch (NumberFormatException exception) {
            return Integer.MAX_VALUE;
        }
    }

    private String convertSky(String skyCode) {
        if (skyCode == null) {
            return "알 수 없음";
        }

        return switch (skyCode) {
            case "1" -> "맑음";
            case "3" -> "구름많음";
            case "4" -> "흐림";
            default -> "알 수 없음";
        };
    }

    private String convertPrecipitationType(String typeCode) {
        return switch (typeCode) {
            case "0" -> "없음";
            case "1" -> "비";
            case "2" -> "비/눈";
            case "3" -> "눈";
            case "4" -> "소나기";
            default -> "알 수 없음";
        };
    }

    private String determineWashCondition(
            Integer precipitationProbability,
            String precipitationTypeCode
    ) {
        if (!"0".equals(precipitationTypeCode)
                || precipitationProbability >= 60) {
            return "세차를 미루는 게 좋아요";
        }

        if (precipitationProbability >= 30) {
            return "세차 전 예보를 확인하세요";
        }

        return "세차하기 좋아요";
    }


}
