# TriviaThrone System Mod Template

This Maven project is a starting point for a system mod for the
[TriviaThrone](https://github.com/QuantumQuestX/TriviaThrone) server. System
mods are loaded during server startup and can provide infrastructure services,
react to server lifecycle events, and maintain provider-specific data.

The included `ExampleSystemProvider` reads a `welcome-message` setting, appends
it to `startup.log`, and logs each system event it receives.

## Requirements

- Java 25
- Maven 3.9 or later
- A TriviaThrone server compatible with system API 2.x

## Build and install

Build the mod JAR:

```sh
mvn package
```

Copy `target/example-system-mod-1.0.0-SNAPSHOT.jar` to the server's system-mod
directory, which defaults to `mods/system` relative to the server working
directory:

```sh
cp target/example-system-mod-1.0.0-SNAPSHOT.jar /path/to/triviathrone/mods/system/
```

Restart the server after installing or replacing a mod. Every JAR in the
system-mod directory must expose at least one valid system provider; a failed
provider prevents the server from starting.

## Configure the example

TriviaThrone passes properties under
`triviathrone.system.<provider-id>.` to the provider after removing that
prefix. The example provider ID is `example-system`, so configure it in the
server's `application.yaml` or another Spring configuration source:

```yaml
triviathrone:
  system:
    example-system:
      welcome-message: Hello from my system mod
```

The default mod and data directories can also be changed:

```yaml
triviathrone:
  mods:
    system-directory: mods/system
  system:
    data-directory: data/system
```

Each provider receives its own data directory:
`<system data directory>/<provider-id>`. For the example, the startup log is
written to `data/system/example-system/startup.log`.

## Turn this into your mod

Update all of the following together:

| Location                                                                 | Change                                                                                                               |
|--------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------|
| `pom.xml`                                                                | Set the group ID, artifact ID, version, and project name. Keep `triviathrone-system-api` as a `provided` dependency. |
| `ExampleSystemProvider.java`                                             | Rename the package and class, then set a unique lowercase descriptor ID, mod version, and provider behavior.         |
| `META-INF/services/quantumquest.triviathrone.api.system.ISystemProvider` | Replace the class name with the fully qualified name of your provider.                                               |

An `ISystemProvider` must have a public no-argument constructor, return a
unique `SystemProviderDescriptor`, and declare compatibility with the server
API. Keep `@SystemCompatibility(major = 2)` and use
`SystemApi.CURRENT_VERSION` unless intentionally targeting a different
compatible API version.

Implement initialization in `initialize`, where services may be registered via
`SystemServiceRegistry`. `onEvent` can be called concurrently, so its state
handling must be thread-safe. It receives lobby-created/closed,
player-joined/left, and game-started/ended events. Release resources in
`close`; providers are closed in reverse initialization order.

The system API is supplied by the TriviaThrone server and must not be bundled
in the mod. If the mod uses third-party libraries, package those dependencies
with the mod artifact (for example, in a shaded JAR) so its dedicated class
loader can resolve them.

## License

MIT. See [LICENSE](LICENSE).
