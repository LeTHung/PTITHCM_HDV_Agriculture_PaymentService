package vn.ptit.soa.payment.contract;

import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("database-learning")
public class DepositService {
    private final ContractRepository contracts;
    private final PaymentTrackingRepository tracking;
    private final TradingDepositClient trading;
    private final DepositFinalizer finalizer;

    public DepositService(ContractRepository contracts, PaymentTrackingRepository tracking,
            TradingDepositClient trading, DepositFinalizer finalizer) {
        this.contracts = contracts;
        this.tracking = tracking;
        this.trading = trading;
        this.finalizer = finalizer;
    }

    // Learning-only: a production endpoint must derive traderId from a verified JWT.
    public DepositFinalizer.PayResult pay(String contractId, String traderId) {
        String id = uuid(contractId);
        String actor = uuid(traderId);
        Contract contract = contracts.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Contract does not exist."));
        if (!contract.traderId().equals(actor)) {
            throw new IllegalArgumentException("Only this Contract's trader can pay its deposit.");
        }
        PaymentTracking deposit = tracking.findByContractId(id)
                .orElseThrow(() -> new IllegalStateException("Contract has no deposit tracking."));
        if ("ACTIVE".equals(contract.status()) && "LOCKED".equals(deposit.status())) {
            return finalizer.finish(id, actor, deposit.paymentTrackingId());
        }
        if (!"PENDING_DEPOSIT".equals(contract.status()) || !"PENDING".equals(deposit.status())) {
            throw new IllegalStateException("Contract is not awaiting a deposit.");
        }
        trading.commit(contract, deposit);
        return finalizer.finish(id, actor, deposit.paymentTrackingId());
    }

    public PaymentTracking find(String contractId) {
        return tracking.findByContractId(uuid(contractId))
                .orElseThrow(() -> new IllegalArgumentException("Deposit does not exist."));
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
