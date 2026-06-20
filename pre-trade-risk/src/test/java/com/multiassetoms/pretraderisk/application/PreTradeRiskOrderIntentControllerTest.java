package com.multiassetoms.pretraderisk.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.multiassetoms.intentgeneration.application.OrderIntentQueryService;
import com.multiassetoms.intentgeneration.model.OrderIntent;
import com.multiassetoms.intentgeneration.model.OrderIntentNotFoundException;
import com.multiassetoms.intentgeneration.model.OrderIntentSourceType;
import com.multiassetoms.intentgeneration.model.OrderIntentStatus;
import com.multiassetoms.intentgeneration.model.OrderSide;
import com.multiassetoms.intentgeneration.model.OrderType;
import com.multiassetoms.intentgeneration.model.TimeInForce;
import com.multiassetoms.pretraderisk.model.PreTradeRiskCheckContext;
import com.multiassetoms.pretraderisk.model.PreTradeRiskCheckResult;
import com.multiassetoms.pretraderisk.model.PreTradeRiskDecision;
import com.multiassetoms.pretraderisk.model.PreTradeRiskOrderIntentResult;
import com.multiassetoms.pretraderisk.model.PreTradeRiskTransitionException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PreTradeRiskOrderIntentController.class)
@ContextConfiguration(classes = {
        PreTradeRiskOrderIntentControllerTest.TestApplication.class,
        PreTradeRiskOrderIntentController.class,
        PreTradeRiskExceptionHandler.class
})
class PreTradeRiskOrderIntentControllerTest {

    @SpringBootConfiguration
    static class TestApplication {
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderIntentQueryService orderIntentQueryService;

    @MockBean
    private PreTradeRiskOrderIntentService preTradeRiskOrderIntentService;

    @Test
    void evaluatesOrderIntentWithContext() throws Exception {
        UUID intentId = UUID.fromString("00000000-0000-0000-0000-000000029001");
        OrderIntent createdIntent = orderIntent(intentId, OrderIntentStatus.CREATED);
        OrderIntent approvedIntent = orderIntent(intentId, OrderIntentStatus.RISK_APPROVED);
        PreTradeRiskEvaluationRequest request = new PreTradeRiskEvaluationRequest(
                new BigDecimal("10"),
                new BigDecimal("550000"),
                new BigDecimal("100"),
                new BigDecimal("90"),
                false,
                null,
                new BigDecimal("50000"),
                new BigDecimal("60000"),
                false
        );

        when(orderIntentQueryService.getByIntentId(intentId)).thenReturn(createdIntent);
        when(preTradeRiskOrderIntentService.evaluate(
                org.mockito.ArgumentMatchers.eq(createdIntent),
                org.mockito.ArgumentMatchers.any(PreTradeRiskCheckContext.class)
        )).thenReturn(result(approvedIntent, PreTradeRiskDecision.APPROVED, "approved"));

        mockMvc.perform(post("/api/pre-trade-risk/order-intents/{intentId}/evaluations", intentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent.intentId").value(intentId.toString()))
                .andExpect(jsonPath("$.intent.status").value("RISK_APPROVED"))
                .andExpect(jsonPath("$.riskCheckResult.decision").value("APPROVED"))
                .andExpect(jsonPath("$.riskCheckResult.reason").value("approved"));

        ArgumentCaptor<PreTradeRiskCheckContext> contextCaptor =
                ArgumentCaptor.forClass(PreTradeRiskCheckContext.class);
        verify(preTradeRiskOrderIntentService).evaluate(
                org.mockito.ArgumentMatchers.eq(createdIntent),
                contextCaptor.capture()
        );
        assertEquals(new BigDecimal("10"), contextCaptor.getValue().limitContext().maxOrderQty());
        assertEquals(false, contextCaptor.getValue().openOrderContext().duplicateOpenOrderExists());
    }

    @Test
    void evaluatesOrderIntentWithLatestPriceBand() throws Exception {
        UUID intentId = UUID.fromString("00000000-0000-0000-0000-000000029004");
        OrderIntent createdIntent = orderIntent(intentId, OrderIntentStatus.CREATED);
        OrderIntent approvedIntent = orderIntent(intentId, OrderIntentStatus.RISK_APPROVED);
        PreTradeRiskLatestPriceBandEvaluationRequest request =
                new PreTradeRiskLatestPriceBandEvaluationRequest(
                        new BigDecimal("10"),
                        new BigDecimal("550000"),
                        new BigDecimal("100"),
                        new BigDecimal("90"),
                        false,
                        null,
                        false,
                        new BigDecimal("0.10")
                );

        when(orderIntentQueryService.getByIntentId(intentId)).thenReturn(createdIntent);
        when(preTradeRiskOrderIntentService.evaluateWithLatestPriceBand(
                org.mockito.ArgumentMatchers.eq(createdIntent),
                org.mockito.ArgumentMatchers.any(PreTradeRiskCheckContext.class),
                org.mockito.ArgumentMatchers.eq(new BigDecimal("0.10"))
        )).thenReturn(result(approvedIntent, PreTradeRiskDecision.APPROVED, "approved"));

        mockMvc.perform(post(
                        "/api/pre-trade-risk/order-intents/{intentId}/evaluations/latest-price-band",
                        intentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent.intentId").value(intentId.toString()))
                .andExpect(jsonPath("$.intent.status").value("RISK_APPROVED"))
                .andExpect(jsonPath("$.riskCheckResult.decision").value("APPROVED"));
    }

    @Test
    void evaluatesOrderIntentWithLatestPriceBandAndDuplicateOpenOrder() throws Exception {
        UUID intentId = UUID.fromString("00000000-0000-0000-0000-000000029006");
        OrderIntent createdIntent = orderIntent(intentId, OrderIntentStatus.CREATED);
        OrderIntent rejectedIntent = orderIntent(intentId, OrderIntentStatus.RISK_REJECTED);
        PreTradeRiskLatestPriceBandDuplicateEvaluationRequest request =
                new PreTradeRiskLatestPriceBandDuplicateEvaluationRequest(
                        new BigDecimal("10"),
                        new BigDecimal("550000"),
                        new BigDecimal("100"),
                        new BigDecimal("90"),
                        false,
                        new BigDecimal("0.10")
                );

        when(orderIntentQueryService.getByIntentId(intentId)).thenReturn(createdIntent);
        when(preTradeRiskOrderIntentService.evaluateWithLatestPriceBandAndDuplicateOpenOrder(
                org.mockito.ArgumentMatchers.eq(createdIntent),
                org.mockito.ArgumentMatchers.any(PreTradeRiskCheckContext.class),
                org.mockito.ArgumentMatchers.eq(new BigDecimal("0.10"))
        )).thenReturn(result(rejectedIntent, PreTradeRiskDecision.REJECTED, "duplicate open order exists"));

        mockMvc.perform(post(
                        "/api/pre-trade-risk/order-intents/{intentId}/evaluations/latest-price-band/duplicate-open-order",
                        intentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent.intentId").value(intentId.toString()))
                .andExpect(jsonPath("$.intent.status").value("RISK_REJECTED"))
                .andExpect(jsonPath("$.riskCheckResult.decision").value("REJECTED"))
                .andExpect(jsonPath("$.riskCheckResult.reason").value("duplicate open order exists"));

        ArgumentCaptor<PreTradeRiskCheckContext> contextCaptor =
                ArgumentCaptor.forClass(PreTradeRiskCheckContext.class);
        verify(preTradeRiskOrderIntentService).evaluateWithLatestPriceBandAndDuplicateOpenOrder(
                org.mockito.ArgumentMatchers.eq(createdIntent),
                contextCaptor.capture(),
                org.mockito.ArgumentMatchers.eq(new BigDecimal("0.10"))
        );
        assertEquals(new BigDecimal("10"), contextCaptor.getValue().limitContext().maxOrderQty());
    }

    @Test
    void returnsBadRequestWhenPriceBandRateIsMissing() throws Exception {
        UUID intentId = UUID.fromString("00000000-0000-0000-0000-000000029005");
        OrderIntent createdIntent = orderIntent(intentId, OrderIntentStatus.CREATED);

        when(orderIntentQueryService.getByIntentId(intentId)).thenReturn(createdIntent);

        mockMvc.perform(post(
                        "/api/pre-trade-risk/order-intents/{intentId}/evaluations/latest-price-band",
                        intentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("priceBandRate is required"));
    }

    @Test
    void returnsNotFoundWhenOrderIntentDoesNotExist() throws Exception {
        UUID intentId = UUID.fromString("00000000-0000-0000-0000-000000029002");
        when(orderIntentQueryService.getByIntentId(intentId))
                .thenThrow(new OrderIntentNotFoundException("order intent not found"));

        mockMvc.perform(post("/api/pre-trade-risk/order-intents/{intentId}/evaluations", intentId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("order intent not found"));
    }

    @Test
    void returnsConflictWhenIntentCannotTransition() throws Exception {
        UUID intentId = UUID.fromString("00000000-0000-0000-0000-000000029003");
        OrderIntent approvedIntent = orderIntent(intentId, OrderIntentStatus.RISK_APPROVED);

        when(orderIntentQueryService.getByIntentId(intentId)).thenReturn(approvedIntent);
        when(preTradeRiskOrderIntentService.evaluate(
                org.mockito.ArgumentMatchers.eq(approvedIntent),
                org.mockito.ArgumentMatchers.any(PreTradeRiskCheckContext.class)
        )).thenThrow(new PreTradeRiskTransitionException(
                "only CREATED order intents can be evaluated by pre-trade risk"
        ));

        mockMvc.perform(post("/api/pre-trade-risk/order-intents/{intentId}/evaluations", intentId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("only CREATED order intents can be evaluated by pre-trade risk"));
    }

    private PreTradeRiskOrderIntentResult result(
            OrderIntent intent,
            PreTradeRiskDecision decision,
            String reason
    ) {
        return new PreTradeRiskOrderIntentResult(
                intent,
                new PreTradeRiskCheckResult(
                        intent.intentId(),
                        decision,
                        reason,
                        Instant.parse("2026-06-05T00:00:00Z"),
                        List.of()
                )
        );
    }

    private OrderIntent orderIntent(UUID intentId, OrderIntentStatus status) {
        Instant now = Instant.parse("2026-06-05T00:00:00Z");
        return new OrderIntent(
                intentId,
                "portfolio-1",
                "005930",
                OrderIntentSourceType.MANUAL,
                null,
                OrderSide.BUY,
                OrderType.LIMIT,
                new BigDecimal("10"),
                new BigDecimal("55000"),
                TimeInForce.DAY,
                "manual order",
                status,
                "manual-key-1",
                "operator",
                now,
                now
        );
    }
}
