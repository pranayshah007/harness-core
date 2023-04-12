/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.instancesyncv2.cg;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.k8s.KubernetesContainerService;
import io.harness.k8s.model.K8sContainer;
import io.harness.k8s.model.K8sPod;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.logging.CommandExecutionStatus;
import io.harness.perpetualtask.instancesyncv2.CgDeploymentReleaseDetails;
import io.harness.perpetualtask.instancesyncv2.DirectK8sInstanceSyncTaskDetails;
import io.harness.perpetualtask.instancesyncv2.InstanceSyncData;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.serializer.KryoSerializer;

import software.wings.helpers.ext.container.ContainerDeploymentDelegateHelper;
import software.wings.helpers.ext.k8s.request.K8sClusterConfig;

import com.google.protobuf.Any;
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodStatusBuilder;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(CDP)
public class CgK8sInstancesDetailsFetcherTest {
  @Mock private ContainerDeploymentDelegateHelper containerDeploymentDelegateHelper;
  @Mock private KubernetesContainerService kubernetesContainerService;
  @Mock private KryoSerializer kryoSerializer;
  @Mock private K8sTaskHelperBase k8sTaskHelperBase;
  @InjectMocks private CgK8sInstancesDetailsFetcher fetcher;

  @Test
  @Owner(developers = OwnerRule.NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testFetchRunningInstanceDetailsK8sDeploy() throws Exception {
    CgDeploymentReleaseDetails cgDeploymentReleaseDetails =
        CgDeploymentReleaseDetails.newBuilder()
            .setTaskDetailsId("taskDetailsId")
            .setInfraMappingType("DIRECT_KUBERNETES")
            .setInfraMappingId("infraMappingId")
            .setReleaseDetails(Any.pack(DirectK8sInstanceSyncTaskDetails.newBuilder()
                                            .setReleaseName("releaseName")
                                            .setNamespace("namespace")
                                            .setIsHelm(false)
                                            .setContainerServiceName("")
                                            .build()))
            .build();

    doReturn(K8sClusterConfig.builder().clusterName("K8sCluster").namespace("namespace").build())
        .when(kryoSerializer)
        .asObject((byte[]) any());
    byte[] bytes = {70};
    doReturn(bytes).when(kryoSerializer).asBytes(any());

    List<K8sPod> k8sPodList = Collections.singletonList(
        K8sPod.builder()
            .podIP("podId")
            .name("nginxPod")
            .containerList(Collections.singletonList(
                K8sContainer.builder().containerId("containerId").image("image").name("nginx").build()))
            .build());

    doReturn(k8sPodList)
        .when(k8sTaskHelperBase)
        .getPodDetails(any(KubernetesConfig.class), anyString(), anyString(), anyLong());

    doReturn(KubernetesConfig.builder().clusterName("K8sCluster").accountId("accountId").namespace("namespace").build())
        .when(containerDeploymentDelegateHelper)
        .getKubernetesConfig(any(K8sClusterConfig.class), anyBoolean());

    InstanceSyncData instanceSyncData =
        fetcher.fetchRunningInstanceDetails("perpetualTaskId", cgDeploymentReleaseDetails);
    assertThat(instanceSyncData.getTaskDetailsId()).isEqualTo("taskDetailsId");
    assertThat(instanceSyncData.getExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS.name());
    assertThat(instanceSyncData.getInstanceCount()).isEqualTo(1);
  }

  @Test
  @Owner(developers = OwnerRule.NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testFetchRunningInstanceDetailsHelmDeploy() {
    CgDeploymentReleaseDetails cgDeploymentReleaseDetails =
        CgDeploymentReleaseDetails.newBuilder()
            .setTaskDetailsId("taskDetailsId")
            .setInfraMappingType("DIRECT_KUBERNETES")
            .setInfraMappingId("infraMappingId")
            .setReleaseDetails(Any.pack(DirectK8sInstanceSyncTaskDetails.newBuilder()
                                            .setReleaseName("releaseName")
                                            .setNamespace("namespace")
                                            .setIsHelm(true)
                                            .setContainerServiceName("")
                                            .build()))
            .build();

    doReturn(K8sClusterConfig.builder().clusterName("K8sCluster").namespace("namespace").build())
        .when(kryoSerializer)
        .asObject((byte[]) any());
    byte[] bytes = {70};
    doReturn(bytes).when(kryoSerializer).asBytes(any());

    List<V1Pod> k8sPodList = Collections.singletonList(
        new V1Pod()
            .status(new V1PodStatusBuilder().withPodIP("podIP").build())
            .metadata(
                new V1ObjectMetaBuilder().withName("nginxPod").withNamespace("namespace").withUid("podId").build()));

    doReturn(k8sPodList)
        .when(kubernetesContainerService)
        .getRunningPodsWithLabels(any(KubernetesConfig.class), anyString(), anyMap());

    doReturn(KubernetesConfig.builder().clusterName("K8sCluster").accountId("accountId").namespace("namespace").build())
        .when(containerDeploymentDelegateHelper)
        .getKubernetesConfig(any(K8sClusterConfig.class), anyBoolean());

    InstanceSyncData instanceSyncData =
        fetcher.fetchRunningInstanceDetails("perpetualTaskId", cgDeploymentReleaseDetails);
    assertThat(instanceSyncData.getTaskDetailsId()).isEqualTo("taskDetailsId");
    assertThat(instanceSyncData.getExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS.name());
    assertThat(instanceSyncData.getInstanceCount()).isEqualTo(1);
  }
}
