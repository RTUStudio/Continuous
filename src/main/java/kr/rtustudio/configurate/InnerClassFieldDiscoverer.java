package kr.rtustudio.configurate;

import io.leangen.geantyref.GenericTypeReflector;

import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SequencedMap;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.configurate.objectmapping.FieldDiscoverer;
import org.spongepowered.configurate.serialize.SerializationException;

import static java.util.Objects.requireNonNullElseGet;

/**
 * Field discoverer that handles ConfigurationPart inner classes.
 */
public final class InnerClassFieldDiscoverer implements FieldDiscoverer<Map<Field, Object>> {

    private final InnerClassInstanceSupplier instanceSupplier;
    private final FieldDiscoverer<Map<Field, Object>> delegate;

    private InnerClassFieldDiscoverer(
            final InnerClassInstanceSupplier instanceSupplier,
            final FieldDiscoverer<Map<Field, Object>> delegate) {
        this.instanceSupplier = instanceSupplier;
        this.delegate = delegate;
    }

    @SuppressWarnings("unchecked")
    public static FieldDiscoverer<?> create() {
        final InnerClassInstanceSupplier instanceSupplier = new InnerClassInstanceSupplier();
        return new InnerClassFieldDiscoverer(
                instanceSupplier,
                (FieldDiscoverer<Map<Field, Object>>) FieldDiscoverer.object(instanceSupplier));
    }

    @Override
    public @Nullable <V> InstanceFactory<Map<Field, Object>> discover(
            final AnnotatedType target,
            final FieldCollector<Map<Field, Object>, V> collector)
            throws SerializationException {
        final Class<?> clazz = GenericTypeReflector.erase(target.getType());
        if (ConfigurationPart.class.isAssignableFrom(clazz)) {
            final Object dummyInstance = this.delegate.<V>discover(
                    target,
                    (name, fieldType, container, deserializer, serializer) -> {
                        if (!GenericTypeReflector.erase(fieldType.getType())
                                .equals(clazz.getEnclosingClass())) {
                            collector.accept(
                                    name,
                                    fieldType,
                                    container,
                                    (intermediate, newValue, implicitInitializer) -> {
                                        final SequencedMap<Field, Object> map = new LinkedHashMap<>();
                                        deserializer.accept(map, newValue, implicitInitializer);
                                        final Object deserializedValue = requireNonNullElseGet(
                                                newValue,
                                                () -> new InnerClassInstanceFactory.ImplicitProvider(
                                                        implicitInitializer));
                                        intermediate.put(map.firstEntry().getKey(), deserializedValue);
                                    },
                                    serializer);
                        }
                    });
            if (dummyInstance == null) {
                return null;
            }
            return new InnerClassInstanceFactory(this.instanceSupplier, target);
        }
        return null;
    }
}
