/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.k8s.watch;

import static io.harness.rule.OwnerRule.AVMOHAN;
import static io.harness.rule.OwnerRule.HANTANG;
import static io.harness.rule.OwnerRule.UTSAV;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableMap;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ContainerBuilder;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.openapi.models.V1PersistentVolumeSpec;
import io.kubernetes.client.openapi.models.V1PersistentVolumeSpecBuilder;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodBuilder;
import io.kubernetes.client.openapi.models.V1PodCondition;
import io.kubernetes.client.openapi.models.V1PodConditionBuilder;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import io.kubernetes.client.openapi.models.V1ResourceRequirementsBuilder;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class K8sResourceUtilsTest extends CategoryTest {
  private static final String K8S_POD_RESOURCE = "pods";
  private final V1ResourceRequirements resourceRequirements = new V1ResourceRequirements();
  private V1Container k8sContainer;
  private final OffsetDateTime TIMESTAMP = OffsetDateTime.now();

  @Before
  public void init() {
    k8sContainer = new V1ContainerBuilder()
                       .withName("init-mydb")
                       .withImage("busybox")
                       .withResources(resourceRequirements)
                       .addToCommand("sh")
                       .addToCommand("-c")
                       .addToCommand("until nslookup mydb; do echo waiting for mydb; sleep 2; done;")
                       .build();
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void testGetResource() {
    Resource actualResource = K8sResourceUtils.getResource(k8sContainer);
    assertThat(actualResource).isNotNull();
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldComputeEffectiveResources() throws Exception {
    V1Pod pod = new V1PodBuilder()
                    .withNewSpec()
                    .withContainers(makeContainer("250m", "1Ki", "1", "50Ki"), makeContainer("500m", "1Mi"))
                    .withInitContainers(makeContainer("0.8", "1Ki"), makeContainer("500m", "2Ki"))
                    .endSpec()
                    .build();
    assertThat(K8sResourceUtils.getEffectiveResources(pod.getSpec()))
        .isEqualTo(Resource.newBuilder()
                       .putRequests("cpu", Quantity.newBuilder().setAmount(800_000_000).setUnit("n").build())
                       .putRequests("memory", Quantity.newBuilder().setAmount(1049600).setUnit("").build())
                       .putLimits("cpu", Quantity.newBuilder().setAmount(1_500_000_000).setUnit("n").build())
                       .putLimits("memory", Quantity.newBuilder().setAmount(1099776).setUnit("").build())
                       .build());
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void testGetResourceMapNoCpu() throws Exception {
    assertThat(
        K8sResourceUtils.getResourceMap(ImmutableMap.of("memory", new io.kubernetes.client.custom.Quantity("0.5Mi"))))
        .isEqualTo(ImmutableMap.of("cpu", Quantity.newBuilder().setUnit("n").setAmount(0).build(), "memory",
            Quantity.newBuilder().setAmount(512 * 1024).build(), K8S_POD_RESOURCE,
            Quantity.newBuilder().setAmount(0).build()));
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void testGetResourceMapNoMemory() throws Exception {
    assertThat(
        K8sResourceUtils.getResourceMap(ImmutableMap.of("cpu", new io.kubernetes.client.custom.Quantity("100m"))))
        .isEqualTo(ImmutableMap.of("cpu", Quantity.newBuilder().setUnit("n").setAmount(100_000_000L).build(), "memory",
            Quantity.newBuilder().setAmount(0).build(), K8S_POD_RESOURCE, Quantity.newBuilder().setAmount(0).build()));

    assertThat(K8sResourceUtils.getResourceMap(ImmutableMap.of("cpu",
                   new io.kubernetes.client.custom.Quantity(
                       new BigDecimal("1.9"), io.kubernetes.client.custom.Quantity.Format.DECIMAL_SI),
                   "memory",
                   new io.kubernetes.client.custom.Quantity(
                       new BigDecimal(1_000L), io.kubernetes.client.custom.Quantity.Format.BINARY_SI),
                   K8S_POD_RESOURCE,
                   new io.kubernetes.client.custom.Quantity(
                       new BigDecimal("1"), io.kubernetes.client.custom.Quantity.Format.DECIMAL_SI))))
        .isEqualTo(ImmutableMap.of("cpu", Quantity.newBuilder().setUnit("n").setAmount(1_900_000_000L).build(),
            "memory", Quantity.newBuilder().setAmount(1_000L).build(), K8S_POD_RESOURCE,
            Quantity.newBuilder().setAmount(1L).build()));
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testGetTimestampInMillisByMethod() {
    V1ObjectMeta v1ObjectMeta =
        new V1ObjectMetaBuilder().withCreationTimestamp(TIMESTAMP).withDeletionTimestamp(TIMESTAMP).build();

    assertThat(v1ObjectMeta.getCreationTimestamp().toInstant().toEpochMilli())
        .isEqualTo(TIMESTAMP.toInstant().toEpochMilli());
    assertThat(v1ObjectMeta.getDeletionTimestamp().toInstant().toEpochMilli())
        .isEqualTo(TIMESTAMP.toInstant().toEpochMilli());

    V1PodCondition podScheduledCondition = new V1PodConditionBuilder().withLastTransitionTime(TIMESTAMP).build();
    assertThat(podScheduledCondition.getLastTransitionTime().toInstant().toEpochMilli())
        .isEqualTo(TIMESTAMP.toInstant().toEpochMilli());
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testGetStorageRequest() {
    V1ResourceRequirements resources = new V1ResourceRequirementsBuilder()
                                           .addToRequests("storage", new io.kubernetes.client.custom.Quantity("1Ki"))
                                           .build();
    assertThat(K8sResourceUtils.getStorageRequest(resources))
        .isEqualTo(Quantity.newBuilder().setAmount(1024L).setUnit("B").build());
  }

  private V1Container makeContainer(String cpuLim, String memLim) {
    return makeContainer(cpuLim, memLim, cpuLim, memLim);
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testGetStorageCapacity() {
    V1PersistentVolumeSpec spec =
        new V1PersistentVolumeSpecBuilder()
            .withCapacity(ImmutableMap.of("storage", new io.kubernetes.client.custom.Quantity("1Ki")))
            .build();
    assertThat(K8sResourceUtils.getStorageCapacity(spec))
        .isEqualTo(Quantity.newBuilder().setAmount(1024L).setUnit("B").build());
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testGetPodsFromResourceMap() {
    assertThat(K8sResourceUtils.getResourceMap(ImmutableMap.of("pods", new io.kubernetes.client.custom.Quantity("101")))
                   .get(K8S_POD_RESOURCE)
                   .getAmount())
        .isEqualTo(101L);
    assertThat(K8sResourceUtils.getResourceMap(ImmutableMap.of()).get(K8S_POD_RESOURCE).getAmount()).isEqualTo(0L);
  }

  private V1Container makeContainer(String cpuReq, String memReq, String cpuLim, String memLim) {
    return new V1ContainerBuilder()
        .withNewResources()
        .addToRequests("cpu", new io.kubernetes.client.custom.Quantity(cpuReq))
        .addToRequests("memory", new io.kubernetes.client.custom.Quantity(memReq))
        .addToLimits("cpu", new io.kubernetes.client.custom.Quantity(cpuLim))
        .addToLimits("memory", new io.kubernetes.client.custom.Quantity(memLim))
        .endResources()
        .build();
  }
}
