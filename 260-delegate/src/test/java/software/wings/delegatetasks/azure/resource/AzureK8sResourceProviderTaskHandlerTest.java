/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.delegatetasks.azure.resource;

import static io.harness.rule.OwnerRule.IVAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.azure.client.AzureKubernetesClient;
import io.harness.azure.model.AzureConfig;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.azure.resource.operation.k8s.AzureKubernetesClusterDTO;
import io.harness.delegate.task.azure.resource.operation.k8s.AzureListKubernetesClustersOperation;
import io.harness.delegate.task.azure.resource.operation.k8s.AzureListKubernetesClustersOperationResponse;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.delegatetasks.azure.resource.taskhandler.AzureK8sResourceProviderTaskHandler;

import com.azure.resourcemanager.containerservice.models.KubernetesCluster;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class AzureK8sResourceProviderTaskHandlerTest extends WingsBaseTest {
  @Mock private AzureKubernetesClient kubernetesClient;

  @Spy @InjectMocks private AzureK8sResourceProviderTaskHandler taskHandler;

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testExecuteTask() {
    String subscriptionId = "subscriptionId";
    String k8sClusterName = "k8sClusterName";
    String resourceGroupName = "resourceGroupName";
    String k8sClusterType = "k8sClusterType";
    String k8sClusterId = "k8sClusterId";
    AzureListKubernetesClustersOperation listKubernetesClustersOperation =
        AzureListKubernetesClustersOperation.builder().subscriptionId(subscriptionId).build();
    AzureConfig azureConfig = AzureConfig.builder().build();

    KubernetesCluster kubernetesCluster = mock(KubernetesCluster.class);

    doReturn(k8sClusterName).when(kubernetesCluster).name();
    doReturn(resourceGroupName).when(kubernetesCluster).resourceGroupName();
    doReturn(k8sClusterType).when(kubernetesCluster).type();
    doReturn(k8sClusterId).when(kubernetesCluster).id();
    doReturn(Collections.singletonList(kubernetesCluster))
        .when(kubernetesClient)
        .listKubernetesClusters(azureConfig, subscriptionId);

    AzureListKubernetesClustersOperationResponse kubernetesClustersOperationResponse =
        (AzureListKubernetesClustersOperationResponse) taskHandler.executeTask(
            listKubernetesClustersOperation, azureConfig);
    List<AzureKubernetesClusterDTO> kubernetesClusterList =
        kubernetesClustersOperationResponse.getKubernetesClusterList();

    assertThat(kubernetesClusterList).isNotEmpty();
    AzureKubernetesClusterDTO azureKubernetesClusterDTO = kubernetesClusterList.get(0);
    assertThat(azureKubernetesClusterDTO.getName()).isEqualTo(k8sClusterName);
    assertThat(azureKubernetesClusterDTO.getResourceGroup()).isEqualTo(resourceGroupName);
    assertThat(azureKubernetesClusterDTO.getType()).isEqualTo(k8sClusterType);
    assertThat(azureKubernetesClusterDTO.getId()).isEqualTo(k8sClusterId);
    assertThat(azureKubernetesClusterDTO.getSubscriptionId()).isEqualTo(subscriptionId);
  }
}
