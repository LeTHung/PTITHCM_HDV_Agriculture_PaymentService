package vn.ptit.soa.payment.contract;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentTracking(String paymentTrackingId, String contractId,
        BigDecimal depositRate, BigDecimal depositAmount, String status,
        Instant paidAt, Instant deliveryDate, Instant completedConfirmedAt,
        Instant releaseAt, Instant releasedAt, boolean disputeHold,
        long stateVersion, Instant createdAt, Instant updatedAt) {
}
