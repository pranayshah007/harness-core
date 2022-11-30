/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.exception;

import io.harness.exception.ExceptionUtils;

import com.google.common.collect.Iterables;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.Path;
import javax.ws.rs.CookieParam;
import javax.ws.rs.FormParam;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.MatrixParam;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.commons.lang3.reflect.MethodUtils;

@Slf4j
@Provider
public class ConstraintViolationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {
  @Context ResourceInfo resourceInfo;

  /**
   * Derives member's name and type from it's annotations
   */
  private static Optional<String> getMemberName(Annotation[] memberAnnotations) {
    for (Annotation a : memberAnnotations) {
      if (a instanceof QueryParam) {
        return Optional.of(((QueryParam) a).value());
      } else if (a instanceof PathParam) {
        return Optional.of(((PathParam) a).value());
      } else if (a instanceof HeaderParam) {
        return Optional.of(((HeaderParam) a).value());
      } else if (a instanceof CookieParam) {
        return Optional.of(((CookieParam) a).value());
      } else if (a instanceof FormParam) {
        return Optional.of(((FormParam) a).value());
      } else if (a instanceof Context) {
        return Optional.of("context");
      } else if (a instanceof MatrixParam) {
        return Optional.of(((MatrixParam) a).value());
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

  @Override
  public Response toResponse(ConstraintViolationException exception) {
    log.error("Exception occurred: " + ExceptionUtils.getMessage(exception), exception);
    Set<ConstraintViolation<?>> constraintViolations = exception.getConstraintViolations();

    List<ValidationError> errors = new ArrayList<>();
    constraintViolations.forEach(constraintViolation -> {
      Optional<String> field = getMemberName(constraintViolation);

      // handling situations when @Valid annotation is used
      if (!field.isPresent()) {
        for (Path.Node node : constraintViolation.getPropertyPath()) {
          field = Optional.of(node.getName());
        }
      }
      errors.add(new ValidationError(field.get(), constraintViolation.getMessage()));
    });
    return Response.status(Response.Status.BAD_REQUEST).entity(errors).type(MediaType.APPLICATION_JSON).build();
  }
}
