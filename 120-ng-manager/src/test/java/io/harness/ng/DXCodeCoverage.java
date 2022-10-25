/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng;

import static io.harness.rule.OwnerRule.ABHINAV;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.reflection.util.CodeUtils;
import io.harness.rule.Owner;

import com.google.common.base.Preconditions;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.reflections.Reflections;

public class DXCodeCoverage extends CategoryTest {
  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testAllCoverageClasses() throws IOException {
    Set<Class<? extends Object>> classes = new HashSet<>();
    Reflections reflectionsHarness = new Reflections("io.harness");
    Reflections reflectionsWings = new Reflections("software.wings");
    classes.addAll(reflectionsHarness.getTypesAnnotatedWith(OwnedBy.class));
    classes.addAll(reflectionsWings.getTypesAnnotatedWith(OwnedBy.class));

    List<String> modulesByDX = new ArrayList<>();
    modulesByDX.add("900-yaml-sdk");
    modulesByDX.add("440-connector-nextgen");
    modulesByDX.add("136-git-sync-manager");

    List<String> nameSet = new ArrayList<>();
    nameSet.addAll(modulesByDX);
    final List<String> annotatedClasses =
        classes.stream()
            .distinct()
            .filter(clazz -> !clazz.isAnonymousClass() && !clazz.isMemberClass() && !clazz.isInterface())
            .filter(clazz
                -> clazz.getAnnotation(OwnedBy.class) != null
                    && clazz.getAnnotation(OwnedBy.class).value().equals(HarnessTeam.DX))
            .map(this::extractClassLocation)
            .sorted()
            .collect(Collectors.toList());
    List<String> expectedSet;
    try (InputStream in = getClass().getResourceAsStream("/dx-code-coverage/coverage-list.txt")) {
      expectedSet = IOUtils.readLines(in, "UTF-8");
    }
    nameSet.addAll(annotatedClasses);
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
