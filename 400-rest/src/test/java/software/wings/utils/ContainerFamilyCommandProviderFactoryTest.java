/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.utils;

import static io.harness.rule.OwnerRule.JOHANNES;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ContainerFamilyCommandProviderFactoryTest extends CategoryTest {
  private final ContainerFamilyCommandProviderFactory containerFamilyCommandProviderFactory =
      new ContainerFamilyCommandProviderFactory();

  @Test
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void testGetProvider() {
    assertThat(this.containerFamilyCommandProviderFactory.getProvider(ContainerFamily.JBOSS).getClass())
        .isEqualTo(JbossContainerFamilyCommandProvider.class);
    assertThat(this.containerFamilyCommandProviderFactory.getProvider(ContainerFamily.TOMCAT).getClass())
        .isEqualTo(TomcatContainerFamilyCommandProvider.class);
  }
}
