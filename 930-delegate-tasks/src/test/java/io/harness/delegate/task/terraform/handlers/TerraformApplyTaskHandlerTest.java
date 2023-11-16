/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.terraform.handlers;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.BUHA;
import static io.harness.rule.OwnerRule.ROHITKARELIA;
import static io.harness.rule.OwnerRule.TMACARI;
import static io.harness.rule.OwnerRule.VLICA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cli.CliResponse;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.service.git.NGGitService;
import io.harness.connector.task.git.ScmConnectorMapperDelegate;
import io.harness.connector.task.shell.SshSessionConfigMapper;
import io.harness.delegate.beans.DelegateFileManagerBase;
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
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.storeconfig.ArtifactoryStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.S3StoreTFDelegateConfig;
import io.harness.delegate.task.git.GitFetchFilesConfig;
import io.harness.delegate.task.terraform.RemoteTerraformVarFileInfo;
import io.harness.delegate.task.terraform.TFTaskType;
import io.harness.delegate.task.terraform.TerraformBaseHelper;
import io.harness.delegate.task.terraform.TerraformCommand;
import io.harness.delegate.task.terraform.TerraformTaskNGParameters;
import io.harness.delegate.task.terraform.TerraformTaskNGParameters.TerraformTaskNGParametersBuilder;
import io.harness.delegate.task.terraform.TerraformTaskNGResponse;
import io.harness.encryption.SecretRefData;
import io.harness.filesystem.FileIo;
import io.harness.git.GitClientHelper;
import io.harness.git.GitClientV2;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@OwnedBy(CDP)
public class TerraformApplyTaskHandlerTest extends CategoryTest {
  @Inject @Spy @InjectMocks TerraformApplyTaskHandler terraformApplyTaskHandler;
  @Mock private ILogStreamingTaskClient logStreamingTaskClient;
  @Mock LogCallback logCallback;
  @Mock SecretDecryptionService secretDecryptionService;
  @Mock TerraformBaseHelper terraformBaseHelper;
  @Mock GitClientV2 gitClient;
  @Mock GitClientHelper gitClientHelper;
  @Mock DelegateFileManagerBase delegateFileManager;
  @Mock private SshSessionConfigMapper sshSessionConfigMapper;
  @Mock private NGGitService ngGitService;
  @Mock private ScmConnectorMapperDelegate scmConnectorMapperDelegate;

  private final EncryptedRecordData encryptedPlanContent =
      EncryptedRecordData.builder().name("planName").encryptedValue("encryptedPlan".toCharArray()).build();
  private static final String gitUsername = "username";
  private static final String gitPasswordRefId = "git_password";

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    doReturn(GitConfigDTO.builder().build()).when(scmConnectorMapperDelegate).toGitConfigDTO(any(), any());
    when(terraformBaseHelper.getBaseDir(any())).thenReturn("./some/dir/entityId");
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testApply() throws IOException, TimeoutException, InterruptedException {
    when(secretDecryptionService.decrypt(any(), any())).thenReturn(null);
    when(terraformBaseHelper.getGitBaseRequestForConfigFile(any(), any(), any())).thenReturn(any());
    when(terraformBaseHelper.fetchConfigFileAndPrepareScriptDir(
             any(), any(), any(), any(), logCallback, any(), any(), anyBoolean()))
        .thenReturn("sourceDir");
    doNothing().when(terraformBaseHelper).downloadTfStateFile(null, "accountId", null, "scriptDir");
    when(gitClientHelper.getRepoDirectory(any())).thenReturn("sourceDir");
    FileIo.createDirectoryIfDoesNotExist("sourceDir");
    File outputFile = new File("sourceDir/terraform-output.tfvars");
    FileUtils.touch(outputFile);

    when(terraformBaseHelper.executeTerraformApplyStep(any()))
        .thenReturn(
            TerraformStepResponse.builder()
                .cliResponse(CliResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build())
                .build());
    TerraformTaskNGResponse response = terraformApplyTaskHandler.executeTaskInternal(
        getTerraformTaskParameters(), "delegateId", "taskId", logCallback, new AtomicBoolean());
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    verify(terraformBaseHelper)
        .fetchConfigFileAndPrepareScriptDir(any(), any(), any(), any(), any(), any(), any(), eq(false));
    verify(terraformBaseHelper, times(1)).uploadTfStateFile(any(), any(), any(), any(), any());
    Files.deleteIfExists(Paths.get(outputFile.getPath()));
    Files.deleteIfExists(Paths.get("sourceDir"));
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testApplyAborted() throws IOException, TimeoutException, InterruptedException {
    AtomicBoolean isAborted = new AtomicBoolean();
    when(secretDecryptionService.decrypt(any(), any())).thenReturn(null);
    when(terraformBaseHelper.getGitBaseRequestForConfigFile(any(), any(), any())).thenReturn(any());
    when(terraformBaseHelper.fetchConfigFileAndPrepareScriptDir(
             any(), any(), any(), any(), logCallback, any(), any(), anyBoolean()))
        .thenReturn("sourceDir");
    doNothing().when(terraformBaseHelper).downloadTfStateFile(null, "accountId", null, "scriptDir");
    when(gitClientHelper.getRepoDirectory(any())).thenReturn("sourceDir");
    FileIo.createDirectoryIfDoesNotExist("sourceDir");
    File outputFile = new File("sourceDir/terraform-output.tfvars");
    FileUtils.touch(outputFile);

    doAnswer(invocationOnMock -> {
      isAborted.set(true);
      return new ArrayList<>();
    })
        .when(terraformBaseHelper)
        .checkoutRemoteVarFileAndConvertToVarFilePaths(any(), any(), any(), any(), any(), any(), anyBoolean(), any());

    TerraformTaskNGParameters tfTaskParams = getTerraformTaskParametersBuilder().encryptedTfPlan(null).build();

    assertThatThrownBy(()
                           -> terraformApplyTaskHandler.executeTaskInternal(
                               tfTaskParams, "delegateId", "taskId", logCallback, isAborted))
        .isInstanceOf(InterruptedException.class);

    verify(terraformBaseHelper)
        .fetchConfigFileAndPrepareScriptDir(any(), any(), any(), any(), any(), any(), any(), eq(false));
    Files.deleteIfExists(Paths.get(outputFile.getPath()));
    Files.deleteIfExists(Paths.get("sourceDir"));
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testApplySkipStateStorage() throws IOException, TimeoutException, InterruptedException {
    when(secretDecryptionService.decrypt(any(), any())).thenReturn(null);
    when(terraformBaseHelper.getGitBaseRequestForConfigFile(any(), any(), any())).thenReturn(any());
    when(terraformBaseHelper.fetchConfigFileAndPrepareScriptDir(
             any(), any(), any(), any(), logCallback, any(), any(), anyBoolean()))
        .thenReturn("sourceDir");
    doNothing().when(terraformBaseHelper).downloadTfStateFile(null, "accountId", null, "scriptDir");
    when(gitClientHelper.getRepoDirectory(any())).thenReturn("sourceDir");
    FileIo.createDirectoryIfDoesNotExist("sourceDir");
    File outputFile = new File("sourceDir/terraform-output.tfvars");
    FileUtils.touch(outputFile);

    when(terraformBaseHelper.executeTerraformApplyStep(any()))
        .thenReturn(
            TerraformStepResponse.builder()
                .cliResponse(CliResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build())
                .build());
    TerraformTaskNGParameters taskNGParameters = getTerraformTaskParametersBuilder().skipStateStorage(true).build();

    TerraformTaskNGResponse response = terraformApplyTaskHandler.executeTaskInternal(
        taskNGParameters, "delegateId", "taskId", logCallback, new AtomicBoolean());
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    Files.deleteIfExists(Paths.get(outputFile.getPath()));
    Files.deleteIfExists(Paths.get("sourceDir"));
    verify(terraformBaseHelper)
        .fetchConfigFileAndPrepareScriptDir(any(), any(), any(), any(), any(), any(), any(), eq(true));
    verify(terraformBaseHelper, times(0)).uploadTfStateFile(any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testApplyWithArtifactoryConfigAndVarFiles() throws IOException, TimeoutException, InterruptedException {
    when(secretDecryptionService.decrypt(any(), any())).thenReturn(null);
    when(terraformBaseHelper.fetchConfigFileAndPrepareScriptDir(
             any(), any(), any(), any(), eq(logCallback), any(), anyBoolean()))
        .thenReturn("sourceDir");
    doNothing().when(terraformBaseHelper).downloadTfStateFile(null, "accountId", null, "scriptDir");
    FileIo.createDirectoryIfDoesNotExist("sourceDir");
    File outputFile = new File("sourceDir/terraform-output.tfvars");
    FileUtils.touch(outputFile);
    when(terraformBaseHelper.executeTerraformApplyStep(any()))
        .thenReturn(
            TerraformStepResponse.builder()
                .cliResponse(CliResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build())
                .build());
    TerraformTaskNGResponse response = terraformApplyTaskHandler.executeTaskInternal(
        getTerraformTaskParametersWithArtifactoryConfig(), "delegateId", "taskId", logCallback, new AtomicBoolean());
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    Files.deleteIfExists(Paths.get(outputFile.getPath()));
    Files.deleteIfExists(Paths.get("sourceDir"));
    verify(terraformBaseHelper).fetchConfigFileAndPrepareScriptDir(any(), any(), any(), any(), any(), any(), eq(false));
    verify(terraformBaseHelper, times(1)).uploadTfStateFile(any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testApplyAndSkipFetchVarFilesIfItInheritsFromPlan()
      throws IOException, TimeoutException, InterruptedException {
    when(secretDecryptionService.decrypt(any(), any())).thenReturn(null);
    when(terraformBaseHelper.getGitBaseRequestForConfigFile(any(), any(), any())).thenReturn(any());
    when(terraformBaseHelper.fetchConfigFileAndPrepareScriptDir(
             any(), any(), any(), any(), logCallback, any(), any(), anyBoolean()))
        .thenReturn("sourceDir");
    doNothing().when(terraformBaseHelper).downloadTfStateFile(null, "accountId", null, "scriptDir");
    when(gitClientHelper.getRepoDirectory(any())).thenReturn("sourceDir");
    FileIo.createDirectoryIfDoesNotExist("sourceDir");
    File outputFile = new File("sourceDir/terraform-output.tfvars");
    FileUtils.touch(outputFile);

    when(terraformBaseHelper.executeTerraformApplyStep(any()))
        .thenReturn(
            TerraformStepResponse.builder()
                .cliResponse(CliResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build())
                .build());

    TerraformTaskNGResponse response = terraformApplyTaskHandler.executeTaskInternal(
        getTerraformTaskParameters(), "delegateId", "taskId", logCallback, new AtomicBoolean());
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    verify(terraformBaseHelper)
        .fetchConfigFileAndPrepareScriptDir(any(), any(), any(), any(), any(), any(), any(), eq(false));
    verify(terraformBaseHelper, times(1)).uploadTfStateFile(any(), any(), any(), any(), any());
    verify(terraformBaseHelper, times(0))
        .checkoutRemoteVarFileAndConvertToVarFilePaths(any(), any(), any(), any(), any(), any(), anyBoolean(), any());
    Files.deleteIfExists(Paths.get(outputFile.getPath()));
    Files.deleteIfExists(Paths.get("sourceDir"));
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testApplyAndFetchVarFilesWhenApplyDoNotInherit()
      throws IOException, TimeoutException, InterruptedException {
    when(secretDecryptionService.decrypt(any(), any())).thenReturn(null);
    when(terraformBaseHelper.getGitBaseRequestForConfigFile(any(), any(), any())).thenReturn(any());
    when(terraformBaseHelper.fetchConfigFileAndPrepareScriptDir(
             any(), any(), any(), any(), logCallback, any(), any(), anyBoolean()))
        .thenReturn("sourceDir");
    doNothing().when(terraformBaseHelper).downloadTfStateFile(null, "accountId", null, "scriptDir");
    when(gitClientHelper.getRepoDirectory(any())).thenReturn("sourceDir");
    FileIo.createDirectoryIfDoesNotExist("sourceDir");
    File outputFile = new File("sourceDir/terraform-output.tfvars");
    FileUtils.touch(outputFile);

    when(terraformBaseHelper.executeTerraformApplyStep(any()))
        .thenReturn(
            TerraformStepResponse.builder()
                .cliResponse(CliResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build())
                .build());

    TerraformTaskNGParameters tfTaskParams = getTerraformTaskParametersBuilder().encryptedTfPlan(null).build();

    TerraformTaskNGResponse response = terraformApplyTaskHandler.executeTaskInternal(
        tfTaskParams, "delegateId", "taskId", logCallback, new AtomicBoolean());
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    verify(terraformBaseHelper)
        .fetchConfigFileAndPrepareScriptDir(any(), any(), any(), any(), any(), any(), any(), eq(false));
    verify(terraformBaseHelper, times(1)).uploadTfStateFile(any(), any(), any(), any(), any());
    verify(terraformBaseHelper, times(1))
        .checkoutRemoteVarFileAndConvertToVarFilePaths(any(), any(), any(), any(), any(), any(), anyBoolean(), any());
    Files.deleteIfExists(Paths.get(outputFile.getPath()));
    Files.deleteIfExists(Paths.get("sourceDir"));
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testApplyWithS3ConfigFiles() throws IOException, TimeoutException, InterruptedException {
    when(secretDecryptionService.decrypt(any(), any())).thenReturn(null);
    when(terraformBaseHelper.fetchS3ConfigFilesAndPrepareScriptDir(
             any(), any(), any(), any(), eq(logCallback), anyBoolean()))
        .thenReturn("sourceDir");
    doNothing().when(terraformBaseHelper).downloadTfStateFile(null, "accountId", null, "scriptDir");
    FileIo.createDirectoryIfDoesNotExist("sourceDir");
    File outputFile = new File("sourceDir/terraform-output.tfvars");
    FileUtils.touch(outputFile);
    when(terraformBaseHelper.executeTerraformApplyStep(any()))
        .thenReturn(
            TerraformStepResponse.builder()
                .cliResponse(CliResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build())
                .build());
    TerraformTaskNGResponse response = terraformApplyTaskHandler.executeTaskInternal(
        getTerraformTaskParametersWithS3Config(), "delegateId", "taskId", logCallback, new AtomicBoolean());
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    verify(terraformBaseHelper, times(1))
        .fetchS3ConfigFilesAndPrepareScriptDir(any(), any(), any(), any(), any(), eq(false));
    verify(terraformBaseHelper, times(1)).uploadTfStateFile(any(), any(), any(), any(), any());
    Files.deleteIfExists(Paths.get(outputFile.getPath()));
    Files.deleteIfExists(Paths.get("sourceDir"));
  }

  private TerraformTaskNGParameters getTerraformTaskParameters() {
    return getTerraformTaskParametersBuilder().build();
  }
  private TerraformTaskNGParametersBuilder getTerraformTaskParametersBuilder() {
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
        .planName("planName");
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
        .taskType(TFTaskType.APPLY)
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
