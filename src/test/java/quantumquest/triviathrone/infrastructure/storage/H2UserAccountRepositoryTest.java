package quantumquest.triviathrone.infrastructure.storage;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import quantumquest.triviathrone.auth.UserAccount;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class H2UserAccountRepositoryTest {
    @Test
    void createsAccountTablesWithUserIdAsPrimaryKeyAndPersistsIds() throws SQLException {
        DataSource dataSource = newDataSource();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        H2UserAccountRepository repository = newRepository(jdbcTemplate, dataSource);
        UserAccount account = new UserAccount();
        account.setUsername("player");
        account.setDisplayName("Trivia Player");
        account.setPasswordHash("hash");
        account.setRoles(Set.of("HOST"));
        repository.insertAll(List.of(account));

        UserAccount persistedAccount = newRepository(jdbcTemplate, dataSource).findAll().stream()
                .filter(candidate -> "player".equals(candidate.getUsername()))
                .findFirst()
                .orElseThrow();

        assertNotNull(persistedAccount.getUserId());
        assertTrue(persistedAccount.getUserId().matches("[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}"));
        assertEquals(account.getUserId(), persistedAccount.getUserId());
        assertEquals(Set.of("HOST"), persistedAccount.getRoles());
        assertUserIdIsPrimaryKey(dataSource);
        assertEquals(
                List.of(
                        "p001__create-account-tables.sql",
                        "p002__enable-user-accounts.sql",
                        "p003__mark-default-admin.sql",
                        "p004__seed-default-admin.sql",
                        "p005__create-refresh-token-sessions.sql"
                ),
                jdbcTemplate.queryForList(
                        "SELECT migration_src FROM triviathrone_schema_migrations ORDER BY migration_src",
                        String.class
                )
        );
    }

    @Test
    void updatesRolesAndDeletesUsersFromTheDatabase() {
        DataSource dataSource = newDataSource();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        H2UserAccountRepository repository = newRepository(jdbcTemplate, dataSource);
        UserAccount account = new UserAccount();
        account.setUsername("player");
        account.setPasswordHash("hash");
        account.setRoles(Set.of("USER"));
        repository.insert(account);

        repository.updateRoles("player", Set.of("USER", "ADMIN"));
        UserAccount player = repository.findAll().stream()
                .filter(user -> "player".equals(user.getUsername()))
                .findFirst()
                .orElseThrow();
        assertEquals(Set.of("USER", "ADMIN"), player.getRoles());
        assertFalse(player.isDefaultAdmin());
        repository.updateRoles("player", Set.of());
        player = repository.findAll().stream()
                .filter(user -> "player".equals(user.getUsername()))
                .findFirst()
                .orElseThrow();
        assertTrue(player.getRoles().isEmpty());
        repository.updateEnabled("player", false);
        player = repository.findAll().stream()
                .filter(user -> "player".equals(user.getUsername()))
                .findFirst()
                .orElseThrow();
        assertFalse(player.isEnabled());

        UserAccount admin = repository.findAll().stream()
                .filter(user -> "admin".equals(user.getUsername()))
                .findFirst()
                .orElseThrow();
        assertTrue(admin.isDefaultAdmin());
        assertThrows(IllegalStateException.class, () -> repository.delete("admin"));
        repository.delete("player");
        assertEquals(List.of("admin"), repository.findAll().stream().map(UserAccount::getUsername).toList());
    }

    @Test
    void generatesUniqueUuidV4IdsWithoutDashes() {
        String firstId = new UserAccount().getUserId();
        String secondId = new UserAccount().getUserId();

        assertTrue(firstId.matches("[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}"));
        assertTrue(secondId.matches("[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}"));
        assertNotEquals(firstId, secondId);
    }

    private H2UserAccountRepository newRepository(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
        return new H2UserAccountRepository(
                jdbcTemplate,
                transactionManager,
                new H2MigrationRunner(jdbcTemplate, transactionManager)
        );
    }

    private DataSource newDataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:account-" + java.util.UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    private void assertUserIdIsPrimaryKey(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             ResultSet primaryKeys = connection.getMetaData().getPrimaryKeys(null, null, "USER_ACCOUNTS")) {
            assertTrue(primaryKeys.next());
            assertEquals("USER_ID", primaryKeys.getString("COLUMN_NAME"));
            assertFalse(primaryKeys.next());
        }
    }
}
