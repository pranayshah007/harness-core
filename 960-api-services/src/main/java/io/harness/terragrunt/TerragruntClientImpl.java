/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.terragrunt;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.provision.TerragruntConstants.TERRAGRUNT_APPLY_COMMAND_FORMAT;
import static io.harness.provision.TerragruntConstants.TERRAGRUNT_DESTROY_COMMAND_FORMAT;
import static io.harness.provision.TerragruntConstants.TERRAGRUNT_PLAN_COMMAND_FORMAT;
import static io.harness.provision.TerragruntConstants.TERRAGRUNT_PLAN_DESTROY_COMMAND_FORMAT;
import static io.harness.provision.TerragruntConstants.TERRAGRUNT_REFRESH_COMMAND_FORMAT;
import static io.harness.provision.TerragruntConstants.TERRAGRUNT_RUN_ALL_APPLY_COMMAND_FORMAT;
import static io.harness.provision.TerragruntConstants.TERRAGRUNT_RUN_ALL_DESTROY_COMMAND_FORMAT;
import static io.harness.provision.TerragruntConstants.TERRAGRUNT_RUN_ALL_PLAN_COMMAND_FORMAT;
import static io.harness.provision.TerragruntConstants.TERRAGRUNT_RUN_ALL_PLAN_DESTROY_COMMAND_FORMAT;
import static io.harness.provision.TerragruntConstants.TERRAGRUNT_RUN_ALL_REFRESH_COMMAND_FORMAT;
import static io.harness.provision.TerragruntConstants.TERRAGRUNT_WORKSPACE_LIST_COMMAND_FORMAT;

import static software.wings.beans.LogHelper.color;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cli.CliResponse;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;

import com.google.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nonnull;
import lombok.NonNull;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;
import org.zeroturnaround.exec.stream.LogOutputStream;

@OwnedBy(CDP)
@Singleton
public class TerragruntClientImpl implements TerragruntClient {
  @Nonnull
  @Override
  public CliResponse init(TerragruntCliCommandRequestParams cliCommandRequestParams, @Nonnull LogCallback logCallback)
      throws InterruptedException, TimeoutException, IOException {
    String command = getInitCommand(cliCommandRequestParams.getBackendConfigFilePath());
    logCallback.saveExecutionLog(color(command, LogColor.White, LogWeight.Bold), INFO, CommandExecutionStatus.RUNNING);

    cliCommandRequestParams.getActivityLogOutputStream().setLogCallback(logCallback);
    cliCommandRequestParams.getErrorLogOutputStream().setLogCallback(logCallback);
    return executeShellCommand(command, cliCommandRequestParams.getDirectory(),
        cliCommandRequestParams.getTimeoutInMillis(), cliCommandRequestParams.getEnvVars(),
        cliCommandRequestParams.getActivityLogOutputStream(), cliCommandRequestParams.getErrorLogOutputStream());
  }

  @Nonnull
  @Override
  public CliResponse workspace(TerragruntCliCommandRequestParams cliCommandRequestParams, String workspaceCommand,
      String workspace, @Nonnull LogCallback logCallback) throws InterruptedException, TimeoutException, IOException {
    String command = getWorkspaceCommand(workspaceCommand, workspace);
    logCallback.saveExecutionLog(color(command, LogColor.White, LogWeight.Bold), INFO, CommandExecutionStatus.RUNNING);
    cliCommandRequestParams.getActivityLogOutputStream().setLogCallback(logCallback);
    cliCommandRequestParams.getErrorLogOutputStream().setLogCallback(logCallback);
    return executeShellCommand(command, cliCommandRequestParams.getDirectory(),
        cliCommandRequestParams.getTimeoutInMillis(), cliCommandRequestParams.getEnvVars(),
        cliCommandRequestParams.getActivityLogOutputStream(), cliCommandRequestParams.getErrorLogOutputStream());
  }

  @Nonnull
  @Override
  public CliResponse runAllNewWorkspace(TerragruntCliCommandRequestParams cliCommandRequestParams, String workspace,
      @Nonnull LogCallback logCallback) throws InterruptedException, TimeoutException, IOException {
    String command = getNewWorkspaceCommand(workspace);
    return executeShellCommand(command, cliCommandRequestParams.getDirectory(),
        cliCommandRequestParams.getTimeoutInMillis(), cliCommandRequestParams.getEnvVars(), null, null);
  }

  @Nonnull
  @Override
  public CliResponse runAllSelectWorkspace(TerragruntCliCommandRequestParams cliCommandRequestParams, String workspace,
      @Nonnull LogCallback logCallback) throws InterruptedException, TimeoutException, IOException {
    String command = getSelectWorkspaceCommand(workspace);
    return executeShellCommand(command, cliCommandRequestParams.getDirectory(),
        cliCommandRequestParams.getTimeoutInMillis(), cliCommandRequestParams.getEnvVars(), null, null);
  }

  @Nonnull
  @Override
  public CliResponse refresh(TerragruntCliCommandRequestParams cliCommandRequestParams, String targetArgs,
      String varParams, String uiLogs, @Nonnull LogCallback logCallback)
      throws InterruptedException, TimeoutException, IOException {
    String command = getRefreshCommand(targetArgs, varParams);
    String commandToLog = format(TERRAGRUNT_REFRESH_COMMAND_FORMAT, targetArgs, uiLogs);
    logCallback.saveExecutionLog(
        color(commandToLog, LogColor.White, LogWeight.Bold), INFO, CommandExecutionStatus.RUNNING);

    cliCommandRequestParams.getActivityLogOutputStream().setLogCallback(logCallback);
    cliCommandRequestParams.getErrorLogOutputStream().setLogCallback(logCallback);
    return executeShellCommand(command, cliCommandRequestParams.getDirectory(),
        cliCommandRequestParams.getTimeoutInMillis(), cliCommandRequestParams.getEnvVars(),
        cliCommandRequestParams.getActivityLogOutputStream(), cliCommandRequestParams.getErrorLogOutputStream());
  }

  @Nonnull
  @Override
  public CliResponse plan(TerragruntCliCommandRequestParams cliCommandRequestParams, String targetArgs,
      String varParams, String uiLogs, @Nonnull LogCallback logCallback)
      throws InterruptedException, TimeoutException, IOException {
    String command = getPlanCommand(targetArgs, varParams);
    String commandToLog = format(TERRAGRUNT_PLAN_COMMAND_FORMAT, targetArgs, uiLogs);
    logCallback.saveExecutionLog(
        color(commandToLog, LogColor.White, LogWeight.Bold), INFO, CommandExecutionStatus.RUNNING);

    cliCommandRequestParams.getPlanLogOutputStream().setLogCallback(logCallback);
    cliCommandRequestParams.getErrorLogOutputStream().setLogCallback(logCallback);
    return executeShellCommand(command, cliCommandRequestParams.getDirectory(),
        cliCommandRequestParams.getTimeoutInMillis(), cliCommandRequestParams.getEnvVars(),
        cliCommandRequestParams.getPlanLogOutputStream(), cliCommandRequestParams.getErrorLogOutputStream());
  }

  @Nonnull
  @Override
  public CliResponse planDestroy(TerragruntCliCommandRequestParams cliCommandRequestParams, String targetArgs,
      String varParams, String uiLogs, @Nonnull LogCallback logCallback)
      throws InterruptedException, TimeoutException, IOException {
    String command = getPlanDestoryCommand(targetArgs, varParams);
    String commandToLog = format(TERRAGRUNT_PLAN_DESTROY_COMMAND_FORMAT, targetArgs, uiLogs);
    logCallback.saveExecutionLog(
        color(commandToLog, LogColor.White, LogWeight.Bold), INFO, CommandExecutionStatus.RUNNING);

    cliCommandRequestParams.getPlanLogOutputStream().setLogCallback(logCallback);
    cliCommandRequestParams.getErrorLogOutputStream().setLogCallback(logCallback);
    return executeShellCommand(command, cliCommandRequestParams.getDirectory(),
        cliCommandRequestParams.getTimeoutInMillis(), cliCommandRequestParams.getEnvVars(),
        cliCommandRequestParams.getPlanLogOutputStream(), cliCommandRequestParams.getErrorLogOutputStream());
  }

  @Nonnull
  @Override
  public CliResponse destroy(TerragruntCliCommandRequestParams cliCommandRequestParams, String targetArgs,
      String varParams, String uiLogs, @Nonnull LogCallback logCallback)
      throws InterruptedException, TimeoutException, IOException {
    String command = getDestoryCommand(targetArgs, varParams);
    String commandToLog = format(TERRAGRUNT_DESTROY_COMMAND_FORMAT, targetArgs, uiLogs);
    logCallback.saveExecutionLog(
        color(commandToLog, LogColor.White, LogWeight.Bold), INFO, CommandExecutionStatus.RUNNING);

    cliCommandRequestParams.getActivityLogOutputStream().setLogCallback(logCallback);
    cliCommandRequestParams.getErrorLogOutputStream().setLogCallback(logCallback);
    return executeShellCommand(command, cliCommandRequestParams.getDirectory(),
        cliCommandRequestParams.getTimeoutInMillis(), cliCommandRequestParams.getEnvVars(),
        cliCommandRequestParams.getActivityLogOutputStream(), cliCommandRequestParams.getErrorLogOutputStream());
  }

  @Nonnull
  @Override
  public CliResponse runAllplan(TerragruntCliCommandRequestParams cliCommandRequestParams, String targetArgs,
      String varParams, String uiLogs, @Nonnull LogCallback logCallback)
      throws InterruptedException, TimeoutException, IOException {
    String command = getRunAllPlanCommand(targetArgs, varParams);
    String commandToLog = format(TERRAGRUNT_RUN_ALL_PLAN_COMMAND_FORMAT, targetArgs, uiLogs);
    logCallback.saveExecutionLog(
        color(commandToLog, LogColor.White, LogWeight.Bold), INFO, CommandExecutionStatus.RUNNING);

    cliCommandRequestParams.getPlanLogOutputStream().setLogCallback(logCallback);
    cliCommandRequestParams.getErrorLogOutputStream().setLogCallback(logCallback);
    return executeShellCommand(command, cliCommandRequestParams.getDirectory(),
        cliCommandRequestParams.getTimeoutInMillis(), cliCommandRequestParams.getEnvVars(),
        cliCommandRequestParams.getPlanLogOutputStream(), cliCommandRequestParams.getErrorLogOutputStream());
  }

  @Nonnull
  @Override
  public CliResponse showJson(TerragruntCliCommandRequestParams cliCommandRequestParams, String planName,
      @Nonnull LogCallback logCallback) throws InterruptedException, TimeoutException, IOException {
    logCallback.saveExecutionLog(
        color(format("%nGenerating json representation of %s %n", planName), LogColor.White, LogWeight.Bold), INFO,
        CommandExecutionStatus.RUNNING);
    String command = getShowJsonCommand(planName);
    logCallback.saveExecutionLog(color(command, LogColor.White, LogWeight.Bold), INFO, CommandExecutionStatus.RUNNING);

    cliCommandRequestParams.getErrorLogOutputStream().setLogCallback(logCallback);
    return executeShellCommand(command, cliCommandRequestParams.getDirectory(),
        cliCommandRequestParams.getTimeoutInMillis(), cliCommandRequestParams.getEnvVars(),
        cliCommandRequestParams.getPlanJsonLogOutputStream(), cliCommandRequestParams.getErrorLogOutputStream());
  }

  @Nonnull
  @Override
  public CliResponse runAllshowJson(TerragruntCliCommandRequestParams cliCommandRequestParams, String planName,
      @Nonnull LogCallback logCallback) throws InterruptedException, TimeoutException, IOException {
    logCallback.saveExecutionLog(
        color(format("%nGenerating json representation of %s %n", planName), LogColor.White, LogWeight.Bold), INFO,
        CommandExecutionStatus.RUNNING);
    String command = getRunAllShowJsonComand(planName);
    logCallback.saveExecutionLog(color(command, LogColor.White, LogWeight.Bold), INFO, CommandExecutionStatus.RUNNING);

    cliCommandRequestParams.getErrorLogOutputStream().setLogCallback(logCallback);
    return executeShellCommand(command, cliCommandRequestParams.getDirectory(),
        cliCommandRequestParams.getTimeoutInMillis(), cliCommandRequestParams.getEnvVars(),
        cliCommandRequestParams.getPlanJsonLogOutputStream(), cliCommandRequestParams.getErrorLogOutputStream());
  }

  @Nonnull
  @Override
  public CliResponse apply(TerragruntCliCommandRequestParams cliCommandRequestParams, @Nonnull LogCallback logCallback)
      throws InterruptedException, TimeoutException, IOException {
    String command = getApplyCommand();
    logCallback.saveExecutionLog(color(command, LogColor.White, LogWeight.Bold), INFO, CommandExecutionStatus.RUNNING);

    cliCommandRequestParams.getActivityLogOutputStream().setLogCallback(logCallback);
    cliCommandRequestParams.getErrorLogOutputStream().setLogCallback(logCallback);
    return executeShellCommand(command, cliCommandRequestParams.getDirectory(),
        cliCommandRequestParams.getTimeoutInMillis(), cliCommandRequestParams.getEnvVars(),
        cliCommandRequestParams.getActivityLogOutputStream(), cliCommandRequestParams.getErrorLogOutputStream());
  }

  @Nonnull
  @Override
  public CliResponse applyDestroyTfPlan(TerragruntCliCommandRequestParams cliCommandRequestParams,
      @Nonnull LogCallback logCallback) throws InterruptedException, TimeoutException, IOException {
    String command = getApplyDestroyTfPlanCommand();
    logCallback.saveExecutionLog(color(command, LogColor.White, LogWeight.Bold), INFO, CommandExecutionStatus.RUNNING);

    cliCommandRequestParams.getActivityLogOutputStream().setLogCallback(logCallback);
    cliCommandRequestParams.getErrorLogOutputStream().setLogCallback(logCallback);
    return executeShellCommand(command, cliCommandRequestParams.getDirectory(),
        cliCommandRequestParams.getTimeoutInMillis(), cliCommandRequestParams.getEnvVars(),
        cliCommandRequestParams.getActivityLogOutputStream(), cliCommandRequestParams.getErrorLogOutputStream());
  }

  @Nonnull
  @Override
  public CliResponse runAllApply(TerragruntCliCommandRequestParams cliCommandRequestParams, String targetArgs,
      String varParams, String uiLogs, @Nonnull LogCallback logCallback)
      throws InterruptedException, TimeoutException, IOException {
    String command = getRunAllApplyCommand(targetArgs, varParams);
    String commandToLog = format(TERRAGRUNT_RUN_ALL_APPLY_COMMAND_FORMAT, targetArgs, uiLogs);
    logCallback.saveExecutionLog(
        color(commandToLog, LogColor.White, LogWeight.Bold), INFO, CommandExecutionStatus.RUNNING);

    cliCommandRequestParams.getActivityLogOutputStream().setLogCallback(logCallback);
    cliCommandRequestParams.getErrorLogOutputStream().setLogCallback(logCallback);
    return executeShellCommand(command, cliCommandRequestParams.getDirectory(),
        cliCommandRequestParams.getTimeoutInMillis(), cliCommandRequestParams.getEnvVars(),
        cliCommandRequestParams.getActivityLogOutputStream(), cliCommandRequestParams.getErrorLogOutputStream());
  }

  @Nonnull
  @Override
  public CliResponse runAllDestroy(TerragruntCliCommandRequestParams cliCommandRequestParams, String targetArgs,
      String varParams, String uiLogs, @Nonnull LogCallback logCallback)
      throws InterruptedException, TimeoutException, IOException {
    String command = getRunAllDestroyCommand(targetArgs, varParams);
    String commandToLog = format(TERRAGRUNT_RUN_ALL_DESTROY_COMMAND_FORMAT, targetArgs, uiLogs);
    logCallback.saveExecutionLog(
        color(commandToLog, LogColor.White, LogWeight.Bold), INFO, CommandExecutionStatus.RUNNING);

    cliCommandRequestParams.getActivityLogOutputStream().setLogCallback(logCallback);
    cliCommandRequestParams.getErrorLogOutputStream().setLogCallback(logCallback);
    return executeShellCommand(command, cliCommandRequestParams.getDirectory(),
        cliCommandRequestParams.getTimeoutInMillis(), cliCommandRequestParams.getEnvVars(),
        cliCommandRequestParams.getActivityLogOutputStream(), cliCommandRequestParams.getErrorLogOutputStream());
  }

  @Nonnull
  @Override
  public CliResponse runAllPlanDestroy(TerragruntCliCommandRequestParams cliCommandRequestParams, String targetArgs,
      String varParams, String uiLogs, @Nonnull LogCallback logCallback)
      throws InterruptedException, TimeoutException, IOException {
    String command = getRunAllPlanDestroyCommand(targetArgs, varParams);
    String commandToLog = format(TERRAGRUNT_RUN_ALL_PLAN_DESTROY_COMMAND_FORMAT, targetArgs, uiLogs);
    logCallback.saveExecutionLog(
        color(commandToLog, LogColor.White, LogWeight.Bold), INFO, CommandExecutionStatus.RUNNING);

    cliCommandRequestParams.getPlanLogOutputStream().setLogCallback(logCallback);
    cliCommandRequestParams.getErrorLogOutputStream().setLogCallback(logCallback);
    return executeShellCommand(command, cliCommandRequestParams.getDirectory(),
        cliCommandRequestParams.getTimeoutInMillis(), cliCommandRequestParams.getEnvVars(),
        cliCommandRequestParams.getPlanLogOutputStream(), cliCommandRequestParams.getErrorLogOutputStream());
  }

  @Nonnull
  @Override
  public CliResponse runAllInit(TerragruntCliCommandRequestParams cliCommandRequestParams,
      @Nonnull LogCallback logCallback) throws InterruptedException, TimeoutException, IOException {
    String command = getRunAllInitCommand(cliCommandRequestParams.getBackendConfigFilePath());
    logCallback.saveExecutionLog(color(command, LogColor.White, LogWeight.Bold), INFO, CommandExecutionStatus.RUNNING);

    cliCommandRequestParams.getActivityLogOutputStream().setLogCallback(logCallback);
    cliCommandRequestParams.getErrorLogOutputStream().setLogCallback(logCallback);
    return executeShellCommand(command, cliCommandRequestParams.getDirectory(),
        cliCommandRequestParams.getTimeoutInMillis(), cliCommandRequestParams.getEnvVars(),
        cliCommandRequestParams.getActivityLogOutputStream(), cliCommandRequestParams.getErrorLogOutputStream());
  }

  @Nonnull
  @Override
  public CliResponse runAllRefresh(TerragruntCliCommandRequestParams cliCommandRequestParams, String targetArgs,
      String varParams, String uiLogs, @Nonnull LogCallback logCallback)
      throws InterruptedException, TimeoutException, IOException {
    String command = getRunAllRefreshCommand(targetArgs, varParams);
    String commandToLog = format(TERRAGRUNT_RUN_ALL_REFRESH_COMMAND_FORMAT, targetArgs, uiLogs);
    logCallback.saveExecutionLog(
        color(commandToLog, LogColor.White, LogWeight.Bold), INFO, CommandExecutionStatus.RUNNING);

    cliCommandRequestParams.getActivityLogOutputStream().setLogCallback(logCallback);
    cliCommandRequestParams.getErrorLogOutputStream().setLogCallback(logCallback);
    return executeShellCommand(command, cliCommandRequestParams.getDirectory(),
        cliCommandRequestParams.getTimeoutInMillis(), cliCommandRequestParams.getEnvVars(),
        cliCommandRequestParams.getActivityLogOutputStream(), cliCommandRequestParams.getErrorLogOutputStream());
  }

  @Nonnull
  @Override
  public CliResponse output(TerragruntCliCommandRequestParams cliCommandRequestParams, String tfOutputsFilePath,
      @Nonnull LogCallback logCallback) throws InterruptedException, TimeoutException, IOException {
    String command = getOutputCommand(tfOutputsFilePath);
    logCallback.saveExecutionLog(color(command, LogColor.White, LogWeight.Bold), INFO, CommandExecutionStatus.RUNNING);

    cliCommandRequestParams.getActivityLogOutputStream().setLogCallback(logCallback);
    cliCommandRequestParams.getErrorLogOutputStream().setLogCallback(logCallback);
    return executeShellCommand(command, cliCommandRequestParams.getDirectory(),
        cliCommandRequestParams.getTimeoutInMillis(), cliCommandRequestParams.getEnvVars(),
        cliCommandRequestParams.getActivityLogOutputStream(), cliCommandRequestParams.getErrorLogOutputStream());
  }

  @Nonnull
  @Override
  public CliResponse runAllOutput(TerragruntCliCommandRequestParams cliCommandRequestParams,
      @Nonnull LogCallback logCallback) throws InterruptedException, TimeoutException, IOException {
    String command = getRunAllOutputCommand(cliCommandRequestParams.getTfOutputsFile().toString());
    logCallback.saveExecutionLog(color(command, LogColor.White, LogWeight.Bold), INFO, CommandExecutionStatus.RUNNING);

    cliCommandRequestParams.getActivityLogOutputStream().setLogCallback(logCallback);
    cliCommandRequestParams.getErrorLogOutputStream().setLogCallback(logCallback);
    return executeShellCommand(command, cliCommandRequestParams.getDirectory(),
        cliCommandRequestParams.getTimeoutInMillis(), cliCommandRequestParams.getEnvVars(),
        cliCommandRequestParams.getActivityLogOutputStream(), cliCommandRequestParams.getErrorLogOutputStream());
  }

  @Nonnull
  @Override
  public CliResponse terragruntInfo(TerragruntCliCommandRequestParams cliCommandRequestParams, LogCallback logCallback)
      throws InterruptedException, TimeoutException, IOException {
    String command = getTerragruntInfoCommand();

    cliCommandRequestParams.getActivityLogOutputStream().setLogCallback(logCallback);
    cliCommandRequestParams.getErrorLogOutputStream().setLogCallback(logCallback);
    return executeShellCommand(command, cliCommandRequestParams.getDirectory(),
        cliCommandRequestParams.getTimeoutInMillis(), cliCommandRequestParams.getEnvVars(),
        cliCommandRequestParams.getActivityLogOutputStream(), cliCommandRequestParams.getErrorLogOutputStream());
  }

  @Nonnull
  @Override
  public CliResponse workspaceList(String directory, long timeoutInMillis)
      throws InterruptedException, TimeoutException, IOException {
    String command = getWorkspaceListCommand();
    return executeShellCommand(command, directory, timeoutInMillis, new HashMap<>(), null, null);
  }

  @Nonnull
  @Override
  public CliResponse version(TerragruntCliCommandRequestParams cliCommandRequestParams,
      @Nonnull LogCallback logCallback) throws InterruptedException, TimeoutException, IOException {
    String command = "terragrunt -version";

    cliCommandRequestParams.getActivityLogOutputStream().setLogCallback(logCallback);
    cliCommandRequestParams.getErrorLogOutputStream().setLogCallback(logCallback);
    return executeShellCommand(command, cliCommandRequestParams.getDirectory(),
        cliCommandRequestParams.getTimeoutInMillis(), cliCommandRequestParams.getEnvVars(),
        cliCommandRequestParams.getActivityLogOutputStream(), cliCommandRequestParams.getErrorLogOutputStream());
  }

  private String getTerragruntInfoCommand() {
    return "terragrunt terragrunt-info";
  }

  private String getApplyDestroyTfPlanCommand() {
    return "terragrunt apply -input=false tfdestroyplan";
  }

  private String getInitCommand(String absoluteBackendConfigFilePath) {
    File backendConfigFile = new File(absoluteBackendConfigFilePath);
    return format("terragrunt init%s",
        backendConfigFile.exists() ? format(" -backend-config=%s", absoluteBackendConfigFilePath) : "");
  }

  private String getRunAllInitCommand(String absoluteBackendConfigFilePath) {
    File backendConfigFile = new File(absoluteBackendConfigFilePath);
    return format("terragrunt run-all init%s",
        backendConfigFile.exists() ? format(" -backend-config=%s", absoluteBackendConfigFilePath) : "");
  }

  private String getWorkspaceCommand(String workspaceCommand, String workspace) {
    return format("terragrunt workspace %s %s", workspaceCommand, workspace);
  }

  private String getSelectWorkspaceCommand(String workspace) {
    return format("terragrunt run-all workspace select %s", workspace);
  }

  private String getNewWorkspaceCommand(String workspace) {
    return format("terragrunt run-all workspace new %s", workspace);
  }

  private String getRefreshCommand(String targetArgs, String varParams) {
    return format(TERRAGRUNT_REFRESH_COMMAND_FORMAT, targetArgs, varParams);
  }

  private String getRunAllRefreshCommand(String targetArgs, String varParams) {
    return format(TERRAGRUNT_RUN_ALL_REFRESH_COMMAND_FORMAT, targetArgs, varParams);
  }

  private String getPlanCommand(String targetArgs, String varParams) {
    return format(TERRAGRUNT_PLAN_COMMAND_FORMAT, targetArgs, varParams);
  }

  private String getPlanDestoryCommand(String targetArgs, String varParams) {
    return format(TERRAGRUNT_PLAN_DESTROY_COMMAND_FORMAT, targetArgs, varParams);
  }

  private String getDestoryCommand(String targetArgs, String varParams) {
    return format(TERRAGRUNT_DESTROY_COMMAND_FORMAT, targetArgs, varParams);
  }

  private String getRunAllPlanCommand(String targetArgs, String varParams) {
    return format(TERRAGRUNT_RUN_ALL_PLAN_COMMAND_FORMAT, targetArgs, varParams);
  }

  private String getRunAllApplyCommand(String targetArgs, String varParams) {
    return format(TERRAGRUNT_RUN_ALL_APPLY_COMMAND_FORMAT, targetArgs, varParams);
  }

  private String getRunAllPlanDestroyCommand(String targetArgs, String varParams) {
    return format(TERRAGRUNT_RUN_ALL_PLAN_DESTROY_COMMAND_FORMAT, targetArgs, varParams);
  }

  private String getRunAllDestroyCommand(String targetArgs, String varParams) {
    return format(TERRAGRUNT_RUN_ALL_DESTROY_COMMAND_FORMAT, targetArgs, varParams);
  }

  private String getShowJsonCommand(String planName) {
    return format("terragrunt show -json %s", planName);
  }

  private String getRunAllShowJsonComand(String planName) {
    return format("terragrunt run-all show -json %s", planName);
  }

  private String getApplyCommand() {
    return TERRAGRUNT_APPLY_COMMAND_FORMAT;
  }

  private String getOutputCommand(String tfOutputsFilePath) {
    return format("terragrunt output --json > %s", tfOutputsFilePath);
  }

  private String getRunAllOutputCommand(String tfOutputsFilePath) {
    return format("terragrunt run-all output --json > %s", tfOutputsFilePath);
  }

  private String getWorkspaceListCommand() {
    return TERRAGRUNT_WORKSPACE_LIST_COMMAND_FORMAT;
  }

  public CliResponse executeShellCommand(String command, String directory, Long timeoutInMillis,
      @NonNull Map<String, String> envVars, LogOutputStream outputStream, LogOutputStream errorLogOutputStream)
      throws RuntimeException, IOException, InterruptedException, TimeoutException {
    ProcessExecutor processExecutor = new ProcessExecutor()
                                          .timeout(timeoutInMillis, TimeUnit.MILLISECONDS)
                                          .command("/bin/sh", "-c", command)
                                          .readOutput(true)
                                          .environment(envVars)
                                          .redirectOutput(outputStream)
                                          .redirectError(errorLogOutputStream)
                                          .directory(Paths.get(directory).toFile());

    ProcessResult processResult = processExecutor.execute();
    CommandExecutionStatus status = processResult.getExitValue() == 0 ? SUCCESS : FAILURE;
    return CliResponse.builder().commandExecutionStatus(status).output(processResult.outputUTF8()).build();
  }
}
