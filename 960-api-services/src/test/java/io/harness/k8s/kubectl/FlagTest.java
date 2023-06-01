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
import io.harness.beans.version.Version;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class FlagTest extends CategoryTest {
  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testVersionForDryRunForKubectl() {
    Version ver1 = Version.parse("1.17");
    assertThat(Flag.dryrun.getForVersion(ver1, Kubectl.ClientType.KUBECTL)).isEqualTo("--dry-run");
    Version ver2 = Version.parse("1.18");
    assertThat(Flag.dryrun.getForVersion(ver2, Kubectl.ClientType.KUBECTL)).isEqualTo("--dry-run=client");
    Version ver3 = Version.parse("1.19");
    assertThat(Flag.dryrun.getForVersion(ver3, Kubectl.ClientType.KUBECTL)).isEqualTo("--dry-run=client");
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testVersionForDryRunForOC() {
    Version ver1 = Version.parse("4.4");
    assertThat(Flag.dryrun.getForVersion(ver1, Kubectl.ClientType.OC)).isEqualTo("--dry-run");
    Version ver2 = Version.parse("4.5");
    assertThat(Flag.dryrun.getForVersion(ver2, Kubectl.ClientType.OC)).isEqualTo("--dry-run=client");
    Version ver3 = Version.parse("4.6");
    assertThat(Flag.dryrun.getForVersion(ver3, Kubectl.ClientType.OC)).isEqualTo("--dry-run=client");
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testExportNoVersionMapProvided() {
    Version ver1 = Version.parse("1.18");
    assertThat(Flag.export.getForVersion(ver1, Kubectl.ClientType.KUBECTL)).isEqualTo("--export");
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testVersionForRecordForKubectl() {
    Version ver1 = Version.parse("1.21");
    assertThat(Flag.record.getForVersion(ver1, Kubectl.ClientType.KUBECTL)).isEqualTo("--record");
    Version ver2 = Version.parse("1.22");
    assertThat(Flag.record.getForVersion(ver2, Kubectl.ClientType.KUBECTL)).isEmpty();
    Version ver3 = Version.parse("1.23");
    assertThat(Flag.record.getForVersion(ver3, Kubectl.ClientType.KUBECTL)).isEmpty();
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testVersionForRecordForOC() {
    Version ver1 = Version.parse("4.8");
    assertThat(Flag.record.getForVersion(ver1, Kubectl.ClientType.OC)).isEqualTo("--record");
    Version ver2 = Version.parse("4.9");
    assertThat(Flag.record.getForVersion(ver2, Kubectl.ClientType.OC)).isEmpty();
    Version ver3 = Version.parse("5.0");
    assertThat(Flag.record.getForVersion(ver3, Kubectl.ClientType.OC)).isEmpty();
  }
}
