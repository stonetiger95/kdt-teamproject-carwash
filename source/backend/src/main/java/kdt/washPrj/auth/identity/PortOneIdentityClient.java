package kdt.washPrj.auth.identity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

@Component
public class PortOneIdentityClient {

    private final PortOneIdentityProperties properties;
    private final RestClient restClient;

    public PortOneIdentityClient(PortOneIdentityProperties properties) {
        this.properties = properties;

        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(60));

        this.restClient = RestClient.builder()
                .baseUrl(properties.getApiUrl())
                .requestFactory(requestFactory)
                .build();
    }

    public PortOneVerification getVerification(
            String identityVerificationId
    ) {
        properties.requireProviderReady();

        try {
            PortOneVerification response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/identity-verifications/{identityVerificationId}")
                            .queryParam(
                                    "storeId",
                                    properties.getStoreId()
                            )
                            .build(identityVerificationId))
                    .header(
                            HttpHeaders.AUTHORIZATION,
                            "PortOne " + properties.getApiSecret()
                    )
                    .retrieve()
                    .body(PortOneVerification.class);

            if (response == null) {
                throw providerUnavailable();
            }
            return response;
        } catch (RestClientException exception) {
            throw providerUnavailable();
        }
    }

    private static ResponseStatusException providerUnavailable() {
        return new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "본인인증 제공기관의 결과를 확인할 수 없습니다."
        );
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PortOneVerification(
            String status,
            String id,
            SelectedChannel channel,
            VerifiedCustomer verifiedCustomer,
            String verifiedAt,
            String version
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SelectedChannel(
            String type,
            String key
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VerifiedCustomer(
            String name,
            String birthDate,
            String phoneNumber,
            String gender,
            String di,
            String ci
    ) {
    }
}
