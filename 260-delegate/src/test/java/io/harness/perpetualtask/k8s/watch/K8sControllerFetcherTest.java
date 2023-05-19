/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.k8s.watch;

import static io.harness.rule.OwnerRule.AVMOHAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableMap;
import io.kubernetes.client.informer.cache.Store;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1DeploymentBuilder;
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.openapi.models.V1PodBuilder;
import io.kubernetes.client.openapi.models.V1ReplicaSet;
import io.kubernetes.client.openapi.models.V1ReplicaSetBuilder;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(HarnessTeam.CE)
public class K8sControllerFetcherTest extends CategoryTest {
  @Mock private Store<V1Deployment> deploymentStore;
  @Mock private Store<V1ReplicaSet> replicaSetStore;
  @Mock private CrdWorkloadFetcher crdWorkloadFetcher;

  private K8sControllerFetcher k8sControllerFetcher;

  @Before
  public void setUp() throws Exception {
    when(deploymentStore.getByKey("harness/event-service"))
        .thenReturn(new V1DeploymentBuilder()
                        .withNewMetadata()
                        .withName("event-service")
                        .withNamespace("harness")
                        .withUid("37f2dbf2-8c96-4bfd-8b32-42ed61c68d50")
                        .addToLabels("app", "event-service")
                        .addToLabels("harness.io/release-name", "1df77a95-fed5-3853-bb2a-0b896c8901b1")
                        .endMetadata()
                        .withNewSpec()
                        .withReplicas(3)
                        .endSpec()
                        .build());
    when(replicaSetStore.getByKey("harness/event-service-65f7f468b4"))
        .thenReturn(new V1ReplicaSetBuilder()
                        .withNewMetadata()
                        .withName("event-service-65f7f468b4")
                        .withNamespace("harness")
                        .withUid("6105c171-44f7-4254-86ad-26badd9ba7fe")
                        .addNewOwnerReference()
                        .withApiVersion("apps/v1")
                        .withBlockOwnerDeletion(true)
                        .withController(true)
                        .withKind("Deployment")
                        .withName("event-service")
                        .withUid("37f2dbf2-8c96-4bfd-8b32-42ed61c68d50")
                        .endOwnerReference()
                        .endMetadata()
                        .build());
    Map<String, Store<?>> stores = ImmutableMap.of("Deployment", deploymentStore, "ReplicaSet", replicaSetStore);
    k8sControllerFetcher = new K8sControllerFetcher(stores, crdWorkloadFetcher);
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void testWorkloadForDeployment() throws Exception {
    assertThat(k8sControllerFetcher.getTopLevelOwner(new V1PodBuilder()
                                                         .withNewMetadata()
                                                         .withName("event-service-65f7f468b4-fs2fm")
                                                         .withNamespace("harness")
                                                         .withUid("39267b76-d563-4a2f-a783-783fcd89111e")
                                                         .addNewOwnerReference()
                                                         .withApiVersion("apps/v1")
                                                         .withBlockOwnerDeletion(true)
                                                         .withController(true)
                                                         .withKind("ReplicaSet")
                                                         .withName("event-service-65f7f468b4")
                                                         .withUid("6105c171-44f7-4254-86ad-26badd9ba7fe")
                                                         .endOwnerReference()
                                                         .endMetadata()
                                                         .build()))
        .isEqualTo(io.harness.perpetualtask.k8s.watch.Owner.newBuilder()
                       .setUid("37f2dbf2-8c96-4bfd-8b32-42ed61c68d50")
                       .setKind("Deployment")
                       .setName("event-service")
                       .setReplicas(3)
                       .putLabels("app", "event-service")
                       .putLabels("harness.io/release-name", "1df77a95-fed5-3853-bb2a-0b896c8901b1")
                       .build());
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void testWorkloadIndependentPod() throws Exception {
    assertThat(k8sControllerFetcher.getTopLevelOwner(new V1PodBuilder()
                                                         .withNewMetadata()
                                                         .withName("event-service-65f7f468b4-fs2fm")
                                                         .withNamespace("harness")
                                                         .withUid("39267b76-d563-4a2f-a783-783fcd89111e")
                                                         .addToLabels("key1", "val1")
                                                         .addToLabels("key2", "val2")
                                                         .endMetadata()
                                                         .build()))
        .isEqualTo(io.harness.perpetualtask.k8s.watch.Owner.newBuilder()
                       .setUid("39267b76-d563-4a2f-a783-783fcd89111e")
                       .setKind("Pod")
                       .setReplicas(1)
                       .setName("event-service-65f7f468b4-fs2fm")
                       .putLabels("key1", "val1")
                       .putLabels("key2", "val2")
                       .build());
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void testWorkloadForUnknownWorkloadKind() throws Exception {
    when(crdWorkloadFetcher.getWorkload(CrdWorkloadFetcher.WorkloadReference.builder()
                                            .apiVersion("apps/v1")
                                            .namespace("harness")
                                            .name("event-service-foobar")
                                            .uid("6105c171-44f7-4254-86ad-26badd9ba7fe")
                                            .kind("FooBar")
                                            .build()))
        .thenReturn(Workload.of("FooBar",
            new V1ObjectMetaBuilder()
                .withName("event-service-foobar")
                .withUid("6105c171-44f7-4254-86ad-26badd9ba7fe")
                .addToLabels("key1", "val1")
                .addToLabels("key2", "val2")
                .build(),
            1));
    assertThat(k8sControllerFetcher.getTopLevelOwner(new V1PodBuilder()
                                                         .withNewMetadata()
                                                         .withName("event-service-65f7f468b4-fs2fm")
                                                         .withNamespace("harness")
                                                         .withUid("39267b76-d563-4a2f-a783-783fcd89111e")
                                                         .addNewOwnerReference()
                                                         .withApiVersion("apps/v1")
                                                         .withBlockOwnerDeletion(true)
                                                         .withController(true)
                                                         .withKind("FooBar")
                                                         .withName("event-service-foobar")
                                                         .withUid("6105c171-44f7-4254-86ad-26badd9ba7fe")
                                                         .endOwnerReference()
                                                         .endMetadata()
                                                         .build()))
        .isEqualTo(io.harness.perpetualtask.k8s.watch.Owner.newBuilder()
                       .setUid("6105c171-44f7-4254-86ad-26badd9ba7fe")
                       .setKind("FooBar")
                       .setReplicas(1)
                       .setName("event-service-foobar")
                       .putLabels("key1", "val1")
                       .putLabels("key2", "val2")
                       .build());
  }
}
