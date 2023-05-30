/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.kubectl;

import static io.harness.rule.OwnerRule.BUHA;
import static io.harness.rule.OwnerRule.PUNEET;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class VersionCommandTest extends CategoryTest {
  @Test
  @Owner(developers = PUNEET)
  @Category(UnitTests.class)
  public void smokeTest() {
    Kubectl client = Kubectl.client(null, null);
    VersionCommand versionCommand = client.version();

    assertThat(versionCommand.command()).isEqualTo("kubectl version ");
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testClientOnlyJson() {
    Kubectl client = Kubectl.client(null, null);
    VersionCommand versionCommand = client.version().clientOnly().jsonVersion();

    assertThat(versionCommand.command()).isEqualTo("kubectl version --client=true --output=json ");
  }
}
