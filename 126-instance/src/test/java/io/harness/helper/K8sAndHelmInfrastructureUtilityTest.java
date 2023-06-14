/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.helper;

import static io.harness.rule.OwnerRule.NAMAN_TALAYCHA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.InstancesTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.infra.beans.K8sAwsInfrastructureOutcome;
import io.harness.cdng.infra.beans.K8sAzureInfrastructureOutcome;
import io.harness.cdng.infra.beans.K8sGcpInfrastructureOutcome;
import io.harness.delegate.task.helm.HelmChartInfo;
import io.harness.dtos.deploymentinfo.DeploymentInfoDTO;
import io.harness.dtos.deploymentinfo.K8sDeploymentInfoDTO;
import io.harness.dtos.deploymentinfo.NativeHelmDeploymentInfoDTO;
import io.harness.k8s.model.HelmVersion;
import io.harness.perpetualtask.instancesync.helm.NativeHelmDeploymentReleaseDetails;
import io.harness.perpetualtask.instancesync.k8s.K8sDeploymentReleaseDetails;
import io.harness.rule.Owner;

import java.util.LinkedHashSet;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDP)

public class K8sAndHelmInfrastructureUtilityTest extends InstancesTestBase {
  private static final String BLUE_GREEN_COLOR = "blueGreenColor";
  private static final String NAMESPACE = "namespace";
  private static final String RELEASE_NAME = "releaseName";

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testGetK8sDeploymentReleaseDetails() {
    LinkedHashSet<String> namespaces = new LinkedHashSet<>();
    namespaces.add(NAMESPACE);
    DeploymentInfoDTO deploymentInfoDTO = K8sDeploymentInfoDTO.builder()
                                              .releaseName(RELEASE_NAME)
                                              .namespaces(namespaces)
                                              .blueGreenStageColor(BLUE_GREEN_COLOR)
                                              .cloudConfigMetadata(K8sAzureCloudConfigMetadata.builder()
                                                                       .clusterName("clusterName")
                                                                       .subscription("subscriptionId")
                                                                       .resourceGroup("resourceGroup")
                                                                       .useClusterAdminCredentials(true)
                                                                       .build())
                                              .build();

    K8sDeploymentReleaseDetails k8sDeploymentReleaseDetails =
        K8sAndHelmInfrastructureUtility.getK8sDeploymentReleaseDetails(deploymentInfoDTO);
    assertThat(k8sDeploymentReleaseDetails).isNotNull();
    assertThat(k8sDeploymentReleaseDetails.getReleaseName()).isEqualTo(RELEASE_NAME);
    assertThat(k8sDeploymentReleaseDetails.getNamespaces()).contains(NAMESPACE);
    assertThat(k8sDeploymentReleaseDetails.getK8sCloudClusterConfig()).isNotNull();
    assertThat(k8sDeploymentReleaseDetails.getK8sCloudClusterConfig().getResourceGroup()).isEqualTo("resourceGroup");
    assertThat(k8sDeploymentReleaseDetails.getK8sCloudClusterConfig().getSubscriptionId()).isEqualTo("subscriptionId");
    assertThat(k8sDeploymentReleaseDetails.getK8sCloudClusterConfig().isUseClusterAdminCredentials()).isTrue();
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testGetNativeHelmDeploymentReleaseDetails() {
    LinkedHashSet<String> namespaces = new LinkedHashSet<>();
    namespaces.add(NAMESPACE);
    DeploymentInfoDTO deploymentInfoDTO = NativeHelmDeploymentInfoDTO.builder()
                                              .releaseName(RELEASE_NAME)
                                              .namespaces(namespaces)
                                              .helmVersion(HelmVersion.V3)
                                              .helmChartInfo(HelmChartInfo.builder().name("helmChart").build())
                                              .cloudConfigMetadata(K8sAzureCloudConfigMetadata.builder()
                                                                       .clusterName("clusterName")
                                                                       .subscription("subscriptionId")
                                                                       .resourceGroup("resourceGroup")
                                                                       .useClusterAdminCredentials(true)
                                                                       .build())
                                              .build();

    NativeHelmDeploymentReleaseDetails helmDeploymentReleaseDetails =
        K8sAndHelmInfrastructureUtility.getNativeHelmDeploymentReleaseDetails(deploymentInfoDTO);
    assertThat(helmDeploymentReleaseDetails).isNotNull();
    assertThat(helmDeploymentReleaseDetails.getReleaseName()).isEqualTo(RELEASE_NAME);
    assertThat(helmDeploymentReleaseDetails.getNamespaces()).contains(NAMESPACE);
    assertThat(helmDeploymentReleaseDetails.getK8sCloudClusterConfig()).isNotNull();
    assertThat(helmDeploymentReleaseDetails.getK8sCloudClusterConfig().getResourceGroup()).isEqualTo("resourceGroup");
    assertThat(helmDeploymentReleaseDetails.getK8sCloudClusterConfig().getSubscriptionId()).isEqualTo("subscriptionId");
    assertThat(helmDeploymentReleaseDetails.getK8sCloudClusterConfig().isUseClusterAdminCredentials()).isTrue();
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testGetK8sAzureCloudConfigMetadata() {
    InfrastructureOutcome infrastructureOutcome = K8sAzureInfrastructureOutcome.builder()
                                                      .cluster("cluster")
                                                      .namespace(NAMESPACE)
                                                      .subscription("subscriptionId")
                                                      .resourceGroup("resourceGroup")
                                                      .useClusterAdminCredentials(true)
                                                      .build();
    K8sCloudConfigMetadata k8sCloudConfigMetadata =
        K8sAndHelmInfrastructureUtility.getK8sCloudConfigMetadata(infrastructureOutcome);
    assertThat(k8sCloudConfigMetadata).isNotNull();
    assertThat(k8sCloudConfigMetadata).isInstanceOf(K8sAzureCloudConfigMetadata.class);
    K8sAzureCloudConfigMetadata k8sAzureCloudConfigMetadata = (K8sAzureCloudConfigMetadata) k8sCloudConfigMetadata;
    assertThat(k8sAzureCloudConfigMetadata.getClusterName()).contains("cluster");
    assertThat(k8sAzureCloudConfigMetadata.getResourceGroup()).contains("resourceGroup");
    assertThat(k8sAzureCloudConfigMetadata.getSubscription()).contains("subscriptionId");
    assertThat(k8sAzureCloudConfigMetadata.isUseClusterAdminCredentials()).isTrue();
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testGetK8sAwsCloudConfigMetadata() {
    InfrastructureOutcome infrastructureOutcome =
        K8sAwsInfrastructureOutcome.builder().cluster("cluster").namespace(NAMESPACE).build();
    K8sCloudConfigMetadata k8sCloudConfigMetadata =
        K8sAndHelmInfrastructureUtility.getK8sCloudConfigMetadata(infrastructureOutcome);
    assertThat(k8sCloudConfigMetadata).isNotNull();
    assertThat(k8sCloudConfigMetadata).isInstanceOf(K8sAWSCloudConfigMetadata.class);
    K8sAWSCloudConfigMetadata k8sAWSCloudConfigMetadata = (K8sAWSCloudConfigMetadata) k8sCloudConfigMetadata;
    assertThat(k8sAWSCloudConfigMetadata.getClusterName()).contains("cluster");
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testGetK8sGCPCloudConfigMetadata() {
    InfrastructureOutcome infrastructureOutcome =
        K8sGcpInfrastructureOutcome.builder().cluster("cluster").namespace(NAMESPACE).build();
    K8sCloudConfigMetadata k8sCloudConfigMetadata =
        K8sAndHelmInfrastructureUtility.getK8sCloudConfigMetadata(infrastructureOutcome);
    assertThat(k8sCloudConfigMetadata).isNotNull();
    assertThat(k8sCloudConfigMetadata).isInstanceOf(K8sGcpCloudConfigMetadata.class);
    K8sGcpCloudConfigMetadata k8sAWSCloudConfigMetadata = (K8sGcpCloudConfigMetadata) k8sCloudConfigMetadata;
    assertThat(k8sAWSCloudConfigMetadata.getClusterName()).contains("cluster");
  }
}
