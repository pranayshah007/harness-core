/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer;

import static java.util.stream.Collectors.toList;

import io.harness.reflection.HarnessReflections;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.introspect.AnnotatedClass;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.jsontype.SubtypeResolver;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.reflections.Reflections;

@Slf4j
public class JsonSubtypeResolver extends SubtypeResolver {
  protected SubtypeResolver subtypeResolver;

  private LoadingCache<Class<?>, List<NamedType>> classListLoadingCache =
      CacheBuilder.newBuilder()
          .maximumSize(1000)
          .expireAfterWrite(10, TimeUnit.MINUTES)
          .build(new CacheLoader<Class<?>, List<NamedType>>() {
            @Override
            public List<NamedType> load(Class<?> key) {
              Reflections reflections = HarnessReflections.get();
              return reflections.getSubTypesOf(key)
                  .stream()
                  .filter(subClass -> subClass.isAnnotationPresent(JsonTypeName.class))
                  .map(subClass -> new NamedType(subClass, subClass.getAnnotation(JsonTypeName.class).value()))
                  .collect(toList());
            }
          });

  protected final LoadingCache<Class<?>, List<NamedType>> getClassListLoadingCache() {
    return classListLoadingCache;
  }

  /**
   * Instantiates a new Json subtype resolver.
   *
   * @param subtypeResolver the subtype resolver
   */
  public JsonSubtypeResolver(SubtypeResolver subtypeResolver) {
    this.subtypeResolver = subtypeResolver;
  }

  @Override
  public void registerSubtypes(NamedType... types) {
    subtypeResolver.registerSubtypes(types);
  }

  @Override
  public void registerSubtypes(Class<?>... classes) {
    subtypeResolver.registerSubtypes(classes);
  }

  @Override
  public void registerSubtypes(Collection<Class<?>> collection) {
    subtypeResolver.registerSubtypes(collection);
  }

  @Override
  public Collection<NamedType> collectAndResolveSubtypes(
      AnnotatedMember property, MapperConfig<?> config, AnnotationIntrospector ai, JavaType baseType) {
    Collection<NamedType> returnValue = subtypeResolver.collectAndResolveSubtypes(property, config, ai, baseType);
    LinkedHashSet<NamedType> newReturnValue = Sets.newLinkedHashSet(returnValue);
    if (newReturnValue.size() == 1) {
      try {
        newReturnValue.addAll(classListLoadingCache.get(property.getRawType()));
      } catch (ExecutionException e) {
        log.error("", e);
      }
    }
    return returnValue;
  }

  @Override
  public Collection<NamedType> collectAndResolveSubtypes(
      AnnotatedClass baseType, MapperConfig<?> config, AnnotationIntrospector ai) {
    Collection<NamedType> returnValue = subtypeResolver.collectAndResolveSubtypes(baseType, config, ai);
    LinkedHashSet<NamedType> newReturnValue = Sets.newLinkedHashSet(returnValue);
    if (newReturnValue.size() == 1) {
      try {
        newReturnValue.addAll(classListLoadingCache.get(baseType.getAnnotated()));
      } catch (ExecutionException e) {
        log.error("", e);
      }
    }
    return newReturnValue;
  }
}
