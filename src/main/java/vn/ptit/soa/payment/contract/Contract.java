package vn.ptit.soa.payment.contract;

import java.math.BigDecimal;
import java.time.Instant;

public record Contract(String contractId, String offerId, String conversationId,
        String farmerId, String traderId,
        @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
        BigDecimal totalAmount, String terms,
        Instant deliveryDate, String status, BigDecimal penaltyRate,
        Instant completedConfirmedAt, long stateVersion, Instant createdAt, Instant updatedAt) {
}
