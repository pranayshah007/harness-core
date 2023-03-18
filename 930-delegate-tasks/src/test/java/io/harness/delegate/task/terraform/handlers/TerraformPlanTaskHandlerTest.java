/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.terraform.handlers;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.JELENA;
import static io.harness.rule.OwnerRule.ROHITKARELIA;
import static io.harness.rule.OwnerRule.TMACARI;
import static io.harness.rule.OwnerRule.VLICA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cli.CliResponse;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthenticationDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryUsernamePasswordAuthDTO;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitHTTPAuthenticationDTO;
import io.harness.delegate.beans.storeconfig.ArtifactoryStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.task.git.GitFetchFilesConfig;
import io.harness.delegate.task.terraform.RemoteTerraformBackendConfigFileInfo;
import io.harness.delegate.task.terraform.RemoteTerraformVarFileInfo;
import io.harness.delegate.task.terraform.TFTaskType;
import io.harness.delegate.task.terraform.TerraformBaseHelper;
import io.harness.delegate.task.terraform.TerraformCommand;
import io.harness.delegate.task.terraform.TerraformTaskNGParameters;
import io.harness.delegate.task.terraform.TerraformTaskNGParameters.TerraformTaskNGParametersBuilder;
import io.harness.delegate.task.terraform.TerraformTaskNGResponse;
import io.harness.encryption.SecretRefData;
import io.harness.git.GitClientHelper;
import io.harness.git.model.GitBaseRequest;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.terraform.TerraformStepResponse;

import com.google.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@OwnedBy(CDP)
public class TerraformPlanTaskHandlerTest extends CategoryTest {
  @Inject @Spy @InjectMocks TerraformPlanTaskHandler terraformPlanTaskHandler;
  @Mock LogCallback logCallback;
  @Mock SecretDecryptionService secretDecryptionService;
  @Mock TerraformBaseHelper terraformBaseHelper;
  @Mock GitClientHelper gitClientHelper;

  private final EncryptedRecordData encryptedPlanContent =
      EncryptedRecordData.builder().name("planName").encryptedValue("encryptedPlan".toCharArray()).build();
  private static final String gitUsername = "username";
  private static final String gitPasswordRefId = "git_password";

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testPlan() throws IOException, TimeoutException, InterruptedException {
    when(secretDecryptionService.decrypt(any(), any())).thenReturn(null);
    when(terraformBaseHelper.getGitBaseRequestForConfigFile(any(), any(), any()))
        .thenReturn(mock(GitBaseRequest.class));
    when(terraformBaseHelper.fetchConfigFileAndPrepareScriptDir(
             any(), any(), any(), any(), any(), eq(logCallback), any(), any()))
        .thenReturn("sourceDir");
    doNothing().when(terraformBaseHelper).downloadTfStateFile(null, "accountId", null, "scriptDir");
    when(gitClientHelper.getRepoDirectory(any())).thenReturn("sourceDir");
    File outputFile = new File("sourceDir/terraform-output.tfvars");
    FileUtils.touch(outputFile);
    File planFile = new File("sourceDir/tfplan");
    FileUtils.touch(planFile);
    when(terraformBaseHelper.getPlanName(TerraformCommand.APPLY)).thenReturn("tfplan");
    when(terraformBaseHelper.executeTerraformPlanStep(any()))
        .thenReturn(
            TerraformStepResponse.builder()
                .cliResponse(
                    CliResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).exitCode(2).build())
                .build());
    TerraformTaskNGResponse response = terraformPlanTaskHandler.executeTaskInternal(
        getTerraformTaskParameters().build(), "delegateId", "taskId", logCallback);
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(response.getDetailedExitCode()).isEqualTo(2);
    Files.deleteIfExists(Paths.get(outputFile.getPath()));
    Files.deleteIfExists(Paths.get(planFile.getPath()));
    Files.deleteIfExists(Paths.get("sourceDir"));
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testPlanWhenTerraformCloudCli() throws IOException, TimeoutException, InterruptedException {
    when(secretDecryptionService.decrypt(any(), any())).thenReturn(null);
    when(terraformBaseHelper.getGitBaseRequestForConfigFile(any(), any(), any()))
        .thenReturn(mock(GitBaseRequest.class));
    when(terraformBaseHelper.fetchConfigFileAndPrepareScriptDir(
             any(), any(), any(), any(), any(), eq(logCallback), any(), any()))
        .thenReturn("sourceDir");
    doNothing().when(terraformBaseHelper).downloadTfStateFile(null, "accountId", null, "scriptDir");
    when(gitClientHelper.getRepoDirectory(any())).thenReturn("sourceDir");
    File outputFile = new File("sourceDir/terraform-output.tfvars");
    FileUtils.touch(outputFile);
    File planFile = new File("sourceDir/tfplan");
    FileUtils.touch(planFile);
    when(terraformBaseHelper.getPlanName(TerraformCommand.APPLY)).thenReturn("tfplan");
    when(terraformBaseHelper.executeTerraformPlanStep(any()))
        .thenReturn(
            TerraformStepResponse.builder()
                .cliResponse(
                    CliResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).exitCode(2).build())
                .build());
    TerraformTaskNGResponse response = terraformPlanTaskHandler.executeTaskInternal(
        getTerraformTaskParameters().isTerraformCloudCli(true).build(), "delegateId", "taskId", logCallback);
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(response.getDetailedExitCode()).isEqualTo(2);

    Files.deleteIfExists(Paths.get(outputFile.getPath()));
    Files.deleteIfExists(Paths.get(planFile.getPath()));
    Files.deleteIfExists(Paths.get("sourceDir"));
    verify(terraformBaseHelper, times(0)).encryptPlan(any(), any(), any(), any());
    verify(terraformBaseHelper, times(0)).uploadTfPlanJson(any(), any(), any(), any(), any(), any());
    verify(terraformBaseHelper, times(0)).uploadTfPlanHumanReadable(any(), any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testPlanWithArtifactoryConfigAndVarFiles() throws IOException, TimeoutException, InterruptedException {
    when(secretDecryptionService.decrypt(any(), any())).thenReturn(null);
    when(terraformBaseHelper.fetchConfigFileAndPrepareScriptDir(any(), any(), any(), any(), eq(logCallback), any()))
        .thenReturn("sourceDir");
    doNothing().when(terraformBaseHelper).downloadTfStateFile(null, "accountId", null, "scriptDir");
    when(gitClientHelper.getRepoDirectory(any())).thenReturn("sourceDir");
    File outputFile = new File("sourceDir/terraform-output.tfvars");
    FileUtils.touch(outputFile);
    File planFile = new File("sourceDir/tfplan");
    FileUtils.touch(planFile);
    when(terraformBaseHelper.getPlanName(TerraformCommand.APPLY)).thenReturn("tfplan");
    when(terraformBaseHelper.executeTerraformPlanStep(any()))
        .thenReturn(
            TerraformStepResponse.builder()
                .cliResponse(
                    CliResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).exitCode(0).build())
                .build());
    TerraformTaskNGResponse response = terraformPlanTaskHandler.executeTaskInternal(
        getTerraformTaskParametersWithArtifactoryConfig(), "delegateId", "taskId", logCallback);
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(response.getDetailedExitCode()).isEqualTo(0);
    Files.deleteIfExists(Paths.get(outputFile.getPath()));
    Files.deleteIfExists(Paths.get(planFile.getPath()));
    Files.deleteIfExists(Paths.get("sourceDir"));
  }

  @Test
  @Owner(developers = JELENA)
  @Category(UnitTests.class)
  public void testPlanWithArtifactoryConfigAndBackendFiles()
      throws IOException, TimeoutException, InterruptedException {
    when(secretDecryptionService.decrypt(any(), any())).thenReturn(null);
    when(terraformBaseHelper.fetchConfigFileAndPrepareScriptDir(any(), any(), any(), any(), eq(logCallback), any()))
        .thenReturn("sourceDir");
    doNothing().when(terraformBaseHelper).downloadTfStateFile(null, "accountId", null, "scriptDir");
    when(gitClientHelper.getRepoDirectory(any())).thenReturn("sourceDir");
    File outputFile = new File("sourceDir/terraform-output.tfvars");
    FileUtils.touch(outputFile);
    File planFile = new File("sourceDir/tfplan");
    FileUtils.touch(planFile);
    when(terraformBaseHelper.getPlanName(TerraformCommand.APPLY)).thenReturn("tfplan");
    when(terraformBaseHelper.executeTerraformPlanStep(any()))
        .thenReturn(
            TerraformStepResponse.builder()
                .cliResponse(
                    CliResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).exitCode(0).build())
                .build());
    TerraformTaskNGResponse response = terraformPlanTaskHandler.executeTaskInternal(
        getTerraformTaskParametersWithBackendConfig(), "delegateId", "taskId", logCallback);
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(response.getDetailedExitCode()).isEqualTo(0);
    Files.deleteIfExists(Paths.get(outputFile.getPath()));
    Files.deleteIfExists(Paths.get(planFile.getPath()));
    Files.deleteIfExists(Paths.get("sourceDir"));
  }

  private TerraformTaskNGParametersBuilder getTerraformTaskParameters() {
    return TerraformTaskNGParameters.builder()
        .accountId("accountId")
        .taskType(TFTaskType.APPLY)
        .entityId("provisionerIdentifier")
        .encryptedTfPlan(encryptedPlanContent)
        .configFile(
            GitFetchFilesConfig.builder()
                .gitStoreDelegateConfig(
                    GitStoreDelegateConfig.builder()
                        .branch("main")
                        .path("main.tf")
                        .gitConfigDTO(
                            GitConfigDTO.builder()
                                .gitAuthType(GitAuthType.HTTP)
                                .gitAuth(GitHTTPAuthenticationDTO.builder()
                                             .username(gitUsername)
                                             .passwordRef(SecretRefData.builder().identifier(gitPasswordRefId).build())
                                             .build())
                                .build())
                        .build())
                .build())
        .planName("planName")
        .terraformCommand(TerraformCommand.APPLY);
  }

  private TerraformTaskNGParameters getTerraformTaskParametersWithArtifactoryConfig() {
    List<EncryptedDataDetail> encryptedDataDetails = Collections.singletonList(mock(EncryptedDataDetail.class));
    ArtifactoryUsernamePasswordAuthDTO credentials = ArtifactoryUsernamePasswordAuthDTO.builder().build();
    ArtifactoryConnectorDTO artifactoryConnectorDTO =
        ArtifactoryConnectorDTO.builder()
            .auth(ArtifactoryAuthenticationDTO.builder().credentials(credentials).build())
            .build();
    ArtifactoryStoreDelegateConfig artifactoryStoreDelegateConfig =
        ArtifactoryStoreDelegateConfig.builder()
            .artifacts(Arrays.asList("artifactPath"))
            .repositoryName("repoName")
            .encryptedDataDetails(encryptedDataDetails)
            .connectorDTO(ConnectorInfoDTO.builder().connectorConfig(artifactoryConnectorDTO).build())
            .build();
    return TerraformTaskNGParameters.builder()
        .accountId("accountId")
        .taskType(TFTaskType.APPLY)
        .entityId("provisionerIdentifier")
        .encryptedTfPlan(encryptedPlanContent)
        .configFile(null)
        .fileStoreConfigFiles(artifactoryStoreDelegateConfig)
        .varFileInfos(Collections.singletonList(RemoteTerraformVarFileInfo.builder().build()))
        .planName("planName")
        .terraformCommand(TerraformCommand.APPLY)
        .build();
  }

  private TerraformTaskNGParameters getTerraformTaskParametersWithBackendConfig() {
    List<EncryptedDataDetail> encryptedDataDetails = Collections.singletonList(mock(EncryptedDataDetail.class));
    ArtifactoryUsernamePasswordAuthDTO credentials = ArtifactoryUsernamePasswordAuthDTO.builder().build();
    ArtifactoryConnectorDTO artifactoryConnectorDTO =
        ArtifactoryConnectorDTO.builder()
            .auth(ArtifactoryAuthenticationDTO.builder().credentials(credentials).build())
            .build();
    ArtifactoryStoreDelegateConfig artifactoryStoreDelegateConfig =
        ArtifactoryStoreDelegateConfig.builder()
            .artifacts(Arrays.asList("artifactPath"))
            .repositoryName("repoName")
            .encryptedDataDetails(encryptedDataDetails)
            .connectorDTO(ConnectorInfoDTO.builder().connectorConfig(artifactoryConnectorDTO).build())
            .build();
    return TerraformTaskNGParameters.builder()
        .accountId("accountId")
        .taskType(TFTaskType.APPLY)
        .entityId("provisionerIdentifier")
        .encryptedTfPlan(encryptedPlanContent)
        .configFile(null)
        .backendConfigFileInfo(RemoteTerraformBackendConfigFileInfo.builder().build())
        .fileStoreConfigFiles(artifactoryStoreDelegateConfig)
        .varFileInfos(Collections.singletonList(RemoteTerraformVarFileInfo.builder().build()))
        .planName("planName")
        .terraformCommand(TerraformCommand.APPLY)
        .build();
  }
}
