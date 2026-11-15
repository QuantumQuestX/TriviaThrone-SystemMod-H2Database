package quantumquest.triviathrone.infrastructure.storage;

import org.springframework.jdbc.core.JdbcTemplate;
import quantumquest.triviathrone.api.storage.migration.IMigration;
import quantumquest.triviathrone.api.storage.migration.IMigrationStateStore;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

final class H2MigrationStateStore implements IMigrationStateStore {
    private static final String MIGRATION_TABLE = "triviathrone_schema_migrations";

    private final JdbcTemplate jdbcTemplate;

    H2MigrationStateStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    @Override
    public List<Integer> getAppliedVersions() {
        ensureTableExists();
        return jdbcTemplate.queryForList(
                        "SELECT migration_src FROM " + MIGRATION_TABLE,
                        String.class
                ).stream()
                .map(H2MigrationProvider::versionFromSource)
                .toList();
    }

    @Override
    public void recordApplied(IMigration migration) {
        IMigration appliedMigration = Objects.requireNonNull(migration, "migration");
        jdbcTemplate.update(
                "INSERT INTO " + MIGRATION_TABLE + " (migration_src, applied_at) VALUES (?, ?)",
                appliedMigration.source(),
                Timestamp.from(Instant.now())
        );
    }

    private void ensureTableExists() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS triviathrone_schema_migrations (
                    migration_src VARCHAR(512) PRIMARY KEY,
                    applied_at TIMESTAMP NOT NULL
                )
                """);
    }
}
