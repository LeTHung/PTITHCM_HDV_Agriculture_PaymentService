package vn.ptit.soa.payment.dispute;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.ptit.soa.payment.contract.Contract;

@Repository
@Profile("database-learning")
public class ReputationDecisionOutboxRepository {
    private static final String TOPIC = "soa.payment.reputation-decision.v1";
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public ReputationDecisionOutboxRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void penalty(Contract contract, String decisionId, long decisionVersion,
            String liableAccountId, String violationCode, BigDecimal points,
            String adminId, Instant now) {
        String counterpartyId = liableAccountId.equals(contract.farmerId())
                ? contract.traderId() : contract.farmerId();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("decisionId", decisionId);
        data.put("decisionVersion", decisionVersion);
        data.put("contractId", contract.contractId());
        data.put("accountId", liableAccountId);
        data.put("counterpartyId", counterpartyId);
        data.put("action", "PENALTY");
        data.put("violationCode", violationCode);
        data.put("requestedDelta", points.negate());
        data.put("reasonCode", violationCode);
        data.put("effectiveAt", now.toString());
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId);
        envelope.put("eventType", "CONTRACT_REPUTATION_DECIDED");
        envelope.put("eventVersion", 1);
        envelope.put("sourceService", "payment-service");
        envelope.put("occurredAt", now.toString());
        envelope.put("actorId", adminId);
        envelope.put("recipientIds", List.of(liableAccountId));
        envelope.put("referenceType", "CONTRACT");
        envelope.put("referenceId", contract.contractId());
        envelope.put("data", data);
        try {
            jdbc.update("""
                    INSERT INTO learning.outbox
                    (event_id,event_type,aggregate_id,topic,event_key,payload,created_at)
                    VALUES (?::uuid,?,?::uuid,?,?::uuid,?::jsonb,?)
                    """, eventId, "CONTRACT_REPUTATION_DECIDED", contract.contractId(),
                    TOPIC, liableAccountId, json.writeValueAsString(envelope), Timestamp.from(now));
        } catch (JsonProcessingException invalid) {
            throw new IllegalStateException("Cannot serialize reputation decision event.", invalid);
        }
    }
}
