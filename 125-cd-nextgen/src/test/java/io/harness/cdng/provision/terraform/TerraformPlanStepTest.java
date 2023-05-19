/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.terraform;

import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.AKHIL_PANDEY;
import static io.harness.rule.OwnerRule.NAMAN_TALAYCHA;
import static io.harness.rule.OwnerRule.NGONZALEZ;
import static io.harness.rule.OwnerRule.VLICA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EnvironmentType;
import io.harness.category.element.UnitTests;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.manifest.yaml.TerraformCommandFlagType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType;
import io.harness.cdng.provision.terraform.functor.TerraformHumanReadablePlanFunctor;
import io.harness.cdng.provision.terraform.functor.TerraformPlanJsonFunctor;
import io.harness.cdng.provision.terraform.outcome.TerraformPlanOutcome;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthType;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthenticationDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryUsernamePasswordAuthDTO;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.storeconfig.ArtifactoryStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.task.git.GitFetchFilesConfig;
import io.harness.delegate.task.terraform.TFTaskType;
import io.harness.delegate.task.terraform.TerraformTaskNGParameters;
import io.harness.delegate.task.terraform.TerraformTaskNGResponse;
import io.harness.delegate.task.terraform.TerraformVarFileInfo;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.UnitProgress;
import io.harness.ng.core.EntityDetail;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepHelper;
import io.harness.steps.TaskRequestsUtils;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.MockitoRule;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;

@RunWith(MockitoJUnitRunner.class)
@PrepareForTest({TaskRequestsUtils.class})
@OwnedBy(HarnessTeam.CDP)
public class TerraformPlanStepTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock private KryoSerializer kryoSerializer;
  @Mock private CDFeatureFlagHelper cdFeatureFlagHelper;
  @Mock private TerraformStepHelper terraformStepHelper;
  @Mock private TerraformConfigHelper terraformConfigHelper;
  @Mock private PipelineRbacHelper pipelineRbacHelper;
  @Mock private StepHelper stepHelper;
  @InjectMocks private TerraformPlanStep terraformPlanStep;

  private Ambiance getAmbiance() {
    return Ambiance.newBuilder()
        .putSetupAbstractions("accountId", "test-account")
        .putSetupAbstractions("projectIdentifier", "test-project")
        .putSetupAbstractions("orgIdentifier", "test-org")
        .build();
  }

  @Captor ArgumentCaptor<List<EntityDetail>> captor;
  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testValidateResourcesWithGithubStore() {
    Ambiance ambiance = getAmbiance();
    TerraformStepDataGenerator.GitStoreConfig gitStoreConfigFiles =
        TerraformStepDataGenerator.GitStoreConfig.builder()
            .branch("master")
            .fetchType(FetchType.BRANCH)
            .folderPath(ParameterField.createValueField("Config/"))
            .connectoref(ParameterField.createValueField("terraform"))
            .build();
    TerraformStepDataGenerator.GitStoreConfig gitStoreVarFiles =
        TerraformStepDataGenerator.GitStoreConfig.builder()
            .branch("master")
            .fetchType(FetchType.BRANCH)
            .folderPath(ParameterField.createValueField("VarFiles/"))
            .connectoref(ParameterField.createValueField("terraform"))
            .build();
    TerraformPlanStepParameters planStepParameters =
        TerraformStepDataGenerator.generateStepPlanFile(StoreConfigType.GITHUB, gitStoreConfigFiles, gitStoreVarFiles);
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(planStepParameters).build();
    terraformPlanStep.validateResources(ambiance, stepElementParameters);
    verify(pipelineRbacHelper, times(1)).checkRuntimePermissions(eq(ambiance), captor.capture(), eq(true));

    List<EntityDetail> entityDetails = captor.getValue();
    assertThat(entityDetails.size()).isEqualTo(3);
    assertThat(entityDetails.get(0).getEntityRef().getIdentifier()).isEqualTo("terraform");
    assertThat(entityDetails.get(0).getEntityRef().getAccountIdentifier()).isEqualTo("test-account");
    assertThat(entityDetails.get(1).getEntityRef().getIdentifier()).isEqualTo("terraform");
    assertThat(entityDetails.get(1).getEntityRef().getAccountIdentifier()).isEqualTo("test-account");
    assertThat(entityDetails.get(2).getEntityRef().getIdentifier()).isEqualTo("secret");
    assertThat(entityDetails.get(2).getEntityRef().getAccountIdentifier()).isEqualTo("test-account");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testValidateResourcesWithArtifactoryStore() {
    Ambiance ambiance = getAmbiance();
    TerraformStepDataGenerator.ArtifactoryStoreConfig artifactoryStoreConfigFiles =
        TerraformStepDataGenerator.ArtifactoryStoreConfig.builder()
            .connectorRef("connectorRef")
            .repositoryName("repositoryPath")
            .build();
    TerraformStepDataGenerator.ArtifactoryStoreConfig artifactoryStoreVarFiles =
        TerraformStepDataGenerator.ArtifactoryStoreConfig.builder()
            .connectorRef("connectorRef2")
            .repositoryName("repositoryPathtoVars")
            .build();
    TerraformPlanStepParameters planStepParameters = TerraformStepDataGenerator.generateStepPlanFile(
        StoreConfigType.ARTIFACTORY, artifactoryStoreConfigFiles, artifactoryStoreVarFiles);
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(planStepParameters).build();
    terraformPlanStep.validateResources(ambiance, stepElementParameters);
    verify(pipelineRbacHelper, times(1)).checkRuntimePermissions(eq(ambiance), captor.capture(), eq(true));

    List<EntityDetail> entityDetails = captor.getValue();
    assertThat(entityDetails.size()).isEqualTo(3);
    assertThat(entityDetails.get(0).getEntityRef().getIdentifier()).isEqualTo("connectorRef");
    assertThat(entityDetails.get(0).getEntityRef().getAccountIdentifier()).isEqualTo("test-account");
    assertThat(entityDetails.get(1).getEntityRef().getIdentifier()).isEqualTo("connectorRef2");
    assertThat(entityDetails.get(1).getEntityRef().getAccountIdentifier()).isEqualTo("test-account");
    assertThat(entityDetails.get(2).getEntityRef().getIdentifier()).isEqualTo("secret");
    assertThat(entityDetails.get(2).getEntityRef().getAccountIdentifier()).isEqualTo("test-account");
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testObtainTaskAfterRbacWithGithubStore() {
    Ambiance ambiance = getAmbiance();
    TerraformStepDataGenerator.GitStoreConfig gitStoreConfigFiles =
        TerraformStepDataGenerator.GitStoreConfig.builder()
            .branch("master")
            .fetchType(FetchType.BRANCH)
            .folderPath(ParameterField.createValueField("Config/"))
            .connectoref(ParameterField.createValueField("terraform"))
            .build();
    TerraformStepDataGenerator.GitStoreConfig gitStoreVarFiles =
        TerraformStepDataGenerator.GitStoreConfig.builder()
            .branch("master")
            .fetchType(FetchType.BRANCH)
            .folderPath(ParameterField.createValueField("VarFiles/"))
            .connectoref(ParameterField.createValueField("terraform"))
            .build();
    TerraformPlanStepParameters planStepParameters =
        TerraformStepDataGenerator.generateStepPlanFile(StoreConfigType.GITHUB, gitStoreConfigFiles, gitStoreVarFiles);

    planStepParameters.getConfiguration().skipTerraformRefresh.setValue(true);
    planStepParameters.getConfiguration().setCliOptionFlags(
        List.of(TerraformCliOptionFlag.builder()
                    .commandType(TerraformCommandFlagType.APPLY)
                    .flag(ParameterField.createValueField("-lock-timeout=0s"))
                    .build()));

    GitConfigDTO gitConfigDTO = GitConfigDTO.builder()
                                    .gitAuthType(GitAuthType.HTTP)
                                    .gitConnectionType(GitConnectionType.ACCOUNT)
                                    .delegateSelectors(Collections.singleton("delegateName"))
                                    .url("https://github.com/wings-software")
                                    .branchName("master")
                                    .build();
    GitStoreDelegateConfig gitStoreDelegateConfig =
        GitStoreDelegateConfig.builder().branch("master").connectorName("terraform").gitConfigDTO(gitConfigDTO).build();
    GitFetchFilesConfig gitFetchFilesConfig = GitFetchFilesConfig.builder()
                                                  .identifier("terraform")
                                                  .gitStoreDelegateConfig(gitStoreDelegateConfig)
                                                  .succeedIfFileNotFound(false)
                                                  .build();

    List<TerraformVarFileInfo> varFileInfo = new ArrayList<>();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(planStepParameters).build();
    StepInputPackage stepInputPackage = StepInputPackage.builder().build();
    doReturn("test-account/test-org/test-project/id").when(terraformStepHelper).generateFullIdentifier(any(), any());
    doReturn("fileId").when(terraformStepHelper).getLatestFileId(any());
    doReturn("planName").when(terraformStepHelper).getTerraformPlanName(any(), any(), any());
    doReturn(gitFetchFilesConfig).when(terraformStepHelper).getGitFetchFilesConfig(any(), any(), any());
    doReturn(varFileInfo).when(terraformStepHelper).toTerraformVarFileInfo(any(), any());
    doReturn(EnvironmentType.NON_PROD).when(stepHelper).getEnvironmentType(any());
    doReturn("back-content").when(terraformStepHelper).getBackendConfig(any());
    doReturn(ImmutableMap.of("KEY", ParameterField.createValueField("VAL")))
        .when(terraformStepHelper)
        .getEnvironmentVariablesMap(any());
    doReturn(true).when(cdFeatureFlagHelper).isEnabled(any(), any());
    doReturn(new HashMap<String, String>() {
      { put("APPLY", "-lock-timeout=0s"); }
    })
        .when(terraformStepHelper)
        .getTerraformCliFlags(any());
    Mockito.mockStatic(TaskRequestsUtils.class);
    PowerMockito.when(TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());
    ArgumentCaptor<TaskData> taskDataArgumentCaptor = ArgumentCaptor.forClass(TaskData.class);
    TaskRequest taskRequest = terraformPlanStep.obtainTaskAfterRbac(ambiance, stepElementParameters, stepInputPackage);
    assertThat(taskRequest).isNotNull();
    PowerMockito.verifyStatic(TaskRequestsUtils.class, times(1));
    TaskRequestsUtils.prepareCDTaskRequest(any(), taskDataArgumentCaptor.capture(), any(), any(), any(), any(), any());
    assertThat(taskDataArgumentCaptor.getValue()).isNotNull();
    assertThat(taskDataArgumentCaptor.getValue().getParameters()).isNotNull();
    TerraformTaskNGParameters taskParameters =
        (TerraformTaskNGParameters) taskDataArgumentCaptor.getValue().getParameters()[0];
    assertThat(taskParameters.getTaskType()).isEqualTo(TFTaskType.PLAN);
    assertThat(taskParameters.getPlanName()).isEqualTo("planName");
    assertThat(taskParameters.isSkipTerraformRefresh()).isTrue();
    assertThat(taskParameters.getTerraformCommandFlags().get("APPLY")).isEqualTo("-lock-timeout=0s");
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testObtainTaskAfterRbacWithGithubStoreWhenTFCloudCli() {
    Ambiance ambiance = getAmbiance();
    TerraformStepDataGenerator.GitStoreConfig gitStoreConfigFiles =
        TerraformStepDataGenerator.GitStoreConfig.builder()
            .branch("master")
            .fetchType(FetchType.BRANCH)
            .folderPath(ParameterField.createValueField("Config/"))
            .connectoref(ParameterField.createValueField("terraform"))
            .build();
    TerraformStepDataGenerator.GitStoreConfig gitStoreVarFiles =
        TerraformStepDataGenerator.GitStoreConfig.builder()
            .branch("master")
            .fetchType(FetchType.BRANCH)
            .folderPath(ParameterField.createValueField("VarFiles/"))
            .connectoref(ParameterField.createValueField("terraform"))
            .build();
    TerraformPlanStepParameters planStepParameters =
        TerraformStepDataGenerator.generateStepPlanFile(StoreConfigType.GITHUB, gitStoreConfigFiles, gitStoreVarFiles);
    planStepParameters.getConfiguration().setCliOptionFlags(
        List.of(TerraformCliOptionFlag.builder()
                    .commandType(TerraformCommandFlagType.APPLY)
                    .flag(ParameterField.createValueField("-lock-timeout=0s"))
                    .build()));

    planStepParameters.configuration.isTerraformCloudCli.setValue(true);

    GitConfigDTO gitConfigDTO = GitConfigDTO.builder()
                                    .gitAuthType(GitAuthType.HTTP)
                                    .gitConnectionType(GitConnectionType.ACCOUNT)
                                    .delegateSelectors(Collections.singleton("delegateName"))
                                    .url("https://github.com/wings-software")
                                    .branchName("master")
                                    .build();
    GitStoreDelegateConfig gitStoreDelegateConfig =
        GitStoreDelegateConfig.builder().branch("master").connectorName("terraform").gitConfigDTO(gitConfigDTO).build();
    GitFetchFilesConfig gitFetchFilesConfig = GitFetchFilesConfig.builder()
                                                  .identifier("terraform")
                                                  .gitStoreDelegateConfig(gitStoreDelegateConfig)
                                                  .succeedIfFileNotFound(false)
                                                  .build();

    List<TerraformVarFileInfo> varFileInfo = new ArrayList<>();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(planStepParameters).build();
    StepInputPackage stepInputPackage = StepInputPackage.builder().build();
    doReturn("test-account/test-org/test-project/id").when(terraformStepHelper).generateFullIdentifier(any(), any());
    doReturn("fileId").when(terraformStepHelper).getLatestFileId(any());
    doReturn("planName").when(terraformStepHelper).getTerraformPlanName(any(), any(), any());
    doReturn(gitFetchFilesConfig).when(terraformStepHelper).getGitFetchFilesConfig(any(), any(), any());
    doReturn(varFileInfo).when(terraformStepHelper).toTerraformVarFileInfo(any(), any());
    doReturn(EnvironmentType.NON_PROD).when(stepHelper).getEnvironmentType(any());
    doReturn("back-content").when(terraformStepHelper).getBackendConfig(any());
    doReturn(ImmutableMap.of("KEY", ParameterField.createValueField("VAL")))
        .when(terraformStepHelper)
        .getEnvironmentVariablesMap(any());
    doReturn(true).when(cdFeatureFlagHelper).isEnabled(any(), any());
    doReturn(new HashMap<String, String>() {
      { put("APPLY", "-lock-timeout=0s"); }
    })
        .when(terraformStepHelper)
        .getTerraformCliFlags(any());
    Mockito.mockStatic(TaskRequestsUtils.class);
    PowerMockito.when(TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());
    ArgumentCaptor<TaskData> taskDataArgumentCaptor = ArgumentCaptor.forClass(TaskData.class);
    TaskRequest taskRequest = terraformPlanStep.obtainTaskAfterRbac(ambiance, stepElementParameters, stepInputPackage);
    assertThat(taskRequest).isNotNull();
    PowerMockito.verifyStatic(TaskRequestsUtils.class, times(1));
    TaskRequestsUtils.prepareCDTaskRequest(any(), taskDataArgumentCaptor.capture(), any(), any(), any(), any(), any());
    assertThat(taskDataArgumentCaptor.getValue()).isNotNull();
    assertThat(taskDataArgumentCaptor.getValue().getParameters()).isNotNull();
    TerraformTaskNGParameters taskParameters =
        (TerraformTaskNGParameters) taskDataArgumentCaptor.getValue().getParameters()[0];
    assertThat(taskParameters.isSaveTerraformStateJson()).isFalse();
    assertThat(taskParameters.isSaveTerraformHumanReadablePlan()).isFalse();
    assertThat(taskParameters.getEncryptionConfig()).isNull();
    assertThat(taskParameters.getWorkspace()).isNull();
    assertThat(taskParameters.isTerraformCloudCli()).isTrue();
    assertThat(taskParameters.isSkipTerraformRefresh()).isFalse();
    assertThat(taskParameters.getTerraformCommandFlags().get("APPLY")).isEqualTo("-lock-timeout=0s");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testObtainTaskAfterRbacWithArtifactoryStore() {
    Ambiance ambiance = getAmbiance();
    TerraformStepDataGenerator.ArtifactoryStoreConfig artifactoryStoreConfigFiles =
        TerraformStepDataGenerator.ArtifactoryStoreConfig.builder()
            .connectorRef("connectorRef")
            .repositoryName("repositoryPath")
            .build();
    TerraformStepDataGenerator.ArtifactoryStoreConfig artifactoryStoreVarFiles =
        TerraformStepDataGenerator.ArtifactoryStoreConfig.builder()
            .connectorRef("connectorRef2")
            .repositoryName("repositoryPathtoVars")
            .build();
    TerraformPlanStepParameters planStepParameters = TerraformStepDataGenerator.generateStepPlanFile(
        StoreConfigType.ARTIFACTORY, artifactoryStoreConfigFiles, artifactoryStoreVarFiles);

    // Create auth with user and password
    char[] password = {'r', 's', 't', 'u', 'v'};
    ArtifactoryAuthenticationDTO artifactoryAuthenticationDTO =
        ArtifactoryAuthenticationDTO.builder()
            .authType(ArtifactoryAuthType.USER_PASSWORD)
            .credentials(ArtifactoryUsernamePasswordAuthDTO.builder()
                             .username("username")
                             .passwordRef(SecretRefData.builder().decryptedValue(password).build())
                             .build())
            .build();

    // Create DTO connector
    ArtifactoryConnectorDTO artifactoryConnectorDTO = ArtifactoryConnectorDTO.builder()
                                                          .artifactoryServerUrl("http://artifactory.com")
                                                          .auth(artifactoryAuthenticationDTO)
                                                          .delegateSelectors(Collections.singleton("delegateSelector"))
                                                          .build();
    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder()
                                            .connectorType(ConnectorType.ARTIFACTORY)
                                            .identifier("connectorRef")
                                            .name("connectorName")
                                            .connectorConfig(artifactoryConnectorDTO)
                                            .build();

    ArtifactoryStoreDelegateConfig artifactoryStoreDelegateConfig = ArtifactoryStoreDelegateConfig.builder()
                                                                        .repositoryName("repositoryPath")
                                                                        .connectorDTO(connectorInfoDTO)
                                                                        .succeedIfFileNotFound(false)
                                                                        .build();

    List<TerraformVarFileInfo> varFileInfo = new ArrayList<>();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(planStepParameters).build();
    StepInputPackage stepInputPackage = StepInputPackage.builder().build();
    doReturn("test-account/test-org/test-project/id").when(terraformStepHelper).generateFullIdentifier(any(), any());
    doReturn("fileId").when(terraformStepHelper).getLatestFileId(any());
    doReturn("planName").when(terraformStepHelper).getTerraformPlanName(any(), any(), any());
    doReturn(artifactoryStoreDelegateConfig)
        .when(terraformStepHelper)
        .getFileStoreFetchFilesConfig(any(), any(), any());
    doReturn(varFileInfo).when(terraformStepHelper).toTerraformVarFileInfo(any(), any());
    doReturn(EnvironmentType.NON_PROD).when(stepHelper).getEnvironmentType(any());
    doReturn("back-content").when(terraformStepHelper).getBackendConfig(any());
    doReturn(ImmutableMap.of("KEY", ParameterField.createValueField("VAL")))
        .when(terraformStepHelper)
        .getEnvironmentVariablesMap(any());
    Mockito.mockStatic(TaskRequestsUtils.class);
    PowerMockito.when(TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());
    ArgumentCaptor<TaskData> taskDataArgumentCaptor = ArgumentCaptor.forClass(TaskData.class);
    TaskRequest taskRequest = terraformPlanStep.obtainTaskAfterRbac(ambiance, stepElementParameters, stepInputPackage);
    assertThat(taskRequest).isNotNull();
    PowerMockito.verifyStatic(TaskRequestsUtils.class, times(1));
    TaskRequestsUtils.prepareCDTaskRequest(any(), taskDataArgumentCaptor.capture(), any(), any(), any(), any(), any());
    assertThat(taskDataArgumentCaptor.getValue()).isNotNull();
    assertThat(taskDataArgumentCaptor.getValue().getParameters()).isNotNull();
    TerraformTaskNGParameters taskParameters =
        (TerraformTaskNGParameters) taskDataArgumentCaptor.getValue().getParameters()[0];
    assertThat(taskParameters.getTaskType()).isEqualTo(TFTaskType.PLAN);
    assertThat(taskParameters.getPlanName()).isEqualTo("planName");
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testGetStepParametersClass() {
    assertThat(terraformPlanStep.getStepParametersClass()).isEqualTo(StepElementParameters.class);
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void handleTaskResultWithSecurityContext() throws Exception {
    Ambiance ambiance = getAmbiance();
    TerraformPlanStepParameters planStepParameters =
        TerraformPlanStepParameters.infoBuilder()
            .provisionerIdentifier(ParameterField.createValueField("id"))
            .configuration(TerraformPlanExecutionDataParameters.builder()
                               .isTerraformCloudCli(ParameterField.createValueField(false))
                               .command(TerraformPlanCommand.APPLY)
                               .build())
            .build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(planStepParameters).build();
    doReturn("test-account/test-org/test-project/Id").when(terraformStepHelper).generateFullIdentifier(any(), any());
    List<UnitProgress> unitProgresses = Collections.singletonList(UnitProgress.newBuilder().build());
    UnitProgressData unitProgressData = UnitProgressData.builder().unitProgresses(unitProgresses).build();
    TerraformTaskNGResponse terraformTaskNGResponse = TerraformTaskNGResponse.builder()
                                                          .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                          .unitProgressData(unitProgressData)
                                                          .detailedExitCode(2)
                                                          .build();
    StepResponse stepResponse = terraformPlanStep.handleTaskResultWithSecurityContext(
        ambiance, stepElementParameters, () -> terraformTaskNGResponse);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getStepOutcomes()).isNotNull();
    assertThat(stepResponse.getStepOutcomes()).hasSize(1);
    StepResponse.StepOutcome stepOutcome = ((List<StepResponse.StepOutcome>) stepResponse.getStepOutcomes()).get(0);
    assertThat(stepOutcome.getOutcome()).isInstanceOf(TerraformPlanOutcome.class);
    assertThat(((TerraformPlanOutcome) (stepOutcome.getOutcome())).getDetailedExitCode()).isEqualTo(2);

    verify(terraformStepHelper, times(1)).saveTerraformInheritOutput(any(), any(), any());
    verify(terraformStepHelper, times(1)).updateParentEntityIdAndVersion(any(), any());
  }

  @Test // Different Status
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void handleTaskResultWithSecurityContextDifferentStatus() throws Exception {
    Ambiance ambiance = getAmbiance();
    TerraformPlanStepParameters planStepParameters =
        TerraformPlanStepParameters.infoBuilder()
            .provisionerIdentifier(ParameterField.createValueField("id"))
            .configuration(TerraformPlanExecutionDataParameters.builder().command(TerraformPlanCommand.APPLY).build())
            .build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(planStepParameters).build();
    doReturn("test-account/test-org/test-project/Id").when(terraformStepHelper).generateFullIdentifier(any(), any());
    List<UnitProgress> unitProgresses = Collections.singletonList(UnitProgress.newBuilder().build());
    UnitProgressData unitProgressData = UnitProgressData.builder().unitProgresses(unitProgresses).build();
    TerraformTaskNGResponse terraformTaskNGResponseFailure = TerraformTaskNGResponse.builder()
                                                                 .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                                                                 .unitProgressData(unitProgressData)
                                                                 .build();
    StepResponse stepResponse = terraformPlanStep.handleTaskResultWithSecurityContext(
        ambiance, stepElementParameters, () -> terraformTaskNGResponseFailure);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
    assertThat(stepResponse.getStepOutcomes()).isNotNull();

    TerraformTaskNGResponse terraformTaskNGResponseRunning = TerraformTaskNGResponse.builder()
                                                                 .commandExecutionStatus(CommandExecutionStatus.RUNNING)
                                                                 .unitProgressData(unitProgressData)
                                                                 .build();
    stepResponse = terraformPlanStep.handleTaskResultWithSecurityContext(
        ambiance, stepElementParameters, () -> terraformTaskNGResponseRunning);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.RUNNING);
    assertThat(stepResponse.getStepOutcomes()).isNotNull();

    TerraformTaskNGResponse terraformTaskNGResponseQueued = TerraformTaskNGResponse.builder()
                                                                .commandExecutionStatus(CommandExecutionStatus.QUEUED)
                                                                .unitProgressData(unitProgressData)
                                                                .build();
    stepResponse = terraformPlanStep.handleTaskResultWithSecurityContext(
        ambiance, stepElementParameters, () -> terraformTaskNGResponseQueued);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.QUEUED);
    assertThat(stepResponse.getStepOutcomes()).isNotNull();
    String message =
        String.format("Unhandled type CommandExecutionStatus: " + CommandExecutionStatus.SKIPPED, WingsException.USER);
    try {
      TerraformTaskNGResponse terraformTaskNGResponseSkipped =
          TerraformTaskNGResponse.builder()
              .commandExecutionStatus(CommandExecutionStatus.SKIPPED)
              .unitProgressData(unitProgressData)
              .build();
      terraformPlanStep.handleTaskResultWithSecurityContext(
          ambiance, stepElementParameters, () -> terraformTaskNGResponseSkipped);
    } catch (InvalidRequestException invalidRequestException) {
      assertThat(invalidRequestException.getMessage()).isEqualTo(message);
    }
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testHandleTaskResultWithTfPlanJsonFileId() throws Exception {
    Ambiance ambiance = getAmbiance();
    StepElementParameters stepElementParameters =
        StepElementParameters.builder()
            .spec(TerraformPlanStepParameters.infoBuilder()
                      .stepFqn("step1")
                      .provisionerIdentifier(ParameterField.createValueField("provisioner1"))
                      .configuration(TerraformPlanExecutionDataParameters.builder()
                                         .isTerraformCloudCli(ParameterField.createValueField(false))
                                         .exportTerraformPlanJson(ParameterField.createValueField(true))
                                         .build())
                      .build())
            .build();

    TerraformTaskNGResponse ngResponse = TerraformTaskNGResponse.builder()
                                             .tfPlanJsonFileId("fileId")
                                             .stateFileId("fileStateId")
                                             .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                             .build();

    doReturn("outputPlanJson")
        .when(terraformStepHelper)
        .saveTerraformPlanJsonOutput(ambiance, ngResponse, "provisioner1");

    StepResponse stepResponse =
        terraformPlanStep.handleTaskResultWithSecurityContext(ambiance, stepElementParameters, () -> ngResponse);

    verify(terraformStepHelper).saveTerraformPlanJsonOutput(ambiance, ngResponse, "provisioner1");

    assertThat(stepResponse.getStepOutcomes()).hasSize(1);
    StepResponse.StepOutcome planOutcome = stepResponse.getStepOutcomes().iterator().next();
    assertThat(planOutcome.getName()).isEqualTo(TerraformPlanOutcome.OUTCOME_NAME);
    assertThat(planOutcome.getOutcome()).isInstanceOf(TerraformPlanOutcome.class);
    TerraformPlanOutcome terraformPlanOutcome = (TerraformPlanOutcome) planOutcome.getOutcome();
    assertThat(terraformPlanOutcome.getJsonFilePath())
        .isEqualTo(TerraformPlanJsonFunctor.getExpression("step1", "outputPlanJson"));
  }

  @Test
  @Owner(developers = AKHIL_PANDEY)
  @Category(UnitTests.class)
  public void testHandleTaskResultWithTfHumanReadableFileId() throws Exception {
    Ambiance ambiance = getAmbiance();
    StepElementParameters stepElementParameters =
        StepElementParameters.builder()
            .spec(TerraformPlanStepParameters.infoBuilder()
                      .stepFqn("step1")
                      .provisionerIdentifier(ParameterField.createValueField("provisioner1"))
                      .configuration(TerraformPlanExecutionDataParameters.builder()
                                         .exportTerraformHumanReadablePlan(ParameterField.createValueField(true))
                                         .isTerraformCloudCli(ParameterField.createValueField(false))
                                         .build())
                      .build())
            .build();

    TerraformTaskNGResponse ngResponse = TerraformTaskNGResponse.builder()
                                             .tfHumanReadablePlanFileId("fileId")
                                             .stateFileId("fileStateId")
                                             .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                             .build();

    doReturn("humanReadablePlan")
        .when(terraformStepHelper)
        .saveTerraformPlanHumanReadableOutput(ambiance, ngResponse, "provisioner1");

    StepResponse stepResponse =
        terraformPlanStep.handleTaskResultWithSecurityContext(ambiance, stepElementParameters, () -> ngResponse);

    verify(terraformStepHelper).saveTerraformPlanHumanReadableOutput(ambiance, ngResponse, "provisioner1");

    assertThat(stepResponse.getStepOutcomes()).hasSize(1);
    StepResponse.StepOutcome planOutcome = stepResponse.getStepOutcomes().iterator().next();
    assertThat(planOutcome.getName()).isEqualTo(TerraformPlanOutcome.OUTCOME_NAME);
    assertThat(planOutcome.getOutcome()).isInstanceOf(TerraformPlanOutcome.class);
    TerraformPlanOutcome terraformPlanOutcome = (TerraformPlanOutcome) planOutcome.getOutcome();
    assertThat(terraformPlanOutcome.getHumanReadableFilePath())
        .isEqualTo(TerraformHumanReadablePlanFunctor.getExpression("step1", "humanReadablePlan"));
  }
}
