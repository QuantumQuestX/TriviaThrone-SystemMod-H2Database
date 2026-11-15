package quantumquest.triviathrone.infrastructure.storage;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import quantumquest.triviathrone.auth.IRefreshTokenSessionRepository;
import quantumquest.triviathrone.auth.RefreshTokenSession;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class H2RefreshTokenSessionRepository implements IRefreshTokenSessionRepository {
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public H2RefreshTokenSessionRepository(JdbcTemplate jdbcTemplate,
                                          DataSourceTransactionManager transactionManager) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
        this.transactionTemplate = new TransactionTemplate(Objects.requireNonNull(transactionManager,
                "transactionManager"));
    }

    @Override
    public Map<String, RefreshTokenSession> load() {
        Map<String, RefreshTokenSession> sessions = new HashMap<>();
        jdbcTemplate.query("SELECT token_digest, username, expires_at, absolute_expiry FROM refresh_token_sessions",
                resultSet -> {
                    String digest = resultSet.getString("token_digest");
                    String username = resultSet.getString("username");
                    Instant expiry = resultSet.getTimestamp("expires_at").toInstant();
                    Instant absoluteExpiry = resultSet.getTimestamp("absolute_expiry").toInstant();
                    Instant now = Instant.now();
                    if (now.isBefore(expiry) && now.isBefore(absoluteExpiry)) {
                        sessions.put(digest, new RefreshTokenSession(username, expiry, absoluteExpiry));
                    }
                });
        jdbcTemplate.update("DELETE FROM refresh_token_sessions WHERE expires_at <= CURRENT_TIMESTAMP "
                + "OR absolute_expiry <= CURRENT_TIMESTAMP");
        return sessions;
    }

    @Override
    public void save(Map<String, RefreshTokenSession> sessions) {
        Objects.requireNonNull(sessions, "sessions");
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.execute("DELETE FROM refresh_token_sessions");
            sessions.forEach((digest, session) -> jdbcTemplate.update("""
                    INSERT INTO refresh_token_sessions (token_digest, username, expires_at, absolute_expiry)
                    VALUES (?, ?, ?, ?)
                    """, digest, session.username(), java.sql.Timestamp.from(session.expiresAt()),
                    java.sql.Timestamp.from(session.absoluteExpiry())));
        });
    }
}
