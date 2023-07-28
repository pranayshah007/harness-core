/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.terragrunt;

import static io.harness.delegate.beans.connector.ConnectorType.GITHUB;
import static io.harness.rule.OwnerRule.SOURABH;
import static io.harness.rule.OwnerRule.TMACARI;
import static io.harness.rule.OwnerRule.VLICA;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.fileservice.FileServiceClient;
import io.harness.cdng.fileservice.FileServiceClientFactory;
import io.harness.cdng.manifest.yaml.GitStoreConfigDTO;
import io.harness.cdng.manifest.yaml.GitStoreDTO;
import io.harness.cdng.manifest.yaml.GithubStore;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.provision.terraform.TerraformStepDataGenerator;
import io.harness.cdng.provision.terraform.executions.TerraformPlanExectionDetailsService;
import io.harness.cdng.provision.terraform.executions.TerraformPlanExecutionDetails;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.validator.scmValidators.GitConfigAuthenticationInfoHelper;
import io.harness.delegate.beans.FileBucket;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.InlineStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.StoreDelegateConfig;
import io.harness.delegate.beans.terragrunt.request.TerragruntRunConfiguration;
import io.harness.delegate.beans.terragrunt.request.TerragruntTaskRunType;
import io.harness.delegate.beans.terragrunt.response.TerragruntPlanTaskResponse;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.UnitProgress;
import io.harness.logging.UnitStatus;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.persistence.HPersistence;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.ParameterField;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.dto.VaultConfigDTO;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.security.encryption.EncryptionConfig;
import io.harness.security.encryption.EncryptionType;

import software.wings.beans.GcpKmsConfig;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.MockitoRule;
import retrofit2.Call;
import retrofit2.Response;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(HarnessTeam.CDP)
public class TerragruntStepHelperTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private CDFeatureFlagHelper cdFeatureFlagHelper;
  @Mock private FileServiceClientFactory mockFileServiceFactory;
  @Mock private CDStepHelper cdStepHelper;
  @Mock private GitConfigAuthenticationInfoHelper gitConfigAuthenticationInfoHelper;
  @Mock private SecretManagerClientService secretManagerClientService;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;
  @Mock TerraformPlanExectionDetailsService terraformPlanExectionDetailsService;
  @Mock public TerragruntConfigDAL terragruntConfigDAL;
  @Mock private FileServiceClient mockFileService;
  @Mock private HPersistence persistence;

  @InjectMocks private TerragruntStepHelper helper;
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
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testPrepareEntityDetailsForVarFiles() {
    LinkedHashMap<String, TerragruntVarFile> varFilesMap = TerragruntTestStepUtils.createVarFilesRemote();

    List<EntityDetail> entityDetails =
        TerragruntStepHelper.prepareEntityDetailsForVarFiles("test-account", "test-org", "test-project", varFilesMap);

    EntityDetail entityDetail = entityDetails.get(0);
    assertThat(entityDetail.getEntityRef().getIdentifier()).isEqualTo("terragrunt-varFiles");
    assertThat(entityDetail.getEntityRef().getOrgIdentifier()).isEqualTo("test-org");
    assertThat(entityDetail.getEntityRef().getProjectIdentifier()).isEqualTo("test-project");
    assertThat(entityDetail.getEntityRef().getAccountIdentifier()).isEqualTo("test-account");
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testPrepareEntityDetailForBackendConfigFileRemote() {
    TerragruntBackendConfig backendConfig = TerragruntTestStepUtils.createRemoteBackendConfig();

    Optional<EntityDetail> entityDetail = TerragruntStepHelper.prepareEntityDetailForBackendConfigFiles(
        "test-account", "test-org", "test-project", backendConfig);
    assertThat(entityDetail.isPresent()).isTrue();
    assertThat(entityDetail.get().getEntityRef().getIdentifier()).isEqualTo("terragrunt-backendFile");
    assertThat(entityDetail.get().getEntityRef().getOrgIdentifier()).isEqualTo("test-org");
    assertThat(entityDetail.get().getEntityRef().getProjectIdentifier()).isEqualTo("test-project");
    assertThat(entityDetail.get().getEntityRef().getAccountIdentifier()).isEqualTo("test-account");
  }

  @Test
  @Owner(developers = VLICA)
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
  @Owner(developers = VLICA)
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
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testGetGitFetchFilesConfig() {
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

    TerraformStepDataGenerator.GitStoreConfig gitStoreConfigFiles =
        TerraformStepDataGenerator.GitStoreConfig.builder()
            .branch("master")
            .fetchType(FetchType.BRANCH)
            .folderPath(ParameterField.createValueField("Config/"))
            .connectoref(ParameterField.createValueField("terragrunt-configFiles"))
            .build();

    StoreConfig storeConfigFiles;
    storeConfigFiles =
        GithubStore.builder()
            .repoName(ParameterField.createValueField("test-repo-name"))
            .branch(ParameterField.createValueField(gitStoreConfigFiles.getBranch()))
            .gitFetchType(gitStoreConfigFiles.getFetchType())
            .folderPath(ParameterField.createValueField(gitStoreConfigFiles.getFolderPath().getValue()))
            .connectorRef(ParameterField.createValueField(gitStoreConfigFiles.getConnectoref().getValue()))
            .build();

    doReturn(GitConfigDTO.builder()
                 .gitAuthType(GitAuthType.HTTP)
                 .gitConnectionType(GitConnectionType.ACCOUNT)
                 .delegateSelectors(Collections.singleton("delegateName"))
                 .url("https://github.com/wings-software/test-repo-name")
                 .branchName("master")
                 .build())
        .when(cdStepHelper)
        .getScmConnector(any(), any());
    doReturn(connectorInfo).when(cdStepHelper).getConnector(anyString(), any());
    doReturn(SSHKeySpecDTO.builder().build())
        .when(gitConfigAuthenticationInfoHelper)
        .getSSHKey(any(), anyString(), anyString(), anyString());
    doReturn(Collections.emptyList())
        .when(gitConfigAuthenticationInfoHelper)
        .getEncryptedDataDetails(any(), any(), any());

    GitStoreDelegateConfig storeDelegateConfig =
        helper.getGitFetchFilesConfig(storeConfigFiles, getAmbiance(), "TG_CONFIG_FILES");

    assertThat(storeDelegateConfig).isNotNull();
    assertThat(storeDelegateConfig.getBranch()).isEqualTo("master");
    assertThat(storeDelegateConfig.getConnectorName()).isEqualTo("terraform");
    assertThat(storeDelegateConfig.getPaths().get(0)).isEqualTo("Config/");
    GitConfigDTO configDTO = (GitConfigDTO) storeDelegateConfig.getGitConfigDTO();
    assertThat(configDTO.getGitAuthType().getDisplayName()).isEqualTo("Http");
    assertThat(configDTO.getUrl()).isEqualTo("https://github.com/wings-software/test-repo-name");
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void toStoreDelegateVarFilesInline() {
    LinkedHashMap<String, TerragruntVarFile> varFilesMap = TerragruntTestStepUtils.createVarFilesInline();

    List<StoreDelegateConfig> storeDelegateConfigList = helper.toStoreDelegateVarFiles(varFilesMap, getAmbiance());

    assertThat(storeDelegateConfigList).isNotEmpty();
    assertThat(((InlineStoreDelegateConfig) storeDelegateConfigList.get(0)).getFiles().get(0).getContent())
        .isEqualTo("test-varFile-Content");
    assertThat(((InlineStoreDelegateConfig) storeDelegateConfigList.get(0)).getFiles().get(0).getName())
        .isEqualTo("terragrunt-${UUID}.tfvars");
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void toStoreDelegateVarFilesRemote() {
    LinkedHashMap<String, TerragruntVarFile> varFilesMap = TerragruntTestStepUtils.createVarFilesRemote();

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
                 .url("https://github.com/wings-software/test-repo-name-var-file")
                 .branchName("master")
                 .build())
        .when(cdStepHelper)
        .getScmConnector(any(), any());
    doReturn(connectorInfo).when(cdStepHelper).getConnector(anyString(), any());
    doReturn(SSHKeySpecDTO.builder().build())
        .when(gitConfigAuthenticationInfoHelper)
        .getSSHKey(any(), anyString(), anyString(), anyString());
    doReturn(Collections.emptyList())
        .when(gitConfigAuthenticationInfoHelper)
        .getEncryptedDataDetails(any(), any(), any());

    List<StoreDelegateConfig> storeDelegateConfigList = helper.toStoreDelegateVarFiles(varFilesMap, getAmbiance());
    assertThat(storeDelegateConfigList).isNotEmpty();
    assertThat(((GitStoreDelegateConfig) storeDelegateConfigList.get(0)).getBranch()).isEqualTo("master");
    assertThat(((GitStoreDelegateConfig) storeDelegateConfigList.get(0)).getConnectorName()).isEqualTo("terraform");
    assertThat(((GitStoreDelegateConfig) storeDelegateConfigList.get(0)).getPaths().get(0)).isEqualTo("path/to");
    GitConfigDTO configDTO = (GitConfigDTO) ((GitStoreDelegateConfig) storeDelegateConfigList.get(0)).getGitConfigDTO();
    assertThat(configDTO.getGitAuthType().getDisplayName()).isEqualTo("Http");
    assertThat(configDTO.getUrl()).isEqualTo("https://github.com/wings-software/test-repo-name-var-file");
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testGetBackendConfigWhenRemote() {
    TerragruntBackendConfig backendConfig = TerragruntTestStepUtils.createRemoteBackendConfig();

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
                 .url("https://github.com/wings-software/test-repo-name-be-file")
                 .branchName("master")
                 .build())
        .when(cdStepHelper)
        .getScmConnector(any(), any());
    doReturn(connectorInfo).when(cdStepHelper).getConnector(anyString(), any());
    doReturn(SSHKeySpecDTO.builder().build())
        .when(gitConfigAuthenticationInfoHelper)
        .getSSHKey(any(), anyString(), anyString(), anyString());
    doReturn(Collections.emptyList())
        .when(gitConfigAuthenticationInfoHelper)
        .getEncryptedDataDetails(any(), any(), any());

    StoreDelegateConfig storeDelegateConfig = helper.getBackendConfig(backendConfig, getAmbiance());
    assertThat(storeDelegateConfig).isNotNull();
    assertThat(((GitStoreDelegateConfig) storeDelegateConfig).getBranch()).isEqualTo("master");
    assertThat(((GitStoreDelegateConfig) storeDelegateConfig).getConnectorName()).isEqualTo("terraform");
    assertThat(((GitStoreDelegateConfig) storeDelegateConfig).getPaths().get(0)).isEqualTo("backend/");
    GitConfigDTO configDTO = (GitConfigDTO) ((GitStoreDelegateConfig) storeDelegateConfig).getGitConfigDTO();
    assertThat(configDTO.getGitAuthType().getDisplayName()).isEqualTo("Http");
    assertThat(configDTO.getUrl()).isEqualTo("https://github.com/wings-software/test-repo-name-be-file");
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testGetBackendConfigWhenInline() {
    TerragruntBackendConfig backendConfig = TerragruntTestStepUtils.createInlineBackendConfig();

    StoreDelegateConfig storeDelegateConfig = helper.getBackendConfig(backendConfig, getAmbiance());

    assertThat(storeDelegateConfig).isNotNull();
    assertThat(((InlineStoreDelegateConfig) storeDelegateConfig).getFiles().get(0).getContent())
        .isEqualTo("back-content");
    assertThat(((InlineStoreDelegateConfig) storeDelegateConfig).getFiles().get(0).getName())
        .isEqualTo("terragrunt-${UUID}.tfvars");
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testGetEncryptionConfig() {
    TerragruntPlanStepParameters parameters =
        TerragruntPlanStepParameters.infoBuilder()
            .provisionerIdentifier(ParameterField.createValueField("test-provisionerId"))
            .configuration(TerragruntPlanExecutionDataParameters.builder()
                               .secretManagerRef(ParameterField.createValueField("test-secretManager"))
                               .build())
            .build();

    doReturn(VaultConfigDTO.builder()
                 .basePath("testBasePath")
                 .renewAppRoleToken(false)
                 .encryptionType(EncryptionType.VAULT)
                 .build())
        .when(secretManagerClientService)
        .getSecretManager(anyString(), anyString(), anyString(), anyString(), anyBoolean());

    EncryptionConfig encryptionConfig = helper.getEncryptionConfig(getAmbiance(), parameters);
    assertThat(encryptionConfig).isNotNull();
  }

  @Test
  @Owner(developers = VLICA)
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
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testGetSavedInheritOutputFoundFirst() {
    Ambiance ambiance = getAmbiance();
    ExecutionSweepingOutput terragruntInheritOutput = TerragruntInheritOutput.builder().build();
    OptionalSweepingOutput foundOptionalSweepingOutput =
        OptionalSweepingOutput.builder().found(true).output(terragruntInheritOutput).build();
    Mockito.doReturn(foundOptionalSweepingOutput)
        .when(executionSweepingOutputService)
        .resolveOptional(ambiance,
            RefObjectUtils.getSweepingOutputRefObject("tgInheritOutput_APPLY_test-account/test-org/test-project/test"));

    TerragruntInheritOutput inheritOutput = helper.getSavedInheritOutput("test", "APPLY", ambiance);
    assertThat(inheritOutput).isEqualTo(terragruntInheritOutput);
    verify(executionSweepingOutputService, times(1)).resolveOptional(any(), any());
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testToStoreDelegateVarFilesFromTgConfigInline() {
    List<TerragruntVarFileConfig> varFileConfig =
        List.of(TerragruntInlineVarFileConfig.builder().varFileContent("test-var1-content").build());

    List<StoreDelegateConfig> storeDelegateConfigList =
        helper.toStoreDelegateVarFilesFromTgConfig(varFileConfig, getAmbiance());
    assertThat(storeDelegateConfigList).isNotEmpty();
    assertThat(((InlineStoreDelegateConfig) storeDelegateConfigList.get(0)).getFiles().get(0).getName())
        .isEqualTo("terragrunt-${UUID}.tfvars");
    assertThat(((InlineStoreDelegateConfig) storeDelegateConfigList.get(0)).getFiles().get(0).getContent())
        .isEqualTo("test-var1-content");
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testToStoreDelegateVarFilesFromTgConfigRemote() {
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
                 .url("https://github.com/wings-software/test-repo-name")
                 .branchName("master")
                 .build())
        .when(cdStepHelper)
        .getScmConnector(any(), any());
    doReturn(connectorInfo).when(cdStepHelper).getConnector(anyString(), any());
    doReturn(SSHKeySpecDTO.builder().build())
        .when(gitConfigAuthenticationInfoHelper)
        .getSSHKey(any(), anyString(), anyString(), anyString());
    doReturn(Collections.emptyList())
        .when(gitConfigAuthenticationInfoHelper)
        .getEncryptedDataDetails(any(), any(), any());

    TerraformStepDataGenerator.GitStoreConfig gitStoreConfigFiles =
        TerraformStepDataGenerator.GitStoreConfig.builder()
            .branch("master")
            .fetchType(FetchType.BRANCH)
            .folderPath(ParameterField.createValueField("Config/"))
            .connectoref(ParameterField.createValueField("terragrunt-configFiles"))
            .build();

    GitStoreConfigDTO storeConfigFiles;
    storeConfigFiles =
        GithubStore.builder()
            .repoName(ParameterField.createValueField("test-repo-name"))
            .branch(ParameterField.createValueField(gitStoreConfigFiles.getBranch()))
            .gitFetchType(gitStoreConfigFiles.getFetchType())
            .folderPath(ParameterField.createValueField(gitStoreConfigFiles.getFolderPath().getValue()))
            .paths(ParameterField.createValueField(List.of("path-var-file")))
            .connectorRef(ParameterField.createValueField(gitStoreConfigFiles.getConnectoref().getValue()))
            .build()
            .toGitStoreConfigDTO();

    List<TerragruntVarFileConfig> varFileConfig =
        List.of(TerragruntRemoteVarFileConfig.builder().gitStoreConfigDTO(storeConfigFiles).build());

    List<StoreDelegateConfig> storeDelegateConfigList =
        helper.toStoreDelegateVarFilesFromTgConfig(varFileConfig, getAmbiance());
    assertThat(storeDelegateConfigList).isNotEmpty();
    assertThat(((GitStoreDelegateConfig) storeDelegateConfigList.get(0)).getBranch()).isEqualTo("master");
    assertThat(((GitStoreDelegateConfig) storeDelegateConfigList.get(0)).getConnectorName()).isEqualTo("terraform");
    assertThat(((GitStoreDelegateConfig) storeDelegateConfigList.get(0)).getPaths().get(0)).isEqualTo("path-var-file");
    GitConfigDTO configDTO = (GitConfigDTO) ((GitStoreDelegateConfig) storeDelegateConfigList.get(0)).getGitConfigDTO();
    assertThat(configDTO.getGitAuthType().getDisplayName()).isEqualTo("Http");
    assertThat(configDTO.getUrl()).isEqualTo("https://github.com/wings-software/test-repo-name");
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testSaveTerragruntConfig() {
    TerragruntConfig terragruntConfig = TerragruntConfig.builder()
                                            .pipelineExecutionId("executionId")
                                            .configFiles(GitStoreDTO.builder().build())
                                            .workspace("test-workspace")
                                            .targets(new ArrayList<>() {
                                              { add("test-target"); }
                                            })
                                            .runConfiguration(TerragruntRunConfiguration.builder()
                                                                  .runType(TerragruntTaskRunType.RUN_MODULE)
                                                                  .path("test-path")
                                                                  .build())
                                            .environmentVariables(new HashMap<>() {
                                              { put("envKey", "envVal"); }
                                            })
                                            .build();

    doNothing().when(terragruntConfigDAL).saveTerragruntConfig(terragruntConfig);
    ArgumentCaptor<TerragruntConfig> terragruntConfigArgumentCaptor = ArgumentCaptor.forClass(TerragruntConfig.class);

    helper.saveTerragruntConfig(terragruntConfig, getAmbiance());
    verify(terragruntConfigDAL, times(1)).saveTerragruntConfig(terragruntConfigArgumentCaptor.capture());
    TerragruntConfig terraformConfigCaptured = terragruntConfigArgumentCaptor.getValue();
    assertThat(terraformConfigCaptured).isNotNull();
    assertThat(terraformConfigCaptured.getConfigFiles()).isEqualTo(terragruntConfig.getConfigFiles());
    assertThat(terraformConfigCaptured.getWorkspace()).isEqualTo(terragruntConfig.getWorkspace());
    assertThat(terraformConfigCaptured.getEnvironmentVariables()).isEqualTo(terragruntConfig.getEnvironmentVariables());
    assertThat(terraformConfigCaptured.getRunConfiguration()).isEqualTo(terragruntConfig.getRunConfiguration());
    assertThat(terraformConfigCaptured.getPipelineExecutionId()).isEqualTo("exec_id");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testSaveTerragruntConfigForRollbackExecutionMode() {
    TerragruntConfig terragruntConfig = TerragruntConfig.builder()
                                            .pipelineExecutionId("executionId")
                                            .configFiles(GitStoreDTO.builder().build())
                                            .workspace("test-workspace")
                                            .targets(new ArrayList<>() {
                                              { add("test-target"); }
                                            })
                                            .runConfiguration(TerragruntRunConfiguration.builder()
                                                                  .runType(TerragruntTaskRunType.RUN_MODULE)
                                                                  .path("test-path")
                                                                  .build())
                                            .environmentVariables(new HashMap<>() {
                                              { put("envKey", "envVal"); }
                                            })
                                            .build();

    doNothing().when(terragruntConfigDAL).saveTerragruntConfig(terragruntConfig);
    ArgumentCaptor<TerragruntConfig> terragruntConfigArgumentCaptor = ArgumentCaptor.forClass(TerragruntConfig.class);

    helper.saveTerragruntConfig(terragruntConfig,
        getAmbiance()
            .toBuilder()
            .setMetadata(ExecutionMetadata.newBuilder()
                             .setExecutionMode(ExecutionMode.PIPELINE_ROLLBACK)
                             .setOriginalPlanExecutionIdForRollbackMode("original_exec_id")
                             .build())
            .build());
    verify(terragruntConfigDAL, times(1)).saveTerragruntConfig(terragruntConfigArgumentCaptor.capture());
    TerragruntConfig terraformConfigCaptured = terragruntConfigArgumentCaptor.getValue();
    assertThat(terraformConfigCaptured).isNotNull();
    assertThat(terraformConfigCaptured.getConfigFiles()).isEqualTo(terragruntConfig.getConfigFiles());
    assertThat(terraformConfigCaptured.getWorkspace()).isEqualTo(terragruntConfig.getWorkspace());
    assertThat(terraformConfigCaptured.getEnvironmentVariables()).isEqualTo(terragruntConfig.getEnvironmentVariables());
    assertThat(terraformConfigCaptured.getRunConfiguration()).isEqualTo(terragruntConfig.getRunConfiguration());
    assertThat(terraformConfigCaptured.getPipelineExecutionId()).isEqualTo("original_exec_id");
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testSaveTerragruntInheritOutput() {
    TerragruntConfigFilesWrapper configFilesWrapper = TerragruntTestStepUtils.createConfigFilesWrapper();
    LinkedHashMap<String, TerragruntVarFile> varFilesMap = TerragruntTestStepUtils.createVarFilesInline();
    TerragruntBackendConfig backendConfig = TerragruntTestStepUtils.createInlineBackendConfig();

    Map<String, Object> envVars = new HashMap<>() {
      { put("envKey", ParameterField.createValueField("envVal")); }
    };
    TerragruntPlanStepParameters parameters =
        TerragruntPlanStepParameters.infoBuilder()
            .provisionerIdentifier(ParameterField.createValueField("test_provisionerId"))
            .configuration(TerragruntPlanExecutionDataParameters.builder()
                               .command(TerragruntPlanCommand.APPLY)
                               .configFiles(configFilesWrapper)
                               .backendConfig(backendConfig)
                               .varFiles(varFilesMap)
                               .terragruntModuleConfig(TerragruntTestStepUtils.createTerragruntModuleConfig())
                               .exportTerragruntPlanJson(ParameterField.createValueField(true))
                               .targets(ParameterField.createValueField(Collections.singletonList("test-target")))
                               .environmentVariables(envVars)
                               .workspace(ParameterField.createValueField("test-workspace"))
                               .secretManagerRef(ParameterField.createValueField("test-secretManager"))
                               .build())
            .build();

    List<UnitProgress> unitProgresses = new ArrayList<>();
    unitProgresses.add(UnitProgress.newBuilder().setStatus(UnitStatus.SUCCESS).build());

    TerragruntPlanTaskResponse terragruntTaskNGResponse =
        TerragruntPlanTaskResponse.builder()
            .unitProgressData(UnitProgressData.builder().unitProgresses(unitProgresses).build())
            .stateFileId("test-stateFileId")
            .planJsonFileId("test-planJsonFileId")
            .configFilesSourceReference("test-configFileSourceRef")
            .backendFileSourceReference("test-backendFileSourceRef")
            .varFilesSourceReference(new HashMap<>() {
              { put("test-var-file-ref-key", "test-var-file-ref-value"); }
            })
            .build();

    ArgumentCaptor<TerragruntInheritOutput> captor = ArgumentCaptor.forClass(TerragruntInheritOutput.class);

    helper.saveTerragruntInheritOutput(parameters, terragruntTaskNGResponse, getAmbiance());
    verify(executionSweepingOutputService, times(1)).consume(any(), any(), captor.capture(), anyString());

    TerragruntInheritOutput output = captor.getValue();
    assertThat(output).isNotNull();
    assertThat(output.getWorkspace()).isEqualTo("test-workspace");
    assertThat(output.getTargets().get(0)).isEqualTo("test-target");
    assertThat(output.getRunConfiguration().getRunType().name()).isEqualTo("RUN_MODULE");
    assertThat(output.getRunConfiguration().getPath()).isEqualTo("test-path");
    assertThat(output.getConfigFiles()).isNotNull();
    assertThat(output.getVarFileConfigs()).isNotNull();
    assertThat(output.getBackendConfigFile()).isNotNull();
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testGetBackendConfigFromTgConfigInline() {
    TerragruntBackendConfigFileConfig backendConfigFileConfig =
        TerragruntInlineBackendConfigFileConfig.builder().backendConfigFileContent("test-backend1-content").build();

    StoreDelegateConfig storeDelegateConfig =
        helper.getBackendConfigFromTgConfig(backendConfigFileConfig, getAmbiance());
    assertThat(storeDelegateConfig).isNotNull();
    assertThat(((InlineStoreDelegateConfig) storeDelegateConfig).getFiles().get(0).getName())
        .isEqualTo("terragrunt-${UUID}.tfvars");
    assertThat(((InlineStoreDelegateConfig) storeDelegateConfig).getFiles().get(0).getContent())
        .isEqualTo("test-backend1-content");
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testGetBackendConfigFromTgConfigRemote() {
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
                 .url("https://github.com/wings-software/test-repo-name")
                 .branchName("master")
                 .build())
        .when(cdStepHelper)
        .getScmConnector(any(), any());
    doReturn(connectorInfo).when(cdStepHelper).getConnector(anyString(), any());
    doReturn(SSHKeySpecDTO.builder().build())
        .when(gitConfigAuthenticationInfoHelper)
        .getSSHKey(any(), anyString(), anyString(), anyString());
    doReturn(Collections.emptyList())
        .when(gitConfigAuthenticationInfoHelper)
        .getEncryptedDataDetails(any(), any(), any());

    TerraformStepDataGenerator.GitStoreConfig gitStoreConfigFiles =
        TerraformStepDataGenerator.GitStoreConfig.builder()
            .branch("master")
            .fetchType(FetchType.BRANCH)
            .folderPath(ParameterField.createValueField("path-be-config/"))
            .connectoref(ParameterField.createValueField("terragrunt-configFiles"))
            .build();

    GitStoreConfigDTO storeConfigFiles =
        GithubStore.builder()
            .repoName(ParameterField.createValueField("test-repo-name"))
            .branch(ParameterField.createValueField(gitStoreConfigFiles.getBranch()))
            .gitFetchType(gitStoreConfigFiles.getFetchType())
            .folderPath(ParameterField.createValueField(gitStoreConfigFiles.getFolderPath().getValue()))
            .paths(ParameterField.createValueField(List.of("path-be-file")))
            .connectorRef(ParameterField.createValueField(gitStoreConfigFiles.getConnectoref().getValue()))
            .build()
            .toGitStoreConfigDTO();

    TerragruntBackendConfigFileConfig backendConfigFileConfig =
        TerragruntRemoteBackendConfigFileConfig.builder().gitStoreConfigDTO(storeConfigFiles).build();
    StoreDelegateConfig storeDelegateConfig =
        helper.getBackendConfigFromTgConfig(backendConfigFileConfig, getAmbiance());
    assertThat(storeDelegateConfig).isNotNull();
    assertThat(((GitStoreDelegateConfig) storeDelegateConfig).getBranch()).isEqualTo("master");
    assertThat(((GitStoreDelegateConfig) storeDelegateConfig).getConnectorName()).isEqualTo("terraform");
    assertThat(((GitStoreDelegateConfig) storeDelegateConfig).getPaths().get(0)).isEqualTo("path-be-config/");
    GitConfigDTO configDTO = (GitConfigDTO) ((GitStoreDelegateConfig) storeDelegateConfig).getGitConfigDTO();
    assertThat(configDTO.getGitAuthType().getDisplayName()).isEqualTo("Http");
    assertThat(configDTO.getUrl()).isEqualTo("https://github.com/wings-software/test-repo-name");
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testSaveTerragruntPlanExecutionDetails() {
    TerragruntConfigFilesWrapper configFilesWrapper = TerragruntTestStepUtils.createConfigFilesWrapper();
    LinkedHashMap<String, TerragruntVarFile> varFilesMap = TerragruntTestStepUtils.createVarFilesInline();
    TerragruntBackendConfig backendConfig = TerragruntTestStepUtils.createInlineBackendConfig();

    Map<String, Object> envVars = new HashMap<>() {
      { put("envKey", ParameterField.createValueField("envVal")); }
    };
    TerragruntPlanStepParameters planParameters =
        TerragruntPlanStepParameters.infoBuilder()
            .provisionerIdentifier(ParameterField.createValueField("test_provisionerId"))
            .configuration(TerragruntPlanExecutionDataParameters.builder()
                               .command(TerragruntPlanCommand.APPLY)
                               .configFiles(configFilesWrapper)
                               .backendConfig(backendConfig)
                               .varFiles(varFilesMap)
                               .terragruntModuleConfig(TerragruntTestStepUtils.createTerragruntModuleConfig())
                               .exportTerragruntPlanJson(ParameterField.createValueField(true))
                               .targets(ParameterField.createValueField(Collections.singletonList("test-target")))
                               .environmentVariables(envVars)
                               .workspace(ParameterField.createValueField("test-workspace"))
                               .secretManagerRef(ParameterField.createValueField("test-secretManager"))
                               .build())
            .build();

    List<UnitProgress> unitProgresses = new ArrayList<>();
    unitProgresses.add(UnitProgress.newBuilder().setStatus(UnitStatus.SUCCESS).build());

    TerragruntPlanTaskResponse terragruntTaskNGResponse =
        TerragruntPlanTaskResponse.builder()
            .unitProgressData(UnitProgressData.builder().unitProgresses(unitProgresses).build())
            .stateFileId("test-stateFileId")
            .planJsonFileId("test-planJsonFileId")
            .configFilesSourceReference("test-configFileSourceRef")
            .backendFileSourceReference("test-backendFileSourceRef")
            .encryptedPlan(EncryptedRecordData.builder().build())
            .varFilesSourceReference(new HashMap<>() {
              { put("test-var-file-ref-key", "test-var-file-ref-value"); }
            })
            .build();

    ArgumentCaptor<TerraformPlanExecutionDetails> planExecutionDetailsCaptor =
        ArgumentCaptor.forClass(TerraformPlanExecutionDetails.class);

    helper.saveTerragruntPlanExecutionDetails(
        getAmbiance(), terragruntTaskNGResponse, "test_provisionerId", planParameters);

    verify(terraformPlanExectionDetailsService, times(1)).save(planExecutionDetailsCaptor.capture());
    TerraformPlanExecutionDetails terraformPlanExecutionDetails = planExecutionDetailsCaptor.getValue();
    assertThat(terraformPlanExecutionDetails).isNotNull();
    assertThat(terraformPlanExecutionDetails.getProvisionerId()).isEqualTo("test_provisionerId");
    assertThat(terraformPlanExecutionDetails.getTfPlanJsonFieldId()).isEqualTo("test-planJsonFileId");
    assertThat(terraformPlanExecutionDetails.getTfPlanFileBucket()).isEqualTo("TERRAFORM_PLAN_JSON");
    assertThat(terraformPlanExecutionDetails.getAccountIdentifier()).isEqualTo("test-account");
    assertThat(terraformPlanExecutionDetails.getOrgIdentifier()).isEqualTo("test-org");
    assertThat(terraformPlanExecutionDetails.getProjectIdentifier()).isEqualTo("test-project");
    assertThat(terraformPlanExecutionDetails.getPipelineExecutionId()).isEqualTo("exec_id");
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

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testFetchFileConfigForGithubApp() {
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

    TerraformStepDataGenerator.GitStoreConfig gitStoreConfigFiles =
        TerraformStepDataGenerator.GitStoreConfig.builder()
            .branch("master")
            .fetchType(FetchType.BRANCH)
            .folderPath(ParameterField.createValueField("Config/"))
            .connectoref(ParameterField.createValueField("terragrunt-configFiles"))
            .build();

    StoreConfig storeConfigFiles;
    storeConfigFiles =
        GithubStore.builder()
            .repoName(ParameterField.createValueField("test-repo-name"))
            .branch(ParameterField.createValueField(gitStoreConfigFiles.getBranch()))
            .gitFetchType(gitStoreConfigFiles.getFetchType())
            .folderPath(ParameterField.createValueField(gitStoreConfigFiles.getFolderPath().getValue()))
            .connectorRef(ParameterField.createValueField(gitStoreConfigFiles.getConnectoref().getValue()))
            .build();

    doReturn(GithubConnectorDTO.builder().build()).when(cdStepHelper).getScmConnector(any(), any());
    doReturn(connectorInfo).when(cdStepHelper).getConnector(anyString(), any());
    doReturn(SSHKeySpecDTO.builder().build())
        .when(gitConfigAuthenticationInfoHelper)
        .getSSHKey(any(), anyString(), anyString(), anyString());
    doReturn(Collections.emptyList())
        .when(gitConfigAuthenticationInfoHelper)
        .getEncryptedDataDetails(any(), any(), any());

    GitStoreDelegateConfig storeDelegateConfig =
        helper.getGitFetchFilesConfig(storeConfigFiles, getAmbiance(), "TG_CONFIG_FILES");

    assertThat(storeDelegateConfig).isNotNull();
    assertThat(storeDelegateConfig.getBranch()).isEqualTo("master");
    assertThat(storeDelegateConfig.getConnectorName()).isEqualTo("terraform");
    assertThat(storeDelegateConfig.getPaths().get(0)).isEqualTo("Config/");
    assertThat(storeDelegateConfig.getGitConfigDTO()).isInstanceOf(GithubConnectorDTO.class);
  }
}
