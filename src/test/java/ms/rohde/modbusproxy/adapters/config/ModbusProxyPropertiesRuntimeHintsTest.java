package ms.rohde.modbusproxy.adapters.config;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;

import static org.assertj.core.api.Assertions.assertThat;

class ModbusProxyPropertiesRuntimeHintsTest {

    @ParameterizedTest
    @ValueSource(classes = {
            ModbusProxyProperties.class,
            ModbusProxyProperties.ProxyProperties.class,
            ModbusProxyProperties.UpstreamProperties.class,
            ModbusProxyProperties.QueueProperties.class
    })
    void registerHints_givenPropertiesRecord_thenAllComponentFieldsAreReflectivelyAccessible(Class<?> recordType) {
        RuntimeHints hints = new RuntimeHints();

        new ModbusProxyPropertiesRuntimeHints().registerHints(hints, getClass().getClassLoader());

        assertThat(recordType.getRecordComponents()).isNotEmpty().allSatisfy(component ->
                assertThat(RuntimeHintsPredicates.reflection().onFieldAccess(recordType, component.getName()))
                        .accepts(hints));
    }
}
