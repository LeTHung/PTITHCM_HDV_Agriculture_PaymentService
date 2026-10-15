package vn.ptit.soa.payment.dispute;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("database-learning")
public class DisputeRepository {
    private final JdbcTemplate jdbc;

    public DisputeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(Dispute dispute) {
        jdbc.update("""
                INSERT INTO learning.disputes
                (dispute_id,contract_id,opened_by,reason_code,description,status,deposit_hold,
                 state_version,decision_version,created_at,updated_at)
                VALUES (?::uuid,?::uuid,?::uuid,?,?,?,?,?,?,?,?)
                """, dispute.disputeId(), dispute.contractId(), dispute.openedBy(),
                dispute.reasonCode(), dispute.description(), dispute.status(), dispute.depositHold(),
                dispute.stateVersion(), dispute.decisionVersion(),
                Timestamp.from(dispute.createdAt()), Timestamp.from(dispute.updatedAt()));
    }

    public Optional<Dispute> findById(String id) {
        return jdbc.query("SELECT * FROM learning.disputes WHERE dispute_id=?::uuid",
                this::map, id).stream().findFirst();
    }

    public Optional<Dispute> findByIdForUpdate(String id) {
        return jdbc.query("SELECT * FROM learning.disputes WHERE dispute_id=?::uuid FOR UPDATE",
                this::map, id).stream().findFirst();
    }

    public boolean activeExists(String contractId) {
        Boolean present = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM learning.disputes
                    WHERE contract_id=?::uuid AND status IN ('OPEN','UNDER_REVIEW'))
                """, Boolean.class, contractId);
        return Boolean.TRUE.equals(present);
    }

    public void review(String id, String previousAdmin, String adminId, String note,
            Instant responseDueAt, long version, Instant now) {
        int changed = jdbc.update("""
                UPDATE learning.disputes
                SET status='UNDER_REVIEW',assigned_admin_id=?::uuid,response_due_at=?,
                    state_version=?,updated_at=?
                WHERE dispute_id=?::uuid AND status IN ('OPEN','UNDER_REVIEW')
                """, adminId, Timestamp.from(responseDueAt), version, Timestamp.from(now), id);
        if (changed != 1) throw new IllegalStateException("Dispute was not reviewed.");
        jdbc.update("""
                INSERT INTO learning.dispute_reviews
                (review_id,dispute_id,previous_admin_id,admin_id,note,created_at)
                VALUES (?::uuid,?::uuid,?::uuid,?::uuid,?,?)
                """, UUID.randomUUID().toString(), id, previousAdmin, adminId,
                note, Timestamp.from(now));
    }

    public void resolve(String id, String outcome, long version, long decisionVersion, Instant now) {
        int changed = jdbc.update("""
                UPDATE learning.disputes
                SET status='RESOLVED',deposit_hold=false,resolved_at=?,outcome=?,
                    state_version=?,decision_version=?,updated_at=?
                WHERE dispute_id=?::uuid AND status='UNDER_REVIEW'
                """, Timestamp.from(now), outcome, version, decisionVersion,
                Timestamp.from(now), id);
        if (changed != 1) throw new IllegalStateException("Dispute was not resolved.");
    }

    public void insertDecision(DisputeDecision decision, Instant now) {
        jdbc.update("""
                INSERT INTO learning.dispute_decisions
                (decision_id,dispute_id,admin_id,expected_version,outcome,
                 deposit_disposition,reason,created_at)
                VALUES (?::uuid,?::uuid,?::uuid,?,?,?,?,?)
                """, decision.decisionId(), decision.disputeId(), decision.adminId(),
                decision.expectedVersion(), decision.outcome(),
                decision.depositDisposition(), decision.reason(), Timestamp.from(now));
    }

    public Optional<DisputeDecision> findDecision(String decisionId) {
        return jdbc.query("""
                SELECT decision_id::text,dispute_id::text,admin_id::text,expected_version,
                       outcome,deposit_disposition,reason
                FROM learning.dispute_decisions WHERE decision_id=?::uuid
                """, (rs, row) -> new DisputeDecision(rs.getString(1), rs.getString(2),
                        rs.getString(3), rs.getLong(4), rs.getString(5),
                        rs.getString(6), rs.getString(7)), decisionId).stream().findFirst();
    }

    private Dispute map(ResultSet rs, int row) throws SQLException {
        Timestamp responseDueAt = rs.getTimestamp("response_due_at");
        Timestamp resolvedAt = rs.getTimestamp("resolved_at");
        return new Dispute(rs.getString("dispute_id"), rs.getString("contract_id"),
                rs.getString("opened_by"), rs.getString("reason_code"),
                rs.getString("description"), rs.getString("status"),
                rs.getBoolean("deposit_hold"), rs.getString("assigned_admin_id"),
                responseDueAt == null ? null : responseDueAt.toInstant(),
                resolvedAt == null ? null : resolvedAt.toInstant(), rs.getString("outcome"),
                rs.getLong("state_version"), rs.getLong("decision_version"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }
}
