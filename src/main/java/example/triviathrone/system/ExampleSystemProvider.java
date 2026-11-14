package example.triviathrone.system;

import quantumquest.triviathrone.api.system.ISystemProvider;
import quantumquest.triviathrone.api.system.SystemApi;
import quantumquest.triviathrone.api.system.SystemCompatibility;
import quantumquest.triviathrone.api.system.SystemProviderContext;
import quantumquest.triviathrone.api.system.SystemProviderDescriptor;
import quantumquest.triviathrone.api.system.SystemServiceRegistry;
import quantumquest.triviathrone.api.system.events.ISystemEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.logging.Logger;

@SystemCompatibility(major = 2)
public final class ExampleSystemProvider implements ISystemProvider {
    private static final Logger LOGGER = Logger.getLogger(ExampleSystemProvider.class.getName());
    private static final SystemProviderDescriptor DESCRIPTOR =
            new SystemProviderDescriptor("example-system", "1.0.0", SystemApi.CURRENT_VERSION);

    private volatile String welcomeMessage = "Example system mod";

    @Override
    public SystemProviderDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public void initialize(SystemProviderContext context, SystemServiceRegistry services) throws IOException {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(services, "services");

        welcomeMessage = context.property("welcome-message")
                .filter(value -> !value.isBlank())
                .orElse(welcomeMessage);

        Files.writeString(
                context.dataDirectory().resolve("startup.log"),
                welcomeMessage + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        );
        LOGGER.info(welcomeMessage + " initialized.");
    }

    @Override
    public void onEvent(ISystemEvent event) {
        Objects.requireNonNull(event, "event");

        String message = welcomeMessage;
        LOGGER.fine(() -> message + " observed " + event.getClass().getSimpleName() + ".");
    }

    @Override
    public void close() {
        LOGGER.info(welcomeMessage + " stopped.");
    }
}
