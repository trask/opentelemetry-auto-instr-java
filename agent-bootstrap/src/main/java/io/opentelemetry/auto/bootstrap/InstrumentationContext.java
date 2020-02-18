package io.opentelemetry.auto.bootstrap;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Instrumentation Context API */
public class InstrumentationContext {
  private static final ConcurrentMap<Class<?>, ContextStore> outerMap = new ConcurrentHashMap();

  private InstrumentationContext() {}

  /**
   * Find a {@link ContextStore} instance for given key class and context class.
   *
   * <p>Conceptually this can be thought of as a map lookup to fetch a second level map given
   * keyClass.
   *
   * <p>However, the implementation is actually provided by bytecode transformation for performance
   * reasons.
   *
   * @param keyClass The key class context is attached to.
   * @param contextClass The context class attached to the user class.
   * @param <K> key class
   * @param <C> context class
   * @return The instance of context store for given arguments.
   */
  public static <K, C> ContextStore<K, C> get(
      final Class<K> keyClass, final Class<C> contextClass) {
    ContextStore contextStore = outerMap.get(keyClass);
    if (contextStore != null) {
      return contextStore;
    }
    contextStore = new ContextStoreImpl();
    final ContextStore previousMap = outerMap.putIfAbsent(keyClass, contextStore);
    if (previousMap == null) {
      return contextStore;
    } else {
      return previousMap;
    }
  }

  private static class ContextStoreImpl implements ContextStore {

    private final ConcurrentMap<Object, Object> map = new ConcurrentHashMap<>();

    @Override
    public Object get(final Object key) {
      return map.get(key);
    }

    @Override
    public void put(final Object key, final Object context) {
      map.put(key, context);
    }

    @Override
    public Object putIfAbsent(final Object key, final Object context) {
      return map.putIfAbsent(key, context);
    }

    @Override
    public Object putIfAbsent(final Object key, final Factory contextFactory) {
      final Object value = map.get(key);
      if (value != null) {
        return value;
      }
      return map.putIfAbsent(key, contextFactory.create());
    }
  }
}
