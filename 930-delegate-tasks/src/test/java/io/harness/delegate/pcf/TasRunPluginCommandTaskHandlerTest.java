/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.pcf;

import static io.harness.pcf.model.PcfConstants.PCF_ARTIFACT_DOWNLOAD_DIR_PATH;
import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;

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
import io.harness.beans.FileData;
import io.harness.category.element.UnitTests;
import io.harness.connector.task.tas.TasNgConfigMapper;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.cf.PcfCommandTaskBaseHelper;
import io.harness.delegate.task.cf.CfCommandTaskHelperNG;
import io.harness.delegate.task.pcf.TasTaskHelperBase;
import io.harness.delegate.task.pcf.request.CfBasicSetupRequestNG;
import io.harness.delegate.task.pcf.request.CfRunPluginCommandRequestNG;
import io.harness.delegate.task.pcf.response.TasInfraConfig;
import io.harness.delegate.task.pcf.response.TasRunPluginResponse;
import io.harness.exception.InvalidArgumentsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.pcf.CfCommandUnitConstants;
import io.harness.pcf.CfDeploymentManager;
import io.harness.pcf.PivotalClientApiException;
import io.harness.pcf.model.CfRunPluginScriptRequestData;
import io.harness.pcf.model.CloudFoundryConfig;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableList;
import java.nio.charset.StandardCharsets;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class TasRunPluginCommandTaskHandlerTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  private final CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
  private final TasInfraConfig tasInfraConfig = TasInfraConfig.builder().build();

  @Mock TasNgConfigMapper tasNgConfigMapper;
  @Mock protected CfCommandTaskHelperNG cfCommandTaskHelperNG;
  @Mock PcfCommandTaskBaseHelper pcfCommandTaskBaseHelper;
  @Mock CfDeploymentManager cfDeploymentManager;
  @Mock TasTaskHelperBase tasTaskHelperBase;
  @Mock ILogStreamingTaskClient logStreamingTaskClient;
  @Mock LogCallback executionLogCallback;
  @Spy @InjectMocks private TasRunPluginCommandTaskHandler tasRunPluginCommandTaskHandler;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    doReturn(executionLogCallback).when(logStreamingTaskClient).obtainLogCallback(any());
    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Pcfplugin, true, commandUnitsProgress);
    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Wrapup, true, commandUnitsProgress);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void test_executeTaskInternal() throws Exception {
    doNothing()
        .when(cfDeploymentManager)
        .runPcfPluginScript(any(CfRunPluginScriptRequestData.class), Mockito.eq(executionLogCallback));
    CfRunPluginCommandRequestNG pcfCommandRequest = getPcfRunPluginCommandRequest();
    char[] password = {'a'};
    char[] username = {'b'};
    CloudFoundryConfig cfConfig = CloudFoundryConfig.builder().userName(username).password(password).build();
    doReturn(cfConfig)
        .when(tasNgConfigMapper)
        .mapTasConfigWithDecryption(tasInfraConfig.getTasConnectorDTO(), tasInfraConfig.getEncryptionDataDetails());
    tasRunPluginCommandTaskHandler.executeTaskInternal(pcfCommandRequest, logStreamingTaskClient, commandUnitsProgress);

    tasTaskHelperBase.getLogCallback(
        logStreamingTaskClient, CfCommandUnitConstants.Pcfplugin, true, commandUnitsProgress);
    // verify
    ArgumentCaptor<CfRunPluginScriptRequestData> argumentCaptor =
        ArgumentCaptor.forClass(CfRunPluginScriptRequestData.class);
    verify(cfDeploymentManager).runPcfPluginScript(argumentCaptor.capture(), eq(executionLogCallback));

    final CfRunPluginScriptRequestData pcfRunPluginScriptRequestData = argumentCaptor.getValue();
    assertThat(pcfRunPluginScriptRequestData.getWorkingDirectory()).isNotNull();
    assertThat(pcfRunPluginScriptRequestData.getFinalScriptString())
        .isEqualTo("cf create-service " + pcfRunPluginScriptRequestData.getWorkingDirectory() + "/manifest.yml");
    assertThat(pcfRunPluginScriptRequestData.getCfRequestConfig()).isNotNull();
    assertThat(pcfRunPluginScriptRequestData.getCfRequestConfig().getCfHomeDirPath())
        .contains(PCF_ARTIFACT_DOWNLOAD_DIR_PATH);

    verify(tasRunPluginCommandTaskHandler, times(1))
        .saveFilesInWorkingDirectoryStringContent(anyList(), eq(pcfRunPluginScriptRequestData.getWorkingDirectory()));
  }

  private CfRunPluginCommandRequestNG getPcfRunPluginCommandRequest() {
    return CfRunPluginCommandRequestNG.builder()
        .renderedScriptString("cf create-service ${service.manifest.repoRoot}/manifest.yml")
        .encryptedDataDetails(null)
        .tasInfraConfig(tasInfraConfig)
        .timeoutIntervalInMin(5)
        .fileDataList(ImmutableList.of(FileData.builder()
                                           .filePath("manifest.yml")
                                           .fileBytes("file data ".getBytes(StandardCharsets.UTF_8))
                                           .build()))
        .filePathsInScript(ImmutableList.of("/manifest.yml"))
        .build();
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void test_handleError() {
    final TasRunPluginResponse commandExecutionResponse = tasRunPluginCommandTaskHandler.handleError(
        executionLogCallback, getPcfRunPluginCommandRequest(), new PivotalClientApiException(""));
    assertThat(commandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalInvalidArgumentsException() {
    try {
      tasRunPluginCommandTaskHandler.executeTaskInternal(
          CfBasicSetupRequestNG.builder().tasInfraConfig(tasInfraConfig).build(), logStreamingTaskClient,
          commandUnitsProgress);
    } catch (Exception e) {
      assertThatExceptionOfType(InvalidArgumentsException.class);
      InvalidArgumentsException invalidArgumentsException = (InvalidArgumentsException) e;
      assertThat(invalidArgumentsException.getParams())
          .containsValue("cfCommandRequestNG: Must be instance of CfRunPluginCommandRequestNG");
    }
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testPrepareFinalScriptWithRepoRoot() {
    String script = "/path-to-binary/v6/cf create-service-push --service-manifest\n"
        + "${service.manifest.repoRoot}/manifests";
    String workingDirPath = "working-dir-path";
    String finalScript = tasRunPluginCommandTaskHandler.prepareFinalScript(script, workingDirPath, StringUtils.EMPTY);
    assertThat(finalScript)
        .isEqualTo("/path-to-binary/v6/cf create-service-push --service-manifest\n"
            + "working-dir-path/manifests");
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testPrepareFinalScriptWithDeployYaml() {
    String script = "cf create-service-push --service-manifest\n"
        + "${service.manifest}/manifests/deploy.yml --no-push";
    String repoRoot = "root-path/rd";
    String finalScript = tasRunPluginCommandTaskHandler.prepareFinalScript(script, StringUtils.EMPTY, repoRoot);
    assertThat(finalScript)
        .isEqualTo("cf create-service-push --service-manifest\n"
            + "root-path/rd/manifests/deploy.yml --no-push");
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testPrepareFinalScript() {
    String script = "/path-to-binary/v7/cf create-service-push --service-manifest\n"
        + "${service.manifest.repoRoot}/manifests\n"
        + "${service.manifest}/manifests/deploy.yml --no-push";
    String workingDirPath = "working-dir-path/";
    String repoRoot = "root-path/rd";
    String finalScript = tasRunPluginCommandTaskHandler.prepareFinalScript(script, workingDirPath, repoRoot);
    assertThat(finalScript)
        .isEqualTo("/path-to-binary/v7/cf create-service-push --service-manifest\n"
            + "working-dir-path//manifests\n"
            + "working-dir-path/root-path/rd/manifests/deploy.yml --no-push");
  }
}
