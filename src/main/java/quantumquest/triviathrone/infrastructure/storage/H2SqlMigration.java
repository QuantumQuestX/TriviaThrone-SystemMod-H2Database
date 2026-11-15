package quantumquest.triviathrone.infrastructure.storage;

import quantumquest.triviathrone.api.storage.migration.IMigration;
import quantumquest.triviathrone.api.storage.migration.IMigrationContext;

import java.util.Objects;

record H2SqlMigration(int version, String description, String source) implements IMigration {
    H2SqlMigration {
        if (version < 1) {
            throw new IllegalArgumentException("Migration version must be positive");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Migration description must not be blank");
        }
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("Migration source must not be blank");
        }
    }

    @Override
    public void apply(IMigrationContext context) {
        Objects.requireNonNull(context, "context").execute(source);
    }
}
