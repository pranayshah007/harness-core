/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.terragrunt;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.task.terragrunt.TerragruntTestUtils.ACCOUNT_ID;
import static io.harness.delegate.task.terragrunt.TerragruntTestUtils.ENTITY_ID;
import static io.harness.delegate.task.terragrunt.TerragruntTestUtils.TG_RUN_PATH;
import static io.harness.delegate.task.terragrunt.TerragruntTestUtils.TG_SCRIPT_DIR;
import static io.harness.delegate.task.terragrunt.TerragruntTestUtils.TG_TEST_BASE_DIR;
import static io.harness.delegate.task.terragrunt.TerragruntTestUtils.TG_WORKING_DIR;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.rule.OwnerRule.SOURABH;
import static io.harness.rule.OwnerRule.VLICA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.version.Version;
import io.harness.category.element.UnitTests;
import io.harness.cli.CliHelper;
import io.harness.connector.helper.DecryptionHelper;
import io.harness.connector.task.git.ScmConnectorMapperDelegate;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.connector.scm.github.GithubAppDTO;
import io.harness.delegate.beans.connector.scm.github.GithubAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.github.GithubHttpCredentialsDTO;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.beans.terragrunt.request.AbstractTerragruntTaskParameters;
import io.harness.delegate.beans.terragrunt.request.TerragruntRunConfiguration;
import io.harness.delegate.beans.terragrunt.request.TerragruntTaskRunType;
import io.harness.delegate.task.terraform.TerraformBaseHelper;
import io.harness.delegate.task.terraform.handlers.HarnessSMEncryptionDecryptionHandlerNG;
import io.harness.delegate.task.terragrunt.files.DownloadResult;
import io.harness.delegate.task.terragrunt.files.FetchFilesResult;
import io.harness.delegate.task.terragrunt.files.TerragruntDownloadService;
import io.harness.encryption.SecretRefData;
import io.harness.exception.runtime.TerragruntFetchFilesRuntimeException;
import io.harness.filesystem.FileIo;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.EncryptDecryptHelper;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.terragrunt.v2.TerragruntClientFactory;
import io.harness.terragrunt.v2.TerragruntClientImpl;

import software.wings.beans.VaultConfig;
import software.wings.delegatetasks.DelegateFileManager;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.commons.io.IOUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDP)
@RunWith(MockitoJUnitRunner.class)
public class TerragruntTaskServiceTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private TerragruntDownloadService terragruntDownloadService;
  @Mock private DelegateFileManager delegateFileManager;
  @Mock private TerragruntClientFactory terragruntClientFactory;
  @Mock private DecryptionHelper decryptionHelper;
  @Mock private EncryptDecryptHelper encryptDecryptHelper;
  @Mock private LogCallback logCallback;
  @Mock private LogCallback executionCommandLogCallback;
  @Mock private CliHelper cliHelper;
  @Mock private TerraformBaseHelper terraformBaseHelper;
  @Mock private HarnessSMEncryptionDecryptionHandlerNG harnessSMEncryptionDecryptionHandlerNG;
  @Mock private ScmConnectorMapperDelegate scmConnectorMapperDelegate;

  @InjectMocks private TerragruntTaskService taskService = new TerragruntTaskService();

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testPrepareTerragruntWhenRunModule() throws IOException {
    TerragruntRunConfiguration runConfiguration =
        TerragruntRunConfiguration.builder().runType(TerragruntTaskRunType.RUN_MODULE).path(TG_RUN_PATH).build();
    AbstractTerragruntTaskParameters parameters = TerragruntTestUtils.createPlanTaskParameters(runConfiguration);
    parameters.setTgModuleSourceInheritSSH(true);

    when(terragruntDownloadService.download(eq(parameters.getConfigFilesStore()), any(), any(), any()))
        .thenReturn(DownloadResult.builder()
                        .rootDirectory("test-root-directory")
                        .sourceReference("test-123-config-source-ref")
                        .build());

    when(terragruntDownloadService.fetchFiles(eq(parameters.getVarFiles().get(0)), any(), any(), any()))
        .thenReturn(FetchFilesResult.builder()
                        .identifier("test-var-file-identifier")
                        .filesSourceReference("test-123-varFile-source-ref")
                        .files(List.of("test-varFile-123.tfVars"))
                        .build());
    when(terragruntDownloadService.fetchFiles(eq(parameters.getBackendFilesStore()), any(), any(), any()))
        .thenReturn(FetchFilesResult.builder()
                        .identifier("test-be-file-identifier")
                        .filesSourceReference("test-123-be-source-ref")
                        .files(List.of("test-be-123.tfVars"))
                        .build());

    when(terragruntClientFactory.getClient(any(), anyLong(), any(), any(), any()))
        .thenReturn(TerragruntClientImpl.builder()
                        .cliHelper(cliHelper)
                        .terragruntInfoJson("{ \"WorkingDir\": \"workingDir/\" }")
                        .terraformVersion(Version.parse("1.2.3"))
                        .terragruntVersion(Version.parse("1.1.1"))
                        .build());

    InputStream inputStream = IOUtils.toInputStream("Some content", "UTF-8");
    when(delegateFileManager.downloadByFileId(any(), any(), any())).thenReturn(inputStream);

    FileIo.createDirectoryIfDoesNotExist(TG_WORKING_DIR);

    TerragruntContext terragruntContext =
        taskService.prepareTerragrunt(logCallback, parameters, TG_TEST_BASE_DIR, executionCommandLogCallback);

    assertThat(terragruntContext).isNotNull();
    assertThat(terragruntContext.getWorkingDirectory()).isEqualTo(TG_TEST_BASE_DIR + "/terragrunt-script-repository");
    assertThat(terragruntContext.getVarFilesDirectory()).isEqualTo(TG_TEST_BASE_DIR + "/tf-var-files");
    assertThat(terragruntContext.getTerragruntWorkingDirectory()).isEqualTo("workingDir/");
    assertThat(terragruntContext.getConfigFilesSourceReference()).isEqualTo("test-123-config-source-ref");
    assertThat(terragruntContext.getBackendFile()).isEqualTo("test-be-123.tfVars");
    assertThat(terragruntContext.getBackendFileSourceReference()).isEqualTo("test-123-be-source-ref");
    assertThat(terragruntContext.getVarFiles().get(0)).isEqualTo("test-varFile-123.tfVars");
    assertThat(terragruntContext.getVarFilesSourceReference().get("test-var-file-identifier"))
        .isEqualTo("test-123-varFile-source-ref");
    assertThat(terragruntContext.getClient()).isNotNull();
    verify(delegateFileManager, times(1)).downloadByFileId(any(), any(), any());
    verify(terraformBaseHelper, times(1)).configureCredentialsForModuleSource(any(), any(), any(), any());
    FileIo.deleteDirectoryAndItsContentIfExists(TG_WORKING_DIR);
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testPrepareTerragruntWhenRunAll() throws IOException {
    TerragruntRunConfiguration runConfiguration =
        TerragruntRunConfiguration.builder().runType(TerragruntTaskRunType.RUN_ALL).path(TG_RUN_PATH).build();
    AbstractTerragruntTaskParameters parameters = TerragruntTestUtils.createPlanTaskParameters(runConfiguration);

    when(terragruntDownloadService.download(eq(parameters.getConfigFilesStore()), any(), any(), any()))
        .thenReturn(DownloadResult.builder()
                        .rootDirectory("test-root-directory")
                        .sourceReference("test-123-config-source-ref")
                        .build());

    when(terragruntDownloadService.fetchFiles(eq(parameters.getVarFiles().get(0)), any(), any(), any()))
        .thenReturn(FetchFilesResult.builder()
                        .identifier("test-var-file-identifier")
                        .filesSourceReference("test-123-varFile-source-ref")
                        .files(List.of("test-varFile-123.tfVars"))
                        .build());
    when(terragruntDownloadService.fetchFiles(eq(parameters.getBackendFilesStore()), any(), any(), any()))
        .thenReturn(FetchFilesResult.builder()
                        .identifier("test-be-file-identifier")
                        .filesSourceReference("test-123-be-source-ref")
                        .files(List.of("test-be-123.tfVars"))
                        .build());

    when(terragruntClientFactory.getClient(any(), anyLong(), any(), any(), any()))
        .thenReturn(TerragruntClientImpl.builder()
                        .cliHelper(cliHelper)
                        .terragruntInfoJson("{ \"WorkingDir\": \"workingDir/\" }")
                        .terraformVersion(Version.parse("1.2.3"))
                        .terragruntVersion(Version.parse("1.1.1"))
                        .build());

    InputStream inputStream = IOUtils.toInputStream("Some content", "UTF-8");
    when(delegateFileManager.downloadByFileId(any(), any(), any())).thenReturn(inputStream);

    FileIo.createDirectoryIfDoesNotExist(TG_WORKING_DIR);

    TerragruntContext terragruntContext =
        taskService.prepareTerragrunt(logCallback, parameters, TG_TEST_BASE_DIR, executionCommandLogCallback);

    assertThat(terragruntContext).isNotNull();
    assertThat(terragruntContext.getWorkingDirectory()).isEqualTo(TG_TEST_BASE_DIR + "/terragrunt-script-repository");
    assertThat(terragruntContext.getVarFilesDirectory()).isEqualTo(TG_TEST_BASE_DIR + "/tf-var-files");
    assertThat(terragruntContext.getTerragruntWorkingDirectory()).isNull();
    assertThat(terragruntContext.getConfigFilesSourceReference()).isEqualTo("test-123-config-source-ref");
    assertThat(terragruntContext.getBackendFile()).isEqualTo("test-be-123.tfVars");
    assertThat(terragruntContext.getBackendFileSourceReference()).isEqualTo("test-123-be-source-ref");
    assertThat(terragruntContext.getVarFiles().get(0)).isEqualTo("test-varFile-123.tfVars");
    assertThat(terragruntContext.getVarFilesSourceReference().get("test-var-file-identifier"))
        .isEqualTo("test-123-varFile-source-ref");
    assertThat(terragruntContext.getClient()).isNotNull();
    verify(delegateFileManager, times(0)).downloadByFileId(any(), any(), any());
    FileIo.deleteDirectoryAndItsContentIfExists(TG_WORKING_DIR);
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testPrepareTerragruntExceptionIsThrown() throws IOException {
    TerragruntRunConfiguration runConfiguration =
        TerragruntRunConfiguration.builder().runType(TerragruntTaskRunType.RUN_ALL).path(TG_RUN_PATH).build();
    AbstractTerragruntTaskParameters parameters = TerragruntTestUtils.createPlanTaskParameters(runConfiguration);

    when(terragruntDownloadService.download(eq(parameters.getConfigFilesStore()), any(), any(), any()))
        .thenThrow(new RuntimeException("wasn't able to fetch files"));

    InputStream inputStream = IOUtils.toInputStream("Some content", "UTF-8");
    when(delegateFileManager.downloadByFileId(any(), any(), any())).thenReturn(inputStream);

    assertThatThrownBy(
        () -> taskService.prepareTerragrunt(logCallback, parameters, TG_TEST_BASE_DIR, executionCommandLogCallback))
        .matches(throwable -> {
          assertThat(throwable).isInstanceOf(TerragruntFetchFilesRuntimeException.class);
          assertThat(throwable.getCause().getMessage()).contains("wasn't able to fetch files");
          return true;
        });
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testUploadStateFile() throws IOException {
    FileIo.createDirectoryIfDoesNotExist(TG_SCRIPT_DIR);
    FileIo.writeFile(TG_SCRIPT_DIR + "terraform.tfstate", new byte[] {});

    taskService.uploadStateFile(
        TG_SCRIPT_DIR, null, ACCOUNT_ID, ENTITY_ID, "test-delegate-id", "test-task-id", logCallback);

    FileIo.deleteDirectoryAndItsContentIfExists(TG_SCRIPT_DIR);

    verify(delegateFileManager, times(1)).upload(any(), any());
    FileIo.deleteDirectoryAndItsContentIfExists(TG_SCRIPT_DIR);
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testCleanDirectoryAndSecrets() throws IOException {
    when(encryptDecryptHelper.deleteEncryptedRecord(any(), any())).thenReturn(true);

    taskService.cleanDirectoryAndSecretFromSecretManager(
        EncryptedRecordData.builder().build(), VaultConfig.builder().build(), TG_TEST_BASE_DIR, logCallback);

    verify(logCallback).saveExecutionLog("Done cleaning up directories.", INFO, CommandExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testSaveTerraformPlanContentToFile() throws IOException {
    byte[] decryptedTerraformPlan = "Tfplan".getBytes(StandardCharsets.UTF_8);

    when(harnessSMEncryptionDecryptionHandlerNG.getDecryptedContent(any(), any(), any()))
        .thenReturn(decryptedTerraformPlan);

    taskService.saveTerraformPlanContentToFile(VaultConfig.builder().build(), EncryptedRecordData.builder().build(),
        "Script", ACCOUNT_ID, TG_TEST_BASE_DIR, true);

    verify(harnessSMEncryptionDecryptionHandlerNG, times(1)).getDecryptedContent(any(), any(), any());
    FileIo.deleteDirectoryAndItsContentIfExists(TG_TEST_BASE_DIR);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testMapGitConfig() throws IOException {
    TerragruntRunConfiguration runConfiguration =
        TerragruntRunConfiguration.builder().runType(TerragruntTaskRunType.RUN_MODULE).path(TG_RUN_PATH).build();
    AbstractTerragruntTaskParameters parameters = TerragruntTestUtils.createPlanTaskParameters(runConfiguration);
    GithubConnectorDTO githubConnectorDTO =
        GithubConnectorDTO.builder()
            .connectionType(GitConnectionType.ACCOUNT)
            .url("http://localhost")
            .authentication(
                GithubAuthenticationDTO.builder()
                    .authType(GitAuthType.HTTP)
                    .credentials(GithubHttpCredentialsDTO.builder()
                                     .type(GithubHttpAuthenticationType.GITHUB_APP)
                                     .httpCredentialsSpec(GithubAppDTO.builder()
                                                              .installationId("id")
                                                              .applicationId("app")
                                                              .privateKeyRef(SecretRefData.builder().build())
                                                              .build())
                                     .build())
                    .build())
            .build();
    doReturn(GitConfigDTO.builder().build()).when(scmConnectorMapperDelegate).toGitConfigDTO(any(), any());

    parameters.setConfigFilesStore(GitStoreDelegateConfig.builder()
                                       .branch("master")
                                       .connectorName("terraform")
                                       .gitConfigDTO(githubConnectorDTO)
                                       .build());
    taskService.mapGitConfig(parameters);
    assertThat(parameters.getConfigFilesStore()).isInstanceOf(GitStoreDelegateConfig.class);
    assertThat(((GitStoreDelegateConfig) parameters.getConfigFilesStore()).getGitConfigDTO())
        .isInstanceOf(GitConfigDTO.class);
  }
}
