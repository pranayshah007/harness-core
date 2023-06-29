/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.kubectl;

import static io.harness.rule.OwnerRule.TARUN_UBA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.version.Version;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDP)
public class CreateCommandTest extends CategoryTest {
  private static final String MANIFEST_FOR_HASH = "manifest-hash.yaml";

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void smokeTestVersionLess() {
    Kubectl client = Kubectl.client("kubectl", "CONFIG_PATH");
    client.setVersion(Version.parse("1.13"));
    CreateCommand createCommand = client.create(MANIFEST_FOR_HASH);

    assertThat(createCommand.command())
        .isEqualTo("kubectl --kubeconfig=CONFIG_PATH create -f manifest-hash.yaml -o=yaml --dry-run");

    client.setVersion(Version.parse("1.19"));
    createCommand = client.create(MANIFEST_FOR_HASH);

    assertThat(createCommand.command())
        .isEqualTo("kubectl --kubeconfig=CONFIG_PATH create -f manifest-hash.yaml -o=yaml --dry-run=client");

    client.setVersion(Version.parse("1.18"));
    createCommand = client.create(MANIFEST_FOR_HASH);

    assertThat(createCommand.command())
        .isEqualTo("kubectl --kubeconfig=CONFIG_PATH create -f manifest-hash.yaml -o=yaml --dry-run=client");

    client = Kubectl.client("", "");
    client.setVersion(Version.parse("1.18"));
    createCommand = client.create(MANIFEST_FOR_HASH);

    assertThat(createCommand.command()).isEqualTo("kubectl create -f manifest-hash.yaml -o=yaml --dry-run=client");
  }
}
