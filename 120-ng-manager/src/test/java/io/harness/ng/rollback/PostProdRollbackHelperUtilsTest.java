/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.rollback;

import static io.harness.rule.OwnerRule.PRATYUSH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.dtos.DeploymentSummaryDTO;
import io.harness.dtos.InfrastructureMappingDTO;
import io.harness.dtos.rollback.K8sPostProdRollbackInfo;
import io.harness.dtos.rollback.NativeHelmPostProdRollbackInfo;
import io.harness.dtos.rollback.PostProdRollbackSwimLaneInfo;
import io.harness.entities.ArtifactDetails;
import io.harness.entities.Instance;
import io.harness.entities.InstanceType;
import io.harness.entities.instanceinfo.K8sInstanceInfo;
import io.harness.entities.instanceinfo.NativeHelmInstanceInfo;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.rule.Owner;
import io.harness.service.deploymentsummary.DeploymentSummaryService;
import io.harness.service.infrastructuremapping.InfrastructureMappingService;

import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@OwnedBy(HarnessTeam.CDP)
public class PostProdRollbackHelperUtilsTest {
  @InjectMocks @Spy private PostProdRollbackHelperUtils postProdRollbackHelperUtils;
  @Mock private DeploymentSummaryService deploymentSummaryService;
  @Mock private InfrastructureMappingService infrastructureMappingService;
  String instanceKey = "instanceUuid";
  String infraMappingId = "instanceUuid";
  String accountId = "accountId";
  String planExecutionId = "planExecutionId";
  String orgId = "orgId";
  String projectId = "projectId";
  String serviceId = "serviceId";
  String envId = "envId";
  String infraId = "infraId";
  String artifactName = "artifactName";
  String artifactId = "artifactId";
  String releaseName = "releaseName";
  String connectorRef = "connectorRef";
  String infraKind = "KubernetesDirect";
  String infraKey = "infraKey";

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testGetK8sSwimlaneInfoNoArtifact() {
    Instance instance = Instance.builder()
                            .lastPipelineExecutionName(planExecutionId)
                            .lastDeployedAt(100)
                            .lastPipelineExecutionId(planExecutionId)
                            .infrastructureMappingId(infraMappingId)
                            .infrastructureKind(infraKind)
                            .instanceType(InstanceType.K8S_INSTANCE)
                            .id(instanceKey)
                            .accountIdentifier(accountId)
                            .orgIdentifier(orgId)
                            .projectIdentifier(projectId)
                            .serviceIdentifier(serviceId)
                            .envName(envId)
                            .envIdentifier(envId)
                            .infraName(infraId)
                            .infraIdentifier(infraId)
                            .instanceInfo(K8sInstanceInfo.builder().releaseName(releaseName).build())
                            .connectorRef(connectorRef)
                            .build();

    doReturn(Optional.empty()).when(infrastructureMappingService).getByInfrastructureMappingId(eq(infraMappingId));
    PostProdRollbackSwimLaneInfo postProdRollbackSwimLaneInfo = postProdRollbackHelperUtils.getSwimlaneInfo(instance);
    assertThat(postProdRollbackSwimLaneInfo).isInstanceOf(K8sPostProdRollbackInfo.class);
    K8sPostProdRollbackInfo k8sPostProdRollbackInfo = (K8sPostProdRollbackInfo) postProdRollbackSwimLaneInfo;
    assertThat(k8sPostProdRollbackInfo.getLastPipelineExecutionName()).isEqualTo(planExecutionId);
    assertThat(k8sPostProdRollbackInfo.getLastPipelineExecutionId()).isEqualTo(planExecutionId);
    assertThat(k8sPostProdRollbackInfo.getLastDeployedAt()).isEqualTo(100);
    assertThat(k8sPostProdRollbackInfo.getEnvName()).isEqualTo(envId);
    assertThat(k8sPostProdRollbackInfo.getEnvIdentifier()).isEqualTo(envId);
    assertThat(k8sPostProdRollbackInfo.getInfraIdentifier()).isEqualTo(infraId);
    assertThat(k8sPostProdRollbackInfo.getInfraName()).isEqualTo(infraId);
    assertThat(k8sPostProdRollbackInfo.getCurrentArtifactDisplayName()).isNull();
    assertThat(k8sPostProdRollbackInfo.getCurrentArtifactId()).isNull();
    assertThat(k8sPostProdRollbackInfo.getPreviousArtifactId()).isNull();
    assertThat(k8sPostProdRollbackInfo.getPreviousArtifactDisplayName()).isNull();
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testGetK8sSwimlaneInfo() {
    Instance instance =
        Instance.builder()
            .lastPipelineExecutionName(planExecutionId)
            .lastDeployedAt(100)
            .lastPipelineExecutionId(planExecutionId)
            .infrastructureMappingId(infraMappingId)
            .instanceType(InstanceType.K8S_INSTANCE)
            .id(instanceKey)
            .accountIdentifier(accountId)
            .orgIdentifier(orgId)
            .projectIdentifier(projectId)
            .serviceIdentifier(serviceId)
            .envName(envId)
            .envIdentifier(envId)
            .infraName(infraId)
            .infraIdentifier(infraId)
            .primaryArtifact(ArtifactDetails.builder().artifactId(artifactId).displayName(artifactName).build())
            .instanceInfo(K8sInstanceInfo.builder().releaseName(releaseName).blueGreenColor(null).build())
            .connectorRef(connectorRef)
            .build();

    InfrastructureMappingDTO infrastructureMappingDTO = mockInfraMappingDTO(instance);
    doReturn(Optional.of(infrastructureMappingDTO))
        .when(infrastructureMappingService)
        .getByInfrastructureMappingId(eq(infraMappingId));
    doReturn(Optional.empty())
        .when(deploymentSummaryService)
        .getNthDeploymentSummaryFromNow(eq(2), eq(releaseName), eq(infrastructureMappingDTO), eq(false));
    PostProdRollbackSwimLaneInfo postProdRollbackSwimLaneInfo = postProdRollbackHelperUtils.getSwimlaneInfo(instance);
    assertThat(postProdRollbackSwimLaneInfo).isInstanceOf(K8sPostProdRollbackInfo.class);
    K8sPostProdRollbackInfo k8sPostProdRollbackInfo = (K8sPostProdRollbackInfo) postProdRollbackSwimLaneInfo;
    assertThat(k8sPostProdRollbackInfo.getLastPipelineExecutionName()).isEqualTo(planExecutionId);
    assertThat(k8sPostProdRollbackInfo.getLastPipelineExecutionId()).isEqualTo(planExecutionId);
    assertThat(k8sPostProdRollbackInfo.getLastDeployedAt()).isEqualTo(100);
    assertThat(k8sPostProdRollbackInfo.getEnvName()).isEqualTo(envId);
    assertThat(k8sPostProdRollbackInfo.getEnvIdentifier()).isEqualTo(envId);
    assertThat(k8sPostProdRollbackInfo.getInfraIdentifier()).isEqualTo(infraId);
    assertThat(k8sPostProdRollbackInfo.getInfraName()).isEqualTo(infraId);
    assertThat(k8sPostProdRollbackInfo.getCurrentArtifactDisplayName()).isEqualTo(artifactName);
    assertThat(k8sPostProdRollbackInfo.getCurrentArtifactId()).isEqualTo(artifactId);
    assertThat(k8sPostProdRollbackInfo.getPreviousArtifactId()).isNull();
    assertThat(k8sPostProdRollbackInfo.getPreviousArtifactDisplayName()).isNull();
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testGetK8sSwimlaneInfoWithArtifact() {
    Instance instance =
        Instance.builder()
            .lastPipelineExecutionName(planExecutionId)
            .lastDeployedAt(100)
            .lastPipelineExecutionId(planExecutionId)
            .infrastructureMappingId(infraMappingId)
            .infrastructureKind(infraKind)
            .instanceType(InstanceType.K8S_INSTANCE)
            .id(instanceKey)
            .accountIdentifier(accountId)
            .orgIdentifier(orgId)
            .projectIdentifier(projectId)
            .serviceIdentifier(serviceId)
            .envName(envId)
            .envIdentifier(envId)
            .infraName(infraId)
            .infraIdentifier(infraId)
            .primaryArtifact(ArtifactDetails.builder().artifactId(artifactId).displayName(artifactName).build())
            .instanceInfo(K8sInstanceInfo.builder().releaseName(releaseName).build())
            .connectorRef(connectorRef)
            .build();

    InfrastructureMappingDTO infrastructureMappingDTO = mockInfraMappingDTO(instance);
    Optional<DeploymentSummaryDTO> optionalDeploymentSummaryDTO = createDeploymentSummaryDTO(instance, false);
    doReturn(Optional.of(infrastructureMappingDTO))
        .when(infrastructureMappingService)
        .getByInfrastructureMappingId(eq(infraMappingId));
    doReturn(optionalDeploymentSummaryDTO)
        .when(deploymentSummaryService)
        .getNthDeploymentSummaryFromNow(eq(2), eq(releaseName), eq(infrastructureMappingDTO), eq(false));
    PostProdRollbackSwimLaneInfo postProdRollbackSwimLaneInfo = postProdRollbackHelperUtils.getSwimlaneInfo(instance);
    assertThat(postProdRollbackSwimLaneInfo).isInstanceOf(K8sPostProdRollbackInfo.class);
    K8sPostProdRollbackInfo k8sPostProdRollbackInfo = (K8sPostProdRollbackInfo) postProdRollbackSwimLaneInfo;
    assertThat(k8sPostProdRollbackInfo.getLastPipelineExecutionName()).isEqualTo(planExecutionId);
    assertThat(k8sPostProdRollbackInfo.getLastPipelineExecutionId()).isEqualTo(planExecutionId);
    assertThat(k8sPostProdRollbackInfo.getLastDeployedAt()).isEqualTo(100);
    assertThat(k8sPostProdRollbackInfo.getEnvName()).isEqualTo(envId);
    assertThat(k8sPostProdRollbackInfo.getEnvIdentifier()).isEqualTo(envId);
    assertThat(k8sPostProdRollbackInfo.getInfraIdentifier()).isEqualTo(infraId);
    assertThat(k8sPostProdRollbackInfo.getInfraName()).isEqualTo(infraId);
    assertThat(k8sPostProdRollbackInfo.getCurrentArtifactDisplayName()).isEqualTo(artifactName);
    assertThat(k8sPostProdRollbackInfo.getCurrentArtifactId()).isEqualTo(artifactId);
    assertThat(k8sPostProdRollbackInfo.getPreviousArtifactId()).isEqualTo("rollback_artifact_id");
    assertThat(k8sPostProdRollbackInfo.getPreviousArtifactDisplayName()).isEqualTo("rollback_artifact_name");
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testGetK8sSwimlaneInfoWithBgDeploymentInstanceSyncKeyCreation() {
    Instance instance =
        Instance.builder()
            .lastPipelineExecutionName(planExecutionId)
            .lastDeployedAt(100)
            .lastPipelineExecutionId(planExecutionId)
            .infrastructureMappingId(infraMappingId)
            .infrastructureKind(infraKind)
            .instanceType(InstanceType.K8S_INSTANCE)
            .id(instanceKey)
            .accountIdentifier(accountId)
            .orgIdentifier(orgId)
            .projectIdentifier(projectId)
            .serviceIdentifier(serviceId)
            .envName(envId)
            .envIdentifier(envId)
            .infraName(infraId)
            .infraIdentifier(infraId)
            .primaryArtifact(ArtifactDetails.builder().artifactId(artifactId).displayName(artifactName).build())
            .instanceInfo(K8sInstanceInfo.builder().releaseName(releaseName).blueGreenColor("blue").build())
            .connectorRef(connectorRef)
            .build();

    InfrastructureMappingDTO infrastructureMappingDTO = mockInfraMappingDTO(instance);
    Optional<DeploymentSummaryDTO> optionalDeploymentSummaryDTO = createDeploymentSummaryDTO(instance, false);
    doReturn(Optional.of(infrastructureMappingDTO))
        .when(infrastructureMappingService)
        .getByInfrastructureMappingId(eq(infraMappingId));
    doReturn(optionalDeploymentSummaryDTO)
        .when(deploymentSummaryService)
        .getNthDeploymentSummaryFromNow(eq(1), eq(releaseName + "_green"), eq(infrastructureMappingDTO), eq(false));
    PostProdRollbackSwimLaneInfo postProdRollbackSwimLaneInfo = postProdRollbackHelperUtils.getSwimlaneInfo(instance);
    assertThat(postProdRollbackSwimLaneInfo).isInstanceOf(K8sPostProdRollbackInfo.class);
    K8sPostProdRollbackInfo k8sPostProdRollbackInfo = (K8sPostProdRollbackInfo) postProdRollbackSwimLaneInfo;
    assertThat(k8sPostProdRollbackInfo.getPreviousArtifactId()).isEqualTo("rollback_artifact_id");
    assertThat(k8sPostProdRollbackInfo.getPreviousArtifactDisplayName()).isEqualTo("rollback_artifact_name");

    // Inverse color
    instance.setInstanceInfo(K8sInstanceInfo.builder().releaseName(releaseName).blueGreenColor("green").build());
    doReturn(Optional.of(infrastructureMappingDTO))
        .when(infrastructureMappingService)
        .getByInfrastructureMappingId(eq(infraMappingId));
    doReturn(optionalDeploymentSummaryDTO)
        .when(deploymentSummaryService)
        .getNthDeploymentSummaryFromNow(eq(1), eq(releaseName + "_blue"), eq(infrastructureMappingDTO), eq(false));
    postProdRollbackSwimLaneInfo = postProdRollbackHelperUtils.getSwimlaneInfo(instance);
    assertThat(postProdRollbackSwimLaneInfo).isInstanceOf(K8sPostProdRollbackInfo.class);
    k8sPostProdRollbackInfo = (K8sPostProdRollbackInfo) postProdRollbackSwimLaneInfo;
    assertThat(k8sPostProdRollbackInfo.getPreviousArtifactId()).isEqualTo("rollback_artifact_id");
    assertThat(k8sPostProdRollbackInfo.getPreviousArtifactDisplayName()).isEqualTo("rollback_artifact_name");
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testGetNativeHelmSwimlaneInfoNoArtifact() {
    Instance instance = Instance.builder()
                            .lastPipelineExecutionName(planExecutionId)
                            .lastDeployedAt(100)
                            .lastPipelineExecutionId(planExecutionId)
                            .infrastructureMappingId(infraMappingId)
                            .infrastructureKind(infraKind)
                            .instanceType(InstanceType.NATIVE_HELM_INSTANCE)
                            .id(instanceKey)
                            .accountIdentifier(accountId)
                            .orgIdentifier(orgId)
                            .projectIdentifier(projectId)
                            .serviceIdentifier(serviceId)
                            .envName(envId)
                            .envIdentifier(envId)
                            .infraName(infraId)
                            .infraIdentifier(infraId)
                            .instanceInfo(NativeHelmInstanceInfo.builder().releaseName(releaseName).build())
                            .connectorRef(connectorRef)
                            .build();

    doReturn(Optional.empty()).when(infrastructureMappingService).getByInfrastructureMappingId(eq(infraMappingId));
    PostProdRollbackSwimLaneInfo postProdRollbackSwimLaneInfo = postProdRollbackHelperUtils.getSwimlaneInfo(instance);
    assertThat(postProdRollbackSwimLaneInfo).isInstanceOf(NativeHelmPostProdRollbackInfo.class);
    NativeHelmPostProdRollbackInfo nativeHelmPostProdRollbackInfo =
        (NativeHelmPostProdRollbackInfo) postProdRollbackSwimLaneInfo;
    assertThat(nativeHelmPostProdRollbackInfo.getLastPipelineExecutionName()).isEqualTo(planExecutionId);
    assertThat(nativeHelmPostProdRollbackInfo.getLastPipelineExecutionId()).isEqualTo(planExecutionId);
    assertThat(nativeHelmPostProdRollbackInfo.getLastDeployedAt()).isEqualTo(100);
    assertThat(nativeHelmPostProdRollbackInfo.getEnvName()).isEqualTo(envId);
    assertThat(nativeHelmPostProdRollbackInfo.getEnvIdentifier()).isEqualTo(envId);
    assertThat(nativeHelmPostProdRollbackInfo.getInfraIdentifier()).isEqualTo(infraId);
    assertThat(nativeHelmPostProdRollbackInfo.getInfraName()).isEqualTo(infraId);
    assertThat(nativeHelmPostProdRollbackInfo.getCurrentArtifactDisplayName()).isNull();
    assertThat(nativeHelmPostProdRollbackInfo.getCurrentArtifactId()).isNull();
    assertThat(nativeHelmPostProdRollbackInfo.getPreviousArtifactId()).isNull();
    assertThat(nativeHelmPostProdRollbackInfo.getPreviousArtifactDisplayName()).isNull();
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testGetNativeHelmSwimlaneInfo() {
    Instance instance =
        Instance.builder()
            .lastPipelineExecutionName(planExecutionId)
            .lastDeployedAt(100)
            .lastPipelineExecutionId(planExecutionId)
            .infrastructureMappingId(infraMappingId)
            .infrastructureKind(infraKind)
            .instanceType(InstanceType.NATIVE_HELM_INSTANCE)
            .id(instanceKey)
            .accountIdentifier(accountId)
            .orgIdentifier(orgId)
            .projectIdentifier(projectId)
            .serviceIdentifier(serviceId)
            .envName(envId)
            .envIdentifier(envId)
            .infraName(infraId)
            .infraIdentifier(infraId)
            .primaryArtifact(ArtifactDetails.builder().artifactId(artifactId).displayName(artifactName).build())
            .instanceInfo(NativeHelmInstanceInfo.builder().releaseName(releaseName).build())
            .connectorRef(connectorRef)
            .build();

    InfrastructureMappingDTO infrastructureMappingDTO = mockInfraMappingDTO(instance);
    doReturn(Optional.of(infrastructureMappingDTO))
        .when(infrastructureMappingService)
        .getByInfrastructureMappingId(eq(infraMappingId));
    doReturn(Optional.empty())
        .when(deploymentSummaryService)
        .getNthDeploymentSummaryFromNow(eq(2), eq(releaseName), eq(infrastructureMappingDTO), eq(false));
    PostProdRollbackSwimLaneInfo postProdRollbackSwimLaneInfo = postProdRollbackHelperUtils.getSwimlaneInfo(instance);
    assertThat(postProdRollbackSwimLaneInfo).isInstanceOf(NativeHelmPostProdRollbackInfo.class);
    NativeHelmPostProdRollbackInfo nativeHelmPostProdRollbackInfo =
        (NativeHelmPostProdRollbackInfo) postProdRollbackSwimLaneInfo;
    assertThat(nativeHelmPostProdRollbackInfo.getLastPipelineExecutionName()).isEqualTo(planExecutionId);
    assertThat(nativeHelmPostProdRollbackInfo.getLastPipelineExecutionId()).isEqualTo(planExecutionId);
    assertThat(nativeHelmPostProdRollbackInfo.getLastDeployedAt()).isEqualTo(100);
    assertThat(nativeHelmPostProdRollbackInfo.getEnvName()).isEqualTo(envId);
    assertThat(nativeHelmPostProdRollbackInfo.getEnvIdentifier()).isEqualTo(envId);
    assertThat(nativeHelmPostProdRollbackInfo.getInfraIdentifier()).isEqualTo(infraId);
    assertThat(nativeHelmPostProdRollbackInfo.getInfraName()).isEqualTo(infraId);
    assertThat(nativeHelmPostProdRollbackInfo.getCurrentArtifactDisplayName()).isEqualTo(artifactName);
    assertThat(nativeHelmPostProdRollbackInfo.getCurrentArtifactId()).isEqualTo(artifactId);
    assertThat(nativeHelmPostProdRollbackInfo.getPreviousArtifactId()).isNull();
    assertThat(nativeHelmPostProdRollbackInfo.getPreviousArtifactDisplayName()).isNull();
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testGetNativeHelmSwimlaneInfoWithArtifact() {
    Instance instance =
        Instance.builder()
            .lastPipelineExecutionName(planExecutionId)
            .lastDeployedAt(100)
            .lastPipelineExecutionId(planExecutionId)
            .infrastructureMappingId(infraMappingId)
            .infrastructureKind(infraKind)
            .instanceType(InstanceType.NATIVE_HELM_INSTANCE)
            .id(instanceKey)
            .accountIdentifier(accountId)
            .orgIdentifier(orgId)
            .projectIdentifier(projectId)
            .serviceIdentifier(serviceId)
            .envName(envId)
            .envIdentifier(envId)
            .infraName(infraId)
            .infraIdentifier(infraId)
            .primaryArtifact(ArtifactDetails.builder().artifactId(artifactId).displayName(artifactName).build())
            .instanceInfo(NativeHelmInstanceInfo.builder().releaseName(releaseName).build())
            .connectorRef(connectorRef)
            .build();

    InfrastructureMappingDTO infrastructureMappingDTO = mockInfraMappingDTO(instance);
    Optional<DeploymentSummaryDTO> optionalDeploymentSummaryDTO = createDeploymentSummaryDTO(instance, false);
    doReturn(Optional.of(infrastructureMappingDTO))
        .when(infrastructureMappingService)
        .getByInfrastructureMappingId(eq(infraMappingId));
    doReturn(optionalDeploymentSummaryDTO)
        .when(deploymentSummaryService)
        .getNthDeploymentSummaryFromNow(eq(2), eq(releaseName), eq(infrastructureMappingDTO), eq(false));

    PostProdRollbackSwimLaneInfo postProdRollbackSwimLaneInfo = postProdRollbackHelperUtils.getSwimlaneInfo(instance);
    assertThat(postProdRollbackSwimLaneInfo).isInstanceOf(NativeHelmPostProdRollbackInfo.class);
    NativeHelmPostProdRollbackInfo nativeHelmPostProdRollbackInfo =
        (NativeHelmPostProdRollbackInfo) postProdRollbackSwimLaneInfo;
    assertThat(nativeHelmPostProdRollbackInfo.getLastPipelineExecutionName()).isEqualTo(planExecutionId);
    assertThat(nativeHelmPostProdRollbackInfo.getLastPipelineExecutionId()).isEqualTo(planExecutionId);
    assertThat(nativeHelmPostProdRollbackInfo.getLastDeployedAt()).isEqualTo(100);
    assertThat(nativeHelmPostProdRollbackInfo.getEnvName()).isEqualTo(envId);
    assertThat(nativeHelmPostProdRollbackInfo.getEnvIdentifier()).isEqualTo(envId);
    assertThat(nativeHelmPostProdRollbackInfo.getInfraIdentifier()).isEqualTo(infraId);
    assertThat(nativeHelmPostProdRollbackInfo.getInfraName()).isEqualTo(infraId);
    assertThat(nativeHelmPostProdRollbackInfo.getCurrentArtifactDisplayName()).isEqualTo(artifactName);
    assertThat(nativeHelmPostProdRollbackInfo.getCurrentArtifactId()).isEqualTo(artifactId);
    assertThat(nativeHelmPostProdRollbackInfo.getPreviousArtifactId()).isEqualTo("rollback_artifact_id");
    assertThat(nativeHelmPostProdRollbackInfo.getPreviousArtifactDisplayName()).isEqualTo("rollback_artifact_name");
  }

  private Optional<DeploymentSummaryDTO> createDeploymentSummaryDTO(Instance instance, boolean isRollbackDeployment) {
    return Optional.of(DeploymentSummaryDTO.builder()
                           .accountIdentifier(instance.getAccountIdentifier())
                           .artifactDetails(ArtifactDetails.builder()
                                                .displayName("rollback_artifact_name")
                                                .artifactId("rollback_artifact_id")
                                                .build())
                           .isRollbackDeployment(isRollbackDeployment)
                           .infrastructureMappingId(instance.getInfrastructureMappingId())
                           .orgIdentifier(instance.getOrgIdentifier())
                           .projectIdentifier(instance.getProjectIdentifier())
                           .build());
  }

  private InfrastructureMappingDTO mockInfraMappingDTO(Instance instance) {
    return InfrastructureMappingDTO.builder()
        .id(instance.getInfrastructureMappingId())
        .accountIdentifier(instance.getAccountIdentifier())
        .orgIdentifier(instance.getOrgIdentifier())
        .projectIdentifier(instance.getProjectIdentifier())
        .infrastructureKind(InfrastructureKind.KUBERNETES_DIRECT)
        .envIdentifier(instance.getEnvIdentifier())
        .serviceIdentifier(instance.getServiceIdentifier())
        .infrastructureKey(infraKey)
        .build();
  }
}
