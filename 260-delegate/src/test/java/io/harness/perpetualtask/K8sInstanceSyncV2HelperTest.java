/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.container.ContainerInfo;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.NativeHelmServerInstanceInfo;
import io.harness.delegate.task.helm.HelmChartInfo;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import software.wings.WingsBaseTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(CDP)
public class K8sInstanceSyncV2HelperTest extends WingsBaseTest {
  @InjectMocks private K8sInstanceSyncV2Helper k8sInstanceSyncV2Helper;
  @Mock private K8sTaskHelperBase k8sTaskHelperBase;

  @Test
  @Owner(developers = OwnerRule.BUHA)
  @Category(UnitTests.class)
  public void testGetServerInstanceInfoList() throws Exception {
    Map<String, List<String>> workloadLabelSelectors =
        Map.of("deploymentName", List.of("label1=value1", "label2=value2"));
    KubernetesConfig kubeConfig = KubernetesConfig.builder().build();
    NativeHelmInstanceSyncPerpetualTaskV2Executor.PodDetailsRequest podDetailsRequest =
        NativeHelmInstanceSyncPerpetualTaskV2Executor.PodDetailsRequest.builder()
            .releaseName("releaseName")
            .namespace("namespace")
            .helmVersion("V380")
            .helmChartInfo(HelmChartInfo.builder().build())
            .workloadLabelSelectors(workloadLabelSelectors)
            .kubernetesConfig(kubeConfig)
            .build();
    List<ContainerInfo> containerInfos = new ArrayList<>(List.of(ContainerInfo.builder()
                                                                     .ip("ip")
                                                                     .containerId("containerId")
                                                                     .namespace("namespace")
                                                                     .podName("podName")
                                                                     .hostName("hostname")
                                                                     .build(),
        ContainerInfo.builder()
            .ip("ip2")
            .containerId("containerId2")
            .namespace("namespace")
            .podName("podName2")
            .hostName("hostname2")
            .build()));
    when(k8sTaskHelperBase.getContainerInfos(kubeConfig, "releaseName", "namespace", workloadLabelSelectors, 300000))
        .thenReturn(containerInfos);

    List<ServerInstanceInfo> serverInstanceInfoList =
        k8sInstanceSyncV2Helper.getServerInstanceInfoList(podDetailsRequest);

    assertThat(serverInstanceInfoList).hasSize(2);
    NativeHelmServerInstanceInfo nativeHelmServerInstanceInfo1 =
        (NativeHelmServerInstanceInfo) serverInstanceInfoList.get(0);
    assertThat(nativeHelmServerInstanceInfo1.getNamespace()).isEqualTo("namespace");
    NativeHelmServerInstanceInfo nativeHelmServerInstanceInfo2 =
        (NativeHelmServerInstanceInfo) serverInstanceInfoList.get(1);
    assertThat(nativeHelmServerInstanceInfo2.getNamespace()).isEqualTo("namespace");
  }
}
