/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.terraform;

import static io.harness.cdng.provision.terraform.TerraformPlanCommand.APPLY;
import static io.harness.cdng.provision.terraform.TerraformPlanCommand.DESTROY;
import static io.harness.cdng.provision.terraform.TerraformStepDataGenerator.getConnectorInfoDTO;
import static io.harness.cdng.provision.terraform.TerraformStepHelper.TF_BACKEND_CONFIG_FILE;
import static io.harness.cdng.provision.terraform.TerraformStepHelper.TF_CONFIG_FILES;
import static io.harness.delegate.beans.connector.ConnectorType.GITHUB;
import static io.harness.delegate.task.terraform.TerraformTaskNGParameters.TerraformTaskNGParametersBuilder;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.AKHIL_PANDEY;
import static io.harness.rule.OwnerRule.BUHA;
import static io.harness.rule.OwnerRule.JELENA;
import static io.harness.rule.OwnerRule.NAMAN_TALAYCHA;
import static io.harness.rule.OwnerRule.NGONZALEZ;
import static io.harness.rule.OwnerRule.ROHITKARELIA;
import static io.harness.rule.OwnerRule.SATYAM;
import static io.harness.rule.OwnerRule.SOURABH;
import static io.harness.rule.OwnerRule.TATHAGAT;
import static io.harness.rule.OwnerRule.TMACARI;
import static io.harness.rule.OwnerRule.VLICA;

import static com.mongodb.assertions.Assertions.assertTrue;
import static java.lang.String.format;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EnvironmentType;
import io.harness.beans.IdentifierRef;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.fileservice.FileServiceClient;
import io.harness.cdng.fileservice.FileServiceClientFactory;
import io.harness.cdng.k8s.K8sStepHelper;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.yaml.ArtifactoryStorageConfigDTO;
import io.harness.cdng.manifest.yaml.ArtifactoryStoreConfig;
import io.harness.cdng.manifest.yaml.BitBucketStoreDTO;
import io.harness.cdng.manifest.yaml.GitLabStoreDTO;
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.cdng.manifest.yaml.GitStoreConfigDTO;
import io.harness.cdng.manifest.yaml.GitStoreDTO;
import io.harness.cdng.manifest.yaml.GithubStore;
import io.harness.cdng.manifest.yaml.GithubStoreDTO;
import io.harness.cdng.manifest.yaml.S3StorageConfigDTO;
import io.harness.cdng.manifest.yaml.S3StoreConfig;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.cdng.manifest.yaml.storeConfig.moduleSource.ModuleSource;
import io.harness.cdng.provision.terraform.executions.TFPlanExecutionDetailsKey;
import io.harness.cdng.provision.terraform.executions.TerraformPlanExectionDetailsService;
import io.harness.cdng.provision.terraform.executions.TerraformPlanExecutionDetails;
import io.harness.cdng.provision.terraform.outcome.TerraformGitRevisionOutcome;
import io.harness.cdng.provision.terraform.output.TerraformHumanReadablePlanOutput;
import io.harness.cdng.provision.terraform.output.TerraformPlanJsonOutput;
import io.harness.common.ParameterFieldHelper;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.validator.scmValidators.GitConfigAuthenticationInfoHelper;
import io.harness.delegate.beans.FileBucket;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.aws.s3.AwsS3FetchFileDelegateConfig;
import io.harness.delegate.beans.aws.s3.AwsS3FetchFilesResponse;
import io.harness.delegate.beans.aws.s3.AwsS3FetchFilesTaskParams;
import io.harness.delegate.beans.aws.s3.S3FileDetailRequest;
import io.harness.delegate.beans.aws.s3.S3FileDetailResponse;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthType;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthenticationDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryUsernamePasswordAuthDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.storeconfig.ArtifactoryStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.S3StoreTFDelegateConfig;
import io.harness.delegate.task.filestore.FileStoreFetchFilesConfig;
import io.harness.delegate.task.git.GitFetchFilesConfig;
import io.harness.delegate.task.git.GitFetchRequest;
import io.harness.delegate.task.git.GitFetchResponse;
import io.harness.delegate.task.terraform.InlineTerraformVarFileInfo;
import io.harness.delegate.task.terraform.RemoteTerraformBackendConfigFileInfo;
import io.harness.delegate.task.terraform.RemoteTerraformVarFileInfo;
import io.harness.delegate.task.terraform.TFTaskType;
import io.harness.delegate.task.terraform.TerraformBackendConfigFileInfo;
import io.harness.delegate.task.terraform.TerraformCommandUnit;
import io.harness.delegate.task.terraform.TerraformTaskNGParameters;
import io.harness.delegate.task.terraform.TerraformTaskNGResponse;
import io.harness.delegate.task.terraform.TerraformVarFileInfo;
import io.harness.delegate.task.terraform.provider.TerraformAwsProviderCredentialDelegateInfo;
import io.harness.delegate.task.terraform.provider.TerraformProviderCredentialDelegateInfo;
import io.harness.delegate.task.terraform.provider.TerraformProviderType;
import io.harness.encryption.SecretRefData;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidRequestException;
import io.harness.filesystem.FileIo;
import io.harness.git.model.CommitResult;
import io.harness.git.model.FetchFilesResult;
import io.harness.git.model.GitFile;
import io.harness.logging.UnitProgress;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.api.NGEncryptedDataService;
import io.harness.ng.core.api.SecretCrudService;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretTextSpecDTO;
import io.harness.persistence.HPersistence;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.pms.yaml.ParameterField;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.NGSecretManagerMetadata;
import io.harness.secretmanagerclient.SecretType;
import io.harness.secretmanagerclient.ValueType;
import io.harness.secretmanagerclient.dto.LocalConfigDTO;
import io.harness.secretmanagerclient.dto.VaultConfigDTO;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.security.encryption.EncryptionConfig;
import io.harness.security.encryption.EncryptionType;
import io.harness.steps.StepHelper;
import io.harness.steps.TaskRequestsUtils;

import software.wings.beans.GcpKmsConfig;
import software.wings.beans.VaultConfig;

import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.powermock.api.mockito.PowerMockito;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(HarnessTeam.CDP)
public class TerraformStepHelperTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private HPersistence mockPersistence;
  @Mock private K8sStepHelper mockK8sStepHelper;
  @Mock private ExecutionSweepingOutputService mockExecutionSweepingOutputService;
  @Mock private GitConfigAuthenticationInfoHelper mockGitConfigAuthenticationInfoHelper;

  @Mock TerraformPlanExectionDetailsService terraformPlanExectionDetailsService;
  @Mock private FileServiceClientFactory mockFileServiceFactory;
  @Mock private FileServiceClient mockFileService;
  @Mock private SecretManagerClientService mockSecretManagerClientService;
  @Mock private TerraformConfigDAL terraformConfigDAL;
  @Mock private CDStepHelper cdStepHelper;
  @Mock private CDFeatureFlagHelper cdFeatureFlagHelper;
  @Mock private NGEncryptedDataService ngEncryptedDataService;
  @Mock private SecretCrudService ngSecretService;
  @Mock private StepHelper stepHelper;
  @Mock private CDExpressionResolver cdExpressionResolver;

  @InjectMocks private TerraformStepHelper helper;
  public static final String GLOBAL_ACCOUNT_ID = "__GLOBAL_ACCOUNT_ID__";

  private Ambiance getAmbiance() {
    return Ambiance.newBuilder()
        .putSetupAbstractions("accountId", "test-account")
        .putSetupAbstractions("projectIdentifier", "test-project")
        .putSetupAbstractions("orgIdentifier", "test-org")
        .setPlanExecutionId("exec_id")
        .setPlanId("plan_id")
        .build();
  }

  @Before
  public void setup() throws IOException {
    doReturn(mockFileService).when(mockFileServiceFactory).get();
    Call<RestResponse<Object>> mockFileServiceResponse = mock(Call.class);
    doReturn(mockFileServiceResponse).when(mockFileService).getLatestFileId(anyString(), any(FileBucket.class));
    doReturn(mockFileServiceResponse)
        .when(mockFileService)
        .updateParentEntityIdAndVersion(anyString(), anyString(), any(FileBucket.class));
    doReturn(mockFileServiceResponse).when(mockFileService).deleteFile(anyString(), any(FileBucket.class));
    doReturn(Response.success(null)).when(mockFileServiceResponse).execute();
    doReturn(GitConfigDTO.builder().build()).when(cdStepHelper).getScmConnector(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testSaveTerraformInheritOutputWithGithubStore() {
    Ambiance ambiance = getAmbiance();
    TerraformStepDataGenerator.GitStoreConfig gitStoreConfigFiles =
        TerraformStepDataGenerator.GitStoreConfig.builder()
            .branch("master")
            .fetchType(FetchType.BRANCH)
            .folderPath(ParameterField.createValueField("Config/"))
            .connectoref(ParameterField.createValueField("terraform"))
            .build();
    TerraformPlanStepParameters planStepParameters = TerraformStepDataGenerator.generateStepPlanWithVarFiles(
        StoreConfigType.GITHUB, null, gitStoreConfigFiles, null, true);
    TerraformTaskNGResponse response = TerraformTaskNGResponse.builder()
                                           .commitIdForConfigFilesMap(ImmutableMap.of(TF_CONFIG_FILES, "commit-1"))
                                           .build();
    doReturn(LocalConfigDTO.builder().encryptionType(EncryptionType.LOCAL).build())
        .when(mockSecretManagerClientService)
        .getSecretManager(anyString(), anyString(), anyString(), anyString(), anyBoolean());
    helper.saveTerraformInheritOutput(planStepParameters, response, ambiance, null);
    ArgumentCaptor<TerraformInheritOutput> captor = ArgumentCaptor.forClass(TerraformInheritOutput.class);
    verify(mockExecutionSweepingOutputService).consume(any(), anyString(), captor.capture(), anyString());
    TerraformInheritOutput output = captor.getValue();
    assertThat(output).isNotNull();
    io.harness.cdng.manifest.yaml.GitStoreConfig configFiles = output.getConfigFiles();
    assertThat(configFiles).isNotNull();
    assertThat(configFiles.getGitFetchType()).isEqualTo(FetchType.COMMIT);
    assertThat(ParameterFieldHelper.getParameterFieldValue(configFiles.getBranch())).isNull();
    String commitId = ParameterFieldHelper.getParameterFieldValue(configFiles.getCommitId());
    assertThat(commitId).isEqualTo("commit-1");
    List<TerraformVarFileConfig> varFileConfigs = output.getVarFileConfigs();
    assertThat(varFileConfigs).isNotNull();
    assertThat(varFileConfigs.size()).isEqualTo(1);
    assertThat(varFileConfigs.get(0) instanceof TerraformInlineVarFileConfig).isTrue();
    assertThat(((TerraformInlineVarFileConfig) varFileConfigs.get(0)).getVarFileContent()).isEqualTo("var-content");
    assertThat(output.getBackendConfig()).isEqualTo("back-content");
    assertThat(output.getEnvironmentVariables()).isNotNull();
    assertThat(output.getEnvironmentVariables().size()).isEqualTo(1);
    assertThat(output.getEnvironmentVariables().get("KEY")).isEqualTo("VAL");
  }

  @Test
  @Owner(developers = JELENA)
  @Category(UnitTests.class)
  public void testSaveTerraformInheritOutputWithRemoteBackendConfig() {
    Ambiance ambiance = getAmbiance();
    TerraformStepDataGenerator.GitStoreConfig gitStoreConfigFiles =
        TerraformStepDataGenerator.GitStoreConfig.builder()
            .branch("master")
            .fetchType(FetchType.BRANCH)
            .folderPath(ParameterField.createValueField("Config/"))
            .connectoref(ParameterField.createValueField("terraform"))
            .build();
    TerraformStepDataGenerator.GitStoreConfig gitStoreConfigFilesBackendConfig =
        TerraformStepDataGenerator.GitStoreConfig.builder()
            .branch("master")
            .fetchType(FetchType.BRANCH)
            .folderPath(ParameterField.createValueField("Config/state"))
            .connectoref(ParameterField.createValueField("terraform"))
            .build();
    TerraformPlanStepParameters planStepParameters = TerraformStepDataGenerator.generateStepPlanWithRemoteBackendConfig(
        StoreConfigType.GITHUB, StoreConfigType.GITHUB, gitStoreConfigFiles, gitStoreConfigFilesBackendConfig);
    TerraformTaskNGResponse response = TerraformTaskNGResponse.builder()
                                           .commitIdForConfigFilesMap(ImmutableMap.of(TF_CONFIG_FILES, "commit-1"))
                                           .build();
    doReturn(LocalConfigDTO.builder().encryptionType(EncryptionType.LOCAL).build())
        .when(mockSecretManagerClientService)
        .getSecretManager(anyString(), anyString(), anyString(), anyString(), anyBoolean());
    helper.saveTerraformInheritOutput(planStepParameters, response, ambiance, null);
    ArgumentCaptor<TerraformInheritOutput> captor = ArgumentCaptor.forClass(TerraformInheritOutput.class);
    verify(mockExecutionSweepingOutputService).consume(any(), anyString(), captor.capture(), anyString());
    TerraformInheritOutput output = captor.getValue();
    assertThat(output).isNotNull();
    io.harness.cdng.manifest.yaml.GitStoreConfig configFiles = output.getConfigFiles();
    assertThat(configFiles).isNotNull();
    assertThat(configFiles.getGitFetchType()).isEqualTo(FetchType.COMMIT);
    assertThat(ParameterFieldHelper.getParameterFieldValue(configFiles.getBranch())).isNull();
    String commitId = ParameterFieldHelper.getParameterFieldValue(configFiles.getCommitId());
    assertThat(commitId).isEqualTo("commit-1");
    TerraformBackendConfigFileConfig backendConfigFile = output.getBackendConfigurationFileConfig();
    assertThat(backendConfigFile).isNotNull();
    assertThat(backendConfigFile instanceof TerraformRemoteFileConfig).isTrue();
    assertThat(((TerraformRemoteFileConfig) backendConfigFile).getGitStoreConfigDTO() instanceof GithubStoreDTO)
        .isTrue();
    GithubStoreDTO gitStoreDTO =
        (GithubStoreDTO) ((TerraformRemoteFileConfig) backendConfigFile).getGitStoreConfigDTO();
    assertThat(gitStoreDTO).isNotNull();
    assertThat(gitStoreDTO.getGitFetchType()).isEqualTo(FetchType.BRANCH);
    assertThat(gitStoreDTO.getBranch()).isEqualTo("master");
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testSaveTerraformInheritOutputWithGithubStoreFFEnabled() {
    Ambiance ambiance = getAmbiance();
    TerraformStepDataGenerator.GitStoreConfig gitStoreConfigFiles =
        TerraformStepDataGenerator.GitStoreConfig.builder()
            .branch("master")
            .fetchType(FetchType.BRANCH)
            .folderPath(ParameterField.createValueField("Config/"))
            .connectoref(ParameterField.createValueField("terraform"))
            .build();

    TerraformPlanStepParameters planStepParameters = TerraformStepDataGenerator.generateStepPlanWithVarFiles(
        StoreConfigType.GITHUB, null, gitStoreConfigFiles, null, true);
    TerraformTaskNGResponse response = TerraformTaskNGResponse.builder()
                                           .commitIdForConfigFilesMap(ImmutableMap.of(TF_CONFIG_FILES, "commit-1"))
                                           .build();
    doReturn(LocalConfigDTO.builder().encryptionType(EncryptionType.LOCAL).build())
        .when(mockSecretManagerClientService)
        .getSecretManager(anyString(), anyString(), anyString(), anyString(), anyBoolean());

    planStepParameters.getConfiguration().getConfigFiles().setModuleSource(
        ModuleSource.builder().useConnectorCredentials(ParameterField.createValueField(true)).build());

    helper.saveTerraformInheritOutput(planStepParameters, response, ambiance, null);
    ArgumentCaptor<TerraformInheritOutput> captor = ArgumentCaptor.forClass(TerraformInheritOutput.class);
    verify(mockExecutionSweepingOutputService).consume(any(), anyString(), captor.capture(), anyString());
    TerraformInheritOutput output = captor.getValue();
    assertThat(output).isNotNull();
    GitStoreConfig configFiles = output.getConfigFiles();
    assertThat(configFiles).isNotNull();
    assertThat(configFiles.getGitFetchType()).isEqualTo(FetchType.COMMIT);
    String commitId = ParameterFieldHelper.getParameterFieldValue(configFiles.getCommitId());
    assertThat(commitId).isEqualTo("commit-1");
    assertThat(output.isUseConnectorCredentials()).isTrue();
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testSaveTerraformInheritOutputWithArtifactoryStore() {
    Ambiance ambiance = getAmbiance();

    TerraformStepDataGenerator.ArtifactoryStoreConfig artifactoryStoreVarFiles =
        TerraformStepDataGenerator.ArtifactoryStoreConfig.builder()
            .repositoryName("RepositoryPath")
            .connectorRef("ConnectorRef")
            .artifacts(TerraformStepDataGenerator.generateArtifacts())
            .build();
    TerraformStepDataGenerator.ArtifactoryStoreConfig artifactoryStoreConfigFiles =
        TerraformStepDataGenerator.ArtifactoryStoreConfig.builder()
            .repositoryName("RepositoryPathConfig")
            .connectorRef("ConnectorRefConfig")
            .artifacts(TerraformStepDataGenerator.generateArtifacts())
            .build();

    TerraformPlanStepParameters planStepParameters =
        TerraformStepDataGenerator.generateStepPlanWithVarFiles(StoreConfigType.ARTIFACTORY,
            StoreConfigType.ARTIFACTORY, artifactoryStoreConfigFiles, artifactoryStoreVarFiles, true);
    TerraformTaskNGResponse response = TerraformTaskNGResponse.builder()
                                           .commitIdForConfigFilesMap(ImmutableMap.of(TF_CONFIG_FILES, "commit-1"))
                                           .build();
    doReturn(LocalConfigDTO.builder().encryptionType(EncryptionType.LOCAL).build())
        .when(mockSecretManagerClientService)
        .getSecretManager(anyString(), anyString(), anyString(), anyString(), anyBoolean());
    helper.saveTerraformInheritOutput(planStepParameters, response, ambiance, null);
    ArgumentCaptor<TerraformInheritOutput> captor = ArgumentCaptor.forClass(TerraformInheritOutput.class);
    verify(mockExecutionSweepingOutputService).consume(any(), anyString(), captor.capture(), anyString());
    TerraformInheritOutput output = captor.getValue();
    assertThat(output).isNotNull();
    ArtifactoryStorageConfigDTO configFiles = (ArtifactoryStorageConfigDTO) output.getFileStorageConfigDTO();
    assertThat(configFiles).isNotNull();
    assertThat(configFiles.getArtifactPaths()).size().isEqualTo(1);
    List<TerraformVarFileConfig> varFileConfigs = output.getVarFileConfigs();
    assertThat(varFileConfigs).isNotNull();
    assertThat(varFileConfigs.size()).isEqualTo(2);
    assertThat(varFileConfigs.get(1) instanceof TerraformInlineVarFileConfig).isTrue();
    assertThat(((TerraformInlineVarFileConfig) varFileConfigs.get(1)).getVarFileContent()).isEqualTo("var-content");
    assertThat(varFileConfigs.get(0) instanceof TerraformRemoteVarFileConfig).isTrue();
    assertThat(((TerraformRemoteVarFileConfig) varFileConfigs.get(0)).getFileStoreConfigDTO().getKind())
        .isEqualTo("Artifactory");
    assertThat(output.getBackendConfig()).isEqualTo("back-content");
    assertThat(output.getEnvironmentVariables()).isNotNull();
    assertThat(output.getEnvironmentVariables().size()).isEqualTo(1);
    assertThat(output.getEnvironmentVariables().get("KEY")).isEqualTo("VAL");
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testSaveRollbackDestroyConfigInline() {
    Ambiance ambiance = getAmbiance();
    TerraformStepDataGenerator.GitStoreConfig gitStoreConfigFiles =
        TerraformStepDataGenerator.GitStoreConfig.builder()
            .branch("master")
            .fetchType(FetchType.BRANCH)
            .folderPath(ParameterField.createValueField("Config/"))
            .connectoref(ParameterField.createValueField("ConnectorRef"))
            .build();
    TerraformStepDataGenerator.GitStoreConfig gitStoreVarFiles =
        TerraformStepDataGenerator.GitStoreConfig.builder()
            .branch("master")
            .fetchType(FetchType.BRANCH)
            .folderPath(ParameterField.createValueField("VarFiles/"))
            .varFolderPath(ParameterField.createValueField(Collections.singletonList("VarFiles/")))
            .connectoref(ParameterField.createValueField("ConnectorRef"))
            .build();
    TerraformConfigFilesWrapper configFilesWrapper = new TerraformConfigFilesWrapper();
    TerraformStepDataGenerator.generateConfigFileStore(configFilesWrapper, StoreConfigType.GITHUB, gitStoreConfigFiles);
    RemoteTerraformVarFileSpec remoteVarFiles =
        TerraformStepDataGenerator.generateRemoteVarFileSpec(StoreConfigType.GITLAB, gitStoreVarFiles);
    LinkedHashMap<String, TerraformVarFile> varFilesMap =
        TerraformStepDataGenerator.generateVarFileSpecs(remoteVarFiles, false);
    TerraformApplyStepParameters parameters =
        TerraformApplyStepParameters.infoBuilder()
            .provisionerIdentifier(ParameterField.createValueField("provId_"))
            .configuration(TerraformStepConfigurationParameters.builder()
                               .type(TerraformStepConfigurationType.INLINE)
                               .spec(TerraformExecutionDataParameters.builder()
                                         .configFiles(configFilesWrapper)
                                         .varFiles(varFilesMap)
                                         .isTerraformCloudCli(ParameterField.createValueField(false))
                                         .build())
                               .build())
            .build();
    TerraformTaskNGResponse response = TerraformTaskNGResponse.builder()
                                           .commitIdForConfigFilesMap(ImmutableMap.of(TF_CONFIG_FILES, "commit-1",
                                               String.format(TerraformStepHelper.TF_VAR_FILES, 1), "commit-2"))
                                           .build();
    helper.saveRollbackDestroyConfigInline(parameters, response, ambiance, null);
    ArgumentCaptor<TerraformConfig> captor = ArgumentCaptor.forClass(TerraformConfig.class);
    verify(terraformConfigDAL).saveTerraformConfig(captor.capture());
    TerraformConfig config = captor.getValue();
    assertThat(config).isNotNull();
    assertThat(config.getAccountId()).isEqualTo("test-account");
    assertThat(config.getOrgId()).isEqualTo("test-org");
    assertThat(config.getProjectId()).isEqualTo("test-project");
    GitStoreConfigDTO configFiles = config.getConfigFiles();
    assertThat(configFiles instanceof GithubStoreDTO).isTrue();
    GithubStoreDTO githubStoreDTO = (GithubStoreDTO) configFiles;
    assertThat(githubStoreDTO.getGitFetchType()).isEqualTo(FetchType.COMMIT);
    assertThat(githubStoreDTO.getCommitId()).isEqualTo("commit-1");
    List<TerraformVarFileConfig> varFileConfigs = config.getVarFileConfigs();
    assertThat(varFileConfigs).isNotNull();
    assertThat(varFileConfigs.size()).isEqualTo(1);
    TerraformVarFileConfig terraformVarFileConfig = varFileConfigs.get(0);
    assertThat(terraformVarFileConfig instanceof TerraformRemoteVarFileConfig).isTrue();
    TerraformRemoteVarFileConfig remoteVarFileConfig = (TerraformRemoteVarFileConfig) terraformVarFileConfig;
    GitStoreConfigDTO gitStoreConfigDTO = remoteVarFileConfig.getGitStoreConfigDTO();
    assertThat(gitStoreConfigDTO instanceof GitLabStoreDTO).isTrue();
    GitLabStoreDTO gitLabStoreDTO = (GitLabStoreDTO) gitStoreConfigDTO;
    assertThat(gitLabStoreDTO.getGitFetchType()).isEqualTo(FetchType.COMMIT);
    assertThat(gitLabStoreDTO.getCommitId()).isEqualTo("commit-2");
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testToTerraformVarFileInfoWithGitStore() {
    Ambiance ambiance = getAmbiance();
    TerraformStepDataGenerator.GitStoreConfig gitStoreVarFiles =
        TerraformStepDataGenerator.GitStoreConfig.builder()
            .branch("master")
            .fetchType(FetchType.BRANCH)
            .folderPath(ParameterField.createValueField("VarFiles/"))
            .varFolderPath(ParameterField.createValueField(Collections.singletonList("VarFiles/")))
            .connectoref(ParameterField.createValueField("ConnectorRef"))
            .build();
    RemoteTerraformVarFileSpec remoteVarFiles =
        TerraformStepDataGenerator.generateRemoteVarFileSpec(StoreConfigType.GIT, gitStoreVarFiles);
    Map<String, TerraformVarFile> varFilesMap = TerraformStepDataGenerator.generateVarFileSpecs(remoteVarFiles, true);
    doNothing().when(cdStepHelper).validateGitStoreConfig(any());
    doReturn(
        ConnectorInfoDTO.builder().connectorConfig(GitConfigDTO.builder().gitAuthType(GitAuthType.SSH).build()).build())
        .when(cdStepHelper)
        .getConnector(anyString(), any());
    doReturn(SSHKeySpecDTO.builder().build())
        .when(mockGitConfigAuthenticationInfoHelper)
        .getSSHKey(any(), anyString(), anyString(), anyString());
    doReturn(Collections.emptyList())
        .when(mockGitConfigAuthenticationInfoHelper)
        .getEncryptedDataDetails(any(), any(), any());
    List<TerraformVarFileInfo> terraformVarFileInfos = helper.toTerraformVarFileInfo(varFilesMap, ambiance);
    verify(cdStepHelper, times(1)).validateGitStoreConfig(any());
    assertThat(terraformVarFileInfos).isNotNull();
    assertThat(terraformVarFileInfos.size()).isEqualTo(2);
    TerraformVarFileInfo terraformVarFileInfo = terraformVarFileInfos.get(1);
    assertThat(terraformVarFileInfo instanceof InlineTerraformVarFileInfo).isTrue();
    InlineTerraformVarFileInfo inlineTerraformVarFileInfo = (InlineTerraformVarFileInfo) terraformVarFileInfo;
    assertThat(inlineTerraformVarFileInfo.getVarFileContent()).isEqualTo("var-content");
    terraformVarFileInfo = terraformVarFileInfos.get(0);
    assertThat(terraformVarFileInfo instanceof RemoteTerraformVarFileInfo).isTrue();
    RemoteTerraformVarFileInfo remoteTerraformVarFileInfo = (RemoteTerraformVarFileInfo) terraformVarFileInfo;
    assertThat(remoteTerraformVarFileInfo.getGitFetchFilesConfig().getGitStoreDelegateConfig().getBranch())
        .isEqualTo("master");
    assertThat(remoteTerraformVarFileInfo.getGitFetchFilesConfig().getGitStoreDelegateConfig().getPaths().get(0))
        .isEqualTo("VarFiles/");
    assertThat(remoteTerraformVarFileInfo.getGitFetchFilesConfig().getGitStoreDelegateConfig().getFetchType())
        .isEqualTo(FetchType.BRANCH);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testToTerraformVarFileInfoWithArtifactoryStore() {
    Ambiance ambiance = getAmbiance();
    TerraformStepDataGenerator.ArtifactoryStoreConfig artifactoryStoreVarFiles =
        TerraformStepDataGenerator.ArtifactoryStoreConfig.builder()
            .repositoryName("RepositoryPath")
            .connectorRef("ConnectorRef")
            .artifacts(TerraformStepDataGenerator.generateArtifacts())
            .build();

    RemoteTerraformVarFileSpec remoteVarFiles =
        TerraformStepDataGenerator.generateRemoteVarFileSpec(StoreConfigType.ARTIFACTORY, artifactoryStoreVarFiles);
    Map<String, TerraformVarFile> varFilesMap = TerraformStepDataGenerator.generateVarFileSpecs(remoteVarFiles, true);
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
    doReturn(connectorInfoDTO).when(cdStepHelper).getConnector(anyString(), any());
    List<TerraformVarFileInfo> terraformVarFileInfos = helper.toTerraformVarFileInfo(varFilesMap, ambiance);
    assertThat(terraformVarFileInfos).isNotNull();
    assertThat(terraformVarFileInfos.size()).isEqualTo(2);
    TerraformVarFileInfo terraformVarFileInfo = terraformVarFileInfos.get(1);
    assertThat(terraformVarFileInfo instanceof InlineTerraformVarFileInfo).isTrue();
    InlineTerraformVarFileInfo inlineTerraformVarFileInfo = (InlineTerraformVarFileInfo) terraformVarFileInfo;
    assertThat(inlineTerraformVarFileInfo.getVarFileContent()).isEqualTo("var-content");
    terraformVarFileInfo = terraformVarFileInfos.get(0);
    assertThat(terraformVarFileInfo instanceof RemoteTerraformVarFileInfo).isTrue();
    RemoteTerraformVarFileInfo remoteTerraformVarFileInfo = (RemoteTerraformVarFileInfo) terraformVarFileInfo;
    ArtifactoryStoreDelegateConfig artifactoryStoreDelegateConfig =
        (ArtifactoryStoreDelegateConfig) remoteTerraformVarFileInfo.getFilestoreFetchFilesConfig();
    assertThat(artifactoryStoreDelegateConfig.getRepositoryName()).isEqualTo("RepositoryPath");
    assertThat(artifactoryStoreDelegateConfig.getConnectorDTO().getName()).isEqualTo("connectorName");
    assertThat(artifactoryStoreDelegateConfig.getConnectorDTO().getConnectorConfig() instanceof ArtifactoryConnectorDTO)
        .isTrue();
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testToTerraformVarFileInfoWithSeveralFilesStore() {
    Ambiance ambiance = getAmbiance();

    // Create 3 var files with different stores at the same time
    TerraformStepDataGenerator.ArtifactoryStoreConfig artifactoryStoreVarFilesA =
        TerraformStepDataGenerator.ArtifactoryStoreConfig.builder()
            .repositoryName("RepositoryPath")
            .connectorRef("ConnectorRef")
            .artifacts(TerraformStepDataGenerator.generateArtifacts())
            .build();
    TerraformStepDataGenerator.ArtifactoryStoreConfig artifactoryStoreVarFilesB =
        TerraformStepDataGenerator.ArtifactoryStoreConfig.builder()
            .repositoryName("RepositoryPath2")
            .connectorRef("ConnectorRef")
            .artifacts(TerraformStepDataGenerator.generateArtifacts())
            .build();
    TerraformStepDataGenerator.GitStoreConfig gitStoreVarFiles =
        TerraformStepDataGenerator.GitStoreConfig.builder()
            .branch("master")
            .fetchType(FetchType.BRANCH)
            .folderPath(ParameterField.createValueField("VarFiles/"))
            .varFolderPath(ParameterField.createValueField(Collections.singletonList("VarFiles/")))
            .connectoref(ParameterField.createValueField("ConnectorRef2"))
            .build();

    RemoteTerraformVarFileSpec remoteVarFiles =
        TerraformStepDataGenerator.generateRemoteVarFileSpec(StoreConfigType.ARTIFACTORY, artifactoryStoreVarFilesA);
    Map<String, TerraformVarFile> varFilesMap = TerraformStepDataGenerator.generateVarFileSpecs(remoteVarFiles, true);
    // Add the second file with different artifactory values but same connectorRef
    varFilesMap.put("var-file-3",
        TerraformVarFile.builder()
            .identifier("var-file-3")
            .type("Remote")
            .spec(TerraformStepDataGenerator.generateRemoteVarFileSpec(
                StoreConfigType.ARTIFACTORY, artifactoryStoreVarFilesB))
            .build());
    // Add another var file with a different connector. This one is a github connector
    varFilesMap.put("var-file-4",
        TerraformVarFile.builder()
            .identifier("var-file-4")
            .type("Remote")
            .spec(TerraformStepDataGenerator.generateRemoteVarFileSpec(StoreConfigType.GITHUB, gitStoreVarFiles))
            .build());
    // Create base auth for the artifactory connector
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
    doNothing().when(cdStepHelper).validateGitStoreConfig(any());
    when(cdStepHelper.getConnector(anyString(), any()))
        .thenReturn(connectorInfoDTO, connectorInfoDTO,
            ConnectorInfoDTO.builder()
                .connectorConfig(GitConfigDTO.builder().gitAuthType(GitAuthType.SSH).build())
                .build());
    List<TerraformVarFileInfo> terraformVarFileInfos = helper.toTerraformVarFileInfo(varFilesMap, ambiance);
    verify(cdStepHelper, times(1)).validateGitStoreConfig(any());
    assertThat(terraformVarFileInfos).isNotNull();
    assertThat(terraformVarFileInfos.size()).isEqualTo(4);
    TerraformVarFileInfo terraformVarFileInfo = terraformVarFileInfos.get(1);
    assertThat(terraformVarFileInfo instanceof InlineTerraformVarFileInfo).isTrue();
    InlineTerraformVarFileInfo inlineTerraformVarFileInfo = (InlineTerraformVarFileInfo) terraformVarFileInfo;
    assertThat(inlineTerraformVarFileInfo.getVarFileContent()).isEqualTo("var-content");
    terraformVarFileInfo = terraformVarFileInfos.get(0);
    assertThat(terraformVarFileInfo instanceof RemoteTerraformVarFileInfo).isTrue();
    RemoteTerraformVarFileInfo remoteTerraformVarFileInfo = (RemoteTerraformVarFileInfo) terraformVarFileInfo;
    ArtifactoryStoreDelegateConfig artifactoryStoreDelegateConfig =
        (ArtifactoryStoreDelegateConfig) remoteTerraformVarFileInfo.getFilestoreFetchFilesConfig();
    assertThat(artifactoryStoreDelegateConfig.getRepositoryName()).isEqualTo("RepositoryPath");
    assertThat(artifactoryStoreDelegateConfig.getConnectorDTO().getName()).isEqualTo("connectorName");
    assertThat(artifactoryStoreDelegateConfig.getConnectorDTO().getConnectorConfig() instanceof ArtifactoryConnectorDTO)
        .isTrue();
    terraformVarFileInfo = terraformVarFileInfos.get(2);
    assertThat(terraformVarFileInfo instanceof RemoteTerraformVarFileInfo).isTrue();
    RemoteTerraformVarFileInfo remoteTerraformVarFileInfoB = (RemoteTerraformVarFileInfo) terraformVarFileInfo;
    ArtifactoryStoreDelegateConfig artifactoryStoreDelegateConfigB =
        (ArtifactoryStoreDelegateConfig) remoteTerraformVarFileInfoB.getFilestoreFetchFilesConfig();
    assertThat(artifactoryStoreDelegateConfigB.getRepositoryName()).isEqualTo("RepositoryPath2");
    assertThat(artifactoryStoreDelegateConfigB.getConnectorDTO().getName()).isEqualTo("connectorName");
    assertThat(
        artifactoryStoreDelegateConfigB.getConnectorDTO().getConnectorConfig() instanceof ArtifactoryConnectorDTO)
        .isTrue();
    terraformVarFileInfo = terraformVarFileInfos.get(3);
    assertThat(terraformVarFileInfo instanceof RemoteTerraformVarFileInfo).isTrue();
    RemoteTerraformVarFileInfo remoteTerraformVarFileInfoC = (RemoteTerraformVarFileInfo) terraformVarFileInfo;
    assertThat(remoteTerraformVarFileInfoC.getGitFetchFilesConfig().getGitStoreDelegateConfig().getBranch())
        .isEqualTo("master");
    assertThat(remoteTerraformVarFileInfoC.getGitFetchFilesConfig().getGitStoreDelegateConfig().getPaths().get(0))
        .isEqualTo("VarFiles/");
    assertThat(remoteTerraformVarFileInfoC.getGitFetchFilesConfig().getGitStoreDelegateConfig().getFetchType())
        .isEqualTo(FetchType.BRANCH);
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testGenerateFullIdentifier() throws IOException {
    String entityId = helper.generateFullIdentifier("tfplan_", getAmbiance());
    FileIo.createDirectoryIfDoesNotExist(entityId);
    assertThat(FileIo.checkIfFileExist(entityId)).isTrue();
    FileUtils.deleteQuietly(new File(entityId));
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testGenerateFullIdentifierInvalidProvisionerIdentifer() {
    try {
      helper.generateFullIdentifier("tfplan_ ", getAmbiance());
    } catch (InvalidRequestException invalidRequestException) {
      assertThat(invalidRequestException.getMessage())
          .isEqualTo("Provisioner Identifier cannot contain special characters or spaces: [tfplan_ ]");
    }
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testPrepareTerraformVarFileInfoWithGitStore() {
    Ambiance ambiance = getAmbiance();
    ConnectorInfoDTO connectorInfo = ConnectorInfoDTO.builder()
                                         .name("terraform")
                                         .identifier("terraform")
                                         .connectorType(GITHUB)
                                         .connectorConfig(GitConfigDTO.builder()
                                                              .gitAuthType(GitAuthType.HTTP)
                                                              .gitConnectionType(GitConnectionType.ACCOUNT)
                                                              .delegateSelectors(Collections.singleton("delegateName"))
                                                              .url("https://github.com/wings-software")
                                                              .branchName("master")
                                                              .build())
                                         .build();

    doReturn(connectorInfo).when(cdStepHelper).getConnector(anyString(), any());
    doReturn(SSHKeySpecDTO.builder().build())
        .when(mockGitConfigAuthenticationInfoHelper)
        .getSSHKey(any(), anyString(), anyString(), anyString());
    doReturn(Collections.emptyList())
        .when(mockGitConfigAuthenticationInfoHelper)
        .getEncryptedDataDetails(any(), any(), any());
    doNothing().when(cdStepHelper).validateGitStoreConfig(any());

    List<TerraformVarFileConfig> varFileConfigs = new LinkedList<>();

    GitStoreConfigDTO configFiles1 = GithubStoreDTO.builder()
                                         .branch("master")
                                         .repoName("terraform")
                                         .paths(Collections.singletonList("VarFiles/"))
                                         .connectorRef("terraform")
                                         .gitFetchType(FetchType.COMMIT)
                                         .commitId("commit")
                                         .build();

    doReturn(GitConfigDTO.builder()
                 .gitAuthType(GitAuthType.HTTP)
                 .gitConnectionType(GitConnectionType.ACCOUNT)
                 .delegateSelectors(Collections.singleton("delegateName"))
                 .url("https://github.com/wings-software/terraform")
                 .branchName("master")
                 .build())
        .when(cdStepHelper)
        .getScmConnector(any(), any(), any(), any());
    TerraformVarFileConfig inlineFileConfig =
        TerraformInlineVarFileConfig.builder().varFileContent("var-content").build();
    TerraformVarFileConfig remoteFileConfig =
        TerraformRemoteVarFileConfig.builder().isOptional(true).gitStoreConfigDTO(configFiles1).build();
    varFileConfigs.add(inlineFileConfig);
    varFileConfigs.add(remoteFileConfig);

    List<TerraformVarFileInfo> terraformVarFileInfos =
        helper.prepareTerraformVarFileInfo(varFileConfigs, ambiance, false);
    verify(cdStepHelper, times(1)).validateGitStoreConfig(any());
    assertThat(terraformVarFileInfos.size()).isEqualTo(2);
    for (TerraformVarFileInfo terraformVarFileInfo : terraformVarFileInfos) {
      if (terraformVarFileInfo instanceof InlineTerraformVarFileInfo) {
        InlineTerraformVarFileInfo inlineTerraformVarFileInfo = (InlineTerraformVarFileInfo) terraformVarFileInfo;
        assertThat(inlineTerraformVarFileInfo.getVarFileContent()).isEqualTo("var-content");
      } else if (terraformVarFileInfo instanceof RemoteTerraformVarFileInfo) {
        RemoteTerraformVarFileInfo remoteTerraformVarFileInfo = (RemoteTerraformVarFileInfo) terraformVarFileInfo;
        assertThat(remoteTerraformVarFileInfo.getGitFetchFilesConfig().getIdentifier()).isEqualTo("TF_VAR_FILES_1");
        assertThat(remoteTerraformVarFileInfo.getGitFetchFilesConfig().getGitStoreDelegateConfig().getBranch())
            .isEqualTo("master");
        assertThat(remoteTerraformVarFileInfo.getGitFetchFilesConfig().getGitStoreDelegateConfig().getPaths().size())
            .isEqualTo(1);
        assertThat(remoteTerraformVarFileInfo.getGitFetchFilesConfig().getGitStoreDelegateConfig().getPaths().get(0))
            .isEqualTo("VarFiles/");
        assertThat(remoteTerraformVarFileInfo.getGitFetchFilesConfig().getGitStoreDelegateConfig().getConnectorName())
            .isEqualTo("terraform");
        assertThat(
            remoteTerraformVarFileInfo.getGitFetchFilesConfig().getGitStoreDelegateConfig().getGitConfigDTO().getUrl())
            .isEqualTo("https://github.com/wings-software/terraform");
        assertThat(remoteTerraformVarFileInfo.getGitFetchFilesConfig().getGitStoreDelegateConfig().isOptional())
            .isTrue();
      }
    }
  }

  @Test
  @Owner(developers = JELENA)
  @Category(UnitTests.class)
  public void testPrepareTerraformRemoteBackendConfigFileInfoWithGitStore() {
    Ambiance ambiance = getAmbiance();
    ConnectorInfoDTO connectorInfo = ConnectorInfoDTO.builder()
                                         .name("terraform")
                                         .identifier("terraform")
                                         .connectorType(GITHUB)
                                         .connectorConfig(GitConfigDTO.builder()
                                                              .gitAuthType(GitAuthType.HTTP)
                                                              .gitConnectionType(GitConnectionType.ACCOUNT)
                                                              .delegateSelectors(Collections.singleton("delegateName"))
                                                              .url("https://github.com/wings-software")
                                                              .branchName("master")
                                                              .build())
                                         .build();

    doReturn(GitConfigDTO.builder()
                 .gitAuthType(GitAuthType.HTTP)
                 .gitConnectionType(GitConnectionType.ACCOUNT)
                 .delegateSelectors(Collections.singleton("delegateName"))
                 .url("https://github.com/wings-software/terraform")
                 .branchName("master")
                 .build())
        .when(cdStepHelper)
        .getScmConnector(any(), any(), any(), any());
    doReturn(true).when(cdFeatureFlagHelper).isEnabled(anyString(), any());
    doReturn(connectorInfo).when(cdStepHelper).getConnector(anyString(), any());
    doReturn(SSHKeySpecDTO.builder().build())
        .when(mockGitConfigAuthenticationInfoHelper)
        .getSSHKey(any(), anyString(), anyString(), anyString());
    doReturn(Collections.emptyList())
        .when(mockGitConfigAuthenticationInfoHelper)
        .getEncryptedDataDetails(any(), any(), any());
    doNothing().when(cdStepHelper).validateGitStoreConfig(any());

    GitStoreConfigDTO configFiles1 = GithubStoreDTO.builder()
                                         .branch("master")
                                         .repoName("terraform")
                                         .folderPath("test-path")
                                         .connectorRef("terraform")
                                         .gitFetchType(FetchType.COMMIT)
                                         .commitId("commit")
                                         .build();

    TerraformBackendConfigFileConfig remoteBackendConfig =
        TerraformRemoteBackendConfigFileConfig.builder().gitStoreConfigDTO(configFiles1).build();

    TerraformBackendConfigFileInfo fileInfo =
        helper.prepareTerraformBackendConfigFileInfo(remoteBackendConfig, ambiance);
    verify(cdStepHelper, times(1)).validateGitStoreConfig(any());
    assertThat(fileInfo).isNotNull();
    assertThat(fileInfo).isInstanceOf(RemoteTerraformBackendConfigFileInfo.class);
    RemoteTerraformBackendConfigFileInfo remoteTerraformFileInfo = (RemoteTerraformBackendConfigFileInfo) fileInfo;
    assertThat(remoteTerraformFileInfo.getGitFetchFilesConfig().getIdentifier()).isEqualTo("TF_BACKEND_CONFIG_FILE");
    assertThat(remoteTerraformFileInfo.getGitFetchFilesConfig().getGitStoreDelegateConfig().getBranch())
        .isEqualTo("master");
    assertThat(remoteTerraformFileInfo.getGitFetchFilesConfig().getGitStoreDelegateConfig().getPaths().size())
        .isEqualTo(1);
    assertThat(remoteTerraformFileInfo.getGitFetchFilesConfig().getGitStoreDelegateConfig().getPaths().get(0))
        .isEqualTo("test-path");
    assertThat(remoteTerraformFileInfo.getGitFetchFilesConfig().getGitStoreDelegateConfig().getConnectorName())
        .isEqualTo("terraform");
    assertThat(remoteTerraformFileInfo.getGitFetchFilesConfig().getGitStoreDelegateConfig().getGitConfigDTO().getUrl())
        .isEqualTo("https://github.com/wings-software/terraform");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testgetFileStoreFetchFilesConfigExceptionThrown() {
    Ambiance ambiance = getAmbiance();
    ArtifactoryStoreConfig artifactoryStoreConfig =
        ArtifactoryStoreConfig.builder()
            .connectorRef(ParameterField.createValueField("connectorRef"))
            .artifactPaths(ParameterField.createValueField(asList("path1", "path2")))
            .build();

    doReturn(getConnectorInfoDTO()).when(cdStepHelper).getConnector(any(), any());
    doNothing().when(cdStepHelper).validateManifest(any(), any(), any());
    doReturn(null).when(mockSecretManagerClientService).getEncryptionDetails(any(), any());
    assertThatThrownBy(() -> helper.getFileStoreFetchFilesConfig(artifactoryStoreConfig, ambiance, TF_CONFIG_FILES))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Config file should not contain more than one file path");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testPrepareTerraformVarFileInfoWithFileStore() {
    Ambiance ambiance = getAmbiance();
    TerraformStepDataGenerator.ArtifactoryStoreConfig artifactoryStoreVarFiles =
        TerraformStepDataGenerator.ArtifactoryStoreConfig.builder()
            .repositoryName("RepositoryPath")
            .connectorRef("ConnectorRef")
            .artifacts(TerraformStepDataGenerator.generateArtifacts())
            .build();

    RemoteTerraformVarFileSpec remoteVarFiles =
        TerraformStepDataGenerator.generateRemoteVarFileSpec(StoreConfigType.ARTIFACTORY, artifactoryStoreVarFiles);
    Map<String, TerraformVarFile> varFilesMap = TerraformStepDataGenerator.generateVarFileSpecs(remoteVarFiles, true);
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
    doReturn(connectorInfoDTO).when(cdStepHelper).getConnector(anyString(), any());
    List<TerraformVarFileInfo> terraformVarFileInfos = helper.toTerraformVarFileInfo(varFilesMap, ambiance);
    assertThat(terraformVarFileInfos).isNotNull();
    assertThat(terraformVarFileInfos.size()).isEqualTo(2);
    for (TerraformVarFileInfo terraformVarFileInfo : terraformVarFileInfos) {
      if (terraformVarFileInfo instanceof InlineTerraformVarFileInfo) {
        InlineTerraformVarFileInfo inlineTerraformVarFileInfo = (InlineTerraformVarFileInfo) terraformVarFileInfo;
        assertThat(inlineTerraformVarFileInfo.getVarFileContent()).isEqualTo("var-content");
      } else if (terraformVarFileInfo instanceof RemoteTerraformVarFileInfo) {
        RemoteTerraformVarFileInfo remoteTerraformVarFileInfo = (RemoteTerraformVarFileInfo) terraformVarFileInfo;
        assertThat(remoteTerraformVarFileInfo.getFilestoreFetchFilesConfig().getIdentifier())
            .isEqualTo("TF_VAR_FILES_1");
        ArtifactoryStoreDelegateConfig artifactoryStoreDelegateConfig =
            (ArtifactoryStoreDelegateConfig) remoteTerraformVarFileInfo.getFilestoreFetchFilesConfig();
        assertThat(artifactoryStoreDelegateConfig.getRepositoryName()).isEqualTo("RepositoryPath");
        assertThat(artifactoryStoreDelegateConfig.getConnectorDTO().getName()).isEqualTo("connectorName");
      }
    }
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testPrepareTerraformVarFileInfoEmpty() {
    Ambiance ambiance = getAmbiance();
    List<TerraformVarFileConfig> varFileConfigs = new LinkedList<>();
    List<TerraformVarFileInfo> terraformVarFileInfos =
        helper.prepareTerraformVarFileInfo(varFileConfigs, ambiance, false);
    assertThat(terraformVarFileInfos).isEmpty();
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testCreateTFPlanExecutionDetailsKey() {
    TFPlanExecutionDetailsKey tfPlanExecutionDetailsKey = helper.createTFPlanExecutionDetailsKey(getAmbiance());
    assertThat(tfPlanExecutionDetailsKey.getScope()).isNotNull();
    assertThat(tfPlanExecutionDetailsKey.getPipelineExecutionId()).isEqualTo("exec_id");
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testToTerraformVarFileConfigEmpty() {
    Ambiance ambiance = getAmbiance();
    Map<String, TerraformVarFile> varFilesMap = new HashMap<>();
    TerraformTaskNGResponse terraformTaskNGResponse = TerraformTaskNGResponse.builder().build();
    List<TerraformVarFileConfig> terraformVarFileConfig =
        helper.toTerraformVarFileConfig(varFilesMap, terraformTaskNGResponse);
    assertThat(terraformVarFileConfig).isEmpty();
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetTerraformPlanName() {
    Ambiance ambiance = getAmbiance();
    String planName = helper.getTerraformPlanName(APPLY, ambiance, "provisionId");
    assertThat(planName).isEqualTo("tfPlan-exec-id-provisionId");

    String destroyPlanName = helper.getTerraformPlanName(DESTROY, ambiance, "provisionId");
    assertThat(destroyPlanName).isEqualTo("tfDestroyPlan-exec-id-provisionId");
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testToTerraformVarFileInfoEmpty() {
    Ambiance ambiance = getAmbiance();
    Map<String, TerraformVarFile> varFilesMap = new HashMap<>();
    List<TerraformVarFileInfo> terraformVarFileInfo = helper.toTerraformVarFileInfo(varFilesMap, ambiance);
    assertThat(terraformVarFileInfo).isEmpty();
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testPrepareEntityDetailsForVarFiles() {
    TerraformStepDataGenerator.GitStoreConfig gitStoreVarFiles =
        TerraformStepDataGenerator.GitStoreConfig.builder()
            .branch("master")
            .fetchType(FetchType.BRANCH)
            .folderPath(ParameterField.createValueField("VarFiles/"))
            .varFolderPath(ParameterField.createValueField(Collections.singletonList("VarFiles/")))
            .connectoref(ParameterField.createValueField("ConnectorRef"))
            .build();
    RemoteTerraformVarFileSpec remoteVarFiles =
        TerraformStepDataGenerator.generateRemoteVarFileSpec(StoreConfigType.GITLAB, gitStoreVarFiles);
    Map<String, TerraformVarFile> varFilesMap = TerraformStepDataGenerator.generateVarFileSpecs(remoteVarFiles, true);
    List<EntityDetail> entityDetails =
        helper.prepareEntityDetailsForVarFiles("test-account", "test-org", "test-project", varFilesMap);
    // NULL CASES
    EntityDetail entityDetail = entityDetails.get(0);
    assertThat(entityDetail.getEntityRef().getIdentifier()).isEqualTo("ConnectorRef");
    assertThat(entityDetail.getEntityRef().getOrgIdentifier()).isEqualTo("test-org");
    assertThat(entityDetail.getEntityRef().getProjectIdentifier()).isEqualTo("test-project");
    assertThat(entityDetail.getEntityRef().getAccountIdentifier()).isEqualTo("test-account");
  }

  @Test
  @Owner(developers = JELENA)
  @Category(UnitTests.class)
  public void testPrepareEntityDetailForBackendConfigFileInline() {
    TerraformBackendConfig inlineConfig = TerraformStepDataGenerator.generateBackendConfigFile(null, true);
    Optional<EntityDetail> entityDetail = TerraformStepHelper.prepareEntityDetailForBackendConfigFiles(
        "test-account", "test-org", "test-project", inlineConfig);
    assertThat(entityDetail.isPresent()).isFalse();
  }

  @Test
  @Owner(developers = JELENA)
  @Category(UnitTests.class)
  public void testPrepareEntityDetailForBackendConfigFileRemote() {
    TerraformStepDataGenerator.GitStoreConfig gitStoreVarFiles =
        TerraformStepDataGenerator.GitStoreConfig.builder()
            .branch("master")
            .fetchType(FetchType.BRANCH)
            .folderPath(ParameterField.createValueField("VarFiles/"))
            .varFolderPath(ParameterField.createValueField(Collections.singletonList("VarFiles/")))
            .connectoref(ParameterField.createValueField("ConnectorRef"))
            .build();
    RemoteTerraformBackendConfigSpec remoteFile =
        TerraformStepDataGenerator.generateRemoteBackendConfigFileSpec(StoreConfigType.GITLAB, gitStoreVarFiles);
    TerraformBackendConfig remoteConfig = TerraformStepDataGenerator.generateBackendConfigFile(remoteFile, false);
    Optional<EntityDetail> entityDetail = TerraformStepHelper.prepareEntityDetailForBackendConfigFiles(
        "test-account", "test-org", "test-project", remoteConfig);
    assertThat(entityDetail.isPresent()).isTrue();
    assertThat(entityDetail.get().getEntityRef().getIdentifier()).isEqualTo("ConnectorRef");
    assertThat(entityDetail.get().getEntityRef().getOrgIdentifier()).isEqualTo("test-org");
    assertThat(entityDetail.get().getEntityRef().getProjectIdentifier()).isEqualTo("test-project");
    assertThat(entityDetail.get().getEntityRef().getAccountIdentifier()).isEqualTo("test-account");
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class) // incomplete
  public void testSaveRollbackDestroyConfigInherited() {
    Ambiance ambiance = getAmbiance();
    doReturn(
        ConnectorInfoDTO.builder().connectorConfig(GitConfigDTO.builder().gitAuthType(GitAuthType.SSH).build()).build())
        .when(cdStepHelper)
        .getConnector(anyString(), any());
    doReturn(SSHKeySpecDTO.builder().build())
        .when(mockGitConfigAuthenticationInfoHelper)
        .getSSHKey(any(), anyString(), anyString(), anyString());
    doReturn(Collections.emptyList())
        .when(mockGitConfigAuthenticationInfoHelper)
        .getEncryptedDataDetails(any(), any(), any());

    TerraformApplyStepParameters parameters =
        TerraformApplyStepParameters.infoBuilder()
            .provisionerIdentifier(ParameterField.createValueField("provId_"))
            .configuration(TerraformStepConfigurationParameters.builder()
                               .type(TerraformStepConfigurationType.INLINE)
                               .spec(TerraformExecutionDataParameters.builder().build())
                               .build())
            .build();

    GitStoreConfigDTO configFiles = GithubStoreDTO.builder().branch("master").connectorRef("terraform").build();
    TerraformConfig terraformConfig =
        TerraformConfig.builder().backendConfig("back-content").workspace("w1").configFiles(configFiles).build();
    ExecutionSweepingOutput terraformInheritOutput = TerraformInheritOutput.builder()
                                                         .configFiles(terraformConfig.configFiles.toGitStoreConfig())
                                                         .varFileConfigs(terraformConfig.getVarFileConfigs())
                                                         .backendConfig(terraformConfig.backendConfig)
                                                         .environmentVariables(terraformConfig.environmentVariables)
                                                         .targets(terraformConfig.targets)
                                                         .workspace(terraformConfig.workspace)
                                                         .build();
    String inheritOutputName = "tfInheritOutput_APPLY_test-account/test-org/test-project/provId_";
    OptionalSweepingOutput optionalSweepingOutput =
        OptionalSweepingOutput.builder().found(true).output(terraformInheritOutput).build();
    Mockito.doReturn(optionalSweepingOutput)
        .when(mockExecutionSweepingOutputService)
        .resolveOptional(ambiance, RefObjectUtils.getSweepingOutputRefObject(inheritOutputName));
    helper.saveRollbackDestroyConfigInherited(parameters, ambiance);
    ArgumentCaptor<TerraformConfig> captor = ArgumentCaptor.forClass(TerraformConfig.class);
    then(terraformConfigDAL).should(times(1)).saveTerraformConfig(captor.capture());
    TerraformConfig config = captor.getValue();
    assertThat(config).isNotNull();
    assertThat(config.getAccountId()).isEqualTo("test-account");
    assertThat(config.getOrgId()).isEqualTo("test-org");
    assertThat(config.getProjectId()).isEqualTo("test-project");
  }

  @Test(expected = NullPointerException.class)
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testSaveRollbackDestroyConfigInheritedNegativeScenario() {
    Ambiance ambiance = getAmbiance();
    doReturn(
        ConnectorInfoDTO.builder().connectorConfig(GitConfigDTO.builder().gitAuthType(GitAuthType.SSH).build()).build())
        .when(cdStepHelper)
        .getConnector(anyString(), any());
    doReturn(SSHKeySpecDTO.builder().build())
        .when(mockGitConfigAuthenticationInfoHelper)
        .getSSHKey(any(), anyString(), anyString(), anyString());
    doReturn(Collections.emptyList())
        .when(mockGitConfigAuthenticationInfoHelper)
        .getEncryptedDataDetails(any(), any(), any());

    TerraformApplyStepParameters parameters =
        TerraformApplyStepParameters.infoBuilder()
            .provisionerIdentifier(ParameterField.createValueField("provId_"))
            .configuration(TerraformStepConfigurationParameters.builder()
                               .type(TerraformStepConfigurationType.INLINE)
                               .spec(TerraformExecutionDataParameters.builder().build())
                               .build())
            .build();
    TerraformConfig terraformConfig = TerraformConfig.builder().backendConfig("back-content").workspace("w1").build();
    ExecutionSweepingOutput terraformInheritOutput = TerraformInheritOutput.builder()
                                                         .varFileConfigs(terraformConfig.getVarFileConfigs())
                                                         .backendConfig(terraformConfig.backendConfig)
                                                         .environmentVariables(terraformConfig.environmentVariables)
                                                         .targets(terraformConfig.targets)
                                                         .workspace(terraformConfig.workspace)
                                                         .build();
    String inheritOutputName = "tfInheritOutput_test-account/test-org/test-project/provId_";
    OptionalSweepingOutput optionalSweepingOutput =
        OptionalSweepingOutput.builder().found(true).output(terraformInheritOutput).build();
    Mockito.doReturn(optionalSweepingOutput)
        .when(mockExecutionSweepingOutputService)
        .resolveOptional(ambiance, RefObjectUtils.getSweepingOutputRefObject(inheritOutputName));
    helper.saveRollbackDestroyConfigInherited(parameters, ambiance);
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testSaveTerraformConfig() {
    Ambiance ambiance = getAmbiance();
    ArgumentCaptor<TerraformConfig> captor = ArgumentCaptor.forClass(TerraformConfig.class);
    GitStoreConfigDTO configFiles = GithubStoreDTO.builder().branch("master").connectorRef("terraform").build();
    ArtifactoryStorageConfigDTO artifactoryStoreConfig = ArtifactoryStorageConfigDTO.builder().build();
    TerraformConfig terraformConfig = TerraformConfig.builder()
                                          .backendConfig("back-content")
                                          .workspace("w1")
                                          .fileStoreConfig(artifactoryStoreConfig)
                                          .configFiles(configFiles)
                                          .build();
    helper.saveTerraformConfig(terraformConfig, ambiance);
    then(terraformConfigDAL).should(times(1)).saveTerraformConfig(captor.capture());
    System.out.println("");
    TerraformConfig config = captor.getValue();
    assertThat(config.getVarFileConfigs()).isNull();
    assertThat(config.getAccountId()).isEqualTo("test-account");
    assertThat(config.getConfigFiles().toGitStoreConfig().getConnectorRef().getValue()).isEqualTo("terraform");
    assertThat(config.getConfigFiles().toGitStoreConfig().getBranch().getValue()).isEqualTo("master");
    assertThat(config.getFileStoreConfig()).isEqualTo(artifactoryStoreConfig);
    assertThat(config.getPipelineExecutionId()).isEqualTo("exec_id");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testSaveTerraformConfigForPipelineRollbackMode() {
    Ambiance ambiance = getAmbiance()
                            .toBuilder()
                            .setMetadata(ExecutionMetadata.newBuilder()
                                             .setExecutionMode(ExecutionMode.PIPELINE_ROLLBACK)
                                             .setOriginalPlanExecutionIdForRollbackMode("original_exec_id")
                                             .build())
                            .build();

    ArgumentCaptor<TerraformConfig> captor = ArgumentCaptor.forClass(TerraformConfig.class);
    GitStoreConfigDTO configFiles = GithubStoreDTO.builder().branch("master").connectorRef("terraform").build();
    ArtifactoryStorageConfigDTO artifactoryStoreConfig = ArtifactoryStorageConfigDTO.builder().build();

    GitStoreConfigDTO configFiles1 = GithubStoreDTO.builder()
                                         .branch("master")
                                         .repoName("terraform")
                                         .folderPath("test-path")
                                         .connectorRef("terraform")
                                         .gitFetchType(FetchType.COMMIT)
                                         .commitId("commit")
                                         .build();
    TerraformBackendConfigFileConfig remoteBackendConfig =
        TerraformRemoteBackendConfigFileConfig.builder().gitStoreConfigDTO(configFiles1).build();

    TerraformConfig terraformConfig = TerraformConfig.builder()
                                          .backendConfig("back-content")
                                          .workspace("w1")
                                          .fileStoreConfig(artifactoryStoreConfig)
                                          .configFiles(configFiles)
                                          .backendConfigFileConfig(remoteBackendConfig)
                                          .build();
    helper.saveTerraformConfig(terraformConfig, ambiance);
    then(terraformConfigDAL).should(times(1)).saveTerraformConfig(captor.capture());
    System.out.println("");
    TerraformConfig config = captor.getValue();
    assertThat(config.getVarFileConfigs()).isNull();
    assertThat(config.getAccountId()).isEqualTo("test-account");
    assertThat(config.getConfigFiles().toGitStoreConfig().getConnectorRef().getValue()).isEqualTo("terraform");
    assertThat(config.getConfigFiles().toGitStoreConfig().getBranch().getValue()).isEqualTo("master");
    assertThat(config.getFileStoreConfig()).isEqualTo(artifactoryStoreConfig);
    assertThat(config.getPipelineExecutionId()).isEqualTo("original_exec_id");
    assertThat(config.getBackendConfigFileConfig()).isSameAs(remoteBackendConfig);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetSavedInheritOutputFirstNotfound() {
    Ambiance ambiance = getAmbiance();
    ExecutionSweepingOutput terraformInheritOutput = TerraformInheritOutput.builder().build();
    OptionalSweepingOutput notFoundOptionalSweepingOutput =
        OptionalSweepingOutput.builder().found(false).output(null).build();
    OptionalSweepingOutput foundOptionalSweepingOutput =
        OptionalSweepingOutput.builder().found(true).output(terraformInheritOutput).build();
    Mockito.doReturn(foundOptionalSweepingOutput)
        .when(mockExecutionSweepingOutputService)
        .resolveOptional(ambiance,
            RefObjectUtils.getSweepingOutputRefObject("tfInheritOutput_test-account/test-org/test-project/test"));
    Mockito.doReturn(notFoundOptionalSweepingOutput)
        .when(mockExecutionSweepingOutputService)
        .resolveOptional(ambiance,
            RefObjectUtils.getSweepingOutputRefObject("tfInheritOutput_APPLY_test-account/test-org/test-project/test"));
    TerraformInheritOutput inheritOutput = helper.getSavedInheritOutput("test", "APPLY", ambiance);
    assertThat(inheritOutput).isEqualTo(terraformInheritOutput);
    verify(mockExecutionSweepingOutputService, times(2)).resolveOptional(any(), any());
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetSavedInheritOutputFoudFirst() {
    Ambiance ambiance = getAmbiance();
    ExecutionSweepingOutput terraformInheritOutput = TerraformInheritOutput.builder().build();
    OptionalSweepingOutput foundOptionalSweepingOutput =
        OptionalSweepingOutput.builder().found(true).output(terraformInheritOutput).build();
    Mockito.doReturn(foundOptionalSweepingOutput)
        .when(mockExecutionSweepingOutputService)
        .resolveOptional(ambiance,
            RefObjectUtils.getSweepingOutputRefObject("tfInheritOutput_APPLY_test-account/test-org/test-project/test"));
    TerraformInheritOutput inheritOutput = helper.getSavedInheritOutput("test", "APPLY", ambiance);
    assertThat(inheritOutput).isEqualTo(terraformInheritOutput);
    verify(mockExecutionSweepingOutputService, times(1)).resolveOptional(any(), any());
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testGetLatestFileIdNegativeScenario() {
    String entityId = "test-account/test-org/test-project/provId_";
    String str = String.format("Unable to call fileservice to fetch latest file id for entityId: [%s]", entityId);
    try {
      helper.getLatestFileId(entityId);
    } catch (InvalidRequestException invalidRequestException) {
      assertThat(invalidRequestException.getMessage()).isEqualTo(str);
    }
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testGetLatestFileId() {
    String entityId = "test-account/test-org/test-project/provId_";
    String str = String.format("Unable to call fileservice to fetch latest file id for entityId: [%s]", entityId);
    try {
      helper.getLatestFileId(entityId);
    } catch (InvalidRequestException invalidRequestException) {
      assertThat(invalidRequestException.getMessage()).isEqualTo(str);
    }

    verify(mockFileService).getLatestFileId(entityId, FileBucket.TERRAFORM_STATE);
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testUpdateParentEntityIdAndVersionNegativeScenario() {
    String entityId = "test-account/test-org/test-project/provId_";
    String stateFileId = "";
    String str =
        format("Unable to update StateFile version for entityId: [%s], Please try re-running pipeline", entityId);
    assertThatThrownBy(() -> helper.updateParentEntityIdAndVersion(entityId, stateFileId)).hasMessageContaining(str);
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testUpdateParentEntityIdAndVersion() throws UnsupportedEncodingException {
    String entityId = "test-account/test-org/test-project/provId_";
    String stateFileId = "";
    String str =
        format("Unable to update StateFile version for entityId: [%s], Please try re-running pipeline", entityId);
    try {
      helper.updateParentEntityIdAndVersion(entityId, stateFileId);
    } catch (InvalidRequestException invalidRequestException) {
      assertThat(invalidRequestException.getMessage()).isEqualTo(str);
    }

    verify(mockFileService)
        .updateParentEntityIdAndVersion(URLEncoder.encode(entityId, "UTF-8"), stateFileId, FileBucket.TERRAFORM_STATE);
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testValidateApplyStepParamsInline() {
    TerraformApplyStepParameters stepParameters =
        TerraformApplyStepParameters.infoBuilder()
            .configuration(
                TerraformStepConfigurationParameters.builder().type(TerraformStepConfigurationType.INLINE).build())
            .build();
    helper.validateApplyStepParamsInline(stepParameters);
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testValidateApplyStepParamsInlineNegativeScenario() {
    TerraformApplyStepParameters stepParameters =
        TerraformApplyStepParameters.infoBuilder()
            .configuration(
                TerraformStepConfigurationParameters.builder().type(TerraformStepConfigurationType.INLINE).build())
            .build();
    try {
      helper.validateApplyStepParamsInline(stepParameters);
    } catch (GeneralException generalException) {
      assertThat(generalException.getMessage()).isEqualTo("Apply Step configuration is NULL");
    }
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testValidateDestroyStepParamsInline() {
    TerraformDestroyStepParameters stepParameters =
        TerraformDestroyStepParameters.infoBuilder()
            .configuration(
                TerraformStepConfigurationParameters.builder().type(TerraformStepConfigurationType.INLINE).build())
            .build();
    helper.validateDestroyStepParamsInline(stepParameters);
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testValidateDestroyStepParamsInlineNegativeScenario() {
    TerraformDestroyStepParameters stepParameters = new TerraformDestroyStepParameters();
    try {
      helper.validateDestroyStepParamsInline(stepParameters);
    } catch (GeneralException generalException) {
      assertThat(generalException.getMessage()).isEqualTo("Destroy Step configuration is NULL");
    }
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testValidateDestroyStepConfigFilesInline() {
    TerraformConfigFilesWrapper configFilesWrapper = new TerraformConfigFilesWrapper();
    configFilesWrapper.setStore(StoreConfigWrapper.builder()
                                    .spec(GithubStore.builder()
                                              .branch(ParameterField.createValueField("master"))
                                              .gitFetchType(FetchType.BRANCH)
                                              .connectorRef(ParameterField.createValueField("terraform"))
                                              .folderPath(ParameterField.createValueField("Config/"))
                                              .build())
                                    .type(StoreConfigType.GITHUB)
                                    .build());
    TerraformDestroyStepParameters stepParameters =
        TerraformDestroyStepParameters.infoBuilder()
            .configuration(TerraformStepConfigurationParameters.builder()
                               .type(TerraformStepConfigurationType.INLINE)
                               .spec(TerraformExecutionDataParameters.builder().configFiles(configFilesWrapper).build())
                               .build())
            .build();
    helper.validateDestroyStepConfigFilesInline(stepParameters);
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testValidateDestroyStepConfigFilesInlineNegativeScenario() {
    TerraformConfigFilesWrapper configFilesWrapper = new TerraformConfigFilesWrapper();
    TerraformDestroyStepParameters stepParameters =
        TerraformDestroyStepParameters.infoBuilder()
            .configuration(TerraformStepConfigurationParameters.builder()
                               .type(TerraformStepConfigurationType.INLINE)
                               .spec(TerraformExecutionDataParameters.builder().configFiles(configFilesWrapper).build())
                               .build())
            .build();
    try {
      helper.validateDestroyStepConfigFilesInline(stepParameters);
    } catch (GeneralException generalException) {
      assertThat(generalException.getMessage()).isEqualTo("Destroy Step Spec does not have Config files store");
    }
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testParseTerraformOutputs() {
    String terraformOutputString = "demo";

    Map<String, Object> response = helper.parseTerraformOutputs(terraformOutputString);
    assertThat(response.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testSaveRollbackDestroyConfigInlineOtherStore() {
    Ambiance ambiance = getAmbiance();
    TerraformStepDataGenerator.GitStoreConfig gitStoreConfigFiles =
        TerraformStepDataGenerator.GitStoreConfig.builder()
            .branch("master")
            .fetchType(FetchType.BRANCH)
            .folderPath(ParameterField.createValueField("Config/"))
            .connectoref(ParameterField.createValueField("ConnectorRef"))
            .build();
    TerraformStepDataGenerator.GitStoreConfig gitStoreVarFiles =
        TerraformStepDataGenerator.GitStoreConfig.builder()
            .branch("master")
            .fetchType(FetchType.BRANCH)
            .folderPath(ParameterField.createValueField("VarFiles/"))
            .varFolderPath(ParameterField.createValueField(Collections.singletonList("VarFiles/")))
            .connectoref(ParameterField.createValueField("ConnectorRef"))
            .build();
    TerraformConfigFilesWrapper configFilesWrapper = new TerraformConfigFilesWrapper();
    TerraformStepDataGenerator.generateConfigFileStore(
        configFilesWrapper, StoreConfigType.BITBUCKET, gitStoreConfigFiles);
    RemoteTerraformVarFileSpec remoteVarFiles =
        TerraformStepDataGenerator.generateRemoteVarFileSpec(StoreConfigType.GIT, gitStoreVarFiles);
    LinkedHashMap<String, TerraformVarFile> varFilesMap =
        TerraformStepDataGenerator.generateVarFileSpecs(remoteVarFiles, false);

    TerraformApplyStepParameters parameters =
        TerraformApplyStepParameters.infoBuilder()
            .provisionerIdentifier(ParameterField.createValueField("provId_"))
            .configuration(TerraformStepConfigurationParameters.builder()
                               .type(TerraformStepConfigurationType.INLINE)
                               .spec(TerraformExecutionDataParameters.builder()
                                         .configFiles(configFilesWrapper)
                                         .varFiles(varFilesMap)
                                         .isTerraformCloudCli(ParameterField.createValueField(false))
                                         .build())
                               .build())
            .build();
    TerraformTaskNGResponse response = TerraformTaskNGResponse.builder()
                                           .commitIdForConfigFilesMap(ImmutableMap.of(TF_CONFIG_FILES, "commit-1",
                                               String.format(TerraformStepHelper.TF_VAR_FILES, 1), "commit-2"))
                                           .build();
    helper.saveRollbackDestroyConfigInline(parameters, response, ambiance, null);
    ArgumentCaptor<TerraformConfig> captor = ArgumentCaptor.forClass(TerraformConfig.class);
    verify(terraformConfigDAL).saveTerraformConfig(captor.capture());
    TerraformConfig config = captor.getValue();
    assertThat(config).isNotNull();
    GitStoreConfigDTO configFiles = config.getConfigFiles();
    assertThat(configFiles instanceof BitBucketStoreDTO).isTrue();
    BitBucketStoreDTO bitBucketStoreDTO = (BitBucketStoreDTO) configFiles;
    assertThat(bitBucketStoreDTO.getGitFetchType()).isEqualTo(FetchType.COMMIT);
    assertThat(bitBucketStoreDTO.getCommitId()).isEqualTo("commit-1");
    List<TerraformVarFileConfig> varFileConfigs = config.getVarFileConfigs();
    assertThat(varFileConfigs).isNotNull();
    assertThat(varFileConfigs.size()).isEqualTo(1);
    TerraformVarFileConfig terraformVarFileConfig = varFileConfigs.get(0);
    assertThat(terraformVarFileConfig instanceof TerraformRemoteVarFileConfig).isTrue();
    TerraformRemoteVarFileConfig remoteVarFileConfig = (TerraformRemoteVarFileConfig) terraformVarFileConfig;
    GitStoreConfigDTO gitStoreConfigDTO = remoteVarFileConfig.getGitStoreConfigDTO();
    assertThat(gitStoreConfigDTO instanceof GitStoreDTO).isTrue();
    GitStoreDTO gitStoreDTO = (GitStoreDTO) gitStoreConfigDTO;
    assertThat(gitStoreDTO.getGitFetchType()).isEqualTo(FetchType.COMMIT);
    assertThat(gitStoreDTO.getCommitId()).isEqualTo("commit-2");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testSaveTerraformPlanJsonOutput() {
    final Ambiance ambiance = getAmbiance();
    final TerraformTaskNGResponse response = TerraformTaskNGResponse.builder().tfPlanJsonFileId("plan_file").build();
    final String expectedOutputName = TerraformPlanJsonOutput.getOutputName("provisioner1");

    String outputName = helper.saveTerraformPlanJsonOutput(ambiance, response, "provisioner1");

    ArgumentCaptor<TerraformPlanJsonOutput> outputCaptor = ArgumentCaptor.forClass(TerraformPlanJsonOutput.class);
    verify(mockExecutionSweepingOutputService)
        .consume(eq(ambiance), eq(expectedOutputName), outputCaptor.capture(), eq(StepCategory.STEP.name()));
    TerraformPlanJsonOutput output = outputCaptor.getValue();
    assertThat(outputName).isEqualTo(expectedOutputName);
    assertThat(output.getProvisionerIdentifier()).isEqualTo("provisioner1");
    assertThat(output.getTfPlanFileId()).isEqualTo("plan_file");
    assertThat(output.getTfPlanFileBucket()).isEqualTo(FileBucket.TERRAFORM_PLAN_JSON.name());
  }

  @Test
  @Owner(developers = AKHIL_PANDEY)
  @Category(UnitTests.class)
  public void testSaveTerraformHumanPlanOutput() {
    final Ambiance ambiance = getAmbiance();

    String fileId = "human_plan_id";

    String provisioner = "provisioner1";

    final TerraformTaskNGResponse response =
        TerraformTaskNGResponse.builder().tfHumanReadablePlanFileId(fileId).build();
    final String expectedOutputName = TerraformHumanReadablePlanOutput.getOutputName(provisioner);

    String outputName = helper.saveTerraformPlanHumanReadableOutput(ambiance, response, provisioner);

    ArgumentCaptor<TerraformHumanReadablePlanOutput> outputCaptor =
        ArgumentCaptor.forClass(TerraformHumanReadablePlanOutput.class);
    verify(mockExecutionSweepingOutputService)
        .consume(eq(ambiance), eq(expectedOutputName), outputCaptor.capture(), eq(StepCategory.STEP.name()));
    TerraformHumanReadablePlanOutput output = outputCaptor.getValue();
    assertThat(outputName).isEqualTo(expectedOutputName);
    assertThat(output.getProvisionerIdentifier()).isEqualTo(provisioner);
    assertThat(output.getTfPlanFileId()).isEqualTo(fileId);
    assertThat(output.getTfPlanFileBucket()).isEqualTo(FileBucket.TERRAFORM_HUMAN_READABLE_PLAN.name());
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testSaveTerraformPlanExecutionDetails() {
    final Ambiance ambiance = getAmbiance();
    String planExecutionId = ambiance.getPlanExecutionId();
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String projectId = AmbianceUtils.getProjectIdentifier(ambiance);
    String orgId = AmbianceUtils.getOrgIdentifier(ambiance);
    String stageExecutionId = ambiance.getStageExecutionId();

    List<EncryptedRecordData> encryptedRecordData = List.of(EncryptedRecordData.builder().build());

    TerraformPlanExecutionDetails terraformPlanExecutionDetails =
        TerraformPlanExecutionDetails.builder()
            .accountIdentifier(accountId)
            .orgIdentifier(orgId)
            .projectIdentifier(projectId)
            .pipelineExecutionId(planExecutionId)
            .stageExecutionId(stageExecutionId)
            .provisionerId("provisioner1")
            .tfPlanJsonFieldId("testId")
            .tfPlanFileBucket(FileBucket.TERRAFORM_PLAN_JSON.name())
            .tfHumanReadablePlanId("humanReadablePlanID")
            .tfHumanReadablePlanFileBucket(FileBucket.TERRAFORM_HUMAN_READABLE_PLAN.name())
            .encryptedTfPlan(encryptedRecordData)
            .build();

    TerraformTaskNGResponse response = TerraformTaskNGResponse.builder()
                                           .tfPlanJsonFileId("testId")
                                           .tfHumanReadablePlanFileId("humanReadablePlanID")
                                           .encryptedTfPlan(EncryptedRecordData.builder().build())
                                           .build();

    TerraformPlanStepParameters terraformPlanStepParameters =
        TerraformPlanStepParameters.infoBuilder()
            .configuration(TerraformPlanExecutionDataParameters.builder()
                               .secretManagerRef(ParameterField.createValueField("secretManagerRefTest"))
                               .build())
            .build();

    helper.saveTerraformPlanExecutionDetails(ambiance, response, "provisioner1", terraformPlanStepParameters);

    ArgumentCaptor<TerraformPlanExecutionDetails> outputCaptor =
        ArgumentCaptor.forClass(TerraformPlanExecutionDetails.class);
    verify(terraformPlanExectionDetailsService).save(outputCaptor.capture());
    TerraformPlanExecutionDetails output = outputCaptor.getValue();
    assertThat(output).isEqualTo(terraformPlanExecutionDetails);
  }

  @Test
  @Owner(developers = AKHIL_PANDEY)
  @Category(UnitTests.class)
  public void testSaveTerraformHumanReadablePlanExecutionDetails() {
    final Ambiance ambiance = getAmbiance();
    String planExecutionId = ambiance.getPlanExecutionId();
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String projectId = AmbianceUtils.getProjectIdentifier(ambiance);
    String orgId = AmbianceUtils.getOrgIdentifier(ambiance);
    String stageExecutionId = ambiance.getStageExecutionId();

    List<EncryptedRecordData> encryptedRecordData = List.of(EncryptedRecordData.builder().build());

    TerraformPlanExecutionDetails terraformPlanExecutionDetails =
        TerraformPlanExecutionDetails.builder()
            .accountIdentifier(accountId)
            .orgIdentifier(orgId)
            .projectIdentifier(projectId)
            .pipelineExecutionId(planExecutionId)
            .stageExecutionId(stageExecutionId)
            .provisionerId("provisioner1")
            .tfPlanJsonFieldId("testId")
            .tfPlanFileBucket(FileBucket.TERRAFORM_PLAN_JSON.name())
            .tfHumanReadablePlanFileBucket(FileBucket.TERRAFORM_HUMAN_READABLE_PLAN.name())
            .encryptedTfPlan(encryptedRecordData)
            .encryptionConfig(VaultConfig.builder()
                                  .basePath("testBasePath")
                                  .renewAppRoleToken(false)
                                  .encryptionType(EncryptionType.VAULT)
                                  .build())
            .build();

    doReturn(VaultConfigDTO.builder()
                 .basePath("testBasePath")
                 .renewAppRoleToken(false)
                 .encryptionType(EncryptionType.VAULT)
                 .build())
        .when(mockSecretManagerClientService)
        .getSecretManager(anyString(), anyString(), anyString(), anyString(), anyBoolean());

    TerraformTaskNGResponse response = TerraformTaskNGResponse.builder()
                                           .tfPlanJsonFileId("testId")
                                           .encryptedTfPlan(EncryptedRecordData.builder().build())
                                           .build();

    TerraformPlanStepParameters terraformPlanStepParameters =
        TerraformPlanStepParameters.infoBuilder()
            .configuration(TerraformPlanExecutionDataParameters.builder()
                               .secretManagerRef(ParameterField.createValueField("secretManagerRefTest"))
                               .build())

            .build();
    helper.saveTerraformPlanExecutionDetails(ambiance, response, "provisioner1", terraformPlanStepParameters);

    ArgumentCaptor<TerraformPlanExecutionDetails> outputCaptor =
        ArgumentCaptor.forClass(TerraformPlanExecutionDetails.class);
    verify(terraformPlanExectionDetailsService).save(outputCaptor.capture());
    TerraformPlanExecutionDetails output = outputCaptor.getValue();
  }

  @Test
  @Owner(developers = AKHIL_PANDEY)
  @Category(UnitTests.class)
  public void testCleanupTfHumanReadablePlan() {
    final Ambiance ambiance = getAmbiance();
    String planExecutionId = ambiance.getPlanExecutionId();
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String projectId = AmbianceUtils.getProjectIdentifier(ambiance);
    String orgId = AmbianceUtils.getOrgIdentifier(ambiance);
    String stageExecutionId = ambiance.getStageExecutionId();

    String fileId = "human_plan_id";
    String provisioner = "provisioner1";

    TerraformPlanExecutionDetails terraformPlanExecutionDetails =
        TerraformPlanExecutionDetails.builder()
            .accountIdentifier(accountId)
            .orgIdentifier(orgId)
            .projectIdentifier(projectId)
            .pipelineExecutionId(planExecutionId)
            .stageExecutionId(stageExecutionId)
            .provisionerId(provisioner)
            .tfHumanReadablePlanId(fileId)
            .tfHumanReadablePlanFileBucket(FileBucket.TERRAFORM_HUMAN_READABLE_PLAN.name())
            .build();

    doReturn(Collections.singletonList(terraformPlanExecutionDetails))
        .when(terraformPlanExectionDetailsService)
        .listAllPipelineTFPlanExecutionDetails(any(TFPlanExecutionDetailsKey.class));
    helper.cleanupTfPlanHumanReadable(List.of(terraformPlanExecutionDetails));

    ArgumentCaptor<String> outputCaptor = ArgumentCaptor.forClass(String.class);
    doReturn(mockFileService).when(mockFileServiceFactory).get();
    verify(mockFileService).deleteFile(outputCaptor.capture(), any(FileBucket.class));
    String output = outputCaptor.getValue();
    assertThat(output).isEqualTo(fileId);
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testCleanupTfPlanJson() {
    final Ambiance ambiance = getAmbiance();
    String planExecutionId = ambiance.getPlanExecutionId();
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String projectId = AmbianceUtils.getProjectIdentifier(ambiance);
    String orgId = AmbianceUtils.getOrgIdentifier(ambiance);
    String stageExecutionId = ambiance.getStageExecutionId();
    List<EncryptedRecordData> encryptedRecordData = List.of(EncryptedRecordData.builder().build());
    TerraformPlanExecutionDetails terraformPlanExecutionDetails =
        TerraformPlanExecutionDetails.builder()
            .accountIdentifier(accountId)
            .orgIdentifier(orgId)
            .projectIdentifier(projectId)
            .pipelineExecutionId(planExecutionId)
            .stageExecutionId(stageExecutionId)
            .provisionerId("provisioner1")
            .tfPlanJsonFieldId("testId")
            .tfPlanFileBucket(FileBucket.TERRAFORM_PLAN_JSON.name())
            .encryptedTfPlan(encryptedRecordData)
            .encryptionConfig(VaultConfig.builder()
                                  .basePath("testBasePath")
                                  .renewAppRoleToken(false)
                                  .encryptionType(EncryptionType.VAULT)
                                  .build())
            .build();

    doReturn(Collections.singletonList(terraformPlanExecutionDetails))
        .when(terraformPlanExectionDetailsService)
        .listAllPipelineTFPlanExecutionDetails(any(TFPlanExecutionDetailsKey.class));
    helper.cleanupTfPlanJson(List.of(terraformPlanExecutionDetails));

    ArgumentCaptor<String> outputCaptor = ArgumentCaptor.forClass(String.class);
    doReturn(mockFileService).when(mockFileServiceFactory).get();
    verify(mockFileService).deleteFile(outputCaptor.capture(), any(FileBucket.class));
    String output = outputCaptor.getValue();
    assertThat(output).isEqualTo("testId");
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testSaveTfPlanExecutionDetailsWithEncryptionConfigAndTfPlan() {
    final Ambiance ambiance = getAmbiance();
    String planExecutionId = ambiance.getPlanExecutionId();
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String projectId = AmbianceUtils.getProjectIdentifier(ambiance);
    String orgId = AmbianceUtils.getOrgIdentifier(ambiance);
    String stageExecutionId = ambiance.getStageExecutionId();

    List<EncryptedRecordData> encryptedRecordData = List.of(EncryptedRecordData.builder().build());

    TerraformPlanExecutionDetails terraformPlanExecutionDetails =
        TerraformPlanExecutionDetails.builder()
            .accountIdentifier(accountId)
            .orgIdentifier(orgId)
            .projectIdentifier(projectId)
            .pipelineExecutionId(planExecutionId)
            .stageExecutionId(stageExecutionId)
            .provisionerId("provisioner1")
            .tfPlanJsonFieldId("testId")
            .tfPlanFileBucket(FileBucket.TERRAFORM_PLAN_JSON.name())
            .encryptedTfPlan(encryptedRecordData)
            .encryptionConfig(VaultConfig.builder()
                                  .basePath("testBasePath")
                                  .renewAppRoleToken(false)
                                  .encryptionType(EncryptionType.VAULT)
                                  .build())
            .build();

    doReturn(VaultConfigDTO.builder()
                 .basePath("testBasePath")
                 .renewAppRoleToken(false)
                 .encryptionType(EncryptionType.VAULT)
                 .build())
        .when(mockSecretManagerClientService)
        .getSecretManager(anyString(), anyString(), anyString(), anyString(), anyBoolean());

    TerraformTaskNGResponse response = TerraformTaskNGResponse.builder()
                                           .tfPlanJsonFileId("testId")
                                           .encryptedTfPlan(EncryptedRecordData.builder().build())
                                           .build();

    TerraformPlanStepParameters terraformPlanStepParameters =
        TerraformPlanStepParameters.infoBuilder()
            .configuration(TerraformPlanExecutionDataParameters.builder()
                               .secretManagerRef(ParameterField.createValueField("secretManagerRefTest"))
                               .build())

            .build();

    helper.saveTerraformPlanExecutionDetails(ambiance, response, "provisioner1", terraformPlanStepParameters);

    ArgumentCaptor<TerraformPlanExecutionDetails> outputCaptor =
        ArgumentCaptor.forClass(TerraformPlanExecutionDetails.class);
    verify(terraformPlanExectionDetailsService).save(outputCaptor.capture());
    TerraformPlanExecutionDetails output = outputCaptor.getValue();

    assertThat(((VaultConfig) output.getEncryptionConfig()).getBasePath())
        .isEqualTo(((VaultConfig) terraformPlanExecutionDetails.getEncryptionConfig()).getBasePath());
    assertThat(output.getEncryptedTfPlan()).isEqualTo(terraformPlanExecutionDetails.getEncryptedTfPlan());
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testEncryptedTfPlanWithConfig() {
    TerraformPlanExecutionDetails tfPlanExecutionDetails_1 = createTfPlanExecutionDetails("encryption-config-1",
        "test-identifier-id-1", "test-project-id-1", "test-account-id-1", "test-org-id-1", "test-encrypted-tf-plan-1");

    TerraformPlanExecutionDetails tfPlanExecutionDetails_2 = createTfPlanExecutionDetails("encryption-config-1",
        "test-identifier-id-1", "test-project-id-1", "test-account-id-1", "test-org-id-1", "test-encrypted-tf-plan-2");

    TerraformPlanExecutionDetails tfPlanExecutionDetails_3 = createTfPlanExecutionDetails("encryption-config-2",
        "test-identifier-id-2", "test-project-id-2", "test-account-id-2", "test-org-id-2", "test-encrypted-tf-plan-3");

    Map<EncryptionConfig, List<EncryptedRecordData>> result = helper.getEncryptedTfPlanWithConfig(
        List.of(tfPlanExecutionDetails_1, tfPlanExecutionDetails_2, tfPlanExecutionDetails_3));
    assertThat(result.size()).isEqualTo(2);

    result.forEach((key, value) -> {
      if (key.getName().equalsIgnoreCase("encryption-config-1")) {
        assertThat(value.size()).isEqualTo(2);
        assertThat(value.get(0).getName()).isEqualTo("test-encrypted-tf-plan-1");
        assertThat(value.get(1).getName()).isEqualTo("test-encrypted-tf-plan-2");
      }

      if (key.getName().equalsIgnoreCase("encryption-config-2")) {
        assertThat(value.size()).isEqualTo(1);
        assertThat(value.get(0).getName()).isEqualTo("test-encrypted-tf-plan-3");
      }
    });
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testGetFileStoreFetchFilesConfigForS3() {
    Ambiance ambiance = getAmbiance();
    doReturn(true).when(cdFeatureFlagHelper).isEnabled(anyString(), any());
    S3StoreConfig s3StoreConfig = S3StoreConfig.builder()
                                      .connectorRef(ParameterField.createValueField("connectorRef"))
                                      .region(ParameterField.createValueField("region"))
                                      .bucketName(ParameterField.createValueField("bucket"))
                                      .folderPath(ParameterField.createValueField("terraform"))
                                      .build();
    doReturn(TerraformStepDataGenerator.getAWSConnectorInfoDTO()).when(cdStepHelper).getConnector(any(), any());
    doNothing().when(cdStepHelper).validateManifest(any(), any(), any());
    doReturn(null).when(mockSecretManagerClientService).getEncryptionDetails(any(), any());

    FileStoreFetchFilesConfig fileStoreFetchFilesConfig =
        helper.getFileStoreFetchFilesConfig(s3StoreConfig, ambiance, TF_CONFIG_FILES);

    assertThat(fileStoreFetchFilesConfig).isInstanceOf(S3StoreTFDelegateConfig.class);
    S3StoreTFDelegateConfig s3Store = (S3StoreTFDelegateConfig) fileStoreFetchFilesConfig;
    assertThat(s3Store.getBucketName()).isEqualTo("bucket");
    assertThat(s3Store.getRegion()).isEqualTo("region");
    assertThat(s3Store.getPaths().get(0)).isEqualTo("terraform");
    assertThat(s3Store.getVersions()).isNull();
    assertThat(s3Store.getConnectorDTO().getConnectorConfig()).isInstanceOf(AwsConnectorDTO.class);
    assertThat(s3Store.getIdentifier()).isEqualTo(TF_CONFIG_FILES);
    assertThat(s3Store.getManifestStoreType()).isEqualTo(ManifestStoreType.S3);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testGetFileStoreFetchFilesBackendConfigForS3() {
    Ambiance ambiance = getAmbiance();
    doReturn(true).when(cdFeatureFlagHelper).isEnabled(anyString(), any());
    S3StoreConfig s3StoreConfig =
        S3StoreConfig.builder()
            .connectorRef(ParameterField.createValueField("connectorRef"))
            .region(ParameterField.createValueField("region"))
            .bucketName(ParameterField.createValueField("bucket"))
            .paths(ParameterField.createValueField(Collections.singletonList("terraform-be/backend.tf")))
            .build();
    doReturn(TerraformStepDataGenerator.getAWSConnectorInfoDTO()).when(cdStepHelper).getConnector(any(), any());
    doNothing().when(cdStepHelper).validateManifest(any(), any(), any());
    doReturn(null).when(mockSecretManagerClientService).getEncryptionDetails(any(), any());

    FileStoreFetchFilesConfig fileStoreFetchFilesConfig =
        helper.getFileStoreFetchFilesConfig(s3StoreConfig, ambiance, TF_BACKEND_CONFIG_FILE);

    assertThat(fileStoreFetchFilesConfig).isInstanceOf(S3StoreTFDelegateConfig.class);
    S3StoreTFDelegateConfig s3Store = (S3StoreTFDelegateConfig) fileStoreFetchFilesConfig;
    assertThat(s3Store.getBucketName()).isEqualTo("bucket");
    assertThat(s3Store.getRegion()).isEqualTo("region");
    assertThat(s3Store.getPaths().get(0)).isEqualTo("terraform-be/backend.tf");
    assertThat(s3Store.getVersions()).isNull();
    assertThat(s3Store.getConnectorDTO().getConnectorConfig()).isInstanceOf(AwsConnectorDTO.class);
    assertThat(s3Store.getIdentifier()).isEqualTo(TF_BACKEND_CONFIG_FILE);
    assertThat(s3Store.getManifestStoreType()).isEqualTo(ManifestStoreType.S3);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testGetFileStoreFetchVarFilesConfigForS3() {
    Ambiance ambiance = getAmbiance();
    doReturn(true).when(cdFeatureFlagHelper).isEnabled(anyString(), any());
    S3StoreConfig s3StoreConfig =
        S3StoreConfig.builder()
            .connectorRef(ParameterField.createValueField("connectorRef"))
            .region(ParameterField.createValueField("region"))
            .bucketName(ParameterField.createValueField("bucket"))
            .paths(ParameterField.createValueField(List.of("terraform/var1", "terraform/var2")))
            .build();
    doReturn(TerraformStepDataGenerator.getAWSConnectorInfoDTO()).when(cdStepHelper).getConnector(any(), any());
    doNothing().when(cdStepHelper).validateManifest(any(), any(), any());
    doReturn(null).when(mockSecretManagerClientService).getEncryptionDetails(any(), any());

    FileStoreFetchFilesConfig fileStoreFetchFilesConfig =
        helper.getFileStoreFetchFilesConfig(s3StoreConfig, ambiance, TerraformStepHelper.TF_VAR_FILES);

    assertThat(fileStoreFetchFilesConfig).isInstanceOf(S3StoreTFDelegateConfig.class);
    S3StoreTFDelegateConfig s3Store = (S3StoreTFDelegateConfig) fileStoreFetchFilesConfig;
    assertThat(s3Store.getBucketName()).isEqualTo("bucket");
    assertThat(s3Store.getRegion()).isEqualTo("region");
    assertTrue(s3Store.getPaths().contains("terraform/var1"));
    assertTrue(s3Store.getPaths().contains("terraform/var2"));
    assertThat(s3Store.getVersions()).isNull();
    assertThat(s3Store.getConnectorDTO().getConnectorConfig()).isInstanceOf(AwsConnectorDTO.class);
    assertThat(s3Store.getIdentifier()).isEqualTo(TerraformStepHelper.TF_VAR_FILES);
    assertThat(s3Store.getManifestStoreType()).isEqualTo(ManifestStoreType.S3);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testSaveRollbackDestroyConfigS3Inline() {
    Ambiance ambiance = getAmbiance();
    doReturn(true).when(cdFeatureFlagHelper).isEnabled(anyString(), any());
    S3StoreConfig s3StoreConfigFiles = S3StoreConfig.builder()
                                           .connectorRef(ParameterField.createValueField("connectorRef"))
                                           .region(ParameterField.createValueField("region"))
                                           .bucketName(ParameterField.createValueField("bucket"))
                                           .folderPath(ParameterField.createValueField("terraform"))
                                           .build();
    S3StoreConfig s3StoreVarFiles = (S3StoreConfig) s3StoreConfigFiles.cloneInternal();
    s3StoreVarFiles.setPaths(ParameterField.createValueField(List.of("terraform/var1", "terraform/var2")));
    S3StoreConfig s3StoreBeFiles = (S3StoreConfig) s3StoreConfigFiles.cloneInternal();
    s3StoreBeFiles.setFolderPath(ParameterField.createValueField("terraformBe"));

    TerraformConfigFilesWrapper configFilesWrapper = new TerraformConfigFilesWrapper();
    TerraformStepDataGenerator.generateConfigFileStore(configFilesWrapper, StoreConfigType.S3, s3StoreConfigFiles);
    RemoteTerraformVarFileSpec remoteVarFiles =
        TerraformStepDataGenerator.generateRemoteVarFileSpec(StoreConfigType.S3, s3StoreVarFiles);
    LinkedHashMap<String, TerraformVarFile> varFilesMap =
        TerraformStepDataGenerator.generateVarFileSpecs(remoteVarFiles, false);
    RemoteTerraformBackendConfigSpec remoteTerraformBackendConfigSpec =
        TerraformStepDataGenerator.generateRemoteBackendConfigFileSpec(StoreConfigType.S3, s3StoreBeFiles);
    TerraformBackendConfig terraformBackendConfig =
        TerraformBackendConfig.builder().type("Remote").spec(remoteTerraformBackendConfigSpec).build();

    TerraformApplyStepParameters parameters =
        TerraformApplyStepParameters.infoBuilder()
            .provisionerIdentifier(ParameterField.createValueField("provId_"))
            .configuration(TerraformStepConfigurationParameters.builder()
                               .type(TerraformStepConfigurationType.INLINE)
                               .spec(TerraformExecutionDataParameters.builder()
                                         .configFiles(configFilesWrapper)
                                         .varFiles(varFilesMap)
                                         .backendConfig(terraformBackendConfig)
                                         .isTerraformCloudCli(ParameterField.createValueField(false))
                                         .build())
                               .build())
            .build();
    Map<String, Map<String, String>> keyVersionMap = new HashMap();
    keyVersionMap.put("TF_CONFIG_FILES", Map.of("main.tf", "111", "file2", "112", "file3", "113"));
    keyVersionMap.put("TF_VAR_FILES_1", Map.of("terraform/var1", "222", "terraform/var2", "333"));
    keyVersionMap.put("TF_BACKEND_CONFIG_FILE", Map.of("terraform/backend.tf", "444"));
    TerraformTaskNGResponse response = TerraformTaskNGResponse.builder().keyVersionMap(keyVersionMap).build();
    helper.saveRollbackDestroyConfigInline(parameters, response, ambiance, null);
    ArgumentCaptor<TerraformConfig> captor = ArgumentCaptor.forClass(TerraformConfig.class);
    verify(terraformConfigDAL).saveTerraformConfig(captor.capture());
    TerraformConfig config = captor.getValue();
    assertThat(config).isNotNull();
    assertThat(config.getAccountId()).isEqualTo("test-account");
    assertThat(config.getOrgId()).isEqualTo("test-org");
    assertThat(config.getProjectId()).isEqualTo("test-project");
    assertTrue(config.getFileStoreConfig() instanceof S3StorageConfigDTO);
    S3StorageConfigDTO s3StorageConfigDTO = (S3StorageConfigDTO) config.getFileStoreConfig();
    assertThat(s3StorageConfigDTO.getRegion()).isEqualTo("region");
    assertThat(s3StorageConfigDTO.getBucket()).isEqualTo("bucket");
    assertThat(s3StorageConfigDTO.getFolderPath()).isEqualTo("terraform");
    assertThat(s3StorageConfigDTO.getVersions().size()).isEqualTo(3);
    assertThat(s3StorageConfigDTO.getVersions().get("main.tf")).isEqualTo("111");
    assertThat(s3StorageConfigDTO.getVersions().get("file2")).isEqualTo("112");
    assertThat(s3StorageConfigDTO.getVersions().get("file3")).isEqualTo("113");
    List<TerraformVarFileConfig> varFileConfigs = config.getVarFileConfigs();
    assertThat(varFileConfigs).isNotNull();
    assertThat(varFileConfigs.size()).isEqualTo(1);
    TerraformVarFileConfig terraformVarFileConfig = varFileConfigs.get(0);
    assertTrue(terraformVarFileConfig instanceof TerraformRemoteVarFileConfig);
    TerraformRemoteVarFileConfig remoteVarFileConfig = (TerraformRemoteVarFileConfig) terraformVarFileConfig;
    assertTrue(remoteVarFileConfig.getFileStoreConfigDTO() instanceof S3StorageConfigDTO);
    S3StorageConfigDTO s3StorageVarsDTO = (S3StorageConfigDTO) remoteVarFileConfig.getFileStoreConfigDTO();
    assertThat(s3StorageVarsDTO.getRegion()).isEqualTo("region");
    assertThat(s3StorageVarsDTO.getBucket()).isEqualTo("bucket");
    assertThat(s3StorageVarsDTO.getPaths().size()).isEqualTo(2);
    assertTrue(s3StorageVarsDTO.getPaths().contains("terraform/var1"));
    assertTrue(s3StorageVarsDTO.getPaths().contains("terraform/var2"));
    assertThat(s3StorageVarsDTO.getVersions().get("terraform/var1")).isEqualTo("222");
    assertThat(s3StorageVarsDTO.getVersions().get("terraform/var2")).isEqualTo("333");
    TerraformRemoteBackendConfigFileConfig backendConfigFileConfig =
        (TerraformRemoteBackendConfigFileConfig) config.getBackendConfigFileConfig();
    assertTrue(backendConfigFileConfig.getFileStoreConfigDTO() instanceof S3StorageConfigDTO);
    S3StorageConfigDTO backendS3Storage = (S3StorageConfigDTO) backendConfigFileConfig.getFileStoreConfigDTO();
    assertThat(backendS3Storage.getRegion()).isEqualTo("region");
    assertThat(backendS3Storage.getBucket()).isEqualTo("bucket");
    assertThat(backendS3Storage.getFolderPath()).isEqualTo("terraformBe");
    assertThat(backendS3Storage.getVersions().size()).isEqualTo(1);
    assertThat(backendS3Storage.getVersions().get("terraform/backend.tf")).isEqualTo("444");
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testSaveTerraformInheritOutputWithS3Store() {
    Ambiance ambiance = getAmbiance();
    doReturn(true).when(cdFeatureFlagHelper).isEnabled(anyString(), any());
    S3StoreConfig s3StoreConfigFiles = S3StoreConfig.builder()
                                           .connectorRef(ParameterField.createValueField("connectorRef"))
                                           .region(ParameterField.createValueField("region"))
                                           .bucketName(ParameterField.createValueField("bucket"))
                                           .folderPath(ParameterField.createValueField("terraform"))
                                           .build();
    S3StoreConfig s3StoreVarFiles = (S3StoreConfig) s3StoreConfigFiles.cloneInternal();
    s3StoreVarFiles.setPaths(ParameterField.createValueField(List.of("terraform/var1", "terraform/var2")));
    S3StoreConfig s3StoreBeFiles = (S3StoreConfig) s3StoreConfigFiles.cloneInternal();
    s3StoreBeFiles.setFolderPath(ParameterField.createValueField("terraformBe"));

    TerraformConfigFilesWrapper configFilesWrapper = new TerraformConfigFilesWrapper();
    TerraformStepDataGenerator.generateConfigFileStore(configFilesWrapper, StoreConfigType.S3, s3StoreConfigFiles);
    RemoteTerraformVarFileSpec remoteVarFiles =
        TerraformStepDataGenerator.generateRemoteVarFileSpec(StoreConfigType.S3, s3StoreVarFiles);
    LinkedHashMap<String, TerraformVarFile> varFilesMap =
        TerraformStepDataGenerator.generateVarFileSpecs(remoteVarFiles, false);
    RemoteTerraformBackendConfigSpec remoteTerraformBackendConfigSpec =
        TerraformStepDataGenerator.generateRemoteBackendConfigFileSpec(StoreConfigType.S3, s3StoreBeFiles);
    TerraformBackendConfig terraformBackendConfig =
        TerraformBackendConfig.builder().type("Remote").spec(remoteTerraformBackendConfigSpec).build();

    TerraformPlanStepParameters parameters =
        TerraformPlanStepParameters.infoBuilder()
            .provisionerIdentifier(ParameterField.createValueField("provId_"))
            .configuration(TerraformPlanExecutionDataParameters.builder()
                               .configFiles(configFilesWrapper)
                               .varFiles(varFilesMap)
                               .backendConfig(terraformBackendConfig)
                               .isTerraformCloudCli(ParameterField.createValueField(false))
                               .command(APPLY)
                               .secretManagerRef(ParameterField.createValueField("ref"))
                               .build())
            .build();
    Map<String, Map<String, String>> keyVersionMap = new HashMap();
    keyVersionMap.put("TF_CONFIG_FILES", Map.of("main.tf", "111", "file2", "112", "file3", "113"));
    keyVersionMap.put("TF_VAR_FILES_1", Map.of("terraform/var1", "222", "terraform/var2", "333"));
    keyVersionMap.put("TF_BACKEND_CONFIG_FILE", Map.of("terraform/backend.tf", "444"));
    TerraformTaskNGResponse response = TerraformTaskNGResponse.builder().keyVersionMap(keyVersionMap).build();
    helper.saveTerraformInheritOutput(parameters, response, ambiance, null);
    ArgumentCaptor<TerraformInheritOutput> captor = ArgumentCaptor.forClass(TerraformInheritOutput.class);
    verify(mockExecutionSweepingOutputService).consume(any(), anyString(), captor.capture(), anyString());
    TerraformInheritOutput output = captor.getValue();
    assertThat(output).isNotNull();
    S3StorageConfigDTO configFiles = (S3StorageConfigDTO) output.getFileStorageConfigDTO();
    assertThat(configFiles).isNotNull();
    assertThat(configFiles.getRegion()).isEqualTo("region");
    assertThat(configFiles.getBucket()).isEqualTo("bucket");
    assertThat(configFiles.getFolderPath()).isEqualTo("terraform");
    assertThat(configFiles.getVersions().get("main.tf")).isEqualTo("111");
    assertThat(configFiles.getVersions().get("file2")).isEqualTo("112");
    assertThat(configFiles.getVersions().get("file3")).isEqualTo("113");

    List<TerraformVarFileConfig> varFileConfigs = output.getVarFileConfigs();
    assertThat(varFileConfigs).isNotNull();
    assertThat(varFileConfigs.size()).isEqualTo(1);
    assertTrue(varFileConfigs.get(0) instanceof TerraformRemoteVarFileConfig);
    S3StorageConfigDTO s3VarConfig =
        (S3StorageConfigDTO) ((TerraformRemoteVarFileConfig) varFileConfigs.get(0)).getFileStoreConfigDTO();
    assertThat(s3VarConfig.getRegion()).isEqualTo("region");
    assertThat(s3VarConfig.getBucket()).isEqualTo("bucket");
    assertThat(s3VarConfig.getPaths().size()).isEqualTo(2);
    assertTrue(s3VarConfig.getPaths().contains("terraform/var1"));
    assertTrue(s3VarConfig.getPaths().contains("terraform/var2"));
    assertThat(s3VarConfig.getVersions().get("terraform/var1")).isEqualTo("222");
    assertThat(s3VarConfig.getVersions().get("terraform/var2")).isEqualTo("333");

    TerraformRemoteBackendConfigFileConfig terraformRemoteBackendConfigFileConfig =
        (TerraformRemoteBackendConfigFileConfig) output.getBackendConfigurationFileConfig();
    S3StorageConfigDTO configBEFiles = (S3StorageConfigDTO) terraformRemoteBackendConfigFileConfig.fileStoreConfigDTO;
    assertThat(configBEFiles.getRegion()).isEqualTo("region");
    assertThat(configBEFiles.getBucket()).isEqualTo("bucket");
    assertThat(configBEFiles.getFolderPath()).isEqualTo("terraformBe");
    assertThat(configBEFiles.getVersions().get("terraform/backend.tf")).isEqualTo("444");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testValidateSecretManager() {
    Ambiance ambiance = getAmbiance();
    IdentifierRef identifierRef = IdentifierRef.builder()
                                      .accountIdentifier("accountIdentifier")
                                      .orgIdentifier("orgIdentifier")
                                      .projectIdentifier("projectIdentifier")
                                      .identifier("identifier")
                                      .build();
    doReturn(true).when(cdFeatureFlagHelper).isEnabled(any(), any());
    doReturn(false).when(ngEncryptedDataService).isSecretManagerReadOnly(any(), any(), any(), any());

    helper.validateSecretManager(ambiance, identifierRef);
    verify(ngEncryptedDataService)
        .isSecretManagerReadOnly(identifierRef.getAccountIdentifier(), identifierRef.getOrgIdentifier(),
            identifierRef.getProjectIdentifier(), identifierRef.getIdentifier());
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testValidateSecretManagerExceptionThrown() {
    Ambiance ambiance = getAmbiance();
    IdentifierRef identifierRef = IdentifierRef.builder()
                                      .accountIdentifier("accountIdentifier")
                                      .orgIdentifier("orgIdentifier")
                                      .projectIdentifier("projectIdentifier")
                                      .identifier("identifier")
                                      .build();
    doReturn(true).when(cdFeatureFlagHelper).isEnabled(any(), any());
    doReturn(true).when(ngEncryptedDataService).isSecretManagerReadOnly(any(), any(), any(), any());

    assertThatThrownBy(() -> helper.validateSecretManager(ambiance, identifierRef))
        .hasMessage(
            "Please configure a secret manager which allows to store terraform plan as a secret. Read-only secret manager is not allowed.");
  }

  private TerraformPlanExecutionDetails createTfPlanExecutionDetails(String encryptionConfigName, String configId,
      String configProjId, String configOrgId, String configAccountId, String encryptedRecordDataName) {
    final Ambiance ambiance = getAmbiance();
    String planExecutionId = ambiance.getPlanExecutionId();
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String projectId = AmbianceUtils.getProjectIdentifier(ambiance);
    String orgId = AmbianceUtils.getOrgIdentifier(ambiance);
    String stageExecutionId = ambiance.getStageExecutionId();

    List<EncryptedRecordData> encryptedRecordData =
        List.of(EncryptedRecordData.builder().name(encryptedRecordDataName).build());

    return TerraformPlanExecutionDetails.builder()
        .accountIdentifier(accountId)
        .orgIdentifier(orgId)
        .projectIdentifier(projectId)
        .pipelineExecutionId(planExecutionId)
        .stageExecutionId(stageExecutionId)
        .provisionerId("provisionerId")
        .tfPlanJsonFieldId("testId")
        .tfPlanFileBucket(FileBucket.TERRAFORM_PLAN_JSON.name())
        .encryptedTfPlan(encryptedRecordData)
        .encryptionConfig(VaultConfig.builder()
                              .name(encryptionConfigName)
                              .basePath("testBasePath")
                              .renewAppRoleToken(false)
                              .encryptionType(EncryptionType.VAULT)
                              .ngMetadata(NGSecretManagerMetadata.builder()
                                              .identifier(configId)
                                              .projectIdentifier(configProjId)
                                              .accountIdentifier(configAccountId)
                                              .orgIdentifier(configOrgId)
                                              .build())
                              .build())
        .build();
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testEncryptTerraformJsonOutput() {
    final Ambiance ambiance = getAmbiance();

    String tfJsonOutput =
        "{   \"test-output-name1\": {     \"sensitive\": false,     \"type\": \"string\",     \"value\": "
        + "\"test-output-value1\"   },   \"test-output-name2\": {     \"sensitive\": false,     \"type\": \"string\",    "
        + " \"value\": \"test-output-value2\"   } }";

    ArgumentCaptor<SecretDTOV2> captor = ArgumentCaptor.forClass(SecretDTOV2.class);

    Map<String, Object> outputs =
        helper.encryptTerraformJsonOutput(tfJsonOutput, ambiance, "secretManagerRef-test", "provisionerId-test");
    verify(ngSecretService).create(anyString(), captor.capture());
    SecretDTOV2 secret = captor.getValue();

    assertThat(outputs).isNotEmpty();
    assertThat((String) outputs.get("TF_JSON_OUTPUT_ENCRYPTED"))
        .contains("<+secrets.getValue(\"terraform_output_provisionerId-test_");

    assertThat(secret).isNotNull();
    assertThat(secret.getType()).isEqualTo(SecretType.SecretText);
    SecretTextSpecDTO secretTextSpecDTO = (SecretTextSpecDTO) secret.getSpec();
    assertThat(secretTextSpecDTO.getValue()).isEqualTo(tfJsonOutput);
    assertThat(secretTextSpecDTO.getValueType()).isEqualTo(ValueType.Inline);
    assertThat(secretTextSpecDTO.getSecretManagerIdentifier()).isEqualTo("secretManagerRef-test");
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testTfPlanEncryptionOnManager() {
    doReturn(true).when(cdFeatureFlagHelper).isEnabled(any(), any());

    boolean flag = helper.tfPlanEncryptionOnManager(
        "accountIdentifier", GcpKmsConfig.builder().accountId(GLOBAL_ACCOUNT_ID).build());
    assertThat(flag).isTrue();
  }

  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testHasS3VarFiles() {
    List<TerraformVarFileInfo> terraformVarFileInfos = new ArrayList<>();

    TerraformVarFileInfo inlineTerraformVarFileInfo =
        InlineTerraformVarFileInfo.builder().varFileContent("inlineVarFile-1").build();

    TerraformVarFileInfo artifactoryTerraformFileInfo =
        RemoteTerraformVarFileInfo.builder()
            .filestoreFetchFilesConfig(TerraformStepDataGenerator.createStoreDelegateConfig())
            .build();

    GitStoreDelegateConfig gitStoreDelegateConfig =
        GitStoreDelegateConfig.builder()
            .gitConfigDTO(GitConfigDTO.builder().gitAuthType(GitAuthType.HTTP).url("repourl").build())
            .connectorName("connectorId")
            .paths(Arrays.asList("filepath1", "filepath2"))
            .branch("master")
            .sshKeySpecDTO(SSHKeySpecDTO.builder().build())
            .build();

    TerraformVarFileInfo gitTerraformFileInfo =
        RemoteTerraformVarFileInfo.builder()
            .gitFetchFilesConfig(GitFetchFilesConfig.builder().gitStoreDelegateConfig(gitStoreDelegateConfig).build())
            .build();

    terraformVarFileInfos.add(inlineTerraformVarFileInfo);
    terraformVarFileInfos.add(artifactoryTerraformFileInfo);
    terraformVarFileInfos.add(gitTerraformFileInfo);

    boolean hasS3VarFiles = helper.hasS3VarFiles(terraformVarFileInfos);

    TerraformVarFileInfo s3TerraformFileInfo =
        RemoteTerraformVarFileInfo.builder()
            .filestoreFetchFilesConfig(S3StoreTFDelegateConfig.builder()
                                           .bucketName("test-bucket")
                                           .region("test-region")
                                           .path("test-path")
                                           .connectorDTO(ConnectorInfoDTO.builder().build())
                                           .build())
            .build();

    assertThat(hasS3VarFiles).isFalse();

    terraformVarFileInfos.add(s3TerraformFileInfo);
    boolean hasS3VarFiles1 = helper.hasS3VarFiles(terraformVarFileInfos);
    assertThat(hasS3VarFiles1).isTrue();
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testHasGitVarFiles() {
    List<TerraformVarFileInfo> terraformVarFileInfos = new ArrayList<>();

    TerraformVarFileInfo inlineTerraformVarFileInfo =
        InlineTerraformVarFileInfo.builder().varFileContent("inlineVarFile-1").build();

    TerraformVarFileInfo artifactoryTerraformFileInfo =
        RemoteTerraformVarFileInfo.builder()
            .filestoreFetchFilesConfig(TerraformStepDataGenerator.createStoreDelegateConfig())
            .build();

    TerraformVarFileInfo s3TerraformFileInfo =
        RemoteTerraformVarFileInfo.builder()
            .filestoreFetchFilesConfig(S3StoreTFDelegateConfig.builder()
                                           .bucketName("test-bucket")
                                           .region("test-region")
                                           .path("test-path")
                                           .connectorDTO(ConnectorInfoDTO.builder().build())
                                           .build())
            .build();

    terraformVarFileInfos.add(inlineTerraformVarFileInfo);
    terraformVarFileInfos.add(artifactoryTerraformFileInfo);
    terraformVarFileInfos.add(s3TerraformFileInfo);

    boolean hasS3VarFiles = helper.hasGitVarFiles(terraformVarFileInfos);

    assertThat(hasS3VarFiles).isFalse();

    GitStoreDelegateConfig gitStoreDelegateConfig =
        GitStoreDelegateConfig.builder()
            .gitConfigDTO(GitConfigDTO.builder().gitAuthType(GitAuthType.HTTP).url("repourl").build())
            .connectorName("connectorId")
            .paths(Arrays.asList("filepath1", "filepath2"))
            .branch("master")
            .sshKeySpecDTO(SSHKeySpecDTO.builder().build())
            .build();

    TerraformVarFileInfo gitTerraformFileInfo =
        RemoteTerraformVarFileInfo.builder()
            .gitFetchFilesConfig(GitFetchFilesConfig.builder().gitStoreDelegateConfig(gitStoreDelegateConfig).build())
            .build();

    terraformVarFileInfos.add(gitTerraformFileInfo);
    boolean hasS3VarFiles1 = helper.hasGitVarFiles(terraformVarFileInfos);
    assertThat(hasS3VarFiles1).isTrue();
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testFetchRemoteVarFilesS3() {
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
        TerraformPassThroughData.builder().hasS3Files(true).hasGitFiles(false).build();

    List<TerraformVarFileInfo> varFileInfo = new ArrayList<>();
    varFileInfo.add(
        RemoteTerraformVarFileInfo.builder()
            .filestoreFetchFilesConfig(
                S3StoreTFDelegateConfig.builder()
                    .bucketName("test-bucket")
                    .region("test-region")
                    .path("test-path")
                    .connectorDTO(
                        ConnectorInfoDTO.builder()
                            .connectorConfig(
                                AwsConnectorDTO.builder().credential(AwsCredentialDTO.builder().build()).build())
                            .build())
                    .build())
            .build());

    ParameterField<List<TaskSelectorYaml>> delegateSelectors = ParameterField.<List<TaskSelectorYaml>>builder().build();

    doReturn(EnvironmentType.NON_PROD).when(stepHelper).getEnvironmentType(any());

    Mockito.mockStatic(TaskRequestsUtils.class);
    PowerMockito.when(TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());
    ArgumentCaptor<TaskData> taskDataArgumentCaptor = ArgumentCaptor.forClass(TaskData.class);

    helper.fetchRemoteVarFiles(terraformPassThroughData, varFileInfo, getAmbiance(), stepElementParameters,
        TerraformCommandUnit.Plan.name(), delegateSelectors);

    PowerMockito.verifyStatic(TaskRequestsUtils.class, times(1));
    TaskRequestsUtils.prepareCDTaskRequest(any(), taskDataArgumentCaptor.capture(), any(), any(), any(), any(), any());
    assertThat(taskDataArgumentCaptor.getValue()).isNotNull();
    assertThat(taskDataArgumentCaptor.getValue().getParameters()).isNotNull();
    assertThat(taskDataArgumentCaptor.getValue().getTaskType()).isEqualTo("FETCH_S3_FILE_TASK_NG");

    AwsS3FetchFilesTaskParams params = (AwsS3FetchFilesTaskParams) taskDataArgumentCaptor.getValue().getParameters()[0];
    AwsS3FetchFileDelegateConfig config = params.getFetchFileDelegateConfigs().get(0);
    assertThat(config.getRegion()).isEqualTo("test-region");
    S3FileDetailRequest s3FileDetailRequest = config.getFileDetails().get(0);
    assertThat(s3FileDetailRequest.getFileKey()).isEqualTo("test-path");
    assertThat(s3FileDetailRequest.getBucketName()).isEqualTo("test-bucket");
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testFetchRemoteVarFilesGit() {
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

    StoreConfig storeVarFiles =
        GithubStore.builder()
            .branch(ParameterField.createValueField(gitStoreVarFiles.getBranch()))
            .gitFetchType(gitStoreVarFiles.getFetchType())
            .folderPath(ParameterField.createValueField(gitStoreVarFiles.getFolderPath().getValue()))
            .connectorRef(ParameterField.createValueField(gitStoreVarFiles.getConnectoref().getValue()))
            .build();

    RemoteTerraformVarFileSpec remoteTerraformVarFileSpec = new RemoteTerraformVarFileSpec();
    remoteTerraformVarFileSpec.setStore(
        StoreConfigWrapper.builder().spec(storeVarFiles).type(StoreConfigType.GITHUB).build());

    TerraformPlanStepParameters planStepParameters = TerraformStepDataGenerator.generateStepPlanWithVarFiles(
        StoreConfigType.GITHUB, StoreConfigType.GITHUB, gitStoreConfigFiles, gitStoreVarFiles, false);
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(planStepParameters).build();

    TerraformPassThroughData terraformPassThroughData =
        TerraformPassThroughData.builder().hasS3Files(false).hasGitFiles(true).build();

    List<TerraformVarFileInfo> varFileInfo = new ArrayList<>();

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
    varFileInfo.add(RemoteTerraformVarFileInfo.builder().gitFetchFilesConfig(gitFetchFilesConfig).build());

    ParameterField<List<TaskSelectorYaml>> delegateSelectors = ParameterField.<List<TaskSelectorYaml>>builder().build();

    doReturn(EnvironmentType.NON_PROD).when(stepHelper).getEnvironmentType(any());

    Mockito.mockStatic(TaskRequestsUtils.class);
    PowerMockito.when(TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());
    ArgumentCaptor<TaskData> taskDataArgumentCaptor = ArgumentCaptor.forClass(TaskData.class);

    helper.fetchRemoteVarFiles(terraformPassThroughData, varFileInfo, getAmbiance(), stepElementParameters,
        TerraformCommandUnit.Plan.name(), delegateSelectors);

    PowerMockito.verifyStatic(TaskRequestsUtils.class, times(1));
    TaskRequestsUtils.prepareCDTaskRequest(any(), taskDataArgumentCaptor.capture(), any(), any(), any(), any(), any());
    assertThat(taskDataArgumentCaptor.getValue()).isNotNull();
    assertThat(taskDataArgumentCaptor.getValue().getParameters()).isNotNull();
    assertThat(taskDataArgumentCaptor.getValue().getTaskType()).isEqualTo("GIT_FETCH_NEXT_GEN_TASK");

    GitFetchRequest gitFetchRequest = (GitFetchRequest) taskDataArgumentCaptor.getValue().getParameters()[0];
    GitFetchFilesConfig gitConfig = gitFetchRequest.getGitFetchFilesConfigs().get(0);
    assertThat(gitConfig.getIdentifier()).isEqualTo("terraform");
    GitStoreDelegateConfig gitStoreDgConfig = gitConfig.getGitStoreDelegateConfig();
    assertThat(gitStoreDgConfig.getBranch()).isEqualTo("master");
    assertThat(gitStoreDgConfig.getConnectorName()).isEqualTo("terraform");
    assertThat(gitStoreDgConfig.getGitConfigDTO().getUrl()).isEqualTo("https://github.com/wings-software");
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testExecuteNextLinkWithGit() {
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

    ParameterField<List<TaskSelectorYaml>> delegateSelectors = ParameterField.<List<TaskSelectorYaml>>builder().build();

    FetchFilesResult fetchFilesResult =
        FetchFilesResult.builder()
            .files(List.of(GitFile.builder().fileContent("test-file-content").filePath("test-file-path").build()))
            .build();
    Map<String, FetchFilesResult> gitFileResultMap = new HashMap<>();
    gitFileResultMap.put("var-file-02", fetchFilesResult);

    GitFetchResponse gitFetchResponse =
        GitFetchResponse.builder()
            .fetchedCommitIdsMap(new HashMap<>() {
              { put("var-file-02", "test-comit-id-123"); }
            })
            .filesFromMultipleRepo(gitFileResultMap)
            .unitProgressData(UnitProgressData.builder()
                                  .unitProgresses(List.of(UnitProgress.newBuilder().setUnitName("Fetch files").build()))
                                  .build())
            .build();
    TerraformTaskNGParametersBuilder terraformTaskNGParametersBuilder = TerraformTaskNGParameters.builder();
    terraformTaskNGParametersBuilder.accountId("test-account-id-1");
    terraformTaskNGParametersBuilder.entityId("test-entity-id-1");
    terraformTaskNGParametersBuilder.taskType(TFTaskType.PLAN);

    TerraformPassThroughData terraformPassThroughData =
        TerraformPassThroughData.builder()
            .hasGitFiles(true)
            .hasS3Files(false)
            .terraformTaskNGParametersBuilder(terraformTaskNGParametersBuilder)
            .originalStepVarFiles(planStepParameters.getConfiguration().getVarFiles())
            .build();

    StepElementParameters stepElementParameters = StepElementParameters.builder()
                                                      .spec(planStepParameters)
                                                      .timeout(ParameterField.createValueField("10m"))
                                                      .build();

    Mockito.doReturn("resolved-var-file-content-1").when(cdExpressionResolver).updateExpressions(any(), any());

    Mockito.mockStatic(TaskRequestsUtils.class);
    PowerMockito.when(TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());
    ArgumentCaptor<TaskData> taskDataArgumentCaptor = ArgumentCaptor.forClass(TaskData.class);

    helper.executeNextLink(getAmbiance(),
        ()
            -> gitFetchResponse,
        terraformPassThroughData, delegateSelectors, stepElementParameters, TerraformCommandUnit.Plan.name());

    PowerMockito.verifyStatic(TaskRequestsUtils.class, times(1));
    TaskRequestsUtils.prepareCDTaskRequest(any(), taskDataArgumentCaptor.capture(), any(), any(), any(), any(), any());
    assertThat(taskDataArgumentCaptor.getValue()).isNotNull();
    assertThat(taskDataArgumentCaptor.getValue().getParameters()).isNotNull();
    assertThat(taskDataArgumentCaptor.getValue().getTaskType()).isEqualTo("TERRAFORM_TASK_NG");

    TerraformTaskNGParameters terraformTaskNGParameters =
        (TerraformTaskNGParameters) taskDataArgumentCaptor.getValue().getParameters()[0];
    assertThat(terraformTaskNGParameters.getTaskType()).isEqualTo(TFTaskType.PLAN);
    assertThat(terraformTaskNGParameters.getVarFileInfos().size()).isEqualTo(2);
    assertThat(((InlineTerraformVarFileInfo) terraformTaskNGParameters.getVarFileInfos().get(0)).getVarFileContent())
        .isEqualTo("var-content");
    assertThat(((InlineTerraformVarFileInfo) terraformTaskNGParameters.getVarFileInfos().get(1)).getVarFileContent())
        .isEqualTo("resolved-var-file-content-1");
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testExecuteNextLinkWithGitAndS3() {
    ParameterField<List<TaskSelectorYaml>> delegateSelectors = ParameterField.<List<TaskSelectorYaml>>builder().build();

    FetchFilesResult fetchFilesResult =
        FetchFilesResult.builder()
            .files(List.of(GitFile.builder().fileContent("test-file-content").filePath("test-file-path").build()))
            .build();
    Map<String, FetchFilesResult> gitFileResultMap = new HashMap<>();
    gitFileResultMap.put("test-git-id", fetchFilesResult);

    GitFetchResponse gitFetchResponse =
        GitFetchResponse.builder()
            .fetchedCommitIdsMap(new HashMap<>() {
              { put("test-git-id", "test-comit-id-123"); }
            })
            .filesFromMultipleRepo(gitFileResultMap)
            .unitProgressData(UnitProgressData.builder()
                                  .unitProgresses(List.of(UnitProgress.newBuilder().setUnitName("Fetch files").build()))
                                  .build())
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
    varFileInfo.add(InlineTerraformVarFileInfo.builder().varFileContent("var-file-inline-1").build());

    TerraformTaskNGParametersBuilder terraformTaskNGParametersBuilder = TerraformTaskNGParameters.builder();
    terraformTaskNGParametersBuilder.varFileInfos(varFileInfo);
    terraformTaskNGParametersBuilder.accountId("test-account-id-1");
    terraformTaskNGParametersBuilder.entityId("test-entity-id-1");
    terraformTaskNGParametersBuilder.taskType(TFTaskType.PLAN);

    TerraformPassThroughData terraformPassThroughData =
        TerraformPassThroughData.builder()
            .hasGitFiles(true)
            .hasS3Files(true)
            .terraformTaskNGParametersBuilder(terraformTaskNGParametersBuilder)
            .build();

    StepElementParameters stepElementParameters =
        StepElementParameters.builder().timeout(ParameterField.createValueField("10m")).build();

    Mockito.doReturn("resolved-var-file-content-1").when(cdExpressionResolver).updateExpressions(any(), any());

    Mockito.mockStatic(TaskRequestsUtils.class);
    PowerMockito.when(TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());
    ArgumentCaptor<TaskData> taskDataArgumentCaptor = ArgumentCaptor.forClass(TaskData.class);

    helper.executeNextLink(getAmbiance(),
        ()
            -> gitFetchResponse,
        terraformPassThroughData, delegateSelectors, stepElementParameters, TerraformCommandUnit.Plan.name());

    PowerMockito.verifyStatic(TaskRequestsUtils.class, times(1));
    TaskRequestsUtils.prepareCDTaskRequest(any(), taskDataArgumentCaptor.capture(), any(), any(), any(), any(), any());
    assertThat(taskDataArgumentCaptor.getValue()).isNotNull();
    assertThat(taskDataArgumentCaptor.getValue().getParameters()).isNotNull();
    assertThat(taskDataArgumentCaptor.getValue().getTaskType()).isEqualTo("FETCH_S3_FILE_TASK_NG");

    AwsS3FetchFilesTaskParams awsS3FetchFilesTaskParams =
        (AwsS3FetchFilesTaskParams) taskDataArgumentCaptor.getValue().getParameters()[0];
    assertThat(awsS3FetchFilesTaskParams.getFetchFileDelegateConfigs().get(0).getRegion()).isEqualTo("test-region");

    S3FileDetailRequest s3FileDetailRequest =
        awsS3FetchFilesTaskParams.getFetchFileDelegateConfigs().get(0).getFileDetails().get(0);
    assertThat(s3FileDetailRequest.getFileKey()).isEqualTo("test-path");
    assertThat(s3FileDetailRequest.getBucketName()).isEqualTo("test-bucket");
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testExecuteNextLinkWithS3() {
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

    TerraformPlanStepParameters planStepParameters = TerraformStepDataGenerator.generateStepPlanWithVarFiles(
        StoreConfigType.GITHUB, StoreConfigType.S3, gitStoreConfigFiles, s3StoreConfigFiles, true);

    ParameterField<List<TaskSelectorYaml>> delegateSelectors = ParameterField.<List<TaskSelectorYaml>>builder().build();

    S3FileDetailResponse s3FileDetailResponse =
        S3FileDetailResponse.builder().fileContent("tf-var-file-from-s3-test-1").build();

    List<S3FileDetailResponse> s3FileDetailResponses = new ArrayList<>();
    s3FileDetailResponses.add(s3FileDetailResponse);

    Map<String, List<S3FileDetailResponse>> s3FetchFileDetailsResponse = new HashMap<>();
    s3FetchFileDetailsResponse.put("var-file-1", s3FileDetailResponses);

    AwsS3FetchFilesResponse awsS3FetchFilesResponse =
        AwsS3FetchFilesResponse.builder()
            .unitProgressData(UnitProgressData.builder()
                                  .unitProgresses(List.of(UnitProgress.newBuilder().setUnitName("Fetch files").build()))
                                  .build())
            .s3filesDetails(s3FetchFileDetailsResponse)
            .keyVersionMap(new HashMap<>() {
              {
                put("id1", new HashMap<>() {
                  { put("id1", "key-version-id-1"); }
                });
              }
            })
            .build();

    TerraformTaskNGParametersBuilder terraformTaskNGParametersBuilder = TerraformTaskNGParameters.builder();
    terraformTaskNGParametersBuilder.accountId("test-account-id-1");
    terraformTaskNGParametersBuilder.entityId("test-entity-id-1");
    terraformTaskNGParametersBuilder.taskType(TFTaskType.PLAN);

    TerraformPassThroughData terraformPassThroughData =
        TerraformPassThroughData.builder()
            .hasGitFiles(true)
            .hasS3Files(false)
            .terraformTaskNGParametersBuilder(terraformTaskNGParametersBuilder)
            .originalStepVarFiles(planStepParameters.getConfiguration().getVarFiles())

            .build();

    StepElementParameters stepElementParameters = StepElementParameters.builder()
                                                      .spec(planStepParameters)
                                                      .timeout(ParameterField.createValueField("10m"))
                                                      .build();

    Mockito.doReturn("resolved-var-file-content-1").when(cdExpressionResolver).updateExpressions(any(), any());

    Mockito.mockStatic(TaskRequestsUtils.class);
    PowerMockito.when(TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());
    ArgumentCaptor<TaskData> taskDataArgumentCaptor = ArgumentCaptor.forClass(TaskData.class);

    helper.executeNextLink(getAmbiance(),
        ()
            -> awsS3FetchFilesResponse,
        terraformPassThroughData, delegateSelectors, stepElementParameters, TerraformCommandUnit.Plan.name());

    PowerMockito.verifyStatic(TaskRequestsUtils.class, times(1));
    TaskRequestsUtils.prepareCDTaskRequest(any(), taskDataArgumentCaptor.capture(), any(), any(), any(), any(), any());
    assertThat(taskDataArgumentCaptor.getValue()).isNotNull();
    assertThat(taskDataArgumentCaptor.getValue().getParameters()).isNotNull();
    assertThat(taskDataArgumentCaptor.getValue().getTaskType()).isEqualTo("TERRAFORM_TASK_NG");

    TerraformTaskNGParameters terraformTaskNGParameters =
        (TerraformTaskNGParameters) taskDataArgumentCaptor.getValue().getParameters()[0];
    assertThat(terraformTaskNGParameters.getTaskType()).isEqualTo(TFTaskType.PLAN);
    assertThat(terraformTaskNGParameters.getVarFileInfos().size()).isEqualTo(2);
    assertThat(((InlineTerraformVarFileInfo) terraformTaskNGParameters.getVarFileInfos().get(0)).getVarFileContent())
        .isEqualTo("resolved-var-file-content-1");
    assertThat(((InlineTerraformVarFileInfo) terraformTaskNGParameters.getVarFileInfos().get(1)).getVarFileContent())
        .isEqualTo("var-content");
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testExecuteNextLinkWithS3VarFilesFromInheritedConfig() {
    ParameterField<List<TaskSelectorYaml>> delegateSelectors = ParameterField.<List<TaskSelectorYaml>>builder().build();

    S3FileDetailResponse s3FileDetailResponse =
        S3FileDetailResponse.builder().fileContent("tf-var-file-from-s3-test-1").build();

    List<S3FileDetailResponse> s3FileDetailResponses = new ArrayList<>();
    s3FileDetailResponses.add(s3FileDetailResponse);

    Map<String, List<S3FileDetailResponse>> s3FetchFileDetailsResponse = new HashMap<>();
    s3FetchFileDetailsResponse.put("test-content-remote-2", s3FileDetailResponses);

    AwsS3FetchFilesResponse awsS3FetchFilesResponse =
        AwsS3FetchFilesResponse.builder()
            .unitProgressData(UnitProgressData.builder()
                                  .unitProgresses(List.of(UnitProgress.newBuilder().setUnitName("Fetch files").build()))
                                  .build())
            .s3filesDetails(s3FetchFileDetailsResponse)
            .keyVersionMap(new HashMap<>() {
              {
                put("id1", new HashMap<>() {
                  { put("id1", "key-version-id-1"); }
                });
              }
            })
            .build();

    TerraformTaskNGParametersBuilder terraformTaskNGParametersBuilder = TerraformTaskNGParameters.builder();
    terraformTaskNGParametersBuilder.accountId("test-account-id-1");
    terraformTaskNGParametersBuilder.entityId("test-entity-id-1");
    terraformTaskNGParametersBuilder.taskType(TFTaskType.PLAN);

    List<TerraformVarFileConfig> originalVarFileConfigs = new ArrayList<>();

    TerraformInlineVarFileConfig terraformInlineVarFileConfigInline = TerraformInlineVarFileConfig.builder()
                                                                          .varFileContent("test-content-inline-1")
                                                                          .identifier("test-var-file-identifier-1")
                                                                          .build();

    TerraformRemoteVarFileConfig terraformRemoteVarFileConfigGit =
        TerraformRemoteVarFileConfig.builder()
            .identifier("test-content-remote-2")
            .fileStoreConfigDTO(S3StorageConfigDTO.builder().build())
            .build();

    originalVarFileConfigs.add(terraformInlineVarFileConfigInline);
    originalVarFileConfigs.add(terraformRemoteVarFileConfigGit);

    TerraformPassThroughData terraformPassThroughData =
        TerraformPassThroughData.builder()
            .hasGitFiles(true)
            .hasS3Files(false)
            .terraformTaskNGParametersBuilder(terraformTaskNGParametersBuilder)
            .originalVarFileConfigs(originalVarFileConfigs)
            .build();

    StepElementParameters stepElementParameters =
        StepElementParameters.builder().timeout(ParameterField.createValueField("10m")).build();

    Mockito.doReturn("resolved-var-file-content-2").when(cdExpressionResolver).updateExpressions(any(), any());

    Mockito.mockStatic(TaskRequestsUtils.class);
    PowerMockito.when(TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());
    ArgumentCaptor<TaskData> taskDataArgumentCaptor = ArgumentCaptor.forClass(TaskData.class);

    helper.executeNextLink(getAmbiance(),
        ()
            -> awsS3FetchFilesResponse,
        terraformPassThroughData, delegateSelectors, stepElementParameters, TerraformCommandUnit.Plan.name());

    PowerMockito.verifyStatic(TaskRequestsUtils.class, times(1));
    TaskRequestsUtils.prepareCDTaskRequest(any(), taskDataArgumentCaptor.capture(), any(), any(), any(), any(), any());
    assertThat(taskDataArgumentCaptor.getValue()).isNotNull();
    assertThat(taskDataArgumentCaptor.getValue().getParameters()).isNotNull();
    assertThat(taskDataArgumentCaptor.getValue().getTaskType()).isEqualTo("TERRAFORM_TASK_NG");

    TerraformTaskNGParameters terraformTaskNGParameters =
        (TerraformTaskNGParameters) taskDataArgumentCaptor.getValue().getParameters()[0];
    assertThat(terraformTaskNGParameters.getTaskType()).isEqualTo(TFTaskType.PLAN);
    assertThat(terraformTaskNGParameters.getVarFileInfos().size()).isEqualTo(2);
    assertThat(((InlineTerraformVarFileInfo) terraformTaskNGParameters.getVarFileInfos().get(0)).getVarFileContent())
        .isEqualTo("test-content-inline-1");
    assertThat(((InlineTerraformVarFileInfo) terraformTaskNGParameters.getVarFileInfos().get(1)).getVarFileContent())
        .isEqualTo("resolved-var-file-content-2");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetRevisionsMapFromTerraformVarFile() {
    Map<String, String> commitIdForConfigFilesMap = new HashMap<>();
    commitIdForConfigFilesMap.put(TF_CONFIG_FILES, "commit_1");
    commitIdForConfigFilesMap.put(TF_BACKEND_CONFIG_FILE, "commit_2");
    commitIdForConfigFilesMap.put("TF_VAR_FILES_1", "commit_v1");
    commitIdForConfigFilesMap.put("TF_VAR_FILES_2", "commit_v2");

    LinkedHashMap<String, TerraformVarFile> varFiles = new LinkedHashMap<>();
    TerraformVarFile terraformVarFile0 = new TerraformVarFile();
    RemoteTerraformVarFileSpec remoteTerraformVarFileSpec0 = new RemoteTerraformVarFileSpec();
    remoteTerraformVarFileSpec0.setStore(StoreConfigWrapper.builder().spec(GithubStore.builder().build()).build());
    terraformVarFile0.setSpec(remoteTerraformVarFileSpec0);

    TerraformVarFile terraformVarFile1 = new TerraformVarFile();
    terraformVarFile1.setSpec(new InlineTerraformVarFileSpec());

    TerraformVarFile terraformVarFile2 = new TerraformVarFile();
    RemoteTerraformVarFileSpec remoteTerraformVarFileSpec2 = new RemoteTerraformVarFileSpec();
    remoteTerraformVarFileSpec2.setStore(StoreConfigWrapper.builder().spec(GithubStore.builder().build()).build());
    terraformVarFile2.setSpec(remoteTerraformVarFileSpec2);

    varFiles.put("identifier0", terraformVarFile0);
    varFiles.put("identifier1", terraformVarFile1);
    varFiles.put("identifier2", terraformVarFile2);

    Map<String, String> outputs = helper.getRevisionsMap(varFiles, commitIdForConfigFilesMap);

    assertThat(outputs.get("identifier0")).isEqualTo("commit_v1");
    assertThat(outputs.get("identifier2")).isEqualTo("commit_v2");
    assertThat(outputs.get(TF_CONFIG_FILES)).isEqualTo("commit_1");
    assertThat(outputs.get(TF_BACKEND_CONFIG_FILE)).isEqualTo("commit_2");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetRevisionsMapFromTerraformVarFileConfig() {
    Map<String, String> commitIdForConfigFilesMap = new HashMap<>();
    commitIdForConfigFilesMap.put(TF_CONFIG_FILES, "commit_1");
    commitIdForConfigFilesMap.put(TF_BACKEND_CONFIG_FILE, "commit_2");
    commitIdForConfigFilesMap.put("TF_VAR_FILES_1", "commit_v1");
    commitIdForConfigFilesMap.put("TF_VAR_FILES_2", "commit_v2");

    List<TerraformVarFileConfig> varFileConfigs = new ArrayList<>();
    TerraformVarFileConfig terraformVarFile0 = TerraformRemoteVarFileConfig.builder()
                                                   .identifier("identifier0")
                                                   .gitStoreConfigDTO(GithubStoreDTO.builder().build())
                                                   .build();
    TerraformVarFileConfig terraformVarFile1 = TerraformInlineVarFileConfig.builder().identifier("identifier1").build();
    TerraformVarFileConfig terraformVarFile2 = TerraformRemoteVarFileConfig.builder()
                                                   .identifier("identifier2")
                                                   .gitStoreConfigDTO(GithubStoreDTO.builder().build())
                                                   .build();
    varFileConfigs.add(terraformVarFile0);
    varFileConfigs.add(terraformVarFile1);
    varFileConfigs.add(terraformVarFile2);

    Map<String, String> outputs = helper.getRevisionsMap(varFileConfigs, commitIdForConfigFilesMap);

    assertThat(outputs.get("identifier0")).isEqualTo("commit_v1");
    assertThat(outputs.get("identifier2")).isEqualTo("commit_v2");
    assertThat(outputs.get(TF_CONFIG_FILES)).isEqualTo("commit_1");
    assertThat(outputs.get(TF_BACKEND_CONFIG_FILE)).isEqualTo("commit_2");
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testGetRevisionsMapWithNullVarFilesWhenRollback() {
    Map<String, String> commitIdForConfigFilesMap = new HashMap<>();
    commitIdForConfigFilesMap.put(TF_CONFIG_FILES, "commit_1");
    commitIdForConfigFilesMap.put(TF_BACKEND_CONFIG_FILE, "commit_2");
    commitIdForConfigFilesMap.put("TF_VAR_FILES_1", "commit_v1");
    commitIdForConfigFilesMap.put("TF_VAR_FILES_2", "commit_v2");

    List<TerraformVarFileConfig> varFiles = null;

    Map<String, String> outputs = helper.getRevisionsMap(varFiles, commitIdForConfigFilesMap);

    assertThat(outputs.get(TF_CONFIG_FILES)).isEqualTo("commit_1");
    assertThat(outputs.get(TF_BACKEND_CONFIG_FILE)).isEqualTo("commit_2");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetRevisions() {
    Map<String, String> commitIdForConfigFilesMap = new HashMap<>();
    commitIdForConfigFilesMap.put(TF_CONFIG_FILES, "commitId_1");
    commitIdForConfigFilesMap.put(TF_BACKEND_CONFIG_FILE, "commitId_2");
    TerraformTaskNGResponse taskNGResponse =
        TerraformTaskNGResponse.builder().commitIdForConfigFilesMap(commitIdForConfigFilesMap).build();

    Map<String, String> fetchedCommitIdsMap = new HashMap<>();
    fetchedCommitIdsMap.put("varFileId", "commitId_v1");
    TerraformPassThroughData terraformPassThroughData =
        TerraformPassThroughData.builder().fetchedCommitIdsMap(fetchedCommitIdsMap).build();

    Map<String, String> getRevisionsMap = helper.getRevisionsMap(terraformPassThroughData, taskNGResponse);

    assertThat(getRevisionsMap.size()).isEqualTo(3);
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testOrderOfVarFilesWithIdentifiersFromSteps() {
    TerraformPassThroughData terraformPassThroughData = TerraformPassThroughData.builder().build();

    LinkedHashMap<String, TerraformVarFile> originalStepVarFiles = new LinkedHashMap<>();

    InlineTerraformVarFileSpec inlineTerraformVarFileSpec1 = new InlineTerraformVarFileSpec();
    inlineTerraformVarFileSpec1.setContent(ParameterField.createValueField("inline-var-content-1"));
    InlineTerraformVarFileSpec inlineTerraformVarFileSpec5 = new InlineTerraformVarFileSpec();
    inlineTerraformVarFileSpec5.setContent(ParameterField.createValueField("inline-var-content-5"));

    RemoteTerraformVarFileSpec git1RemoteTerraformVarFileSpec = new RemoteTerraformVarFileSpec();
    TerraformStepDataGenerator.GitStoreConfig gitStoreVarFiles =
        TerraformStepDataGenerator.GitStoreConfig.builder()
            .branch("master")
            .fetchType(FetchType.BRANCH)
            .folderPath(ParameterField.createValueField("VarFiles/"))
            .connectoref(ParameterField.createValueField("terraform"))
            .build();

    StoreConfig storeGit1VarFiles;
    storeGit1VarFiles = GithubStore.builder()
                            .branch(ParameterField.createValueField(gitStoreVarFiles.getBranch()))
                            .gitFetchType(gitStoreVarFiles.getFetchType())
                            .folderPath(ParameterField.createValueField(gitStoreVarFiles.getFolderPath().getValue()))
                            .connectorRef(ParameterField.createValueField(gitStoreVarFiles.getConnectoref().getValue()))
                            .build();
    git1RemoteTerraformVarFileSpec.setStore(
        StoreConfigWrapper.builder().spec(storeGit1VarFiles).type(StoreConfigType.GITHUB).build());

    RemoteTerraformVarFileSpec artifactoryRemoteTerraformVarFileSpec = new RemoteTerraformVarFileSpec();
    TerraformStepDataGenerator.ArtifactoryStoreConfig artifactoryStoreVarFiles =
        TerraformStepDataGenerator.ArtifactoryStoreConfig.builder()
            .connectorRef("connectorRef2")
            .repositoryName("repositoryPathtoVars")
            .build();

    StoreConfig storeArtifactory1VarFiles;
    storeArtifactory1VarFiles =
        ArtifactoryStoreConfig.builder()
            .repositoryName(ParameterField.createValueField(artifactoryStoreVarFiles.getRepositoryName()))
            .connectorRef(ParameterField.createValueField(artifactoryStoreVarFiles.getConnectorRef()))
            .artifactPaths(ParameterField.createValueField(artifactoryStoreVarFiles.getArtifacts()))
            .build();

    artifactoryRemoteTerraformVarFileSpec.setStore(
        StoreConfigWrapper.builder().spec(storeArtifactory1VarFiles).type(StoreConfigType.ARTIFACTORY).build());

    S3StoreConfig s3StoreConfigFiles = S3StoreConfig.builder()
                                           .connectorRef(ParameterField.createValueField("connectorRef"))
                                           .region(ParameterField.createValueField("region"))
                                           .bucketName(ParameterField.createValueField("bucket"))
                                           .folderPath(ParameterField.createValueField("terraform"))
                                           .build();

    RemoteTerraformVarFileSpec remoteS3TerraformVarFileSpec = new RemoteTerraformVarFileSpec();
    StoreConfigWrapper storeConfigWrapper =
        StoreConfigWrapper.builder().spec(s3StoreConfigFiles).type(StoreConfigType.S3).build();
    remoteS3TerraformVarFileSpec.setStore(storeConfigWrapper);

    originalStepVarFiles.put("inline-var-file-01",
        TerraformVarFile.builder()
            .identifier("inline-var-file-01")
            .type("Inline")
            .spec(inlineTerraformVarFileSpec1)
            .build());
    originalStepVarFiles.put("git-var-file-02",
        TerraformVarFile.builder()
            .identifier("git-var-file-02")
            .type("Remote")
            .spec(git1RemoteTerraformVarFileSpec)
            .build());
    originalStepVarFiles.put("artifactory-var-file-03",
        TerraformVarFile.builder()
            .identifier("artifactory-var-file-03")
            .type("Remote")
            .spec(artifactoryRemoteTerraformVarFileSpec)
            .build());

    originalStepVarFiles.put("s3-var-file-04",
        TerraformVarFile.builder()
            .identifier("s3-var-file-04")
            .type("Remote")
            .spec(remoteS3TerraformVarFileSpec)
            .build());
    originalStepVarFiles.put("inline-var-file-05",
        TerraformVarFile.builder()
            .identifier("inline-var-file-05")
            .type("Inline")
            .spec(inlineTerraformVarFileSpec5)
            .build());

    S3FileDetailResponse s3FileDetailResponse =
        S3FileDetailResponse.builder().fileContent("s3-test-file-content").build();
    List<S3FileDetailResponse> s3FileDetailResponses = new ArrayList<>();
    s3FileDetailResponses.add(s3FileDetailResponse);
    Map<String, List<S3FileDetailResponse>> s3FetchFileDetails = new HashMap<>();
    s3FetchFileDetails.put("s3-var-file-04", s3FileDetailResponses);

    FetchFilesResult fetchFilesResult =
        FetchFilesResult.builder()
            .files(List.of(GitFile.builder().fileContent("git-test-file-content").filePath("test-file-path").build()))
            .build();
    Map<String, FetchFilesResult> gitFileResultMap = new HashMap<>();
    gitFileResultMap.put("git-var-file-02", fetchFilesResult);

    terraformPassThroughData.setOriginalStepVarFiles(originalStepVarFiles);
    terraformPassThroughData.setS3VarFilesDetails(s3FetchFileDetails);
    terraformPassThroughData.setGitVarFilesFromMultipleRepo(gitFileResultMap);

    ConnectorInfoDTO connectorInfoDTO = getConnectorInfoDTO();
    ArtifactoryStoreDelegateConfig.builder()
        .repositoryName("repositoryPath")
        .connectorDTO(connectorInfoDTO)
        .succeedIfFileNotFound(false)
        .build();
    TerraformVarFileInfo artifactoryTerraformFileInfo =
        RemoteTerraformVarFileInfo.builder()
            .filestoreFetchFilesConfig(ArtifactoryStoreDelegateConfig.builder()
                                           .repositoryName("repositoryPath")
                                           .connectorDTO(connectorInfoDTO)
                                           .succeedIfFileNotFound(false)
                                           .identifier("artifactory-var-file-03")
                                           .build())
            .build();

    List<TerraformVarFileInfo> varFileInfosInBuilder = new ArrayList<>();
    varFileInfosInBuilder.add(artifactoryTerraformFileInfo);

    TerraformTaskNGParametersBuilder terraformTaskNGParametersBuilder =
        TerraformTaskNGParameters.builder().varFileInfos(varFileInfosInBuilder);
    terraformTaskNGParametersBuilder.accountId("test-account-id-1");
    terraformTaskNGParametersBuilder.entityId("test-entity-id-1");
    terraformTaskNGParametersBuilder.taskType(TFTaskType.PLAN).build();

    Mockito.doReturn("git-test-file-content")
        .when(cdExpressionResolver)
        .updateExpressions(eq(getAmbiance()), eq("git-test-file-content"));
    Mockito.doReturn("s3-test-file-content")
        .when(cdExpressionResolver)
        .updateExpressions(eq(getAmbiance()), eq("s3-test-file-content"));

    List<TerraformVarFileInfo> terraformVarFileInfos = helper.getVarFilesWithStepIdentifiersOrder(
        getAmbiance(), terraformPassThroughData, terraformTaskNGParametersBuilder);

    assertThat(terraformVarFileInfos.size()).isEqualTo(5);
    assertThat(((InlineTerraformVarFileInfo) terraformVarFileInfos.get(0)).getVarFileContent())
        .isEqualTo("inline-var-content-1");
    assertThat(((InlineTerraformVarFileInfo) terraformVarFileInfos.get(1)).getVarFileContent())
        .isEqualTo("git-test-file-content");
    FileStoreFetchFilesConfig fileStoreFetchFilesConfig =
        ((RemoteTerraformVarFileInfo) terraformVarFileInfos.get(2)).getFilestoreFetchFilesConfig();
    assertThat(fileStoreFetchFilesConfig.getIdentifier()).isEqualTo("artifactory-var-file-03");
    assertThat(((InlineTerraformVarFileInfo) terraformVarFileInfos.get(3)).getVarFileContent())
        .isEqualTo("s3-test-file-content");
    assertThat(((InlineTerraformVarFileInfo) terraformVarFileInfos.get(4)).getVarFileContent())
        .isEqualTo("inline-var-content-5");
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testOrderOfVarFilesWithIdentifiersFromInheritedConfigs() {
    TerraformPassThroughData terraformPassThroughData = TerraformPassThroughData.builder().build();

    S3FileDetailResponse s3FileDetailResponse =
        S3FileDetailResponse.builder().fileContent("s3-test-file-content").build();
    List<S3FileDetailResponse> s3FileDetailResponses = new ArrayList<>();
    s3FileDetailResponses.add(s3FileDetailResponse);
    Map<String, List<S3FileDetailResponse>> s3FetchFileDetails = new HashMap<>();
    s3FetchFileDetails.put("s3-var-file-04", s3FileDetailResponses);

    FetchFilesResult fetchFilesResult =
        FetchFilesResult.builder()
            .files(List.of(GitFile.builder().fileContent("git-test-file-content").filePath("test-file-path").build()))
            .build();
    Map<String, FetchFilesResult> gitFileResultMap = new HashMap<>();
    gitFileResultMap.put("git-var-file-02", fetchFilesResult);

    TerraformVarFileConfig inlineVarFileConfig1 = TerraformInlineVarFileConfig.builder()
                                                      .identifier("inline-var-file-01")
                                                      .varFileContent("inline-var-content-1")
                                                      .build();

    TerraformVarFileConfig inlineVarFileConfig5 = TerraformInlineVarFileConfig.builder()
                                                      .identifier("inline-var-file-05")
                                                      .varFileContent("inline-var-content-5")
                                                      .build();

    GitStoreConfigDTO configFiles2 = GithubStoreDTO.builder()
                                         .branch("master")
                                         .repoName("terraform")
                                         .paths(Collections.singletonList("VarFiles/"))
                                         .connectorRef("terraform")
                                         .gitFetchType(FetchType.COMMIT)
                                         .commitId("commit")
                                         .build();

    TerraformVarFileConfig gitVarFileConfig2 =
        TerraformRemoteVarFileConfig.builder().identifier("git-var-file-02").gitStoreConfigDTO(configFiles2).build();

    TerraformRemoteVarFileConfig artifactoryVarFileConfig =
        TerraformRemoteVarFileConfig.builder()
            .identifier("artifactory-var-file-03")
            .fileStoreConfigDTO(ArtifactoryStorageConfigDTO.builder().build())
            .build();

    TerraformRemoteVarFileConfig s3VarFileConfig = TerraformRemoteVarFileConfig.builder()
                                                       .identifier("s3-var-file-04")
                                                       .fileStoreConfigDTO(S3StorageConfigDTO.builder().build())
                                                       .build();

    List<TerraformVarFileConfig> terraformVarFileConfigs = new ArrayList<>();
    terraformVarFileConfigs.add(inlineVarFileConfig1);
    terraformVarFileConfigs.add(gitVarFileConfig2);
    terraformVarFileConfigs.add(artifactoryVarFileConfig);
    terraformVarFileConfigs.add(s3VarFileConfig);
    terraformVarFileConfigs.add(inlineVarFileConfig5);

    terraformPassThroughData.setOriginalVarFileConfigs(terraformVarFileConfigs);
    terraformPassThroughData.setS3VarFilesDetails(s3FetchFileDetails);
    terraformPassThroughData.setGitVarFilesFromMultipleRepo(gitFileResultMap);

    ConnectorInfoDTO connectorInfoDTO = getConnectorInfoDTO();
    ArtifactoryStoreDelegateConfig.builder()
        .repositoryName("repositoryPath")
        .connectorDTO(connectorInfoDTO)
        .succeedIfFileNotFound(false)
        .build();
    TerraformVarFileInfo artifactoryTerraformFileInfo =
        RemoteTerraformVarFileInfo.builder()
            .filestoreFetchFilesConfig(ArtifactoryStoreDelegateConfig.builder()
                                           .repositoryName("repositoryPath")
                                           .connectorDTO(connectorInfoDTO)
                                           .succeedIfFileNotFound(false)
                                           .identifier("artifactory-var-file-03")
                                           .build())
            .build();

    List<TerraformVarFileInfo> varFileInfosInBuilder = new ArrayList<>();
    varFileInfosInBuilder.add(artifactoryTerraformFileInfo);

    TerraformTaskNGParametersBuilder terraformTaskNGParametersBuilder =
        TerraformTaskNGParameters.builder().varFileInfos(varFileInfosInBuilder);
    terraformTaskNGParametersBuilder.accountId("test-account-id-1");
    terraformTaskNGParametersBuilder.entityId("test-entity-id-1");
    terraformTaskNGParametersBuilder.taskType(TFTaskType.PLAN).build();

    Mockito.doReturn("git-test-file-content")
        .when(cdExpressionResolver)
        .updateExpressions(eq(getAmbiance()), eq("git-test-file-content"));
    Mockito.doReturn("s3-test-file-content")
        .when(cdExpressionResolver)
        .updateExpressions(eq(getAmbiance()), eq("s3-test-file-content"));

    List<TerraformVarFileInfo> terraformVarFileInfos = helper.getVarFilesWithInheritedConfigsOrder(
        getAmbiance(), terraformPassThroughData, terraformTaskNGParametersBuilder);

    assertThat(terraformVarFileInfos.size()).isEqualTo(5);
    assertThat(((InlineTerraformVarFileInfo) terraformVarFileInfos.get(0)).getVarFileContent())
        .isEqualTo("inline-var-content-1");
    assertThat(((InlineTerraformVarFileInfo) terraformVarFileInfos.get(1)).getVarFileContent())
        .isEqualTo("git-test-file-content");
    FileStoreFetchFilesConfig fileStoreFetchFilesConfig =
        ((RemoteTerraformVarFileInfo) terraformVarFileInfos.get(2)).getFilestoreFetchFilesConfig();
    assertThat(fileStoreFetchFilesConfig.getIdentifier()).isEqualTo("artifactory-var-file-03");
    assertThat(((InlineTerraformVarFileInfo) terraformVarFileInfos.get(3)).getVarFileContent())
        .isEqualTo("s3-test-file-content");
    assertThat(((InlineTerraformVarFileInfo) terraformVarFileInfos.get(4)).getVarFileContent())
        .isEqualTo("inline-var-content-5");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testAddTerraformRevisionOutcomeIfRequired() {
    StepResponseBuilder stepResponseBuilder = StepResponse.builder().status(Status.SUCCEEDED);
    helper.addTerraformRevisionOutcomeIfRequired(stepResponseBuilder, Collections.singletonMap("key", "commitId"));

    StepResponse.StepOutcome outcome =
        ((List<StepResponse.StepOutcome>) stepResponseBuilder.build().getStepOutcomes()).get(0);

    assertThat(outcome.getName()).isEqualTo(TerraformGitRevisionOutcome.OUTCOME_NAME);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testGitStoreConfigForGithubApp() {
    Ambiance ambiance = getAmbiance();
    ConnectorInfoDTO connectorInfo = ConnectorInfoDTO.builder()
                                         .name("terraform")
                                         .identifier("terraform")
                                         .connectorType(GITHUB)
                                         .connectorConfig(GitConfigDTO.builder()
                                                              .gitAuthType(GitAuthType.HTTP)
                                                              .gitConnectionType(GitConnectionType.ACCOUNT)
                                                              .delegateSelectors(Collections.singleton("delegateName"))
                                                              .url("https://github.com/wings-software")
                                                              .branchName("master")
                                                              .build())
                                         .build();

    doReturn(connectorInfo).when(cdStepHelper).getConnector(anyString(), any());
    doReturn(SSHKeySpecDTO.builder().build())
        .when(mockGitConfigAuthenticationInfoHelper)
        .getSSHKey(any(), anyString(), anyString(), anyString());
    doReturn(Collections.emptyList())
        .when(mockGitConfigAuthenticationInfoHelper)
        .getEncryptedDataDetails(any(), any(), any());
    doNothing().when(cdStepHelper).validateGitStoreConfig(any());

    List<TerraformVarFileConfig> varFileConfigs = new LinkedList<>();

    GitStoreConfigDTO configFiles1 = GithubStoreDTO.builder()
                                         .branch("master")
                                         .repoName("terraform")
                                         .paths(Collections.singletonList("VarFiles/"))
                                         .connectorRef("terraform")
                                         .gitFetchType(FetchType.COMMIT)
                                         .commitId("commit")
                                         .build();

    doReturn(GithubConnectorDTO.builder().build()).when(cdStepHelper).getScmConnector(any(), any(), any(), any());
    TerraformVarFileConfig inlineFileConfig =
        TerraformInlineVarFileConfig.builder().varFileContent("var-content").build();
    TerraformVarFileConfig remoteFileConfig =
        TerraformRemoteVarFileConfig.builder().isOptional(true).gitStoreConfigDTO(configFiles1).build();
    varFileConfigs.add(inlineFileConfig);
    varFileConfigs.add(remoteFileConfig);

    List<TerraformVarFileInfo> terraformVarFileInfos =
        helper.prepareTerraformVarFileInfo(varFileConfigs, ambiance, false);
    verify(cdStepHelper, times(1)).validateGitStoreConfig(any());
    assertThat(terraformVarFileInfos.size()).isEqualTo(2);
    for (TerraformVarFileInfo terraformVarFileInfo : terraformVarFileInfos) {
      if (terraformVarFileInfo instanceof InlineTerraformVarFileInfo) {
        InlineTerraformVarFileInfo inlineTerraformVarFileInfo = (InlineTerraformVarFileInfo) terraformVarFileInfo;
        assertThat(inlineTerraformVarFileInfo.getVarFileContent()).isEqualTo("var-content");
      } else if (terraformVarFileInfo instanceof RemoteTerraformVarFileInfo) {
        RemoteTerraformVarFileInfo remoteTerraformVarFileInfo = (RemoteTerraformVarFileInfo) terraformVarFileInfo;
        assertThat(remoteTerraformVarFileInfo.getGitFetchFilesConfig().getIdentifier()).isEqualTo("TF_VAR_FILES_1");
        assertThat(remoteTerraformVarFileInfo.getGitFetchFilesConfig().getGitStoreDelegateConfig().getBranch())
            .isEqualTo("master");
        assertThat(remoteTerraformVarFileInfo.getGitFetchFilesConfig().getGitStoreDelegateConfig().getPaths().size())
            .isEqualTo(1);
        assertThat(remoteTerraformVarFileInfo.getGitFetchFilesConfig().getGitStoreDelegateConfig().getPaths().get(0))
            .isEqualTo("VarFiles/");
        assertThat(remoteTerraformVarFileInfo.getGitFetchFilesConfig().getGitStoreDelegateConfig().getConnectorName())
            .isEqualTo("terraform");
        assertThat(remoteTerraformVarFileInfo.getGitFetchFilesConfig().getGitStoreDelegateConfig().getGitConfigDTO())
            .isInstanceOf(GithubConnectorDTO.class);
        assertThat(remoteTerraformVarFileInfo.getGitFetchFilesConfig().getGitStoreDelegateConfig().isOptional())
            .isTrue();
      }
    }
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testGetProviderCredentialDelegateInfoNull() {
    Ambiance ambiance = getAmbiance();
    TerraformProviderCredentialDelegateInfo credentialDelegateInfo =
        helper.getProviderCredentialDelegateInfo(null, ambiance);
    assertThat(credentialDelegateInfo).isNull();
    credentialDelegateInfo = helper.getProviderCredentialDelegateInfo(
        TerraformProviderCredential.builder().type(TerraformProviderType.AWS).build(), ambiance);
    assertThat(credentialDelegateInfo).isNull();
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testGetProviderCredentialDelegateInfoInvalidConnector() {
    Ambiance ambiance = getAmbiance();
    doReturn(
        ConnectorInfoDTO.builder().connectorConfig(GitConfigDTO.builder().gitAuthType(GitAuthType.SSH).build()).build())
        .when(cdStepHelper)
        .getConnector(anyString(), any());
    assertThatThrownBy(()
                           -> helper.getProviderCredentialDelegateInfo(
                               TerraformProviderCredential.builder()
                                   .type(TerraformProviderType.AWS)
                                   .spec(AWSIAMRoleCredentialSpec.builder()
                                             .connectorRef(ParameterField.createValueField("dummyConnector"))
                                             .build())
                                   .build(),
                               ambiance))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Connector provided for terraform Aws provider must be of type AWS");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testGetProviderCredentialDelegateInfo() {
    Ambiance ambiance = getAmbiance();
    ConnectorInfoDTO connectorInfoDTO =
        ConnectorInfoDTO.builder()
            .connectorConfig(
                AwsConnectorDTO.builder()
                    .credential(
                        AwsCredentialDTO.builder().awsCredentialType(AwsCredentialType.MANUAL_CREDENTIALS).build())
                    .build())
            .build();

    doReturn(connectorInfoDTO).when(cdStepHelper).getConnector(any(), any());
    doReturn(null).when(mockSecretManagerClientService).getEncryptionDetails(any(), any());

    TerraformProviderCredentialDelegateInfo credentialDelegateInfo = helper.getProviderCredentialDelegateInfo(
        TerraformProviderCredential.builder()
            .type(TerraformProviderType.AWS)
            .spec(AWSIAMRoleCredentialSpec.builder()
                      .connectorRef(ParameterField.createValueField("aws-connector"))
                      .region(ParameterField.createValueField("us-east-1"))
                      .roleArn(ParameterField.createValueField("dummy-role-arn"))
                      .build())
            .build(),
        ambiance);

    assertThat(credentialDelegateInfo).isNotNull();
    assertThat(credentialDelegateInfo).isInstanceOf(TerraformAwsProviderCredentialDelegateInfo.class);
    TerraformAwsProviderCredentialDelegateInfo tfAwsDelegateInfo =
        (TerraformAwsProviderCredentialDelegateInfo) credentialDelegateInfo;
    assertThat(tfAwsDelegateInfo.getConnectorDTO()).isSameAs(connectorInfoDTO);
    assertThat(tfAwsDelegateInfo.getRegion()).isEqualTo("us-east-1");
    assertThat(tfAwsDelegateInfo.getRoleArn()).isEqualTo("dummy-role-arn");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testToTerraformProviderCredential() {
    TerraformProviderCredential providerCredential =
        helper.toTerraformProviderCredential(TerraformAwsProviderCredentialConfig.builder()
                                                 .connectorRef("connectorRef")
                                                 .roleArn("roleArn")
                                                 .region("region")
                                                 .type(TerraformProviderType.AWS)
                                                 .build());

    assertThat(providerCredential).isNotNull();
    assertThat(providerCredential.getSpec()).isNotNull();
    assertThat(providerCredential.getSpec()).isInstanceOf(AWSIAMRoleCredentialSpec.class);
    AWSIAMRoleCredentialSpec spec = (AWSIAMRoleCredentialSpec) providerCredential.getSpec();
    assertThat(ParameterFieldHelper.getParameterFieldValue(spec.getConnectorRef())).isEqualTo("connectorRef");
    assertThat(ParameterFieldHelper.getParameterFieldValue(spec.getRoleArn())).isEqualTo("roleArn");
    assertThat(ParameterFieldHelper.getParameterFieldValue(spec.getRegion())).isEqualTo("region");
  }

  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testGetFetchedCommitId() {
    String gitFileId = "gitFileId-test-123";
    String commitId = "ag4j34m";
    TerraformPassThroughData terraformPassThroughData = TerraformPassThroughData.builder()
                                                            .fetchedCommitIdsMap(new HashMap<>() {
                                                              { put(gitFileId, commitId); }
                                                            })
                                                            .gitVarFilesFromMultipleRepo(null)
                                                            .build();
    String fetchedCommitId = helper.getFetchedCommitId(terraformPassThroughData, gitFileId);

    assertThat(fetchedCommitId).isNotNull();
    assertThat(fetchedCommitId).isEqualTo(commitId);
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testGetFetchedCommitIdFromVarFilesRemoteRepo() {
    String gitFileId = "gitFileId-test-123";
    String commitId = "ag4j35a";
    FetchFilesResult fetchFilesResult =
        FetchFilesResult.builder().commitResult(CommitResult.builder().commitId(commitId).build()).build();

    TerraformPassThroughData terraformPassThroughData = TerraformPassThroughData.builder()
                                                            .fetchedCommitIdsMap(null)
                                                            .gitVarFilesFromMultipleRepo(new HashMap<>() {
                                                              { put(gitFileId, fetchFilesResult); }
                                                            })
                                                            .build();
    String fetchedCommitId = helper.getFetchedCommitId(terraformPassThroughData, gitFileId);

    assertThat(fetchedCommitId).isNotNull();
    assertThat(fetchedCommitId).isEqualTo(commitId);
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testGetFetchedCommitIdWhenFileIdIsNull() {
    String gitFileId = null;
    String commitId = "ag4j35a";
    FetchFilesResult fetchFilesResult =
        FetchFilesResult.builder().commitResult(CommitResult.builder().commitId(commitId).build()).build();

    TerraformPassThroughData terraformPassThroughData = TerraformPassThroughData.builder()
                                                            .fetchedCommitIdsMap(null)
                                                            .gitVarFilesFromMultipleRepo(new HashMap<>() {
                                                              { put(gitFileId, fetchFilesResult); }
                                                            })
                                                            .build();
    String fetchedCommitId = helper.getFetchedCommitId(terraformPassThroughData, gitFileId);

    assertThat(fetchedCommitId).isNull();
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testGetFetchedCommitIdWhenFetchedCommitIdsMapAndVarFilesFromMultipleRepoIsNull() {
    String gitFileId = "gitFileId-test-123";

    TerraformPassThroughData terraformPassThroughData =
        TerraformPassThroughData.builder().fetchedCommitIdsMap(null).gitVarFilesFromMultipleRepo(null).build();
    String fetchedCommitId = helper.getFetchedCommitId(terraformPassThroughData, gitFileId);

    assertThat(fetchedCommitId).isNull();
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testGetRemoteVarFilesInfoWhenVarFileIsOptional() {
    StoreConfig storeVarFiles;

    RemoteTerraformVarFileSpec remoteTerraformVarFileSpec = new RemoteTerraformVarFileSpec();

    TerraformStepDataGenerator.GitStoreConfig gitStoreVarFiles =
        TerraformStepDataGenerator.GitStoreConfig.builder()
            .branch("master")
            .fetchType(FetchType.BRANCH)
            .folderPath(ParameterField.createValueField("VarFiles/"))
            .connectoref(ParameterField.createValueField("terraform"))
            .build();

    storeVarFiles = GithubStore.builder()
                        .branch(ParameterField.createValueField(gitStoreVarFiles.getBranch()))
                        .gitFetchType(gitStoreVarFiles.getFetchType())
                        .repoName(ParameterField.createValueField("testRepo"))
                        .paths(ParameterField.createValueField(List.of("testPath1, testPath2")))
                        .folderPath(ParameterField.createValueField(gitStoreVarFiles.getFolderPath().getValue()))
                        .connectorRef(ParameterField.createValueField(gitStoreVarFiles.getConnectoref().getValue()))
                        .build();
    remoteTerraformVarFileSpec.setOptional(ParameterField.createValueField(true));
    remoteTerraformVarFileSpec.setStore(
        StoreConfigWrapper.builder().spec(storeVarFiles).type(StoreConfigType.GITHUB).build());

    LinkedHashMap<String, TerraformVarFile> varFilesMap = new LinkedHashMap<>();
    varFilesMap.put("var-file-02",
        TerraformVarFile.builder().identifier("var-file-02").type("Remote").spec(remoteTerraformVarFileSpec).build());

    ConnectorInfoDTO connectorInfo = ConnectorInfoDTO.builder()
                                         .name("terraform")
                                         .identifier("terraform")
                                         .connectorType(GITHUB)
                                         .connectorConfig(GitConfigDTO.builder()
                                                              .gitAuthType(GitAuthType.HTTP)
                                                              .gitConnectionType(GitConnectionType.ACCOUNT)
                                                              .delegateSelectors(Collections.singleton("delegateName"))
                                                              .url("https://github.com/wings-software")
                                                              .branchName("master")
                                                              .validationRepo("test")
                                                              .build())
                                         .build();

    doNothing().when(cdStepHelper).validateGitStoreConfig(any());
    doNothing().when(cdStepHelper).validateManifest(any(), any(), any());

    doReturn(connectorInfo).when(cdStepHelper).getConnector(any(), any());
    doReturn(SSHKeySpecDTO.builder().build())
        .when(mockGitConfigAuthenticationInfoHelper)
        .getSSHKey(any(), anyString(), anyString(), anyString());
    doReturn(GithubConnectorDTO.builder().build()).when(cdStepHelper).getScmConnector(any(), any(), any(), any());

    doReturn(Collections.emptyList())
        .when(mockGitConfigAuthenticationInfoHelper)
        .getEncryptedDataDetails(any(), any(), any());

    List<TerraformVarFileInfo> varFileInfos = helper.getRemoteVarFilesInfo(varFilesMap, getAmbiance());

    assertThat(varFileInfos).isNotEmpty();
    assertThat(((RemoteTerraformVarFileInfo) varFileInfos.get(0))
                   .getGitFetchFilesConfig()
                   .getGitStoreDelegateConfig()
                   .isOptional())
        .isTrue();
    assertThat(((RemoteTerraformVarFileInfo) varFileInfos.get(0)).getGitFetchFilesConfig().getIdentifier())
        .isEqualTo("var-file-02");
    assertThat(((RemoteTerraformVarFileInfo) varFileInfos.get(0)).getGitFetchFilesConfig().getManifestType())
        .isEqualTo("GIT VAR_FILES");
  }
}
