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

import static software.wings.beans.delegation.TerragruntProvisionParameters.TerragruntCommand.APPLY;
import static software.wings.beans.delegation.TerragruntProvisionParameters.TerragruntCommand.DESTROY;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import io.harness.terragrunt.TerragruntCliCommandRequestParams;
import io.harness.terragrunt.TerragruntClient;

import software.wings.beans.delegation.TerragruntProvisionParameters;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.concurrent.TimeoutException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(CDP)
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class TerragruntRunAllTaskHandlerTest extends CategoryTest {
  @Mock LogCallback logCallback;
  @Mock DelegateLogService delegateLogService;
  @Mock private TerragruntClient terragruntClient;
  @Mock private TerragruntProvisionTaskHelper provisionTaskHelper;
  @InjectMocks @Inject TerragruntRunAllTaskHandler runAllTaskHandler;

  private static final String TARGET_ARGS = "-target=target1 -target=target2";
  private static final String VAR_PARAMS = " -var='k1=v1'  -var='k2=HarnessSecret:[k2]' ";
  private static final String UI_LOGS = "uiLogs";
  private static final String WORKSPACE = "workspace";
  private static final String TF_PLAN_NAME = "tfplan";
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
        .runAllInit(any(TerragruntCliCommandRequestParams.class), any());
    doReturn(terragruntCliResponse).when(terragruntClient).version(any(TerragruntCliCommandRequestParams.class), any());
    doReturn(terragruntCliResponse)
        .when(terragruntClient)
        .runAllRefresh(any(TerragruntCliCommandRequestParams.class), anyString(), anyString(), anyString(), any());
    doReturn(terragruntCliResponse)
        .when(terragruntClient)
        .runAllNewWorkspace(any(TerragruntCliCommandRequestParams.class), anyString(), any());
    doReturn(terragruntCliResponse)
        .when(terragruntClient)
        .runAllshowJson(any(TerragruntCliCommandRequestParams.class), anyString(), any());
    doReturn(terragruntCliResponse)
        .when(terragruntClient)
        .runAllSelectWorkspace(any(TerragruntCliCommandRequestParams.class), anyString(), any());
    doReturn(terragruntCliResponse)
        .when(terragruntClient)
        .runAllplan(any(TerragruntCliCommandRequestParams.class), anyString(), anyString(), anyString(), any());
    doReturn(terragruntCliResponse)
        .when(terragruntClient)
        .runAllPlanDestroy(any(TerragruntCliCommandRequestParams.class), anyString(), anyString(), anyString(), any());
    URL url = this.getClass().getResource("/terragrunt/terragrunt-info.json");
    String terragruntInfoJson = Resources.toString(url, Charsets.UTF_8);
    doReturn(CliResponse.builder().commandExecutionStatus(SUCCESS).output(terragruntInfoJson).build())
        .when(terragruntClient)
        .terragruntInfo(any(), any());
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testExecuteRunAllPlanOnlyTask() throws InterruptedException, TimeoutException, IOException {
    TerragruntProvisionParameters provisionParameters =
        TerragruntProvisionParameters.builder().command(APPLY).runPlanOnly(true).build();
    runAllTaskHandler.executeRunAllTask(provisionParameters, cliCommandRequestParams, delegateLogService, APPLY);

    verify(terragruntClient, times(1))
        .runAllplan(eq(cliCommandRequestParams), eq(TARGET_ARGS), eq(VAR_PARAMS), eq(UI_LOGS), any(LogCallback.class));
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testExecuteRunAllTaskWithNewWorkspace() throws InterruptedException, TimeoutException, IOException {
    TerragruntProvisionParameters provisionParameters =
        TerragruntProvisionParameters.builder().command(APPLY).runPlanOnly(true).workspace(WORKSPACE).build();
    doReturn(CliResponse.builder().commandExecutionStatus(SUCCESS).build())
        .when(terragruntClient)
        .runAllNewWorkspace(any(TerragruntCliCommandRequestParams.class), anyString(), any());
    runAllTaskHandler.executeRunAllTask(provisionParameters, cliCommandRequestParams, delegateLogService, APPLY);

    ArgumentCaptor<String> workspaceLogCaptor = ArgumentCaptor.forClass(String.class);
    verify(terragruntClient, times(1))
        .runAllNewWorkspace(eq(cliCommandRequestParams), eq(WORKSPACE), any(LogCallback.class));
    verify(terragruntClient, never())
        .runAllSelectWorkspace(eq(cliCommandRequestParams), eq(WORKSPACE), any(LogCallback.class));
    verify(terragruntClient, times(1))
        .runAllplan(eq(cliCommandRequestParams), eq(TARGET_ARGS), eq(VAR_PARAMS), eq(UI_LOGS), any(LogCallback.class));
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testExecuteRunAllTaskWithSelectExistingWorkspace()
      throws InterruptedException, TimeoutException, IOException {
    TerragruntProvisionParameters provisionParameters =
        TerragruntProvisionParameters.builder().command(APPLY).runPlanOnly(true).workspace(WORKSPACE).build();
    doReturn(CliResponse.builder().commandExecutionStatus(FAILURE).build())
        .when(terragruntClient)
        .runAllNewWorkspace(any(TerragruntCliCommandRequestParams.class), anyString(), any());

    runAllTaskHandler.executeRunAllTask(provisionParameters, cliCommandRequestParams, delegateLogService, APPLY);

    ArgumentCaptor<String> workspaceLogCaptor = ArgumentCaptor.forClass(String.class);
    verify(terragruntClient, times(1))
        .runAllNewWorkspace(eq(cliCommandRequestParams), eq(WORKSPACE), any(LogCallback.class));
    verify(terragruntClient, times(1))
        .runAllSelectWorkspace(eq(cliCommandRequestParams), eq(WORKSPACE), any(LogCallback.class));
    verify(terragruntClient, times(1))
        .runAllplan(eq(cliCommandRequestParams), eq(TARGET_ARGS), eq(VAR_PARAMS), eq(UI_LOGS), any(LogCallback.class));
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testExecuteRunAllTaskWorkspaceCommandFail() throws InterruptedException, TimeoutException, IOException {
    TerragruntProvisionParameters provisionParameters =
        TerragruntProvisionParameters.builder().command(APPLY).runPlanOnly(true).workspace(WORKSPACE).build();
    doReturn(CliResponse.builder().commandExecutionStatus(FAILURE).build())
        .when(terragruntClient)
        .runAllSelectWorkspace(any(TerragruntCliCommandRequestParams.class), anyString(), any());
    doReturn(CliResponse.builder().commandExecutionStatus(FAILURE).build())
        .when(terragruntClient)
        .runAllNewWorkspace(any(TerragruntCliCommandRequestParams.class), anyString(), any());

    runAllTaskHandler.executeRunAllTask(provisionParameters, cliCommandRequestParams, delegateLogService, APPLY);

    ArgumentCaptor<String> workspaceLogCaptor = ArgumentCaptor.forClass(String.class);
    verify(terragruntClient, times(1))
        .runAllNewWorkspace(eq(cliCommandRequestParams), eq(WORKSPACE), any(LogCallback.class));
    verify(terragruntClient, times(1))
        .runAllSelectWorkspace(eq(cliCommandRequestParams), eq(WORKSPACE), any(LogCallback.class));
    verify(terragruntClient, never())
        .runAllplan(eq(cliCommandRequestParams), eq(TARGET_ARGS), eq(VAR_PARAMS), eq(UI_LOGS), any(LogCallback.class));
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testExecuteRunAllPlanOnlyTaskSaveTfPlanJson() throws InterruptedException, TimeoutException, IOException {
    TerragruntProvisionParameters provisionParameters =
        TerragruntProvisionParameters.builder().command(APPLY).runPlanOnly(true).saveTerragruntJson(true).build();

    runAllTaskHandler.executeRunAllTask(provisionParameters, cliCommandRequestParams, delegateLogService, APPLY);

    verify(terragruntClient, times(1))
        .runAllplan(eq(cliCommandRequestParams), eq(TARGET_ARGS), eq(VAR_PARAMS), eq(UI_LOGS), any(LogCallback.class));
    verify(terragruntClient, times(1))
        .runAllshowJson(eq(cliCommandRequestParams), eq(TF_PLAN_NAME), any(LogCallback.class));
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testExecuteRunAllApplyTask() throws InterruptedException, TimeoutException, IOException {
    TerragruntProvisionParameters provisionParameters =
        TerragruntProvisionParameters.builder().command(APPLY).runPlanOnly(false).saveTerragruntJson(true).build();
    doReturn(CliResponse.builder().commandExecutionStatus(SUCCESS).build())
        .when(terragruntClient)
        .runAllApply(any(TerragruntCliCommandRequestParams.class), anyString(), anyString(), anyString(), any());
    doReturn(CliResponse.builder().commandExecutionStatus(SUCCESS).build())
        .when(terragruntClient)
        .runAllOutput(any(TerragruntCliCommandRequestParams.class), any());

    runAllTaskHandler.executeRunAllTask(provisionParameters, cliCommandRequestParams, delegateLogService, APPLY);

    verify(terragruntClient, times(1))
        .runAllApply(eq(cliCommandRequestParams), eq(TARGET_ARGS), eq(VAR_PARAMS), eq(UI_LOGS), any(LogCallback.class));
    verify(terragruntClient, times(1)).runAllOutput(eq(cliCommandRequestParams), any(LogCallback.class));
    verify(terragruntClient, never()).runAllshowJson(any(), any(), any());
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testExecuteRunAllDestroyPlanOnlyTask() throws InterruptedException, TimeoutException, IOException {
    TerragruntProvisionParameters provisionParameters =
        TerragruntProvisionParameters.builder().command(DESTROY).runPlanOnly(true).build();
    runAllTaskHandler.executeRunAllTask(provisionParameters, cliCommandRequestParams, delegateLogService, APPLY);

    verify(terragruntClient, times(1))
        .runAllPlanDestroy(
            eq(cliCommandRequestParams), eq(TARGET_ARGS), eq(VAR_PARAMS), eq(UI_LOGS), any(LogCallback.class));
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testExecuteRunAllDestroyPlanOnlyTaskSaveTfPlanJson()
      throws InterruptedException, TimeoutException, IOException {
    TerragruntProvisionParameters provisionParameters =
        TerragruntProvisionParameters.builder().command(DESTROY).runPlanOnly(true).saveTerragruntJson(true).build();

    runAllTaskHandler.executeRunAllTask(provisionParameters, cliCommandRequestParams, delegateLogService, APPLY);

    verify(terragruntClient, times(1))
        .runAllPlanDestroy(
            eq(cliCommandRequestParams), eq(TARGET_ARGS), eq(VAR_PARAMS), eq(UI_LOGS), any(LogCallback.class));
    verify(terragruntClient, times(1))
        .runAllshowJson(eq(cliCommandRequestParams), eq(TF_PLAN_NAME), any(LogCallback.class));
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testExecuteRunAllDestroyTask() throws InterruptedException, TimeoutException, IOException {
    TerragruntProvisionParameters provisionParameters =
        TerragruntProvisionParameters.builder().command(DESTROY).runPlanOnly(false).saveTerragruntJson(true).build();
    doReturn(CliResponse.builder().commandExecutionStatus(SUCCESS).build())
        .when(terragruntClient)
        .runAllDestroy(any(TerragruntCliCommandRequestParams.class), anyString(), anyString(), anyString(), any());
    doReturn("-force")
        .when(provisionTaskHelper)
        .getTfAutoApproveArgument(any(TerragruntCliCommandRequestParams.class), eq("terraform"));

    ArgumentCaptor<TerragruntCliCommandRequestParams> cliParamsCaptor =
        ArgumentCaptor.forClass(TerragruntCliCommandRequestParams.class);
    cliCommandRequestParams.setUseAutoApproveFlag(true);

    runAllTaskHandler.executeRunAllTask(provisionParameters, cliCommandRequestParams, delegateLogService, APPLY);

    verify(terragruntClient, times(1))
        .terragruntInfo(any(TerragruntCliCommandRequestParams.class), any(LogCallback.class));
    verify(terragruntClient, never()).runAllOutput(any(), any());
    verify(terragruntClient, times(1))
        .runAllDestroy(cliParamsCaptor.capture(), eq(TARGET_ARGS), eq(VAR_PARAMS), eq(UI_LOGS), any(LogCallback.class));
    assertThat(cliParamsCaptor.getValue().getAutoApproveArgument()).isEqualTo("-force");
  }
}
