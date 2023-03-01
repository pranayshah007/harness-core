/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.terraform;

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
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.connector.ConnectorType;
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

import java.util.Collections;
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
public class TerraformApplyStepTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private KryoSerializer kryoSerializer;
  @Mock private TerraformStepHelper terraformStepHelper;
  @Mock private TerraformConfigHelper terraformConfigHelper;
  @Mock private PipelineRbacHelper pipelineRbacHelper;
  @InjectMocks private TerraformApplyStep terraformApplyStep;
  @Mock private StepHelper stepHelper;
  @Mock private CDFeatureFlagHelper cdFeatureFlagHelper;

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

    TerraformApplyStepParameters applyStepParameters =
        TerraformStepDataGenerator.generateApplyStepPlan(StoreConfigType.GITHUB, gitStoreConfigFiles, gitStoreVarFiles);
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(applyStepParameters).build();
    terraformApplyStep.validateResources(ambiance, stepElementParameters);
    verify(pipelineRbacHelper, times(1)).checkRuntimePermissions(eq(ambiance), captor.capture(), eq(true));

    assertThat("true").isEqualTo("true");
    List<EntityDetail> entityDetails = captor.getValue();
    assertThat(entityDetails.size()).isEqualTo(2);
    assertThat(entityDetails.get(0).getEntityRef().getIdentifier()).isEqualTo("terraform");
    assertThat(entityDetails.get(0).getEntityRef().getAccountIdentifier()).isEqualTo("test-account");
    assertThat(entityDetails.get(1).getEntityRef().getIdentifier()).isEqualTo("terraform");
    assertThat(entityDetails.get(1).getEntityRef().getAccountIdentifier()).isEqualTo("test-account");
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

    TerraformApplyStepParameters applyStepParameters = TerraformStepDataGenerator.generateApplyStepPlan(
        StoreConfigType.ARTIFACTORY, artifactoryStoreConfigFiles, artifactoryStoreVarFiles);
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(applyStepParameters).build();
    terraformApplyStep.validateResources(ambiance, stepElementParameters);
    verify(pipelineRbacHelper, times(1)).checkRuntimePermissions(eq(ambiance), captor.capture(), eq(true));

    assertThat("true").isEqualTo("true");
    List<EntityDetail> entityDetails = captor.getValue();
    assertThat(entityDetails.size()).isEqualTo(2);
    assertThat(entityDetails.get(0).getEntityRef().getIdentifier()).isEqualTo("connectorRef");
    assertThat(entityDetails.get(0).getEntityRef().getAccountIdentifier()).isEqualTo("test-account");
    assertThat(entityDetails.get(1).getEntityRef().getIdentifier()).isEqualTo("connectorRef2");
    assertThat(entityDetails.get(1).getEntityRef().getAccountIdentifier()).isEqualTo("test-account");
  }

  @Test(expected = NullPointerException.class)
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testValidateResourcesNegativeScenario() {
    Ambiance ambiance = getAmbiance();
    TerraformApplyStepParameters applyStepParameters =
        TerraformApplyStepParameters.infoBuilder()
            .provisionerIdentifier(ParameterField.createValueField("provId_$"))
            .configuration(TerraformStepConfigurationParameters.builder()
                               .type(TerraformStepConfigurationType.INLINE)
                               .spec(TerraformExecutionDataParameters.builder().build())
                               .build())
            .build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(applyStepParameters).build();
    terraformApplyStep.validateResources(ambiance, stepElementParameters);
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testObtainTaskAfterRbacWithGithub() {
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

    TerraformApplyStepParameters applyStepParameters =
        TerraformStepDataGenerator.generateApplyStepPlan(StoreConfigType.GITHUB, gitStoreConfigFiles, gitStoreVarFiles);

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
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(applyStepParameters).build();
    StepInputPackage stepInputPackage = StepInputPackage.builder().build();
    doReturn("test-account/test-org/test-project/Id").when(terraformStepHelper).generateFullIdentifier(any(), any());
    doReturn(gitFetchFilesConfig).when(terraformStepHelper).getGitFetchFilesConfig(any(), any(), any());
    doReturn(EnvironmentType.NON_PROD).when(stepHelper).getEnvironmentType(any());
    Mockito.mockStatic(TaskRequestsUtils.class);
    PowerMockito.when(TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());
    ArgumentCaptor<TaskData> taskDataArgumentCaptor = ArgumentCaptor.forClass(TaskData.class);
    TaskRequest taskRequest = terraformApplyStep.obtainTaskAfterRbac(ambiance, stepElementParameters, stepInputPackage);
    assertThat(taskRequest).isNotNull();
    PowerMockito.verifyStatic(TaskRequestsUtils.class, times(1));
    TaskRequestsUtils.prepareCDTaskRequest(any(), taskDataArgumentCaptor.capture(), any(), any(), any(), any(), any());
    assertThat(taskDataArgumentCaptor.getValue()).isNotNull();
    assertThat(taskDataArgumentCaptor.getValue().getParameters()).isNotNull();
    TerraformTaskNGParameters taskParameters =
        (TerraformTaskNGParameters) taskDataArgumentCaptor.getValue().getParameters()[0];
    assertThat(taskParameters.getTaskType()).isEqualTo(TFTaskType.APPLY);
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testObtainTaskAfterRbacWithGithubWhenTFCloudCli() {
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

    TerraformApplyStepParameters applyStepParameters =
        TerraformStepDataGenerator.generateApplyStepPlan(StoreConfigType.GITHUB, gitStoreConfigFiles, gitStoreVarFiles);
    applyStepParameters.getConfiguration().getSpec().getIsTerraformCloudCli().setValue(true);

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
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(applyStepParameters).build();
    StepInputPackage stepInputPackage = StepInputPackage.builder().build();
    doReturn("test-account/test-org/test-project/Id").when(terraformStepHelper).generateFullIdentifier(any(), any());
    doReturn(gitFetchFilesConfig).when(terraformStepHelper).getGitFetchFilesConfig(any(), any(), any());
    doReturn(EnvironmentType.NON_PROD).when(stepHelper).getEnvironmentType(any());
    Mockito.mockStatic(TaskRequestsUtils.class);
    PowerMockito.when(TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());
    ArgumentCaptor<TaskData> taskDataArgumentCaptor = ArgumentCaptor.forClass(TaskData.class);
    TaskRequest taskRequest = terraformApplyStep.obtainTaskAfterRbac(ambiance, stepElementParameters, stepInputPackage);
    assertThat(taskRequest).isNotNull();
    PowerMockito.verifyStatic(TaskRequestsUtils.class, times(1));
    TaskRequestsUtils.prepareCDTaskRequest(any(), taskDataArgumentCaptor.capture(), any(), any(), any(), any(), any());
    assertThat(taskDataArgumentCaptor.getValue()).isNotNull();
    assertThat(taskDataArgumentCaptor.getValue().getParameters()).isNotNull();
    TerraformTaskNGParameters taskParameters =
        (TerraformTaskNGParameters) taskDataArgumentCaptor.getValue().getParameters()[0];
    assertThat(taskParameters.getTaskType()).isEqualTo(TFTaskType.APPLY);
    assertThat(taskParameters.getEncryptionConfig()).isNull();
    assertThat(taskParameters.getWorkspace()).isNull();
    assertThat(taskParameters.isTerraformCloudCli()).isTrue();
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testObtainTaskAfterRbacWithArtifactory() {
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

    TerraformApplyStepParameters applyStepParameters = TerraformStepDataGenerator.generateApplyStepPlan(
        StoreConfigType.ARTIFACTORY, artifactoryStoreConfigFiles, artifactoryStoreVarFiles);

    ArtifactoryStoreDelegateConfig artifactoryStoreDelegateConfig =
        TerraformStepDataGenerator.createStoreDelegateConfig();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(applyStepParameters).build();
    StepInputPackage stepInputPackage = StepInputPackage.builder().build();
    doReturn("test-account/test-org/test-project/Id").when(terraformStepHelper).generateFullIdentifier(any(), any());
    doReturn(artifactoryStoreDelegateConfig)
        .when(terraformStepHelper)
        .getFileStoreFetchFilesConfig(any(), any(), any());
    doReturn(EnvironmentType.NON_PROD).when(stepHelper).getEnvironmentType(any());
    Mockito.mockStatic(TaskRequestsUtils.class);
    PowerMockito.when(TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());
    ArgumentCaptor<TaskData> taskDataArgumentCaptor = ArgumentCaptor.forClass(TaskData.class);
    TaskRequest taskRequest = terraformApplyStep.obtainTaskAfterRbac(ambiance, stepElementParameters, stepInputPackage);
    assertThat(taskRequest).isNotNull();
    PowerMockito.verifyStatic(TaskRequestsUtils.class, times(1));
    TaskRequestsUtils.prepareCDTaskRequest(any(), taskDataArgumentCaptor.capture(), any(), any(), any(), any(), any());
    assertThat(taskDataArgumentCaptor.getValue()).isNotNull();
    assertThat(taskDataArgumentCaptor.getValue().getParameters()).isNotNull();
    TerraformTaskNGParameters taskParameters =
        (TerraformTaskNGParameters) taskDataArgumentCaptor.getValue().getParameters()[0];
    assertThat(taskParameters.getTaskType()).isEqualTo(TFTaskType.APPLY);
    assertThat(taskParameters.getFileStoreConfigFiles().getConnectorDTO().getConnectorType().toString())
        .isEqualTo(ConnectorType.ARTIFACTORY.toString());
  }
  @Test(expected = NullPointerException.class) // configFile is Absent
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testObtainTaskAfterRbacNegtiveScenario() {
    Ambiance ambiance = getAmbiance();
    TerraformApplyStepParameters applyStepParameters =
        TerraformApplyStepParameters.infoBuilder()
            .provisionerIdentifier(ParameterField.createValueField("Id"))
            .configuration(TerraformStepConfigurationParameters.builder()
                               .type(TerraformStepConfigurationType.INLINE)
                               .spec(TerraformExecutionDataParameters.builder().build())
                               .build())
            .build();
    GitFetchFilesConfig gitFetchFilesConfig =
        GitFetchFilesConfig.builder().identifier("terraform").succeedIfFileNotFound(false).build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(applyStepParameters).build();
    StepInputPackage stepInputPackage = StepInputPackage.builder().build();
    doReturn("test-account/test-org/test-project/Id").when(terraformStepHelper).generateFullIdentifier(any(), any());
    doReturn(gitFetchFilesConfig).when(terraformStepHelper).getGitFetchFilesConfig(any(), any(), any());
    doReturn(EnvironmentType.NON_PROD).when(stepHelper).getEnvironmentType(any());
    Mockito.mockStatic(TaskRequestsUtils.class);
    PowerMockito.when(TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());
    terraformApplyStep.obtainTaskAfterRbac(ambiance, stepElementParameters, stepInputPackage);
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testObtainTaskAfterRbacInheritPlan() {
    Ambiance ambiance = getAmbiance();
    TerraformApplyStepParameters applyStepParameters =
        TerraformApplyStepParameters.infoBuilder()
            .provisionerIdentifier(ParameterField.createValueField("Id"))
            .configuration(TerraformStepConfigurationParameters.builder()
                               .type(TerraformStepConfigurationType.INHERIT_FROM_PLAN)
                               .build())
            .build();
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
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(applyStepParameters).build();
    StepInputPackage stepInputPackage = StepInputPackage.builder().build();
    doReturn("test-account/test-org/test-project/Id").when(terraformStepHelper).generateFullIdentifier(any(), any());
    doReturn(gitFetchFilesConfig).when(terraformStepHelper).getGitFetchFilesConfig(any(), any(), any());
    doReturn(EnvironmentType.NON_PROD).when(stepHelper).getEnvironmentType(any());

    Mockito.mockStatic(TaskRequestsUtils.class);
    PowerMockito.when(TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());
    ArgumentCaptor<TaskData> taskDataArgumentCaptor = ArgumentCaptor.forClass(TaskData.class);

    TerraformBackendConfigFileConfig backendConfigFileConfig =
        TerraformInlineBackendConfigFileConfig.builder().backendConfigFileContent("test-backend-config").build();
    TerraformInheritOutput inheritOutput = TerraformInheritOutput.builder()
                                               .backendConfig("back-content")
                                               .backendConfigurationFileConfig(backendConfigFileConfig)
                                               .workspace("w1")
                                               .planName("plan")
                                               .build();
    doReturn(inheritOutput).when(terraformStepHelper).getSavedInheritOutput(any(), any(), any());
    TaskRequest taskRequest = terraformApplyStep.obtainTaskAfterRbac(ambiance, stepElementParameters, stepInputPackage);
    assertThat(taskRequest).isNotNull();
    PowerMockito.verifyStatic(TaskRequestsUtils.class, times(1));
    TaskRequestsUtils.prepareCDTaskRequest(any(), taskDataArgumentCaptor.capture(), any(), any(), any(), any(), any());
    assertThat(taskDataArgumentCaptor.getValue()).isNotNull();
    assertThat(taskDataArgumentCaptor.getValue().getParameters()).isNotNull();
    TerraformTaskNGParameters taskParameters =
        (TerraformTaskNGParameters) taskDataArgumentCaptor.getValue().getParameters()[0];
    assertThat(taskParameters.getTaskType()).isEqualTo(TFTaskType.APPLY);
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testObtainTaskAfterRbacInheritPlanFFEnabled() {
    Ambiance ambiance = getAmbiance();
    TerraformApplyStepParameters applyStepParameters =
        TerraformApplyStepParameters.infoBuilder()
            .provisionerIdentifier(ParameterField.createValueField("Id"))
            .configuration(TerraformStepConfigurationParameters.builder()
                               .type(TerraformStepConfigurationType.INHERIT_FROM_PLAN)
                               .build())
            .build();
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
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(applyStepParameters).build();
    StepInputPackage stepInputPackage = StepInputPackage.builder().build();
    doReturn("test-account/test-org/test-project/Id").when(terraformStepHelper).generateFullIdentifier(any(), any());
    doReturn(gitFetchFilesConfig).when(terraformStepHelper).getGitFetchFilesConfig(any(), any(), any());
    doReturn(EnvironmentType.NON_PROD).when(stepHelper).getEnvironmentType(any());

    Mockito.mockStatic(TaskRequestsUtils.class);
    PowerMockito.when(TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());
    ArgumentCaptor<TaskData> taskDataArgumentCaptor = ArgumentCaptor.forClass(TaskData.class);

    TerraformInheritOutput inheritOutput = TerraformInheritOutput.builder()
                                               .backendConfig("back-content")
                                               .useConnectorCredentials(true)
                                               .workspace("w1")
                                               .planName("plan")
                                               .build();
    doReturn(inheritOutput).when(terraformStepHelper).getSavedInheritOutput(any(), any(), any());
    TaskRequest taskRequest = terraformApplyStep.obtainTaskAfterRbac(ambiance, stepElementParameters, stepInputPackage);
    assertThat(taskRequest).isNotNull();
    PowerMockito.verifyStatic(TaskRequestsUtils.class, times(1));
    TaskRequestsUtils.prepareCDTaskRequest(any(), taskDataArgumentCaptor.capture(), any(), any(), any(), any(), any());
    assertThat(taskDataArgumentCaptor.getValue()).isNotNull();
    assertThat(taskDataArgumentCaptor.getValue().getParameters()).isNotNull();
    TerraformTaskNGParameters taskParameters =
        (TerraformTaskNGParameters) taskDataArgumentCaptor.getValue().getParameters()[0];
    assertThat(taskParameters.getTaskType()).isEqualTo(TFTaskType.APPLY);
    assertThat(taskParameters.isTfModuleSourceInheritSSH()).isTrue();
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testObtainTaskAfterRbacInheritPlanWithArtifactory() {
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

    TerraformApplyStepParameters applyStepParameters = TerraformStepDataGenerator.generateApplyStepPlan(
        StoreConfigType.ARTIFACTORY, artifactoryStoreConfigFiles, artifactoryStoreVarFiles);
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(applyStepParameters).build();

    ArtifactoryStoreDelegateConfig artifactoryStoreDelegateConfig =
        TerraformStepDataGenerator.createStoreDelegateConfig();
    StepInputPackage stepInputPackage = StepInputPackage.builder().build();
    doReturn("test-account/test-org/test-project/Id").when(terraformStepHelper).generateFullIdentifier(any(), any());
    doReturn(artifactoryStoreDelegateConfig)
        .when(terraformStepHelper)
        .getFileStoreFetchFilesConfig(any(), any(), any());
    doReturn(EnvironmentType.NON_PROD).when(stepHelper).getEnvironmentType(any());

    Mockito.mockStatic(TaskRequestsUtils.class);
    PowerMockito.when(TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());
    ArgumentCaptor<TaskData> taskDataArgumentCaptor = ArgumentCaptor.forClass(TaskData.class);

    TerraformInheritOutput inheritOutput =
        TerraformInheritOutput.builder().backendConfig("back-content").workspace("w1").planName("plan").build();
    doReturn(inheritOutput).when(terraformStepHelper).getSavedInheritOutput(any(), any(), any());
    TaskRequest taskRequest = terraformApplyStep.obtainTaskAfterRbac(ambiance, stepElementParameters, stepInputPackage);
    assertThat(taskRequest).isNotNull();
    PowerMockito.verifyStatic(TaskRequestsUtils.class, times(1));
    TaskRequestsUtils.prepareCDTaskRequest(any(), taskDataArgumentCaptor.capture(), any(), any(), any(), any(), any());
    assertThat(taskDataArgumentCaptor.getValue()).isNotNull();
    assertThat(taskDataArgumentCaptor.getValue().getParameters()).isNotNull();
    TerraformTaskNGParameters taskParameters =
        (TerraformTaskNGParameters) taskDataArgumentCaptor.getValue().getParameters()[0];
    assertThat(taskParameters.getTaskType()).isEqualTo(TFTaskType.APPLY);
  }

  @Test // Unknown configuration Type: [InheritFromApply]
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testObtainTaskAfterRbacNegativeScenario() {
    Ambiance ambiance = getAmbiance();
    TerraformApplyStepParameters applyStepParameters =
        TerraformApplyStepParameters.infoBuilder()
            .provisionerIdentifier(ParameterField.createValueField("Id"))
            .configuration(TerraformStepConfigurationParameters.builder()
                               .type(TerraformStepConfigurationType.INHERIT_FROM_APPLY)
                               .build())
            .build();
    GitFetchFilesConfig gitFetchFilesConfig =
        GitFetchFilesConfig.builder().identifier("terraform").succeedIfFileNotFound(false).build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(applyStepParameters).build();
    StepInputPackage stepInputPackage = StepInputPackage.builder().build();
    doReturn("test-account/test-org/test-project/Id").when(terraformStepHelper).generateFullIdentifier(any(), any());
    doReturn(gitFetchFilesConfig).when(terraformStepHelper).getGitFetchFilesConfig(any(), any(), any());
    doReturn(EnvironmentType.NON_PROD).when(stepHelper).getEnvironmentType(any());

    Mockito.mockStatic(TaskRequestsUtils.class);
    PowerMockito.when(TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());

    TerraformInheritOutput inheritOutput =
        TerraformInheritOutput.builder().backendConfig("back-content").workspace("w1").planName("plan").build();
    doReturn(inheritOutput).when(terraformStepHelper).getSavedInheritOutput(any(), any(), any());
    String message = "Unknown configuration Type: [InheritFromApply]";
    try {
      terraformApplyStep.obtainTaskAfterRbac(ambiance, stepElementParameters, stepInputPackage);
    } catch (InvalidRequestException invalidRequestException) {
      assertThat(invalidRequestException.getMessage()).isEqualTo(message);
    }
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testHandleTaskResultWithSecurityContext_Inherited() throws Exception {
    Ambiance ambiance = getAmbiance();
    TerraformApplyStepParameters applyStepParameters =
        TerraformApplyStepParameters.infoBuilder()
            .provisionerIdentifier(ParameterField.createValueField("Id"))
            .configuration(TerraformStepConfigurationParameters.builder()
                               .type(TerraformStepConfigurationType.INHERIT_FROM_PLAN)
                               .build())
            .build();
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
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(applyStepParameters).build();
    doReturn("test-account/test-org/test-project/Id").when(terraformStepHelper).generateFullIdentifier(any(), any());
    doReturn(gitFetchFilesConfig).when(terraformStepHelper).getGitFetchFilesConfig(any(), any(), any());
    TerraformInheritOutput inheritOutput =
        TerraformInheritOutput.builder().backendConfig("back-content").workspace("w1").planName("plan").build();
    doReturn(inheritOutput).when(terraformStepHelper).getSavedInheritOutput(any(), any(), any());
    List<UnitProgress> unitProgresses = Collections.singletonList(UnitProgress.newBuilder().build());
    UnitProgressData unitProgressData = UnitProgressData.builder().unitProgresses(unitProgresses).build();
    TerraformTaskNGResponse terraformTaskNGResponse = TerraformTaskNGResponse.builder()
                                                          .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                          .unitProgressData(unitProgressData)
                                                          .build();
    StepResponse stepResponse = terraformApplyStep.handleTaskResultWithSecurityContext(
        ambiance, stepElementParameters, () -> terraformTaskNGResponse);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getStepOutcomes()).isNotNull();
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testHandleTaskResultWithSecurityContext_Inline() throws Exception {
    Ambiance ambiance = getAmbiance();
    TerraformApplyStepParameters applyStepParameters =
        TerraformApplyStepParameters.infoBuilder()
            .provisionerIdentifier(ParameterField.createValueField("Id"))
            .configuration(
                TerraformStepConfigurationParameters.builder().type(TerraformStepConfigurationType.INLINE).build())
            .build();
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
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(applyStepParameters).build();
    doReturn("test-account/test-org/test-project/Id").when(terraformStepHelper).generateFullIdentifier(any(), any());
    doReturn(gitFetchFilesConfig).when(terraformStepHelper).getGitFetchFilesConfig(any(), any(), any());
    TerraformInheritOutput inheritOutput =
        TerraformInheritOutput.builder().backendConfig("back-content").workspace("w1").planName("plan").build();
    doReturn(inheritOutput).when(terraformStepHelper).getSavedInheritOutput(any(), any(), any());
    List<UnitProgress> unitProgresses = Collections.singletonList(UnitProgress.newBuilder().build());
    UnitProgressData unitProgressData = UnitProgressData.builder().unitProgresses(unitProgresses).build();
    TerraformTaskNGResponse terraformTaskNGResponse = TerraformTaskNGResponse.builder()
                                                          .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                          .unitProgressData(unitProgressData)
                                                          .build();
    StepResponse stepResponse = terraformApplyStep.handleTaskResultWithSecurityContext(
        ambiance, stepElementParameters, () -> terraformTaskNGResponse);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getStepOutcomes()).isNotNull();
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testHandleTaskResultWithSecurityContextAndArtifactoryAsStore_Inline() throws Exception {
    Ambiance ambiance = getAmbiance();
    TerraformApplyStepParameters applyStepParameters =
        TerraformApplyStepParameters.infoBuilder()
            .provisionerIdentifier(ParameterField.createValueField("Id"))
            .configuration(
                TerraformStepConfigurationParameters.builder().type(TerraformStepConfigurationType.INLINE).build())
            .build();
    ArtifactoryStoreDelegateConfig artifactoryStoreDelegateConfig =
        TerraformStepDataGenerator.createStoreDelegateConfig();

    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(applyStepParameters).build();
    doReturn("test-account/test-org/test-project/Id").when(terraformStepHelper).generateFullIdentifier(any(), any());
    doReturn(artifactoryStoreDelegateConfig)
        .when(terraformStepHelper)
        .getFileStoreFetchFilesConfig(any(), any(), any());
    TerraformInheritOutput inheritOutput =
        TerraformInheritOutput.builder().backendConfig("back-content").workspace("w1").planName("plan").build();
    doReturn(inheritOutput).when(terraformStepHelper).getSavedInheritOutput(any(), any(), any());
    List<UnitProgress> unitProgresses = Collections.singletonList(UnitProgress.newBuilder().build());
    UnitProgressData unitProgressData = UnitProgressData.builder().unitProgresses(unitProgresses).build();
    TerraformTaskNGResponse terraformTaskNGResponse = TerraformTaskNGResponse.builder()
                                                          .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                          .unitProgressData(unitProgressData)
                                                          .build();
    StepResponse stepResponse = terraformApplyStep.handleTaskResultWithSecurityContext(
        ambiance, stepElementParameters, () -> terraformTaskNGResponse);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getStepOutcomes()).isNotNull();
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testGetStepParametersClass() {
    assertThat(terraformApplyStep.getStepParametersClass()).isEqualTo(StepElementParameters.class);
  }
  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testHandleTaskResultWithSecurityContextNegativeScenario() throws Exception {
    Ambiance ambiance = getAmbiance();
    TerraformApplyStepParameters applyStepParameters =
        TerraformApplyStepParameters.infoBuilder()
            .provisionerIdentifier(ParameterField.createValueField("Id"))
            .configuration(TerraformStepConfigurationParameters.builder()
                               .type(TerraformStepConfigurationType.INHERIT_FROM_APPLY)
                               .build())
            .build();
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
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(applyStepParameters).build();
    doReturn("test-account/test-org/test-project/Id").when(terraformStepHelper).generateFullIdentifier(any(), any());
    doReturn(gitFetchFilesConfig).when(terraformStepHelper).getGitFetchFilesConfig(any(), any(), any());
    TerraformInheritOutput inheritOutput =
        TerraformInheritOutput.builder().backendConfig("back-content").workspace("w1").planName("plan").build();
    doReturn(inheritOutput).when(terraformStepHelper).getSavedInheritOutput(any(), any(), any());
    List<UnitProgress> unitProgresses = Collections.singletonList(UnitProgress.newBuilder().build());
    UnitProgressData unitProgressData = UnitProgressData.builder().unitProgresses(unitProgresses).build();
    TerraformTaskNGResponse terraformTaskNGResponse = TerraformTaskNGResponse.builder()
                                                          .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                          .unitProgressData(unitProgressData)
                                                          .build();
    String message = "Unknown configuration Type: [InheritFromApply]";
    try {
      terraformApplyStep.handleTaskResultWithSecurityContext(
          ambiance, stepElementParameters, () -> terraformTaskNGResponse);
    } catch (InvalidRequestException invalidRequestException) {
      assertThat(invalidRequestException.getMessage()).isEqualTo(message);
    }
  }

  @Test // Different Status
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void handleTaskResultWithSecurityContextDifferentStatus() throws Exception {
    Ambiance ambiance = getAmbiance();
    TerraformApplyStepParameters applyStepParameters =
        TerraformApplyStepParameters.infoBuilder()
            .provisionerIdentifier(ParameterField.createValueField("Id"))
            .configuration(TerraformStepConfigurationParameters.builder()
                               .type(TerraformStepConfigurationType.INHERIT_FROM_PLAN)
                               .build())
            .build();
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
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(applyStepParameters).build();
    doReturn("test-account/test-org/test-project/Id").when(terraformStepHelper).generateFullIdentifier(any(), any());
    doReturn(gitFetchFilesConfig).when(terraformStepHelper).getGitFetchFilesConfig(any(), any(), any());
    TerraformInheritOutput inheritOutput =
        TerraformInheritOutput.builder().backendConfig("back-content").workspace("w1").planName("plan").build();
    doReturn(inheritOutput).when(terraformStepHelper).getSavedInheritOutput(any(), any(), any());
    List<UnitProgress> unitProgresses = Collections.singletonList(UnitProgress.newBuilder().build());
    UnitProgressData unitProgressData = UnitProgressData.builder().unitProgresses(unitProgresses).build();

    TerraformTaskNGResponse terraformTaskNGResponseFailure = TerraformTaskNGResponse.builder()
                                                                 .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                                                                 .unitProgressData(unitProgressData)
                                                                 .build();
    StepResponse stepResponse = terraformApplyStep.handleTaskResultWithSecurityContext(
        ambiance, stepElementParameters, () -> terraformTaskNGResponseFailure);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
    assertThat(stepResponse.getStepOutcomes()).isNotNull();

    TerraformTaskNGResponse terraformTaskNGResponseRunning = TerraformTaskNGResponse.builder()
                                                                 .commandExecutionStatus(CommandExecutionStatus.RUNNING)
                                                                 .unitProgressData(unitProgressData)
                                                                 .build();
    stepResponse = terraformApplyStep.handleTaskResultWithSecurityContext(
        ambiance, stepElementParameters, () -> terraformTaskNGResponseRunning);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.RUNNING);
    assertThat(stepResponse.getStepOutcomes()).isNotNull();

    TerraformTaskNGResponse terraformTaskNGResponseQueued = TerraformTaskNGResponse.builder()
                                                                .commandExecutionStatus(CommandExecutionStatus.QUEUED)
                                                                .unitProgressData(unitProgressData)
                                                                .build();
    stepResponse = terraformApplyStep.handleTaskResultWithSecurityContext(
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
      terraformApplyStep.handleTaskResultWithSecurityContext(
          ambiance, stepElementParameters, () -> terraformTaskNGResponseSkipped);
    } catch (InvalidRequestException invalidRequestException) {
      assertThat(invalidRequestException.getMessage()).isEqualTo(message);
    }
  }
}
