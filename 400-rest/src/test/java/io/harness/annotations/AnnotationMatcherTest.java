/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.annotations;

import static io.harness.rule.OwnerRule.PRASHANT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.reflection.util.CodeUtils;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;

import com.google.common.base.Preconditions;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.io.IOUtils;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.reflections.Reflections;

public class AnnotationMatcherTest extends WingsBaseTest {
  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  @Ignore("As sonar does not work for bazel ignoring it")
  public void testAllCoverageClasses() throws IOException {
    Set<Class<? extends Object>> redesignClasses = new HashSet<>();
    Reflections reflectionsHarness = new Reflections("io.harness");
    Reflections reflectionsWings = new Reflections("software.wings");
    redesignClasses.addAll(reflectionsHarness.getTypesAnnotatedWith(OwnedBy.class));
    redesignClasses.addAll(reflectionsWings.getTypesAnnotatedWith(OwnedBy.class));
    List<String> nameSet =
        redesignClasses.stream()
            .distinct()
            .filter(clazz -> !clazz.isAnonymousClass() && !clazz.isMemberClass() && !clazz.isInterface())
            .filter(clazz
                -> clazz.getAnnotation(OwnedBy.class) != null
                    && clazz.getAnnotation(OwnedBy.class).value().equals(HarnessTeam.CDC))
            .map(this::extractClassLocation)
            .sorted()
            .collect(Collectors.toList());
    List<String> expectedSet;
    try (InputStream in = getClass().getResourceAsStream("/annotations/coverage-class-list.txt")) {
      expectedSet = IOUtils.readLines(in, "UTF-8");
    }
    assertThat(nameSet).isEqualTo(expectedSet);
  }

  private String extractClassLocation(Class<?> clazz) {
    List<String> locationList = Arrays.asList(Preconditions.checkNotNull(CodeUtils.location(clazz)).split("/"));
    String moduleBasePath = "";
    // Bazel do not have target folder, so tha path is directly coming from .m2.
    // Instead of target we are using 0.0.1-SNAPSHOT to handle bazel build modules.
    if (locationList.indexOf("0.0.1-SNAPSHOT") != -1) {
      moduleBasePath = locationList.get(locationList.indexOf("0.0.1-SNAPSHOT") - 1) + "/src/main/java/";
    } else {
      moduleBasePath = locationList.get(locationList.indexOf("target") - 1) + "/src/main/java/";
    }
    String packagePath = clazz.getPackage().getName().replace(".", "/") + "/";
    return moduleBasePath + packagePath + clazz.getSimpleName() + ".java";
  }
}
