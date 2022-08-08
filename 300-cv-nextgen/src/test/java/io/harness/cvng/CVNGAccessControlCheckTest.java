/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng;

import static io.harness.rule.OwnerRule.KAPIL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.category.element.UnitTests;
import io.harness.cvng.utils.NGAccessControlClientCheck;
import io.harness.reflection.HarnessReflections;
import io.harness.rule.Owner;

import com.google.common.collect.Sets;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.ws.rs.BeanParam;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class CVNGAccessControlCheckTest extends CategoryTest {
  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testNGAccessControlCheckPresentOrNot() {
    Set<Class<? extends Annotation>> supportedAnnotation =
        Sets.newHashSet(GET.class, POST.class, PUT.class, DELETE.class);
    List<String> necessaryAccessControlAnnotations = Arrays.asList("@io.harness.accesscontrol.AccountIdentifier()",
        "@io.harness.accesscontrol.OrgIdentifier()", "@io.harness.accesscontrol.ProjectIdentifier()");

    final String MONITORED_SERVICE_RESOURCE_PATH = "io.harness.cvng.core.resources.MonitoredServiceResource";
    final String SLO_RESOURCE_PATH = "io.harness.cvng.servicelevelobjective.resources.ServiceLevelObjectiveResource";
    final String SLO_DASHBOARD_RESOURCE_PATH = "io.harness.cvng.servicelevelobjective.resources.SLODashboardResource";

    final Class<? extends Annotation> ngAccessControlCheckClass = NGAccessControlCheck.class;
    final Class<? extends Annotation> ngAccessControlClientCheckClass = NGAccessControlClientCheck.class;
    List<Class<?>> klasses =
        HarnessReflections.get()
            .getTypesAnnotatedWith(Path.class)
            .stream()
            .filter(klazz
                -> StringUtils.startsWithAny(klazz.getPackage().getName(), MONITORED_SERVICE_RESOURCE_PATH,
                    SLO_RESOURCE_PATH, SLO_DASHBOARD_RESOURCE_PATH))
            .collect(Collectors.toList());

    klasses.forEach(klass -> {
      for (final Method method : klass.getDeclaredMethods()) {
        supportedAnnotation.stream().filter(method::isAnnotationPresent).forEach(annotation -> {
          assertThat(method.isAnnotationPresent(ngAccessControlCheckClass)
              || method.isAnnotationPresent(ngAccessControlClientCheckClass))
              .withFailMessage(
                  "Neither NGAccessControlCheck nor NGAccessControlClientCheck annotation is present on resource method "
                  + klass.getName() + "." + method.getName())
              .isTrue();

          if (method.isAnnotationPresent(ngAccessControlCheckClass)) {
            Map<String, Integer> methodAnnotationMap = new HashMap<>();
            Arrays.asList(method.getParameterAnnotations())
                .forEach(paramAnnotations
                    -> Arrays.asList(paramAnnotations)
                           .forEach(paramAnnotation -> methodAnnotationMap.put(paramAnnotation.toString(), 1)));

            Arrays.stream(method.getParameters())
                .filter(parameter -> parameter.isAnnotationPresent(BeanParam.class))
                .flatMap(parameter -> Arrays.stream(parameter.getType().getDeclaredFields()))
                .flatMap(field -> Arrays.stream(field.getAnnotations()))
                .forEach(fieldAnnotation -> methodAnnotationMap.put(fieldAnnotation.toString(), 1));

            necessaryAccessControlAnnotations.forEach(accessControlAnnotation
                -> assertThat(methodAnnotationMap.containsKey(accessControlAnnotation))
                       .withFailMessage(accessControlAnnotation + " annotation is missing on resource method "
                           + klass.getName() + "." + method.getName())
                       .isTrue());
          }
        });
      }
    });
  }
}
