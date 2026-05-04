package com.multiassetoms.intentgeneration.manual;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.multiassetoms.intentgeneration.api.OrderIntentExceptionHandler;
import com.multiassetoms.intentgeneration.model.OrderIntent;
import com.multiassetoms.intentgeneration.model.OrderIntentSourceType;
import com.multiassetoms.intentgeneration.model.OrderIntentStatus;
import com.multiassetoms.intentgeneration.model.OrderIntentValidationException;
import com.multiassetoms.intentgeneration.model.OrderSide;
import com.multiassetoms.intentgeneration.model.OrderType;
import com.multiassetoms.intentgeneration.model.TimeInForce;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 전체 Spring Context를 띄우지 않고 MVC 계층만 테스트하므로 빠름
@WebMvcTest(ManualOrderIntentController.class)
@ContextConfiguration(classes = {
        ManualOrderIntentControllerTest.TestApplication.class,
        ManualOrderIntentController.class,
        OrderIntentExceptionHandler.class
})
class ManualOrderIntentControllerTest {

    // intent-generation은 독립 라이브러리 모듈이라 테스트용 Spring Boot 설정을 직접 제공한다.
    @SpringBootConfiguration
    static class TestApplication {
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ManualOrderIntentService manualOrderIntentService;

    @Test
    void createsManualLimitOrderIntent() throws Exception {
        // API 요청으로 들어올 수동 주문 의도 입력값
        ManualOrderIntentRequest request = new ManualOrderIntentRequest(
                "portfolio-1",
                "005930",
                OrderSide.BUY,
                OrderType.LIMIT,
                new BigDecimal("10"),
                new BigDecimal("55000"),
                TimeInForce.DAY,
                "operator order",
                "manual-key-1",
                "operator"
        );
        // controller 테스트에서는 service 내부 로직 대신 HTTP 응답 변환만 검증한다.
        OrderIntent response = new OrderIntent(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "portfolio-1",
                "005930",
                OrderIntentSourceType.MANUAL,
                null,
                OrderSide.BUY,
                OrderType.LIMIT,
                new BigDecimal("10"),
                new BigDecimal("55000"),
                TimeInForce.DAY,
                "operator order",
                OrderIntentStatus.CREATED,
                "manual-key-1",
                "operator",
                Instant.parse("2026-04-25T00:00:00Z"),
                Instant.parse("2026-04-25T00:00:00Z")
        );

        when(manualOrderIntentService.create(
                                        any(ManualOrderIntentRequest.class)))
                                .thenReturn(response);

        // POST 요청이 성공하면 201 Created와 생성된 OrderIntent JSON을 반환해야 한다.
        mockMvc.perform(post("/api/order-intents/manual")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.intentId").value("00000000-0000-0000-0000-000000000001"))
                .andExpect(jsonPath("$.sourceType").value("MANUAL"))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.idempotencyKey").value("manual-key-1"));
    }

    @Test
    void returnsBadRequestWhenValidationFails() throws Exception {
        // LIMIT 주문은 limitPrice가 필수인데 null로 요청한 상황
        ManualOrderIntentRequest request = new ManualOrderIntentRequest(
                "portfolio-1",
                "005930",
                OrderSide.BUY,
                OrderType.LIMIT,
                new BigDecimal("1"),
                null,
                TimeInForce.DAY,
                null,
                null,
                "operator"
        );
        when(manualOrderIntentService.create(any(ManualOrderIntentRequest.class)))
                .thenThrow(new OrderIntentValidationException("limitPrice is required for LIMIT orders"));

        // service/factory에서 발생한 검증 예외는 API에서 400 응답으로 변환된다.
        mockMvc.perform(post("/api/order-intents/manual")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("limitPrice is required for LIMIT orders"));
    }

    @Test
    void returnsBadRequestWhenMarketOrderHasLimitPrice() throws Exception {
        // MARKET 주문은 limitPrice를 받으면 안 되는데 값이 포함된 상황
        ManualOrderIntentRequest request = new ManualOrderIntentRequest(
                "portfolio-1",
                "005930",
                OrderSide.SELL,
                OrderType.MARKET,
                new BigDecimal("1"),
                new BigDecimal("55000"),
                TimeInForce.DAY,
                null,
                null,
                "operator"
        );
        when(manualOrderIntentService.create(any(ManualOrderIntentRequest.class)))
                .thenThrow(new OrderIntentValidationException("limitPrice must be null for MARKET orders"));

        // 예외 메시지가 그대로 error response의 message 필드로 내려가는지 확인한다.
        mockMvc.perform(post("/api/order-intents/manual")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("limitPrice must be null for MARKET orders"));
    }
}
