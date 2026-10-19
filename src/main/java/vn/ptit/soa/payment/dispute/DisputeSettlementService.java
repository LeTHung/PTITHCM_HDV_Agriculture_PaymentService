package vn.ptit.soa.payment.dispute;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.ptit.soa.payment.contract.Contract;
import vn.ptit.soa.payment.contract.ContractOutboxRepository;
import vn.ptit.soa.payment.contract.ContractRepository;
import vn.ptit.soa.payment.contract.DepositOutboxRepository;
import vn.ptit.soa.payment.contract.PaymentTracking;
import vn.ptit.soa.payment.contract.PaymentTrackingRepository;

@Service
@Profile("database-learning")
public class DisputeSettlementService {
    private final DisputeRepository disputes;
    private final DisputeEvidenceRepository evidence;
    private final DisputeSettlementRepository settlements;
    private final ContractRepository contracts;
    private final PaymentTrackingRepository tracking;
    private final ContractOutboxRepository contractOutbox;
    private final DepositOutboxRepository depositOutbox;
    private final Clock clock;

    public DisputeSettlementService(DisputeRepository disputes, DisputeEvidenceRepository evidence,
            DisputeSettlementRepository settlements, ContractRepository contracts,
            PaymentTrackingRepository tracking, ContractOutboxRepository contractOutbox,
            DepositOutboxRepository depositOutbox, Clock clock) {
        this.disputes = disputes;
        this.evidence = evidence;
        this.settlements = settlements;
        this.contracts = contracts;
        this.tracking = tracking;
        this.contractOutbox = contractOutbox;
        this.depositOutbox = depositOutbox;
        this.clock = clock;
    }

    // Learning-only: adminId is supplied by the caller until JWT/User integration exists.
    @Transactional
    public Dispute settle(String disputeId, String decisionId, String idempotencyKey,
            String adminId, long expectedVersion, String outcome, String disposition,
            String reason, List<String> evidenceRefs, String liableAccountId,
            String violationCode, String penaltyPoints) {
        String id = uuid(disputeId);
        String decision = uuid(decisionId);
        String admin = uuid(adminId);
        if (!decision.equals(uuid(idempotencyKey))) {
            throw new IllegalArgumentException("Idempotency-Key must equal decisionId.");
        }
        boolean farmerFault = "FARMER_VIOLATION".equals(outcome)
                && "REFUND_TO_TRADER".equals(disposition);
        boolean traderFault = "TRADER_VIOLATION".equals(outcome)
                && "FORFEIT_TO_FARMER".equals(disposition);
        if (!farmerFault && !traderFault) {
            throw new IllegalArgumentException("Outcome and deposit disposition do not match.");
        }
        if (reason == null || reason.isBlank() || reason.length() > 5000) {
            throw new IllegalArgumentException("reason must be 1..5000 characters.");
        }
        if (evidenceRefs == null || evidenceRefs.isEmpty() || evidenceRefs.size() > 20) {
            throw new IllegalArgumentException("Settlement requires 1..20 evidence references.");
        }
        List<String> refs = evidenceRefs.stream().map(this::uuid).toList();
        if (new HashSet<>(refs).size() != refs.size()
                || refs.stream().anyMatch(ref -> !evidence.belongsToDispute(ref, id))) {
            throw new IllegalArgumentException("Evidence must belong to this dispute without duplicates.");
        }
        String liable = uuid(liableAccountId);
        if (violationCode != null && (violationCode.isBlank() || violationCode.length() > 40)) {
            throw new IllegalArgumentException("Invalid violationCode.");
        }
        BigDecimal points = penaltyPoints == null ? null : new BigDecimal(penaltyPoints);
        if (points != null && (points.signum() <= 0 || points.scale() > 1
                || points.compareTo(new BigDecimal("10.0")) > 0 || violationCode == null)) {
            throw new IllegalArgumentException("Invalid penaltyPoints.");
        }
        Dispute snapshot = disputes.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Dispute does not exist."));
        Contract contract = contracts.findByIdForUpdate(snapshot.contractId())
                .orElseThrow(() -> new IllegalArgumentException("Contract does not exist."));
        if (admin.equals(contract.farmerId()) || admin.equals(contract.traderId())) {
            throw new IllegalArgumentException("Admin cannot settle their own Contract.");
        }
        PaymentTracking deposit = tracking.findByContractIdForUpdate(contract.contractId())
                .orElseThrow(() -> new IllegalStateException("Contract has no deposit tracking."));
        Dispute current = disputes.findByIdForUpdate(id).orElseThrow();
        DisputeDecision submitted = new DisputeDecision(decision, id, admin, expectedVersion,
                outcome, disposition, reason.trim(), refs);
        var previous = disputes.findDecision(decision);
        if (previous.isPresent()) {
            var previousTerms = settlements.findTerms(decision).orElseThrow(
                    () -> new IllegalStateException("Decision is not a settlement."));
            if (!previous.get().equals(submitted)
                    || !previousTerms.liableAccountId().equals(liable)
                    || !java.util.Objects.equals(previousTerms.violationCode(), violationCode)
                    || !java.util.Objects.equals(previousTerms.penaltyPoints(), points)) {
                throw new IllegalStateException("Idempotency key was reused with different content.");
            }
            return current;
        }
        if (!"UNDER_REVIEW".equals(current.status())
                || !admin.equals(current.assignedAdminId())
                || current.stateVersion() != expectedVersion
                || !"ACTIVE".equals(contract.status())
                || !("LOCKED".equals(deposit.status()) || "RELEASABLE".equals(deposit.status()))
                || !current.depositHold() || !deposit.disputeHold()) {
            throw new IllegalStateException("Dispute, Contract or deposit is not eligible for settlement.");
        }
        String expectedLiable = farmerFault ? contract.farmerId() : contract.traderId();
        if (!liable.equals(expectedLiable)) {
            throw new IllegalArgumentException("liableAccountId does not match outcome.");
        }
        Instant now = clock.instant();
        BigDecimal penaltyRate = farmerFault ? contract.penaltyRate() : BigDecimal.ZERO;
        BigDecimal penaltyAmount = deposit.depositAmount().multiply(penaltyRate)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        String beneficiary = farmerFault ? contract.traderId() : contract.farmerId();
        String nextStatus = farmerFault ? "REFUNDED" : "FORFEITED";
        disputes.insertDecision(submitted, now);
        settlements.insert(decision, deposit.paymentTrackingId(), contract.contractId(), admin,
                liable, violationCode, points, beneficiary, disposition,
                deposit.depositAmount(), penaltyRate, penaltyAmount, now);
        contracts.markCancelled(contract.contractId(), contract.stateVersion() + 1, now);
        tracking.settleDispute(contract.contractId(), nextStatus, deposit.stateVersion() + 1, now);
        disputes.resolve(id, outcome, current.stateVersion() + 1,
                current.decisionVersion() + 1, now);
        Contract cancelled = contracts.findById(contract.contractId()).orElseThrow();
        PaymentTracking settled = tracking.findByContractId(contract.contractId()).orElseThrow();
        contractOutbox.statusChanged(cancelled, "ACTIVE", admin, now);
        depositOutbox.statusChanged(cancelled, settled, deposit.status(), admin, now);
        return disputes.findById(id).orElseThrow();
    }

    @Transactional
    public Dispute cancelMutually(String disputeId, String decisionId, String idempotencyKey,
            String adminId, long expectedVersion, String disposition, String reason,
            List<String> evidenceRefs, String liableAccountId, String violationCode,
            String penaltyPoints) {
        String id = uuid(disputeId);
        String decision = uuid(decisionId);
        String admin = uuid(adminId);
        if (!decision.equals(uuid(idempotencyKey))) {
            throw new IllegalArgumentException("Idempotency-Key must equal decisionId.");
        }
        if (!"REFUND_TO_TRADER".equals(disposition) || liableAccountId != null
                || violationCode != null || penaltyPoints != null) {
            throw new IllegalArgumentException("Mutual cancellation refunds the deposit without penalty.");
        }
        if (reason == null || reason.isBlank() || reason.length() > 5000) {
            throw new IllegalArgumentException("reason must be 1..5000 characters.");
        }
        if (evidenceRefs == null || evidenceRefs.size() < 2 || evidenceRefs.size() > 20) {
            throw new IllegalArgumentException("Evidence from both Contract participants is required.");
        }
        List<String> refs = evidenceRefs.stream().map(this::uuid).toList();
        if (new HashSet<>(refs).size() != refs.size()) {
            throw new IllegalArgumentException("Duplicate evidence reference.");
        }
        Dispute snapshot = disputes.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Dispute does not exist."));
        Contract contract = contracts.findByIdForUpdate(snapshot.contractId())
                .orElseThrow(() -> new IllegalArgumentException("Contract does not exist."));
        if (admin.equals(contract.farmerId()) || admin.equals(contract.traderId())) {
            throw new IllegalArgumentException("Admin cannot decide their own Contract.");
        }
        PaymentTracking deposit = tracking.findByContractIdForUpdate(contract.contractId())
                .orElseThrow(() -> new IllegalStateException("Contract has no deposit tracking."));
        Dispute current = disputes.findByIdForUpdate(id).orElseThrow();
        DisputeDecision submitted = new DisputeDecision(decision, id, admin, expectedVersion,
                "MUTUAL_CANCEL", disposition, reason.trim(), refs);
        var previous = disputes.findDecision(decision);
        if (previous.isPresent()) {
            var previousTerms = settlements.findTerms(decision)
                    .orElseThrow(() -> new IllegalStateException("Decision is not a settlement."));
            if (!previous.get().equals(submitted) || previousTerms.liableAccountId() != null
                    || previousTerms.violationCode() != null || previousTerms.penaltyPoints() != null) {
                throw new IllegalStateException("Idempotency key was reused with different content.");
            }
            return current;
        }
        if (!"UNDER_REVIEW".equals(current.status())
                || !admin.equals(current.assignedAdminId())
                || current.stateVersion() != expectedVersion
                || !"ACTIVE".equals(contract.status())
                || !("LOCKED".equals(deposit.status()) || "RELEASABLE".equals(deposit.status()))
                || !current.depositHold() || !deposit.disputeHold()) {
            throw new IllegalStateException("Dispute, Contract or deposit is not eligible for mutual cancellation.");
        }
        boolean farmerEvidence = false;
        boolean traderEvidence = false;
        for (String ref : refs) {
            boolean farmer = evidence.belongsTo(ref, id, contract.farmerId());
            boolean trader = evidence.belongsTo(ref, id, contract.traderId());
            if (!farmer && !trader) {
                throw new IllegalArgumentException("Evidence must be submitted by a Contract participant.");
            }
            farmerEvidence |= farmer;
            traderEvidence |= trader;
        }
        if (!farmerEvidence || !traderEvidence) {
            throw new IllegalArgumentException("Evidence from both Contract participants is required.");
        }
        Instant now = clock.instant();
        disputes.insertDecision(submitted, now);
        settlements.insert(decision, deposit.paymentTrackingId(), contract.contractId(), admin,
                null, null, null, contract.traderId(), disposition,
                deposit.depositAmount(), BigDecimal.ZERO, BigDecimal.ZERO, now);
        contracts.markCancelled(contract.contractId(), contract.stateVersion() + 1, now);
        tracking.settleDispute(contract.contractId(), "REFUNDED", deposit.stateVersion() + 1, now);
        disputes.resolve(id, "MUTUAL_CANCEL", current.stateVersion() + 1,
                current.decisionVersion() + 1, now);
        Contract cancelled = contracts.findById(contract.contractId()).orElseThrow();
        PaymentTracking settled = tracking.findByContractId(contract.contractId()).orElseThrow();
        contractOutbox.statusChanged(cancelled, "ACTIVE", admin, now);
        depositOutbox.statusChanged(cancelled, settled, deposit.status(), admin, now);
        return disputes.findById(id).orElseThrow();
    }

    private String uuid(String value) {
        if (value == null) throw new IllegalArgumentException("ID must be a UUID.");
        try {
            String parsed = UUID.fromString(value).toString();
            if (!parsed.equalsIgnoreCase(value)) throw new IllegalArgumentException();
            return parsed;
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("ID must be a UUID.");
        }
    }
}
