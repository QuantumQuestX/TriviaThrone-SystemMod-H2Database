package quantumquest.triviathrone.infrastructure.storage;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import quantumquest.triviathrone.api.storage.migration.IMigrationRunner;
import quantumquest.triviathrone.auth.IUserAccountRepository;
import quantumquest.triviathrone.auth.UserAccount;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class H2UserAccountRepository implements IUserAccountRepository {
    private static final String SQL_STATEMENT__FIND_ALL = """
                SELECT account.user_id, account.username, account.display_name, account.password_hash, account.enabled, account.default_admin, account_role.role_name
                FROM user_accounts account
                LEFT JOIN user_account_roles account_role ON account_role.username = account.username
                ORDER BY account.username, account_role.role_name
                """;
    private static final String SQL_STATEMENT__INSERT_ALL = "INSERT INTO user_accounts (user_id, username, display_name, password_hash, enabled, default_admin) VALUES (?, ?, ?, ?, ?, ?)";
    private static final String SQL_STATEMENT__INSERT_ALL_ROLES = "INSERT INTO user_account_roles (username, role_name) VALUES (?, ?)";
    private static final String SQL_STATEMENT__UPDATE_PASSWORD = "UPDATE user_accounts SET password_hash = ? WHERE username = ?";
    private static final String SQL_STATEMENT__UPDATE_ROLES = "DELETE FROM user_account_roles WHERE username = ?";
    private static final String SQL_STATEMENT__UPDATE_ENABLED = "UPDATE user_accounts SET enabled = ? WHERE username = ?";
    private static final String SQL_STATEMENT__UPDATE_DEFAULT_ADMIN = "UPDATE user_accounts SET default_admin = ? WHERE username = ?";

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public H2UserAccountRepository(
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            IMigrationRunner migrationRunner
    ) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
        this.transactionTemplate = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "transactionManager")
        );
        Objects.requireNonNull(migrationRunner, "migrationRunner").migrate();
    }

    @Override
    public List<UserAccount> findAll() {
        return jdbcTemplate.query(SQL_STATEMENT__FIND_ALL, this::readAccounts);
    }

    @Override
    public void insertAll(List<UserAccount> accounts) {
        Objects.requireNonNull(accounts, "accounts");
        transactionTemplate.executeWithoutResult(status -> {
            for (UserAccount account : accounts) {
                UserAccount user = Objects.requireNonNull(account, "account");

                jdbcTemplate.update(
                        SQL_STATEMENT__INSERT_ALL,
                        Objects.requireNonNull(user.getUserId(), "userId"),
                        Objects.requireNonNull(user.getUsername(), "username"),
                        user.getDisplayName(),
                        Objects.requireNonNull(user.getPasswordHash(), "passwordHash"),
                        user.isEnabled(),
                        user.isDefaultAdmin()
                );

                for (String role : user.getRoles()) {
                    jdbcTemplate.update(
                            SQL_STATEMENT__INSERT_ALL_ROLES,
                            user.getUsername(),
                            role
                    );
                }
            }
        });
    }

    @Override
    public void insert(UserAccount account) {
        UserAccount user = Objects.requireNonNull(account, "account");
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.update(
                    SQL_STATEMENT__INSERT_ALL,
                    Objects.requireNonNull(user.getUserId(), "userId"),
                    Objects.requireNonNull(user.getUsername(), "username"),
                    user.getDisplayName(),
                    Objects.requireNonNull(user.getPasswordHash(), "passwordHash"),
                    user.isEnabled(),
                    user.isDefaultAdmin()
            );
            insertRoles(user.getUsername(), user.getRoles());
        });
    }

    @Override
    public void updatePassword(String username, String passwordHash) {
        int updatedRows = jdbcTemplate.update(
                SQL_STATEMENT__UPDATE_PASSWORD,
                Objects.requireNonNull(passwordHash, "passwordHash"),
                Objects.requireNonNull(username, "username")
        );
        if (updatedRows != 1) {
            throw new IllegalArgumentException("User not found");
        }
    }

    @Override
    public void updateRoles(String username, Set<String> roles) {
        Objects.requireNonNull(username, "username");
        Set<String> accountRoles = Set.copyOf(Objects.requireNonNull(roles, "roles"));
        transactionTemplate.executeWithoutResult(status -> {
            if (jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM user_accounts WHERE username = ?", Integer.class, username
            ) != 1) {
                throw new IllegalArgumentException("User not found");
            }
            jdbcTemplate.update(SQL_STATEMENT__UPDATE_ROLES, username);
            insertRoles(username, accountRoles);
        });
    }

    @Override
    public void updateEnabled(String username, boolean enabled) {
        int updatedRows = jdbcTemplate.update(SQL_STATEMENT__UPDATE_ENABLED, enabled,
                Objects.requireNonNull(username, "username"));
        if (updatedRows != 1) {
            throw new IllegalArgumentException("User not found");
        }
    }

    @Override
    public void updateDefaultAdmin(String username, boolean defaultAdmin) {
        int updatedRows = jdbcTemplate.update(SQL_STATEMENT__UPDATE_DEFAULT_ADMIN, defaultAdmin,
                Objects.requireNonNull(username, "username"));
        if (updatedRows != 1) {
            throw new IllegalArgumentException("User not found");
        }
    }

    @Override
    public void delete(String username) {
        String accountName = Objects.requireNonNull(username, "username");
        int deletedRows = jdbcTemplate.update(
                "DELETE FROM user_accounts WHERE username = ? AND default_admin = FALSE",
                accountName
        );
        if (deletedRows != 1) {
            List<Boolean> defaultAdminRows = jdbcTemplate.queryForList(
                    "SELECT default_admin FROM user_accounts WHERE username = ?",
                    Boolean.class,
                    accountName
            );
            if (defaultAdminRows.isEmpty()) {
                throw new IllegalArgumentException("User not found");
            }
            throw new IllegalStateException("The default admin account cannot be deleted");
        }
    }

    private void insertRoles(String username, Set<String> roles) {
        for (String role : roles) {
            jdbcTemplate.update(SQL_STATEMENT__INSERT_ALL_ROLES, username, role);
        }
    }

    private List<UserAccount> readAccounts(ResultSet resultSet) throws SQLException {
        Map<String, UserAccount> accounts = new LinkedHashMap<>();
        Map<String, Set<String>> rolesByUsername = new HashMap<>();
        while (resultSet.next()) {
            String userId = resultSet.getString("user_id");
            String username = resultSet.getString("username");
            UserAccount account = accounts.get(username);
            if (account == null) {
                UserAccount user = new UserAccount();
                user.setUserId(userId);
                user.setUsername(username);
                user.setDisplayName(resultSet.getString("display_name"));
                user.setPasswordHash(resultSet.getString("password_hash"));
                user.setEnabled(resultSet.getBoolean("enabled"));
                user.setDefaultAdmin(resultSet.getBoolean("default_admin"));
                accounts.put(username, user);
                account = user;
            }
            String role = resultSet.getString("role_name");
            if (role != null) {
                rolesByUsername.computeIfAbsent(account.getUsername(), key -> new HashSet<>()).add(role);
            }
        }
        accounts.forEach((username, account) ->
                account.setRoles(rolesByUsername.getOrDefault(username, Set.of())));
        return new ArrayList<>(accounts.values());
    }
}
