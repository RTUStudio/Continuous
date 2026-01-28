package kr.rtustudio.configurate;

import io.leangen.geantyref.GenericTypeReflector;

import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.util.CheckedFunction;
import org.spongepowered.configurate.util.CheckedSupplier;

/**
 * Instance supplier that handles creating non-static inner classes
 * by tracking all instances of ConfigurationPart objects.
 */
final class InnerClassInstanceSupplier
        implements CheckedFunction<AnnotatedType, @Nullable Supplier<Object>, SerializationException> {

    private final Map<Class<?>, Object> instanceMap = new HashMap<>();

    @Override
    public Supplier<Object> apply(final AnnotatedType target) throws SerializationException {
        final Class<?> type = GenericTypeReflector.erase(target.getType());
        if (ConfigurationPart.class.isAssignableFrom(type) && !this.instanceMap.containsKey(type)) {
            try {
                final Constructor<?> constructor;
                final CheckedSupplier<Object, ReflectiveOperationException> instanceSupplier;
                if (type.getEnclosingClass() != null && !Modifier.isStatic(type.getModifiers())) {
                    final Object instance = this.instanceMap.get(type.getEnclosingClass());
                    if (instance == null) {
                        throw new SerializationException(
                                "Cannot create inner class " + type.getName()
                                        + " without enclosing class " + type.getEnclosingClass().getName());
                    }
                    constructor = type.getDeclaredConstructor(type.getEnclosingClass());
                    instanceSupplier = () -> constructor.newInstance(instance);
                } else {
                    constructor = type.getDeclaredConstructor();
                    instanceSupplier = constructor::newInstance;
                }
                constructor.setAccessible(true);
                final Object instance = instanceSupplier.get();
                this.instanceMap.put(type, instance);
                return () -> instance;
            } catch (final ReflectiveOperationException e) {
                throw new SerializationException(
                        ConfigurationPart.class, target + " must be a valid ConfigurationPart", e);
            }
        } else {
            throw new SerializationException(target + " must be a valid ConfigurationPart");
        }
    }

    Map<Class<?>, Object> instanceMap() {
        return this.instanceMap;
    }
}
