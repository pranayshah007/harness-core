/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.terraform;

import static io.harness.cdng.provision.terraform.TerraformStepConfigurationType.INHERIT_FROM_APPLY;
import static io.harness.cdng.provision.terraform.TerraformStepConfigurationType.INHERIT_FROM_PLAN;
import static io.harness.cdng.provision.terraform.TerraformStepConfigurationType.INLINE;
import static io.harness.cdng.provision.terraform.TerraformStepHelper.TF_BACKEND_CONFIG_FILE;
import static io.harness.cdng.provision.terraform.TerraformStepHelper.TF_CONFIG_FILES;
import static io.harness.rule.OwnerRule.TMACARI;
import static io.harness.rule.OwnerRule.VLICA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EnvironmentType;
import io.harness.category.element.UnitTests;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.k8s.beans.StepExceptionPassThroughData;
import io.harness.cdng.manifest.yaml.ArtifactoryStorageConfigDTO;
import io.harness.cdng.manifest.yaml.GitStoreConfigDTO;
import io.harness.cdng.manifest.yaml.GithubStoreDTO;
import io.harness.cdng.manifest.yaml.TerraformCommandFlagType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.storeconfig.ArtifactoryStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.S3StoreTFDelegateConfig;
import io.harness.delegate.task.filestore.FileStoreFetchFilesConfig;
import io.harness.delegate.task.git.GitFetchFilesConfig;
import io.harness.delegate.task.git.GitFetchResponse;
import io.harness.delegate.task.terraform.InlineTerraformVarFileInfo;
import io.harness.delegate.task.terraform.RemoteTerraformVarFileInfo;
import io.harness.delegate.task.terraform.TFTaskType;
import io.harness.delegate.task.terraform.TerraformTaskNGParameters;
import io.harness.delegate.task.terraform.TerraformTaskNGResponse;
import io.harness.delegate.task.terraform.TerraformVarFileInfo;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.UnitProgress;
import io.harness.logging.UnitStatus;
import io.harness.ng.core.EntityDetail;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepHelper;
import io.harness.steps.TaskRequestsUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.MockitoRule;
import org.powermock.core.classloader.annotations.PrepareForTest;

@RunWith(MockitoJUnitRunner.class)
@PrepareForTest({TaskRequestsUtils.class})
@OwnedBy(HarnessTeam.CDP)
public class TerraformDestroyStepV2Test extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private KryoSerializer kryoSerializer;
  @Mock private TerraformStepHelper terraformStepHelper;
  @Mock private CDFeatureFlagHelper cdFeatureFlagHelper;
  @Mock private PipelineRbacHelper pipelineRbacHelper;
  @Mock private StepHelper stepHelper;
  @Mock private TerraformConfigDAL terraformConfigDAL;
  @InjectMocks private TerraformDestroyStepV2 terraformDestroyStepV2;
  @Captor ArgumentCaptor<List<EntityDetail>> captor;

  private Ambiance getAmbiance() {
    return Ambiance.newBuilder()
        .putSetupAbstractions("accountId", "test-account")
        .putSetupAbstractions("projectIdentifier", "test-project")
        .putSetupAbstractions("orgIdentifier", "test-org")
        .build();
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testValidateResourcesWithGithub() {
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
    TerraformDestroyStepParameters destroyStepParameters = TerraformStepDataGenerator.generateDestroyStepPlan(
        StoreConfigType.GITHUB, gitStoreConfigFiles, gitStoreVarFiles);

    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(destroyStepParameters).build();
    terraformDestroyStepV2.validateResources(ambiance, stepElementParameters);
    verify(pipelineRbacHelper, times(1)).checkRuntimePermissions(eq(ambiance), captor.capture(), eq(true));

    List<EntityDetail> entityDetails = captor.getValue();
    assertThat(entityDetails.size()).isEqualTo(2);
    assertThat(entityDetails.get(0).getEntityRef().getIdentifier()).isEqualTo("terraform");
    assertThat(entityDetails.get(0).getEntityRef().getAccountIdentifier()).isEqualTo("test-account");
    assertThat(entityDetails.get(1).getEntityRef().getIdentifier()).isEqualTo("terraform");
    assertThat(entityDetails.get(1).getEntityRef().getAccountIdentifier()).isEqualTo("test-account");
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testValidateResourcesWithArtifactory() {
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
    TerraformDestroyStepParameters destroyStepParameters = TerraformStepDataGenerator.generateDestroyStepPlan(
        StoreConfigType.ARTIFACTORY, artifactoryStoreConfigFiles, artifactoryStoreVarFiles);

    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(destroyStepParameters).build();
    terraformDestroyStepV2.validateResources(ambiance, stepElementParameters);
    verify(pipelineRbacHelper, times(1)).checkRuntimePermissions(eq(ambiance), captor.capture(), eq(true));

    List<EntityDetail> entityDetails = captor.getValue();
    assertThat(entityDetails.size()).isEqualTo(2);
    assertThat(entityDetails.get(0).getEntityRef().getIdentifier()).isEqualTo("connectorRef");
    assertThat(entityDetails.get(0).getEntityRef().getAccountIdentifier()).isEqualTo("test-account");
    assertThat(entityDetails.get(1).getEntityRef().getIdentifier()).isEqualTo("connectorRef2");
    assertThat(entityDetails.get(1).getEntityRef().getAccountIdentifier()).isEqualTo("test-account");
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testStartChainLink() {
    Ambiance ambiance = getAmbiance();
    TerraformStepDataGenerator.GitStoreConfig gitStoreConfigFiles =
        TerraformStepDataGenerator.GitStoreConfig.builder()
            .branch("master")
            .fetchType(FetchType.BRANCH)
            .folderPath(ParameterField.createValueField("Config/"))
            .connectoref(ParameterField.createValueField("terraform"))
            .build();

    TerraformDestroyStepParameters destroyStepParameters =
        TerraformStepDataGenerator.generateDestroyStepPlanWithVarsInline(StoreConfigType.GITHUB, gitStoreConfigFiles);

    destroyStepParameters.getConfiguration().getIsSkipTerraformRefresh().setValue(true);
    destroyStepParameters.getConfiguration().setCliOptions(
        List.of(TerraformCliOptionFlag.builder()
                    .commandType(TerraformCommandFlagType.DESTROY)
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
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(destroyStepParameters).build();
    StepInputPackage stepInputPackage = StepInputPackage.builder().build();
    doReturn("test-account/test-org/test-project/Id").when(terraformStepHelper).generateFullIdentifier(any(), any());
    doReturn(gitFetchFilesConfig).when(terraformStepHelper).getGitFetchFilesConfig(any(), any(), any());
    doReturn(EnvironmentType.NON_PROD).when(stepHelper).getEnvironmentType(any());
    doReturn(true).when(cdFeatureFlagHelper).isEnabled(any(), any());
    doReturn(new HashMap<String, String>() {
      { put("DESTROY", "-lock-timeout=0s"); }
    })
        .when(terraformStepHelper)
        .getTerraformCliFlags(any());

    List<TerraformVarFileInfo> varFileInfo = new ArrayList<>();
    varFileInfo.add(InlineTerraformVarFileInfo.builder().varFileContent("var-file-inline").build());
    doReturn(varFileInfo).when(terraformStepHelper).toTerraformVarFileInfoWithIdentifierAndManifest(any(), any());
    doReturn(varFileInfo).when(terraformStepHelper).getRemoteVarFilesInfo(any(), any());
    doReturn(false).when(terraformStepHelper).hasGitVarFiles(any());
    doReturn(false).when(terraformStepHelper).hasS3VarFiles(any());

    doReturn(TaskChainResponse.builder().chainEnd(true).taskRequest(TaskRequest.newBuilder().build()).build())
        .when(terraformStepHelper)
        .executeTerraformTask(any(), any(), any(), any(), any(), any());

    ArgumentCaptor<TerraformTaskNGParameters> tfTaskNGParametersArgumentCaptor =
        ArgumentCaptor.forClass(TerraformTaskNGParameters.class);
    ArgumentCaptor<TerraformPassThroughData> tfPassThroughDataArgumentCaptor =
        ArgumentCaptor.forClass(TerraformPassThroughData.class);

    terraformDestroyStepV2.startChainLinkAfterRbac(ambiance, stepElementParameters, stepInputPackage);

    verify(terraformStepHelper)
        .executeTerraformTask(tfTaskNGParametersArgumentCaptor.capture(), any(), any(),
            tfPassThroughDataArgumentCaptor.capture(), any(), any());

    assertThat(tfTaskNGParametersArgumentCaptor.getValue()).isNotNull();
    TerraformTaskNGParameters taskParameters = tfTaskNGParametersArgumentCaptor.getValue();

    assertThat(taskParameters.getTaskType()).isEqualTo(TFTaskType.DESTROY);
    assertThat(taskParameters.isSkipTerraformRefresh()).isTrue();
    assertThat(taskParameters.getTerraformCommandFlags().get("DESTROY")).isEqualTo("-lock-timeout=0s");

    TerraformPassThroughData terraformPassThroughData = tfPassThroughDataArgumentCaptor.getValue();
    assertThat(terraformPassThroughData).isNotNull();
    assertThat(terraformPassThroughData.getTerraformTaskNGParametersBuilder()).isNotNull();
    assertThat(terraformPassThroughData.hasGitFiles).isFalse();
    assertThat(terraformPassThroughData.hasS3Files).isFalse();
    assertThat(terraformPassThroughData.getUnitProgresses()).isEmpty();
    verify(terraformStepHelper, times(0)).fetchRemoteVarFiles(any(), any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testStartChainLinkAndTFCloudCli() {
    Ambiance ambiance = getAmbiance();
    TerraformStepDataGenerator.GitStoreConfig gitStoreConfigFiles =
        TerraformStepDataGenerator.GitStoreConfig.builder()
            .branch("master")
            .fetchType(FetchType.BRANCH)
            .folderPath(ParameterField.createValueField("Config/"))
            .connectoref(ParameterField.createValueField("terraform"))
            .build();

    TerraformDestroyStepParameters destroyStepParameters =
        TerraformStepDataGenerator.generateDestroyStepPlanWithVarsInline(StoreConfigType.GITHUB, gitStoreConfigFiles);

    destroyStepParameters.getConfiguration().getSpec().isTerraformCloudCli.setValue(true);
    destroyStepParameters.getConfiguration().setCliOptions(
        List.of(TerraformCliOptionFlag.builder()
                    .commandType(TerraformCommandFlagType.DESTROY)
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
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(destroyStepParameters).build();
    StepInputPackage stepInputPackage = StepInputPackage.builder().build();
    doReturn("test-account/test-org/test-project/Id").when(terraformStepHelper).generateFullIdentifier(any(), any());
    doReturn(gitFetchFilesConfig).when(terraformStepHelper).getGitFetchFilesConfig(any(), any(), any());
    doReturn(EnvironmentType.NON_PROD).when(stepHelper).getEnvironmentType(any());
    doReturn(true).when(cdFeatureFlagHelper).isEnabled(any(), any());
    doReturn(new HashMap<String, String>() {
      { put("DESTROY", "-lock-timeout=0s"); }
    })
        .when(terraformStepHelper)
        .getTerraformCliFlags(any());

    List<TerraformVarFileInfo> varFileInfo = new ArrayList<>();
    varFileInfo.add(InlineTerraformVarFileInfo.builder().varFileContent("var-file-inline").build());
    doReturn(varFileInfo).when(terraformStepHelper).toTerraformVarFileInfoWithIdentifierAndManifest(any(), any());

    doReturn(varFileInfo).when(terraformStepHelper).getRemoteVarFilesInfo(any(), any());
    doReturn(false).when(terraformStepHelper).hasGitVarFiles(any());
    doReturn(false).when(terraformStepHelper).hasS3VarFiles(any());

    doReturn(TaskChainResponse.builder().chainEnd(true).taskRequest(TaskRequest.newBuilder().build()).build())
        .when(terraformStepHelper)
        .executeTerraformTask(any(), any(), any(), any(), any(), any());

    ArgumentCaptor<TerraformTaskNGParameters> tfTaskNGParametersArgumentCaptor =
        ArgumentCaptor.forClass(TerraformTaskNGParameters.class);
    ArgumentCaptor<TerraformPassThroughData> tfPassThroughDataArgumentCaptor =
        ArgumentCaptor.forClass(TerraformPassThroughData.class);

    terraformDestroyStepV2.startChainLinkAfterRbac(ambiance, stepElementParameters, stepInputPackage);

    verify(terraformStepHelper)
        .executeTerraformTask(tfTaskNGParametersArgumentCaptor.capture(), any(), any(),
            tfPassThroughDataArgumentCaptor.capture(), any(), any());

    assertThat(tfTaskNGParametersArgumentCaptor.getValue()).isNotNull();
    TerraformTaskNGParameters taskParameters = tfTaskNGParametersArgumentCaptor.getValue();

    assertThat(taskParameters.getTaskType()).isEqualTo(TFTaskType.DESTROY);
    assertThat(taskParameters.getEncryptionConfig()).isNull();
    assertThat(taskParameters.getWorkspace()).isNull();
    assertThat(taskParameters.isTerraformCloudCli()).isTrue();
    assertThat(taskParameters.isSkipTerraformRefresh()).isFalse();
    assertThat(taskParameters.getTerraformCommandFlags().get("DESTROY")).isEqualTo("-lock-timeout=0s");

    TerraformPassThroughData terraformPassThroughData = tfPassThroughDataArgumentCaptor.getValue();
    assertThat(terraformPassThroughData).isNotNull();
    assertThat(terraformPassThroughData.getTerraformTaskNGParametersBuilder()).isNotNull();
    assertThat(terraformPassThroughData.hasGitFiles).isFalse();
    assertThat(terraformPassThroughData.hasS3Files).isFalse();
    assertThat(terraformPassThroughData.getUnitProgresses()).isEmpty();
    verify(terraformStepHelper, times(0)).fetchRemoteVarFiles(any(), any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testStartChainLinkArtifactoryStoreInline() {
    Ambiance ambiance = getAmbiance();
    TerraformStepDataGenerator.ArtifactoryStoreConfig artifactoryStoreConfigFiles =
        TerraformStepDataGenerator.ArtifactoryStoreConfig.builder().build();

    TerraformDestroyStepParameters destroyStepParameters =
        TerraformStepDataGenerator.generateDestroyStepPlanWithVarsInline(
            StoreConfigType.ARTIFACTORY, artifactoryStoreConfigFiles);
    FileStoreFetchFilesConfig fileStoreFetchFilesConfig = ArtifactoryStoreDelegateConfig.builder().build();
    destroyStepParameters.getConfiguration().setType(INLINE);

    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(destroyStepParameters).build();
    StepInputPackage stepInputPackage = StepInputPackage.builder().build();
    doReturn("test-account/test-org/test-project/Id").when(terraformStepHelper).generateFullIdentifier(any(), any());
    doReturn(null).when(terraformStepHelper).getGitFetchFilesConfig(any(), any(), any());
    doReturn(fileStoreFetchFilesConfig).when(terraformStepHelper).getFileStoreFetchFilesConfig(any(), any(), any());
    doReturn(EnvironmentType.NON_PROD).when(stepHelper).getEnvironmentType(any());

    List<TerraformVarFileInfo> varFileInfo = new ArrayList<>();
    varFileInfo.add(InlineTerraformVarFileInfo.builder().varFileContent("var-file-inline").build());
    doReturn(varFileInfo).when(terraformStepHelper).toTerraformVarFileInfoWithIdentifierAndManifest(any(), any());

    doReturn(varFileInfo).when(terraformStepHelper).getRemoteVarFilesInfo(any(), any());
    doReturn(false).when(terraformStepHelper).hasGitVarFiles(any());
    doReturn(false).when(terraformStepHelper).hasS3VarFiles(any());

    doReturn(TaskChainResponse.builder().chainEnd(true).taskRequest(TaskRequest.newBuilder().build()).build())
        .when(terraformStepHelper)
        .executeTerraformTask(any(), any(), any(), any(), any(), any());

    ArgumentCaptor<TerraformTaskNGParameters> tfTaskNGParametersArgumentCaptor =
        ArgumentCaptor.forClass(TerraformTaskNGParameters.class);
    ArgumentCaptor<TerraformPassThroughData> tfPassThroughDataArgumentCaptor =
        ArgumentCaptor.forClass(TerraformPassThroughData.class);

    terraformDestroyStepV2.startChainLinkAfterRbac(ambiance, stepElementParameters, stepInputPackage);

    verify(terraformStepHelper)
        .executeTerraformTask(tfTaskNGParametersArgumentCaptor.capture(), any(), any(),
            tfPassThroughDataArgumentCaptor.capture(), any(), any());

    assertThat(tfTaskNGParametersArgumentCaptor.getValue()).isNotNull();
    TerraformTaskNGParameters taskParameters = tfTaskNGParametersArgumentCaptor.getValue();
    assertThat(taskParameters.getTaskType()).isEqualTo(TFTaskType.DESTROY);
    assertThat(taskParameters.getConfigFile()).isNull();
    assertThat(taskParameters.getFileStoreConfigFiles()).isEqualTo(fileStoreFetchFilesConfig);
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testStartChainLinkInheritPlan() {
    Ambiance ambiance = getAmbiance();
    TerraformDestroyStepParameters destroyStepParameters =
        TerraformDestroyStepParameters.infoBuilder()
            .provisionerIdentifier(ParameterField.createValueField("Id"))
            .configuration(TerraformStepConfigurationParameters.builder()
                               .type(TerraformStepConfigurationType.INHERIT_FROM_PLAN)
                               .commandFlags(List.of(TerraformCliOptionFlag.builder()
                                                         .commandType(TerraformCommandFlagType.DESTROY)
                                                         .flag(ParameterField.createValueField("-lock-timeout=0s"))
                                                         .build()))
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
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(destroyStepParameters).build();
    StepInputPackage stepInputPackage = StepInputPackage.builder().build();
    doReturn("test-account/test-org/test-project/Id").when(terraformStepHelper).generateFullIdentifier(any(), any());
    doReturn(gitFetchFilesConfig).when(terraformStepHelper).getGitFetchFilesConfig(any(), any(), any());
    doReturn(EnvironmentType.NON_PROD).when(stepHelper).getEnvironmentType(any());
    doReturn(true).when(cdFeatureFlagHelper).isEnabled(any(), any());

    doReturn(new HashMap<String, String>() {
      { put("DESTROY", "-lock-timeout=0s"); }
    })
        .when(terraformStepHelper)
        .getTerraformCliFlags(any());

    doReturn(TaskChainResponse.builder().chainEnd(true).taskRequest(TaskRequest.newBuilder().build()).build())
        .when(terraformStepHelper)
        .executeTerraformTask(any(), any(), any(), any(), any(), any());
    TerraformInheritOutput inheritOutput =
        TerraformInheritOutput.builder().backendConfig("back-content").workspace("w1").planName("plan").build();
    doReturn(inheritOutput).when(terraformStepHelper).getSavedInheritOutput(any(), any(), any());

    ArgumentCaptor<TerraformTaskNGParameters> tfTaskNGParametersArgumentCaptor =
        ArgumentCaptor.forClass(TerraformTaskNGParameters.class);
    ArgumentCaptor<TerraformPassThroughData> tfPassThroughDataArgumentCaptor =
        ArgumentCaptor.forClass(TerraformPassThroughData.class);

    terraformDestroyStepV2.startChainLinkAfterRbac(ambiance, stepElementParameters, stepInputPackage);

    verify(terraformStepHelper)
        .executeTerraformTask(tfTaskNGParametersArgumentCaptor.capture(), any(), any(),
            tfPassThroughDataArgumentCaptor.capture(), any(), any());

    assertThat(tfTaskNGParametersArgumentCaptor.getValue()).isNotNull();
    TerraformTaskNGParameters taskParameters = tfTaskNGParametersArgumentCaptor.getValue();

    assertThat(taskParameters.getTaskType()).isEqualTo(TFTaskType.DESTROY);
    assertThat(taskParameters.getTerraformCommandFlags().get("DESTROY")).isEqualTo("-lock-timeout=0s");

    TerraformPassThroughData terraformPassThroughData = tfPassThroughDataArgumentCaptor.getValue();
    assertThat(terraformPassThroughData).isNotNull();
    assertThat(terraformPassThroughData.getTerraformTaskNGParametersBuilder()).isNotNull();
    assertThat(terraformPassThroughData.hasGitFiles).isFalse();
    assertThat(terraformPassThroughData.hasS3Files).isFalse();
    assertThat(terraformPassThroughData.getUnitProgresses()).isEmpty();
    verify(terraformStepHelper, times(0)).fetchRemoteVarFiles(any(), any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testStartChainLinkArtifactoryStoreInheritPlan() {
    Ambiance ambiance = getAmbiance();
    TerraformStepDataGenerator.ArtifactoryStoreConfig artifactoryStoreConfigFiles =
        TerraformStepDataGenerator.ArtifactoryStoreConfig.builder().build();

    TerraformDestroyStepParameters destroyStepParameters =
        TerraformStepDataGenerator.generateDestroyStepPlanWithVarsInline(
            StoreConfigType.ARTIFACTORY, artifactoryStoreConfigFiles);
    FileStoreFetchFilesConfig fileStoreFetchFilesConfig = ArtifactoryStoreDelegateConfig.builder().build();
    destroyStepParameters.getConfiguration().setType(INHERIT_FROM_PLAN);

    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(destroyStepParameters).build();
    StepInputPackage stepInputPackage = StepInputPackage.builder().build();
    doReturn("test-account/test-org/test-project/Id").when(terraformStepHelper).generateFullIdentifier(any(), any());
    doReturn(null).when(terraformStepHelper).getGitFetchFilesConfig(any(), any(), any());
    doReturn(fileStoreFetchFilesConfig).when(terraformStepHelper).getFileStoreFetchFilesConfig(any(), any(), any());
    doReturn(EnvironmentType.NON_PROD).when(stepHelper).getEnvironmentType(any());
    TerraformInheritOutput inheritOutput =
        TerraformInheritOutput.builder().backendConfig("back-content").workspace("w1").planName("plan").build();
    doReturn(inheritOutput).when(terraformStepHelper).getSavedInheritOutput(any(), any(), any());

    ArgumentCaptor<TerraformTaskNGParameters> tfTaskNGParametersArgumentCaptor =
        ArgumentCaptor.forClass(TerraformTaskNGParameters.class);
    ArgumentCaptor<TerraformPassThroughData> tfPassThroughDataArgumentCaptor =
        ArgumentCaptor.forClass(TerraformPassThroughData.class);

    terraformDestroyStepV2.startChainLinkAfterRbac(ambiance, stepElementParameters, stepInputPackage);

    verify(terraformStepHelper)
        .executeTerraformTask(tfTaskNGParametersArgumentCaptor.capture(), any(), any(),
            tfPassThroughDataArgumentCaptor.capture(), any(), any());

    assertThat(tfTaskNGParametersArgumentCaptor.getValue()).isNotNull();
    TerraformTaskNGParameters taskParameters = tfTaskNGParametersArgumentCaptor.getValue();
    assertThat(taskParameters.getTaskType()).isEqualTo(TFTaskType.DESTROY);
    assertThat(taskParameters.getConfigFile()).isNull();
    assertThat(taskParameters.getFileStoreConfigFiles()).isEqualTo(fileStoreFetchFilesConfig);

    TerraformPassThroughData terraformPassThroughData = tfPassThroughDataArgumentCaptor.getValue();
    assertThat(terraformPassThroughData).isNotNull();
    assertThat(terraformPassThroughData.getTerraformTaskNGParametersBuilder()).isNotNull();
    assertThat(terraformPassThroughData.hasGitFiles).isFalse();
    assertThat(terraformPassThroughData.hasS3Files).isFalse();
    assertThat(terraformPassThroughData.getUnitProgresses()).isEmpty();
    verify(terraformStepHelper, times(0)).fetchRemoteVarFiles(any(), any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testStartChainLinkInheritApply() {
    Ambiance ambiance = getAmbiance();
    TerraformDestroyStepParameters destroyStepParameters =
        TerraformDestroyStepParameters.infoBuilder()
            .provisionerIdentifier(ParameterField.createValueField("Id"))
            .configuration(TerraformStepConfigurationParameters.builder()
                               .type(TerraformStepConfigurationType.INHERIT_FROM_APPLY)
                               .skipTerraformRefresh(ParameterField.createValueField(true))
                               .commandFlags(List.of(TerraformCliOptionFlag.builder()
                                                         .commandType(TerraformCommandFlagType.DESTROY)
                                                         .flag(ParameterField.createValueField("-lock-timeout=0s"))
                                                         .build()))
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
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(destroyStepParameters).build();
    StepInputPackage stepInputPackage = StepInputPackage.builder().build();
    doReturn("test-account/test-org/test-project/Id").when(terraformStepHelper).generateFullIdentifier(any(), any());
    doReturn(gitFetchFilesConfig).when(terraformStepHelper).getGitFetchFilesConfig(any(), any(), any());
    doReturn(EnvironmentType.NON_PROD).when(stepHelper).getEnvironmentType(any());
    doReturn(true).when(cdFeatureFlagHelper).isEnabled(any(), any());
    doReturn(new HashMap<String, String>() {
      { put("DESTROY", "-lock-timeout=0s"); }
    })
        .when(terraformStepHelper)
        .getTerraformCliFlags(any());

    List<TerraformVarFileInfo> varFileInfo = new ArrayList<>();
    varFileInfo.add(InlineTerraformVarFileInfo.builder().varFileContent("var-file-inline").build());

    doReturn(varFileInfo).when(terraformStepHelper).prepareTerraformVarFileInfo(any(), any(), anyBoolean());
    doReturn(false).when(terraformStepHelper).hasGitVarFiles(any());
    doReturn(false).when(terraformStepHelper).hasS3VarFiles(any());

    GitStoreConfigDTO configFiles = GithubStoreDTO.builder().build();
    TerraformConfig terraformConfig =
        TerraformConfig.builder().backendConfig("back-content").workspace("w1").configFiles(configFiles).build();
    doReturn(terraformConfig).when(terraformStepHelper).getLastSuccessfulApplyConfig(any(), any());

    ArgumentCaptor<TerraformTaskNGParameters> tfTaskNGParametersArgumentCaptor =
        ArgumentCaptor.forClass(TerraformTaskNGParameters.class);
    ArgumentCaptor<TerraformPassThroughData> tfPassThroughDataArgumentCaptor =
        ArgumentCaptor.forClass(TerraformPassThroughData.class);

    terraformDestroyStepV2.startChainLinkAfterRbac(ambiance, stepElementParameters, stepInputPackage);

    verify(terraformStepHelper)
        .executeTerraformTask(tfTaskNGParametersArgumentCaptor.capture(), any(), any(),
            tfPassThroughDataArgumentCaptor.capture(), any(), any());

    assertThat(tfTaskNGParametersArgumentCaptor.getValue()).isNotNull();
    TerraformTaskNGParameters taskParameters = tfTaskNGParametersArgumentCaptor.getValue();
    assertThat(taskParameters.getTaskType()).isEqualTo(TFTaskType.DESTROY);
    assertThat(taskParameters.isSkipTerraformRefresh()).isTrue();
    assertThat(taskParameters.getTerraformCommandFlags().get("DESTROY")).isEqualTo("-lock-timeout=0s");

    TerraformPassThroughData terraformPassThroughData = tfPassThroughDataArgumentCaptor.getValue();
    assertThat(terraformPassThroughData).isNotNull();
    assertThat(terraformPassThroughData.getTerraformTaskNGParametersBuilder()).isNotNull();
    assertThat(terraformPassThroughData.hasGitFiles).isFalse();
    assertThat(terraformPassThroughData.hasS3Files).isFalse();
    assertThat(terraformPassThroughData.getUnitProgresses()).isEmpty();
    verify(terraformStepHelper, times(0)).fetchRemoteVarFiles(any(), any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testStartChainLinkArtifactoryStoreInheritApply() {
    Ambiance ambiance = getAmbiance();
    TerraformStepDataGenerator.ArtifactoryStoreConfig artifactoryStoreConfigFiles =
        TerraformStepDataGenerator.ArtifactoryStoreConfig.builder().build();
    TerraformStepDataGenerator.ArtifactoryStoreConfig artifactoryStoreVarFiles =
        TerraformStepDataGenerator.ArtifactoryStoreConfig.builder().build();
    TerraformDestroyStepParameters destroyStepParameters = TerraformStepDataGenerator.generateDestroyStepPlan(
        StoreConfigType.ARTIFACTORY, artifactoryStoreConfigFiles, artifactoryStoreVarFiles);
    FileStoreFetchFilesConfig fileStoreFetchFilesConfig = ArtifactoryStoreDelegateConfig.builder().build();
    destroyStepParameters.getConfiguration().setType(INHERIT_FROM_APPLY);
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(destroyStepParameters).build();
    StepInputPackage stepInputPackage = StepInputPackage.builder().build();
    doReturn("test-account/test-org/test-project/Id").when(terraformStepHelper).generateFullIdentifier(any(), any());
    doReturn(null).when(terraformStepHelper).getGitFetchFilesConfig(any(), any(), any());
    doReturn(fileStoreFetchFilesConfig).when(terraformStepHelper).prepareTerraformConfigFileInfo(any(), any());
    doReturn(EnvironmentType.NON_PROD).when(stepHelper).getEnvironmentType(any());

    List<TerraformVarFileInfo> varFileInfo = new ArrayList<>();
    varFileInfo.add(InlineTerraformVarFileInfo.builder().varFileContent("var-file-inline").build());

    TerraformConfig terraformConfig =
        TerraformConfig.builder()
            .backendConfig("back-content")
            .workspace("w1")
            .configFiles(null)
            .fileStoreConfig(
                ArtifactoryStorageConfigDTO.builder().artifactPaths(Collections.singletonList("artifactPath")).build())
            .build();

    doReturn(terraformConfig).when(terraformStepHelper).getLastSuccessfulApplyConfig(any(), any());

    doReturn(varFileInfo).when(terraformStepHelper).prepareTerraformVarFileInfo(any(), any(), anyBoolean());
    doReturn(false).when(terraformStepHelper).hasGitVarFiles(any());
    doReturn(false).when(terraformStepHelper).hasS3VarFiles(any());

    doReturn(terraformConfig).when(terraformStepHelper).getLastSuccessfulApplyConfig(any(), any());

    ArgumentCaptor<TerraformTaskNGParameters> tfTaskNGParametersArgumentCaptor =
        ArgumentCaptor.forClass(TerraformTaskNGParameters.class);
    ArgumentCaptor<TerraformPassThroughData> tfPassThroughDataArgumentCaptor =
        ArgumentCaptor.forClass(TerraformPassThroughData.class);

    terraformDestroyStepV2.startChainLinkAfterRbac(ambiance, stepElementParameters, stepInputPackage);

    verify(terraformStepHelper)
        .executeTerraformTask(tfTaskNGParametersArgumentCaptor.capture(), any(), any(),
            tfPassThroughDataArgumentCaptor.capture(), any(), any());

    assertThat(tfTaskNGParametersArgumentCaptor.getValue()).isNotNull();
    TerraformTaskNGParameters taskParameters = tfTaskNGParametersArgumentCaptor.getValue();
    assertThat(taskParameters.getTaskType()).isEqualTo(TFTaskType.DESTROY);
    assertThat(taskParameters.getConfigFile()).isNull();
    assertThat(taskParameters.getFileStoreConfigFiles()).isEqualTo(fileStoreFetchFilesConfig);

    TerraformPassThroughData terraformPassThroughData = tfPassThroughDataArgumentCaptor.getValue();
    assertThat(terraformPassThroughData).isNotNull();
    assertThat(terraformPassThroughData.getTerraformTaskNGParametersBuilder()).isNotNull();
    assertThat(terraformPassThroughData.hasGitFiles).isFalse();
    assertThat(terraformPassThroughData.hasS3Files).isFalse();
    assertThat(terraformPassThroughData.getUnitProgresses()).isEmpty();
    verify(terraformStepHelper, times(0)).fetchRemoteVarFiles(any(), any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testFinalizeExecutionWithSecurityContext() throws Exception {
    Ambiance ambiance = getAmbiance();
    TerraformDestroyStepParameters destroyStepParameters =
        TerraformDestroyStepParameters.infoBuilder()
            .provisionerIdentifier(ParameterField.createValueField("Id"))
            .configuration(TerraformStepConfigurationParameters.builder()
                               .type(TerraformStepConfigurationType.INHERIT_FROM_PLAN)
                               .build())
            .build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(destroyStepParameters).build();
    doReturn("test-account/test-org/test-project/Id").when(terraformStepHelper).generateFullIdentifier(any(), any());
    List<UnitProgress> unitProgresses = Collections.singletonList(UnitProgress.newBuilder().build());
    UnitProgressData unitProgressData = UnitProgressData.builder().unitProgresses(unitProgresses).build();
    Map<String, String> commitIdForConfigFilesMap = new HashMap<>();
    commitIdForConfigFilesMap.put(TF_CONFIG_FILES, "commitId_1");
    commitIdForConfigFilesMap.put(TF_BACKEND_CONFIG_FILE, "commitId_2");
    TerraformTaskNGResponse terraformTaskNGResponse = TerraformTaskNGResponse.builder()
                                                          .commitIdForConfigFilesMap(commitIdForConfigFilesMap)
                                                          .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                          .unitProgressData(unitProgressData)
                                                          .build();

    TerraformPassThroughData terraformPassThroughData =
        TerraformPassThroughData.builder().hasGitFiles(false).hasS3Files(false).build();

    StepResponse stepResponse = terraformDestroyStepV2.finalizeExecutionWithSecurityContext(
        ambiance, stepElementParameters, terraformPassThroughData, () -> terraformTaskNGResponse);

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getStepOutcomes()).isNotNull();
    verify(terraformConfigDAL, times(1)).clearTerraformConfig(any(), any());
    verify(terraformStepHelper, times(1)).updateParentEntityIdAndVersion(any(), any());
    verify(terraformStepHelper).addTerraformRevisionOutcomeIfRequired(any(), any());
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testGetStepParametersClass() {
    assertThat(terraformDestroyStepV2.getStepParametersClass()).isEqualTo(StepElementParameters.class);
  }

  @Test // Different Status
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void handleFinalizeExecutionWithSecurityContextDifferentStatus() throws Exception {
    Ambiance ambiance = getAmbiance();
    TerraformDestroyStepParameters destroyStepParameters =
        TerraformDestroyStepParameters.infoBuilder()
            .provisionerIdentifier(ParameterField.createValueField("Id"))
            .configuration(TerraformStepConfigurationParameters.builder()
                               .type(TerraformStepConfigurationType.INHERIT_FROM_PLAN)
                               .build())
            .build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(destroyStepParameters).build();
    doReturn("test-account/test-org/test-project/Id").when(terraformStepHelper).generateFullIdentifier(any(), any());
    List<UnitProgress> unitProgresses = Collections.singletonList(UnitProgress.newBuilder().build());
    UnitProgressData unitProgressData = UnitProgressData.builder().unitProgresses(unitProgresses).build();
    TerraformTaskNGResponse terraformTaskNGResponseFailure = TerraformTaskNGResponse.builder()
                                                                 .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                                                                 .unitProgressData(unitProgressData)
                                                                 .build();

    TerraformPassThroughData terraformPassThroughData =
        TerraformPassThroughData.builder().hasGitFiles(false).hasS3Files(false).build();

    StepResponse stepResponse = terraformDestroyStepV2.finalizeExecutionWithSecurityContext(
        ambiance, stepElementParameters, terraformPassThroughData, () -> terraformTaskNGResponseFailure);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
    assertThat(stepResponse.getStepOutcomes()).isNotNull();

    TerraformTaskNGResponse terraformTaskNGResponseRunning = TerraformTaskNGResponse.builder()
                                                                 .commandExecutionStatus(CommandExecutionStatus.RUNNING)
                                                                 .unitProgressData(unitProgressData)
                                                                 .build();
    stepResponse = terraformDestroyStepV2.finalizeExecutionWithSecurityContext(
        ambiance, stepElementParameters, terraformPassThroughData, () -> terraformTaskNGResponseRunning);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.RUNNING);
    assertThat(stepResponse.getStepOutcomes()).isNotNull();

    TerraformTaskNGResponse terraformTaskNGResponseQueued = TerraformTaskNGResponse.builder()
                                                                .commandExecutionStatus(CommandExecutionStatus.QUEUED)
                                                                .unitProgressData(unitProgressData)
                                                                .build();
    stepResponse = terraformDestroyStepV2.finalizeExecutionWithSecurityContext(
        ambiance, stepElementParameters, terraformPassThroughData, () -> terraformTaskNGResponseQueued);
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
      terraformDestroyStepV2.finalizeExecutionWithSecurityContext(
          ambiance, stepElementParameters, terraformPassThroughData, () -> terraformTaskNGResponseSkipped);
    } catch (InvalidRequestException invalidRequestException) {
      assertThat(invalidRequestException.getMessage()).isEqualTo(message);
    }
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testFinalizeExecutionAndFailed() throws Exception {
    Ambiance ambiance = getAmbiance();
    TerraformDestroyStepParameters destroyStepParameters =
        TerraformDestroyStepParameters.infoBuilder()
            .provisionerIdentifier(ParameterField.createValueField("Id"))
            .configuration(TerraformStepConfigurationParameters.builder()
                               .type(TerraformStepConfigurationType.INHERIT_FROM_PLAN)
                               .build())
            .build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(destroyStepParameters).build();
    doReturn("test-account/test-org/test-project/Id").when(terraformStepHelper).generateFullIdentifier(any(), any());
    List<UnitProgress> unitProgresses = Collections.singletonList(UnitProgress.newBuilder().build());
    UnitProgressData unitProgressData = UnitProgressData.builder().unitProgresses(unitProgresses).build();

    StepExceptionPassThroughData terraformPassThroughData =
        StepExceptionPassThroughData.builder()
            .unitProgressData(UnitProgressData.builder().unitProgresses(new ArrayList<>()).build())
            .errorMessage("Exception Error in executing terraform plan task or fetching remote var files")
            .build();

    doReturn(StepResponse.builder()
                 .status(Status.FAILED)
                 .failureInfo(FailureInfo.newBuilder()
                                  .setErrorMessage(
                                      "Exception Error in executing terraform plan task or fetching remote var files")
                                  .build())
                 .build())
        .when(terraformStepHelper)
        .handleStepExceptionFailure(any());

    TerraformTaskNGResponse terraformTaskNGResponse = TerraformTaskNGResponse.builder()
                                                          .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                                                          .unitProgressData(unitProgressData)
                                                          .build();

    StepResponse stepResponse = terraformDestroyStepV2.finalizeExecutionWithSecurityContext(
        ambiance, stepElementParameters, terraformPassThroughData, () -> terraformTaskNGResponse);

    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
    assertThat(stepResponse.getFailureInfo().getErrorMessage())
        .isEqualTo("Exception Error in executing terraform plan task or fetching remote var files");
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testFinalizeExecutionAndMultipleCommandUnits() throws Exception {
    Ambiance ambiance = getAmbiance();
    TerraformDestroyStepParameters destroyStepParameters =
        TerraformDestroyStepParameters.infoBuilder()
            .provisionerIdentifier(ParameterField.createValueField("Id"))
            .configuration(TerraformStepConfigurationParameters.builder()
                               .type(TerraformStepConfigurationType.INHERIT_FROM_PLAN)
                               .build())
            .build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(destroyStepParameters).build();
    doReturn("test-account/test-org/test-project/Id").when(terraformStepHelper).generateFullIdentifier(any(), any());

    List<UnitProgress> unitProgressesPlan = new ArrayList<>();
    UnitProgress planUP = UnitProgress.newBuilder().setUnitName("Plan").setStatus(UnitStatus.SUCCESS).build();
    unitProgressesPlan.add(planUP);

    List<UnitProgress> unitProgressesFetch = new ArrayList<>();
    UnitProgress fetchFilesUP =
        UnitProgress.newBuilder().setUnitName("Fetch Files").setStatus(UnitStatus.SUCCESS).build();
    unitProgressesFetch.add(fetchFilesUP);

    UnitProgressData unitProgressDataPlan = UnitProgressData.builder().unitProgresses(unitProgressesPlan).build();

    TerraformTaskNGResponse terraformTaskNGResponse = TerraformTaskNGResponse.builder()
                                                          .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                          .unitProgressData(unitProgressDataPlan)
                                                          .detailedExitCode(2)
                                                          .build();

    TerraformPassThroughData terraformPassThroughData = TerraformPassThroughData.builder()
                                                            .hasGitFiles(true)
                                                            .hasS3Files(true)
                                                            .unitProgresses(unitProgressesFetch)
                                                            .build();

    StepResponse stepResponse = terraformDestroyStepV2.finalizeExecutionWithSecurityContext(
        ambiance, stepElementParameters, terraformPassThroughData, () -> terraformTaskNGResponse);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getStepOutcomes()).isNotNull();
    assertThat(stepResponse.getUnitProgressList().size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testExecuteNextLinkWithSecurityContext() throws Exception {
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
    TerraformDestroyStepParameters destroyStepParameters = TerraformStepDataGenerator.generateDestroyStepPlan(
        StoreConfigType.GITHUB, gitStoreConfigFiles, gitStoreVarFiles);
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(destroyStepParameters).build();

    TerraformPassThroughData terraformPassThroughData =
        TerraformPassThroughData.builder()
            .hasS3Files(true)
            .terraformTaskNGParametersBuilder(TerraformTaskNGParameters.builder())
            .build();

    doReturn(TaskChainResponse.builder().taskRequest(TaskRequest.newBuilder().build()).build())
        .when(terraformStepHelper)
        .executeNextLink(eq(ambiance), any(), eq(terraformPassThroughData), any(), eq(stepElementParameters), any());

    GitFetchResponse gitFetchResponse = GitFetchResponse.builder().build();

    terraformDestroyStepV2.executeNextLinkWithSecurityContext(ambiance, stepElementParameters,
        StepInputPackage.builder().build(), terraformPassThroughData, () -> gitFetchResponse);

    verify(terraformStepHelper, times(1)).executeNextLink(any(), any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testStartChainLinkWithGithubWithRemoteWarFileGIT() {
    StepInputPackage stepInputPackage = StepInputPackage.builder().build();

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
    TerraformDestroyStepParameters destroyStepParameters = TerraformStepDataGenerator.generateDestroyStepPlan(
        StoreConfigType.GITHUB, gitStoreConfigFiles, gitStoreVarFiles);
    destroyStepParameters.getConfiguration().getIsSkipTerraformRefresh().setValue(true);
    destroyStepParameters.getConfiguration().setCliOptions(
        List.of(TerraformCliOptionFlag.builder()
                    .commandType(TerraformCommandFlagType.APPLY)
                    .flag(ParameterField.createValueField("-lock-timeout=0s"))
                    .build()));

    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(destroyStepParameters).build();
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

    doReturn("test-account/test-org/test-project/Id").when(terraformStepHelper).generateFullIdentifier(any(), any());
    doReturn(gitFetchFilesConfig).when(terraformStepHelper).getGitFetchFilesConfig(any(), any(), any());
    doReturn(EnvironmentType.NON_PROD).when(stepHelper).getEnvironmentType(any());
    doReturn(true).when(cdFeatureFlagHelper).isEnabled(any(), any());
    doReturn(new HashMap<String, String>() {
      { put("DESTROY", "-lock-timeout=0s"); }
    })
        .when(terraformStepHelper)
        .getTerraformCliFlags(any());

    List<TerraformVarFileInfo> varFileInfo = new ArrayList<>();
    varFileInfo.add(RemoteTerraformVarFileInfo.builder().gitFetchFilesConfig(gitFetchFilesConfig).build());
    doReturn(varFileInfo).when(terraformStepHelper).toTerraformVarFileInfoWithIdentifierAndManifest(any(), any());

    doReturn(varFileInfo).when(terraformStepHelper).getRemoteVarFilesInfo(any(), any());
    doReturn(true).when(terraformStepHelper).hasGitVarFiles(any());
    doReturn(false).when(terraformStepHelper).hasS3VarFiles(any());

    doReturn(TaskChainResponse.builder().chainEnd(true).taskRequest(TaskRequest.newBuilder().build()).build())
        .when(terraformStepHelper)
        .executeTerraformTask(any(), any(), any(), any(), any(), any());

    doReturn(TaskChainResponse.builder().chainEnd(true).taskRequest(TaskRequest.newBuilder().build()).build())
        .when(terraformStepHelper)
        .fetchRemoteVarFiles(any(), any(), any(), any(), any(), any());

    ArgumentCaptor<TerraformPassThroughData> tfPassThroughDataArgumentCaptor =
        ArgumentCaptor.forClass(TerraformPassThroughData.class);

    terraformDestroyStepV2.startChainLinkAfterRbac(ambiance, stepElementParameters, stepInputPackage);

    verify(terraformStepHelper)
        .fetchRemoteVarFiles(tfPassThroughDataArgumentCaptor.capture(), any(), any(), any(), any(), any());

    TerraformPassThroughData terraformPassThroughData = tfPassThroughDataArgumentCaptor.getValue();
    assertThat(terraformPassThroughData).isNotNull();
    assertThat(terraformPassThroughData.getTerraformTaskNGParametersBuilder()).isNotNull();
    assertThat(terraformPassThroughData.hasGitFiles).isTrue();
    assertThat(terraformPassThroughData.hasS3Files).isFalse();
    assertThat(terraformPassThroughData.getUnitProgresses()).isEmpty();
    verify(terraformStepHelper, times(1)).fetchRemoteVarFiles(any(), any(), any(), any(), any(), any());
    verify(terraformStepHelper, times(0)).executeTerraformTask(any(), any(), any(), any(), any(), any());

    TerraformTaskNGParameters taskParameters = terraformPassThroughData.getTerraformTaskNGParametersBuilder().build();
    assertThat(taskParameters.getTaskType()).isEqualTo(TFTaskType.DESTROY);
    assertThat(taskParameters.isSkipTerraformRefresh()).isTrue();
    assertThat(taskParameters.getTerraformCommandFlags().get("DESTROY")).isEqualTo("-lock-timeout=0s");
    assertThat(taskParameters.getConfigFile()).isNotNull();
    assertThat(((RemoteTerraformVarFileInfo) taskParameters.getVarFileInfos().get(0)).getGitFetchFilesConfig())
        .isNotNull();
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testStartChainLinkWithGithubWithRemoteWarFileS3() {
    StepInputPackage stepInputPackage = StepInputPackage.builder().build();

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
    TerraformDestroyStepParameters destroyStepParameters = TerraformStepDataGenerator.generateDestroyStepPlan(
        StoreConfigType.GITHUB, gitStoreConfigFiles, gitStoreVarFiles);
    destroyStepParameters.getConfiguration().getIsSkipTerraformRefresh().setValue(true);
    destroyStepParameters.getConfiguration().setCliOptions(
        List.of(TerraformCliOptionFlag.builder()
                    .commandType(TerraformCommandFlagType.APPLY)
                    .flag(ParameterField.createValueField("-lock-timeout=0s"))
                    .build()));

    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(destroyStepParameters).build();
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

    doReturn("test-account/test-org/test-project/Id").when(terraformStepHelper).generateFullIdentifier(any(), any());
    doReturn(gitFetchFilesConfig).when(terraformStepHelper).getGitFetchFilesConfig(any(), any(), any());
    doReturn(EnvironmentType.NON_PROD).when(stepHelper).getEnvironmentType(any());
    doReturn(true).when(cdFeatureFlagHelper).isEnabled(any(), any());
    doReturn(new HashMap<String, String>() {
      { put("DESTROY", "-lock-timeout=0s"); }
    })
        .when(terraformStepHelper)
        .getTerraformCliFlags(any());

    List<TerraformVarFileInfo> varFileInfo = new ArrayList<>();
    varFileInfo.add(RemoteTerraformVarFileInfo.builder()
                        .filestoreFetchFilesConfig(S3StoreTFDelegateConfig.builder()
                                                       .bucketName("test-bucket")
                                                       .region("test-region")
                                                       .path("test-path")
                                                       .connectorDTO(ConnectorInfoDTO.builder().build())
                                                       .build())
                        .build());
    doReturn(varFileInfo).when(terraformStepHelper).toTerraformVarFileInfoWithIdentifierAndManifest(any(), any());

    doReturn(varFileInfo).when(terraformStepHelper).getRemoteVarFilesInfo(any(), any());
    doReturn(false).when(terraformStepHelper).hasGitVarFiles(any());
    doReturn(true).when(terraformStepHelper).hasS3VarFiles(any());

    doReturn(TaskChainResponse.builder().chainEnd(true).taskRequest(TaskRequest.newBuilder().build()).build())
        .when(terraformStepHelper)
        .executeTerraformTask(any(), any(), any(), any(), any(), any());

    doReturn(TaskChainResponse.builder().chainEnd(true).taskRequest(TaskRequest.newBuilder().build()).build())
        .when(terraformStepHelper)
        .fetchRemoteVarFiles(any(), any(), any(), any(), any(), any());

    ArgumentCaptor<TerraformPassThroughData> tfPassThroughDataArgumentCaptor =
        ArgumentCaptor.forClass(TerraformPassThroughData.class);

    terraformDestroyStepV2.startChainLinkAfterRbac(ambiance, stepElementParameters, stepInputPackage);

    verify(terraformStepHelper)
        .fetchRemoteVarFiles(tfPassThroughDataArgumentCaptor.capture(), any(), any(), any(), any(), any());

    TerraformPassThroughData terraformPassThroughData = tfPassThroughDataArgumentCaptor.getValue();
    assertThat(terraformPassThroughData).isNotNull();
    assertThat(terraformPassThroughData.getTerraformTaskNGParametersBuilder()).isNotNull();
    assertThat(terraformPassThroughData.hasGitFiles).isFalse();
    assertThat(terraformPassThroughData.hasS3Files).isTrue();
    assertThat(terraformPassThroughData.getUnitProgresses()).isEmpty();
    verify(terraformStepHelper, times(1)).fetchRemoteVarFiles(any(), any(), any(), any(), any(), any());
    verify(terraformStepHelper, times(0)).executeTerraformTask(any(), any(), any(), any(), any(), any());

    TerraformTaskNGParameters taskParameters = terraformPassThroughData.getTerraformTaskNGParametersBuilder().build();
    assertThat(taskParameters.getTaskType()).isEqualTo(TFTaskType.DESTROY);
    assertThat(taskParameters.isSkipTerraformRefresh()).isTrue();
    assertThat(taskParameters.getTerraformCommandFlags().get("DESTROY")).isEqualTo("-lock-timeout=0s");
    assertThat(taskParameters.getConfigFile()).isNotNull();
    assertThat(((RemoteTerraformVarFileInfo) taskParameters.getVarFileInfos().get(0)).getFilestoreFetchFilesConfig())
        .isNotNull();
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetGitRevisionsOutputInlineConfigType() {
    TerraformDestroyStepParameters terraformDestroyStepParameters =
        TerraformDestroyStepParameters.infoBuilder()
            .configuration(TerraformStepConfigurationParameters.builder().type(INLINE).build())
            .build();

    Map<String, String> commitIdForConfigFilesMap = new HashMap<>();
    commitIdForConfigFilesMap.put(TF_CONFIG_FILES, "commitId_1");
    commitIdForConfigFilesMap.put(TF_BACKEND_CONFIG_FILE, "commitId_2");
    TerraformTaskNGResponse taskNGResponse =
        TerraformTaskNGResponse.builder().commitIdForConfigFilesMap(commitIdForConfigFilesMap).build();

    Map<String, String> fetchedCommitIdsMap = new HashMap<>();
    fetchedCommitIdsMap.put("varFileId", "commitId_v1");
    TerraformPassThroughData terraformPassThroughData =
        TerraformPassThroughData.builder().fetchedCommitIdsMap(fetchedCommitIdsMap).build();

    Map<String, String> gitRevisionsOutput = terraformDestroyStepV2.getGitRevisionsOutput(
        terraformDestroyStepParameters, taskNGResponse, terraformPassThroughData);

    assertThat(gitRevisionsOutput.size()).isEqualTo(3);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetGitRevisionsOutputInheritFromApplyConfigType() {
    TerraformDestroyStepParameters terraformDestroyStepParameters =
        TerraformDestroyStepParameters.infoBuilder()
            .configuration(TerraformStepConfigurationParameters.builder().type(INHERIT_FROM_APPLY).build())
            .build();

    Map<String, String> commitIdForConfigFilesMap = new HashMap<>();
    commitIdForConfigFilesMap.put(TF_CONFIG_FILES, "commitId_1");
    commitIdForConfigFilesMap.put(TF_BACKEND_CONFIG_FILE, "commitId_2");
    TerraformTaskNGResponse taskNGResponse =
        TerraformTaskNGResponse.builder().commitIdForConfigFilesMap(commitIdForConfigFilesMap).build();

    Map<String, String> fetchedCommitIdsMap = new HashMap<>();
    fetchedCommitIdsMap.put("varFileId", "commitId_v1");
    TerraformPassThroughData terraformPassThroughData =
        TerraformPassThroughData.builder().fetchedCommitIdsMap(fetchedCommitIdsMap).build();

    Map<String, String> gitRevisionsOutput = terraformDestroyStepV2.getGitRevisionsOutput(
        terraformDestroyStepParameters, taskNGResponse, terraformPassThroughData);

    assertThat(gitRevisionsOutput.size()).isEqualTo(3);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetGitRevisionsOutputInheritFromPlanConfigType() {
    TerraformDestroyStepParameters terraformDestroyStepParameters =
        TerraformDestroyStepParameters.infoBuilder()
            .configuration(TerraformStepConfigurationParameters.builder().type(INHERIT_FROM_PLAN).build())
            .build();

    Map<String, String> commitIdForConfigFilesMap = new HashMap<>();
    commitIdForConfigFilesMap.put(TF_CONFIG_FILES, "commitId_1");
    commitIdForConfigFilesMap.put(TF_BACKEND_CONFIG_FILE, "commitId_2");
    TerraformTaskNGResponse taskNGResponse =
        TerraformTaskNGResponse.builder().commitIdForConfigFilesMap(commitIdForConfigFilesMap).build();

    Map<String, String> fetchedCommitIdsMap = new HashMap<>();
    fetchedCommitIdsMap.put("varFileId", "commitId_v1");
    TerraformPassThroughData terraformPassThroughData =
        TerraformPassThroughData.builder().fetchedCommitIdsMap(fetchedCommitIdsMap).build();

    Map<String, String> gitRevisionsOutput = terraformDestroyStepV2.getGitRevisionsOutput(
        terraformDestroyStepParameters, taskNGResponse, terraformPassThroughData);

    assertThat(gitRevisionsOutput.size()).isEqualTo(2);
  }
}
