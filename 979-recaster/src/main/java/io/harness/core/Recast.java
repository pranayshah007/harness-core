/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.core;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.transformers.RecastTransformer;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.reflections.Reflections;

/**
 * The translation layer for conversion from PipelineService json string to sdk objects
 */
@OwnedBy(HarnessTeam.PIPELINE)
public class Recast {
  private final Recaster recaster;
  private final AliasRegistry aliasRegistry;

  public Recast() {
    this(new Recaster(), new HashSet<>());
  }

  public Recast(final Set<Class<?>> classesToMap) {
    this(new Recaster(), classesToMap);
  }

  public Recast(final Recaster recaster, final Set<Class<?>> classesToMap) {
    this.recaster = recaster;
    this.aliasRegistry = AliasRegistry.getInstance();

    for (final Class<?> c : classesToMap) {
      map(c);
    }
  }

  public synchronized Recast map(final Class<?>... entityClasses) {
    if (entityClasses != null && entityClasses.length > 0) {
      for (final Class<?> entityClass : entityClasses) {
        if (!recaster.isCasted(entityClass)) {
          recaster.addCastedClass(entityClass);
        }
      }
    }
    return this;
  }

  public synchronized void addTransformer(RecastTransformer recastTransformer) {
    if (recastTransformer == null) {
      return;
    }
    recaster.getTransformer().addCustomTransformer(recastTransformer);
  }

  public void registerAliases(String... params) {
    Reflections reflections = new Reflections((Object[]) params);
    Set<Class<?>> typesAnnotatedWith = reflections.getTypesAnnotatedWith(RecasterAlias.class);
    aliasRegistry.addPackages(params);
    typesAnnotatedWith.forEach(aliasRegistry::register);
  }

  public void registerAliases(Reflections reflections, String... params) {
    Set<Class<?>> typesAnnotatedWith = reflections.getTypesAnnotatedWith(RecasterAlias.class);
    aliasRegistry.addPackages(params);
    typesAnnotatedWith.forEach(aliasRegistry::register);
  }

  public <T> T fromMap(final Map<String, Object> map, final Class<T> entityClazz) {
    return recaster.fromMap(map, entityClazz);
  }

  public <T> T fromMap(final Map<String, Object> map, final Class<T> entityClazz, boolean newRecastFlow) {
    return recaster.fromMap(map, entityClazz, newRecastFlow);
  }

  public Map<String, Object> toMap(final Object entity) {
    return toMap(entity, false);
  }

  public Map<String, Object> toMap(final Object entity, boolean newRecastFlow) {
    Map<String, Object> map = recaster.toMap(entity, newRecastFlow);
    return map == null ? null : new LinkedHashMap<>(map);
  }
}
