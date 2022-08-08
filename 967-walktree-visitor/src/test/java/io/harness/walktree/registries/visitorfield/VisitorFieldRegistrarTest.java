/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.walktree.registries.visitorfield;

import static io.harness.rule.OwnerRule.ARCHIT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.WalkTreeTestBase;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.walktree.registries.registrars.VisitableFieldRegistrar;

import com.google.inject.Inject;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.reflections.Reflections;

public class VisitorFieldRegistrarTest extends WalkTreeTestBase {
  @Inject Map<String, VisitableFieldRegistrar> visitableFieldRegistrarMap;

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testAllRegistrarsAreRegistered() {
    Set<String> fieldRegistrarClasses = new HashSet<>();

    Reflections reflections = new Reflections("io.harness");
    for (Class clazz : reflections.getSubTypesOf(VisitableFieldRegistrar.class)) {
      fieldRegistrarClasses.add(clazz.getName());
    }
    assertThat(visitableFieldRegistrarMap.keySet()).isEqualTo(fieldRegistrarClasses);
  }
}
