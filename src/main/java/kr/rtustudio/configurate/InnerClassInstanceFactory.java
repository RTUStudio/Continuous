package kr.rtustudio.configurate;

import io.leangen.geantyref.GenericTypeReflector;

import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import org.spongepowered.configurate.objectmapping.FieldDiscoverer;
import org.spongepowered.configurate.serialize.SerializationException;

/**
 * Instance factory for ConfigurationPart inner classes.
 */
final class InnerClassInstanceFactory
        implements FieldDiscoverer.MutableInstanceFactory<Map<Field, Object>> {

    private final InnerClassInstanceSupplier instanceSupplier;
    private final AnnotatedType targetType;

    InnerClassInstanceFactory(
            final InnerClassInstanceSupplier instanceSupplier, final AnnotatedType targetType) {
        this.instanceSupplier = instanceSupplier;
        this.targetType = targetType;
    }

    @Override
    public Map<Field, Object> begin() {
        return new LinkedHashMap<>();
    }

    @Override
    public void complete(final Object instance, final Map<Field, Object> intermediate)
            throws SerializationException {
        for (final Map.Entry<Field, Object> entry : intermediate.entrySet()) {
            try {
                if (entry.getValue() instanceof ImplicitProvider(final Supplier<Object> provider)) {
                    final Object valueInField = entry.getKey().get(instance);
                    if (valueInField == null) {
                        entry.getKey().set(instance, provider.get());
                    }
                } else {
                    entry.getKey().set(instance, entry.getValue());
                }
            } catch (final IllegalAccessException e) {
                throw new SerializationException(this.targetType.getType(), e);
            }
        }
    }

    @Override
    public Object complete(final Map<Field, Object> intermediate) throws SerializationException {
        final Object targetInstance = Objects.requireNonNull(
                this.instanceSupplier.instanceMap().get(
                        GenericTypeReflector.erase(this.targetType.getType())),
                () -> this.targetType.getType() + " must already have an instance created");
        this.complete(targetInstance, intermediate);
        return targetInstance;
    }

    @Override
    public boolean canCreateInstances() {
        return true;
    }

    record ImplicitProvider(Supplier<Object> provider) {}
}
