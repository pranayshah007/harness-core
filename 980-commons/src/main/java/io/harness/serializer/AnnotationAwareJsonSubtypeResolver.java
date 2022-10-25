/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer;

import io.harness.reflection.util.CodeUtils;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.AnnotatedMethod;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.jsontype.SubtypeResolver;
import com.google.common.collect.Sets;
import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AnnotationAwareJsonSubtypeResolver extends JsonSubtypeResolver {
  private AnnotationAwareJsonSubtypeResolver(SubtypeResolver subtypeResolver) {
    super(subtypeResolver);
  }

  public static AnnotationAwareJsonSubtypeResolver newInstance(SubtypeResolver subtypeResolver) {
    return new AnnotationAwareJsonSubtypeResolver(subtypeResolver);
  }

  public List<NamedType> findSubtypes(Annotated a) {
    final Class<?> rawType = getRawType(a);
    if (CodeUtils.isHarnessClass(rawType) && hasJsonTypeAnnotation(rawType)) {
      try {
        return getClassListLoadingCache().get(rawType);
      } catch (ExecutionException e) {
        log.error("error while finding subtypes", e);
      }
    }
    return Collections.emptyList();
  }

  private boolean hasJsonTypeAnnotation(Class<?> c) {
    for (Annotation ann : c.getAnnotations()) {
      if (ann instanceof JsonTypeInfo) {
        return true;
      }
    }
    return false;
  }

  @Override
  public Collection<NamedType> collectAndResolveSubtypes(
      AnnotatedMember property, MapperConfig<?> config, AnnotationIntrospector ai, JavaType baseType) {
    final Collection<NamedType> returnValue = subtypeResolver.collectAndResolveSubtypes(property, config, ai, baseType);
    final Set<NamedType> newReturnValue = Sets.newLinkedHashSet(returnValue);
    if (newReturnValue.size() == 1) {
      try {
        newReturnValue.addAll(getClassListLoadingCache().get(getRawType(property)));
      } catch (ExecutionException e) {
        log.error("Error while getting subtypes", e);
      }
    }
    return newReturnValue;
  }

  private Class<?> getRawType(Annotated member) {
    if (member instanceof AnnotatedMethod) {
      final AnnotatedMethod method = (AnnotatedMethod) member;
      if (method.getParameterCount() > 0) {
        return method.getRawParameterType(0);
      }
    }
    return member.getRawType();
  }
}
