# TriviaThrone H2 Infrastructure

H2-backed infrastructure provider for the
[TriviaThrone](https://github.com/QuantumQuestX/TriviaThrone) server. The
mod implements persistent user accounts, refresh-token sessions, and
database migrations through the TriviaThrone system API.

The provider is registered as `h2` and exposes these services:

| Service                          | Implementation                    |
|----------------------------------|-----------------------------------|
| `IMigrationRunner`               | `H2MigrationRunner`               |
| `IUserAccountRepository`         | `H2UserAccountRepository`         |
| `IRefreshTokenSessionRepository` | `H2RefreshTokenSessionRepository` |

## Requirements

- Java 25
- Maven 3.9+
- TriviaThrone server and system API 2.0.1

The system API is supplied by the server and is kept as a `provided`
dependency. It is not bundled into the mod.

## Build

Build the mod and its shaded H2 artifact:

```sh
mvn clean package
```

The installable artifact is:

```text
target/triviathrone-infrastructure-1.0.1-SNAPSHOT-mod.jar
```

The Maven build also copies this file to `../mods/system` when that directory
is available. To install manually, copy it to the server's configured system
mod directory and restart the server.

## Configuration

TriviaThrone passes provider properties from
`triviathrone.system.h2`. All properties are optional:

```yaml
triviathrone:
  system:
    h2:
      jdbc-url: "jdbc:h2:file:./triviathrone-users;AUTO_SERVER=TRUE"
      username: sa
      password: ""
```

Defaults:

| Property   | Default                                              |
|------------|------------------------------------------------------|
| `jdbc-url` | `jdbc:h2:file:./triviathrone-users;AUTO_SERVER=TRUE` |
| `username` | `sa`                                                 |
| `password` | empty                                                |

The H2 database is created by H2 when it does not already exist. Choose a
location writable by the server process and protect the configured password.

The server's system mod and data directories can be configured separately:

```yaml
triviathrone:
  mods:
    system-directory: mods/system
  system:
    data-directory: data/system
```

## Database migrations

Migrations are stored in
`src/main/resources/storage/h2/migrations`. Files must use the naming format
`p<number>__<description>.sql`, for example:

```text
p006__add-example-column.sql
```

The `process-classes` build step generates
`storage/h2/migrations/index.xml` from the SQL files. At startup, migrations
are applied in ascending version order and recorded in the migration state
table. Already-applied versions are skipped.

Do not edit the generated `target/classes/storage/h2/migrations/index.xml`.
Add a new numbered SQL file instead. Applied migrations must be treated as
immutable because changing them can make existing databases inconsistent.

## Project structure

```text
src/main/java/quantumquest/triviathrone/infrastructure/storage/
  H2SystemProvider.java
  H2UserAccountRepository.java
  H2RefreshTokenSessionRepository.java
  H2MigrationRunner.java
  H2MigrationProvider.java
  H2MigrationStateStore.java
  H2SqlMigration.java
src/main/resources/storage/h2/migrations/
src/main/resources/META-INF/services/
```

`H2SystemProvider` creates the H2 connection pool, wires Spring JDBC
transactions, runs migrations through the registered migration runner, and
registers the account and refresh-token repositories. The pool is disposed
when the provider is closed.

## Tests

Run the test suite with:

```sh
mvn test
```

Tests cover migration discovery, migration execution, provider registration,
account persistence, and refresh-token persistence.

## License

MIT. See [LICENSE](LICENSE).
