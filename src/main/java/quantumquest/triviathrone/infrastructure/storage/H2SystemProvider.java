package quantumquest.triviathrone.infrastructure.storage;

import org.h2.jdbcx.JdbcConnectionPool;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import quantumquest.triviathrone.api.storage.migration.IMigrationRunner;
import quantumquest.triviathrone.api.system.ISystemProvider;
import quantumquest.triviathrone.api.system.SystemApi;
import quantumquest.triviathrone.api.system.SystemCompatibility;
import quantumquest.triviathrone.api.system.SystemProviderContext;
import quantumquest.triviathrone.api.system.SystemProviderDescriptor;
import quantumquest.triviathrone.api.system.SystemServiceRegistry;
import quantumquest.triviathrone.auth.IRefreshTokenSessionRepository;
import quantumquest.triviathrone.auth.IUserAccountRepository;

import java.util.Objects;

@SystemCompatibility(major = 2)
public final class H2SystemProvider implements ISystemProvider {
    private static final SystemProviderDescriptor DESCRIPTOR =
            new SystemProviderDescriptor("h2", "1.0.0", SystemApi.CURRENT_VERSION);
    private static final String DEFAULT_JDBC_URL = "jdbc:h2:file:./triviathrone-users;AUTO_SERVER=TRUE";
    private JdbcConnectionPool connectionPool;

    @Override
    public SystemProviderDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public void initialize(SystemProviderContext context, SystemServiceRegistry services) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(services, "META-INF/services");
        if (connectionPool != null) {
            throw new IllegalStateException("The H2 system provider is already initialized.");
        }

        String jdbcUrl = context.property("jdbc-url")
                .filter(value -> !value.isBlank())
                .orElse(DEFAULT_JDBC_URL);
        String username = context.property("username")
                .filter(value -> !value.isBlank())
                .orElse("sa");
        String password = context.property("password").orElse("");

        connectionPool = JdbcConnectionPool.create(jdbcUrl, username, password);

        JdbcTemplate jdbcTemplate = new JdbcTemplate(connectionPool);
        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(connectionPool);
        IMigrationRunner migrationRunner = new H2MigrationRunner(jdbcTemplate, transactionManager);
        IUserAccountRepository accountRepository = new H2UserAccountRepository(
                jdbcTemplate,
                transactionManager,
                migrationRunner
        );
        IRefreshTokenSessionRepository refreshTokenSessionRepository = new H2RefreshTokenSessionRepository(
                jdbcTemplate,
                transactionManager
        );

        services.register(IMigrationRunner.class, migrationRunner);
        services.register(IUserAccountRepository.class, accountRepository);
        services.register(IRefreshTokenSessionRepository.class, refreshTokenSessionRepository);
    }

    @Override
    public void close() {
        if (connectionPool != null) {
            connectionPool.dispose();
            connectionPool = null;
        }
    }
}
