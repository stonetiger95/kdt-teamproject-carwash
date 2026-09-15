package kdt.washPrj.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RestConfig {
    @Bean
    RestClient restClient() {
        return RestClient.builder()
                .requestFactory(new SimpleClientHttpRequestFactory())
                .build();
    }
}