package vn.ptit.soa.payment.learning;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.ptit.soa.payment.contract.DepositFinalizer;
import vn.ptit.soa.payment.contract.DepositService;
import vn.ptit.soa.payment.contract.PaymentTracking;

@RestController
@Profile("learning & database-learning")
@RequestMapping("/learning/deposits")
public class LearningDepositController {
    private final DepositService deposits;

    public LearningDepositController(DepositService deposits) {
        this.deposits = deposits;
    }

    @GetMapping("/{contractId}")
    public PaymentTracking find(@PathVariable String contractId) {
        return deposits.find(contractId);
    }

    @PostMapping("/{contractId}/pay")
    public DepositFinalizer.PayResult pay(@PathVariable String contractId,
            @RequestBody PayRequest request) {
        return deposits.pay(contractId, request.traderId());
    }

    public record PayRequest(String traderId) {}
}
