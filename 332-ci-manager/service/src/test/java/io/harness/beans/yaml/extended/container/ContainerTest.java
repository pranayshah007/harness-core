/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.yaml.extended.container;

import static io.harness.rule.OwnerRule.ALEKSANDAR;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.yaml.extended.ci.container.Container;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ContainerTest extends CategoryTest {
  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void TestBuilderDefaults() {
    final Container container = Container.builder().resources(Container.Resources.builder().build()).build();
    assertThat(container.getResources().getReserve().getMemory()).isEqualTo(Container.MEM_RESERVE_DEFAULT);
  }
}
