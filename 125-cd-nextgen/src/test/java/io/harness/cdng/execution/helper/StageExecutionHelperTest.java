/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.execution.helper;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.cdng.instance.InstanceDeploymentInfoStatus.SUCCEEDED;
import static io.harness.rule.OwnerRule.ANIL;
import static io.harness.rule.OwnerRule.ARVIND;
import static io.harness.rule.OwnerRule.IVAN;
import static io.harness.rule.OwnerRule.VLAD;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.Scope;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.artifact.outcome.ArtifactOutcome;
import io.harness.cdng.artifact.outcome.ArtifactoryArtifactOutcome;
import io.harness.cdng.artifact.outcome.DockerArtifactOutcome;
import io.harness.cdng.artifact.outcome.NexusArtifactOutcome;
import io.harness.cdng.configfile.ConfigFilesOutcome;
import io.harness.cdng.customDeployment.beans.CustomDeploymentExecutionDetails;
import io.harness.cdng.execution.DefaultExecutionDetails;
import io.harness.cdng.execution.ExecutionDetails;
import io.harness.cdng.execution.ExecutionInfoKey;
import io.harness.cdng.execution.StageExecutionInfo;
import io.harness.cdng.execution.service.StageExecutionInfoService;
import io.harness.cdng.execution.sshwinrm.SshWinRmStageExecutionDetails;
import io.harness.cdng.instance.InstanceDeploymentInfo;
import io.harness.cdng.instance.service.InstanceDeploymentInfoService;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.entities.instanceinfo.PdcInstanceInfo;
import io.harness.exception.InvalidArgumentsException;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.rule.Owner;
import io.harness.utils.NGFeatureFlagHelperService;
import io.harness.utils.StageStatus;

import software.wings.utils.ArtifactType;

import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.internal.util.collections.Sets;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(CDP)
@RunWith(MockitoJUnitRunner.class)
public class StageExecutionHelperTest extends CategoryTest {
  private static final String ENV_IDENTIFIER = "envIdentifier";
  private static final String INFRA_IDENTIFIER = "infraIdentifier";

  private static final String DEPLOYMENT_IDENTIFIER = "deploymentId";
  private static final String SERVICE_IDENTIFIER = "serviceIdentifier";
  private static final String INFRA_KIND = InfrastructureKind.PDC;
  private static final String ACCOUNT_IDENTIFIER = "accountIdentifier";
  private static final String ORG_IDENTIFIER = "orgIdentifier";
  private static final String PROJECT_IDENTIFIER = "projectIdentifier";
  private static final String EXECUTION_ID = "executionId";
  private static final String PLAN_EXECUTION_ID = "planExecutionId";

  @Mock private CDStepHelper cdStepHelper;
  @Mock private InstanceDeploymentInfoService instanceDeploymentInfoService;
  @Mock private StageExecutionInfoService stageExecutionInfoService;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;
  @Mock private NGFeatureFlagHelperService ngFeatureFlagHelperService;
  @InjectMocks private StageExecutionHelper stageExecutionHelper;
  private AutoCloseable mocks;

  @Before
  public void setUp() throws Exception {
    mocks = MockitoAnnotations.openMocks(this);
    when(ngFeatureFlagHelperService.isEnabled(anyString(), any(FeatureName.class))).thenReturn(false);
  }
  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testSaveStageExecutionInfo() {
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions(SetupAbstractionKeys.accountId, ACCOUNT_IDENTIFIER)
                            .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, ORG_IDENTIFIER)
                            .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, PROJECT_IDENTIFIER)
                            .setStageExecutionId(EXECUTION_ID)
                            .setPlanExecutionId(PLAN_EXECUTION_ID)
                            .build();

    Scope scope = Scope.of(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);

    ArtifactoryArtifactOutcome artifactOutcome = ArtifactoryArtifactOutcome.builder().build();
    ConfigFilesOutcome configFilesOutcome = new ConfigFilesOutcome();

    when(cdStepHelper.resolveArtifactsOutcome(ambiance)).thenReturn(Optional.ofNullable(artifactOutcome));
    when(cdStepHelper.getConfigFilesOutcome(ambiance)).thenReturn(Optional.of(configFilesOutcome));

    stageExecutionHelper.saveStageExecutionInfo(ambiance,
        ExecutionInfoKey.builder()
            .scope(scope)
            .envIdentifier(ENV_IDENTIFIER)
            .infraIdentifier(INFRA_IDENTIFIER)
            .serviceIdentifier(SERVICE_IDENTIFIER)
            .build(),
        INFRA_KIND);

    verify(stageExecutionInfoService)
        .save(StageExecutionInfo.builder()
                  .accountIdentifier(ACCOUNT_IDENTIFIER)
                  .orgIdentifier(ORG_IDENTIFIER)
                  .projectIdentifier(PROJECT_IDENTIFIER)
                  .envIdentifier(ENV_IDENTIFIER)
                  .infraIdentifier(INFRA_IDENTIFIER)
                  .serviceIdentifier(SERVICE_IDENTIFIER)
                  .stageStatus(StageStatus.IN_PROGRESS)
                  .stageExecutionId(EXECUTION_ID)
                  .planExecutionId(PLAN_EXECUTION_ID)
                  .executionDetails(SshWinRmStageExecutionDetails.builder()
                                        .artifactsOutcome(Lists.newArrayList(artifactOutcome))
                                        .configFilesOutcome(configFilesOutcome)
                                        .build())
                  .build());
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testSaveStageExecutionInfoWithKubernetesInfraNoArtifact() {
    Ambiance ambiance = Ambiance.newBuilder().build();

    stageExecutionHelper.saveStageExecutionInfo(ambiance,
        ExecutionInfoKey.builder()
            .scope(Scope.of(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER))
            .envIdentifier(ENV_IDENTIFIER)
            .infraIdentifier(INFRA_IDENTIFIER)
            .serviceIdentifier(SERVICE_IDENTIFIER)
            .build(),
        InfrastructureKind.KUBERNETES_GCP);

    ArgumentCaptor<StageExecutionInfo> stageExecutionInfoArgumentCaptor =
        ArgumentCaptor.forClass(StageExecutionInfo.class);
    verify(stageExecutionInfoService, times(0)).save(stageExecutionInfoArgumentCaptor.capture());
  }

  @Test
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void testSaveStageExecutionInfoWithKubernetesInfra() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    when(cdStepHelper.resolveArtifactsOutcome(ambiance)).thenReturn(Optional.of(mock(ArtifactOutcome.class)));

    stageExecutionHelper.saveStageExecutionInfo(ambiance,
        ExecutionInfoKey.builder()
            .scope(Scope.of(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER))
            .envIdentifier(ENV_IDENTIFIER)
            .infraIdentifier(INFRA_IDENTIFIER)
            .serviceIdentifier(SERVICE_IDENTIFIER)
            .build(),
        InfrastructureKind.KUBERNETES_GCP);

    ArgumentCaptor<StageExecutionInfo> stageExecutionInfoArgumentCaptor =
        ArgumentCaptor.forClass(StageExecutionInfo.class);
    verify(stageExecutionInfoService, times(1)).save(stageExecutionInfoArgumentCaptor.capture());

    StageExecutionInfo value = stageExecutionInfoArgumentCaptor.getValue();
    assertThat(value.getExecutionDetails()).isInstanceOf(DefaultExecutionDetails.class);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testSaveStageExecutionInfoWithoutInfraKind() {
    Ambiance ambiance = Ambiance.newBuilder().build();

    assertThatThrownBy(()
                           -> stageExecutionHelper.saveStageExecutionInfo(ambiance,
                               ExecutionInfoKey.builder()
                                   .envIdentifier(ENV_IDENTIFIER)
                                   .infraIdentifier(INFRA_IDENTIFIER)
                                   .serviceIdentifier(SERVICE_IDENTIFIER)
                                   .build(),
                               null))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage(
            "Unable to save stage execution info, infrastructure kind cannot be null or empty, infrastructureKind: null, executionInfoKey: ExecutionInfoKey(scope=null, envIdentifier=envIdentifier, infraIdentifier=infraIdentifier, serviceIdentifier=serviceIdentifier, deploymentIdentifier=null)");
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testExcludeHostsWithSameArtifactDeployed() {
    Ambiance ambiance = Mockito.mock(Ambiance.class);
    ExecutionInfoKey executionInfoKey = Mockito.mock(ExecutionInfoKey.class);
    Set<String> infrastructureHosts = Sets.newSet("host1", "host2", "host3");
    doReturn(
        Arrays.asList(
            InstanceDeploymentInfo.builder().instanceInfo(PdcInstanceInfo.builder().host("host2").build()).build()))
        .when(instanceDeploymentInfoService)
        .getByHostsAndArtifact(eq(executionInfoKey), eq(new ArrayList<>(infrastructureHosts)), any(), eq(SUCCEEDED));
    doReturn(Optional.empty()).when(cdStepHelper).resolveArtifactsOutcome(ambiance);
    Set<String> result =
        stageExecutionHelper.excludeHostsWithSameArtifactDeployed(ambiance, executionInfoKey, infrastructureHosts);
    assertThat(result).isEqualTo(infrastructureHosts);
    doReturn(Optional.of(NexusArtifactOutcome.builder().build())).when(cdStepHelper).resolveArtifactsOutcome(ambiance);
    result = stageExecutionHelper.excludeHostsWithSameArtifactDeployed(ambiance, executionInfoKey, infrastructureHosts);
    assertThat(result).contains("host1", "host3");
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testSaveStageExecutionInfoForCustomDeployment() {
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions(SetupAbstractionKeys.accountId, ACCOUNT_IDENTIFIER)
                            .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, ORG_IDENTIFIER)
                            .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, PROJECT_IDENTIFIER)
                            .setStageExecutionId(EXECUTION_ID)
                            .setPlanExecutionId(PLAN_EXECUTION_ID)
                            .build();

    Scope scope = Scope.of(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);

    ArtifactoryArtifactOutcome artifactOutcome = ArtifactoryArtifactOutcome.builder().build();
    ConfigFilesOutcome configFilesOutcome = new ConfigFilesOutcome();

    when(cdStepHelper.resolveArtifactsOutcome(ambiance)).thenReturn(Optional.ofNullable(artifactOutcome));
    when(cdStepHelper.getConfigFilesOutcome(ambiance)).thenReturn(Optional.of(configFilesOutcome));

    stageExecutionHelper.saveStageExecutionInfo(ambiance,
        ExecutionInfoKey.builder()
            .scope(scope)
            .envIdentifier(ENV_IDENTIFIER)
            .infraIdentifier(INFRA_IDENTIFIER)
            .serviceIdentifier(SERVICE_IDENTIFIER)
            .build(),
        InfrastructureKind.CUSTOM_DEPLOYMENT);

    verify(stageExecutionInfoService)
        .save(StageExecutionInfo.builder()
                  .accountIdentifier(ACCOUNT_IDENTIFIER)
                  .orgIdentifier(ORG_IDENTIFIER)
                  .projectIdentifier(PROJECT_IDENTIFIER)
                  .envIdentifier(ENV_IDENTIFIER)
                  .infraIdentifier(INFRA_IDENTIFIER)
                  .serviceIdentifier(SERVICE_IDENTIFIER)
                  .stageStatus(StageStatus.IN_PROGRESS)
                  .stageExecutionId(EXECUTION_ID)
                  .planExecutionId(PLAN_EXECUTION_ID)
                  .executionDetails(CustomDeploymentExecutionDetails.builder()
                                        .artifactsOutcome(Lists.newArrayList(artifactOutcome))
                                        .build())
                  .build());
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testRollbackArtifactCustomDeploymentFirstRun() {
    StepResponseBuilder stepResponseBuilder = StepResponse.builder();
    stepResponseBuilder.status(Status.SUCCEEDED);

    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions(SetupAbstractionKeys.accountId, ACCOUNT_IDENTIFIER)
                            .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, ORG_IDENTIFIER)
                            .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, PROJECT_IDENTIFIER)
                            .setStageExecutionId(EXECUTION_ID)
                            .build();

    Scope scope = Scope.of(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);

    ExecutionInfoKey executionInfoKey = ExecutionInfoKey.builder()
                                            .scope(scope)
                                            .envIdentifier(ENV_IDENTIFIER)
                                            .infraIdentifier(INFRA_IDENTIFIER)
                                            .serviceIdentifier(SERVICE_IDENTIFIER)
                                            .build();

    doReturn(Optional.empty())
        .when(stageExecutionInfoService)
        .getLatestSuccessfulStageExecutionInfo(eq(executionInfoKey), eq(EXECUTION_ID));

    stageExecutionHelper.addRollbackArtifactToStageOutcomeIfPresent(
        ambiance, stepResponseBuilder, executionInfoKey, InfrastructureKind.CUSTOM_DEPLOYMENT);

    StepResponse stepResponse = stepResponseBuilder.build();
    Collection<StepResponse.StepOutcome> stepOutcomes = stepResponse.getStepOutcomes();
    assertThat(stepOutcomes.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testRollbackArtifactCustomDeployment() {
    String image = "registry.hub.docker.com/library/nginx:latest";
    String imagePath = "library/nginx";
    String tag = "mainline-perl";
    String connectorRef = "privateDockerId";

    StepResponseBuilder stepResponseBuilder = StepResponse.builder();
    stepResponseBuilder.status(Status.SUCCEEDED);

    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions(SetupAbstractionKeys.accountId, ACCOUNT_IDENTIFIER)
                            .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, ORG_IDENTIFIER)
                            .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, PROJECT_IDENTIFIER)
                            .setStageExecutionId(EXECUTION_ID)
                            .build();

    Scope scope = Scope.of(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);

    ExecutionInfoKey executionInfoKey = ExecutionInfoKey.builder()
                                            .scope(scope)
                                            .envIdentifier(ENV_IDENTIFIER)
                                            .infraIdentifier(INFRA_IDENTIFIER)
                                            .serviceIdentifier(SERVICE_IDENTIFIER)
                                            .build();

    DockerArtifactOutcome rollbackArtifactOutcome = DockerArtifactOutcome.builder()
                                                        .primaryArtifact(true)
                                                        .image(image)
                                                        .imagePath(imagePath)
                                                        .tag(tag)
                                                        .connectorRef(connectorRef)
                                                        .type(ArtifactType.DOCKER.name())
                                                        .build();

    CustomDeploymentExecutionDetails executionDetails =
        CustomDeploymentExecutionDetails.builder()
            .artifactsOutcome(Collections.singletonList(rollbackArtifactOutcome))
            .build();

    StageExecutionInfo executionInfo = StageExecutionInfo.builder().executionDetails(executionDetails).build();

    doReturn(Optional.of(executionInfo))
        .when(stageExecutionInfoService)
        .getLatestSuccessfulStageExecutionInfo(eq(executionInfoKey), eq(EXECUTION_ID));

    stageExecutionHelper.addRollbackArtifactToStageOutcomeIfPresent(
        ambiance, stepResponseBuilder, executionInfoKey, InfrastructureKind.CUSTOM_DEPLOYMENT);

    StepResponse stepResponse = stepResponseBuilder.build();
    Collection<StepResponse.StepOutcome> stepOutcomes = stepResponse.getStepOutcomes();
    assertThat(stepOutcomes.size()).isEqualTo(1);

    StepResponse.StepOutcome stepOutcome = stepOutcomes.iterator().next();
    assertThat(stepOutcome.getGroup()).isEqualTo(StepCategory.STAGE.name());
    assertThat(stepOutcome.getName()).isEqualTo(OutcomeExpressionConstants.ROLLBACK_ARTIFACT);

    assertThat(stepOutcome.getOutcome()).isNotNull();
    assertThat(stepOutcome.getOutcome()).isInstanceOf(DockerArtifactOutcome.class);

    DockerArtifactOutcome rollbackOutCome = (DockerArtifactOutcome) stepOutcome.getOutcome();
    assertThat(rollbackOutCome.getArtifactType()).isEqualTo(ArtifactType.DOCKER.name());
    assertThat(rollbackOutCome.getImage()).isEqualTo(image);
    assertThat(rollbackOutCome.getImagePath()).isEqualTo(imagePath);
    assertThat(rollbackOutCome.getTag()).isEqualTo(tag);
    assertThat(rollbackOutCome.getConnectorRef()).isEqualTo(connectorRef);
    assertThat(rollbackOutCome.isPrimaryArtifact()).isEqualTo(true);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testSaveExecutionAndPublishExecutionInfoKey() {
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions(SetupAbstractionKeys.accountId, ACCOUNT_IDENTIFIER)
                            .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, ORG_IDENTIFIER)
                            .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, PROJECT_IDENTIFIER)
                            .setStageExecutionId(EXECUTION_ID)
                            .build();

    ExecutionInfoKey executionInfoKey = ExecutionInfoKey.builder()
                                            .scope(Scope.builder().build())
                                            .deploymentIdentifier(DEPLOYMENT_IDENTIFIER)
                                            .envIdentifier(ENV_IDENTIFIER)
                                            .infraIdentifier(INFRA_IDENTIFIER)
                                            .serviceIdentifier(SERVICE_IDENTIFIER)

                                            .build();

    String connectorRef = "artifactoryConnector";
    String repositoryName = "Harness-internal";
    String artifactPath = "animation/v1/test.war";

    ArtifactoryArtifactOutcome artifactOutcome = ArtifactoryArtifactOutcome.builder()
                                                     .connectorRef(connectorRef)
                                                     .artifactPath(artifactPath)
                                                     .repositoryName(repositoryName)
                                                     .primaryArtifact(true)
                                                     .build();
    when(cdStepHelper.resolveArtifactsOutcome(ambiance)).thenReturn(Optional.of(artifactOutcome));

    ArgumentCaptor<StageExecutionInfo> executionInfoArgumentCaptor = ArgumentCaptor.forClass(StageExecutionInfo.class);

    stageExecutionHelper.saveStageExecutionInfo(ambiance, executionInfoKey, InfrastructureKind.CUSTOM_DEPLOYMENT);

    verify(stageExecutionInfoService, times(1)).save(executionInfoArgumentCaptor.capture());
    StageExecutionInfo stageExecutionInfo = executionInfoArgumentCaptor.getValue();
    assertThat(stageExecutionInfo).isNotNull();
    assertThat(stageExecutionInfo.getEnvIdentifier()).isEqualTo(ENV_IDENTIFIER);
    assertThat(stageExecutionInfo.getInfraIdentifier()).isEqualTo(INFRA_IDENTIFIER);
    assertThat(stageExecutionInfo.getServiceIdentifier()).isEqualTo(SERVICE_IDENTIFIER);
    assertThat(stageExecutionInfo.getStageExecutionId()).isEqualTo(EXECUTION_ID);
    assertThat(stageExecutionInfo.getDeploymentIdentifier()).isEqualTo(DEPLOYMENT_IDENTIFIER);

    assertThat(stageExecutionInfo.getExecutionDetails()).isNotNull();
    ExecutionDetails executionDetails = stageExecutionInfo.getExecutionDetails();
    assertThat(executionDetails).isNotNull();
    assertThat(executionDetails).isInstanceOf(CustomDeploymentExecutionDetails.class);
    CustomDeploymentExecutionDetails customDeploymentExecutionDetails =
        (CustomDeploymentExecutionDetails) executionDetails;

    assertThat(customDeploymentExecutionDetails.getArtifactsOutcome().size()).isEqualTo(1);

    assertThat(customDeploymentExecutionDetails.getArtifactsOutcome().get(0))
        .isInstanceOf(ArtifactoryArtifactOutcome.class);
    ArtifactoryArtifactOutcome saveExecutionArtifact =
        (ArtifactoryArtifactOutcome) customDeploymentExecutionDetails.getArtifactsOutcome().get(0);
    assertThat(saveExecutionArtifact.getConnectorRef()).isEqualTo(connectorRef);
    assertThat(saveExecutionArtifact.getRepositoryName()).isEqualTo(repositoryName);
    assertThat(saveExecutionArtifact.getArtifactPath()).isEqualTo(artifactPath);
    assertThat(saveExecutionArtifact.isPrimaryArtifact()).isEqualTo(true);
  }

  @Test
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void testSaveStageExecutionInfoForECSInfraKindNoArtifact() {
    Ambiance ambiance = Ambiance.newBuilder().build();

    stageExecutionHelper.saveStageExecutionInfo(ambiance,
        ExecutionInfoKey.builder()
            .scope(Scope.of(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER))
            .envIdentifier(ENV_IDENTIFIER)
            .infraIdentifier(INFRA_IDENTIFIER)
            .serviceIdentifier(SERVICE_IDENTIFIER)
            .build(),
        InfrastructureKind.KUBERNETES_GCP);

    ArgumentCaptor<StageExecutionInfo> stageExecutionInfoArgumentCaptor =
        ArgumentCaptor.forClass(StageExecutionInfo.class);
    verify(stageExecutionInfoService, times(0)).save(stageExecutionInfoArgumentCaptor.capture());
  }

  @Test
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void testSaveStageExecutionInfoForECSInfraKind() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    when(cdStepHelper.resolveArtifactsOutcome(ambiance)).thenReturn(Optional.of(mock(ArtifactOutcome.class)));
    stageExecutionHelper.saveStageExecutionInfo(ambiance,
        ExecutionInfoKey.builder()
            .scope(Scope.of(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER))
            .envIdentifier(ENV_IDENTIFIER)
            .infraIdentifier(INFRA_IDENTIFIER)
            .serviceIdentifier(SERVICE_IDENTIFIER)
            .build(),
        InfrastructureKind.KUBERNETES_GCP);

    ArgumentCaptor<StageExecutionInfo> stageExecutionInfoArgumentCaptor =
        ArgumentCaptor.forClass(StageExecutionInfo.class);
    verify(stageExecutionInfoService, times(1)).save(stageExecutionInfoArgumentCaptor.capture());

    StageExecutionInfo value = stageExecutionInfoArgumentCaptor.getValue();
    assertThat(value.getExecutionDetails()).isInstanceOf(DefaultExecutionDetails.class);
  }

  @Test
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void testRollbackArtifactAzureWebApp() {
    StepResponseBuilder stepResponseBuilder = StepResponse.builder();
    stepResponseBuilder.status(Status.SUCCEEDED);

    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions(SetupAbstractionKeys.accountId, ACCOUNT_IDENTIFIER)
                            .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, ORG_IDENTIFIER)
                            .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, PROJECT_IDENTIFIER)
                            .setStageExecutionId(EXECUTION_ID)
                            .build();

    Scope scope = Scope.of(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);

    ExecutionInfoKey executionInfoKey = ExecutionInfoKey.builder()
                                            .scope(scope)
                                            .envIdentifier(ENV_IDENTIFIER)
                                            .infraIdentifier(INFRA_IDENTIFIER)
                                            .serviceIdentifier(SERVICE_IDENTIFIER)
                                            .build();

    doReturn(Optional.empty())
        .when(stageExecutionInfoService)
        .getLatestSuccessfulStageExecutionInfo(eq(executionInfoKey), eq(EXECUTION_ID));

    stageExecutionHelper.addRollbackArtifactToStageOutcomeIfPresent(
        ambiance, stepResponseBuilder, executionInfoKey, InfrastructureKind.AZURE_WEB_APP);
    List<ArtifactOutcome> result =
        stageExecutionHelper.getRollbackArtifacts(ambiance, executionInfoKey, InfrastructureKind.AZURE_WEB_APP);
    assertThat(result.size()).isEqualTo(0);
  }
}
