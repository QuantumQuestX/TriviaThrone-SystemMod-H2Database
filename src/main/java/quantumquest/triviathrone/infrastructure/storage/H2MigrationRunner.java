package quantumquest.triviathrone.infrastructure.storage;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import quantumquest.triviathrone.api.storage.migration.IMigration;
import quantumquest.triviathrone.api.storage.migration.IMigrationProvider;
import quantumquest.triviathrone.api.storage.migration.IMigrationRunner;
import quantumquest.triviathrone.api.storage.migration.IMigrationStateStore;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class H2MigrationRunner implements IMigrationRunner {
    private static final String MIGRATION_DIRECTORY = "storage/h2/migrations";

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final IMigrationProvider migrationProvider;
    private final IMigrationStateStore migrationStateStore;

    public H2MigrationRunner(
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager
    ) {
        this(
                jdbcTemplate,
                new TransactionTemplate(Objects.requireNonNull(transactionManager, "transactionManager")),
                new H2MigrationProvider(),
                new H2MigrationStateStore(jdbcTemplate)
        );
    }

    H2MigrationRunner(
            JdbcTemplate jdbcTemplate,
            TransactionTemplate transactionTemplate,
            IMigrationProvider migrationProvider,
            IMigrationStateStore migrationStateStore
    ) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
        this.transactionTemplate = Objects.requireNonNull(transactionTemplate, "transactionTemplate");
        this.migrationProvider = Objects.requireNonNull(migrationProvider, "migrationProvider");
        this.migrationStateStore = Objects.requireNonNull(migrationStateStore, "migrationStateStore");
    }

    @Override
    public void migrate() {
        List<IMigration> migrations = List.copyOf(
                Objects.requireNonNull(migrationProvider.getMigrations(), "migrations")
        );
        validateMigrations(migrations);
        Set<Integer> appliedVersions = new HashSet<>(migrationStateStore.getAppliedVersions());

        for (IMigration migration : migrations) {
            if (appliedVersions.contains(migration.version())) {
                continue;
            }

            transactionTemplate.executeWithoutResult(status -> {
                migration.apply(this::executeScript);
                migrationStateStore.recordApplied(migration);
            });
            appliedVersions.add(migration.version());
        }
    }

    private void validateMigrations(List<IMigration> migrations) {
        if (migrations.isEmpty()) {
            throw new IllegalStateException("No H2 migrations are configured");
        }

        Set<Integer> versions = new HashSet<>();
        int previousVersion = 0;
        for (IMigration migration : migrations) {
            Objects.requireNonNull(migration, "migration");
            if (migration.version() < 1 || !versions.add(migration.version())) {
                throw new IllegalStateException("Invalid or duplicate migration version: " + migration.version());
            }
            if (migration.version() <= previousVersion) {
                throw new IllegalStateException("Migrations must be supplied in ascending version order");
            }
            if (migration.description() == null || migration.description().isBlank()) {
                throw new IllegalStateException("Migration " + migration.version() + " has no description");
            }
            if (migration.source() == null || migration.source().isBlank()) {
                throw new IllegalStateException("Migration " + migration.version() + " has no source");
            }
            previousVersion = migration.version();
        }
    }

    private void executeScript(String source) {
        H2MigrationProvider.validateSource(source);
        Resource script = new ClassPathResource(
                MIGRATION_DIRECTORY + "/" + source,
                H2MigrationRunner.class.getClassLoader()
        );
        if (!script.exists()) {
            throw new IllegalStateException("H2 migration resource does not exist: " + source);
        }

        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            ScriptUtils.executeSqlScript(
                    connection,
                    new EncodedResource(script, StandardCharsets.UTF_8)
            );
            return null;
        });
    }
}
