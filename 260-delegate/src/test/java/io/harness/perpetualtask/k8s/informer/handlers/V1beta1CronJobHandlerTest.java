/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.k8s.informer.handlers;

import static io.harness.rule.OwnerRule.AVMOHAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.event.client.EventPublisher;
import io.harness.perpetualtask.k8s.informer.ClusterDetails;
import io.harness.perpetualtask.k8s.watch.K8sWorkloadSpec;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.Message;
import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.models.V1CronJob;
import io.kubernetes.client.openapi.models.V1CronJobBuilder;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;

public class V1beta1CronJobHandlerTest extends CategoryTest {
  private static final Integer VERSION = 1;
  private EventPublisher eventPublisher;
  private final ClusterDetails clusterDetails = ClusterDetails.builder()
                                                    .clusterName("ce-test-cluster")
                                                    .cloudProviderId("cloud-provider-id")
                                                    .clusterId("cluster-id")
                                                    .kubeSystemUid("kube-system-uid")
                                                    .build();
  private V1beta1CronJobHandler cronjobHandler;
  private ArgumentCaptor<Message> captor;

  @Before
  public void setUp() throws Exception {
    eventPublisher = mock(EventPublisher.class);
    cronjobHandler = new V1beta1CronJobHandler(eventPublisher, clusterDetails);
    captor = ArgumentCaptor.forClass(Message.class);
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldPublishWorkloadSpecOnAdd() throws Exception {
    V1CronJob cronjob = new V1CronJobBuilder()
                            .withNewMetadata()
                            .withName("test-name")
                            .withNamespace("test-namespace")
                            .withUid("test-uid")
                            .endMetadata()
                            .withNewSpec()
                            .withNewJobTemplate()
                            .withNewSpec()
                            .withNewTemplate()
                            .withNewSpec()
                            .addNewInitContainer()
                            .withName("init-container-1")
                            .endInitContainer()
                            .addNewContainer()
                            .withName("container-1")
                            .withNewResources()
                            .addToRequests("cpu", Quantity.fromString("100m"))
                            .addToRequests("memory", Quantity.fromString("100Mi"))
                            .addToLimits("cpu", Quantity.fromString("200m"))
                            .addToLimits("memory", Quantity.fromString("200Mi"))
                            .endResources()
                            .endContainer()
                            .addNewContainer()
                            .withName("container-2")
                            .withNewResources()
                            .addToRequests("cpu", Quantity.fromString("750m"))
                            .addToRequests("memory", Quantity.fromString("1Gi"))
                            .addToLimits("cpu", Quantity.fromString("1500m"))
                            .addToLimits("memory", Quantity.fromString("2Gi"))
                            .endResources()
                            .endContainer()
                            .endSpec()
                            .endTemplate()
                            .endSpec()
                            .endJobTemplate()
                            .endSpec()
                            .build();
    cronjobHandler.onAdd(cronjob);
    assertThat(getPublishedMessages().get(0))
        .isEqualTo(K8sWorkloadSpec.newBuilder()
                       .setNamespace("test-namespace")
                       .setWorkloadName("test-name")
                       .setUid("test-uid")
                       .setVersion(VERSION)
                       .setClusterName("ce-test-cluster")
                       .setClusterId("cluster-id")
                       .setKubeSystemUid("kube-system-uid")
                       .setCloudProviderId("cloud-provider-id")
                       .setWorkloadKind("CronJob")
                       .addAllContainerSpecs(ImmutableList.of(K8sWorkloadSpec.ContainerSpec.newBuilder()
                                                                  .setName("container-1")
                                                                  .putRequests("cpu", "100m")
                                                                  .putRequests("memory", "100Mi")
                                                                  .putLimits("cpu", "200m")
                                                                  .putLimits("memory", "200Mi")
                                                                  .build(),
                           K8sWorkloadSpec.ContainerSpec.newBuilder()
                               .setName("container-2")
                               .putRequests("cpu", "750m")
                               .putRequests("memory", "1Gi")
                               .putLimits("cpu", "1500m")
                               .putLimits("memory", "2Gi")
                               .build()))
                       .addAllInitContainerSpecs(ImmutableList.of(
                           K8sWorkloadSpec.ContainerSpec.newBuilder().setName("init-container-1").build()))
                       .build());
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldPublishWorkloadSpecOnUpdateIfChangeMade() throws Exception {
    V1CronJob oldCronJob = new V1CronJobBuilder()
                               .withNewMetadata()
                               .withName("test-name")
                               .withNamespace("test-namespace")
                               .withUid("test-uid")
                               .endMetadata()
                               .withNewSpec()
                               .withNewJobTemplate()
                               .withNewSpec()
                               .withNewTemplate()
                               .withNewSpec()
                               .addNewContainer()
                               .withName("container-1")
                               .withNewResources()
                               .addToRequests("cpu", Quantity.fromString("100m"))
                               .addToRequests("memory", Quantity.fromString("100Mi"))
                               .addToLimits("cpu", Quantity.fromString("200m"))
                               .addToLimits("memory", Quantity.fromString("200Mi"))
                               .endResources()
                               .endContainer()
                               .addNewContainer()
                               .withName("container-2")
                               .withNewResources()
                               .addToRequests("cpu", Quantity.fromString("750m"))
                               .addToRequests("memory", Quantity.fromString("1Gi"))
                               .addToLimits("cpu", Quantity.fromString("1500m"))
                               .addToLimits("memory", Quantity.fromString("2Gi"))
                               .endResources()
                               .endContainer()
                               .endSpec()
                               .endTemplate()
                               .endSpec()
                               .endJobTemplate()
                               .endSpec()
                               .build();
    V1CronJob newCronJob = new V1CronJobBuilder(oldCronJob)
                               .withNewSpec()
                               .withNewJobTemplate()
                               .withNewSpec()
                               .withNewTemplate()
                               .withNewSpec()
                               .addNewContainer()
                               .withName("container-1")
                               .withNewResources()
                               .addToRequests("cpu", Quantity.fromString("100m"))
                               .addToRequests("memory", Quantity.fromString("100Mi"))
                               .addToLimits("cpu", Quantity.fromString("400m"))
                               .addToLimits("memory", Quantity.fromString("400Mi"))
                               .endResources()
                               .endContainer()
                               .addNewContainer()
                               .withName("container-2")
                               .withNewResources()
                               .addToRequests("cpu", Quantity.fromString("750m"))
                               .addToRequests("memory", Quantity.fromString("1Gi"))
                               .addToLimits("cpu", Quantity.fromString("3000m"))
                               .addToLimits("memory", Quantity.fromString("4Gi"))
                               .endResources()
                               .endContainer()
                               .endSpec()
                               .endTemplate()
                               .endSpec()
                               .endJobTemplate()
                               .endSpec()
                               .build();
    cronjobHandler.onUpdate(oldCronJob, newCronJob);
    assertThat(getPublishedMessages().get(0))
        .isEqualTo(K8sWorkloadSpec.newBuilder()
                       .setNamespace("test-namespace")
                       .setWorkloadName("test-name")
                       .setClusterName("ce-test-cluster")
                       .setUid("test-uid")
                       .setVersion(VERSION)
                       .setClusterId("cluster-id")
                       .setKubeSystemUid("kube-system-uid")
                       .setCloudProviderId("cloud-provider-id")
                       .setWorkloadKind("CronJob")
                       .addAllContainerSpecs(ImmutableList.of(K8sWorkloadSpec.ContainerSpec.newBuilder()
                                                                  .setName("container-1")
                                                                  .putRequests("cpu", "100m")
                                                                  .putRequests("memory", "100Mi")
                                                                  .putLimits("cpu", "400m")
                                                                  .putLimits("memory", "400Mi")
                                                                  .build(),
                           K8sWorkloadSpec.ContainerSpec.newBuilder()
                               .setName("container-2")
                               .putRequests("cpu", "750m")
                               .putRequests("memory", "1Gi")
                               .putLimits("cpu", "3")
                               .putLimits("memory", "4Gi")
                               .build()))
                       .build());
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldNotPublishWorkloadSpecOnUpdateIfNoChangeMade() throws Exception {
    V1CronJob oldCronJob = new V1CronJobBuilder()
                               .withNewMetadata()
                               .withName("test-name")
                               .withNamespace("test-namespace")
                               .withUid("test-uid")
                               .endMetadata()
                               .withNewSpec()
                               .withNewJobTemplate()
                               .withNewSpec()
                               .withNewTemplate()
                               .withNewSpec()
                               .addNewContainer()
                               .withName("container-1")
                               .withNewResources()
                               .addToRequests("cpu", Quantity.fromString("100m"))
                               .addToRequests("memory", Quantity.fromString("100Mi"))
                               .addToLimits("cpu", Quantity.fromString("200m"))
                               .addToLimits("memory", Quantity.fromString("200Mi"))
                               .endResources()
                               .endContainer()
                               .addNewContainer()
                               .withName("container-2")
                               .withNewResources()
                               .addToRequests("cpu", Quantity.fromString("750m"))
                               .addToRequests("memory", Quantity.fromString("1Gi"))
                               .addToLimits("cpu", Quantity.fromString("1500m"))
                               .addToLimits("memory", Quantity.fromString("2Gi"))
                               .endResources()
                               .endContainer()
                               .endSpec()
                               .endTemplate()
                               .endSpec()
                               .endJobTemplate()
                               .endSpec()
                               .build();
    V1CronJob newCronJob = new V1CronJobBuilder(oldCronJob)
                               .withNewSpec()
                               .withNewJobTemplate()
                               .withNewSpec()
                               .withNewTemplate()
                               .withNewSpec()
                               .withAutomountServiceAccountToken(false)
                               .addNewContainer()
                               .withName("container-1")
                               .withNewResources()
                               .addToRequests("cpu", Quantity.fromString("100m"))
                               .addToRequests("memory", Quantity.fromString("100Mi"))
                               .addToLimits("cpu", Quantity.fromString("200m"))
                               .addToLimits("memory", Quantity.fromString("200Mi"))
                               .endResources()
                               .endContainer()
                               .addNewContainer()
                               .withName("container-2")
                               .withNewResources()
                               .addToRequests("cpu", Quantity.fromString("750m"))
                               .addToRequests("memory", Quantity.fromString("1Gi"))
                               .addToLimits("cpu", Quantity.fromString("1500m"))
                               .addToLimits("memory", Quantity.fromString("2Gi"))
                               .endResources()
                               .endContainer()
                               .endSpec()
                               .endTemplate()
                               .endSpec()
                               .endJobTemplate()
                               .endSpec()
                               .build();
    cronjobHandler.onUpdate(oldCronJob, newCronJob);
    assertThat(getPublishedMessages()).isEmpty();
  }

  private List<Message> getPublishedMessages() {
    verify(eventPublisher, atLeastOnce()).publishMessage(captor.capture(), any(), any());
    return captor.getAllValues().stream().filter(x -> x instanceof K8sWorkloadSpec).collect(Collectors.toList());
  }
}
