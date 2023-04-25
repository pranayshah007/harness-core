/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.cf;

import static io.harness.delegate.cf.CfTestConstants.ACCOUNT_ID;
import static io.harness.delegate.cf.CfTestConstants.CF_PATH;
import static io.harness.delegate.cf.CfTestConstants.ORG;
import static io.harness.delegate.cf.CfTestConstants.SPACE;
import static io.harness.delegate.cf.CfTestConstants.URL;
import static io.harness.delegate.cf.CfTestConstants.USER_NAME_DECRYPTED;
import static io.harness.rule.OwnerRule.ANIL;
import static io.harness.rule.OwnerRule.BOJANA;
import static io.harness.rule.OwnerRule.IVAN;
import static io.harness.rule.OwnerRule.ROHIT_KUMAR;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FileData;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.pcf.CfInternalConfig;
import io.harness.delegate.task.pcf.CfCommandRequest.PcfCommandType;
import io.harness.delegate.task.pcf.request.CfCommandRollbackRequest;
import io.harness.delegate.task.pcf.request.CfRunPluginCommandRequest;
import io.harness.delegate.task.pcf.response.CfCommandExecutionResponse;
import io.harness.exception.InvalidArgumentsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.pcf.CfDeploymentManager;
import io.harness.pcf.PivotalClientApiException;
import io.harness.pcf.model.CfRunPluginScriptRequestData;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;

import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@OwnedBy(HarnessTeam.CDP)
public class PcfRunPluginCommandTaskHandlerTest extends CategoryTest {
  @Mock private CfDeploymentManager pcfDeploymentManager;
  @Mock private PcfCommandTaskBaseHelper pcfCommandTaskHelper;
  @Mock EncryptedDataDetail encryptedDataDetail;
  @Mock ILogStreamingTaskClient logStreamingTaskClient;
  @Mock LogCallback executionLogCallback;
  @Mock private SecretDecryptionService encryptionService;

  @Spy @InjectMocks PcfRunPluginCommandTaskHandler pcfRunPluginCommandTaskHandler;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    doReturn(executionLogCallback).when(logStreamingTaskClient).obtainLogCallback(any());
  }

  private CfInternalConfig getPcfConfig() {
    return CfInternalConfig.builder().username(USER_NAME_DECRYPTED).endpointUrl(URL).password(new char[0]).build();
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void test_executeTaskInternal() throws PivotalClientApiException, IOException {
    doNothing()
        .when(pcfDeploymentManager)
        .runPcfPluginScript(any(CfRunPluginScriptRequestData.class), Mockito.eq(executionLogCallback));
    CfRunPluginCommandRequest pcfCommandRequest = getPcfRunPluginCommandRequest();
    pcfRunPluginCommandTaskHandler.executeTaskInternal(pcfCommandRequest, null, logStreamingTaskClient, false);

    // verify
    ArgumentCaptor<CfRunPluginScriptRequestData> argumentCaptor =
        ArgumentCaptor.forClass(CfRunPluginScriptRequestData.class);
    verify(pcfDeploymentManager).runPcfPluginScript(argumentCaptor.capture(), eq(executionLogCallback));

    final CfRunPluginScriptRequestData pcfRunPluginScriptRequestData = argumentCaptor.getValue();
    assertThat(pcfRunPluginScriptRequestData.getWorkingDirectory()).isNotNull();
    assertThat(pcfRunPluginScriptRequestData.getFinalScriptString())
        .isEqualTo("cf create-service " + pcfRunPluginScriptRequestData.getWorkingDirectory() + "/manifest.yml");

    verify(pcfRunPluginCommandTaskHandler, times(1))
        .saveFilesInWorkingDirectoryStringContent(anyList(), eq(pcfRunPluginScriptRequestData.getWorkingDirectory()));
  }

  private CfRunPluginCommandRequest getPcfRunPluginCommandRequest() {
    return CfRunPluginCommandRequest.builder()
        .pcfCommandType(PcfCommandType.SETUP)
        .pcfConfig(getPcfConfig())
        .organization(ORG)
        .space(SPACE)
        .accountId(ACCOUNT_ID)
        .timeoutIntervalInMin(5)
        .renderedScriptString("cf create-service ${service.manifest.repoRoot}/manifest.yml")
        .encryptedDataDetails(null)
        .fileDataList(ImmutableList.of(FileData.builder()
                                           .filePath("manifest.yml")
                                           .fileBytes("file data ".getBytes(StandardCharsets.UTF_8))
                                           .build()))
        .filePathsInScript(ImmutableList.of("/manifest.yml"))
        .build();
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void test_handleError() {
    final CfCommandExecutionResponse commandExecutionResponse = pcfRunPluginCommandTaskHandler.handleError(
        executionLogCallback, getPcfRunPluginCommandRequest(), new PivotalClientApiException(""));
    assertThat(commandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testNoSecretInLogs() {
    String secretScript = "secret script";
    String secretFilePath = "secretPath";
    String secretContent = "secret content";
    String secretRepoRoot = "root";

    CfRunPluginCommandRequest pluginCommandRequest =
        CfRunPluginCommandRequest.builder()
            .pcfCommandType(PcfCommandType.SETUP)
            .pcfConfig(getPcfConfig())
            .organization(ORG)
            .space(SPACE)
            .accountId(ACCOUNT_ID)
            .timeoutIntervalInMin(5)
            .renderedScriptString(secretScript)
            .encryptedDataDetails(null)
            .fileDataList(
                ImmutableList.of(FileData.builder().filePath(secretFilePath).fileContent(secretContent).build()))
            .filePathsInScript(ImmutableList.of("/manifest.yml"))
            .repoRoot(secretRepoRoot)
            .build();

    String errorMsg = pluginCommandRequest.toString();
    assertThat(errorMsg.contains(secretScript)).isFalse();
    assertThat(errorMsg.contains(secretFilePath)).isFalse();
    assertThat(errorMsg.contains(secretContent)).isFalse();
    assertThat(errorMsg.contains(secretRepoRoot)).isFalse();
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalInvalidArgumentsException() {
    try {
      pcfRunPluginCommandTaskHandler.executeTaskInternal(
          CfCommandRollbackRequest.builder().build(), null, logStreamingTaskClient, false);
    } catch (Exception e) {
      assertThatExceptionOfType(InvalidArgumentsException.class);
      InvalidArgumentsException invalidArgumentsException = (InvalidArgumentsException) e;
      assertThat(invalidArgumentsException.getParams())
          .containsValue("cfCommandRequest: Must be instance of CfPluginCommandRequest");
    }
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testPrepareFinalScriptWithRepoRoot() {
    String script = "/path-to-binary/v6/cf create-service-push --service-manifest\n"
        + "${service.manifest.repoRoot}/manifests";
    String workingDirPath = "working-dir-path";
    String finalScript =
        pcfRunPluginCommandTaskHandler.prepareFinalScript(script, workingDirPath, StringUtils.EMPTY, CF_PATH);
    assertThat(finalScript)
        .isEqualTo("/path-to-binary/v6/cf create-service-push --service-manifest\n"
            + "working-dir-path/manifests");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testPrepareFinalScriptWithDeployYaml() {
    String script = "cf create-service-push --service-manifest\n"
        + "${service.manifest}/manifests/deploy.yml --no-push";
    String repoRoot = "root-path/rd";
    String finalScript =
        pcfRunPluginCommandTaskHandler.prepareFinalScript(script, StringUtils.EMPTY, repoRoot, CF_PATH);
    assertThat(finalScript)
        .isEqualTo("cf create-service-push --service-manifest\n"
            + "root-path/rd/manifests/deploy.yml --no-push");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testPrepareFinalScript() {
    String script = "/path-to-binary/v7/cf create-service-push --service-manifest\n"
        + "${service.manifest.repoRoot}/manifests\n"
        + "${service.manifest}/manifests/deploy.yml --no-push";
    String workingDirPath = "working-dir-path/";
    String repoRoot = "root-path/rd";
    String finalScript = pcfRunPluginCommandTaskHandler.prepareFinalScript(script, workingDirPath, repoRoot, CF_PATH);
    assertThat(finalScript)
        .isEqualTo("/path-to-binary/v7/cf create-service-push --service-manifest\n"
            + "working-dir-path//manifests\n"
            + "working-dir-path/root-path/rd/manifests/deploy.yml --no-push");
  }
}
