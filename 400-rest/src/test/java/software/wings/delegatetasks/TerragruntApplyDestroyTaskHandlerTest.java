/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.rule.OwnerRule.TATHAGAT;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.cli.CliResponse;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.EncryptDecryptHelper;
import io.harness.security.encryption.EncryptedRecord;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.security.encryption.EncryptionConfig;
import io.harness.terragrunt.TerragruntCliCommandRequestParams;
import io.harness.terragrunt.TerragruntClient;

import software.wings.beans.KmsConfig;
import software.wings.beans.delegation.TerragruntProvisionParameters;

import com.google.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeoutException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(CDP)
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class TerragruntApplyDestroyTaskHandlerTest extends CategoryTest {
  @Mock private LogCallback logCallback;
  @Mock DelegateLogService delegateLogService;
  @Mock private TerragruntClient terragruntClient;
  @Mock private EncryptDecryptHelper encryptDecryptHelper;
  @InjectMocks @Inject TerragruntApplyDestroyTaskHandler applyDestroyTaskHandler;

  private static final String TARGET_ARGS = "-target=target1 -target=target2";
  private static final String VAR_PARAMS = " -var='k1=v1'  -var='k2=HarnessSecret:[k2]' ";
  private static final String TF_PLAN_NAME = "tfplan";
  private static final String UI_LOGS = "uiLogs";
  private static final String TF_CONFIG_FILE_DIRECTORY = "configFileDirectory";
  private static final String TF_OUTPUT_FILE_PATH = "tfOuputFilePath";
  private final File tfOutputFile = new File(TF_OUTPUT_FILE_PATH);
  private TerragruntCliCommandRequestParams cliCommandRequestParams = TerragruntCliCommandRequestParams.builder()
                                                                          .targetArgs(TARGET_ARGS)
                                                                          .varParams(VAR_PARAMS)
                                                                          .uiLogs(UI_LOGS)
                                                                          .tfOutputsFile(tfOutputFile)
                                                                          .build();

  @Before
  public void setUp() throws Exception {
    initMocks(this);
    CliResponse terragruntCliResponse =
        CliResponse.builder().commandExecutionStatus(SUCCESS).output("cli-output").build();
    doReturn(terragruntCliResponse)
        .when(terragruntClient)
        .plan(any(), nullable(String.class), nullable(String.class), nullable(String.class), any());
    doReturn(terragruntCliResponse)
        .when(terragruntClient)
        .planDestroy(any(), nullable(String.class), nullable(String.class), nullable(String.class), any());
    doReturn(terragruntCliResponse).when(terragruntClient).output(any(), nullable(String.class), any());
    doReturn(terragruntCliResponse).when(terragruntClient).apply(any(), any());
    doReturn("encryptedPlanContent".getBytes()).when(encryptDecryptHelper).getDecryptedContent(any(), any());
    doReturn(terragruntCliResponse).when(terragruntClient).showJson(any(), nullable(String.class), any());
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testExecuteApplyTaskCreateAndApplyPlan() throws InterruptedException, TimeoutException, IOException {
    TerragruntProvisionParameters provisionParameters = TerragruntProvisionParameters.builder().build();

    applyDestroyTaskHandler.executeApplyTask(
        provisionParameters, cliCommandRequestParams, delegateLogService, TF_PLAN_NAME, TF_CONFIG_FILE_DIRECTORY);

    verify(terragruntClient, times(1))
        .plan(eq(cliCommandRequestParams), eq(TARGET_ARGS), eq(VAR_PARAMS), eq(UI_LOGS), any(LogCallback.class));
    verify(terragruntClient, times(1)).apply(eq(cliCommandRequestParams), any(LogCallback.class));
    verify(terragruntClient, times(1))
        .output(eq(cliCommandRequestParams), eq(tfOutputFile.toString()), any(LogCallback.class));
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testExecuteApplyTaskInheritTfPlan() throws InterruptedException, TimeoutException, IOException {
    TerragruntProvisionParameters provisionParameters = TerragruntProvisionParameters.builder()
                                                            .encryptedTfPlan(EncryptedRecordData.builder().build())
                                                            .secretManagerConfig(KmsConfig.builder().build())
                                                            .build();
    applyDestroyTaskHandler.executeApplyTask(
        provisionParameters, cliCommandRequestParams, delegateLogService, TF_PLAN_NAME, TF_CONFIG_FILE_DIRECTORY);

    verify(terragruntClient, never()).plan(any(), any(), any(), any(), any());
    verify(encryptDecryptHelper, times(1)).getDecryptedContent(any(EncryptionConfig.class), any(EncryptedRecord.class));
    verify(terragruntClient, times(1)).apply(eq(cliCommandRequestParams), any(LogCallback.class));
    verify(terragruntClient, times(1))
        .output(eq(cliCommandRequestParams), eq(tfOutputFile.toString()), any(LogCallback.class));
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testExecuteApplyTaskSaveTfPlanJson() throws InterruptedException, TimeoutException, IOException {
    TerragruntProvisionParameters provisionParameters =
        TerragruntProvisionParameters.builder().saveTerragruntJson(true).build();
    applyDestroyTaskHandler.executeApplyTask(
        provisionParameters, cliCommandRequestParams, delegateLogService, TF_PLAN_NAME, TF_CONFIG_FILE_DIRECTORY);

    verify(terragruntClient, times(1))
        .plan(eq(cliCommandRequestParams), eq(TARGET_ARGS), eq(VAR_PARAMS), eq(UI_LOGS), any(LogCallback.class));
    verify(terragruntClient, times(1)).apply(eq(cliCommandRequestParams), any(LogCallback.class));
    verify(terragruntClient, times(1))
        .output(eq(cliCommandRequestParams), eq(tfOutputFile.toString()), any(LogCallback.class));
    verify(terragruntClient, times(1)).showJson(eq(cliCommandRequestParams), eq(TF_PLAN_NAME), any(LogCallback.class));
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testExecuteDestroyTaskRunPlanOnly() throws InterruptedException, TimeoutException, IOException {
    TerragruntProvisionParameters provisionParameters =
        TerragruntProvisionParameters.builder().runPlanOnly(true).build();
    applyDestroyTaskHandler.executeDestroyTask(
        provisionParameters, cliCommandRequestParams, delegateLogService, TF_PLAN_NAME, TF_CONFIG_FILE_DIRECTORY);

    verify(terragruntClient, times(1))
        .planDestroy(eq(cliCommandRequestParams), eq(TARGET_ARGS), eq(VAR_PARAMS), eq(UI_LOGS), any(LogCallback.class));
    verify(terragruntClient, never()).showJson(any(), any(), any());
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testExecuteDestroyTaskRunDestroy() throws InterruptedException, TimeoutException, IOException {
    TerragruntProvisionParameters provisionParameters =
        TerragruntProvisionParameters.builder().runPlanOnly(false).build();
    doReturn(CliResponse.builder().commandExecutionStatus(SUCCESS).build())
        .when(terragruntClient)
        .destroy(any(TerragruntCliCommandRequestParams.class), nullable(String.class), nullable(String.class),
            nullable(String.class), any(LogCallback.class));
    applyDestroyTaskHandler.executeDestroyTask(
        provisionParameters, cliCommandRequestParams, delegateLogService, TF_PLAN_NAME, TF_CONFIG_FILE_DIRECTORY);

    verify(terragruntClient, times(1))
        .destroy(eq(cliCommandRequestParams), eq(TARGET_ARGS), eq(VAR_PARAMS), eq(UI_LOGS), any(LogCallback.class));
    verify(terragruntClient, never()).planDestroy(any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testExecuteDestroyTaskInheritTfDestroyPlan() throws InterruptedException, TimeoutException, IOException {
    TerragruntProvisionParameters provisionParameters = TerragruntProvisionParameters.builder()
                                                            .runPlanOnly(false)
                                                            .encryptedTfPlan(EncryptedRecordData.builder().build())
                                                            .build();
    doReturn(CliResponse.builder().commandExecutionStatus(SUCCESS).build())
        .when(terragruntClient)
        .applyDestroyTfPlan(any(), any());
    applyDestroyTaskHandler.executeDestroyTask(
        provisionParameters, cliCommandRequestParams, delegateLogService, TF_PLAN_NAME, TF_CONFIG_FILE_DIRECTORY);

    verify(terragruntClient, times(1)).applyDestroyTfPlan(eq(cliCommandRequestParams), any(LogCallback.class));
    verify(encryptDecryptHelper, times(1)).getDecryptedContent(any(), any());
    verify(terragruntClient, never()).planDestroy(any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testExecuteApplyTaskPlanCommandFail() throws InterruptedException, TimeoutException, IOException {
    TerragruntProvisionParameters provisionParameters = TerragruntProvisionParameters.builder().build();
    doReturn(CliResponse.builder().commandExecutionStatus(FAILURE).build())
        .when(terragruntClient)
        .plan(any(TerragruntCliCommandRequestParams.class), nullable(String.class), nullable(String.class),
            nullable(String.class), any());

    applyDestroyTaskHandler.executeApplyTask(
        provisionParameters, cliCommandRequestParams, delegateLogService, TF_PLAN_NAME, TF_CONFIG_FILE_DIRECTORY);

    verify(terragruntClient, times(1))
        .plan(eq(cliCommandRequestParams), eq(TARGET_ARGS), eq(VAR_PARAMS), eq(UI_LOGS), any(LogCallback.class));
    verify(terragruntClient, times(0)).apply(eq(cliCommandRequestParams), any(LogCallback.class));
    verify(terragruntClient, times(0))
        .output(eq(cliCommandRequestParams), eq(tfOutputFile.toString()), any(LogCallback.class));
  }
}
