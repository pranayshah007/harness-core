/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.config.k8s.recommendation;

import static io.harness.rule.OwnerRule.AVMOHAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.entities.events.PublishedMessage;
import io.harness.ccm.commons.entities.k8s.recommendation.K8sWorkloadRecommendation;
import io.harness.ff.FeatureFlagService;
import io.harness.perpetualtask.k8s.watch.K8sWorkloadSpec;
import io.harness.perpetualtask.k8s.watch.K8sWorkloadSpec.ContainerSpec;
import io.harness.rule.Owner;

import software.wings.graphql.datafetcher.ce.recommendation.entity.ContainerRecommendation;
import software.wings.graphql.datafetcher.ce.recommendation.entity.ResourceRequirement;

import com.google.common.collect.ImmutableList;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class WorkloadSpecWriterTest extends CategoryTest {
  private static final String ACCOUNT_ID = "ACCOUNT_ID1";
  public static final String CLUSTER_ID = "CLUSTER_ID1";

  @Mock private FeatureFlagService featureFlagService;
  @Mock private WorkloadRecommendationDao workloadRecommendationDao;
  @InjectMocks private WorkloadSpecWriter workloadSpecWriter;

  @Before
  public void setUp() throws Exception {
    when(featureFlagService.isEnabled(eq(FeatureName.NODE_RECOMMENDATION_1), eq(ACCOUNT_ID))).thenReturn(false);
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void testWrite() throws Exception {
    when(workloadRecommendationDao.fetchRecommendationForWorkload(any()))
        .thenReturn(K8sWorkloadRecommendation.builder()
                        .accountId(ACCOUNT_ID)
                        .clusterId(CLUSTER_ID)
                        .namespace("kube-system")
                        .workloadName("kube-dns")
                        .workloadType("Deployment")
                        .containerRecommendations(new HashMap<>())
                        .containerCheckpoints(new HashMap<>())
                        .build());
    workloadSpecWriter.write(
        ImmutableList.of(PublishedMessage.builder()
                             .accountId(ACCOUNT_ID)
                             .message(K8sWorkloadSpec.newBuilder()
                                          .setClusterId(CLUSTER_ID)
                                          .setNamespace("kube-system")
                                          .setWorkloadKind("kube-dns")
                                          .setWorkloadName("Deployment")
                                          .setUid("test-uid")
                                          .addContainerSpecs(ContainerSpec.newBuilder()
                                                                 .setName("kubedns")
                                                                 .putRequests("cpu", "100m")
                                                                 .putRequests("memory", "70Mi")
                                                                 .putLimits("memory", "170Mi")
                                                                 .build())

                                          .addContainerSpecs(ContainerSpec.newBuilder()
                                                                 .setName("dnsmasq")
                                                                 .putRequests("cpu", "150m")
                                                                 .putRequests("memory", "20Mi")
                                                                 .build())
                                          .addContainerSpecs(ContainerSpec.newBuilder()
                                                                 .setName("sidecar")
                                                                 .putRequests("cpu", "10m")
                                                                 .putRequests("memory", "20Mi")
                                                                 .build())

                                          .addContainerSpecs(ContainerSpec.newBuilder()
                                                                 .setName("without-requests")
                                                                 .putLimits("cpu", "10m")
                                                                 .putLimits("memory", "20Mi")
                                                                 .build())

                                          .addContainerSpecs(ContainerSpec.newBuilder().setName("nothing").build())
                                          .build())
                             .build()));
    ArgumentCaptor<K8sWorkloadRecommendation> captor = ArgumentCaptor.forClass(K8sWorkloadRecommendation.class);
    verify(workloadRecommendationDao).save(captor.capture());
    assertThat(captor.getAllValues()).hasSize(1);
    Map<String, ContainerRecommendation> containerRecommendations = captor.getValue().getContainerRecommendations();
    assertThat(containerRecommendations).hasSize(5);
    assertThat(containerRecommendations.get("kubedns").getCurrent())
        .isEqualTo(ResourceRequirement.builder()
                       .request("cpu", "100m")
                       .request("memory", "70Mi")
                       .limit("memory", "170Mi")
                       .build());
    assertThat(containerRecommendations.get("dnsmasq").getCurrent())
        .isEqualTo(ResourceRequirement.builder().request("cpu", "150m").request("memory", "20Mi").build());
    assertThat(containerRecommendations.get("sidecar").getCurrent())
        .isEqualTo(ResourceRequirement.builder().request("cpu", "10m").request("memory", "20Mi").build());
    assertThat(containerRecommendations.get("without-requests").getCurrent())
        .isEqualTo(ResourceRequirement.builder()
                       .request("cpu", "10m")
                       .request("memory", "20Mi")
                       .limit("cpu", "10m")
                       .limit("memory", "20Mi")
                       .build());
    assertThat(containerRecommendations.get("nothing").getCurrent()).isEqualTo(ResourceRequirement.builder().build());
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void testExistingRecommendationIsNotOverwritten() throws Exception {
    when(workloadRecommendationDao.fetchRecommendationForWorkload(any()))
        .thenReturn(
            K8sWorkloadRecommendation.builder()
                .accountId(ACCOUNT_ID)
                .clusterId(CLUSTER_ID)
                .namespace("harness")
                .workloadName("test-ctr")
                .workloadType("Deployment")
                .containerRecommendation("test-ctr",
                    ContainerRecommendation.builder()
                        .current(ResourceRequirement.builder().build())
                        .guaranteed(ResourceRequirement.builder().request("cpu", "10m").limit("cpu", "10m").build())
                        .burstable(ResourceRequirement.builder().request("cpu", "5m").limit("cpu", "25m").build())
                        .build())
                .containerCheckpoints(new HashMap<>())
                .build());
    workloadSpecWriter.write(ImmutableList.of(PublishedMessage.builder()
                                                  .accountId(ACCOUNT_ID)
                                                  .message(K8sWorkloadSpec.newBuilder()
                                                               .setClusterId(CLUSTER_ID)
                                                               .setNamespace("harness")
                                                               .setWorkloadName("test-ctr")
                                                               .setWorkloadKind("Deployment")
                                                               .addContainerSpecs(ContainerSpec.newBuilder()
                                                                                      .setName("test-ctr")
                                                                                      .putRequests("cpu", "1500m")
                                                                                      .putLimits("cpu", "3000m")
                                                                                      .build())
                                                               .build())
                                                  .build()));
    ArgumentCaptor<K8sWorkloadRecommendation> captor = ArgumentCaptor.forClass(K8sWorkloadRecommendation.class);
    verify(workloadRecommendationDao).save(captor.capture());
    assertThat(captor.getAllValues()).hasSize(1);
    Map<String, ContainerRecommendation> containerRecommendations = captor.getValue().getContainerRecommendations();
    assertThat(containerRecommendations).hasSize(1);
    assertThat(containerRecommendations.get("test-ctr").getCurrent())
        .isEqualTo(ResourceRequirement.builder().request("cpu", "1500m").limit("cpu", "3000m").build());
    assertThat(containerRecommendations.get("test-ctr").getGuaranteed())
        .isEqualTo(ResourceRequirement.builder().request("cpu", "10m").limit("cpu", "10m").build());
    assertThat(containerRecommendations.get("test-ctr").getBurstable())
        .isEqualTo(ResourceRequirement.builder().request("cpu", "5m").limit("cpu", "25m").build());
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void testAddRemoveContainers() throws Exception {
    // existing in db has containers (a, b) and both have current recommendations.
    // according to new workload spec, we have containers (b, c). i.e a was deleted, b was added.
    // final state should have (b, c) but with current history of b preserved.
    when(workloadRecommendationDao.fetchRecommendationForWorkload(any()))
        .thenReturn(
            K8sWorkloadRecommendation.builder()
                .accountId(ACCOUNT_ID)
                .clusterId(CLUSTER_ID)
                .namespace("harness")
                .workloadName("test-ctr")
                .workloadType("Deployment")
                .containerRecommendation("ctr-a",
                    ContainerRecommendation.builder()
                        .current(ResourceRequirement.builder().build())
                        .guaranteed(ResourceRequirement.builder().request("cpu", "20m").limit("cpu", "20m").build())
                        .burstable(ResourceRequirement.builder().request("cpu", "15m").limit("cpu", "35m").build())
                        .build())
                .containerRecommendation("ctr-b",
                    ContainerRecommendation.builder()
                        .current(ResourceRequirement.builder().build())
                        .guaranteed(ResourceRequirement.builder().request("cpu", "10m").limit("cpu", "10m").build())
                        .burstable(ResourceRequirement.builder().request("cpu", "5m").limit("cpu", "25m").build())
                        .build())
                .containerCheckpoints(new HashMap<>())
                .build());
    workloadSpecWriter.write(ImmutableList.of(PublishedMessage.builder()
                                                  .accountId(ACCOUNT_ID)
                                                  .message(K8sWorkloadSpec.newBuilder()
                                                               .setClusterId(CLUSTER_ID)
                                                               .setNamespace("harness")
                                                               .setWorkloadName("test-ctr")
                                                               .setUid("test-uid")
                                                               .setWorkloadKind("Deployment")
                                                               .addContainerSpecs(ContainerSpec.newBuilder()
                                                                                      .setName("ctr-b")
                                                                                      .putRequests("cpu", "1000m")
                                                                                      .putLimits("cpu", "1500m")
                                                                                      .build())
                                                               .addContainerSpecs(ContainerSpec.newBuilder()
                                                                                      .setName("ctr-c")
                                                                                      .putRequests("cpu", "500m")
                                                                                      .putLimits("cpu", "750m")
                                                                                      .build())
                                                               .build())
                                                  .build()));
    ArgumentCaptor<K8sWorkloadRecommendation> captor = ArgumentCaptor.forClass(K8sWorkloadRecommendation.class);
    verify(workloadRecommendationDao).save(captor.capture());
    assertThat(captor.getAllValues()).hasSize(1);
    assertThat(captor.getValue().isDirty()).isTrue();
    Map<String, ContainerRecommendation> containerRecommendations = captor.getValue().getContainerRecommendations();
    assertThat(containerRecommendations).hasSize(2).containsOnlyKeys("ctr-b", "ctr-c");
    assertThat(containerRecommendations.get("ctr-b").getCurrent())
        .isEqualTo(ResourceRequirement.builder().request("cpu", "1000m").limit("cpu", "1500m").build());
    assertThat(containerRecommendations.get("ctr-b").getGuaranteed())
        .isEqualTo(ResourceRequirement.builder().request("cpu", "10m").limit("cpu", "10m").build());
    assertThat(containerRecommendations.get("ctr-b").getBurstable())
        .isEqualTo(ResourceRequirement.builder().request("cpu", "5m").limit("cpu", "25m").build());
    assertThat(containerRecommendations.get("ctr-c").getCurrent())
        .isEqualTo(ResourceRequirement.builder().request("cpu", "500m").limit("cpu", "750m").build());
    assertThat(containerRecommendations.get("ctr-c").getGuaranteed()).isNull();
    assertThat(containerRecommendations.get("ctr-c").getBurstable()).isNull();
  }
}
