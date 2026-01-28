package kr.rtustudio.configurate;

import io.leangen.geantyref.GenericTypeReflector;

import java.io.BufferedReader;
import java.lang.reflect.Type;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.ConfigurationOptions;
import org.spongepowered.configurate.loader.HeaderMode;
import org.spongepowered.configurate.objectmapping.ObjectMapper;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.util.CheckedFunction;
import org.spongepowered.configurate.util.MapFactories;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

/**
 * Lightweight configuration loader for ConfigurationPart-based configs.
 *
 * @param <T> the configuration type
 */
public class Configuration<T extends ConfigurationPart> {

    private static final Logger log = LoggerFactory.getLogger("Configuration");

    protected YamlConfigurationLoader loader;
    protected final Class<T> type;
    protected final Path path;
    protected final BufferedReader defaultConfig;
    protected final String header;

    public Configuration(Class<T> type, Path path) {
        this(type, path, null, null);
    }

    public Configuration(Class<T> type, Path path, String header) {
        this(type, path, null, header);
    }

    public Configuration(Class<T> type, Path path, BufferedReader defaultConfig, String header) {
        this.type = type;
        this.path = path;
        this.defaultConfig = defaultConfig;
        this.header = header;
    }

    private static <T> CheckedFunction<ConfigurationNode, T, SerializationException> creator(
            final Class<T> type) {
        return node -> {
            final T instance = node.require(type);
            node.set(type, instance);
            return instance;
        };
    }

    private static <T> CheckedFunction<ConfigurationNode, T, SerializationException> reloader(
            Class<T> type, T instance) {
        return node -> {
            @SuppressWarnings("unchecked")
            ObjectMapper.Factory factory = (ObjectMapper.Factory)
                    java.util.Objects.requireNonNull(node.options().serializers().get(type));
            @SuppressWarnings("unchecked")
            ObjectMapper.Mutable<T> mutable = (ObjectMapper.Mutable<T>) factory.get(type);
            mutable.load(instance, node);
            return instance;
        };
    }

    protected ConfigurationOptions defaultOptions(ConfigurationOptions options) {
        ConfigurationOptions result = options.mapFactory(MapFactories.insertionOrdered());
        if (header != null) {
            result = result.header(header);
        }
        return result;
    }

    protected ObjectMapper.Factory.Builder createObjectMapper() {
        return ObjectMapper.factoryBuilder()
                .addDiscoverer(InnerClassFieldDiscoverer.create());
    }

    protected YamlConfigurationLoader.Builder createLoaderBuilder() {
        return YamlConfigurationLoader.builder()
                .indent(2)
                .nodeStyle(NodeStyle.BLOCK)
                .headerMode(HeaderMode.PRESERVE)
                .defaultOptions(this::defaultOptions);
    }

    protected boolean isConfigType(final Type type) {
        return ConfigurationPart.class.isAssignableFrom(GenericTypeReflector.erase(type));
    }

    private void trySaveFileNode(ConfigurationNode node) throws ConfigurateException {
        try {
            loader.save(node);
        } catch (ConfigurateException ex) {
            if (ex.getCause() instanceof AccessDeniedException) {
                log.warn("Could not save {}", path, ex);
            } else throw ex;
        }
    }

    protected T initializeConfiguration(
            final CheckedFunction<ConfigurationNode, T, SerializationException> creator)
            throws ConfigurateException {
        final ObjectMapper.Factory factory = this.createObjectMapper().build();
        final YamlConfigurationLoader.Builder builder = this.createLoaderBuilder()
                .defaultOptions(options -> options.serializers(b ->
                        b.register(this::isConfigType, factory.asTypeSerializer())
                                .registerAnnotatedObjects(factory)))
                .path(path);

        if (this.defaultConfig == null) {
            loader = builder.build();
        } else {
            loader = builder.source(() -> this.defaultConfig).build();
        }

        final ConfigurationNode node;
        if (Files.notExists(path)) {
            if (this.defaultConfig == null) {
                node = CommentedConfigurationNode.root(loader.defaultOptions());
            } else {
                node = loader.load();
            }
        } else {
            node = loader.load();
        }

        final T instance = creator.apply(node);
        trySaveFileNode(node);
        return instance;
    }

    /**
     * Load the configuration from file.
     *
     * @return the loaded configuration instance
     */
    public T load() {
        try {
            Files.createDirectories(path.getParent());
            return this.initializeConfiguration(creator(this.type));
        } catch (Exception ex) {
            throw new RuntimeException("Could not load configuration: " + path, ex);
        }
    }

    /**
     * Reload the configuration into an existing instance.
     *
     * @param instance the instance to reload into
     */
    public void reload(T instance) {
        try {
            this.initializeConfiguration(reloader(this.type, instance));
        } catch (Exception ex) {
            throw new RuntimeException("Could not reload configuration: " + path, ex);
        }
    }

    /**
     * Save the configuration to file.
     *
     * @param instance the instance to save
     */
    public void save(T instance) {
        try {
            final ConfigurationNode node = loader.load();
            node.set(type, instance);
            loader.save(node);
        } catch (ConfigurateException ex) {
            throw new RuntimeException("Could not save configuration: " + path, ex);
        }
    }
}
