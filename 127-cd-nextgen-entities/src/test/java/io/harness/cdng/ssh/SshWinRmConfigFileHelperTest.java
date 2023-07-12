/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.ssh;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ACASIAN;
import static io.harness.rule.OwnerRule.IVAN;
import static io.harness.rule.OwnerRule.VITALIE;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.configfile.ConfigFileOutcome;
import io.harness.cdng.configfile.ConfigGitFile;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.manifest.yaml.BitbucketStore;
import io.harness.cdng.manifest.yaml.GitLabStore;
import io.harness.cdng.manifest.yaml.GitStore;
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.cdng.manifest.yaml.GithubStore;
import io.harness.cdng.manifest.yaml.harness.HarnessStore;
import io.harness.delegate.beans.storeconfig.GitFetchedStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.HarnessStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.StoreDelegateConfig;
import io.harness.delegate.task.ssh.config.ConfigFileParameters;
import io.harness.delegate.task.ssh.config.FileDelegateConfig;
import io.harness.exception.InvalidRequestException;
import io.harness.filestore.dto.node.FileNodeDTO;
import io.harness.filestore.dto.node.FolderNodeDTO;
import io.harness.filestore.service.FileStoreService;
import io.harness.ng.core.api.NGEncryptedDataService;
import io.harness.ng.core.filestore.FileUsage;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDP)
public class SshWinRmConfigFileHelperTest extends CategoryTest {
  private static final String ACCOUNT_ID = "test";
  private static final String PROJECT_ID = "testProject";
  private static final String ORG_ID = "testOrg";
  private static final String CONFIG_FILE_VALID_PATH = "validFilePath";
  private static final String CONFIG_FILE_VALID_SECRET_ID = "validSecretFileId";
  private static final String CONFIG_FILE_CONTENT = "content";
  private static final long CONFIG_FILE_SIZE = 20L;
  private static final long CONFIG_FILE_16MB_SIZE = 16 * 1024 * 1024;
  private static final String CONFIG_FILE_NAME = "sshConfigFile";
  private static final String ENCRYPTED_FILE_NAME = "encryptedFileName";
  private static final String CONFIG_FILE_FOLDER_NAME = "configFolder";

  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock private FileStoreService fileStoreService;
  @Mock private NGEncryptedDataService ngEncryptedDataService;
  @Mock private CDExpressionResolver cdExpressionResolver;

  @InjectMocks private SshWinRmConfigFileHelper sshWinRmConfigFileHelper;

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetFileDelegateConfig() {
    Ambiance ambiance = getAmbiance();
    Map<String, ConfigFileOutcome> configFilesOutcome = new HashMap<>();
    HarnessStore harnessStore = HarnessStore.builder()
                                    .files(ParameterField.createValueField(List.of(CONFIG_FILE_VALID_PATH)))
                                    .secretFiles(ParameterField.createValueField(List.of(CONFIG_FILE_VALID_SECRET_ID)))
                                    .build();
    configFilesOutcome.put(
        "validConfigFile", ConfigFileOutcome.builder().identifier("validConfigFile").store(harnessStore).build());
    when(cdExpressionResolver.updateExpressions(ambiance, harnessStore)).thenReturn(harnessStore);
    when(cdExpressionResolver.renderExpression(eq(ambiance), anyString(), anyBoolean()))
        .thenReturn(CONFIG_FILE_CONTENT);
    when(fileStoreService.getWithChildrenByPath(ACCOUNT_ID, ORG_ID, PROJECT_ID, CONFIG_FILE_VALID_PATH, true))
        .thenReturn(Optional.of(getFileNodeDTO()));
    when(ngEncryptedDataService.getEncryptionDetails(any(), any()))
        .thenReturn(List.of(EncryptedDataDetail.builder().fieldName(ENCRYPTED_FILE_NAME).build()));

    FileDelegateConfig fileDelegateConfig =
        sshWinRmConfigFileHelper.getFileDelegateConfig(configFilesOutcome, ambiance);

    assertThat(fileDelegateConfig.getStores()).isNotEmpty();
    List<StoreDelegateConfig> stores = fileDelegateConfig.getStores();
    HarnessStoreDelegateConfig storeDelegateConfig = (HarnessStoreDelegateConfig) stores.get(0);
    assertThat(storeDelegateConfig.getConfigFiles()).isNotEmpty();
    assertThat(storeDelegateConfig.getConfigFiles().size()).isEqualTo(2);

    ConfigFileParameters configFile = storeDelegateConfig.getConfigFiles().get(0);
    assertThat(configFile.getFileName()).isEqualTo(CONFIG_FILE_NAME);
    assertThat(configFile.getFileContent()).isEqualTo(CONFIG_FILE_CONTENT);
    assertThat(configFile.getFileSize()).isEqualTo(CONFIG_FILE_SIZE);

    ConfigFileParameters secretConfigFile = storeDelegateConfig.getConfigFiles().get(1);
    assertThat(secretConfigFile.getFileName()).isEqualTo(CONFIG_FILE_VALID_SECRET_ID);
    assertThat(secretConfigFile.getEncryptionDataDetails().get(0).getFieldName()).isEqualTo(ENCRYPTED_FILE_NAME);
    assertThat(secretConfigFile.getSecretConfigFile().getEncryptedConfigFile().getIdentifier())
        .isEqualTo(CONFIG_FILE_VALID_SECRET_ID);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testGetFileDelegateConfigFromGithub() {
    GithubStore githubStore = GithubStore.builder().build();
    verifyGetFileDelegateConfig(githubStore);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testGetFileDelegateConfigFromGit() {
    GitStore gitStore = GitStore.builder().build();
    verifyGetFileDelegateConfig(gitStore);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetFileDelegateConfigFromBitBucket() {
    BitbucketStore bitbucketStore = BitbucketStore.builder().build();
    verifyGetFileDelegateConfig(bitbucketStore);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testGetFileDelegateConfigFromGitLab() {
    GitLabStore gitLabStore = GitLabStore.builder().build();
    verifyGetFileDelegateConfig(gitLabStore);
  }

  private void verifyGetFileDelegateConfig(GitStoreConfig githubStore) {
    Ambiance ambiance = getAmbiance();
    Map<String, ConfigFileOutcome> configFilesOutcome = new HashMap<>();

    configFilesOutcome.put("validConfigFile",
        ConfigFileOutcome.builder()
            .identifier("validConfigFile")
            .gitFiles(List.of(
                ConfigGitFile.builder().filePath("/path/" + CONFIG_FILE_NAME).fileContent(CONFIG_FILE_CONTENT).build()))
            .store(githubStore)
            .build());
    when(cdExpressionResolver.updateExpressions(ambiance, githubStore)).thenReturn(githubStore);
    when(cdExpressionResolver.renderExpression(any(), anyString(), anyBoolean())).thenReturn(CONFIG_FILE_CONTENT);

    when(fileStoreService.getWithChildrenByPath(ACCOUNT_ID, ORG_ID, PROJECT_ID, CONFIG_FILE_VALID_PATH, true))
        .thenReturn(Optional.of(getFileNodeDTO()));
    when(ngEncryptedDataService.getEncryptionDetails(any(), any()))
        .thenReturn(List.of(EncryptedDataDetail.builder().fieldName(ENCRYPTED_FILE_NAME).build()));

    FileDelegateConfig fileDelegateConfig =
        sshWinRmConfigFileHelper.getFileDelegateConfig(configFilesOutcome, ambiance);

    assertThat(fileDelegateConfig.getStores()).isNotEmpty();
    List<StoreDelegateConfig> stores = fileDelegateConfig.getStores();
    GitFetchedStoreDelegateConfig storeDelegateConfig = (GitFetchedStoreDelegateConfig) stores.get(0);
    assertThat(storeDelegateConfig.getConfigFiles()).isNotEmpty();
    assertThat(storeDelegateConfig.getConfigFiles().size()).isEqualTo(1);

    ConfigFileParameters configFile = storeDelegateConfig.getConfigFiles().get(0);
    assertThat(configFile.getFileName()).isEqualTo(CONFIG_FILE_NAME);
    assertThat(configFile.getFileContent()).isEqualTo(CONFIG_FILE_CONTENT);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldRenderConfigFiles() {
    Ambiance ambiance = getAmbiance();
    Map<String, ConfigFileOutcome> configFilesOutcome = new HashMap<>();
    HarnessStore harnessStore =
        HarnessStore.builder().files(ParameterField.createValueField(List.of(CONFIG_FILE_VALID_PATH))).build();
    configFilesOutcome.put(
        "validConfigFile", ConfigFileOutcome.builder().identifier("validConfigFile").store(harnessStore).build());
    when(cdExpressionResolver.updateExpressions(ambiance, harnessStore)).thenReturn(harnessStore);
    when(fileStoreService.getWithChildrenByPath(ACCOUNT_ID, ORG_ID, PROJECT_ID, CONFIG_FILE_VALID_PATH, true))
        .thenReturn(Optional.of(getFileNodeDTO()));
    when(cdExpressionResolver.renderExpression(eq(ambiance), any(), anyBoolean())).thenReturn(CONFIG_FILE_CONTENT);

    FileDelegateConfig fileDelegateConfig =
        sshWinRmConfigFileHelper.getFileDelegateConfig(configFilesOutcome, ambiance);

    assertThat(fileDelegateConfig.getStores()).isNotEmpty();
    List<StoreDelegateConfig> stores = fileDelegateConfig.getStores();
    HarnessStoreDelegateConfig storeDelegateConfig = (HarnessStoreDelegateConfig) stores.get(0);
    assertThat(storeDelegateConfig.getConfigFiles()).isNotEmpty();
    assertThat(storeDelegateConfig.getConfigFiles().size()).isEqualTo(1);

    ConfigFileParameters configFile = storeDelegateConfig.getConfigFiles().get(0);
    assertThat(configFile.getFileName()).isEqualTo(CONFIG_FILE_NAME);
    assertThat(configFile.getFileContent()).isEqualTo(CONFIG_FILE_CONTENT);
    assertThat(configFile.getFileSize()).isEqualTo(CONFIG_FILE_SIZE);
    verify(cdExpressionResolver, times(1)).renderExpression(eq(ambiance), any(), anyBoolean());
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldNotRenderSecretConfigFiles() {
    Ambiance ambiance = getAmbiance();
    Map<String, ConfigFileOutcome> configFilesOutcome = new HashMap<>();
    HarnessStore harnessStore = HarnessStore.builder()
                                    .secretFiles(ParameterField.createValueField(List.of(CONFIG_FILE_VALID_SECRET_ID)))
                                    .build();
    configFilesOutcome.put(
        "validConfigFile", ConfigFileOutcome.builder().identifier("validConfigFile").store(harnessStore).build());
    when(cdExpressionResolver.updateExpressions(ambiance, harnessStore)).thenReturn(harnessStore);
    when(fileStoreService.getWithChildrenByPath(ACCOUNT_ID, ORG_ID, PROJECT_ID, CONFIG_FILE_VALID_PATH, true))
        .thenReturn(Optional.of(getFileNodeDTO()));
    when(ngEncryptedDataService.getEncryptionDetails(any(), any()))
        .thenReturn(List.of(EncryptedDataDetail.builder().fieldName(ENCRYPTED_FILE_NAME).build()));

    FileDelegateConfig fileDelegateConfig =
        sshWinRmConfigFileHelper.getFileDelegateConfig(configFilesOutcome, ambiance);

    assertThat(fileDelegateConfig.getStores()).isNotEmpty();
    List<StoreDelegateConfig> stores = fileDelegateConfig.getStores();
    HarnessStoreDelegateConfig storeDelegateConfig = (HarnessStoreDelegateConfig) stores.get(0);
    assertThat(storeDelegateConfig.getConfigFiles()).isNotEmpty();
    assertThat(storeDelegateConfig.getConfigFiles().size()).isEqualTo(1);

    ConfigFileParameters secretConfigFile = storeDelegateConfig.getConfigFiles().get(0);
    assertThat(secretConfigFile.getFileName()).isEqualTo(CONFIG_FILE_VALID_SECRET_ID);
    assertThat(secretConfigFile.getEncryptionDataDetails().get(0).getFieldName()).isEqualTo(ENCRYPTED_FILE_NAME);
    assertThat(secretConfigFile.getSecretConfigFile().getEncryptedConfigFile().getIdentifier())
        .isEqualTo(CONFIG_FILE_VALID_SECRET_ID);
    verify(cdExpressionResolver, times(0)).renderExpression(any(), any(), anyBoolean());
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetFileDelegateConfigWith16MBFileSize() {
    Ambiance ambiance = getAmbiance();
    Map<String, ConfigFileOutcome> configFilesOutcome = new HashMap<>();
    HarnessStore harnessStore =
        HarnessStore.builder().files(ParameterField.createValueField(List.of(CONFIG_FILE_VALID_PATH))).build();
    configFilesOutcome.put(
        "invalidConfigFile", ConfigFileOutcome.builder().identifier("invalidConfigFile").store(harnessStore).build());
    when(cdExpressionResolver.updateExpressions(ambiance, harnessStore)).thenReturn(harnessStore);
    when(fileStoreService.getWithChildrenByPath(ACCOUNT_ID, ORG_ID, PROJECT_ID, CONFIG_FILE_VALID_PATH, true))
        .thenReturn(Optional.of(getFileNodeDTOWith16MBFile()));

    assertThatThrownBy(() -> sshWinRmConfigFileHelper.getFileDelegateConfig(configFilesOutcome, ambiance))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Config file size is larger than maximum [15728640], path [validFilePath], scope: [PROJECT]");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetFileDelegateConfigWithConfigFileFolder() {
    Ambiance ambiance = getAmbiance();
    Map<String, ConfigFileOutcome> configFilesOutcome = new HashMap<>();
    HarnessStore harnessStore =
        HarnessStore.builder().files(ParameterField.createValueField(List.of(CONFIG_FILE_VALID_PATH))).build();
    configFilesOutcome.put(
        "invalidConfigFile", ConfigFileOutcome.builder().identifier("invalidConfigFile").store(harnessStore).build());
    when(cdExpressionResolver.updateExpressions(ambiance, harnessStore)).thenReturn(harnessStore);
    when(fileStoreService.getWithChildrenByPath(ACCOUNT_ID, ORG_ID, PROJECT_ID, CONFIG_FILE_VALID_PATH, true))
        .thenReturn(Optional.of(getFolderNodeDTO()));

    assertThatThrownBy(() -> sshWinRmConfigFileHelper.getFileDelegateConfig(configFilesOutcome, ambiance))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Config file cannot be directory, path [validFilePath], scope: [PROJECT]");
  }

  private Ambiance getAmbiance() {
    return Ambiance.newBuilder()
        .putSetupAbstractions(SetupAbstractionKeys.accountId, ACCOUNT_ID)
        .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, PROJECT_ID)
        .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, ORG_ID)
        .build();
  }

  private FileNodeDTO getFileNodeDTOWith16MBFile() {
    return FileNodeDTO.builder()
        .name(CONFIG_FILE_NAME)
        .fileUsage(FileUsage.CONFIG)
        .content(CONFIG_FILE_CONTENT)
        .size(CONFIG_FILE_16MB_SIZE)
        .build();
  }

  private FileNodeDTO getFileNodeDTO() {
    return FileNodeDTO.builder()
        .name(CONFIG_FILE_NAME)
        .fileUsage(FileUsage.CONFIG)
        .content(CONFIG_FILE_CONTENT)
        .size(CONFIG_FILE_SIZE)
        .build();
  }

  private FolderNodeDTO getFolderNodeDTO() {
    return FolderNodeDTO.builder().name(CONFIG_FILE_FOLDER_NAME).build();
  }
}
