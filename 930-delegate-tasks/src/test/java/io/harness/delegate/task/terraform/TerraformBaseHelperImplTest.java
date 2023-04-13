/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.terraform;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.task.terraform.TerraformBaseHelperImpl.SSH_KEY_FILENAME;
import static io.harness.logging.LogLevel.WARN;
import static io.harness.provision.TerraformConstants.TERRAFORM_PLAN_FILE_OUTPUT_NAME;
import static io.harness.provision.TerraformConstants.TERRAFORM_PLAN_JSON_FILE_NAME;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.BUHA;
import static io.harness.rule.OwnerRule.JELENA;
import static io.harness.rule.OwnerRule.NAMAN_TALAYCHA;
import static io.harness.rule.OwnerRule.ROHITKARELIA;
import static io.harness.rule.OwnerRule.TMACARI;
import static io.harness.rule.OwnerRule.VAIBHAV_SI;
import static io.harness.rule.OwnerRule.VLICA;

import static software.wings.beans.LogColor.Yellow;
import static software.wings.beans.LogHelper.color;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifactory.ArtifactoryConfigRequest;
import io.harness.artifactory.ArtifactoryNgService;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.beans.FileData;
import io.harness.category.element.UnitTests;
import io.harness.cli.CliResponse;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.service.git.NGGitService;
import io.harness.connector.task.shell.SshSessionConfigMapper;
import io.harness.delegate.beans.DelegateFile;
import io.harness.delegate.beans.DelegateFileManagerBase;
import io.harness.delegate.beans.FileBucket;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthenticationDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryUsernamePasswordAuthDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.storeconfig.ArtifactoryStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.S3StoreTFDelegateConfig;
import io.harness.delegate.task.artifactory.ArtifactoryRequestMapper;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.delegate.task.git.GitFetchFilesConfig;
import io.harness.filesystem.FileIo;
import io.harness.git.GitClientHelper;
import io.harness.git.GitClientV2;
import io.harness.git.model.GitBaseRequest;
import io.harness.logging.LogCallback;
import io.harness.logging.PlanJsonLogOutputStream;
import io.harness.logging.PlanLogOutputStream;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.EncryptDecryptHelper;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.security.encryption.EncryptionConfig;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.shell.SshSessionConfig;
import io.harness.terraform.TerraformClientImpl;
import io.harness.terraform.request.TerraformApplyCommandRequest;
import io.harness.terraform.request.TerraformDestroyCommandRequest;
import io.harness.terraform.request.TerraformExecuteStepRequest;
import io.harness.terraform.request.TerraformExecuteStepRequest.TerraformExecuteStepRequestBuilder;
import io.harness.terraform.request.TerraformInitCommandRequest;
import io.harness.terraform.request.TerraformPlanCommandRequest;
import io.harness.terraform.request.TerraformRefreshCommandRequest;

import software.wings.service.impl.AwsApiHelperService;

import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.google.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class TerraformBaseHelperImplTest extends CategoryTest {
  @InjectMocks @Inject TerraformBaseHelperImpl terraformBaseHelper;
  @Mock private LogCallback logCallback;
  @Mock private PlanJsonLogOutputStream planJsonLogOutputStream;
  @Mock private PlanLogOutputStream planLogOutputStream;
  @Mock private TerraformClientImpl terraformClient;
  private TerraformBaseHelperImpl spyTerraformBaseHelper;
  @Mock EncryptionConfig encryptionConfig;
  @Mock GitClientHelper gitClientHelper;
  @Mock SshSessionConfigMapper sshSessionConfigMapper;
  @Mock NGGitService ngGitService;
  @Mock SecretDecryptionService secretDecryptionService;
  @Mock GitClientV2 gitClient;
  @Mock private EncryptDecryptHelper encryptDecryptHelper;
  @Mock private DelegateFileManagerBase delegateFileManagerBase;
  @Mock ArtifactoryNgService artifactoryNgService;
  @Mock ArtifactoryRequestMapper artifactoryRequestMapper;
  @Mock AwsApiHelperService awsApiHelperService;
  @Mock AwsNgConfigMapper awsNgConfigMapper;
  @Mock ListObjectsV2Result listObjectsV2Result;
  @Mock S3Object s3Object;
  @Mock ObjectMetadata objectMetadata;

  private File tfBackendConfig;
  private final EncryptedRecordData encryptedPlanContent =
      EncryptedRecordData.builder().name("planName").encryptedValue("encryptedPlan".toCharArray()).build();

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    spyTerraformBaseHelper = spy(terraformBaseHelper);
    tfBackendConfig = createBackendConfigFile("a1 = b1\na2 = b2\na3 = b3", "backendConfigFile.txt");
  }

  @After
  public void tearDown() throws Exception {
    FileIo.deleteFileIfExists(tfBackendConfig.getAbsolutePath());
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testParseOutput() {
    String workspaceCommandOutput = "* w1\n w2\n w3";
    assertThat(Arrays.asList("w1", "w2", "w3").equals(terraformBaseHelper.parseOutput(workspaceCommandOutput)))
        .isTrue();
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testexecuteTerraformApplyStep() throws InterruptedException, TimeoutException, IOException {
    TerraformExecuteStepRequest terraformExecuteStepRequest = getTerraformExecuteStepRequest().build();

    doReturn(Arrays.asList("w1")).when(spyTerraformBaseHelper).parseOutput("* w1\n");

    when(terraformClient.getWorkspaceList(terraformExecuteStepRequest.getTimeoutInMillis(),
             terraformExecuteStepRequest.getEnvVars(), terraformExecuteStepRequest.getScriptDirectory(),
             terraformExecuteStepRequest.getLogCallback()))
        .thenReturn(CliResponse.builder().output("workspace").build());

    spyTerraformBaseHelper.executeTerraformApplyStep(terraformExecuteStepRequest);

    Mockito.verify(terraformClient, times(1))
        .init(TerraformInitCommandRequest.builder()
                  .tfBackendConfigsFilePath(terraformExecuteStepRequest.getTfBackendConfigsFile())
                  .build(),
            terraformExecuteStepRequest.getTimeoutInMillis(), terraformExecuteStepRequest.getEnvVars(),
            terraformExecuteStepRequest.getScriptDirectory(), terraformExecuteStepRequest.getLogCallback());
    Mockito.verify(terraformClient, times(1))
        .getWorkspaceList(terraformExecuteStepRequest.getTimeoutInMillis(), terraformExecuteStepRequest.getEnvVars(),
            terraformExecuteStepRequest.getScriptDirectory(), terraformExecuteStepRequest.getLogCallback());
    Mockito.verify(terraformClient, times(1))
        .workspace(terraformExecuteStepRequest.getWorkspace(), true, terraformExecuteStepRequest.getTimeoutInMillis(),
            terraformExecuteStepRequest.getEnvVars(), terraformExecuteStepRequest.getScriptDirectory(),
            terraformExecuteStepRequest.getLogCallback(), terraformExecuteStepRequest.getAdditionalCliFlags());
    Mockito.verify(terraformClient, times(1))
        .apply(TerraformApplyCommandRequest.builder().planName("tfplan").build(),
            terraformExecuteStepRequest.getTimeoutInMillis(), terraformExecuteStepRequest.getEnvVars(),
            terraformExecuteStepRequest.getScriptDirectory(), terraformExecuteStepRequest.getLogCallback());
    Mockito.verify(terraformClient, times(1))
        .plan(TerraformPlanCommandRequest.builder().build(), terraformExecuteStepRequest.getTimeoutInMillis(),
            terraformExecuteStepRequest.getEnvVars(), terraformExecuteStepRequest.getScriptDirectory(),
            terraformExecuteStepRequest.getLogCallback());
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testexecuteTerraformApplyStepAndSkipRefresh() throws InterruptedException, TimeoutException, IOException {
    TerraformExecuteStepRequest terraformExecuteStepRequest =
        getTerraformExecuteStepRequest().skipTerraformRefresh(true).build();

    doReturn(Arrays.asList("w1")).when(spyTerraformBaseHelper).parseOutput("* w1\n");

    when(terraformClient.getWorkspaceList(terraformExecuteStepRequest.getTimeoutInMillis(),
             terraformExecuteStepRequest.getEnvVars(), terraformExecuteStepRequest.getScriptDirectory(),
             terraformExecuteStepRequest.getLogCallback()))
        .thenReturn(CliResponse.builder().output("workspace").build());

    spyTerraformBaseHelper.executeTerraformApplyStep(terraformExecuteStepRequest);

    Mockito.verify(terraformClient, times(1))
        .init(TerraformInitCommandRequest.builder()
                  .tfBackendConfigsFilePath(terraformExecuteStepRequest.getTfBackendConfigsFile())
                  .build(),
            terraformExecuteStepRequest.getTimeoutInMillis(), terraformExecuteStepRequest.getEnvVars(),
            terraformExecuteStepRequest.getScriptDirectory(), terraformExecuteStepRequest.getLogCallback());
    Mockito.verify(terraformClient, times(1))
        .getWorkspaceList(terraformExecuteStepRequest.getTimeoutInMillis(), terraformExecuteStepRequest.getEnvVars(),
            terraformExecuteStepRequest.getScriptDirectory(), terraformExecuteStepRequest.getLogCallback());
    Mockito.verify(terraformClient, times(1))
        .workspace(terraformExecuteStepRequest.getWorkspace(), true, terraformExecuteStepRequest.getTimeoutInMillis(),
            terraformExecuteStepRequest.getEnvVars(), terraformExecuteStepRequest.getScriptDirectory(),
            terraformExecuteStepRequest.getLogCallback(), terraformExecuteStepRequest.getAdditionalCliFlags());

    Mockito.verify(terraformClient, times(0)).refresh(any(), anyLong(), any(), anyString(), any());

    Mockito.verify(terraformClient, times(1))
        .plan(TerraformPlanCommandRequest.builder().build(), terraformExecuteStepRequest.getTimeoutInMillis(),
            terraformExecuteStepRequest.getEnvVars(), terraformExecuteStepRequest.getScriptDirectory(),
            terraformExecuteStepRequest.getLogCallback());

    Mockito.verify(terraformClient, times(1))
        .apply(TerraformApplyCommandRequest.builder().planName("tfplan").build(),
            terraformExecuteStepRequest.getTimeoutInMillis(), terraformExecuteStepRequest.getEnvVars(),
            terraformExecuteStepRequest.getScriptDirectory(), terraformExecuteStepRequest.getLogCallback());
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testexecuteTerraformApplyStepWhenTfCloudCli() throws InterruptedException, TimeoutException, IOException {
    TerraformExecuteStepRequest terraformExecuteStepRequest =
        getTerraformExecuteStepRequest().isTerraformCloudCli(true).build();

    doReturn(Arrays.asList("w1")).when(spyTerraformBaseHelper).parseOutput("* w1\n");

    spyTerraformBaseHelper.executeTerraformApplyStep(terraformExecuteStepRequest);

    Mockito.verify(terraformClient, times(1)).init(any(), anyLong(), any(), anyString(), any());
    Mockito.verify(terraformClient, times(0)).getWorkspaceList(anyLong(), any(), anyString(), any());
    Mockito.verify(terraformClient, times(0))
        .workspace(anyString(), anyBoolean(), anyLong(), any(), anyString(), any(), anyMap());
    Mockito.verify(terraformClient, times(0)).refresh(any(), anyLong(), any(), anyString(), any());
    Mockito.verify(terraformClient, times(0)).plan(any(), anyLong(), any(), anyString(), any());
    Mockito.verify(terraformClient, times(1)).apply(any(), anyLong(), any(), anyString(), any());
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testexecuteTerraformPlanStep() throws InterruptedException, TimeoutException, IOException {
    TerraformExecuteStepRequest terraformExecuteStepRequest = getTerraformExecuteStepRequest().build();

    doReturn(Arrays.asList("w1")).when(spyTerraformBaseHelper).parseOutput("* w1\n");

    when(terraformClient.getWorkspaceList(terraformExecuteStepRequest.getTimeoutInMillis(),
             terraformExecuteStepRequest.getEnvVars(), terraformExecuteStepRequest.getScriptDirectory(),
             terraformExecuteStepRequest.getLogCallback()))
        .thenReturn(CliResponse.builder().output("workspace").build());

    terraformBaseHelper.executeTerraformPlanStep(terraformExecuteStepRequest);

    Mockito.verify(terraformClient, times(1))
        .init(TerraformInitCommandRequest.builder()
                  .tfBackendConfigsFilePath(terraformExecuteStepRequest.getTfBackendConfigsFile())
                  .build(),
            terraformExecuteStepRequest.getTimeoutInMillis(), terraformExecuteStepRequest.getEnvVars(),
            terraformExecuteStepRequest.getScriptDirectory(), terraformExecuteStepRequest.getLogCallback());

    Mockito.verify(terraformClient, times(1))
        .getWorkspaceList(terraformExecuteStepRequest.getTimeoutInMillis(), terraformExecuteStepRequest.getEnvVars(),
            terraformExecuteStepRequest.getScriptDirectory(), terraformExecuteStepRequest.getLogCallback());

    Mockito.verify(terraformClient, times(1))
        .workspace(terraformExecuteStepRequest.getWorkspace(), true, terraformExecuteStepRequest.getTimeoutInMillis(),
            terraformExecuteStepRequest.getEnvVars(), terraformExecuteStepRequest.getScriptDirectory(),
            terraformExecuteStepRequest.getLogCallback(), terraformExecuteStepRequest.getAdditionalCliFlags());

    Mockito.verify(terraformClient, times(1))
        .refresh(TerraformRefreshCommandRequest.builder().build(), terraformExecuteStepRequest.getTimeoutInMillis(),
            terraformExecuteStepRequest.getEnvVars(), terraformExecuteStepRequest.getScriptDirectory(),
            terraformExecuteStepRequest.getLogCallback());

    Mockito.verify(terraformClient, times(1))
        .plan(TerraformPlanCommandRequest.builder().build(), terraformExecuteStepRequest.getTimeoutInMillis(),
            terraformExecuteStepRequest.getEnvVars(), terraformExecuteStepRequest.getScriptDirectory(),
            terraformExecuteStepRequest.getLogCallback());
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testexecuteTerraformPlanStepAndSkipRefresh() throws InterruptedException, TimeoutException, IOException {
    TerraformExecuteStepRequest terraformExecuteStepRequest =
        getTerraformExecuteStepRequest().skipTerraformRefresh(true).build();

    doReturn(Arrays.asList("w1")).when(spyTerraformBaseHelper).parseOutput("* w1\n");

    when(terraformClient.getWorkspaceList(terraformExecuteStepRequest.getTimeoutInMillis(),
             terraformExecuteStepRequest.getEnvVars(), terraformExecuteStepRequest.getScriptDirectory(),
             terraformExecuteStepRequest.getLogCallback()))
        .thenReturn(CliResponse.builder().output("workspace").build());

    terraformBaseHelper.executeTerraformPlanStep(terraformExecuteStepRequest);

    Mockito.verify(terraformClient, times(1))
        .init(TerraformInitCommandRequest.builder()
                  .tfBackendConfigsFilePath(terraformExecuteStepRequest.getTfBackendConfigsFile())
                  .build(),
            terraformExecuteStepRequest.getTimeoutInMillis(), terraformExecuteStepRequest.getEnvVars(),
            terraformExecuteStepRequest.getScriptDirectory(), terraformExecuteStepRequest.getLogCallback());

    Mockito.verify(terraformClient, times(1))
        .getWorkspaceList(terraformExecuteStepRequest.getTimeoutInMillis(), terraformExecuteStepRequest.getEnvVars(),
            terraformExecuteStepRequest.getScriptDirectory(), terraformExecuteStepRequest.getLogCallback());

    Mockito.verify(terraformClient, times(1))
        .workspace(terraformExecuteStepRequest.getWorkspace(), true, terraformExecuteStepRequest.getTimeoutInMillis(),
            terraformExecuteStepRequest.getEnvVars(), terraformExecuteStepRequest.getScriptDirectory(),
            terraformExecuteStepRequest.getLogCallback(), terraformExecuteStepRequest.getAdditionalCliFlags());

    Mockito.verify(terraformClient, times(0)).refresh(any(), anyLong(), any(), anyString(), any());

    Mockito.verify(terraformClient, times(1))
        .plan(TerraformPlanCommandRequest.builder().build(), terraformExecuteStepRequest.getTimeoutInMillis(),
            terraformExecuteStepRequest.getEnvVars(), terraformExecuteStepRequest.getScriptDirectory(),
            terraformExecuteStepRequest.getLogCallback());
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testexecuteTerraformPlanStepAndTfCloudCli() throws InterruptedException, TimeoutException, IOException {
    TerraformExecuteStepRequest terraformExecuteStepRequest =
        getTerraformExecuteStepRequest().isTerraformCloudCli(true).build();

    doReturn(Arrays.asList("w1")).when(spyTerraformBaseHelper).parseOutput("* w1\n");

    terraformBaseHelper.executeTerraformPlanStep(terraformExecuteStepRequest);

    Mockito.verify(terraformClient, times(1))
        .init(TerraformInitCommandRequest.builder()
                  .tfBackendConfigsFilePath(terraformExecuteStepRequest.getTfBackendConfigsFile())
                  .build(),
            terraformExecuteStepRequest.getTimeoutInMillis(), terraformExecuteStepRequest.getEnvVars(),
            terraformExecuteStepRequest.getScriptDirectory(), terraformExecuteStepRequest.getLogCallback());

    Mockito.verify(terraformClient, times(0)).getWorkspaceList(anyLong(), any(), anyString(), any());

    Mockito.verify(terraformClient, times(0)).refresh(any(), anyLong(), any(), anyString(), any());

    Mockito.verify(terraformClient, times(1))
        .plan(any(), eq(terraformExecuteStepRequest.getTimeoutInMillis()), eq(terraformExecuteStepRequest.getEnvVars()),
            eq(terraformExecuteStepRequest.getScriptDirectory()), eq(terraformExecuteStepRequest.getLogCallback()));
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testexecuteTerraformDestroyStep() throws InterruptedException, TimeoutException, IOException {
    TerraformExecuteStepRequest terraformExecuteStepRequest = getTerraformExecuteStepRequest().build();
    doReturn(Arrays.asList("w1")).when(spyTerraformBaseHelper).parseOutput("* w1\n");

    when(terraformClient.getWorkspaceList(terraformExecuteStepRequest.getTimeoutInMillis(),
             terraformExecuteStepRequest.getEnvVars(), terraformExecuteStepRequest.getScriptDirectory(),
             terraformExecuteStepRequest.getLogCallback()))
        .thenReturn(CliResponse.builder().output("workspace").build());

    terraformBaseHelper.executeTerraformDestroyStep(terraformExecuteStepRequest);

    Mockito.verify(terraformClient, times(1))
        .init(TerraformInitCommandRequest.builder()
                  .tfBackendConfigsFilePath(terraformExecuteStepRequest.getTfBackendConfigsFile())
                  .build(),
            terraformExecuteStepRequest.getTimeoutInMillis(), terraformExecuteStepRequest.getEnvVars(),
            terraformExecuteStepRequest.getScriptDirectory(), terraformExecuteStepRequest.getLogCallback());
    Mockito.verify(terraformClient, times(1))
        .getWorkspaceList(terraformExecuteStepRequest.getTimeoutInMillis(), terraformExecuteStepRequest.getEnvVars(),
            terraformExecuteStepRequest.getScriptDirectory(), terraformExecuteStepRequest.getLogCallback());
    Mockito.verify(terraformClient, times(1))
        .workspace(terraformExecuteStepRequest.getWorkspace(), true, terraformExecuteStepRequest.getTimeoutInMillis(),
            terraformExecuteStepRequest.getEnvVars(), terraformExecuteStepRequest.getScriptDirectory(),
            terraformExecuteStepRequest.getLogCallback(), terraformExecuteStepRequest.getAdditionalCliFlags());
    Mockito.verify(terraformClient, times(1))
        .destroy(TerraformDestroyCommandRequest.builder().targets(terraformExecuteStepRequest.getTargets()).build(),
            terraformExecuteStepRequest.getTimeoutInMillis(), terraformExecuteStepRequest.getEnvVars(),
            terraformExecuteStepRequest.getScriptDirectory(), terraformExecuteStepRequest.getLogCallback());
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testexecuteTerraformDestroyStepAndSkipRefresh()
      throws InterruptedException, TimeoutException, IOException {
    TerraformExecuteStepRequest terraformExecuteStepRequest =
        getTerraformExecuteStepRequest().skipTerraformRefresh(true).build();
    doReturn(Arrays.asList("w1")).when(spyTerraformBaseHelper).parseOutput("* w1\n");

    when(terraformClient.getWorkspaceList(terraformExecuteStepRequest.getTimeoutInMillis(),
             terraformExecuteStepRequest.getEnvVars(), terraformExecuteStepRequest.getScriptDirectory(),
             terraformExecuteStepRequest.getLogCallback()))
        .thenReturn(CliResponse.builder().output("workspace").build());

    terraformBaseHelper.executeTerraformDestroyStep(terraformExecuteStepRequest);

    Mockito.verify(terraformClient, times(1))
        .init(TerraformInitCommandRequest.builder()
                  .tfBackendConfigsFilePath(terraformExecuteStepRequest.getTfBackendConfigsFile())
                  .build(),
            terraformExecuteStepRequest.getTimeoutInMillis(), terraformExecuteStepRequest.getEnvVars(),
            terraformExecuteStepRequest.getScriptDirectory(), terraformExecuteStepRequest.getLogCallback());
    Mockito.verify(terraformClient, times(1))
        .getWorkspaceList(terraformExecuteStepRequest.getTimeoutInMillis(), terraformExecuteStepRequest.getEnvVars(),
            terraformExecuteStepRequest.getScriptDirectory(), terraformExecuteStepRequest.getLogCallback());
    Mockito.verify(terraformClient, times(1))
        .workspace(terraformExecuteStepRequest.getWorkspace(), true, terraformExecuteStepRequest.getTimeoutInMillis(),
            terraformExecuteStepRequest.getEnvVars(), terraformExecuteStepRequest.getScriptDirectory(),
            terraformExecuteStepRequest.getLogCallback(), terraformExecuteStepRequest.getAdditionalCliFlags());
    Mockito.verify(terraformClient, times(0)).refresh(any(), anyLong(), any(), anyString(), any());
    Mockito.verify(terraformClient, times(1))
        .destroy(TerraformDestroyCommandRequest.builder().targets(terraformExecuteStepRequest.getTargets()).build(),
            terraformExecuteStepRequest.getTimeoutInMillis(), terraformExecuteStepRequest.getEnvVars(),
            terraformExecuteStepRequest.getScriptDirectory(), terraformExecuteStepRequest.getLogCallback());
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testexecuteTerraformDestroyStepWhenTfCloudCli()
      throws InterruptedException, TimeoutException, IOException {
    TerraformExecuteStepRequest terraformExecuteStepRequest =
        getTerraformExecuteStepRequest().isTerraformCloudCli(true).build();
    doReturn(Arrays.asList("w1")).when(spyTerraformBaseHelper).parseOutput("* w1\n");

    terraformBaseHelper.executeTerraformDestroyStep(terraformExecuteStepRequest);

    Mockito.verify(terraformClient, times(1))
        .init(TerraformInitCommandRequest.builder()
                  .tfBackendConfigsFilePath(terraformExecuteStepRequest.getTfBackendConfigsFile())
                  .build(),
            terraformExecuteStepRequest.getTimeoutInMillis(), terraformExecuteStepRequest.getEnvVars(),
            terraformExecuteStepRequest.getScriptDirectory(), terraformExecuteStepRequest.getLogCallback());
    Mockito.verify(terraformClient, times(0)).getWorkspaceList(anyLong(), any(), anyString(), any());
    Mockito.verify(terraformClient, times(0))
        .workspace(anyString(), anyBoolean(), anyLong(), any(), anyString(), any(), anyMap());
    Mockito.verify(terraformClient, times(0)).refresh(any(), anyLong(), any(), anyString(), any());
    Mockito.verify(terraformClient, times(1)).destroy(any(), anyLong(), any(), anyString(), any());
  }

  private File createBackendConfigFile(String content, String fileName) throws IOException {
    final File file = File.createTempFile("abc", fileName);
    try (FileWriter fileWriter = new FileWriter(file)) {
      fileWriter.write(content);
    }
    return file;
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testsaveTerraformPlanContentToFile() throws IOException {
    String scriptDirectory = "repository/testSaveAndGetTerraformPlanFile";
    FileIo.createDirectoryIfDoesNotExist(scriptDirectory);
    byte[] planContent = "terraformPlanContent".getBytes();

    doReturn(planContent).when(encryptDecryptHelper).getDecryptedContent(any(), any(), any());

    terraformBaseHelper.saveTerraformPlanContentToFile(
        encryptionConfig, encryptedPlanContent, scriptDirectory, "accountId", TERRAFORM_PLAN_FILE_OUTPUT_NAME);
    List<FileData> fileDataList = FileIo.getFilesUnderPath(scriptDirectory);
    assertThat(fileDataList.size()).isEqualTo(1);
    assertThat(fileDataList.get(0).getFileBytes()).isEqualTo(planContent);

    FileIo.deleteDirectoryAndItsContentIfExists(scriptDirectory);
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testBuildCommitIdToFetchedFilesMapHasConfigFile() {
    GitBaseRequest mockGitBaseRequest = mock(GitBaseRequest.class);
    doReturn("commitsha").when(spyTerraformBaseHelper).getLatestCommitSHA(new File("repoDir"));
    doReturn("repoDir").when(gitClientHelper).getRepoDirectory(any(GitBaseRequest.class));
    doReturn("commitsha").when(spyTerraformBaseHelper).getLatestCommitSHAFromLocalRepo(any(GitBaseRequest.class));

    Map<String, String> resultMap =
        terraformBaseHelper.buildCommitIdToFetchedFilesMap("configFileIdentifier", mockGitBaseRequest, new HashMap<>());
    assertThat(resultMap).isNotNull();
    assertThat(resultMap.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testFetchConfigFileAndPrepareScriptDir() throws IOException {
    ClassLoader classLoader = TerraformBaseHelperImplTest.class.getClassLoader();

    EncryptedDataDetail encryptedDataDetail = EncryptedDataDetail.builder().fieldName("fieldName").build();
    List<EncryptedDataDetail> encryptedDataDetails = Collections.singletonList(encryptedDataDetail);
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
    ArtifactoryConfigRequest artifactoryConfigRequest = ArtifactoryConfigRequest.builder().build();
    doReturn(null).when(secretDecryptionService).decrypt(credentials, encryptedDataDetails);
    doReturn(artifactoryConfigRequest).when(artifactoryRequestMapper).toArtifactoryRequest(artifactoryConnectorDTO);
    doReturn(classLoader.getResourceAsStream("terraform/localresource.tfvar.zip"))
        .when(artifactoryNgService)
        .downloadArtifacts(eq(artifactoryConfigRequest), any(), any(), eq("artifactPath"), eq("artifactName"));
    doReturn(new ByteArrayInputStream("test".getBytes()))
        .when(delegateFileManagerBase)
        .downloadByFileId(any(), any(), any());

    terraformBaseHelper.fetchConfigFileAndPrepareScriptDir(
        artifactoryStoreDelegateConfig, "accountId", "workspace", "stateFileId", logCallback, "baseDir");
    File configFile = new File("baseDir/script-repository/repoName/localresource.tfvar");
    File stateFile = new File("baseDir/script-repository/repoName/terraform.tfstate.d/workspace/terraform.tfstate");
    assertThat(configFile.exists()).isTrue();
    assertThat(stateFile.exists()).isTrue();
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testConfigureCredentialsForModuleSource() throws IOException {
    TerraformTaskNGParameters taskNGParameters = TerraformTaskNGParameters.builder()
                                                     .entityId("entityId")
                                                     .accountId("account_id")
                                                     .taskType(TFTaskType.APPLY)
                                                     .environmentVariables(new HashMap<>())
                                                     .build();

    GitStoreDelegateConfig gitStoreDelegateConfig =
        GitStoreDelegateConfig.builder()
            .gitConfigDTO(GitConfigDTO.builder().gitAuthType(GitAuthType.SSH).url("repourl").build())
            .connectorName("connectorId")
            .paths(Arrays.asList("filepath1", "filepath2"))
            .branch("master")
            .sshKeySpecDTO(SSHKeySpecDTO.builder().build())
            .build();

    SshSessionConfig sshSessionConfig = SshSessionConfig.Builder.aSshSessionConfig()
                                            .withKeyLess(false)
                                            .withKeyPassphrase(null)
                                            .withKey(new char[] {'a', 'b'})
                                            .withKeyPath("ExpectedKeyPath")
                                            .build();

    when(sshSessionConfigMapper.getSSHSessionConfig(any(), any())).thenReturn(sshSessionConfig);

    String baseDir = "./terraform-working-dir/entityId";

    terraformBaseHelper.configureCredentialsForModuleSource(
        baseDir, taskNGParameters.getEnvironmentVariables(), gitStoreDelegateConfig, logCallback);
    assertThat(taskNGParameters.getEnvironmentVariables().size()).isEqualTo(1);
    assertThat(taskNGParameters.getEnvironmentVariables().get(TerraformBaseHelperImpl.GIT_SSH_COMMAND))
        .contains("ssh -o StrictHostKeyChecking=no -o BatchMode=yes -o PasswordAuthentication=no -i");
    assertThat(taskNGParameters.getEnvironmentVariables().get(TerraformBaseHelperImpl.GIT_SSH_COMMAND))
        .contains("./terraform-working-dir/entityId/.ssh/ssh.key");
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testConfigureCredentialsForModuleSourceSSHKeyPath() throws IOException {
    TerraformTaskNGParameters taskNGParameters = TerraformTaskNGParameters.builder()
                                                     .entityId("entityId")
                                                     .accountId("account_id")
                                                     .taskType(TFTaskType.APPLY)
                                                     .environmentVariables(new HashMap<>())
                                                     .build();

    GitStoreDelegateConfig gitStoreDelegateConfig =
        GitStoreDelegateConfig.builder()
            .gitConfigDTO(GitConfigDTO.builder().gitAuthType(GitAuthType.SSH).url("repourl").build())
            .connectorName("connectorId")
            .paths(Arrays.asList("filepath1", "filepath2"))
            .branch("master")
            .sshKeySpecDTO(SSHKeySpecDTO.builder().build())
            .build();

    SshSessionConfig sshSessionConfig = SshSessionConfig.Builder.aSshSessionConfig()
                                            .withKeyLess(true)
                                            .withKeyPassphrase(null)
                                            .withKeyPath("/home/user/.ssh/id.key")
                                            .build();

    when(sshSessionConfigMapper.getSSHSessionConfig(any(), any())).thenReturn(sshSessionConfig);

    String baseDir = "./terraform-working-dir/entityId";

    terraformBaseHelper.configureCredentialsForModuleSource(
        baseDir, taskNGParameters.getEnvironmentVariables(), gitStoreDelegateConfig, logCallback);
    assertThat(taskNGParameters.getEnvironmentVariables().size()).isEqualTo(1);
    assertThat(taskNGParameters.getEnvironmentVariables().get(TerraformBaseHelperImpl.GIT_SSH_COMMAND))
        .contains("ssh -o StrictHostKeyChecking=no -o BatchMode=yes -o PasswordAuthentication=no -i");
    assertThat(taskNGParameters.getEnvironmentVariables().get(TerraformBaseHelperImpl.GIT_SSH_COMMAND))
        .contains("/home/user/.ssh/id.key");
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testConfigureCredentialsForModuleSourcePassPhrase() throws IOException {
    TerraformTaskNGParameters taskNGParameters = TerraformTaskNGParameters.builder()
                                                     .entityId("entityId")
                                                     .accountId("account_id")
                                                     .taskType(TFTaskType.APPLY)
                                                     .environmentVariables(new HashMap<>())
                                                     .build();

    GitStoreDelegateConfig gitStoreDelegateConfig =
        GitStoreDelegateConfig.builder()
            .gitConfigDTO(GitConfigDTO.builder().gitAuthType(GitAuthType.SSH).url("repourl").build())
            .connectorName("connectorId")
            .paths(Arrays.asList("filepath1", "filepath2"))
            .branch("master")
            .sshKeySpecDTO(SSHKeySpecDTO.builder().build())
            .build();

    SshSessionConfig sshSessionConfig = SshSessionConfig.Builder.aSshSessionConfig()
                                            .withKeyLess(true)
                                            .withKeyPassphrase(new char[] {'a', 'b'})
                                            .withKeyPath("/home/user/.ssh/id.key")
                                            .build();

    when(sshSessionConfigMapper.getSSHSessionConfig(any(), any())).thenReturn(sshSessionConfig);

    String baseDir = "./terraform-working-dir/entityId";

    terraformBaseHelper.configureCredentialsForModuleSource(
        baseDir, taskNGParameters.getEnvironmentVariables(), gitStoreDelegateConfig, logCallback);
    verify(logCallback, times(1))
        .saveExecutionLog(
            color("\nExporting SSH Key with Passphrase for Module Source is not Supported", Yellow), WARN);
    assertThat(taskNGParameters.getEnvironmentVariables().size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testConfigureCredentialsForModuleSourceSSHUsername() throws IOException {
    TerraformTaskNGParameters taskNGParameters = TerraformTaskNGParameters.builder()
                                                     .entityId("entityId")
                                                     .accountId("account_id")
                                                     .taskType(TFTaskType.APPLY)
                                                     .environmentVariables(new HashMap<>())
                                                     .build();

    GitStoreDelegateConfig gitStoreDelegateConfig =
        GitStoreDelegateConfig.builder()
            .gitConfigDTO(GitConfigDTO.builder().gitAuthType(GitAuthType.SSH).url("repourl").build())
            .connectorName("connectorId")
            .paths(Arrays.asList("filepath1", "filepath2"))
            .branch("master")
            .sshKeySpecDTO(SSHKeySpecDTO.builder().build())
            .build();

    SshSessionConfig sshSessionConfig = SshSessionConfig.Builder.aSshSessionConfig()
                                            .withKeyLess(false)
                                            .withUserName("username")
                                            .withPassword(new char[] {'p', 'w'})
                                            .build();

    when(sshSessionConfigMapper.getSSHSessionConfig(any(), any())).thenReturn(sshSessionConfig);

    String baseDir = "./terraform-working-dir/entityId";

    terraformBaseHelper.configureCredentialsForModuleSource(
        baseDir, taskNGParameters.getEnvironmentVariables(), gitStoreDelegateConfig, logCallback);
    verify(logCallback, times(1))
        .saveExecutionLog(
            color("\nExporting Username and Password with SSH for Module Source is not Supported", Yellow), WARN);
    assertThat(taskNGParameters.getEnvironmentVariables().size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testConfigureCredentialsForModuleSourceHTTPAuth() throws IOException {
    try {
      TerraformTaskNGParameters taskNGParameters = TerraformTaskNGParameters.builder()
                                                       .entityId("entityId")
                                                       .accountId("account_id")
                                                       .taskType(TFTaskType.APPLY)
                                                       .environmentVariables(new HashMap<>())
                                                       .build();

      GitStoreDelegateConfig gitStoreDelegateConfig =
          GitStoreDelegateConfig.builder()
              .gitConfigDTO(GitConfigDTO.builder().gitAuthType(GitAuthType.HTTP).url("repourl").build())
              .connectorName("connectorId")
              .paths(Arrays.asList("filepath1", "filepath2"))
              .branch("master")
              .sshKeySpecDTO(SSHKeySpecDTO.builder().build())
              .build();

      String baseDir = "./terraform-working-dir/entityId";

      terraformBaseHelper.configureCredentialsForModuleSource(
          baseDir, taskNGParameters.getEnvironmentVariables(), gitStoreDelegateConfig, logCallback);
      verify(logCallback, times(1))
          .saveExecutionLog(
              color("\nExporting Username and Password for Module Source is not Supported", Yellow), WARN);
      assertThat(taskNGParameters.getEnvironmentVariables().size()).isEqualTo(0);
    } finally {
      FileIo.deleteFileIfExists(Paths.get(SSH_KEY_FILENAME).toAbsolutePath().toString());
    }
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testCheckoutRemoteGitVarFileAndConvertToVarFilePaths() throws IOException {
    HashMap<String, String> commitIdMap = new HashMap<>();
    String scriptDirectory = "repository/testSaveAndGetTerraformPlanFile";
    FileIo.createDirectoryIfDoesNotExist(scriptDirectory);
    String tfvarDir = "repository/tfVarDir";
    FileIo.createDirectoryIfDoesNotExist(tfvarDir);
    doReturn("varFilesCommitId").when(gitClient).downloadFiles(any());

    List<String> varFilePaths = terraformBaseHelper.checkoutRemoteVarFileAndConvertToVarFilePaths(
        getGitTerraformFileInfoList(), scriptDirectory, logCallback, "accountId", tfvarDir, commitIdMap, false, null);
    assertThat(varFilePaths.size()).isEqualTo(2);
    assertThat(varFilePaths.get(0))
        .isEqualTo(Paths.get(tfvarDir).toAbsolutePath() + "/"
            + "filepath1");
    assertThat(commitIdMap.get("varFiles")).isEqualTo("varFilesCommitId");
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testCheckoutRemoteGitVarFileAndConvertToVarFilePathsWhenTfCloudCli() throws IOException {
    HashMap<String, String> commitIdMap = new HashMap<>();
    String scriptDirectory = "repository/testSaveAndGetTerraformPlanFile";
    FileIo.createDirectoryIfDoesNotExist(scriptDirectory);
    String tfvarDir = "repository/tfVarDir";
    FileIo.createDirectoryIfDoesNotExist(tfvarDir);
    doReturn("varFilesCommitId").when(gitClient).downloadFiles(any());

    List<String> varFilePaths =
        terraformBaseHelper.checkoutRemoteVarFileAndConvertToVarFilePaths(getGitTerraformFileInfoListInline(),
            scriptDirectory, logCallback, "accountId", tfvarDir, commitIdMap, true, null);
    assertThat(varFilePaths.size()).isEqualTo(1);
    assertThat(varFilePaths.get(0)).contains(scriptDirectory);
    assertThat(varFilePaths.get(0)).contains(".auto.tfvars");
  }

  @Test
  @Owner(developers = JELENA)
  @Category(UnitTests.class)
  public void testCheckoutRemoteBackendConfigFileAndConvertToFilePath() throws IOException {
    HashMap<String, String> commitIdMap = new HashMap<>();
    String scriptDirectory = "repository/testSaveAndGetTerraformPlanFile";
    FileIo.createDirectoryIfDoesNotExist(scriptDirectory);
    String configDir = "repository/backendConfigDir";
    FileIo.createDirectoryIfDoesNotExist(configDir);
    GitStoreDelegateConfig gitStoreDelegateConfig = getGitStoreDelegateConfig();
    TerraformBackendConfigFileInfo configFile =
        RemoteTerraformBackendConfigFileInfo.builder()
            .gitFetchFilesConfig(GitFetchFilesConfig.builder()
                                     .identifier("backendConfig")
                                     .gitStoreDelegateConfig(gitStoreDelegateConfig)
                                     .build())
            .build();
    doReturn("backendConfigCommitId").when(gitClient).downloadFiles(any());

    String filePath = terraformBaseHelper.checkoutRemoteBackendConfigFileAndConvertToFilePath(
        configFile, scriptDirectory, logCallback, "accountId", configDir, commitIdMap, null);

    assertThat(filePath).isNotNull();
    assertThat(filePath).isEqualTo(Paths.get(configDir).toAbsolutePath() + "/"
        + "filepath1");
    assertThat(commitIdMap.get("backendConfig")).isEqualTo("backendConfigCommitId");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testCheckoutRemoteArtifactoryVarFileAndConvertToVarFilePaths() throws IOException {
    String scriptDirectory = "repository/testSaveAndGetTerraformPlanFile";
    FileIo.createDirectoryIfDoesNotExist(scriptDirectory);
    String tfvarDir = "repository/tfVarDir";
    FileIo.createDirectoryIfDoesNotExist(tfvarDir);
    ClassLoader classLoader = TerraformBaseHelperImplTest.class.getClassLoader();

    EncryptedDataDetail encryptedDataDetail = EncryptedDataDetail.builder().fieldName("fieldName").build();
    List<EncryptedDataDetail> encryptedDataDetails = Collections.singletonList(encryptedDataDetail);
    ArtifactoryUsernamePasswordAuthDTO credentials = ArtifactoryUsernamePasswordAuthDTO.builder().build();
    ArtifactoryConnectorDTO artifactoryConnectorDTO =
        ArtifactoryConnectorDTO.builder()
            .auth(ArtifactoryAuthenticationDTO.builder().credentials(credentials).build())
            .build();
    ArtifactoryStoreDelegateConfig artifactoryStoreDelegateConfig =
        ArtifactoryStoreDelegateConfig.builder()
            .artifacts(Arrays.asList("artifactPath", "/artifactPath/artifactPath"))
            .repositoryName("repoName")
            .encryptedDataDetails(encryptedDataDetails)
            .connectorDTO(ConnectorInfoDTO.builder().connectorConfig(artifactoryConnectorDTO).build())
            .build();
    ArtifactoryConfigRequest artifactoryConfigRequest = ArtifactoryConfigRequest.builder().build();
    doReturn(null).when(secretDecryptionService).decrypt(credentials, encryptedDataDetails);
    doReturn(artifactoryConfigRequest).when(artifactoryRequestMapper).toArtifactoryRequest(artifactoryConnectorDTO);
    when(artifactoryNgService.downloadArtifacts(eq(artifactoryConfigRequest), any(), any(), any(), eq("artifactName")))
        .thenReturn(classLoader.getResourceAsStream("terraform/localresource.tfvar.zip"))
        .thenReturn(classLoader.getResourceAsStream("terraform/localresource.tfvar.zip"));
    doReturn(new ByteArrayInputStream("test".getBytes()))
        .when(delegateFileManagerBase)
        .downloadByFileId(any(), any(), any());

    List<String> varFilePaths = terraformBaseHelper.checkoutRemoteVarFileAndConvertToVarFilePaths(
        Arrays.asList(
            RemoteTerraformVarFileInfo.builder().filestoreFetchFilesConfig(artifactoryStoreDelegateConfig).build()),
        scriptDirectory, logCallback, "accountId", tfvarDir, new HashMap<>(), false, null);

    verify(artifactoryNgService, times(2)).downloadArtifacts(any(), any(), any(), any(), any());
    assertThat(varFilePaths.size()).isEqualTo(2);
    assertThat(varFilePaths.get(0)).contains("localresource.tfvar");
    assertThat(varFilePaths.get(1)).contains("localresource.tfvar");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testCheckoutRemoteGitAndArtifactoryVarFileAndConvertToVarFilePaths() throws IOException {
    String scriptDirectory = "repository/testSaveAndGetTerraformPlanFile";
    FileIo.createDirectoryIfDoesNotExist(scriptDirectory);
    String tfvarDir = "repository/tfVarDir";
    FileIo.createDirectoryIfDoesNotExist(tfvarDir);
    ClassLoader classLoader = TerraformBaseHelperImplTest.class.getClassLoader();

    EncryptedDataDetail encryptedDataDetail = EncryptedDataDetail.builder().fieldName("fieldName").build();
    List<EncryptedDataDetail> encryptedDataDetails = Collections.singletonList(encryptedDataDetail);
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
    ArtifactoryConfigRequest artifactoryConfigRequest = ArtifactoryConfigRequest.builder().build();
    doReturn(null).when(secretDecryptionService).decrypt(credentials, encryptedDataDetails);
    doReturn(artifactoryConfigRequest).when(artifactoryRequestMapper).toArtifactoryRequest(artifactoryConnectorDTO);
    doReturn(classLoader.getResourceAsStream("terraform/localresource.tfvar.zip"))
        .when(artifactoryNgService)
        .downloadArtifacts(eq(artifactoryConfigRequest), any(), any(), eq("artifactPath"), eq("artifactName"));
    doReturn(new ByteArrayInputStream("test".getBytes()))
        .when(delegateFileManagerBase)
        .downloadByFileId(any(), any(), any());

    List<TerraformVarFileInfo> terraformVarFileInfos = getGitTerraformFileInfoList();
    terraformVarFileInfos.add(
        RemoteTerraformVarFileInfo.builder().filestoreFetchFilesConfig(artifactoryStoreDelegateConfig).build());

    List<String> varFilePaths = terraformBaseHelper.checkoutRemoteVarFileAndConvertToVarFilePaths(
        terraformVarFileInfos, scriptDirectory, logCallback, "accountId", tfvarDir, new HashMap<>(), false, null);

    assertThat(varFilePaths.size()).isEqualTo(3);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testUploadTfPlanJson() throws IOException {
    final String fileId = "fileId";
    final String tfPlanJsonContent = "plan-json-content";
    final File tfPlanJsonFile = File.createTempFile("testFile", "tfPlanJson");

    try (FileWriter fileWriter = new FileWriter(tfPlanJsonFile)) {
      fileWriter.write(tfPlanJsonContent);
    }

    terraformBaseHelper.uploadTfPlanJson(
        "accountId", "delegateId", "taskId", "entityId", "planName", tfPlanJsonFile.getAbsolutePath());

    ArgumentCaptor<DelegateFile> delegateFileCaptor = ArgumentCaptor.forClass(DelegateFile.class);
    verify(delegateFileManagerBase, times(1)).upload(delegateFileCaptor.capture(), any(InputStream.class));
    DelegateFile delegateFile = delegateFileCaptor.getValue();
    assertThat(delegateFile.getDelegateId()).isEqualTo("delegateId");
    assertThat(delegateFile.getAccountId()).isEqualTo("accountId");
    assertThat(delegateFile.getTaskId()).isEqualTo("taskId");
    assertThat(delegateFile.getBucket()).isEqualTo(FileBucket.TERRAFORM_PLAN_JSON);
    assertThat(delegateFile.getFileName()).isEqualTo(format(TERRAFORM_PLAN_JSON_FILE_NAME, "planName"));

    FileIo.deleteFileIfExists(tfPlanJsonFile.getAbsolutePath());
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testFetchS3ConfigFilesAndPrepareScriptDirWithVersioning() throws IOException {
    Map<String, Map<String, String>> keyVersionMap = new HashMap<>();
    Map<String, String> versions = new HashMap<>();
    versions.put("terraform/file1.tf", "v1");
    versions.put("terraform/file2.tf", "v2");
    S3StoreTFDelegateConfig s3StoreTFDelegateConfig =
        getDefaultS3StoreTFDelegateConfig("TF_CONFIG_FILES", Collections.singletonList("terraform"), versions);
    List<S3ObjectSummary> summaries =
        getS3ObjectSummaries(List.of("terraform/", "terraform/file1.tf", "terraform/file2.tf"));
    TerraformTaskNGParameters taskNGParameters = getTerraformTaskNgParams();
    doReturn(null).when(secretDecryptionService).decrypt(any(), any());
    doReturn(new ByteArrayInputStream("test".getBytes()))
        .when(delegateFileManagerBase)
        .downloadByFileId(any(), any(), any());
    doReturn(AwsInternalConfig.builder().build()).when(awsNgConfigMapper).createAwsInternalConfig(any());
    doReturn(listObjectsV2Result).when(awsApiHelperService).listObjectsInS3(any(), any(), any());
    doReturn(summaries).when(listObjectsV2Result).getObjectSummaries();
    doReturn(true).when(awsApiHelperService).isVersioningEnabledForBucket(any(), any(), any());
    doReturn(s3Object).when(awsApiHelperService).getVersionedObjectFromS3(any(), any(), any(), any(), any());
    doReturn(objectMetadata).when(s3Object).getObjectMetadata();
    doReturn("v1", "v2").when(objectMetadata).getVersionId();
    doReturn(new S3ObjectInputStream(new ByteArrayInputStream("test".getBytes()), null))
        .when(s3Object)
        .getObjectContent();

    terraformBaseHelper.fetchS3ConfigFilesAndPrepareScriptDir(
        s3StoreTFDelegateConfig, taskNGParameters, "baseDir", keyVersionMap, logCallback);

    verify(awsApiHelperService, times(1)).isVersioningEnabledForBucket(any(), any(), any());
    verify(awsApiHelperService, times(1))
        .getVersionedObjectFromS3(any(), eq("region"), eq("bucket"), eq("terraform/file1.tf"), eq("v1"));
    verify(awsApiHelperService, times(1))
        .getVersionedObjectFromS3(any(), eq("region"), eq("bucket"), eq("terraform/file2.tf"), eq("v2"));
    assertThat(FileUtils.getFile("baseDir/script-repository/terraform/file1.tf")).exists();
    assertThat(FileUtils.getFile("baseDir/script-repository/terraform/file2.tf")).exists();
    assertThat(keyVersionMap).isNotEmpty();
    assertThat(keyVersionMap.get("TF_CONFIG_FILES")).containsEntry("terraform/file1.tf", "v1");
    assertThat(keyVersionMap.get("TF_CONFIG_FILES")).containsEntry("terraform/file2.tf", "v2");
    FileUtils.deleteDirectory(Paths.get("baseDir").toFile());
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testFetchS3ConfigFilesAndPrepareScriptDirWithoutVersioning() throws IOException {
    Map<String, Map<String, String>> keyVersionMap = new HashMap<>();
    S3StoreTFDelegateConfig s3StoreTFDelegateConfig =
        getDefaultS3StoreTFDelegateConfig("TF_CONFIG_FILES", Collections.singletonList("terraform"), null);
    List<S3ObjectSummary> summaries =
        getS3ObjectSummaries(List.of("terraform/", "terraform/file1.tf", "terraform/file2.tf"));
    TerraformTaskNGParameters taskNGParameters = getTerraformTaskNgParams();
    doReturn(null).when(secretDecryptionService).decrypt(any(), any());
    doReturn(new ByteArrayInputStream("test".getBytes()))
        .when(delegateFileManagerBase)
        .downloadByFileId(any(), any(), any());
    doReturn(AwsInternalConfig.builder().build()).when(awsNgConfigMapper).createAwsInternalConfig(any());
    doReturn(listObjectsV2Result).when(awsApiHelperService).listObjectsInS3(any(), any(), any());
    doReturn(summaries).when(listObjectsV2Result).getObjectSummaries();
    doReturn(false).when(awsApiHelperService).isVersioningEnabledForBucket(any(), any(), any());
    doReturn(s3Object).when(awsApiHelperService).getObjectFromS3(any(), any(), any(), any());
    doReturn(new S3ObjectInputStream(new ByteArrayInputStream("test".getBytes()), null))
        .when(s3Object)
        .getObjectContent();

    terraformBaseHelper.fetchS3ConfigFilesAndPrepareScriptDir(
        s3StoreTFDelegateConfig, taskNGParameters, "baseDir", keyVersionMap, logCallback);

    verify(awsApiHelperService, times(1)).isVersioningEnabledForBucket(any(), any(), any());
    verify(awsApiHelperService, times(1)).getObjectFromS3(any(), eq("region"), eq("bucket"), eq("terraform/file1.tf"));
    verify(awsApiHelperService, times(1)).getObjectFromS3(any(), eq("region"), eq("bucket"), eq("terraform/file2.tf"));
    assertThat(FileUtils.getFile("baseDir/script-repository/terraform/file1.tf")).exists();
    assertThat(FileUtils.getFile("baseDir/script-repository/terraform/file2.tf")).exists();
    assertThat(keyVersionMap.get("TF_CONFIG_FILES")).isEmpty();
    FileUtils.deleteDirectory(Paths.get("baseDir").toFile());
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void checkoutRemoteVarFileAndConvertToVarFilePathsS3() throws IOException {
    Map<String, String> versions1 = new HashMap<>();
    versions1.put("var1/file1", "v1");
    versions1.put("var1/file2", "v2");
    Map<String, String> versions2 = new HashMap<>();
    versions2.put("var2/file1", "v3");
    List<S3ObjectSummary> summaries1 = getS3ObjectSummaries(List.of("var1/file1"));
    List<S3ObjectSummary> summaries2 = getS3ObjectSummaries(List.of("var1/file2"));
    List<S3ObjectSummary> summaries3 = getS3ObjectSummaries(List.of("var2/file1"));
    List<TerraformVarFileInfo> varFileInfo = List.of(RemoteTerraformVarFileInfo.builder()
                                                         .filestoreFetchFilesConfig(getDefaultS3StoreTFDelegateConfig(
                                                             "VAR1", List.of("var1/file1", "var1/file2"), versions1))
                                                         .build(),
        RemoteTerraformVarFileInfo.builder()
            .filestoreFetchFilesConfig(
                getDefaultS3StoreTFDelegateConfig("VAR2", Collections.singletonList("var2/file1"), versions2))
            .build());
    Map<String, Map<String, String>> keyVersionMap = new HashMap<>();

    doReturn(null).when(secretDecryptionService).decrypt(any(), any());
    doReturn(AwsInternalConfig.builder().build()).when(awsNgConfigMapper).createAwsInternalConfig(any());
    doReturn(listObjectsV2Result).when(awsApiHelperService).listObjectsInS3(any(), any(), any());
    doReturn(summaries1, summaries2, summaries3).when(listObjectsV2Result).getObjectSummaries();
    doReturn(true).when(awsApiHelperService).isVersioningEnabledForBucket(any(), any(), any());
    doReturn(s3Object).when(awsApiHelperService).getVersionedObjectFromS3(any(), any(), any(), any(), any());
    doReturn(objectMetadata).when(s3Object).getObjectMetadata();
    doReturn("v1", "v2", "v3").when(objectMetadata).getVersionId();
    doReturn(new S3ObjectInputStream(new ByteArrayInputStream("test".getBytes()), null))
        .when(s3Object)
        .getObjectContent();

    List<String> paths = terraformBaseHelper.checkoutRemoteVarFileAndConvertToVarFilePaths(
        varFileInfo, null, logCallback, "accountID", "tfVarDirectory", new HashMap<>(), false, keyVersionMap);

    verify(awsApiHelperService, times(3)).isVersioningEnabledForBucket(any(), any(), any());
    verify(awsApiHelperService, times(1))
        .getVersionedObjectFromS3(any(), eq("region"), eq("bucket"), eq("var1/file1"), eq("v1"));
    verify(awsApiHelperService, times(1))
        .getVersionedObjectFromS3(any(), eq("region"), eq("bucket"), eq("var1/file2"), eq("v2"));
    verify(awsApiHelperService, times(1))
        .getVersionedObjectFromS3(any(), eq("region"), eq("bucket"), eq("var2/file1"), eq("v3"));
    assertThat(FileUtils.getFile("tfVarDirectory/var1/file1")).exists();
    assertThat(FileUtils.getFile("tfVarDirectory/var1/file2")).exists();
    assertThat(FileUtils.getFile("tfVarDirectory/var2/file1")).exists();
    assertThat(keyVersionMap).isNotEmpty();
    assertThat(keyVersionMap.get("VAR1")).containsEntry("var1/file1", "v1");
    assertThat(keyVersionMap.get("VAR1")).containsEntry("var1/file2", "v2");
    assertThat(keyVersionMap.get("VAR2")).containsEntry("var2/file1", "v3");
    assertThat(paths.size()).isEqualTo(3);
    FileUtils.deleteDirectory(Paths.get("baseDir").toFile());
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void checkoutRemoteBackendConfigFileAndConvertToFilePathS3() throws IOException {
    Map<String, String> versions1 = new HashMap<>();
    versions1.put("terraform/backend/backend.tf", "v1");
    List<S3ObjectSummary> summaries1 = getS3ObjectSummaries(List.of("terraform/backend/backend.tf"));
    RemoteTerraformBackendConfigFileInfo remoteTerraformBackendConfigFileInfo =
        RemoteTerraformBackendConfigFileInfo.builder()
            .filestoreFetchFilesConfig(getDefaultS3StoreTFDelegateConfig(
                "TF_BACKEND_FILE", List.of("terraform/backend/backend.tf"), versions1))
            .build();
    Map<String, Map<String, String>> keyVersionMap = new HashMap<>();

    doReturn(null).when(secretDecryptionService).decrypt(any(), any());
    doReturn(AwsInternalConfig.builder().build()).when(awsNgConfigMapper).createAwsInternalConfig(any());
    doReturn(listObjectsV2Result).when(awsApiHelperService).listObjectsInS3(any(), any(), any());
    doReturn(summaries1).when(listObjectsV2Result).getObjectSummaries();
    doReturn(true).when(awsApiHelperService).isVersioningEnabledForBucket(any(), any(), any());
    doReturn(s3Object).when(awsApiHelperService).getVersionedObjectFromS3(any(), any(), any(), any(), any());
    doReturn(objectMetadata).when(s3Object).getObjectMetadata();
    doReturn("v1").when(objectMetadata).getVersionId();
    doReturn(new S3ObjectInputStream(new ByteArrayInputStream("test".getBytes()), null))
        .when(s3Object)
        .getObjectContent();

    String path =
        terraformBaseHelper.checkoutRemoteBackendConfigFileAndConvertToFilePath(remoteTerraformBackendConfigFileInfo,
            null, logCallback, "accountID", "tfBeDirectory", new HashMap<>(), keyVersionMap);

    verify(awsApiHelperService, times(1)).isVersioningEnabledForBucket(any(), any(), any());
    verify(awsApiHelperService, times(1))
        .getVersionedObjectFromS3(any(), eq("region"), eq("bucket"), eq("terraform/backend/backend.tf"), eq("v1"));
    assertThat(FileUtils.getFile("tfBeDirectory/terraform/backend/backend.tf")).exists();
    assertThat(keyVersionMap).isNotEmpty();
    assertThat(keyVersionMap.get("TF_BACKEND_FILE")).containsEntry("terraform/backend/backend.tf", "v1");
    assertThat(path).endsWith("tfBeDirectory/terraform/backend/backend.tf");
    FileUtils.deleteDirectory(Paths.get("baseDir").toFile());
  }

  private List<TerraformVarFileInfo> getGitTerraformFileInfoList() {
    List<TerraformVarFileInfo> varFileInfos = new ArrayList<>();
    GitStoreDelegateConfig gitStoreDelegateConfig = getGitStoreDelegateConfig();
    RemoteTerraformVarFileInfo remoteTerraformVarFileInfo =
        RemoteTerraformVarFileInfo.builder()
            .gitFetchFilesConfig(GitFetchFilesConfig.builder()
                                     .identifier("varFiles")
                                     .gitStoreDelegateConfig(gitStoreDelegateConfig)
                                     .build())
            .build();

    varFileInfos.add(remoteTerraformVarFileInfo);

    return varFileInfos;
  }

  private List<TerraformVarFileInfo> getGitTerraformFileInfoListInline() {
    List<TerraformVarFileInfo> varFileInfos = new ArrayList<>();
    InlineTerraformVarFileInfo inlineTerraformVarFileInfo =
        InlineTerraformVarFileInfo.builder().varFileContent("test-key=test-value").build();

    varFileInfos.add(inlineTerraformVarFileInfo);

    return varFileInfos;
  }

  private GitStoreDelegateConfig getGitStoreDelegateConfig() {
    return GitStoreDelegateConfig.builder()
        .gitConfigDTO(GitConfigDTO.builder().url("repourl").build())
        .connectorName("connectorId")
        .paths(Arrays.asList("filepath1", "filepath2"))
        .branch("master")
        .sshKeySpecDTO(SSHKeySpecDTO.builder().build())
        .build();
  }

  private TerraformExecuteStepRequestBuilder getTerraformExecuteStepRequest() {
    return TerraformExecuteStepRequest.builder()
        .tfBackendConfigsFile(tfBackendConfig.getAbsolutePath())
        .tfOutputsFile("outputfile.txt")
        .scriptDirectory("scriptDirectory")
        .envVars(new HashMap<>())
        .workspace("workspace")
        .isRunPlanOnly(false)
        .isSkipRefreshBeforeApplyingPlan(true)
        .isSaveTerraformJson(false)
        .logCallback(logCallback)
        .planJsonLogOutputStream(planJsonLogOutputStream)
        .planLogOutputStream(planLogOutputStream)
        .skipTerraformRefresh(false);
  }

  private S3StoreTFDelegateConfig getDefaultS3StoreTFDelegateConfig(
      String identifier, List<String> paths, Map<String, String> versions) {
    return S3StoreTFDelegateConfig.builder()
        .identifier(identifier)
        .region("region")
        .bucketName("bucket")
        .paths(paths)
        .versions(versions)
        .encryptedDataDetails(Collections.singletonList(mock(EncryptedDataDetail.class)))
        .connectorDTO(ConnectorInfoDTO.builder()
                          .connectorConfig(AwsConnectorDTO.builder()
                                               .credential(AwsCredentialDTO.builder()
                                                               .awsCredentialType(AwsCredentialType.MANUAL_CREDENTIALS)
                                                               .config(AwsManualConfigSpecDTO.builder().build())
                                                               .build())
                                               .build())
                          .build())
        .build();
  }

  @NotNull
  private List<S3ObjectSummary> getS3ObjectSummaries(List<String> keys) {
    return keys.stream()
        .map(key -> {
          S3ObjectSummary objectSummary = new S3ObjectSummary();
          objectSummary.setKey(key);
          return objectSummary;
        })
        .collect(Collectors.toList());
  }

  private TerraformTaskNGParameters getTerraformTaskNgParams() {
    return TerraformTaskNGParameters.builder()
        .entityId("entity-id")
        .workspace("ws")
        .accountId("accountId")
        .currentStateFileId("stateFieldId")
        .taskType(TFTaskType.PLAN)
        .build();
  }
}
