/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.terragrunt;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.TATHAGAT;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cli.CliResponse;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;
import io.harness.terraform.TerraformClient;
import io.harness.terraform.beans.TerraformVersion;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.concurrent.TimeoutException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.zeroturnaround.exec.stream.LogOutputStream;

@OwnedBy(CDP)
public class TerragruntClientImplTest extends CategoryTest {
  @Mock private LogOutputStream mockedLogOutputStream;
  @Mock private LogCallback logCallback;
  @Mock private TerraformClient terraformClient;

  @InjectMocks private TerragruntClientImpl terragruntClient = Mockito.spy(TerragruntClientImpl.class);

  private static final String TARGET_ARGS = "targetArgs";
  private static final String UI_LOGS = "uiLogs";
  private static final String VAR_PARAMS = "varParams";

  private ArgumentCaptor<String> stringCaptor = ArgumentCaptor.forClass(String.class);
  private ArgumentCaptor<LogOutputStream> cliParamsCaptor = ArgumentCaptor.forClass(LogOutputStream.class);
  private TerragruntCliCommandRequestParams terragruntCliCommandRequestParams =
      TerragruntCliCommandRequestParams.builder()
          .commandUnitName("Apply")
          .backendConfigFilePath("backendConfigPath")
          .directory("rooDirectory")
          .timeoutInMillis(1000L)
          .envVars(new HashMap<>())
          .tfOutputsFile(new File("tfOutputFile"))
          .errorLogOutputStream(new ErrorLogOutputStream(logCallback))
          .planLogOutputStream(new PlanLogOutputStream(logCallback, emptyList()))
          .activityLogOutputStream(new ActivityLogOutputStream(logCallback))
          .build();

  @Before
  public void setup() throws InterruptedException, TimeoutException, IOException {
    MockitoAnnotations.initMocks(this);
    doReturn(CliResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build())
        .when(terragruntClient)
        .executeShellCommand(anyString(), anyString(), anyLong(), anyMap(), nullable(LogOutputStream.class),
            nullable(LogOutputStream.class));
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testInitWithBackendConfig() throws Exception {
    Files.createFile(Paths.get(terragruntCliCommandRequestParams.getBackendConfigFilePath()));
    terragruntClient.init(terragruntCliCommandRequestParams, logCallback);
    assertThat(getTerraguntCommandPassedToExecutor()).isEqualTo("terragrunt init -backend-config=backendConfigPath");
    Files.deleteIfExists(Paths.get(terragruntCliCommandRequestParams.getBackendConfigFilePath()));
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testInit() throws Exception {
    terragruntClient.init(terragruntCliCommandRequestParams, logCallback);
    assertThat(getTerraguntCommandPassedToExecutor()).isEqualTo("terragrunt init");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testWorkspace() throws Exception {
    terragruntClient.workspace(terragruntCliCommandRequestParams, "workspace", "workspaceCommand", logCallback);
    assertThat(getTerraguntCommandPassedToExecutor()).isEqualTo("terragrunt workspace workspace workspaceCommand");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testRunAllNewWorkspace() throws Exception {
    terragruntClient.runAllNewWorkspace(terragruntCliCommandRequestParams, "workspace", logCallback);
    assertThat(getTerraguntCommandPassedToExecutor()).isEqualTo("terragrunt run-all workspace new workspace");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testRunAllSelectWorkspace() throws Exception {
    terragruntClient.runAllSelectWorkspace(terragruntCliCommandRequestParams, "workspace", logCallback);
    assertThat(getTerraguntCommandPassedToExecutor()).isEqualTo("terragrunt run-all workspace select workspace");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testRefresh() throws Exception {
    terragruntClient.refresh(terragruntCliCommandRequestParams, TARGET_ARGS, VAR_PARAMS, UI_LOGS, logCallback);
    assertThat(getTerraguntCommandPassedToExecutor()).isEqualTo("terragrunt refresh -input=false targetArgs varParams");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testPlan() throws Exception {
    terragruntClient.plan(terragruntCliCommandRequestParams, TARGET_ARGS, VAR_PARAMS, UI_LOGS, logCallback);
    assertThat(getTerraguntCommandPassedToExecutor())
        .isEqualTo("terragrunt plan -out=tfplan -input=false targetArgs varParams");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testPlanDestroy() throws Exception {
    terragruntClient.planDestroy(terragruntCliCommandRequestParams, TARGET_ARGS, VAR_PARAMS, UI_LOGS, logCallback);
    assertThat(getTerraguntCommandPassedToExecutor())
        .isEqualTo("terragrunt plan -destroy -out=tfdestroyplan -input=false targetArgs varParams");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testDestroyForce() throws Exception {
    terragruntCliCommandRequestParams.setAutoApproveArgument("-force");
    terragruntClient.destroy(terragruntCliCommandRequestParams, TARGET_ARGS, VAR_PARAMS, UI_LOGS, logCallback);
    assertThat(getTerraguntCommandPassedToExecutor())
        .isEqualTo("terragrunt destroy -force --terragrunt-non-interactive targetArgs varParams");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testDestroyForceAutoApprove() throws Exception {
    terragruntCliCommandRequestParams.setAutoApproveArgument("-auto-approve");
    doReturn(TerraformVersion.create(0, 16, 3)).when(terraformClient).version(anyLong(), anyString());
    terragruntClient.destroy(terragruntCliCommandRequestParams, TARGET_ARGS, VAR_PARAMS, UI_LOGS, logCallback);
    assertThat(getTerraguntCommandPassedToExecutor())
        .isEqualTo("terragrunt destroy -auto-approve --terragrunt-non-interactive targetArgs varParams");

    doReturn(TerraformVersion.create(0, 0, 1)).when(terraformClient).version(anyLong(), anyString());
    assertThat(getTerraguntCommandPassedToExecutor())
        .isEqualTo("terragrunt destroy -auto-approve --terragrunt-non-interactive targetArgs varParams");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testRunAllplan() throws Exception {
    terragruntClient.runAllplan(terragruntCliCommandRequestParams, TARGET_ARGS, VAR_PARAMS, UI_LOGS, logCallback);
    assertThat(getTerraguntCommandPassedToExecutor())
        .isEqualTo("terragrunt run-all plan -out=tfplan -input=false targetArgs varParams");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testShowJson() throws Exception {
    terragruntClient.showJson(terragruntCliCommandRequestParams, "tfPlan", logCallback);
    assertThat(getTerraguntCommandPassedToExecutor()).isEqualTo("terragrunt show -json tfPlan");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testRunAllshowJson() throws Exception {
    terragruntClient.runAllshowJson(terragruntCliCommandRequestParams, "tfPlan", logCallback);
    assertThat(getTerraguntCommandPassedToExecutor()).isEqualTo("terragrunt run-all show -json tfPlan");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testApply() throws Exception {
    terragruntClient.apply(terragruntCliCommandRequestParams, logCallback);
    assertThat(getTerraguntCommandPassedToExecutor()).isEqualTo("terragrunt apply -input=false tfplan");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testApplyDestroyTfPlan() throws Exception {
    terragruntClient.applyDestroyTfPlan(terragruntCliCommandRequestParams, logCallback);
    assertThat(getTerraguntCommandPassedToExecutor()).isEqualTo("terragrunt apply -input=false tfdestroyplan");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testRunAllApply() throws Exception {
    terragruntClient.runAllApply(terragruntCliCommandRequestParams, TARGET_ARGS, VAR_PARAMS, UI_LOGS, logCallback);
    assertThat(getTerraguntCommandPassedToExecutor())
        .isEqualTo("terragrunt run-all apply -input=false --terragrunt-non-interactive targetArgs varParams");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testRunAllDestroyForce() throws Exception {
    terragruntCliCommandRequestParams.setAutoApproveArgument("-force");
    terragruntClient.runAllDestroy(terragruntCliCommandRequestParams, TARGET_ARGS, VAR_PARAMS, UI_LOGS, logCallback);
    assertThat(getTerraguntCommandPassedToExecutor())
        .isEqualTo("terragrunt run-all destroy -force --terragrunt-non-interactive targetArgs varParams");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testRunAllDestroyAutoApprove() throws Exception {
    terragruntCliCommandRequestParams.setAutoApproveArgument("-auto-approve");
    terragruntClient.runAllDestroy(terragruntCliCommandRequestParams, TARGET_ARGS, VAR_PARAMS, UI_LOGS, logCallback);
    assertThat(getTerraguntCommandPassedToExecutor())
        .isEqualTo("terragrunt run-all destroy -auto-approve --terragrunt-non-interactive targetArgs varParams");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testRunAllPlanDestroy() throws Exception {
    terragruntClient.runAllPlanDestroy(
        terragruntCliCommandRequestParams, TARGET_ARGS, VAR_PARAMS, UI_LOGS, logCallback);
    assertThat(getTerraguntCommandPassedToExecutor())
        .isEqualTo("terragrunt run-all plan -destroy -out=tfdestroyplan -input=false targetArgs varParams");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testRunAllInitWithBackendConfig() throws Exception {
    Files.createFile(Paths.get(terragruntCliCommandRequestParams.getBackendConfigFilePath()));
    terragruntClient.runAllInit(terragruntCliCommandRequestParams, logCallback);
    assertThat(getTerraguntCommandPassedToExecutor())
        .isEqualTo("terragrunt run-all init -backend-config=backendConfigPath");
    Files.deleteIfExists(Paths.get(terragruntCliCommandRequestParams.getBackendConfigFilePath()));
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testRunAllInit() throws Exception {
    terragruntClient.runAllInit(terragruntCliCommandRequestParams, logCallback);
    assertThat(getTerraguntCommandPassedToExecutor()).isEqualTo("terragrunt run-all init");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testRunAllRefresh() throws Exception {
    terragruntClient.runAllRefresh(terragruntCliCommandRequestParams, TARGET_ARGS, VAR_PARAMS, UI_LOGS, logCallback);
    assertThat(getTerraguntCommandPassedToExecutor())
        .isEqualTo("terragrunt run-all refresh -input=false targetArgs varParams");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testOutput() throws Exception {
    terragruntClient.output(terragruntCliCommandRequestParams, "tfOutputFilePath", logCallback);
    assertThat(getTerraguntCommandPassedToExecutor()).isEqualTo("terragrunt output --json > tfOutputFilePath");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testrunAllOutput() throws Exception {
    terragruntClient.runAllOutput(terragruntCliCommandRequestParams, logCallback);
    assertThat(getTerraguntCommandPassedToExecutor()).isEqualTo("terragrunt run-all output --json > tfOutputFile");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testTerragruntInfo() throws Exception {
    terragruntClient.terragruntInfo(terragruntCliCommandRequestParams, logCallback);
    assertThat(getTerraguntCommandPassedToExecutor()).isEqualTo("terragrunt terragrunt-info");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testWorkspaceList() throws Exception {
    terragruntClient.workspaceList("scriptDirectoty", 1000L);
    assertThat(getTerraguntCommandPassedToExecutor()).isEqualTo("terragrunt workspace list");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testVersion() throws Exception {
    terragruntClient.version(terragruntCliCommandRequestParams, logCallback);
    assertThat(getTerraguntCommandPassedToExecutor()).isEqualTo("terragrunt -version");
  }

  private String getTerraguntCommandPassedToExecutor() throws Exception {
    verify(terragruntClient, Mockito.atLeastOnce())
        .executeShellCommand(stringCaptor.capture(), anyString(), anyLong(), anyMap(), nullable(LogOutputStream.class),
            nullable(LogOutputStream.class));

    return stringCaptor.getValue();
  }
}
