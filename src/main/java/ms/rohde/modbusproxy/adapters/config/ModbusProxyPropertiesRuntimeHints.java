package ms.rohde.modbusproxy.adapters.config;

import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

import java.util.List;

/**
 * Registers reflection metadata that the GraalVM native image needs for {@link ModbusProxyProperties}.
 *
 * <p>Hibernate Validator cascades {@code @Valid} into the nested property records by reading their
 * fields reflectively. Spring AOT only registers the binding constructors, so the fields and accessors
 * are registered here.</p>
 */
class ModbusProxyPropertiesRuntimeHints implements RuntimeHintsRegistrar {

    private static final List<Class<?>> PROPERTY_RECORDS = List.of(
            ModbusProxyProperties.class,
            ModbusProxyProperties.ProxyProperties.class,
            ModbusProxyProperties.UpstreamProperties.class,
            ModbusProxyProperties.QueueProperties.class);

    @Override
    public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
        PROPERTY_RECORDS.forEach(recordType -> hints.reflection().registerType(recordType,
                MemberCategory.ACCESS_DECLARED_FIELDS,
                MemberCategory.INVOKE_PUBLIC_METHODS));
    }
}
