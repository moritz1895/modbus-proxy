package ms.rohde.modbusproxy.adapters.health;

import ms.rohde.modbusproxy.core.app.ErrorEntry;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Actuator endpoint that exposes the last proxy errors.
 *
 * <p>Reachable at {@code GET /actuator/errors}.</p>
 */
@Component
@Endpoint(id = "errors")
public class ErrorsEndpoint {

    private final ErrorRegistry registry;

    public ErrorsEndpoint(ErrorRegistry registry) {
        this.registry = registry;
    }

    /**
     * Returns the most recent proxy errors, oldest first.
     */
    @ReadOperation
    public ErrorsDescriptor errors() {
        return new ErrorsDescriptor(registry.recent());
    }

    public record ErrorsDescriptor(List<ErrorEntry> errors) {}
}
