/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.core;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.CastedField;
import io.harness.beans.RecasterMap;
import io.harness.exceptions.RecasterException;
import io.harness.utils.RecastReflectionUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.internal.objenesis.Objenesis;
import org.modelmapper.internal.objenesis.ObjenesisStd;

@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class RecastObjectCreator implements RecastObjectFactory {
  private static final Objenesis objenesis = new ObjenesisStd(true);
  private final Map<Class<?>, InstanceConstructor<?>> instanceConstructors = new ConcurrentHashMap<>();

  interface InstanceConstructor<T> {
    T construct();
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T createInstance(final Class<T> clazz) {
    InstanceConstructor<?> instanceConstructor =
        instanceConstructors.computeIfAbsent(clazz, c -> makeInstanceConstructor(clazz));
    try {
      return (T) instanceConstructor.construct();
    } catch (Exception e) {
      throw new RecasterException("Failed to instantiate " + clazz.getName(), e);
    }
  }

  public Object createGivenInstance(RecasterMap recasterMap) {
    Class<?> clazz = recasterMap.getEncodedValue().getClass();
    InstanceConstructor<?> instanceConstructor =
        instanceConstructors.computeIfAbsent(clazz, c -> makeInstanceConstructor(clazz));
    try {
      return instanceConstructor.construct();
    } catch (Exception e) {
      throw new RecasterException("Failed to instantiate " + clazz.getName(), e);
    }
  }

  private <T> InstanceConstructor<T> makeInstanceConstructor(Class<T> clazz) {
    final Constructor<T> constructor = noArgsConstructorOrNull(clazz);
    if (constructor != null) {
      return () -> newInstance(constructor);
    }

    if (clazz.isArray()) {
      return () -> null;
    }
    if (!Collection.class.isAssignableFrom(clazz)) {
      return () -> objenesis.newInstance(clazz);
    }

    return () -> createInstanceInternal(clazz);
  }

  private static <T> Constructor<T> noArgsConstructorOrNull(final Class<T> type) {
    if (type == null) {
      return null;
    }
    try {
      final Constructor<T> constructor = type.getDeclaredConstructor();
      constructor.setAccessible(true);
      return constructor;
    } catch (NoSuchMethodException e) {
      return null;
    }
  }

  @SuppressWarnings("unchecked")
  private <T> T createInstanceInternal(final Class<T> clazz) {
    try {
      return getNoArgsConstructorOrException(clazz).newInstance();
    } catch (Exception e) {
      if (Collection.class.isAssignableFrom(clazz)) {
        if (Set.class.isAssignableFrom(clazz)) {
          return (T) createSet(null);
        } else if (Collection.class.isAssignableFrom(clazz)) {
          return (T) createList(null);
        } else if (Map.class.isAssignableFrom(clazz)) {
          return (T) createMap(null);
        }
      }
      throw new RecasterException("No usable constructor for " + clazz.getName(), e);
    }
  }

  private static <T> Constructor<T> getNoArgsConstructorOrException(final Class<T> type) {
    try {
      final Constructor<T> constructor = type.getDeclaredConstructor();
      constructor.setAccessible(true);
      return constructor;
    } catch (NoSuchMethodException e) {
      throw new RecasterException("No usable constructor for " + type.getName(), e);
    }
  }

  @Override
  public <T> T createInstance(Class<T> clazz, RecasterMap recasterMap) {
    Class<T> c = RecastReflectionUtils.getClass(recasterMap);
    if (c == null) {
      c = clazz;
    }
    return createInstance(c);
  }

  @Override
  public Object createInstance(Recaster recaster, CastedField cf, RecasterMap recasterMap) {
    Class<?> c = RecastReflectionUtils.getClass(recasterMap);
    if (c == null) {
      c = cf.isSingleValue() ? cf.getType() : cf.getSubClass();
      if (c.equals(Object.class)) {
        c = cf.getType();
      }
    }
    return createInstance(c, recasterMap);
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<Object> createList(final CastedField cf) {
    return newInstance(cf != null ? cf.getConstructor() : null, ArrayList.class);
  }

  @Override
  @SuppressWarnings("unchecked")
  public Map<Object, Object> createMap(final CastedField cf) {
    return newInstance(cf != null ? cf.getConstructor() : null, HashMap.class);
  }

  @Override
  @SuppressWarnings("unchecked")
  public Set<Object> createSet(final CastedField cf) {
    return newInstance(cf != null ? cf.getConstructor() : null, HashSet.class);
  }

  protected ClassLoader getClassLoaderForClass() {
    return Thread.currentThread().getContextClassLoader();
  }

  /**
   * creates an instance of testType (if it isn't Object.class or null) or fallbackType
   */
  private <T> T newInstance(final Constructor<T> tryMe, final Class<T> fallbackType) {
    if (tryMe != null) {
      tryMe.setAccessible(true);
      try {
        return tryMe.newInstance();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
    return createInstance(fallbackType);
  }

  private static <T> T newInstance(Constructor<T> constructor) {
    try {
      return constructor.newInstance();
    } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
        | InvocationTargetException exception) {
      throw new RecasterException("The class constructor fail", exception);
    }
  }
}
