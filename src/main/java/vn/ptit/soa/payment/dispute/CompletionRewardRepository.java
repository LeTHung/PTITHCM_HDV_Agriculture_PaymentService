package vn.ptit.soa.payment.dispute;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("database-learning")
public class CompletionRewardRepository {
    private final JdbcTemplate jdbc;

    public CompletionRewardRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<String> findDue(Instant cutoff, int limit) {
        return jdbc.query("""
                SELECT c.contract_id::text FROM learning.contracts c
                WHERE c.status='COMPLETED' AND c.completed_confirmed_at<=?
                  AND (NOT EXISTS (SELECT 1 FROM learning.completion_rewards r
                       WHERE r.contract_id=c.contract_id AND r.account_id=c.farmer_id)
                    OR NOT EXISTS (SELECT 1 FROM learning.completion_rewards r
                       WHERE r.contract_id=c.contract_id AND r.account_id=c.trader_id))
                  AND NOT EXISTS (SELECT 1 FROM learning.disputes d
                       WHERE d.contract_id=c.contract_id AND d.status IN ('OPEN','UNDER_REVIEW'))
                  AND NOT EXISTS (SELECT 1 FROM learning.disputes d
                       JOIN learning.dispute_decisions x USING(dispute_id)
                       WHERE d.contract_id=c.contract_id
                         AND x.outcome IN ('FARMER_VIOLATION','TRADER_VIOLATION')
                         AND NOT EXISTS (SELECT 1 FROM learning.decision_reversals v
                                         WHERE v.original_decision_id=x.decision_id))
                ORDER BY c.completed_confirmed_at,c.contract_id LIMIT ?
                """, (rs, row) -> rs.getString(1), Timestamp.from(cutoff), limit);
    }

    public boolean blocked(String contractId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM learning.disputes d
                    WHERE d.contract_id=?::uuid AND d.status IN ('OPEN','UNDER_REVIEW'))
                OR EXISTS (SELECT 1 FROM learning.disputes d
                    JOIN learning.dispute_decisions x USING(dispute_id)
                    WHERE d.contract_id=?::uuid
                      AND x.outcome IN ('FARMER_VIOLATION','TRADER_VIOLATION')
                      AND NOT EXISTS (SELECT 1 FROM learning.decision_reversals v
                                      WHERE v.original_decision_id=x.decision_id))
                """, Boolean.class, contractId, contractId));
    }

    public boolean insert(String contractId, String accountId, String decisionId, Instant now) {
        return jdbc.update("""
                INSERT INTO learning.completion_rewards(contract_id,account_id,decision_id,created_at)
                VALUES (?::uuid,?::uuid,?::uuid,?)
                ON CONFLICT (contract_id,account_id) DO NOTHING
                """, contractId, accountId, decisionId, Timestamp.from(now)) == 1;
    }
}
