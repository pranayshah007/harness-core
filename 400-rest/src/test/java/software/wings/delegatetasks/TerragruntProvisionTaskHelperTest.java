/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.beans.FileBucket.TERRAFORM_STATE;
import static io.harness.git.model.GitRepositoryType.TERRAGRUNT;
import static io.harness.provision.TerraformConstants.WORKSPACE_STATE_FILE_PATH_FORMAT;
import static io.harness.rule.OwnerRule.TATHAGAT;

import static software.wings.delegatetasks.TerragruntProvisionTaskHelper.copyFilesToWorkingDirectory;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.filesystem.FileIo;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.EncryptedRecordData;

import software.wings.api.terraform.TfVarGitSource;
import software.wings.beans.GitConfig;
import software.wings.beans.GitFileConfig;
import software.wings.beans.NameValuePair;
import software.wings.beans.delegation.TerragruntProvisionParameters;
import software.wings.beans.yaml.GitFetchFilesRequest;
import software.wings.service.impl.yaml.GitClientHelper;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.service.intfc.yaml.GitClient;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(CDP)
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class TerragruntProvisionTaskHelperTest extends CategoryTest {
  @Mock private GitClient gitClient;
  @Mock private GitClientHelper gitClientHelper;
  @Mock private EncryptionService encryptionService;
  @Mock private DelegateFileManager delegateFileManager;
  @Mock private LogCallback logCallback;
  @Mock private BufferedWriter bufferedWriter;

  @InjectMocks @Inject TerragruntProvisionTaskHelper terragruntProvisionTaskHelper;

  private TerragruntProvisionParameters terragruntProvisionParameters = TerragruntProvisionParameters.builder().build();
  private static final String GIT_BRANCH = "master";
  private static final String TERRAFORM_CONFIG_FILE_DIRECTORY = "configFileDirectory";

  @Before
  public void setUp() throws Exception {
    initMocks(this);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testPopulateGitConfigAndSaveExecutionLog() {
    GitConfig gitConfig = GitConfig.builder().branch(GIT_BRANCH).reference("reference").build();
    terragruntProvisionTaskHelper.setGitRepoTypeAndSaveExecutionLog(
        terragruntProvisionParameters, gitConfig, logCallback);
    assertThat(gitConfig.getGitRepoType()).isEqualTo(TERRAGRUNT);
    verify(logCallback, times(3)).saveExecutionLog(any(), any(), any());
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testSaveVariable() throws IOException {
    terragruntProvisionTaskHelper.saveVariable(bufferedWriter, "key", "value");
    verify(bufferedWriter).write("key = \"value\" \n");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testGetCommandLineVariableParams() throws IOException {
    doReturn(new char[] {'v', '2'}).when(encryptionService).getDecryptedValue(any(), eq(false));
    TerragruntProvisionParameters parameters =
        TerragruntProvisionParameters.builder()
            .variables(ImmutableMap.of("k1", "v1"))
            .encryptedVariables(ImmutableMap.of("k2", EncryptedDataDetail.builder().build()))
            .build();
    StringBuilder inlineCommandBuffer = new StringBuilder();
    StringBuilder inlineUILogBuffer = new StringBuilder();
    terragruntProvisionTaskHelper.getCommandLineVariableParams(
        parameters, null, inlineCommandBuffer, inlineUILogBuffer);
    String varParams = inlineCommandBuffer.toString();
    String uiLogs = inlineUILogBuffer.toString();
    assertThat(varParams).isEqualTo(" -var='k1=v1'  -var='k2=v2' ");
    assertThat(uiLogs).isEqualTo(" -var='k1=v1'  -var='k2=HarnessSecret:[k2]' ");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testFetchTfVarGitSource() {
    GitFileConfig gitFileConfig = GitFileConfig.builder()
                                      .branch(GIT_BRANCH)
                                      .commitId("commitId")
                                      .useBranch(true)
                                      .connectorId("connectorId")
                                      .build();
    TfVarGitSource tfVarGitSource = TfVarGitSource.builder()
                                        .gitFileConfig(gitFileConfig)
                                        .gitConfig(GitConfig.builder().build())
                                        .encryptedDataDetails(singletonList(EncryptedDataDetail.builder().build()))
                                        .build();
    TerragruntProvisionParameters provisionParameters =
        TerragruntProvisionParameters.builder().tfVarSource(tfVarGitSource).build();

    terragruntProvisionTaskHelper.fetchTfVarGitSource(provisionParameters, "tfvarDirectory", logCallback);

    ArgumentCaptor<GitFetchFilesRequest> requestArgumentCaptor = ArgumentCaptor.forClass(GitFetchFilesRequest.class);
    verify(logCallback, times(2)).saveExecutionLog(any(), any(), any());
    verify(encryptionService, times(1))
        .decrypt(tfVarGitSource.getGitConfig(), tfVarGitSource.getEncryptedDataDetails(), false);
    verify(gitClient).downloadFiles(any(GitConfig.class), requestArgumentCaptor.capture(), anyString(), eq(false));
    GitFetchFilesRequest gitFetchFilesRequest = requestArgumentCaptor.getValue();
    assertThat(gitFetchFilesRequest.getBranch()).isEqualTo(gitFileConfig.getBranch());
    assertThat(gitFetchFilesRequest.getCommitId()).isEqualTo(gitFileConfig.getCommitId());
    assertThat(gitFetchFilesRequest.isUseBranch()).isEqualTo(gitFileConfig.isUseBranch());
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testCopyFilesToWorkingDirectory() throws IOException {
    String destDir = "destDir";
    String srcDir = "srcDir";
    File destinationFile = new File(destDir);
    FileIo.createDirectoryIfDoesNotExist(srcDir);
    FileIo.createDirectoryIfDoesNotExist("srcDir/nestedSource");
    FileIo.createDirectoryIfDoesNotExist(destDir);

    assertThat(destinationFile.list()).hasSize(0);
    copyFilesToWorkingDirectory(srcDir, destDir);
    assertThat(destinationFile.list()).hasSize(1);
    assertThat(destinationFile.list()).contains("nestedSource");

    // clean up
    FileIo.deleteDirectoryAndItsContentIfExists(srcDir);
    FileIo.deleteDirectoryAndItsContentIfExists(destDir);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testGetEnvironmentVariables() throws IOException {
    // no environment variable
    TerragruntProvisionParameters parametersEmptyEnvVar = TerragruntProvisionParameters.builder().build();
    ImmutableMap<String, String> environmentVariables =
        terragruntProvisionTaskHelper.getEnvironmentVariables(parametersEmptyEnvVar);
    verify(encryptionService, times(0)).getDecryptedValue(any(EncryptedDataDetail.class), anyBoolean());
    assertThat(environmentVariables).isEmpty();

    // with environment variables
    TerragruntProvisionParameters parameters =
        TerragruntProvisionParameters.builder()
            .environmentVariables(Collections.singletonMap("username", "user"))
            .encryptedEnvironmentVariables(Collections.singletonMap("password", EncryptedDataDetail.builder().build()))
            .build();
    when(encryptionService.getDecryptedValue(any(EncryptedDataDetail.class), anyBoolean()))
        .thenReturn("decrypted_password".toCharArray());
    environmentVariables = terragruntProvisionTaskHelper.getEnvironmentVariables(parameters);
    verify(encryptionService, times(1)).getDecryptedValue(any(EncryptedDataDetail.class), anyBoolean());
    assertThat(environmentVariables.size()).isEqualTo(2);
    assertThat(environmentVariables.keySet()).contains("username", "password");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testCollectEnvVarKeys() {
    Map<String, String> envVars = new HashMap<>();
    assertThat(terragruntProvisionTaskHelper.collectEnvVarKeys(envVars)).isEqualTo("");
    envVars.put("k1", "v1");
    envVars.put("k2", "v2");
    assertThat(terragruntProvisionTaskHelper.collectEnvVarKeys(envVars)).isEqualTo("k1, k2");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testDownloadTfStateFile() throws IOException {
    TerragruntProvisionParameters provisionParameters = TerragruntProvisionParameters.builder()
                                                            .workspace("default")
                                                            .currentStateFileId("stateFileId")
                                                            .accountId(ACCOUNT_ID)
                                                            .build();

    InputStream fileContent = IOUtils.toInputStream("fileContent", Charset.defaultCharset());
    doReturn(fileContent).when(delegateFileManager).downloadByFileId(any(), any(), any());

    File tfStateFile = Paths
                           .get(TERRAFORM_CONFIG_FILE_DIRECTORY,
                               format(WORKSPACE_STATE_FILE_PATH_FORMAT, provisionParameters.getWorkspace()))
                           .toFile();

    terragruntProvisionTaskHelper.downloadTfStateFile(provisionParameters, "configFileDirectory");
    verify(delegateFileManager)
        .downloadByFileId(
            TERRAFORM_STATE, provisionParameters.getCurrentStateFileId(), provisionParameters.getAccountId());
    assertThat(new String(Files.readAllBytes(tfStateFile.toPath()))).isEqualTo("fileContent");
    // clean up
    Files.deleteIfExists(tfStateFile.toPath());
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testDownloadTfStateFileForRemoteStateFile() throws IOException {
    TerragruntProvisionParameters provisionParameters = TerragruntProvisionParameters.builder()
                                                            .workspace("default")
                                                            .currentStateFileId("stateFileId")
                                                            .accountId(ACCOUNT_ID)
                                                            .build();

    InputStream fileContent = IOUtils.toInputStream("", Charset.defaultCharset());
    doReturn(fileContent).when(delegateFileManager).downloadByFileId(any(), any(), any());

    File tfStateFile = Paths
                           .get(TERRAFORM_CONFIG_FILE_DIRECTORY,
                               format(WORKSPACE_STATE_FILE_PATH_FORMAT, provisionParameters.getWorkspace()))
                           .toFile();

    terragruntProvisionTaskHelper.downloadTfStateFile(provisionParameters, "configFileDirectory");
    verify(delegateFileManager)
        .downloadByFileId(
            TERRAFORM_STATE, provisionParameters.getCurrentStateFileId(), provisionParameters.getAccountId());
    assertThat(tfStateFile).doesNotExist();
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testGetTargetArgs() {
    List<String> targets = asList("target1", "target2");
    assertThat(terragruntProvisionTaskHelper.getTargetArgs(emptyList())).isEqualTo("");
    assertThat(terragruntProvisionTaskHelper.getTargetArgs(targets)).isEqualTo("-target=target1 -target=target2 ");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testShouldSkipRefresh() {
    assertThat(
        terragruntProvisionTaskHelper.shouldSkipRefresh(
            TerragruntProvisionParameters.builder().encryptedTfPlan(EncryptedRecordData.builder().build()).build()))
        .isFalse();

    assertThat(
        terragruntProvisionTaskHelper.shouldSkipRefresh(TerragruntProvisionParameters.builder()
                                                            .encryptedTfPlan(EncryptedRecordData.builder().build())
                                                            .skipRefreshBeforeApplyingPlan(true)
                                                            .build()))
        .isTrue();
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testGetAllVariables() {
    assertThat(terragruntProvisionTaskHelper.getAllVariables(emptyMap(), emptyMap())).hasSize(0);

    List<NameValuePair> allVariables = terragruntProvisionTaskHelper.getAllVariables(singletonMap("textVar", "v1"),
        singletonMap("encryptedVar",
            EncryptedDataDetail.builder().encryptedData(EncryptedRecordData.builder().build()).build()));
    assertThat(allVariables).hasSize(2);
    assertThat(allVariables.get(0).getName()).isEqualTo("textVar");
    assertThat(allVariables.get(1).getName()).isEqualTo("encryptedVar");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testGetTerraformStateFile() throws IOException {
    String tfStateFilePath = "configFileDirectory/terraform.tfstate.d/workspace/terraform.tfstate";
    File tfStateFile = new File(tfStateFilePath);
    InputStream fileContent = IOUtils.toInputStream("fileContent", Charset.defaultCharset());
    FileUtils.copyInputStreamToFile(fileContent, tfStateFile);

    File resultTfStateFile =
        terragruntProvisionTaskHelper.getTerraformStateFile(TERRAFORM_CONFIG_FILE_DIRECTORY, "workspace");

    assertThat(resultTfStateFile.toPath().toString()).isEqualTo(tfStateFilePath);
    assertThat(new String(Files.readAllBytes(resultTfStateFile.toPath()))).isEqualTo("fileContent");

    // clean up
    Files.deleteIfExists(resultTfStateFile.toPath());
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testSaveBacknedConfigToFile() throws IOException {
    TerragruntProvisionTaskHelper spyTerragruntProvisionTaskHelper = spy(terragruntProvisionTaskHelper);

    Map<String, String> backendConfig = new HashMap<>();
    backendConfig.put("k1", "v1");
    backendConfig.put("k2", "v2");
    Map<String, EncryptedDataDetail> encryptedBackendConfig = new HashMap<>();
    encryptedBackendConfig.put("encryptedVar1", EncryptedDataDetail.builder().build());
    encryptedBackendConfig.put("encryptedVar2", EncryptedDataDetail.builder().build());

    when(encryptionService.getDecryptedValue(any(EncryptedDataDetail.class), anyBoolean()))
        .thenReturn("decrypted_password".toCharArray());

    spyTerragruntProvisionTaskHelper.saveBacknedConfigToFile(
        new File("tfBackendConfig"), backendConfig, encryptedBackendConfig);
    verify(encryptionService, times(2)).getDecryptedValue(any(EncryptedDataDetail.class), anyBoolean());
    verify(spyTerragruntProvisionTaskHelper, times(4)).saveVariable(any(), any(), any());
  }
}
