/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.terraform.handlers;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.BUHA;
import static io.harness.rule.OwnerRule.JELENA;
import static io.harness.rule.OwnerRule.ROHITKARELIA;
import static io.harness.rule.OwnerRule.TMACARI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitHTTPAuthenticationDTO;
import io.harness.delegate.beans.storeconfig.ArtifactoryStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.S3StoreTFDelegateConfig;
import io.harness.delegate.task.git.GitFetchFilesConfig;
import io.harness.delegate.task.terraform.RemoteTerraformBackendConfigFileInfo;
import io.harness.delegate.task.terraform.RemoteTerraformVarFileInfo;
import io.harness.delegate.task.terraform.TFTaskType;
import io.harness.delegate.task.terraform.TerraformBaseHelper;
import io.harness.delegate.task.terraform.TerraformCommand;
import io.harness.delegate.task.terraform.TerraformTaskNGParameters;
import io.harness.delegate.task.terraform.TerraformTaskNGResponse;
import io.harness.encryption.SecretRefData;
import io.harness.filesystem.FileIo;
import io.harness.git.GitClientHelper;
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
public class TerraformDestroyTaskHandlerTest extends CategoryTest {
  @Inject @Spy @InjectMocks TerraformDestroyTaskHandler terraformDestroyTaskHandler;
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
    when(terraformBaseHelper.getBaseDir(any())).thenReturn("./some/dir/entityId");
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testDestroy() throws IOException, TimeoutException, InterruptedException {
    when(secretDecryptionService.decrypt(any(), any())).thenReturn(null);
    when(terraformBaseHelper.getGitBaseRequestForConfigFile(any(), any(), any())).thenReturn(any());
    when(terraformBaseHelper.fetchConfigFileAndPrepareScriptDir(
             any(), any(), any(), any(), any(), logCallback, any(), any()))
        .thenReturn("sourceDir");
    doNothing().when(terraformBaseHelper).downloadTfStateFile(null, "accountId", null, "scriptDir");
    when(gitClientHelper.getRepoDirectory(any())).thenReturn("sourceDir");
    File outputFile = new File("sourceDir/terraform-output.tfvars");
    FileUtils.touch(outputFile);
    when(terraformBaseHelper.executeTerraformDestroyStep(any()))
        .thenReturn(
            TerraformStepResponse.builder()
                .cliResponse(CliResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build())
                .build());
    TerraformTaskNGResponse response = terraformDestroyTaskHandler.executeTaskInternal(
        getTerraformTaskParameters(), "delegateId", "taskId", logCallback);
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    Files.deleteIfExists(Paths.get(outputFile.getPath()));
    Files.deleteIfExists(Paths.get("sourceDir"));
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testApplyWithArtifactoryConfigAndVarFiles() throws IOException, TimeoutException, InterruptedException {
    when(secretDecryptionService.decrypt(any(), any())).thenReturn(null);
    when(terraformBaseHelper.fetchConfigFileAndPrepareScriptDir(any(), any(), any(), any(), eq(logCallback), any()))
        .thenReturn("sourceDir");
    doNothing().when(terraformBaseHelper).downloadTfStateFile(null, "accountId", null, "scriptDir");
    FileIo.createDirectoryIfDoesNotExist("sourceDir");
    File outputFile = new File("sourceDir/terraform-output.tfvars");
    FileUtils.touch(outputFile);
    when(terraformBaseHelper.executeTerraformDestroyStep(any()))
        .thenReturn(
            TerraformStepResponse.builder()
                .cliResponse(CliResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build())
                .build());
    TerraformTaskNGResponse response = terraformDestroyTaskHandler.executeTaskInternal(
        getTerraformTaskParametersWithArtifactoryConfig(), "delegateId", "taskId", logCallback);
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    Files.deleteIfExists(Paths.get(outputFile.getPath()));
    Files.deleteIfExists(Paths.get("sourceDir"));
  }

  @Test
  @Owner(developers = JELENA)
  @Category(UnitTests.class)
  public void testDestoryWithArtifactoryConfigAndBackendConfig()
      throws IOException, TimeoutException, InterruptedException {
    when(secretDecryptionService.decrypt(any(), any())).thenReturn(null);
    when(terraformBaseHelper.fetchConfigFileAndPrepareScriptDir(any(), any(), any(), any(), eq(logCallback), any()))
        .thenReturn("sourceDir");
    doNothing().when(terraformBaseHelper).downloadTfStateFile(null, "accountId", null, "scriptDir");
    FileIo.createDirectoryIfDoesNotExist("sourceDir");
    File outputFile = new File("sourceDir/terraform-backend-config");
    FileUtils.touch(outputFile);
    when(terraformBaseHelper.executeTerraformDestroyStep(any()))
        .thenReturn(
            TerraformStepResponse.builder()
                .cliResponse(CliResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build())
                .build());
    TerraformTaskNGResponse response = terraformDestroyTaskHandler.executeTaskInternal(
        getTerraformTaskParametersWithArtifactoryConfig(), "delegateId", "taskId", logCallback);
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    Files.deleteIfExists(Paths.get(outputFile.getPath()));
    Files.deleteIfExists(Paths.get("sourceDir"));
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testApplyWithS3Config() throws IOException, TimeoutException, InterruptedException {
    when(secretDecryptionService.decrypt(any(), any())).thenReturn(null);
    when(terraformBaseHelper.fetchS3ConfigFilesAndPrepareScriptDir(any(), any(), any(), any(), eq(logCallback)))
        .thenReturn("sourceDir");
    doNothing().when(terraformBaseHelper).downloadTfStateFile(null, "accountId", null, "scriptDir");
    FileIo.createDirectoryIfDoesNotExist("sourceDir");
    File outputFile = new File("sourceDir/terraform-output.tfvars");
    FileUtils.touch(outputFile);
    when(terraformBaseHelper.executeTerraformDestroyStep(any()))
        .thenReturn(
            TerraformStepResponse.builder()
                .cliResponse(CliResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build())
                .build());
    TerraformTaskNGResponse response = terraformDestroyTaskHandler.executeTaskInternal(
        getTerraformTaskParametersWithS3Config(), "delegateId", "taskId", logCallback);
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    verify(terraformBaseHelper, times(1)).fetchS3ConfigFilesAndPrepareScriptDir(any(), any(), any(), any(), any());
    Files.deleteIfExists(Paths.get(outputFile.getPath()));
    Files.deleteIfExists(Paths.get("sourceDir"));
  }

  private TerraformTaskNGParameters getTerraformTaskParameters() {
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
        .build();
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
        .taskType(TFTaskType.DESTROY)
        .entityId("provisionerIdentifier")
        .encryptedTfPlan(encryptedPlanContent)
        .configFile(null)
        .backendConfigFileInfo(
            RemoteTerraformBackendConfigFileInfo.builder()
                .gitFetchFilesConfig(
                    GitFetchFilesConfig.builder()
                        .gitStoreDelegateConfig(
                            GitStoreDelegateConfig.builder()
                                .branch("main")
                                .path("remote_state")
                                .gitConfigDTO(
                                    GitConfigDTO.builder()
                                        .gitAuthType(GitAuthType.HTTP)
                                        .gitAuth(GitHTTPAuthenticationDTO.builder()
                                                     .username(gitUsername)
                                                     .passwordRef(
                                                         SecretRefData.builder().identifier(gitPasswordRefId).build())
                                                     .build())
                                        .build())
                                .build())
                        .build())
                .build())
        .fileStoreConfigFiles(artifactoryStoreDelegateConfig)
        .varFileInfos(Collections.singletonList(RemoteTerraformVarFileInfo.builder().build()))
        .planName("planName")
        .build();
  }

  private TerraformTaskNGParameters getTerraformTaskParametersWithS3Config() {
    List<EncryptedDataDetail> encryptedDataDetails = Collections.singletonList(mock(EncryptedDataDetail.class));
    AwsConnectorDTO awsConnectorDTO = AwsConnectorDTO.builder()
                                          .credential(AwsCredentialDTO.builder()
                                                          .awsCredentialType(AwsCredentialType.MANUAL_CREDENTIALS)
                                                          .config(AwsManualConfigSpecDTO.builder().build())
                                                          .build())
                                          .build();
    S3StoreTFDelegateConfig s3StoreTFDelegateConfig =
        S3StoreTFDelegateConfig.builder()
            .region("region")
            .bucketName("bucket")
            .paths(Collections.singletonList("terraform"))
            .encryptedDataDetails(encryptedDataDetails)
            .connectorDTO(ConnectorInfoDTO.builder().connectorConfig(awsConnectorDTO).build())
            .build();
    return TerraformTaskNGParameters.builder()
        .accountId("accountId")
        .taskType(TFTaskType.DESTROY)
        .entityId("provisionerIdentifier")
        .encryptedTfPlan(encryptedPlanContent)
        .configFile(null)
        .fileStoreConfigFiles(s3StoreTFDelegateConfig)
        .varFileInfos(Collections.singletonList(RemoteTerraformVarFileInfo.builder().build()))
        .planName("planName")
        .terraformCommand(TerraformCommand.APPLY)
        .build();
  }
}
