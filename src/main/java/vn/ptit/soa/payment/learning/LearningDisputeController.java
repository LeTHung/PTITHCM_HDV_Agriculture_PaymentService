package vn.ptit.soa.payment.learning;

import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.ptit.soa.payment.dispute.Dispute;
import vn.ptit.soa.payment.dispute.DisputeService;
import vn.ptit.soa.payment.dispute.AdminDisputeListService;

@RestController
@Profile("learning & database-learning")
@RequestMapping("/learning")
public class LearningDisputeController {
    private final DisputeService disputes;
    private final AdminDisputeListService adminList;

    public LearningDisputeController(DisputeService disputes, AdminDisputeListService adminList) {
        this.disputes = disputes;
        this.adminList = adminList;
    }

    @PostMapping("/contracts/{contractId}/disputes")
    @ResponseStatus(HttpStatus.CREATED)
    public Dispute open(@PathVariable String contractId, @RequestBody OpenRequest request) {
        return disputes.open(contractId, request.actorId(), request.reasonCode(),
                request.description(), request.evidenceRefs());
    }

    @GetMapping("/disputes/{disputeId}")
    public Dispute find(@PathVariable String disputeId, @RequestParam String actorId) {
        return disputes.findForParticipant(disputeId, actorId);
    }

    @GetMapping("/admin/disputes/{disputeId}")
    public Dispute findForAdmin(@PathVariable String disputeId, @RequestParam String adminId) {
        return disputes.findForAdmin(disputeId, adminId);
    }

    @GetMapping("/admin/disputes")
    public AdminDisputeListService.Page listForAdmin(@RequestParam String adminId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return adminList.list(adminId, status, cursor, limit);
    }

    @PostMapping("/admin/disputes/{disputeId}/review")
    public Dispute review(@PathVariable String disputeId, @RequestBody ReviewRequest request) {
        return disputes.review(disputeId, request.adminId(), request.note(), request.expectedVersion());
    }

    @PostMapping("/admin/disputes/{disputeId}/decisions")
    public Dispute closeSafely(@PathVariable String disputeId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody SafeDecisionRequest request) {
        return disputes.closeSafely(disputeId, request.decisionId(), idempotencyKey,
                request.adminId(), request.expectedVersion(), request.outcome(),
                request.depositDisposition(), request.reason(), request.evidenceRefs(),
                request.liableAccountId(), request.violationCode(), request.penaltyPoints());
    }

    public record OpenRequest(String actorId, String reasonCode, String description,
            List<String> evidenceRefs) {}

    public record ReviewRequest(String adminId, String note, Long expectedVersion) {}

    public record SafeDecisionRequest(String decisionId, String adminId, long expectedVersion,
            String outcome, String depositDisposition, String reason, List<String> evidenceRefs,
            String liableAccountId, String violationCode, String penaltyPoints) {}
}
