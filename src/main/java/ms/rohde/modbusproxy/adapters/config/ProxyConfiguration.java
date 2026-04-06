package ms.rohde.modbusproxy.adapters.config;

import ms.rohde.modbusproxy.adapters.inbound.TcpServer;
import ms.rohde.modbusproxy.adapters.outbound.UpstreamConnector;
import ms.rohde.modbusproxy.core.app.RequestDispatcher;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires together all components of the Modbus proxy.
 *
 * <p>Lifecycle phases ensure correct start/stop ordering:</p>
 * <ol>
 *   <li>Phase 100 – {@link UpstreamConnector}: establishes the inverter connection first</li>
 *   <li>Phase 200 – {@link RequestDispatcher}: starts the dispatch loop once upstream is ready</li>
 *   <li>Phase 300 – {@link TcpServer}: accepts client connections last, stops first</li>
 * </ol>
 */
@Configuration
@EnableConfigurationProperties(ModbusProxyProperties.class)
public class ProxyConfiguration {

    @Bean
    public UpstreamConnector upstreamConnector(ModbusProxyProperties properties) {
        return new UpstreamConnector(properties.upstream());
    }

    @Bean
    public RequestDispatcher requestDispatcher(UpstreamConnector upstreamConnector,
                                               ModbusProxyProperties properties) {
        return new RequestDispatcher(
                upstreamConnector,
                properties.queue().capacity(),
                properties.queue().enqueueTimeoutMs(),
                properties.upstream().requestTimeoutMs()
        );
    }

    /**
     * Wraps {@link RequestDispatcher} (a plain Java object) in a Spring lifecycle bean
     * so that Spring manages its start/stop at the correct phase.
     */
    @Bean
    public SmartLifecycle requestDispatcherLifecycle(RequestDispatcher dispatcher) {
        return new SmartLifecycle() {
            private volatile boolean started = false;

            @Override
            public void start() {
                dispatcher.start();
                started = true;
            }

            @Override
            public void stop() {
                dispatcher.stop();
                started = false;
            }

            @Override
            public boolean isRunning() {
                return started;
            }

            @Override
            public int getPhase() {
                return 200;
            }
        };
    }

    @Bean
    public TcpServer tcpServer(ModbusProxyProperties properties,
                               RequestDispatcher requestDispatcher) {
        return new TcpServer(
                properties.proxy(),
                properties.upstream(),
                requestDispatcher
        );
    }
}
