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

import com.google.common.collect.ImmutableList;
import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ContainerBuilder;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodSpecBuilder;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ResourceClaimUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldHandleNull() throws Exception {
    assertThat(ResourceClaimUtils.resourceClaimDiffForPod(null, null))
        .isEqualTo(new ResourceClaimDiff(ResourceClaim.EMPTY, ResourceClaim.EMPTY));
    assertThat(ResourceClaimUtils.resourceClaimDiffForPodWithScale(null, 0, null, 0))
        .isEqualTo(new ResourceClaimDiff(ResourceClaim.EMPTY, ResourceClaim.EMPTY));
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldHandlePodWithMultipleContainers() throws Exception {
    assertThat(ResourceClaimUtils.resourceClaimDiffForPod(podSpec(ImmutableList.of(), ImmutableList.of()),
                   podSpec(ImmutableList.of(container("1", "1Gi"), container("100m", "50Mi")), ImmutableList.of())))
        .isEqualTo(new ResourceClaimDiff(ResourceClaim.EMPTY, new ResourceClaim(1100000000, 1126170624)));
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldHandlePodWithInitContainerMaxLessThanContainerSum() throws Exception {
    ResourceClaimDiff resourceClaimDiff =
        ResourceClaimUtils.resourceClaimDiffForPod(podSpec(ImmutableList.of(), ImmutableList.of()),
            podSpec(ImmutableList.of(container("1", "1Gi"), container("100m", "50Mi")),
                ImmutableList.of(container("1", "0.5Gi"), container("0.9", "1Gi"))));
    assertThat(resourceClaimDiff.getOldResourceClaim()).isEqualTo(ResourceClaim.EMPTY);
    assertThat(resourceClaimDiff.getNewResourceClaim()).isEqualTo(new ResourceClaim(1100000000, 1126170624));
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldHandlePodWithInitContainerMaxMoreThanContainerSum() throws Exception {
    ResourceClaimDiff resourceClaimDiff =
        ResourceClaimUtils.resourceClaimDiffForPod(podSpec(ImmutableList.of(), ImmutableList.of()),
            podSpec(ImmutableList.of(container("1", "1Gi"), container("100m", "50Mi")),
                ImmutableList.of(container("1.2", "0.5Gi"), container("0.9", "1.5Gi"))));
    assertThat(resourceClaimDiff.getOldResourceClaim()).isEqualTo(ResourceClaim.EMPTY);
    assertThat(resourceClaimDiff.getNewResourceClaim()).isEqualTo(new ResourceClaim(1200000000, 1610612736));
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldHandlePodDiff() throws Exception {
    ResourceClaimDiff resourceClaimDiff = ResourceClaimUtils.resourceClaimDiffForPod(
        podSpec(ImmutableList.of(container("0.9", "1034Mi"), container("200m", "40Mi")), ImmutableList.of()),
        podSpec(ImmutableList.of(container("1", "1Gi"), container("100m", "50Mi")), ImmutableList.of()));
    assertThat(resourceClaimDiff.getOldResourceClaim())
        .isEqualTo(new ResourceClaim((900 + 200) * 1_000_000, (1034 + 40) * 1024 * 1024));
    assertThat(resourceClaimDiff.getNewResourceClaim())
        .isEqualTo(new ResourceClaim((1000 + 100) * 1_000_000, (1024 + 50) * 1024 * 1024));
    assertThat(resourceClaimDiff.getDiff()).isEqualTo(ResourceClaim.EMPTY);
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldHandleScaling() throws Exception {
    ResourceClaimDiff resourceClaimDiff = ResourceClaimUtils.resourceClaimDiffForPodWithScale(
        podSpec(ImmutableList.of(container("1", "1Gi"), container("100m", "50Mi")), ImmutableList.of()), 4,
        podSpec(ImmutableList.of(container("1", "1Gi"), container("100m", "50Mi")), ImmutableList.of()), 2);
    assertThat(resourceClaimDiff.getDiff()).isEqualTo(new ResourceClaim(-2200000000L, -2252341248L));
  }

  private V1PodSpec podSpec(List<V1Container> containers, List<V1Container> initContainers) {
    return new V1PodSpecBuilder().withContainers(containers).withInitContainers(initContainers).build();
  }

  private V1Container container(String cpu, String memory) {
    return new V1ContainerBuilder()
        .withNewResources()
        .addToRequests("cpu", Quantity.fromString(cpu))
        .addToRequests("memory", Quantity.fromString(memory))
        .endResources()
        .build();
  }
}
