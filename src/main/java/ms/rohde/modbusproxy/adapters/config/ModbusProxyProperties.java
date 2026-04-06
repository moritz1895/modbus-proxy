package ms.rohde.modbusproxy.adapters.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Type-safe configuration for the Modbus proxy, loaded from {@code application.yml}.
 * All values are validated at startup – the application refuses to start if invalid.
 */
@ConfigurationProperties(prefix = "modbus")
@Validated
public record ModbusProxyProperties(

        @Valid ProxyProperties proxy,
        @Valid UpstreamProperties upstream,
        @Valid QueueProperties queue

) {

    public record ProxyProperties(

            @Min(1) @Max(65535)
            int port,

            @Positive
            int maxClients

    ) {}

    public record UpstreamProperties(

            @NotBlank
            String host,

            @Min(1) @Max(65535)
            int port,

            @Positive
            long requestTimeoutMs,

            @Positive
            long reconnectInitialDelayMs,

            @Positive
            long reconnectMaxDelayMs

    ) {}

    public record QueueProperties(

            @Positive
            int capacity,

            @Positive
            long enqueueTimeoutMs

    ) {}
}
