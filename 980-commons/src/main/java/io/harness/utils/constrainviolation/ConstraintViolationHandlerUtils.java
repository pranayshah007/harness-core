/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.util.stream.Collectors.toList;

import io.harness.data.validator.EntityName;

import com.google.common.base.Joiner;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import io.dropwizard.validation.ValidationMethod;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.ElementKind;
import javax.validation.Path;
import javax.validation.metadata.ConstraintDescriptor;
import javax.ws.rs.CookieParam;
import javax.ws.rs.FormParam;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.MatrixParam;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.apache.commons.lang3.tuple.Pair;

public class ConstraintViolationHandlerUtils {
  private ConstraintViolationHandlerUtils() {}
  private static final Cache<Pair<Path, ? extends ConstraintDescriptor<?>>, String> MESSAGES_CACHE =
      CacheBuilder.newBuilder().expireAfterWrite(1, TimeUnit.HOURS).build();
  private static final Joiner DOT_JOINER = Joiner.on('.');

  public static ImmutableList<String> getErrorMessages(ConstraintViolationException exception) {
    return ImmutableList.copyOf(
        exception.getConstraintViolations().stream().map(v -> processMessage(v, getMessage(v))).collect(toList()));
  }

  private static String getMessage(ConstraintViolation<?> v) {
    Pair<Path, ? extends ConstraintDescriptor<?>> of = Pair.of(v.getPropertyPath(), v.getConstraintDescriptor());

    String message = MESSAGES_CACHE.getIfPresent(of);
    if (message == null) {
      message = calculateMessage(v);
      MESSAGES_CACHE.put(of, message);
    }
    return message;
  }

  private static String calculateMessage(ConstraintViolation<?> v) {
    final Optional<String> returnValueName = getMethodReturnValueName(v);
    if (returnValueName.isPresent()) {
      final String name =
          isValidationMethod(v) ? StringUtils.substringBeforeLast(returnValueName.get(), ".") : returnValueName.get();
      return name + " " + v.getMessage();
    } else if (isValidationMethod(v)) {
      return validationMethodFormatted(v);
    } else {
      final String name = getMemberName(v).orElse(v.getPropertyPath().toString());
      return name + " " + v.getMessage();
    }
  }

  private static Optional<String> getMethodReturnValueName(ConstraintViolation<?> violation) {
    int returnValueNames = -1;

    final StringBuilder result = new StringBuilder("server response");
    for (Path.Node node : violation.getPropertyPath()) {
      if (node.getKind() == ElementKind.RETURN_VALUE) {
        returnValueNames = 0;
      } else if (returnValueNames >= 0) {
        result.append(returnValueNames++ == 0 ? " " : ".").append(node);
      }
    }

    return returnValueNames >= 0 ? Optional.of(result.toString()) : Optional.empty();
  }

  /**
   * Derives member's name and type from it's annotations
   */
  private static Optional<String> getMemberName(Annotation[] memberAnnotations) {
    for (Annotation a : memberAnnotations) {
      if (a instanceof QueryParam) {
        return Optional.of("query param " + ((QueryParam) a).value());
      } else if (a instanceof PathParam) {
        return Optional.of("path param " + ((PathParam) a).value());
      } else if (a instanceof HeaderParam) {
        return Optional.of("header " + ((HeaderParam) a).value());
      } else if (a instanceof CookieParam) {
        return Optional.of("cookie " + ((CookieParam) a).value());
      } else if (a instanceof FormParam) {
        return Optional.of("form field " + ((FormParam) a).value());
      } else if (a instanceof Context) {
        return Optional.of("context");
      } else if (a instanceof MatrixParam) {
        return Optional.of("matrix param " + ((MatrixParam) a).value());
      } else if (a instanceof EntityName) {
        String displayName = ((EntityName) a).displayName();
        if (isNotEmpty(displayName)) {
          return Optional.of(displayName);
        }
      }
    }

    return Optional.empty();
  }

  /**
   * Gets a method parameter (or a parameter field) name, if the violation raised in it.
   */
  private static Optional<String> getMemberName(ConstraintViolation<?> violation) {
    final int size = Iterables.size(violation.getPropertyPath());
    if (size < 2) {
      return Optional.empty();
    }

    final Path.Node parent = Iterables.get(violation.getPropertyPath(), size - 2);
    final Path.Node member = Iterables.getLast(violation.getPropertyPath());
    final Class<?> resourceClass = violation.getLeafBean().getClass();
    switch (parent.getKind()) {
      case PARAMETER:
        if (isEmpty(member.getName())) {
          return Optional.empty();
        }
        Field field = FieldUtils.getField(resourceClass, member.getName(), true);
        return getMemberName(field.getDeclaredAnnotations());
      case METHOD:
        List<Class<?>> params = parent.as(Path.MethodNode.class).getParameterTypes();
        Class<?>[] parcs = params.toArray(new Class<?>[ 0 ]);
        Method method = MethodUtils.getAccessibleMethod(resourceClass, parent.getName(), parcs);

        int paramIndex = member.as(Path.ParameterNode.class).getParameterIndex();
        return getMemberName(method.getParameterAnnotations()[paramIndex]);
      default:
        return Optional.empty();
    }
  }

  private static boolean isValidationMethod(ConstraintViolation<?> v) {
    return v.getConstraintDescriptor().getAnnotation() instanceof ValidationMethod;
  }

  private static <T> String validationMethodFormatted(ConstraintViolation<T> v) {
    final ImmutableList<Path.Node> nodes = ImmutableList.copyOf(v.getPropertyPath());
    String usefulNodes = DOT_JOINER.join(nodes.subList(0, nodes.size() - 1));
    String msg = usefulNodes + (v.getMessage().charAt(0) == '.' ? "" : " ") + v.getMessage();
    return msg.trim();
  }

  private static String processMessage(ConstraintViolation<?> v, String message) {
    boolean replaceArg = false;
    for (Path.Node node : v.getPropertyPath()) {
      switch (node.getKind()) {
        case METHOD:
          message = message.replace(node + ".", "");
          replaceArg = true;
          break;
        case PARAMETER:
          if (replaceArg) {
            message = message.replace(node + ".", "");
          }
          break;
        case PROPERTY:
          if (replaceArg) {
            message = message.replace(node + ".", "");
          }
          break;
        default:
          break;
      }
    }
    return message;
  }
}
