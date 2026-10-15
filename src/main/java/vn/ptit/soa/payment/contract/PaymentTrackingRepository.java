package vn.ptit.soa.payment.contract;

import java.sql.Timestamp;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("database-learning")
public class PaymentTrackingRepository {
    private final JdbcTemplate jdbc;

    public PaymentTrackingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public PaymentTracking insert(PaymentTracking tracking) {
        jdbc.update("""
                INSERT INTO learning.payment_tracking
                (payment_tracking_id,contract_id,deposit_rate,deposit_amount,status,
                 delivery_date,dispute_hold,state_version,created_at,updated_at)
                VALUES (?::uuid,?::uuid,?,?,?,?,?,?,?,?)
                """, tracking.paymentTrackingId(), tracking.contractId(),
                tracking.depositRate(), tracking.depositAmount(), tracking.status(),
                Timestamp.from(tracking.deliveryDate()), tracking.disputeHold(),
                tracking.stateVersion(), Timestamp.from(tracking.createdAt()),
                Timestamp.from(tracking.updatedAt()));
        return tracking;
    }

    public Optional<PaymentTracking> findByContractId(String contractId) {
        return jdbc.query("SELECT * FROM learning.payment_tracking WHERE contract_id=?::uuid",
                this::map, contractId).stream().findFirst();
    }

    public Optional<PaymentTracking> findByContractIdForUpdate(String contractId) {
        return jdbc.query("SELECT * FROM learning.payment_tracking WHERE contract_id=?::uuid FOR UPDATE",
                this::map, contractId).stream().findFirst();
    }

    public void markLocked(String contractId, long stateVersion, java.time.Instant now) {
        int changed = jdbc.update("""
                UPDATE learning.payment_tracking
                SET status='LOCKED',paid_at=?,state_version=?,updated_at=?
                WHERE contract_id=?::uuid AND status='PENDING'
                """, Timestamp.from(now), stateVersion, Timestamp.from(now), contractId);
        if (changed != 1) throw new IllegalStateException("Deposit was not locked.");
    }

    private PaymentTracking map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new PaymentTracking(rs.getString("payment_tracking_id"),
                        rs.getString("contract_id"), rs.getBigDecimal("deposit_rate"),
                        rs.getBigDecimal("deposit_amount"), rs.getString("status"),
                        rs.getTimestamp("paid_at") == null ? null : rs.getTimestamp("paid_at").toInstant(),
                        rs.getTimestamp("delivery_date").toInstant(),
                        rs.getTimestamp("completed_confirmed_at") == null ? null
                                : rs.getTimestamp("completed_confirmed_at").toInstant(),
                        rs.getTimestamp("release_at") == null ? null : rs.getTimestamp("release_at").toInstant(),
                        rs.getTimestamp("released_at") == null ? null : rs.getTimestamp("released_at").toInstant(),
                        rs.getBoolean("dispute_hold"), rs.getLong("state_version"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant());
    }
}
