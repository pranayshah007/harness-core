/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.io.harness.ng.utils;

import static io.harness.rule.OwnerRule.VIKAS;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.agent.sdk.HarnessAlwaysRun;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import com.google.common.collect.Sets;
import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.Operation;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Set;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.reflections.Reflections;

public class SwaggerApiOperationTest extends CategoryTest {
  private static final String BASE_PACKAGE = "io.harness.ng";

  @Test
  @Owner(developers = VIKAS)
  @Category(UnitTests.class)
  @HarnessAlwaysRun
  public void testNickNameUniqueness() {
    // Not adding PATCH at present.
    Set<Class<? extends Annotation>> supportedAnnotation =
        Sets.newHashSet(GET.class, POST.class, PUT.class, DELETE.class);

    final Set<String> uniqueOperationName = Sets.newHashSet();
    Reflections ref = new Reflections(BASE_PACKAGE);

    final Class<? extends Annotation> getMethodClass = GET.class;
    final Class<? extends Annotation> apiOperationClass = ApiOperation.class;

    for (Class<?> klass : ref.getTypesAnnotatedWith(Path.class)) {
      for (final Method method : klass.getDeclaredMethods()) {
        if (!isHidden(method)) {
          supportedAnnotation.stream()
              .filter(annotation -> method.isAnnotationPresent(annotation))
              .forEach(annotation -> {
                assertThat(method.isAnnotationPresent(apiOperationClass)).isTrue();
                ApiOperation apiOperationAnnotation = (ApiOperation) method.getAnnotation(apiOperationClass);
                assertThat(apiOperationAnnotation.nickname()).isNotBlank();
                assertThat(uniqueOperationName.contains(apiOperationAnnotation.nickname())).isFalse();
                uniqueOperationName.add(apiOperationAnnotation.nickname());
              });
        }
      }
    }
  }

  private boolean isHidden(Method method) {
    final Class<? extends Annotation> operationClass = Operation.class;
    Operation operationAnnotation = (Operation) method.getAnnotation(operationClass);
    if (operationAnnotation != null) {
      return operationAnnotation.hidden();
    }
    return false;
  }
}
