/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng;

import static io.harness.rule.OwnerRule.DHRUVX;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;

public class CVNGBeanValidationTest extends CategoryTest {
  private static final String BASE_PACKAGE = "io.harness.cvng";

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testNotBlankAnnotatedWithNotNull() {
    Reflections reflections = new Reflections(BASE_PACKAGE, new SubTypesScanner(false));
    Set<Class<?>> allClasses = reflections.getSubTypesOf(Object.class);
    final Class<? extends Annotation> notNullClass = NotNull.class;

    for (Class<?> klass : allClasses) {
      for (final Method method : klass.getDeclaredMethods()) {
        Arrays.stream(method.getParameters())
            .filter(param -> param.isAnnotationPresent(NotBlank.class))
            .forEach(param
                -> assertThat(param.isAnnotationPresent(notNullClass))
                       .withFailMessage("@NotNull annotation not present for param " + param.getName() + " in method "
                           + method.getName() + " in " + klass)
                       .isTrue());
      }
      for (final Field field : klass.getDeclaredFields()) {
        if (field.isAnnotationPresent(NotBlank.class)) {
          assertThat(field.isAnnotationPresent(notNullClass))
              .withFailMessage("@NotNull annotation not present for field " + field.getName() + " in " + klass)
              .isTrue();
        }
      }
    }
  }
}
