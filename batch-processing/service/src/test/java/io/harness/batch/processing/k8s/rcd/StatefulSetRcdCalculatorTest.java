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

import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.models.V1StatefulSetBuilder;
import io.kubernetes.client.util.Yaml;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class StatefulSetRcdCalculatorTest extends CategoryTest {
  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldHandleAdd() throws Exception {
    assertThat(
        new StatefulSetRcdCalculator().computeResourceClaimDiff("", statefulSetYaml("100m", "1200Mi", 2)).getDiff())
        .isEqualTo(ResourceClaim.builder().cpuNano(200000000L).memBytes(2516582400L).build());
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldHandleDelete() throws Exception {
    assertThat(
        new StatefulSetRcdCalculator().computeResourceClaimDiff(statefulSetYaml("750m", "1300Mi", 3), "").getDiff())
        .isEqualTo(ResourceClaim.builder().cpuNano(-2250000000L).memBytes(-4089446400L).build());
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldHandleUpdate() throws Exception {
    assertThat(new StatefulSetRcdCalculator()
                   .computeResourceClaimDiff(statefulSetYaml("100m", "1200Mi", 2), statefulSetYaml("300m", "1.5G", 3))
                   .getDiff())
        .isEqualTo(ResourceClaim.builder().cpuNano(700000000).memBytes(1983417600L).build());
  }
  private String statefulSetYaml(String cpu, String memory, int replicas) {
    return Yaml.dump(new V1StatefulSetBuilder()
                         .withNewSpec()
                         .withReplicas(replicas)
                         .withNewTemplate()
                         .withNewSpec()
                         .addNewContainer()
                         .withNewResources()
                         .addToRequests("cpu", Quantity.fromString(cpu))
                         .addToRequests("memory", Quantity.fromString(memory))
                         .endResources()
                         .endContainer()
                         .endSpec()
                         .endTemplate()
                         .endSpec()
                         .build());
  }
}
