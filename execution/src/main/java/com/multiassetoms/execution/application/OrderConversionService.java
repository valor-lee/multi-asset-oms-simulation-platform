package com.multiassetoms.execution.application;

import com.multiassetoms.execution.model.Order;
import com.multiassetoms.execution.model.OrderConversionException;
import com.multiassetoms.execution.model.OrderStatus;
import com.multiassetoms.intentgeneration.application.OrderIntentRepository;
import com.multiassetoms.intentgeneration.model.OrderIntent;
import com.multiassetoms.intentgeneration.model.OrderIntentStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class OrderConversionService {

    private final OrderRepository orderRepository;
    private final OrderIntentRepository orderIntentRepository;
    private final Clock clock;

    @Autowired
    public OrderConversionService(
            OrderRepository orderRepository,
            OrderIntentRepository orderIntentRepository
    ) {
        this(orderRepository, orderIntentRepository, Clock.systemUTC());
    }

    OrderConversionService(
            OrderRepository orderRepository,
            OrderIntentRepository orderIntentRepository,
            Clock clock
    ) {
        this.orderRepository = orderRepository;
        this.orderIntentRepository = orderIntentRepository;
        this.clock = clock;
    }

    /**
     * 저장소에서 intent를 조회한 뒤 order 변환을 수행한다.
     *
     * @param intentId order로 변환할 order intent id
     * @return 생성되었거나 이미 존재하는 order와 변환 완료 상태의 intent
     */
    public OrderConversionResult convert(UUID intentId) {
        OrderIntent intent = orderIntentRepository.findByIntentId(intentId)
                .orElseThrow(() -> new OrderConversionException("order intent not found"));
        return convert(intent);
    }

    /**
     * risk 승인된 intent를 order로 변환한다.
     * 같은 intent로 이미 order가 만들어져 있으면 새 order를 만들지 않고 기존 order를 반환한다.
     *
     * @param intent order 변환 대상 intent
     * @return 생성되었거나 이미 존재하는 order와 변환 완료 상태의 intent
     */
    public OrderConversionResult convert(OrderIntent intent) {
        return orderRepository.findByIntentId(intent.intentId())
                .map(order -> existingConversionResult(order, intent))
                .orElseGet(() -> convertNew(intent));
    }

    /**
     * 중복 요청이나 재시도처럼 이미 order가 있는 경우 기존 order를 반환한다.
     * intent가 아직 RISK_APPROVED이면 변환 완료 상태로 보정하고,
     * 이미 CONVERTED_TO_ORDER이면 updatedAt을 바꾸지 않도록 다시 저장하지 않는다.
     */
    private OrderConversionResult existingConversionResult(Order order, OrderIntent intent) {
        validateRiskApprovedOrConverted(intent);
        if (intent.status() == OrderIntentStatus.CONVERTED_TO_ORDER) {
            return new OrderConversionResult(order, intent);
        }
        return new OrderConversionResult(order, markConverted(intent));
    }

    /**
     * 아직 order가 없는 risk 승인 intent를 새 order로 변환한다.
     */
    private OrderConversionResult convertNew(OrderIntent intent) {
        validateRiskApproved(intent);

        Instant now = Instant.now(clock);
        Order order = orderRepository.save(new Order(
                UUID.randomUUID(),
                intent.intentId(),
                intent.portfolioId(),
                intent.instrumentId(),
                intent.side(),
                intent.orderType(),
                intent.requestedQty(),
                intent.limitPrice(),
                intent.timeInForce(),
                OrderStatus.CREATED,
                now,
                now
        ));
        OrderIntent convertedIntent = markConverted(intent);

        return new OrderConversionResult(order, convertedIntent);
    }

    /**
     * 새 order를 만들 수 있는 최초 변환 상태인지 확인한다.
     */
    private void validateRiskApproved(OrderIntent intent) {
        if (intent.status() != OrderIntentStatus.RISK_APPROVED) {
            throw new OrderConversionException("only RISK_APPROVED order intents can be converted to orders");
        }
    }

    /**
     * 기존 order 반환이 가능한 재시도 상태인지 확인한다.
     */
    private void validateRiskApprovedOrConverted(OrderIntent intent) {
        if (intent.status() != OrderIntentStatus.RISK_APPROVED
                && intent.status() != OrderIntentStatus.CONVERTED_TO_ORDER) {
            throw new OrderConversionException(
                    "only RISK_APPROVED or CONVERTED_TO_ORDER order intents can be converted to orders"
            );
        }
    }

    /**
     * 원본 intent를 직접 바꾸지 않고 CONVERTED_TO_ORDER 상태의 새 스냅샷으로 저장한다.
     */
    private OrderIntent markConverted(OrderIntent intent) {
        Instant now = Instant.now(clock);
        return orderIntentRepository.save(new OrderIntent(
                intent.intentId(),
                intent.portfolioId(),
                intent.instrumentId(),
                intent.sourceType(),
                intent.sourceRefId(),
                intent.side(),
                intent.orderType(),
                intent.requestedQty(),
                intent.limitPrice(),
                intent.timeInForce(),
                intent.reason(),
                OrderIntentStatus.CONVERTED_TO_ORDER,
                intent.idempotencyKey(),
                intent.createdBy(),
                intent.createdAt(),
                now
        ));
    }
}
