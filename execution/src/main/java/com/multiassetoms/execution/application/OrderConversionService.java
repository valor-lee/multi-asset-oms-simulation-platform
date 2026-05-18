package com.multiassetoms.execution.application;

import com.multiassetoms.execution.model.Order;
import com.multiassetoms.execution.model.OrderConversionException;
import com.multiassetoms.execution.model.OrderStatus;
import com.multiassetoms.intentgeneration.application.OrderIntentRepository;
import com.multiassetoms.intentgeneration.model.OrderIntent;
import com.multiassetoms.intentgeneration.model.OrderIntentStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class OrderConversionService {

    private final OrderRepository orderRepository;
    private final OrderIntentRepository orderIntentRepository;
    private final Clock clock;

    public OrderConversionService(
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
     * 상태별 처리:
     * - RISK_APPROVED: order가 없으면 CREATED order를 만들고 intent를 CONVERTED_TO_ORDER로 저장
     * - CONVERTED_TO_ORDER: 기존 order가 있으면 중복 요청으로 보고 기존 결과 반환
     * - 그 외 상태: order 변환 대상이 아니므로 예외
     *
     * @param intentId order로 변환할 order intent id
     * @return 생성되었거나 이미 존재하는 order와 변환 완료 상태의 intent
     */
    public OrderConversionResult convert(UUID intentId) {
        OrderIntent intent = orderIntentRepository.findByIntentId(intentId)
                .orElseThrow(() -> new OrderConversionException("order intent not found"));
        return convert(intent);
    }

    private OrderConversionResult convert(OrderIntent intent) {
        return orderRepository.findByIntentId(intent.intentId())
                .map(order -> existingConversionResult(order, intent))
                .orElseGet(() -> convertNew(intent));
    }

    /**
     * 중복 요청이나 재시도처럼 이미 order가 있는 경우 기존 order를 반환한다.
     * 저장소의 최신 intent가 아직 RISK_APPROVED이면 변환 완료 상태로 보정하고,
     * 이미 CONVERTED_TO_ORDER이면 updatedAt을 바꾸지 않도록 다시 저장하지 않는다.
     * 상태별 처리:
     * - RISK_APPROVED: 기존 order를 반환하고 intent만 CONVERTED_TO_ORDER로 보정 저장
     * - CONVERTED_TO_ORDER: 기존 order와 최신 intent를 그대로 반환
     * - 그 외 상태: 정합성이 맞지 않으므로 예외
     */
    private OrderConversionResult existingConversionResult(Order order, OrderIntent intent) {
        OrderIntent latestIntent = orderIntentRepository.findByIntentId(intent.intentId())
                .orElseThrow(() -> new OrderConversionException("order intent not found"));
        validateRiskApprovedOrConverted(latestIntent);

        if (latestIntent.status() == OrderIntentStatus.CONVERTED_TO_ORDER) {
            return new OrderConversionResult(order, latestIntent);
        }
        return new OrderConversionResult(order, markConverted(latestIntent, Instant.now(clock)));
    }

    /**
     * 아직 order가 없는 risk 승인 intent를 새 order로 변환한다.
     * 상태별 처리:
     * - RISK_APPROVED: CREATED order 생성 후 intent를 CONVERTED_TO_ORDER로 저장
     * - 그 외 상태: 예외
     */
    private OrderConversionResult convertNew(OrderIntent intent) {
        validateRiskApproved(intent);

        Instant conversionTime = Instant.now(clock);
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
                conversionTime,
                conversionTime
        ));
        OrderIntent convertedIntent = markConverted(intent, conversionTime);

        return new OrderConversionResult(order, convertedIntent);
    }

    /**
     * 새 order를 만들 수 있는 최초 변환 상태인지 확인한다.
     * 허용 상태: RISK_APPROVED
     */
    private void validateRiskApproved(OrderIntent intent) {
        if (intent.status() != OrderIntentStatus.RISK_APPROVED) {
            throw new OrderConversionException("only RISK_APPROVED order intents can be converted to orders");
        }
    }

    /**
     * 기존 order 반환이 가능한 재시도 상태인지 확인한다.
     * 허용 상태: RISK_APPROVED, CONVERTED_TO_ORDER
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
     * 상태별 처리:
     * - 입력 intent의 현재 상태와 관계없이 호출자가 검증한 뒤 CONVERTED_TO_ORDER 스냅샷으로 저장
     */
    private OrderIntent markConverted(OrderIntent intent, Instant convertedAt) {
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
                convertedAt
        ));
    }
}
