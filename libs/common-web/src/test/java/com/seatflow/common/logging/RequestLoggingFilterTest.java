package com.seatflow.common.logging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

// 테스트에선 트레이싱이 기본으로 꺼져 있어서 @AutoConfigureObservability로 켠다.
// 인증 없는 요청이라 Security가 컨트롤러 전에 막는데, 그 응답까지 traceId와 함께 찍혀야 한다.
@SpringBootTest(classes = RequestLoggingFilterTest.App.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureObservability
@ExtendWith(OutputCaptureExtension.class)
class RequestLoggingFilterTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class App {
    }

    @Autowired
    TestRestTemplate rest;

    @Test
    void 모든_요청을_traceId와_함께_남기고_응답헤더로_돌려준다(CapturedOutput output) {
        ResponseEntity<String> response = rest.getForEntity("/api/payments?token=secret", String.class);

        String traceId = response.getHeaders().getFirst("X-Trace-Id");
        assertThat(traceId).matches("[0-9a-f]{32}");
        assertThat(output).containsPattern(traceId + "-[0-9a-f]{16}].*GET /api/payments \\d{3} \\d+ms")
                .doesNotContain("secret");
    }
}
