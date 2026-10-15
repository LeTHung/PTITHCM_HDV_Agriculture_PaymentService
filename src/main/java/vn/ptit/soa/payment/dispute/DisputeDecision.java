package vn.ptit.soa.payment.dispute;

public record DisputeDecision(String decisionId, String disputeId, String adminId,
        long expectedVersion, String outcome, String depositDisposition, String reason) {}
