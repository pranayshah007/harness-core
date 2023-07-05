/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.terraform;

import static io.harness.rule.OwnerRule.VLICA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import io.harness.cdng.manifest.yaml.GithubStore;
import io.harness.cdng.manifest.yaml.S3StoreConfig;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.cdng.provision.terraform.functor.TerraformHumanReadablePlanFunctor;
import io.harness.cdng.provision.terraform.functor.TerraformPlanJsonFunctor;
import io.harness.cdng.provision.terraform.outcome.TerraformPlanOutcome;
import io.harness.connector.ConnectorInfoDTO;
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
import io.harness.delegate.beans.storeconfig.S3StoreTFDelegateConfig;
import io.harness.delegate.task.git.GitFetchFilesConfig;
import io.harness.delegate.task.git.GitFetchResponse;
import io.harness.delegate.task.terraform.InlineTerraformVarFileInfo;
import io.harness.delegate.task.terraform.RemoteTerraformVarFileInfo;
import io.harness.delegate.task.terraform.TFTaskType;
import io.harness.delegate.task.terraform.TerraformCommand;
import io.harness.delegate.task.terraform.TerraformTaskNGParameters;
import io.harness.delegate.task.terraform.TerraformTaskNGResponse;
import io.harness.delegate.task.terraform.TerraformVarFileInfo;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.UnitProgress;
import io.harness.logging.UnitStatus;
import io.harness.ng.core.EntityDetail;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepHelper;
import io.harness.steps.TaskRequestsUtils;

import software.wings.beans.GcpKmsConfig;

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
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.MockitoRule;
import org.powermock.core.classloader.annotations.PrepareForTest;

@RunWith(MockitoJUnitRunner.class)
@PrepareForTest({TaskRequestsUtils.class})
@OwnedBy(HarnessTeam.CDP)
public class TerraformPlanStepV2Test extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock private KryoSerializer kryoSerializer;
  @Mock private CDFeatureFlagHelper cdFeatureFlagHelper;
  @Mock private TerraformStepHelper terraformStepHelper;
  @Mock private TerraformConfigHelper terraformConfigHelper;
  @Mock private PipelineRbacHelper pipelineRbacHelper;
  @Mock private StepHelper stepHelper;
  @InjectMocks private TerraformPlanStepV2 terraformPlanStepV2;

  private Ambiance getAmbiance() {
    return Ambiance.newBuilder()
        .addLevels(Level.newBuilder()
                       .setIdentifier("step1")
                       .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).build())
                       .build())
        .putSetupAbstractions("accountId", "test-account")
        .putSetupAbstractions("projectIdentifier", "test-project")
        .putSetupAbstractions("orgIdentifier", "test-org")
        .build();
  }

  @Captor ArgumentCaptor<List<EntityDetail>> captor;

  @Test
  @Owner(developers = VLICA)
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
    terraformPlanStepV2.validateResources(ambiance, stepElementParameters);
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
  @Owner(developers = VLICA)
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
    terraformPlanStepV2.validateResources(ambiance, stepElementParameters);
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
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testStartChainLinkAfterRbacWithGitHubStoreConfig() {
    Ambiance ambiance = getAmbiance();
    StepInputPackage stepInputPackage = StepInputPackage.builder().build();

    TerraformStepDataGenerator.GitStoreConfig gitStoreConfigFiles =
        TerraformStepDataGenerator.GitStoreConfig.builder()
            .branch("master")
            .fetchType(FetchType.BRANCH)
            .folderPath(ParameterField.createValueField("Config/"))
            .connectoref(ParameterField.createValueField("terraform"))
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

    List<TerraformVarFileInfo> varFileInfo = new ArrayList<>();
    varFileInfo.add(InlineTerraformVarFileInfo.builder().varFileContent("var-file-inline").build());

    TerraformPlanStepParameters planStepParameters = TerraformStepDataGenerator.generateStepPlanWithVarFiles(
        StoreConfigType.GITHUB, null, gitStoreConfigFiles, null, true);

    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(planStepParameters).build();

    doReturn("test-account/test-org/test-project/id").when(terraformStepHelper).generateFullIdentifier(any(), any());
    doReturn("fileId").when(terraformStepHelper).getLatestFileId(any());
    doReturn("planName").when(terraformStepHelper).getTerraformPlanName(any(), any(), any());
    doReturn(gitFetchFilesConfig).when(terraformStepHelper).getGitFetchFilesConfig(any(), any(), any());
    doReturn(varFileInfo).when(terraformStepHelper).toTerraformVarFileInfoWithIdentifierAndManifest(any(), any());
    doReturn(varFileInfo).when(terraformStepHelper).getRemoteVarFilesInfo(any(), any());
    doReturn(false).when(terraformStepHelper).hasGitVarFiles(any());
    doReturn(false).when(terraformStepHelper).hasS3VarFiles(any());
    doReturn(EnvironmentType.NON_PROD).when(stepHelper).getEnvironmentType(any());
    doReturn(GcpKmsConfig.builder().build()).when(terraformStepHelper).getEncryptionConfig(any(), any());
    doReturn(true).when(terraformStepHelper).tfPlanEncryptionOnManager(any(), any());
    doReturn("back-content").when(terraformStepHelper).getBackendConfig(any());
    doReturn(ImmutableMap.of("KEY", ParameterField.createValueField("VAL")))
        .when(terraformStepHelper)
        .getEnvironmentVariablesMap(any());
    doReturn(true).when(cdFeatureFlagHelper).isEnabled(any(), any());
    doReturn(new HashMap<String, String>() {
      { put("PLAN", "-lock-timeout=0s"); }
    })
        .when(terraformStepHelper)
        .getTerraformCliFlags(any());

    doReturn(TaskChainResponse.builder().chainEnd(true).taskRequest(TaskRequest.newBuilder().build()).build())
        .when(terraformStepHelper)
        .executeTerraformTask(any(), any(), any(), any(), any(), any());

    ArgumentCaptor<TerraformTaskNGParameters> tfTaskNGParametersArgumentCaptor =
        ArgumentCaptor.forClass(TerraformTaskNGParameters.class);
    ArgumentCaptor<TerraformPassThroughData> tfPassThroughDataArgumentCaptor =
        ArgumentCaptor.forClass(TerraformPassThroughData.class);

    terraformPlanStepV2.startChainLinkAfterRbac(ambiance, stepElementParameters, stepInputPackage);

    verify(terraformStepHelper)
        .executeTerraformTask(tfTaskNGParametersArgumentCaptor.capture(), any(), any(),
            tfPassThroughDataArgumentCaptor.capture(), any(), any());

    assertThat(tfTaskNGParametersArgumentCaptor.getValue()).isNotNull();
    TerraformTaskNGParameters taskParameters = tfTaskNGParametersArgumentCaptor.getValue();
    assertThat(taskParameters).isNotNull();
    assertThat(taskParameters.getTaskType()).isEqualTo(TFTaskType.PLAN);
    assertThat(taskParameters.getPlanName()).isEqualTo("planName");
    assertThat(taskParameters.isSkipTerraformRefresh()).isFalse();
    assertThat(taskParameters.getTerraformCommand()).isEqualTo(TerraformCommand.APPLY);
    assertThat(taskParameters.getTerraformCommandFlags().get("PLAN")).isEqualTo("-lock-timeout=0s");
    assertThat(taskParameters.getBackendConfig()).isEqualTo("back-content");
    assertThat(taskParameters.getConfigFile()).isNotNull();
    assertThat(((InlineTerraformVarFileInfo) taskParameters.getVarFileInfos().get(0)).getVarFileContent())
        .isEqualTo("var-file-inline");

    TerraformPassThroughData terraformPassThroughData = tfPassThroughDataArgumentCaptor.getValue();
    assertThat(terraformPassThroughData).isNotNull();
    assertThat(terraformPassThroughData.getTerraformTaskNGParametersBuilder()).isNotNull();
    assertThat(terraformPassThroughData.hasGitFiles).isFalse();
    assertThat(terraformPassThroughData.hasS3Files).isFalse();
    assertThat(terraformPassThroughData.getUnitProgresses()).isEmpty();
    verify(terraformStepHelper, times(0)).fetchRemoteVarFiles(any(), any(), any(), any(), any(), any());
    verify(terraformStepHelper, times(1)).getEncryptionConfig(any(), any());
    assertThat(taskParameters.isEncryptDecryptPlanForHarnessSMOnManager()).isTrue();
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testStartChainLinkAfterRbacWithGitHubStoreConfigAndGitHubRemoteVarFile() {
    Ambiance ambiance = getAmbiance();
    StepInputPackage stepInputPackage = StepInputPackage.builder().build();

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

    RemoteTerraformVarFileSpec remoteTerraformVarFileSpec = new RemoteTerraformVarFileSpec();

    StoreConfig storeVarFiles =
        GithubStore.builder()
            .branch(ParameterField.createValueField(gitStoreVarFiles.getBranch()))
            .gitFetchType(gitStoreVarFiles.getFetchType())
            .folderPath(ParameterField.createValueField(gitStoreVarFiles.getFolderPath().getValue()))
            .connectorRef(ParameterField.createValueField(gitStoreVarFiles.getConnectoref().getValue()))
            .build();
    remoteTerraformVarFileSpec.setStore(
        StoreConfigWrapper.builder().spec(storeVarFiles).type(StoreConfigType.GITHUB).build());

    TerraformPlanStepParameters planStepParameters =
        TerraformStepDataGenerator.generateStepPlanFile(StoreConfigType.GITHUB, gitStoreConfigFiles, gitStoreVarFiles);

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
    varFileInfo.add(RemoteTerraformVarFileInfo.builder().gitFetchFilesConfig(gitFetchFilesConfig).build());

    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(planStepParameters).build();

    doReturn("test-account/test-org/test-project/id").when(terraformStepHelper).generateFullIdentifier(any(), any());
    doReturn("fileId").when(terraformStepHelper).getLatestFileId(any());
    doReturn("planName").when(terraformStepHelper).getTerraformPlanName(any(), any(), any());
    doReturn(gitFetchFilesConfig).when(terraformStepHelper).getGitFetchFilesConfig(any(), any(), any());
    doReturn(varFileInfo).when(terraformStepHelper).toTerraformVarFileInfoWithIdentifierAndManifest(any(), any());
    doReturn(EnvironmentType.NON_PROD).when(stepHelper).getEnvironmentType(any());
    doReturn("back-content").when(terraformStepHelper).getBackendConfig(any());
    doReturn(ImmutableMap.of("KEY", ParameterField.createValueField("VAL")))
        .when(terraformStepHelper)
        .getEnvironmentVariablesMap(any());
    doReturn(true).when(cdFeatureFlagHelper).isEnabled(any(), any());
    doReturn(new HashMap<String, String>() {
      { put("PLAN", "-lock-timeout=0s"); }
    })
        .when(terraformStepHelper)
        .getTerraformCliFlags(any());

    doReturn(varFileInfo).when(terraformStepHelper).getRemoteVarFilesInfo(any(), any());
    doReturn(true).when(terraformStepHelper).hasGitVarFiles(any());
    doReturn(false).when(terraformStepHelper).hasS3VarFiles(any());

    doReturn(TaskChainResponse.builder().chainEnd(true).taskRequest(TaskRequest.newBuilder().build()).build())
        .when(terraformStepHelper)
        .fetchRemoteVarFiles(any(), any(), any(), any(), any(), any());

    ArgumentCaptor<TerraformPassThroughData> tfPassThroughDataArgumentCaptor =
        ArgumentCaptor.forClass(TerraformPassThroughData.class);

    terraformPlanStepV2.startChainLinkAfterRbac(ambiance, stepElementParameters, stepInputPackage);

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
    assertThat(taskParameters.getTaskType()).isEqualTo(TFTaskType.PLAN);
    assertThat(taskParameters.getPlanName()).isEqualTo("planName");
    assertThat(taskParameters.isSkipTerraformRefresh()).isFalse();
    assertThat(taskParameters.getTerraformCommand()).isEqualTo(TerraformCommand.APPLY);
    assertThat(taskParameters.getTerraformCommandFlags().get("PLAN")).isEqualTo("-lock-timeout=0s");
    assertThat(taskParameters.getBackendConfig()).isEqualTo("back-content");
    assertThat(taskParameters.getConfigFile()).isNotNull();
    assertThat(((RemoteTerraformVarFileInfo) taskParameters.getVarFileInfos().get(0)).getGitFetchFilesConfig())
        .isNotNull();
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testStartChainLinkAfterRbacWithGitHubStoreConfigAndS3RemoteVarFile() {
    Ambiance ambiance = getAmbiance();
    StepInputPackage stepInputPackage = StepInputPackage.builder().build();

    TerraformStepDataGenerator.GitStoreConfig gitStoreConfigFiles =
        TerraformStepDataGenerator.GitStoreConfig.builder()
            .branch("master")
            .fetchType(FetchType.BRANCH)
            .folderPath(ParameterField.createValueField("Config/"))
            .connectoref(ParameterField.createValueField("terraform"))
            .build();

    S3StoreConfig s3StoreConfigFiles = S3StoreConfig.builder()
                                           .connectorRef(ParameterField.createValueField("connectorRef"))
                                           .region(ParameterField.createValueField("region"))
                                           .bucketName(ParameterField.createValueField("bucket"))
                                           .folderPath(ParameterField.createValueField("terraform"))
                                           .build();

    RemoteTerraformVarFileSpec remoteTerraformVarFileSpec = new RemoteTerraformVarFileSpec();
    remoteTerraformVarFileSpec.setStore(
        StoreConfigWrapper.builder().spec(s3StoreConfigFiles).type(StoreConfigType.S3).build());

    TerraformPlanStepParameters planStepParameters = TerraformStepDataGenerator.generateStepPlanWithVarFiles(
        StoreConfigType.GITHUB, StoreConfigType.S3, gitStoreConfigFiles, s3StoreConfigFiles, false);

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
    varFileInfo.add(RemoteTerraformVarFileInfo.builder()
                        .filestoreFetchFilesConfig(S3StoreTFDelegateConfig.builder()
                                                       .bucketName("test-bucket")
                                                       .region("test-region")
                                                       .path("test-path")
                                                       .connectorDTO(ConnectorInfoDTO.builder().build())
                                                       .build())
                        .build());

    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(planStepParameters).build();

    doReturn("test-account/test-org/test-project/id").when(terraformStepHelper).generateFullIdentifier(any(), any());
    doReturn("fileId").when(terraformStepHelper).getLatestFileId(any());
    doReturn("planName").when(terraformStepHelper).getTerraformPlanName(any(), any(), any());
    doReturn(gitFetchFilesConfig).when(terraformStepHelper).getGitFetchFilesConfig(any(), any(), any());
    doReturn(varFileInfo).when(terraformStepHelper).toTerraformVarFileInfoWithIdentifierAndManifest(any(), any());
    doReturn(EnvironmentType.NON_PROD).when(stepHelper).getEnvironmentType(any());
    doReturn("back-content").when(terraformStepHelper).getBackendConfig(any());
    doReturn(ImmutableMap.of("KEY", ParameterField.createValueField("VAL")))
        .when(terraformStepHelper)
        .getEnvironmentVariablesMap(any());
    doReturn(true).when(cdFeatureFlagHelper).isEnabled(any(), any());
    doReturn(new HashMap<String, String>() {
      { put("PLAN", "-lock-timeout=0s"); }
    })
        .when(terraformStepHelper)
        .getTerraformCliFlags(any());

    doReturn(varFileInfo).when(terraformStepHelper).getRemoteVarFilesInfo(any(), any());
    doReturn(true).when(terraformStepHelper).hasGitVarFiles(any());
    doReturn(true).when(terraformStepHelper).hasS3VarFiles(any());

    doReturn(TaskChainResponse.builder().chainEnd(true).taskRequest(TaskRequest.newBuilder().build()).build())
        .when(terraformStepHelper)
        .fetchRemoteVarFiles(any(), any(), any(), any(), any(), any());

    ArgumentCaptor<TerraformPassThroughData> tfPassThroughDataArgumentCaptor =
        ArgumentCaptor.forClass(TerraformPassThroughData.class);

    terraformPlanStepV2.startChainLinkAfterRbac(ambiance, stepElementParameters, stepInputPackage);

    verify(terraformStepHelper)
        .fetchRemoteVarFiles(tfPassThroughDataArgumentCaptor.capture(), any(), any(), any(), any(), any());

    TerraformPassThroughData terraformPassThroughData = tfPassThroughDataArgumentCaptor.getValue();
    assertThat(terraformPassThroughData).isNotNull();
    assertThat(terraformPassThroughData.getTerraformTaskNGParametersBuilder()).isNotNull();
    assertThat(terraformPassThroughData.hasGitFiles).isTrue();
    assertThat(terraformPassThroughData.hasS3Files).isTrue();
    assertThat(terraformPassThroughData.getUnitProgresses()).isEmpty();
    verify(terraformStepHelper, times(1)).fetchRemoteVarFiles(any(), any(), any(), any(), any(), any());
    verify(terraformStepHelper, times(0)).executeTerraformTask(any(), any(), any(), any(), any(), any());

    TerraformTaskNGParameters taskParameters = terraformPassThroughData.getTerraformTaskNGParametersBuilder().build();
    assertThat(taskParameters.getTaskType()).isEqualTo(TFTaskType.PLAN);
    assertThat(taskParameters.getPlanName()).isEqualTo("planName");
    assertThat(taskParameters.isSkipTerraformRefresh()).isFalse();
    assertThat(taskParameters.getTerraformCommand()).isEqualTo(TerraformCommand.APPLY);
    assertThat(taskParameters.getTerraformCommandFlags().get("PLAN")).isEqualTo("-lock-timeout=0s");
    assertThat(taskParameters.getBackendConfig()).isEqualTo("back-content");
    assertThat(taskParameters.getConfigFile()).isNotNull();
    assertThat(((RemoteTerraformVarFileInfo) taskParameters.getVarFileInfos().get(0)).getFilestoreFetchFilesConfig())
        .isNotNull();
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testObtainTaskAfterRbacWithGithubStoreWhenTFCloudCli() {
    Ambiance ambiance = getAmbiance();
    StepInputPackage stepInputPackage = StepInputPackage.builder().build();

    TerraformStepDataGenerator.GitStoreConfig gitStoreConfigFiles =
        TerraformStepDataGenerator.GitStoreConfig.builder()
            .branch("master")
            .fetchType(FetchType.BRANCH)
            .folderPath(ParameterField.createValueField("Config/"))
            .connectoref(ParameterField.createValueField("terraform"))
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

    TerraformPlanStepParameters planStepParameters = TerraformStepDataGenerator.generateStepPlanWithVarFiles(
        StoreConfigType.GITHUB, null, gitStoreConfigFiles, null, true);

    planStepParameters.configuration.isTerraformCloudCli.setValue(true);

    List<TerraformVarFileInfo> varFileInfo = new ArrayList<>();
    varFileInfo.add(InlineTerraformVarFileInfo.builder().varFileContent("var-file-inline").build());
    doReturn(varFileInfo).when(terraformStepHelper).getRemoteVarFilesInfo(any(), any());
    doReturn(false).when(terraformStepHelper).hasGitVarFiles(any());
    doReturn(false).when(terraformStepHelper).hasS3VarFiles(any());

    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(planStepParameters).build();

    doReturn("test-account/test-org/test-project/id").when(terraformStepHelper).generateFullIdentifier(any(), any());
    doReturn("fileId").when(terraformStepHelper).getLatestFileId(any());
    doReturn("planName").when(terraformStepHelper).getTerraformPlanName(any(), any(), any());
    doReturn(gitFetchFilesConfig).when(terraformStepHelper).getGitFetchFilesConfig(any(), any(), any());
    doReturn(varFileInfo).when(terraformStepHelper).toTerraformVarFileInfoWithIdentifierAndManifest(any(), any());
    doReturn(EnvironmentType.NON_PROD).when(stepHelper).getEnvironmentType(any());
    doReturn(GcpKmsConfig.builder().build()).when(terraformStepHelper).getEncryptionConfig(any(), any());
    doReturn(true).when(terraformStepHelper).tfPlanEncryptionOnManager(any(), any());
    doReturn("back-content").when(terraformStepHelper).getBackendConfig(any());
    doReturn(ImmutableMap.of("KEY", ParameterField.createValueField("VAL")))
        .when(terraformStepHelper)
        .getEnvironmentVariablesMap(any());
    doReturn(true).when(cdFeatureFlagHelper).isEnabled(any(), any());
    doReturn(new HashMap<String, String>() {
      { put("PLAN", "-lock-timeout=0s"); }
    })
        .when(terraformStepHelper)
        .getTerraformCliFlags(any());

    doReturn(TaskChainResponse.builder().chainEnd(true).taskRequest(TaskRequest.newBuilder().build()).build())
        .when(terraformStepHelper)
        .executeTerraformTask(any(), any(), any(), any(), any(), any());

    ArgumentCaptor<TerraformTaskNGParameters> tfTaskNGParametersArgumentCaptor =
        ArgumentCaptor.forClass(TerraformTaskNGParameters.class);
    ArgumentCaptor<TerraformPassThroughData> tfPassThroughDataArgumentCaptor =
        ArgumentCaptor.forClass(TerraformPassThroughData.class);

    terraformPlanStepV2.startChainLinkAfterRbac(ambiance, stepElementParameters, stepInputPackage);

    verify(terraformStepHelper)
        .executeTerraformTask(tfTaskNGParametersArgumentCaptor.capture(), any(), any(),
            tfPassThroughDataArgumentCaptor.capture(), any(), any());

    assertThat(tfTaskNGParametersArgumentCaptor.getValue()).isNotNull();
    TerraformTaskNGParameters taskParameters = tfTaskNGParametersArgumentCaptor.getValue();
    assertThat(taskParameters.getTaskType()).isEqualTo(TFTaskType.PLAN);
    assertThat(taskParameters.getTerraformCommand()).isEqualTo(TerraformCommand.APPLY);
    assertThat(taskParameters.getBackendConfig()).isEqualTo("back-content");
    assertThat(taskParameters.getConfigFile()).isNotNull();
    assertThat(((InlineTerraformVarFileInfo) taskParameters.getVarFileInfos().get(0)).getVarFileContent())
        .isEqualTo("var-file-inline");
    assertThat(taskParameters.isSaveTerraformStateJson()).isFalse();
    assertThat(taskParameters.isSaveTerraformHumanReadablePlan()).isFalse();
    assertThat(taskParameters.getEncryptionConfig()).isNull();
    assertThat(taskParameters.getWorkspace()).isNull();
    assertThat(taskParameters.isTerraformCloudCli()).isTrue();
    assertThat(taskParameters.isSkipTerraformRefresh()).isFalse();
    assertThat(taskParameters.getTerraformCommandFlags().get("PLAN")).isEqualTo("-lock-timeout=0s");

    TerraformPassThroughData terraformPassThroughData = tfPassThroughDataArgumentCaptor.getValue();
    assertThat(terraformPassThroughData).isNotNull();
    assertThat(terraformPassThroughData.getTerraformTaskNGParametersBuilder()).isNotNull();
    assertThat(terraformPassThroughData.hasGitFiles).isFalse();
    assertThat(terraformPassThroughData.hasS3Files).isFalse();
    assertThat(terraformPassThroughData.getUnitProgresses()).isEmpty();
    verify(terraformStepHelper, times(0)).fetchRemoteVarFiles(any(), any(), any(), any(), any(), any());
    verify(terraformStepHelper, times(0)).getEncryptionConfig(any(), any());
    assertThat(taskParameters.isEncryptDecryptPlanForHarnessSMOnManager()).isFalse();
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testStartChainLinkAfterRbacArtifactoryStore() {
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
    varFileInfo.add(InlineTerraformVarFileInfo.builder().varFileContent("var-file-inline").build());

    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(planStepParameters).build();
    StepInputPackage stepInputPackage = StepInputPackage.builder().build();
    doReturn("test-account/test-org/test-project/id").when(terraformStepHelper).generateFullIdentifier(any(), any());
    doReturn("fileId").when(terraformStepHelper).getLatestFileId(any());
    doReturn("planName").when(terraformStepHelper).getTerraformPlanName(any(), any(), any());
    doReturn(artifactoryStoreDelegateConfig)
        .when(terraformStepHelper)
        .getFileStoreFetchFilesConfig(any(), any(), any());
    doReturn(varFileInfo).when(terraformStepHelper).toTerraformVarFileInfoWithIdentifierAndManifest(any(), any());
    doReturn(varFileInfo).when(terraformStepHelper).getRemoteVarFilesInfo(any(), any());
    doReturn(false).when(terraformStepHelper).hasGitVarFiles(any());
    doReturn(false).when(terraformStepHelper).hasS3VarFiles(any());
    doReturn(EnvironmentType.NON_PROD).when(stepHelper).getEnvironmentType(any());
    doReturn("back-content").when(terraformStepHelper).getBackendConfig(any());
    doReturn(ImmutableMap.of("KEY", ParameterField.createValueField("VAL")))
        .when(terraformStepHelper)
        .getEnvironmentVariablesMap(any());

    doReturn(TaskChainResponse.builder().chainEnd(true).taskRequest(TaskRequest.newBuilder().build()).build())
        .when(terraformStepHelper)
        .executeTerraformTask(any(), any(), any(), any(), any(), any());

    ArgumentCaptor<TerraformTaskNGParameters> tfTaskNGParametersArgumentCaptor =
        ArgumentCaptor.forClass(TerraformTaskNGParameters.class);
    ArgumentCaptor<TerraformPassThroughData> tfPassThroughDataArgumentCaptor =
        ArgumentCaptor.forClass(TerraformPassThroughData.class);

    terraformPlanStepV2.startChainLinkAfterRbac(ambiance, stepElementParameters, stepInputPackage);

    verify(terraformStepHelper)
        .executeTerraformTask(tfTaskNGParametersArgumentCaptor.capture(), any(), any(),
            tfPassThroughDataArgumentCaptor.capture(), any(), any());

    assertThat(tfTaskNGParametersArgumentCaptor.getValue()).isNotNull();

    TerraformTaskNGParameters taskParameters = tfTaskNGParametersArgumentCaptor.getValue();
    assertThat(taskParameters.getTaskType()).isEqualTo(TFTaskType.PLAN);
    assertThat(taskParameters.getPlanName()).isEqualTo("planName");
    assertThat(taskParameters.getFileStoreConfigFiles() instanceof ArtifactoryStoreDelegateConfig).isTrue();

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
  public void testGetStepParametersClass() {
    assertThat(terraformPlanStepV2.getStepParametersClass()).isEqualTo(StepElementParameters.class);
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testFinalizeExecutionWithSecurityContext() throws Exception {
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

    TerraformPassThroughData terraformPassThroughData =
        TerraformPassThroughData.builder().hasGitFiles(false).hasS3Files(false).build();

    StepResponse stepResponse = terraformPlanStepV2.finalizeExecutionWithSecurityContext(
        ambiance, stepElementParameters, terraformPassThroughData, () -> terraformTaskNGResponse);

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getStepOutcomes()).isNotNull();
    assertThat(stepResponse.getStepOutcomes()).hasSize(1);
    StepResponse.StepOutcome stepOutcome = ((List<StepResponse.StepOutcome>) stepResponse.getStepOutcomes()).get(0);
    assertThat(stepOutcome.getOutcome()).isInstanceOf(TerraformPlanOutcome.class);
    assertThat(((TerraformPlanOutcome) (stepOutcome.getOutcome())).getDetailedExitCode()).isEqualTo(2);

    verify(terraformStepHelper, times(1)).saveTerraformInheritOutput(any(), any(), any(), any());
    verify(terraformStepHelper, times(1)).updateParentEntityIdAndVersion(any(), any());
    verify(terraformStepHelper)
        .saveTerraformPlanExecutionDetails(eq(ambiance), eq(terraformTaskNGResponse), eq("id"), any());
    verify(terraformStepHelper, times(1)).getRevisionsMap(any(TerraformPassThroughData.class), any());
    verify(terraformStepHelper).addTerraformRevisionOutcomeIfRequired(any(), any());
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testFinalizeExecutionWithSecurityContextAndErrorPTD() throws Exception {
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

    StepResponse stepResponse = terraformPlanStepV2.finalizeExecutionWithSecurityContext(
        ambiance, stepElementParameters, terraformPassThroughData, () -> terraformTaskNGResponse);

    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
    assertThat(stepResponse.getFailureInfo().getErrorMessage())
        .isEqualTo("Exception Error in executing terraform plan task or fetching remote var files");
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testFinalizeExecutionWithSecurityContextWithMultipleCommandUnits() throws Exception {
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

    StepResponse stepResponse = terraformPlanStepV2.finalizeExecutionWithSecurityContext(
        ambiance, stepElementParameters, terraformPassThroughData, () -> terraformTaskNGResponse);

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getUnitProgressList().size()).isEqualTo(2);

    assertThat(stepResponse.getStepOutcomes()).isNotNull();
    assertThat(stepResponse.getStepOutcomes()).hasSize(1);
    StepResponse.StepOutcome stepOutcome = ((List<StepResponse.StepOutcome>) stepResponse.getStepOutcomes()).get(0);
    assertThat(stepOutcome.getOutcome()).isInstanceOf(TerraformPlanOutcome.class);
    assertThat(((TerraformPlanOutcome) (stepOutcome.getOutcome())).getDetailedExitCode()).isEqualTo(2);

    verify(terraformStepHelper, times(1)).saveTerraformInheritOutput(any(), any(), any(), any());
    verify(terraformStepHelper, times(1)).updateParentEntityIdAndVersion(any(), any());
    verify(terraformStepHelper)
        .saveTerraformPlanExecutionDetails(eq(ambiance), eq(terraformTaskNGResponse), eq("id"), any());
    verify(terraformStepHelper, times(1)).getRevisionsMap(any(TerraformPassThroughData.class), any());
    verify(terraformStepHelper).addTerraformRevisionOutcomeIfRequired(any(), any());
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testFinalizeExecutionWithSecurityContextDifferentStatus() throws Exception {
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

    TerraformPassThroughData terraformPassThroughData =
        TerraformPassThroughData.builder().hasS3Files(false).hasGitFiles(false).build();

    StepResponse stepResponse = terraformPlanStepV2.finalizeExecutionWithSecurityContext(
        ambiance, stepElementParameters, terraformPassThroughData, () -> terraformTaskNGResponseFailure);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
    assertThat(stepResponse.getStepOutcomes()).isNotNull();

    TerraformTaskNGResponse terraformTaskNGResponseRunning = TerraformTaskNGResponse.builder()
                                                                 .commandExecutionStatus(CommandExecutionStatus.RUNNING)
                                                                 .unitProgressData(unitProgressData)
                                                                 .build();
    stepResponse = terraformPlanStepV2.finalizeExecutionWithSecurityContext(
        ambiance, stepElementParameters, terraformPassThroughData, () -> terraformTaskNGResponseRunning);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.RUNNING);
    assertThat(stepResponse.getStepOutcomes()).isNotNull();

    TerraformTaskNGResponse terraformTaskNGResponseQueued = TerraformTaskNGResponse.builder()
                                                                .commandExecutionStatus(CommandExecutionStatus.QUEUED)
                                                                .unitProgressData(unitProgressData)
                                                                .build();
    stepResponse = terraformPlanStepV2.finalizeExecutionWithSecurityContext(
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
      terraformPlanStepV2.finalizeExecutionWithSecurityContext(
          ambiance, stepElementParameters, terraformPassThroughData, () -> terraformTaskNGResponseSkipped);
    } catch (InvalidRequestException invalidRequestException) {
      assertThat(invalidRequestException.getMessage()).isEqualTo(message);
    }
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testFinalizeExecutionWithSecurityContextWithTfPlanJsonFileId() throws Exception {
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

    TerraformPassThroughData terraformPassThroughData =
        TerraformPassThroughData.builder().hasS3Files(false).hasGitFiles(false).build();

    StepResponse stepResponse = terraformPlanStepV2.finalizeExecutionWithSecurityContext(
        ambiance, stepElementParameters, terraformPassThroughData, () -> ngResponse);

    verify(terraformStepHelper).saveTerraformPlanJsonOutput(ambiance, ngResponse, "provisioner1");
    verify(terraformStepHelper)
        .saveTerraformPlanExecutionDetails(eq(ambiance), eq(ngResponse), eq("provisioner1"), any());
    verify(terraformStepHelper, times(1)).getRevisionsMap(any(TerraformPassThroughData.class), any());
    verify(terraformStepHelper).addTerraformRevisionOutcomeIfRequired(any(), any());
    assertThat(stepResponse.getStepOutcomes()).hasSize(1);
    StepResponse.StepOutcome planOutcome = stepResponse.getStepOutcomes().iterator().next();
    assertThat(planOutcome.getName()).isEqualTo(TerraformPlanOutcome.OUTCOME_NAME);
    assertThat(planOutcome.getOutcome()).isInstanceOf(TerraformPlanOutcome.class);
    TerraformPlanOutcome terraformPlanOutcome = (TerraformPlanOutcome) planOutcome.getOutcome();
    assertThat(terraformPlanOutcome.getJsonFilePath())
        .isEqualTo(TerraformPlanJsonFunctor.getExpression("step1", "outputPlanJson"));
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testFinalizeExecutionWithSecurityContextWithTfHumanReadableFileId() throws Exception {
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

    TerraformPassThroughData terraformPassThroughData =
        TerraformPassThroughData.builder().hasS3Files(false).hasGitFiles(false).build();

    StepResponse stepResponse = terraformPlanStepV2.finalizeExecutionWithSecurityContext(
        ambiance, stepElementParameters, terraformPassThroughData, () -> ngResponse);

    verify(terraformStepHelper).saveTerraformPlanHumanReadableOutput(ambiance, ngResponse, "provisioner1");
    verify(terraformStepHelper)
        .saveTerraformPlanExecutionDetails(eq(ambiance), eq(ngResponse), eq("provisioner1"), any());

    assertThat(stepResponse.getStepOutcomes()).hasSize(1);
    StepResponse.StepOutcome planOutcome = stepResponse.getStepOutcomes().iterator().next();
    assertThat(planOutcome.getName()).isEqualTo(TerraformPlanOutcome.OUTCOME_NAME);
    assertThat(planOutcome.getOutcome()).isInstanceOf(TerraformPlanOutcome.class);
    TerraformPlanOutcome terraformPlanOutcome = (TerraformPlanOutcome) planOutcome.getOutcome();
    assertThat(terraformPlanOutcome.getHumanReadableFilePath())
        .isEqualTo(TerraformHumanReadablePlanFunctor.getExpression("step1", "humanReadablePlan"));
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

    S3StoreConfig s3StoreConfigFiles = S3StoreConfig.builder()
                                           .connectorRef(ParameterField.createValueField("connectorRef"))
                                           .region(ParameterField.createValueField("region"))
                                           .bucketName(ParameterField.createValueField("bucket"))
                                           .folderPath(ParameterField.createValueField("terraform"))
                                           .build();

    RemoteTerraformVarFileSpec remoteTerraformVarFileSpec = new RemoteTerraformVarFileSpec();
    remoteTerraformVarFileSpec.setStore(
        StoreConfigWrapper.builder().spec(s3StoreConfigFiles).type(StoreConfigType.S3).build());

    TerraformPlanStepParameters planStepParameters = TerraformStepDataGenerator.generateStepPlanWithVarFiles(
        StoreConfigType.GITHUB, StoreConfigType.S3, gitStoreConfigFiles, s3StoreConfigFiles, false);

    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(planStepParameters).build();

    TerraformPassThroughData terraformPassThroughData =
        TerraformPassThroughData.builder()
            .hasS3Files(true)
            .terraformTaskNGParametersBuilder(TerraformTaskNGParameters.builder())
            .build();

    doReturn(TaskChainResponse.builder().taskRequest(TaskRequest.newBuilder().build()).build())
        .when(terraformStepHelper)
        .executeNextLink(eq(ambiance), any(), eq(terraformPassThroughData), any(), eq(stepElementParameters), any());

    GitFetchResponse gitFetchResponse = GitFetchResponse.builder().build();

    terraformPlanStepV2.executeNextLinkWithSecurityContext(ambiance, stepElementParameters,
        StepInputPackage.builder().build(), terraformPassThroughData, () -> gitFetchResponse);

    verify(terraformStepHelper, times(1)).executeNextLink(any(), any(), any(), any(), any(), any());
  }
}
