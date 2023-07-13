/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.core;

import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.PipelineServiceConfiguration;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import com.google.common.collect.Sets;
import io.swagger.annotations.ApiOperation;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class ResourceFileAnnotationsTest extends CategoryTest {
  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testNickNameUniqueness() {
    // Not adding PATCH at present.
    Set<Class<? extends Annotation>> supportedAnnotation = new HashSet<>();
    Collections.addAll(supportedAnnotation, GET.class, POST.class, PUT.class, DELETE.class);

    final Set<String> uniqueOperationName = Sets.newHashSet();

    final Class<? extends Annotation> apiOperationClass = ApiOperation.class;

    Collection<Class<?>> resourceClasses = PipelineServiceConfiguration.getResourceClasses();
    for (Class<?> clazz : resourceClasses) {
      for (final Method method : clazz.getDeclaredMethods()) {
        supportedAnnotation.stream().filter(method::isAnnotationPresent).forEach(annotation -> {
          if (method.isAnnotationPresent(apiOperationClass)) {
            ApiOperation apiOperationAnnotation = (ApiOperation) method.getAnnotation(apiOperationClass);
            assertThat(apiOperationAnnotation.nickname()).isNotBlank();
            assertThat(uniqueOperationName.contains(apiOperationAnnotation.nickname())).isFalse();
            uniqueOperationName.add(apiOperationAnnotation.nickname());
          }
        });
      }
    }
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testPathParams() {
    Collection<Class<?>> resourceClasses = PipelineServiceConfiguration.getResourceClasses();
    for (Class<?> clazz : resourceClasses) {
      for (final Method method : clazz.getDeclaredMethods()) {
        List<String> pathParams = new ArrayList<>();
        Annotation[][] parametersAnnotationsList = method.getParameterAnnotations();
        for (Annotation[] annotations : parametersAnnotationsList) {
          for (Annotation parameterAnnotation : annotations) {
            if (parameterAnnotation.annotationType() == PathParam.class) {
              pathParams.add(((PathParam) parameterAnnotation).value());
            }
          }
        }
        Path pathAnnotation = method.getAnnotation(Path.class);
        List<String> foundPathParams = new ArrayList<>();
        if (pathAnnotation == null) {
          assertThat(pathParams.size()).isEqualTo(0);
          continue;
        }
        String path = pathAnnotation.value();
        String[] pathComponents = path.split("/");
        for (String component : pathComponents) {
          if (component.matches("\\{.*}")) {
            String value = component.split("\\{")[1].split("}")[0];
            assertThat(pathParams.contains(value)).isTrue();
            foundPathParams.add(value);
          }
        }
        pathParams.forEach(pathParam -> {
          boolean contains = foundPathParams.contains(pathParam);
          if (!contains) {
            System.out.println("test");
          }
          assertThat(contains).isTrue();
        });
      }
    }
  }
}
