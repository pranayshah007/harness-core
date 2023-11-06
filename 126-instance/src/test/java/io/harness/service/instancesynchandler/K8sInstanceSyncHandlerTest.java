/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.instancesynchandler;

import static io.harness.beans.FeatureName.CDS_EKS_ADD_REGIONAL_PARAM;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.IVAN;
import static io.harness.rule.OwnerRule.NAMAN_TALAYCHA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import io.harness.InstancesTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.infra.beans.K8sDirectInfrastructureOutcome;
import io.harness.delegate.beans.instancesync.DeploymentOutcomeMetadata;
import io.harness.delegate.beans.instancesync.K8sDeploymentOutcomeMetadata;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.K8sServerInstanceInfo;
import io.harness.delegate.task.helm.HelmChartInfo;
import io.harness.dtos.deploymentinfo.DeploymentInfoDTO;
import io.harness.dtos.deploymentinfo.K8sDeploymentInfoDTO;
import io.harness.dtos.instanceinfo.InstanceInfoDTO;
import io.harness.dtos.instanceinfo.K8sInstanceInfoDTO;
import io.harness.dtos.instancesyncperpetualtaskinfo.DeploymentInfoDetailsDTO;
import io.harness.dtos.instancesyncperpetualtaskinfo.InstanceSyncPerpetualTaskInfoDTO;
import io.harness.entities.InstanceType;
import io.harness.k8s.model.K8sContainer;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.perpetualtask.PerpetualTaskType;
import io.harness.perpetualtask.instancesync.DeploymentReleaseDetails;
import io.harness.perpetualtask.instancesync.k8s.K8sDeploymentReleaseDetails;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.CDP)
public class K8sInstanceSyncHandlerTest extends InstancesTestBase {
  private static final String POD_NAME = "podName";
  private static final String BLUE_GREEN_COLOR = "blueGreenColor";
  private static final String NAMESPACE = "namespace";
  private static final String POD_IP = "podIP";
  private static final String RELEASE_NAME = "releaseName";
  private static final HelmChartInfo HELM_CHART_INFO =
      HelmChartInfo.builder().name("haha").repoUrl("sample.com").version("0.2.0").build();

  @Mock private CDFeatureFlagHelper cdFeatureFlagHelper;
  @InjectMocks private K8sInstanceSyncHandler k8sInstanceSyncHandler;

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetInfrastructureMappingType() {
    String infrastructureMappingType = k8sInstanceSyncHandler.getInfrastructureKind();

    assertThat(infrastructureMappingType).isEqualTo(InfrastructureKind.KUBERNETES_DIRECT);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetPerpetualTaskType() {
    String perpetualTaskType = k8sInstanceSyncHandler.getPerpetualTaskType();

    assertThat(perpetualTaskType).isEqualTo(PerpetualTaskType.K8S_INSTANCE_SYNC);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetInstanceType() {
    InstanceType instanceType = k8sInstanceSyncHandler.getInstanceType();

    assertThat(instanceType).isEqualTo(InstanceType.K8S_INSTANCE);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetInstanceInfoForServerInstance() {
    InstanceInfoDTO instanceInfoForServerInstance =
        k8sInstanceSyncHandler.getInstanceInfoForServerInstance(K8sServerInstanceInfo.builder()
                                                                    .name(POD_NAME)
                                                                    .blueGreenColor(BLUE_GREEN_COLOR)
                                                                    .namespace(NAMESPACE)
                                                                    .podIP(POD_IP)
                                                                    .releaseName(RELEASE_NAME)
                                                                    .containerList(getContainerList())
                                                                    .build());

    assertThat(instanceInfoForServerInstance).isNotNull();
    assertThat(instanceInfoForServerInstance).isInstanceOf(K8sInstanceInfoDTO.class);
    K8sInstanceInfoDTO k8sInstanceInfoDTO = (K8sInstanceInfoDTO) instanceInfoForServerInstance;
    assertThat(k8sInstanceInfoDTO).isNotNull();
    assertThat(k8sInstanceInfoDTO.getBlueGreenColor()).isEqualTo(BLUE_GREEN_COLOR);
    assertThat(k8sInstanceInfoDTO.getPodName()).isEqualTo(POD_NAME);
    assertThat(k8sInstanceInfoDTO.getNamespace()).isEqualTo(NAMESPACE);
    assertThat(k8sInstanceInfoDTO.getPodIP()).isEqualTo(POD_IP);
    assertThat(k8sInstanceInfoDTO.getReleaseName()).isEqualTo(RELEASE_NAME);
    assertThat(k8sInstanceInfoDTO.getContainerList().size()).isEqualTo(2);
    assertThat(k8sInstanceInfoDTO.getContainerList())
        .contains(getK8sContainer("containerName1", "containerId1", "image1"));
    assertThat(k8sInstanceInfoDTO.getContainerList())
        .contains(getK8sContainer("containerName2", "containerId2", "image2"));
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetDeploymentInfo() {
    K8sDirectInfrastructureOutcome k8sDirectInfrastructureOutcome = K8sDirectInfrastructureOutcome.builder().build();
    DeploymentInfoDTO deploymentInfo =
        k8sInstanceSyncHandler.getDeploymentInfo(k8sDirectInfrastructureOutcome, getServiceInstanceInfos());

    assertThat(deploymentInfo).isNotNull();
    assertThat(deploymentInfo).isInstanceOf(K8sDeploymentInfoDTO.class);
    K8sDeploymentInfoDTO k8sDeploymentInfoDTO = (K8sDeploymentInfoDTO) deploymentInfo;
    assertThat(k8sDeploymentInfoDTO.getBlueGreenStageColor()).isEqualTo(BLUE_GREEN_COLOR);
    assertThat(k8sDeploymentInfoDTO.getReleaseName()).isEqualTo(RELEASE_NAME);
    assertThat(k8sDeploymentInfoDTO.getNamespaces()).contains(NAMESPACE);
    assertThat(k8sDeploymentInfoDTO.getHelmChartInfo()).isEqualTo(HELM_CHART_INFO);
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testGetDeploymentReleaseDetails() {
    LinkedHashSet<String> namespaces = new LinkedHashSet<>();
    namespaces.add("namespace1");
    doReturn(true).when(cdFeatureFlagHelper).isEnabled(any(), eq(CDS_EKS_ADD_REGIONAL_PARAM));
    List<DeploymentInfoDetailsDTO> deploymentInfoDetailsDTOList = Arrays.asList(
        DeploymentInfoDetailsDTO.builder()
            .deploymentInfoDTO(K8sDeploymentInfoDTO.builder().releaseName("releaseName").namespaces(namespaces).build())
            .build());
    DeploymentReleaseDetails deploymentReleaseDetails = k8sInstanceSyncHandler.getDeploymentReleaseDetails(
        InstanceSyncPerpetualTaskInfoDTO.builder()
            .id("taskInfoId")
            .deploymentInfoDetailsDTOList(deploymentInfoDetailsDTOList)
            .build());

    assertThat(deploymentReleaseDetails).isNotNull();
    assertThat(deploymentReleaseDetails.getDeploymentDetails().size()).isEqualTo(1);
    assertThat(((K8sDeploymentReleaseDetails) deploymentReleaseDetails.getDeploymentDetails().get(0)).getReleaseName())
        .isEqualTo("releaseName");
    assertThat(
        ((K8sDeploymentReleaseDetails) deploymentReleaseDetails.getDeploymentDetails().get(0)).getNamespaces().size())
        .isEqualTo(1);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testUpdateDeploymentInfoDTO() {
    K8sDeploymentInfoDTO k8sDeploymentInfoDTO = K8sDeploymentInfoDTO.builder().build();
    k8sInstanceSyncHandler.updateDeploymentInfoDTO(
        k8sDeploymentInfoDTO, K8sDeploymentOutcomeMetadata.builder().canary(true).build());
    assertThat(k8sDeploymentInfoDTO.isCanary()).isTrue();

    k8sInstanceSyncHandler.updateDeploymentInfoDTO(
        k8sDeploymentInfoDTO, K8sDeploymentOutcomeMetadata.builder().canary(false).build());
    assertThat(k8sDeploymentInfoDTO.isCanary()).isFalse();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testUpdateDeploymentInfoDTONegativeCases() {
    DeploymentInfoDTO deploymentInfoDTO = mock(DeploymentInfoDTO.class);
    K8sDeploymentInfoDTO k8sDeploymentInfoDTO = mock(K8sDeploymentInfoDTO.class);
    DeploymentOutcomeMetadata deploymentOutcomeMetadata = mock(DeploymentOutcomeMetadata.class);

    k8sInstanceSyncHandler.updateDeploymentInfoDTO(null, null);
    k8sInstanceSyncHandler.updateDeploymentInfoDTO(deploymentInfoDTO, null);
    k8sInstanceSyncHandler.updateDeploymentInfoDTO(deploymentInfoDTO, deploymentOutcomeMetadata);
    k8sInstanceSyncHandler.updateDeploymentInfoDTO(
        deploymentInfoDTO, K8sDeploymentOutcomeMetadata.builder().canary(true).build());
    verifyNoInteractions(deploymentInfoDTO);
    verifyNoInteractions(deploymentOutcomeMetadata);

    k8sInstanceSyncHandler.updateDeploymentInfoDTO(k8sDeploymentInfoDTO, null);
    k8sInstanceSyncHandler.updateDeploymentInfoDTO(k8sDeploymentInfoDTO, deploymentOutcomeMetadata);
    verifyNoInteractions(k8sDeploymentInfoDTO);
  }

  private List<K8sContainer> getContainerList() {
    List<K8sContainer> k8sContainers = new LinkedList<>();
    k8sContainers.add(getK8sContainer("containerName1", "containerId1", "image1"));
    k8sContainers.add(getK8sContainer("containerName2", "containerId2", "image2"));

    return k8sContainers;
  }

  private K8sContainer getK8sContainer(String containerName, String containerId, String image) {
    return K8sContainer.builder().name(containerName).containerId(containerId).image(image).build();
  }

  private List<ServerInstanceInfo> getServiceInstanceInfos() {
    return Collections.singletonList(K8sServerInstanceInfo.builder()
                                         .podIP(POD_IP)
                                         .name(POD_NAME)
                                         .namespace(NAMESPACE)
                                         .releaseName(RELEASE_NAME)
                                         .blueGreenColor(BLUE_GREEN_COLOR)
                                         .helmChartInfo(HELM_CHART_INFO)
                                         .build());
  }
}
