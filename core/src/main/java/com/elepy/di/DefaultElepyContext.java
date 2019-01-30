package com.elepy.di;

import com.elepy.annotations.Inject;
import com.elepy.exceptions.ElepyConfigException;
import com.elepy.utils.ClassUtils;

import java.util.*;

public class DefaultElepyContext implements ElepyContext {

    private final Map<ContextKey, Object> contextMap;

    private UnsatisfiedDependencies unsatisfiedDependencies;

    private List<ContextKey> preInitialisedDependencies;
    private boolean strictMode = false;

    public DefaultElepyContext() {
        this.contextMap = new HashMap<>();
        this.unsatisfiedDependencies = new UnsatisfiedDependencies(this);
        preInitialisedDependencies = new ArrayList<>();
    }

    public <T> void registerDependency(Class<T> cls, T object) {
        registerDependency(cls, null, object);
    }

    public <T> void registerDependency(T object) {
        registerDependency(object, null);
    }

    public <T> void registerDependency(T object, String tag) {
        ContextKey<?> contextKey = new ContextKey<>(object.getClass(), tag);
        contextMap.put(contextKey, object);
        preInitialisedDependencies.add(contextKey);
    }

    public <T> void registerDependency(Class<T> cls, String tag, T object) {
        ContextKey<T> contextKey = new ContextKey<>(cls, tag);
        contextMap.put(contextKey, object);
        preInitialisedDependencies.add(contextKey);
    }

    public <T> T getDependency(Class<T> cls, String tag) {
        final ContextKey<T> key = new ContextKey<>(cls, tag);

        final T t = (T) contextMap.get(key);
        if (t != null) {
            return t;
        }

        throw new ElepyConfigException(String.format("No context object for %s available with the tag: %s", cls.getName(), tag));
    }

    public void registerDependency(Class<?> clazz) {
        registerDependency(clazz, ElepyContext.getTag(clazz));
    }

    public void registerDependency(Class<?> clazz, String tag) {
        registerDependency(new ContextKey<>(clazz, tag));
    }

    public void registerDependency(ContextKey contextKey) {
        unsatisfiedDependencies.add(contextKey);
        if (strictMode) {
            resolveDependencies();
        }
    }
    
    public void strictMode(boolean strictMode) {
        this.strictMode = strictMode;
    }

    public void resolveDependencies() {
        unsatisfiedDependencies.tryToSatisfy();

        for (ContextKey preInitialisedDependency : preInitialisedDependencies) {
            if (!ClassUtils.searchForFieldsWithAnnotation(preInitialisedDependency.getClassType(), Inject.class).isEmpty()) {
                try {
                    this.injectFields(contextMap.get(preInitialisedDependency));
                } catch (IllegalAccessException ignored) {
                    //Will never be thrown
                }
            }
        }
    }

    public Map<ContextKey, Object> getContextMap() {
        return contextMap;
    }

    @Override
    public Set<ContextKey> getDependencyKeys() {
        return contextMap.keySet();
    }


}
