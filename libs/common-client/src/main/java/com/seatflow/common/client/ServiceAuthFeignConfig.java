package com.seatflow.common.client;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

/**
 * 서비스 간 전용 API(/internal/**) 호출에 서비스 토큰을 싣는다(OAuth2 Client Credentials).
 *
 * 사용자 토큰 전파(FeignAuthConfig)만으로는 두 가지가 안 된다.
 * 1) 사용자 요청이 없는 호출(Kafka 컨슈머, 스케줄러)은 실을 토큰이 없다.
 * 2) 사용자 토큰으로 통과되는 내부 API는 그 사용자가 직접 호출해도 통과한다.
 * 그래서 내부 API는 "누가 요청했나(사용자)"가 아니라 "어느 서비스가 호출했나"로 인가하고,
 * 사용자 식별자는 파라미터로 명시적으로 넘긴다.
 *
 * spring.security.oauth2.client.registration.seatflow-internal 설정이 있는 서비스에서만 켜진다.
 */
@AutoConfiguration(after = OAuth2ClientAutoConfiguration.class)
@ConditionalOnBean(ClientRegistrationRepository.class)
public class ServiceAuthFeignConfig {

    static final String INTERNAL_PATH_PREFIX = "/internal/";
    private static final String REGISTRATION_ID = "seatflow-internal";

    /**
     * 요청 컨텍스트 없이 동작하는 매니저. 발급받은 토큰을 캐시해 두고 만료가 가까우면 다시 발급받는다.
     */
    @Bean
    public OAuth2AuthorizedClientManager serviceAuthorizedClientManager(
            ClientRegistrationRepository clientRegistrationRepository,
            OAuth2AuthorizedClientService authorizedClientService) {
        return new AuthorizedClientServiceOAuth2AuthorizedClientManager(
                clientRegistrationRepository, authorizedClientService);
    }

    @Bean
    public RequestInterceptor serviceTokenInterceptor(
            OAuth2AuthorizedClientManager serviceAuthorizedClientManager,
            @Value("${spring.application.name}") String applicationName) {
        OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest
                .withClientRegistrationId(REGISTRATION_ID)
                .principal(applicationName)
                .build();
        return template -> {
            if (!template.path().startsWith(INTERNAL_PATH_PREFIX)) {
                return;
            }
            OAuth2AuthorizedClient client = serviceAuthorizedClientManager.authorize(authorizeRequest);
            if (client == null) {
                throw new IllegalStateException("Service token unavailable: registrationId=" + REGISTRATION_ID);
            }
            template.header(HttpHeaders.AUTHORIZATION, "Bearer " + client.getAccessToken().getTokenValue());
        };
    }
}
