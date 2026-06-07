package com.multiassetoms.posttrade.application;

import com.multiassetoms.execution.model.OrderNotFoundException;
import com.multiassetoms.intentgeneration.model.OrderSide;
import com.multiassetoms.posttrade.model.Trade;
import com.multiassetoms.posttrade.model.TradeCaptureException;
import com.multiassetoms.posttrade.model.TradeStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TradeCaptureController.class)
@ContextConfiguration(classes = {
        TradeCaptureControllerTest.TestApplication.class,
        TradeCaptureController.class,
        PostTradeExceptionHandler.class
})
class TradeCaptureControllerTest {

    @SpringBootConfiguration
    static class TestApplication {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TradeCaptureService tradeCaptureService;

    @Test
    void capturesFilledOrderAsTrade() throws Exception {
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000044001");
        UUID tradeId = UUID.fromString("00000000-0000-0000-0000-000000045001");
        Trade trade = trade(tradeId, orderId, new BigDecimal("10"));

        when(tradeCaptureService.capture(orderId)).thenReturn(trade);

        mockMvc.perform(post("/api/post-trade/orders/{orderId}/trades", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tradeId").value(tradeId.toString()))
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("CAPTURED"))
                .andExpect(jsonPath("$.quantity").value(10));
    }

    @Test
    void returnsNotFoundWhenOrderDoesNotExist() throws Exception {
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000044002");

        when(tradeCaptureService.capture(orderId))
                .thenThrow(new OrderNotFoundException("order not found"));

        mockMvc.perform(post("/api/post-trade/orders/{orderId}/trades", orderId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("order not found"));
    }

    @Test
    void returnsConflictWhenOrderCannotBeCaptured() throws Exception {
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000044003");

        when(tradeCaptureService.capture(orderId))
                .thenThrow(new TradeCaptureException(
                        "only FILLED or partially filled CANCELED orders can be captured"
                ));

        mockMvc.perform(post("/api/post-trade/orders/{orderId}/trades", orderId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("only FILLED or partially filled CANCELED orders can be captured"));
    }

    private Trade trade(UUID tradeId, UUID orderId, BigDecimal quantity) {
        Instant now = Instant.parse("2026-06-07T00:00:00Z");
        return new Trade(
                tradeId,
                orderId,
                UUID.fromString("00000000-0000-0000-0000-000000046001"),
                "portfolio-1",
                "005930",
                OrderSide.BUY,
                quantity,
                new BigDecimal("55000.0000000000"),
                new BigDecimal("550000"),
                new BigDecimal("100"),
                new BigDecimal("30"),
                TradeStatus.CAPTURED,
                now,
                null,
                now
        );
    }
}
