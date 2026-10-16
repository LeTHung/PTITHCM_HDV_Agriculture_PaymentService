package vn.ptit.soa.payment.contract;

import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("database-learning")
public class PaymentOutboxRelayRepository {
    private final JdbcTemplate jdbc;

    public PaymentOutboxRelayRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<PendingEvent> claim(String relayId) {
        var claimed = jdbc.query("""
                WITH candidate AS (
                    SELECT event_id FROM learning.outbox
                    WHERE published_at IS NULL
                      AND (lease_until IS NULL OR lease_until <= clock_timestamp())
                    ORDER BY created_at, event_id
                    FOR UPDATE SKIP LOCKED
                    LIMIT 1
                )
                UPDATE learning.outbox AS o
                SET claimed_by = ?::uuid,
                    lease_until = clock_timestamp() + INTERVAL '2 minutes',
                    attempt_count = attempt_count + 1
                FROM candidate AS c
                WHERE o.event_id = c.event_id
                RETURNING o.event_id::text, o.topic, o.event_key::text, o.payload::text, o.attempt_count
                """, (rs, row) -> new PendingEvent(rs.getString(1), rs.getString(2),
                    rs.getString(3), rs.getString(4), rs.getInt(5)), relayId);
        return claimed.stream().findFirst();
    }

    public boolean markPublished(String eventId, String relayId) {
        return jdbc.update("""
                UPDATE learning.outbox
                SET published_at = clock_timestamp(), claimed_by = NULL, lease_until = NULL
                WHERE event_id = ?::uuid AND claimed_by = ?::uuid AND published_at IS NULL
                """, eventId, relayId) == 1;
    }

    public void defer(String eventId, String relayId) {
        jdbc.update("""
                UPDATE learning.outbox
                SET claimed_by = NULL, lease_until = clock_timestamp() + INTERVAL '30 seconds'
                WHERE event_id = ?::uuid AND claimed_by = ?::uuid AND published_at IS NULL
                """, eventId, relayId);
    }

    public record PendingEvent(String eventId, String topic, String eventKey,
            String payload, int attemptCount) {}
}
