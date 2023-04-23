/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.terraform;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.provision.TerraformConstants.TERRAFORM_PLAN_FILE_OUTPUT_NAME;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.ARVIND;
import static io.harness.rule.OwnerRule.BOGDAN;
import static io.harness.rule.OwnerRule.ROHITKARELIA;
import static io.harness.rule.OwnerRule.VLICA;
import static io.harness.terraform.TerraformConstants.DEFAULT_TERRAFORM_COMMAND_TIMEOUT;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalMatchers.and;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cli.CliHelper;
import io.harness.cli.CliResponse;
import io.harness.exception.runtime.TerraformCliRuntimeException;
import io.harness.filesystem.FileIo;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.logging.NoopExecutionCallback;
import io.harness.logging.PlanJsonLogOutputStream;
import io.harness.rule.Owner;
import io.harness.terraform.beans.TerraformVersion;
import io.harness.terraform.request.TerraformApplyCommandRequest;
import io.harness.terraform.request.TerraformDestroyCommandRequest;
import io.harness.terraform.request.TerraformInitCommandRequest;
import io.harness.terraform.request.TerraformPlanCommandRequest;
import io.harness.terraform.request.TerraformRefreshCommandRequest;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.TimeoutException;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.zeroturnaround.exec.stream.LogOutputStream;

@OwnedBy(CDP)
public class TerraformClientImplTest extends CategoryTest {
  @Mock private CliHelper cliHelper;
  @Mock private LogCallback logCallback;
  @Mock private PlanJsonLogOutputStream planJsonLogOutputStream;
  @InjectMocks private TerraformClientImpl terraformClientImpl;

  private static final String SCRIPT_FILES_DIRECTORY = "SCRIPT_FILES_DIRECTORY";

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
    FileIo.createDirectoryIfDoesNotExist(SCRIPT_FILES_DIRECTORY);
  }

  @AfterClass
  public static void afterClass() throws IOException {
    FileIo.deleteDirectoryAndItsContentIfExists(SCRIPT_FILES_DIRECTORY);
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testInitCommand() throws InterruptedException, IOException, TimeoutException {
    CliResponse cliResponse = getCliResponse();
    TerraformInitCommandRequest terraformInitCommandRequest =
        TerraformInitCommandRequest.builder()
            .tfBackendConfigsFilePath("/tmp/terraform/backendconfig.tf")
            .additionalCliFlags(new HashMap<>() {
              { put("INIT", "-lock-timeout=5s"); }
            })
            .build();

    String command = format("terraform init -input=false -backend-config=%s -lock-timeout=5s",
        terraformInitCommandRequest.getTfBackendConfigsFilePath());
    doReturn(cliResponse)
        .when(cliHelper)
        .executeCliCommand(eq(format("echo \"no\" | %s", command)), eq(DEFAULT_TERRAFORM_COMMAND_TIMEOUT),
            eq(Collections.emptyMap()), eq(SCRIPT_FILES_DIRECTORY), eq(logCallback), eq(command), any(), any(),
            anyLong());

    CliResponse actualResponse = terraformClientImpl.init(terraformInitCommandRequest,
        DEFAULT_TERRAFORM_COMMAND_TIMEOUT, Collections.emptyMap(), SCRIPT_FILES_DIRECTORY, logCallback);

    assertThat(actualResponse).isEqualTo(cliResponse);
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testDestroyCommandUsingTf012() throws InterruptedException, IOException, TimeoutException {
    testDestroyCommandUsingVersion(TerraformVersion.create(0, 12, 3));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testDestroyCommandUsingTf015() throws InterruptedException, IOException, TimeoutException {
    testDestroyCommandUsingVersion(TerraformVersion.create(0, 15, 3));
  }

  private void testDestroyCommandUsingVersion(TerraformVersion version)
      throws InterruptedException, IOException, TimeoutException {
    CliResponse cliResponse = getCliResponse();
    TerraformDestroyCommandRequest terraformDestroyCommandRequest =
        TerraformDestroyCommandRequest.builder()
            .targets(Arrays.asList("10.0.10.1", "10.0.10.2"))
            .varFilePaths(Arrays.asList("variableParams"))
            .additionalCliFlags(new HashMap<>() {
              { put("DESTROY", "-lock-timeout=5s"); }
            })
            .build();
    String command = format("terraform destroy %s %s %s %s", TerraformHelperUtils.getAutoApproveArgument(version),
        TerraformHelperUtils.generateCommandFlagsString(terraformDestroyCommandRequest.getTargets(), "-target="),
        TerraformHelperUtils.generateCommandFlagsString(terraformDestroyCommandRequest.getVarFilePaths(), "-var-file="),
        "-lock-timeout=5s");
    doReturn(cliResponse)
        .when(cliHelper)
        .executeCliCommand(eq(command), eq(DEFAULT_TERRAFORM_COMMAND_TIMEOUT), eq(Collections.emptyMap()),
            eq(SCRIPT_FILES_DIRECTORY), eq(logCallback), eq(command), any(), any(), anyLong());

    doReturn(getCliResponseTfVersion(version.getMajor(), version.getMinor(), version.getPatch()))
        .when(cliHelper)
        .executeCliCommand(eq("terraform version"), eq(DEFAULT_TERRAFORM_COMMAND_TIMEOUT), eq(Collections.emptyMap()),
            eq(SCRIPT_FILES_DIRECTORY), any(LogCallback.class));

    CliResponse actualResponse = terraformClientImpl.destroy(terraformDestroyCommandRequest,
        DEFAULT_TERRAFORM_COMMAND_TIMEOUT, Collections.emptyMap(), SCRIPT_FILES_DIRECTORY, logCallback);

    assertThat(actualResponse).isEqualTo(cliResponse);
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testDestroyCommandUsingTf012AndTfCloudCli() throws InterruptedException, IOException, TimeoutException {
    testDestroyCommandUsingVersionAndTerraformCloudCLI(TerraformVersion.create(0, 12, 3));
  }

  private void testDestroyCommandUsingVersionAndTerraformCloudCLI(TerraformVersion version)
      throws InterruptedException, IOException, TimeoutException {
    CliResponse cliResponse = getCliResponse();
    TerraformDestroyCommandRequest terraformDestroyCommandRequest =
        TerraformDestroyCommandRequest.builder()
            .targets(Arrays.asList("10.0.10.1", "10.0.10.2"))
            .varFilePaths(Arrays.asList("variableParams"))
            .isTerraformCloudCli(true)
            .additionalCliFlags(new HashMap<>() {
              { put("DESTROY", "-lock-timeout=5s"); }
            })
            .build();
    String command =
        format("echo yes | terraform destroy %s %s %s", TerraformHelperUtils.getAutoApproveArgument(version),
            TerraformHelperUtils.generateCommandFlagsString(terraformDestroyCommandRequest.getTargets(), "-target="),
            "-lock-timeout=5s");
    doReturn(cliResponse)
        .when(cliHelper)
        .executeCliCommand(eq(command), eq(DEFAULT_TERRAFORM_COMMAND_TIMEOUT), eq(Collections.emptyMap()),
            eq(SCRIPT_FILES_DIRECTORY), eq(logCallback), eq(command), any(), any(), anyLong());

    doReturn(getCliResponseTfVersion(version.getMajor(), version.getMinor(), version.getPatch()))
        .when(cliHelper)
        .executeCliCommand(eq("terraform version"), eq(DEFAULT_TERRAFORM_COMMAND_TIMEOUT), eq(Collections.emptyMap()),
            eq(SCRIPT_FILES_DIRECTORY), any(LogCallback.class));

    CliResponse actualResponse = terraformClientImpl.destroy(terraformDestroyCommandRequest,
        DEFAULT_TERRAFORM_COMMAND_TIMEOUT, Collections.emptyMap(), SCRIPT_FILES_DIRECTORY, logCallback);

    assertThat(actualResponse).isEqualTo(cliResponse);
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testPlanCommandWithDestroy() throws InterruptedException, IOException, TimeoutException {
    CliResponse cliResponse = getCliResponse();
    TerraformPlanCommandRequest terraformPlanCommandRequest = TerraformPlanCommandRequest.builder()
                                                                  .destroySet(true)
                                                                  .additionalCliFlags(new HashMap<>() {
                                                                    { put("PLAN", "-lock-timeout=5s"); }
                                                                  })
                                                                  .build();

    String command = format("terraform plan -input=false -detailed-exitcode -destroy -out=tfdestroyplan %s %s %s",
        TerraformHelperUtils.generateCommandFlagsString(terraformPlanCommandRequest.getTargets(), "-target="),
        TerraformHelperUtils.generateCommandFlagsString(terraformPlanCommandRequest.getVarFilePaths(), "-var-file="),
        "-lock-timeout=5s");
    doReturn(cliResponse)
        .when(cliHelper)
        .executeCliCommand(eq(command), eq(DEFAULT_TERRAFORM_COMMAND_TIMEOUT), eq(Collections.emptyMap()),
            eq(SCRIPT_FILES_DIRECTORY), eq(logCallback), eq(command), any(), any(), anyLong());

    CliResponse actualResponse = terraformClientImpl.plan(terraformPlanCommandRequest,
        DEFAULT_TERRAFORM_COMMAND_TIMEOUT, Collections.emptyMap(), SCRIPT_FILES_DIRECTORY, logCallback);

    assertThat(actualResponse).isEqualTo(cliResponse);
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testPlanCommandWithDestroyAndTerraformCloudCli()
      throws InterruptedException, IOException, TimeoutException {
    CliResponse cliResponse = getCliResponse();
    TerraformPlanCommandRequest terraformPlanCommandRequest =
        TerraformPlanCommandRequest.builder().destroySet(true).isTerraformCloudCli(true).build();

    String command = format("terraform plan -input=false -detailed-exitcode -destroy %s ",
        TerraformHelperUtils.generateCommandFlagsString(terraformPlanCommandRequest.getTargets(), "-target="));
    doReturn(cliResponse)
        .when(cliHelper)
        .executeCliCommand(eq(command), eq(DEFAULT_TERRAFORM_COMMAND_TIMEOUT), eq(Collections.emptyMap()),
            eq(SCRIPT_FILES_DIRECTORY), eq(logCallback), eq(command), any(), any(), anyLong());

    CliResponse actualResponse = terraformClientImpl.plan(terraformPlanCommandRequest,
        DEFAULT_TERRAFORM_COMMAND_TIMEOUT, Collections.emptyMap(), SCRIPT_FILES_DIRECTORY, logCallback);

    assertThat(actualResponse).isEqualTo(cliResponse);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testPlanCommand() throws InterruptedException, IOException, TimeoutException {
    CliResponse cliResponse = getCliResponseWithExitCode(2);
    String varParams = "-compact-warnings";
    TerraformPlanCommandRequest terraformPlanCommandRequest =
        TerraformPlanCommandRequest.builder().varParams(varParams).build();

    String command = format("terraform plan -input=false -detailed-exitcode -out=tfplan %s %s",
        TerraformHelperUtils.generateCommandFlagsString(terraformPlanCommandRequest.getTargets(), "-target="),
        TerraformHelperUtils.generateCommandFlagsString(terraformPlanCommandRequest.getVarFilePaths(), "-var-file="));
    doReturn(cliResponse)
        .when(cliHelper)
        .executeCliCommand(and(contains(command), contains(varParams)), eq(DEFAULT_TERRAFORM_COMMAND_TIMEOUT),
            eq(Collections.emptyMap()), eq(SCRIPT_FILES_DIRECTORY), eq(logCallback), contains(command), any(), any(),
            anyLong());

    CliResponse actualResponse = terraformClientImpl.plan(terraformPlanCommandRequest,
        DEFAULT_TERRAFORM_COMMAND_TIMEOUT, Collections.emptyMap(), SCRIPT_FILES_DIRECTORY, logCallback);
    assertThat(actualResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(actualResponse.getExitCode()).isEqualTo(2);

    cliResponse.setExitCode(0);
    actualResponse = terraformClientImpl.plan(terraformPlanCommandRequest, DEFAULT_TERRAFORM_COMMAND_TIMEOUT,
        Collections.emptyMap(), SCRIPT_FILES_DIRECTORY, logCallback);
    assertThat(actualResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(actualResponse.getExitCode()).isEqualTo(0);
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testPlanCommandAndTerraformCloudCli() throws InterruptedException, IOException, TimeoutException {
    CliResponse cliResponse = getCliResponseWithExitCode(2);
    String varParams = "-compact-warnings";
    TerraformPlanCommandRequest terraformPlanCommandRequest =
        TerraformPlanCommandRequest.builder().varParams(varParams).isTerraformCloudCli(true).build();

    String command = format("terraform plan -input=false -detailed-exitcode %s",
        TerraformHelperUtils.generateCommandFlagsString(terraformPlanCommandRequest.getTargets(), "-target="));
    doReturn(cliResponse)
        .when(cliHelper)
        .executeCliCommand(and(contains(command), contains(varParams)), eq(DEFAULT_TERRAFORM_COMMAND_TIMEOUT),
            eq(Collections.emptyMap()), eq(SCRIPT_FILES_DIRECTORY), eq(logCallback), contains(command), any(), any(),
            anyLong());

    CliResponse actualResponse = terraformClientImpl.plan(terraformPlanCommandRequest,
        DEFAULT_TERRAFORM_COMMAND_TIMEOUT, Collections.emptyMap(), SCRIPT_FILES_DIRECTORY, logCallback);
    assertThat(actualResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(actualResponse.getExitCode()).isEqualTo(2);

    cliResponse.setExitCode(0);
    actualResponse = terraformClientImpl.plan(terraformPlanCommandRequest, DEFAULT_TERRAFORM_COMMAND_TIMEOUT,
        Collections.emptyMap(), SCRIPT_FILES_DIRECTORY, logCallback);
    assertThat(actualResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(actualResponse.getExitCode()).isEqualTo(0);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testPlanCommandWithFailure() throws InterruptedException, IOException, TimeoutException {
    CliResponse cliResponse = getCliResponseWithExitCode(1);
    String varParams = "-compact-warnings";
    TerraformPlanCommandRequest terraformPlanCommandRequest =
        TerraformPlanCommandRequest.builder().varParams(varParams).build();

    String command = format("terraform plan -input=false -detailed-exitcode -out=tfplan %s %s",
        TerraformHelperUtils.generateCommandFlagsString(terraformPlanCommandRequest.getTargets(), "-target="),
        TerraformHelperUtils.generateCommandFlagsString(terraformPlanCommandRequest.getVarFilePaths(), "-var-file="));
    doReturn(cliResponse)
        .when(cliHelper)
        .executeCliCommand(and(contains(command), contains(varParams)), eq(DEFAULT_TERRAFORM_COMMAND_TIMEOUT),
            eq(Collections.emptyMap()), eq(SCRIPT_FILES_DIRECTORY), eq(logCallback), contains(command), any(), any(),
            anyLong());

    assertThatThrownBy(()
                           -> terraformClientImpl.plan(terraformPlanCommandRequest, DEFAULT_TERRAFORM_COMMAND_TIMEOUT,
                               Collections.emptyMap(), SCRIPT_FILES_DIRECTORY, logCallback))
        .isInstanceOf(TerraformCliRuntimeException.class);
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testRefreshCommand() throws InterruptedException, IOException, TimeoutException {
    CliResponse cliResponse = getCliResponse();
    TerraformRefreshCommandRequest terraformRefreshCommandRequest = TerraformRefreshCommandRequest.builder()
                                                                        .additionalCliFlags(new HashMap<>() {
                                                                          { put("REFRESH", "-lock-timeout=5s"); }
                                                                        })
                                                                        .build();
    String command = "terraform refresh -input=false "
        + TerraformHelperUtils.generateCommandFlagsString(terraformRefreshCommandRequest.getTargets(), "-target=")
        + TerraformHelperUtils.generateCommandFlagsString(
            terraformRefreshCommandRequest.getVarFilePaths(), "-var-file=")
        + "-lock-timeout=5s";
    doReturn(cliResponse)
        .when(cliHelper)
        .executeCliCommand(eq(command), eq(DEFAULT_TERRAFORM_COMMAND_TIMEOUT), eq(Collections.emptyMap()),
            eq(SCRIPT_FILES_DIRECTORY), eq(logCallback), eq(command), any(), any(), anyLong());

    CliResponse actualResponse = terraformClientImpl.refresh(terraformRefreshCommandRequest,
        DEFAULT_TERRAFORM_COMMAND_TIMEOUT, Collections.emptyMap(), SCRIPT_FILES_DIRECTORY, logCallback);

    assertThat(actualResponse).isEqualTo(cliResponse);
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testRefreshCommandWithVarParams() throws InterruptedException, IOException, TimeoutException {
    CliResponse cliResponse = getCliResponse();
    TerraformRefreshCommandRequest terraformRefreshCommandRequest =
        TerraformRefreshCommandRequest.builder()
            .targets(Arrays.asList("127.0.0.1"))
            .varFilePaths(Arrays.asList("file1.txt"))
            .varParams("-var='instance_name=tf-instance'")
            .uiLogs("-var='instance_name=HarnessSecret:[instance_name]")
            .build();
    String command = "terraform refresh -input=false "
        + TerraformHelperUtils.generateCommandFlagsString(terraformRefreshCommandRequest.getTargets(), "-target=")
        + TerraformHelperUtils.generateCommandFlagsString(
            terraformRefreshCommandRequest.getVarFilePaths(), "-var-file=");
    String loggingCommand = command + terraformRefreshCommandRequest.getUiLogs();
    command = command + terraformRefreshCommandRequest.getVarParams();

    doReturn(cliResponse)
        .when(cliHelper)
        .executeCliCommand(eq(command), eq(DEFAULT_TERRAFORM_COMMAND_TIMEOUT), eq(Collections.emptyMap()),
            eq(SCRIPT_FILES_DIRECTORY), eq(logCallback), eq(loggingCommand), any(), any(), anyLong());

    CliResponse actualResponse = terraformClientImpl.refresh(terraformRefreshCommandRequest,
        DEFAULT_TERRAFORM_COMMAND_TIMEOUT, Collections.emptyMap(), SCRIPT_FILES_DIRECTORY, logCallback);

    assertThat(actualResponse).isEqualTo(cliResponse);
  }

  private CliResponse getCliResponse() {
    return CliResponse.builder()
        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
        .output("Command Output")
        .build();
  }

  private CliResponse getCliResponseWithExitCode(int exitCode) {
    return CliResponse.builder()
        .commandExecutionStatus(exitCode == 0 ? CommandExecutionStatus.SUCCESS : CommandExecutionStatus.FAILURE)
        .output("Command Output")
        .exitCode(exitCode)
        .build();
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testApplyCommand() throws InterruptedException, IOException, TimeoutException {
    CliResponse cliResponse = getCliResponse();
    TerraformApplyCommandRequest terraformApplyCommandRequest = TerraformApplyCommandRequest.builder()
                                                                    .planName(TERRAFORM_PLAN_FILE_OUTPUT_NAME)
                                                                    .additionalCliFlags(new HashMap<>() {
                                                                      { put("APPLY", "-lock-timeout=5s"); }
                                                                    })
                                                                    .build();

    String command = "terraform apply -input=false -lock-timeout=5s tfplan";
    doReturn(cliResponse)
        .when(cliHelper)
        .executeCliCommand(eq(command), eq(DEFAULT_TERRAFORM_COMMAND_TIMEOUT), eq(Collections.emptyMap()),
            eq(SCRIPT_FILES_DIRECTORY), eq(logCallback), eq(command), any(), any(), anyLong());

    CliResponse actualResponse = terraformClientImpl.apply(terraformApplyCommandRequest,
        DEFAULT_TERRAFORM_COMMAND_TIMEOUT, Collections.emptyMap(), SCRIPT_FILES_DIRECTORY, logCallback);

    assertThat(actualResponse).isEqualTo(cliResponse);
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testApplyCommandAndTerraformCloudCli() throws InterruptedException, IOException, TimeoutException {
    CliResponse cliResponse = getCliResponse();
    TerraformApplyCommandRequest terraformApplyCommandRequest = TerraformApplyCommandRequest.builder()
                                                                    .planName(TERRAFORM_PLAN_FILE_OUTPUT_NAME)
                                                                    .isTerraformCloudCli(true)
                                                                    .additionalCliFlags(new HashMap<>() {
                                                                      { put("APPLY", "-lock-timeout=5s"); }
                                                                    })
                                                                    .build();
    String command = "echo yes | terraform apply  -lock-timeout=5s";
    doReturn(cliResponse)
        .when(cliHelper)
        .executeCliCommand(eq(command), eq(DEFAULT_TERRAFORM_COMMAND_TIMEOUT), eq(Collections.emptyMap()),
            eq(SCRIPT_FILES_DIRECTORY), eq(logCallback), eq(command), any(), any(), anyLong());

    CliResponse actualResponse = terraformClientImpl.apply(terraformApplyCommandRequest,
        DEFAULT_TERRAFORM_COMMAND_TIMEOUT, Collections.emptyMap(), SCRIPT_FILES_DIRECTORY, logCallback);

    assertThat(actualResponse).isEqualTo(cliResponse);
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testWorkspaceCommand() throws InterruptedException, IOException, TimeoutException {
    CliResponse cliResponse = getCliResponse();
    String workspace = "workspace";
    String command = "terraform workspace select " + workspace;
    doReturn(cliResponse)
        .when(cliHelper)
        .executeCliCommand(eq(command), eq(DEFAULT_TERRAFORM_COMMAND_TIMEOUT), eq(Collections.emptyMap()),
            eq(SCRIPT_FILES_DIRECTORY), eq(logCallback), eq(command), any(), any(), anyLong());

    CliResponse actualResponse = terraformClientImpl.workspace(workspace, true, DEFAULT_TERRAFORM_COMMAND_TIMEOUT,
        Collections.emptyMap(), SCRIPT_FILES_DIRECTORY, logCallback, new HashMap<>());

    assertThat(actualResponse).isEqualTo(cliResponse);
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testshowCommand() throws InterruptedException, IOException, TimeoutException {
    doReturn(getCliResponseTfVersion(1, 0, 1))
        .when(cliHelper)
        .executeCliCommand(
            and(contains("terraform"), contains("version")), anyLong(), anyMap(), anyString(), any(LogCallback.class));

    CliResponse cliResponse = getCliResponse();
    String plan = "planName";
    String command = "terraform show -json " + plan;
    doReturn(cliResponse)
        .when(cliHelper)
        .executeCliCommand(eq(command), eq(DEFAULT_TERRAFORM_COMMAND_TIMEOUT), eq(Collections.emptyMap()),
            eq(SCRIPT_FILES_DIRECTORY), eq(logCallback), eq(command), any(), any(), anyLong());

    CliResponse actualResponse = terraformClientImpl.show(plan, DEFAULT_TERRAFORM_COMMAND_TIMEOUT,
        Collections.emptyMap(), SCRIPT_FILES_DIRECTORY, logCallback, planJsonLogOutputStream);

    assertThat(actualResponse).isEqualTo(cliResponse);
  }

  @Test
  @Owner(developers = BOGDAN)
  @Category(UnitTests.class)
  public void testshowCommand_skipIfVersionLessThan_0_12_0()
      throws InterruptedException, IOException, TimeoutException {
    doReturn(getCliResponseTfVersion(0, 11, 15))
        .when(cliHelper)
        .executeCliCommand(
            and(contains("terraform"), contains("version")), anyLong(), anyMap(), anyString(), any(LogCallback.class));

    CliResponse cliResponse = terraformClientImpl.show("planName", DEFAULT_TERRAFORM_COMMAND_TIMEOUT,
        Collections.emptyMap(), SCRIPT_FILES_DIRECTORY, logCallback, planJsonLogOutputStream);

    verify(cliHelper, never())
        .executeCliCommand(and(contains("terraform show"), contains("-json")), anyLong(), anyMap(), anyString(),
            any(LogCallback.class), anyString(), any(LogOutputStream.class), any(), anyLong());
    assertThat(cliResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SKIPPED);
  }

  @Test
  @Owner(developers = BOGDAN)
  @Category(UnitTests.class)
  public void testshowCommand_skipIfVersionLessThan_0_12_0_logMessage()
      throws InterruptedException, IOException, TimeoutException {
    doReturn(getCliResponseTfVersion(0, 11, 15))
        .when(cliHelper)
        .executeCliCommand(
            and(contains("terraform"), contains("version")), anyLong(), anyMap(), anyString(), any(LogCallback.class));

    terraformClientImpl.show("planName", DEFAULT_TERRAFORM_COMMAND_TIMEOUT, Collections.emptyMap(),
        SCRIPT_FILES_DIRECTORY, logCallback, planJsonLogOutputStream);

    verify(logCallback)
        .saveExecutionLog(
            contains(
                "Terraform plan json export not supported in v0.11.15. Minimum version is v0.12.x. Skipping command."),
            eq(LogLevel.WARN), eq(CommandExecutionStatus.SKIPPED));
  }

  @Test
  @Owner(developers = BOGDAN)
  @Category(UnitTests.class)
  public void testshowCommand_executeIfVersionEquals_0_12_0()
      throws InterruptedException, IOException, TimeoutException {
    doReturn(getCliResponseTfVersion(0, 12, 0))
        .when(cliHelper)
        .executeCliCommand(
            and(contains("terraform"), contains("version")), anyLong(), anyMap(), anyString(), any(LogCallback.class));

    doReturn(CliResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build())
        .when(cliHelper)
        .executeCliCommand(contains("terraform"), anyLong(), anyMap(), anyString(), any(LogCallback.class),
            contains("terraform"), any(LogOutputStream.class), any(), anyLong());

    terraformClientImpl.show("planName", DEFAULT_TERRAFORM_COMMAND_TIMEOUT, Collections.emptyMap(),
        SCRIPT_FILES_DIRECTORY, logCallback, planJsonLogOutputStream);

    ArgumentCaptor<String> commandCaptor = ArgumentCaptor.forClass(String.class);
    verify(cliHelper).executeCliCommand(commandCaptor.capture(), anyLong(), anyMap(), anyString(),
        any(LogCallback.class), anyString(), any(LogOutputStream.class), any(), anyLong());
    assertThat(commandCaptor.getAllValues().get(0)).contains("terraform show");
    assertThat(commandCaptor.getAllValues().get(0)).contains("-json");
  }

  @Test
  @Owner(developers = BOGDAN)
  @Category(UnitTests.class)
  public void testshowCommand_executeIfVersionBiggerThan_0_12_0()
      throws InterruptedException, IOException, TimeoutException {
    doReturn(getCliResponseTfVersion(1, 0, 1))
        .when(cliHelper)
        .executeCliCommand(
            and(contains("terraform"), contains("version")), anyLong(), anyMap(), anyString(), any(LogCallback.class));

    doReturn(CliResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build())
        .when(cliHelper)
        .executeCliCommand(contains("terraform"), anyLong(), anyMap(), anyString(), any(LogCallback.class),
            contains("terraform"), any(LogOutputStream.class), any(), anyLong());

    terraformClientImpl.show("planName", DEFAULT_TERRAFORM_COMMAND_TIMEOUT, Collections.emptyMap(),
        SCRIPT_FILES_DIRECTORY, logCallback, planJsonLogOutputStream);

    ArgumentCaptor<String> commandCaptor = ArgumentCaptor.forClass(String.class);
    verify(cliHelper).executeCliCommand(commandCaptor.capture(), anyLong(), anyMap(), anyString(),
        any(LogCallback.class), anyString(), any(LogOutputStream.class), any(), anyLong());
    assertThat(commandCaptor.getAllValues().get(0)).contains("terraform show");
    assertThat(commandCaptor.getAllValues().get(0)).contains("-json");
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testOutputCommand() throws InterruptedException, IOException, TimeoutException {
    CliResponse cliResponse = getCliResponse();
    String tfOutputsFile = "OutFile.txt";
    String command = "terraform output -json > " + tfOutputsFile;
    doReturn(cliResponse)
        .when(cliHelper)
        .executeCliCommand(eq(command), eq(DEFAULT_TERRAFORM_COMMAND_TIMEOUT), eq(Collections.emptyMap()),
            eq(SCRIPT_FILES_DIRECTORY), eq(logCallback), eq(command), any(), any(), anyLong());

    CliResponse actualResponse = terraformClientImpl.output(
        tfOutputsFile, DEFAULT_TERRAFORM_COMMAND_TIMEOUT, Collections.emptyMap(), SCRIPT_FILES_DIRECTORY, logCallback);

    assertThat(actualResponse).isEqualTo(cliResponse);
  }

  @Test(expected = TerraformCliRuntimeException.class)
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testCommandFailureThrowsException() throws InterruptedException, TimeoutException, IOException {
    CliResponse cliResponse =
        CliResponse.builder().commandExecutionStatus(CommandExecutionStatus.FAILURE).output("Command Failed").build();
    String tfOutputsFile = "OutFile.txt";
    String command = "terraform output -json > " + tfOutputsFile;
    doReturn(cliResponse)
        .when(cliHelper)
        .executeCliCommand(eq(command), eq(DEFAULT_TERRAFORM_COMMAND_TIMEOUT), eq(Collections.emptyMap()),
            eq(SCRIPT_FILES_DIRECTORY), eq(logCallback), eq(command), any(), any(), anyLong());

    CliResponse actualResponse = terraformClientImpl.output(
        tfOutputsFile, DEFAULT_TERRAFORM_COMMAND_TIMEOUT, Collections.emptyMap(), SCRIPT_FILES_DIRECTORY, logCallback);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testVersionSuccess() throws InterruptedException, TimeoutException, IOException {
    doReturn(getCliResponseTfVersion(0, 13, 4))
        .when(cliHelper)
        .executeCliCommand(eq("terraform version"), eq(DEFAULT_TERRAFORM_COMMAND_TIMEOUT), eq(Collections.emptyMap()),
            eq(SCRIPT_FILES_DIRECTORY), any(NoopExecutionCallback.class));

    TerraformVersion version = terraformClientImpl.version(DEFAULT_TERRAFORM_COMMAND_TIMEOUT, SCRIPT_FILES_DIRECTORY);

    assertThat(version.getMajor()).isEqualTo(0);
    assertThat(version.getMinor()).isEqualTo(13);
    assertThat(version.getPatch()).isEqualTo(4);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testVersionFailed() throws InterruptedException, TimeoutException, IOException {
    doReturn(CliResponse.builder().commandExecutionStatus(CommandExecutionStatus.FAILURE).build())
        .when(cliHelper)
        .executeCliCommand(eq("terraform version"), eq(DEFAULT_TERRAFORM_COMMAND_TIMEOUT), eq(Collections.emptyMap()),
            eq(SCRIPT_FILES_DIRECTORY), any(NoopExecutionCallback.class));

    TerraformVersion version = terraformClientImpl.version(DEFAULT_TERRAFORM_COMMAND_TIMEOUT, SCRIPT_FILES_DIRECTORY);

    assertThat(version.getMajor()).isNull();
    assertThat(version.getMinor()).isNull();
    assertThat(version.getPatch()).isNull();
  }

  private CliResponse getCliResponseTfVersion(int major, int minor, int patch) {
    return CliResponse.builder()
        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
        .output(format("Terraform v%d.%d.%d\n"
                + "\n"
                + "Your version of Terraform is out of date! The latest version\n"
                + "is 1.1.4. You can update by downloading from www.terraform.io",
            major, minor, patch))
        .build();
  }
}