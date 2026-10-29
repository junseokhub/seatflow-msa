package com.seatflow.common.client;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * /internal/** 호출에는 서비스 토큰만, 그 외 호출에는 사용자 토큰만 실리는지 확인한다.
 */
class ServiceAuthFeignConfigTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OAuth2ClientAutoConfiguration.class, ServiceAuthFeignConfig.class))
            .withPropertyValues("spring.application.name=payment-service");

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("seatflow-internal 클라이언트 등록이 있을 때만 서비스 토큰 인터셉터가 켜진다")
    void activatesOnlyWithClientRegistration() {
        contextRunner
                .withPropertyValues(
                        "spring.security.oauth2.client.registration.seatflow-internal.client-id=payment-service",
                        "spring.security.oauth2.client.registration.seatflow-internal.client-secret=secret",
                        "spring.security.oauth2.client.registration.seatflow-internal.authorization-grant-type=client_credentials",
                        "spring.security.oauth2.client.registration.seatflow-internal.provider=seatflow-auth",
                        "spring.security.oauth2.client.provider.seatflow-auth.token-uri=http://auth/oauth2/token")
                .run(context -> assertThat(context).hasBean("serviceTokenInterceptor"));

        contextRunner.run(context -> assertThat(context).doesNotHaveBean("serviceTokenInterceptor"));
    }

    @Test
    @DisplayName("내부 API 호출에는 서비스 토큰을 싣는다")
    void addsServiceTokenToInternalCall() {
        OAuth2AuthorizedClientManager manager = mock(OAuth2AuthorizedClientManager.class);
        given(manager.authorize(any())).willReturn(authorizedClient("service-token"));
        RequestInterceptor interceptor = new ServiceAuthFeignConfig().serviceTokenInterceptor(manager, "payment-service");

        RequestTemplate template = new RequestTemplate().uri("/internal/coupons/reservations/1/restore");
        interceptor.apply(template);

        assertThat(template.headers().get("Authorization")).containsExactly("Bearer service-token");
    }

    @Test
    @DisplayName("내부 API가 아닌 호출에는 서비스 토큰을 싣지 않는다")
    void skipsNonInternalCall() {
        OAuth2AuthorizedClientManager manager = mock(OAuth2AuthorizedClientManager.class);
        RequestInterceptor interceptor = new ServiceAuthFeignConfig().serviceTokenInterceptor(manager, "payment-service");

        RequestTemplate template = new RequestTemplate().uri("/api/reservations/1");
        interceptor.apply(template);

        assertThat(template.headers()).doesNotContainKey("Authorization");
        verifyNoInteractions(manager);
    }

    @Test
    @DisplayName("사용자 토큰은 내부 API로 전파하지 않고 일반 API로만 전파한다")
    void forwardsUserTokenOnlyToNonInternalCall() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer user-token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        RequestInterceptor forwarding = new FeignAuthConfig().authForwardingInterceptor();

        RequestTemplate internal = new RequestTemplate().uri("/internal/coupons/1/validate");
        forwarding.apply(internal);
        RequestTemplate api = new RequestTemplate().uri("/api/reservations/1");
        forwarding.apply(api);

        assertThat(internal.headers()).doesNotContainKey("Authorization");
        assertThat(api.headers().get("Authorization")).containsExactly("Bearer user-token");
    }

    private OAuth2AuthorizedClient authorizedClient(String tokenValue) {
        ClientRegistration registration = ClientRegistration.withRegistrationId("seatflow-internal")
                .clientId("payment-service")
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .tokenUri("http://auth/oauth2/token")
                .build();
        OAuth2AccessToken token = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER, tokenValue, Instant.now(), Instant.now().plusSeconds(300));
        return new OAuth2AuthorizedClient(registration, "payment-service", token);
    }
}
