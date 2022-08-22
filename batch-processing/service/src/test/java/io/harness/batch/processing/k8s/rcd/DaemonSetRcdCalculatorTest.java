/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.k8s.rcd;

import static io.harness.rule.OwnerRule.AVMOHAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableMap;
import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.models.V1DaemonSetBuilder;
import io.kubernetes.client.util.Yaml;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class DaemonSetRcdCalculatorTest extends CategoryTest {
  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldHandleAdd() throws Exception {
    assertThat(new DaemonSetRcdCalculator().computeResourceClaimDiff("", daemonSetYaml("100m", "1200Mi")).getDiff())
        .isEqualTo(ResourceClaim.builder().cpuNano(100000000L).memBytes(1258291200L).build());
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldHandleDelete() throws Exception {
    assertThat(new DaemonSetRcdCalculator().computeResourceClaimDiff(daemonSetYaml("750m", "1300Mi"), "").getDiff())
        .isEqualTo(ResourceClaim.builder().cpuNano(-750000000L).memBytes(-1363148800L).build());
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldHandleUpdate() throws Exception {
    assertThat(new DaemonSetRcdCalculator()
                   .computeResourceClaimDiff(daemonSetYaml("120m", "1200Mi"), daemonSetYaml("100m", "1210Mi"))
                   .getDiff())
        .isEqualTo(ResourceClaim.builder().cpuNano(-20000000L).memBytes(10485760L).build());
  }
  private String daemonSetYaml(String cpu, String memory) {
    return Yaml.dump(
        new V1DaemonSetBuilder()
            .withNewSpec()
            .withNewTemplate()
            .withNewSpec()
            .addNewContainer()
            .withNewResources()
            .withRequests(ImmutableMap.of("cpu", Quantity.fromString(cpu), "memory", Quantity.fromString(memory)))
            .endResources()
            .endContainer()
            .endSpec()
            .endTemplate()
            .endSpec()
            .build());
  }
}
