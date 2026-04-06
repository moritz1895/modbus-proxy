package ms.rohde.modbusproxy.adapters.health;

import ms.rohde.modbusproxy.adapters.outbound.UpstreamConnector;
import ms.rohde.modbusproxy.core.app.RequestDispatcher;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Exposes proxy health via Spring Boot Actuator's {@code /actuator/health} endpoint.
 *
 * <p>Reports {@code DOWN} if the upstream inverter is not reachable, so that
 * monitoring systems and Docker health checks can react to connectivity loss.</p>
 */
@Component
public class ModbusProxyHealthIndicator implements HealthIndicator {

    private final UpstreamConnector upstreamConnector;
    private final RequestDispatcher requestDispatcher;

    public ModbusProxyHealthIndicator(UpstreamConnector upstreamConnector,
                                      RequestDispatcher requestDispatcher) {
        this.upstreamConnector = upstreamConnector;
        this.requestDispatcher = requestDispatcher;
    }

    @Override
    public Health health() {
        boolean connected = upstreamConnector.isConnected();
        Health.Builder builder = connected ? Health.up() : Health.down();

        return builder
                .withDetail("upstreamConnected", connected)
                .withDetail("dispatcherRunning", requestDispatcher.isRunning())
                .withDetail("queueSize", requestDispatcher.queueSize())
                .build();
    }
}
