/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.kubectl;

import static io.harness.rule.OwnerRule.BUHA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDP)
public class KustomizeCommandTest extends CategoryTest {
  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void smoke() {
    Kubectl client = KubectlFactory.getKubectlClient("tools/kubectl", null, ".");

    KustomizeCommand kustomizeCommand = client.kustomize("/kustomizeDirPath").commandFlags(null).withPlugin(null);

    assertThat(kustomizeCommand.command()).isEqualTo("tools/kubectl kustomize /kustomizeDirPath");
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testWithFlagsAndPlugins() {
    Kubectl client = KubectlFactory.getKubectlClient("tools/kubectl", null, ".");

    KustomizeCommand kustomizeCommand = client.kustomize("/kustomizeDirPath")
                                            .commandFlags(Map.of("BUILD", "--flag-1"))
                                            .withPlugin("/somedir/kustommize/plugins");

    assertThat(kustomizeCommand.command())
        .isEqualTo(
            "XDG_CONFIG_HOME=\"/somedir/kustommize/plugins\" tools/kubectl kustomize /kustomizeDirPath --enable-alpha-plugins --flag-1");
  }
}
