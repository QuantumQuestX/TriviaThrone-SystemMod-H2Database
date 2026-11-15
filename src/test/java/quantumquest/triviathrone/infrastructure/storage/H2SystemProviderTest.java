package quantumquest.triviathrone.infrastructure.storage;

import org.junit.jupiter.api.Test;
import quantumquest.triviathrone.api.storage.migration.IMigrationRunner;
import quantumquest.triviathrone.api.system.ISystemProvider;
import quantumquest.triviathrone.api.system.SystemProviderContext;
import quantumquest.triviathrone.api.system.SystemServiceRegistry;
import quantumquest.triviathrone.auth.IRefreshTokenSessionRepository;
import quantumquest.triviathrone.auth.IUserAccountRepository;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class H2SystemProviderTest {
    @Test
    void isRegisteredAsSystemProviderService() {
        assertTrue(ServiceLoader.load(ISystemProvider.class)
                .stream()
                .anyMatch(provider -> provider.type().equals(H2SystemProvider.class)));
    }

    @Test
    void initializesAndRegistersStorageServices() throws Exception {
        H2SystemProvider provider = new H2SystemProvider();
        TestSystemServiceRegistry services = new TestSystemServiceRegistry();
        SystemProviderContext context = new SystemProviderContext(
                Path.of("target", "test-system-data"),
                Map.of(
                        "jdbc-url", "jdbc:h2:mem:h2-system-provider;DB_CLOSE_DELAY=-1",
                        "username", "sa",
                        "password", ""
                )
        );

        assertEquals("h2", provider.descriptor().id());
        try {
            provider.initialize(context, services);

            IMigrationRunner migrationRunner = services.find(IMigrationRunner.class).orElseThrow();
            IUserAccountRepository accountRepository = services.find(IUserAccountRepository.class).orElseThrow();
            IRefreshTokenSessionRepository refreshTokenSessionRepository =
                    services.find(IRefreshTokenSessionRepository.class).orElseThrow();
            assertTrue(migrationRunner instanceof H2MigrationRunner);
            assertEquals("admin", accountRepository.findAll().getFirst().getUsername());
            assertTrue(refreshTokenSessionRepository.load().isEmpty());
        } finally {
            provider.close();
        }
    }

    private static final class TestSystemServiceRegistry implements SystemServiceRegistry {
        private final Map<Class<?>, Object> services = new HashMap<>();

        @Override
        public <T> void register(Class<T> serviceType, T service) {
            if (!serviceType.isInterface()) {
                throw new IllegalArgumentException("System services must be registered by interface type.");
            }
            if (services.putIfAbsent(serviceType, service) != null) {
                throw new IllegalStateException("Service already registered: " + serviceType.getName());
            }
        }

        @Override
        public <T> Optional<T> find(Class<T> serviceType) {
            return Optional.ofNullable(serviceType.cast(services.get(serviceType)));
        }
    }
}
