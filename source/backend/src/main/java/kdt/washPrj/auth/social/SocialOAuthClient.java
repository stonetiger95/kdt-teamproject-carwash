package kdt.washPrj.auth.social;

import kdt.washPrj.auth.dto.SocialProfileDTO;

public interface SocialOAuthClient {

    SocialProvider provider();

    boolean isConfigured();

    String createAuthorizationUrl(String state);

    default String createAuthorizationUrl(String state, boolean appClient) {
        return createAuthorizationUrl(state);
    }

    SocialProfileDTO exchangeCodeAndFetchProfile(String code);

    default SocialProfileDTO exchangeCodeAndFetchProfile(
            String code,
            boolean appClient
    ) {
        return exchangeCodeAndFetchProfile(code);
    }
}
