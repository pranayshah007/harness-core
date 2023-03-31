/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.terraform;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.LogLevel.WARN;
import static io.harness.provision.TerraformConstants.SECONDS_TO_WAIT_FOR_GRACEFUL_SHUTDOWN;
import static io.harness.terraform.TerraformConstants.APPLY;
import static io.harness.terraform.TerraformConstants.DESTROY;
import static io.harness.terraform.TerraformConstants.INIT;
import static io.harness.terraform.TerraformConstants.PLAN;
import static io.harness.terraform.TerraformConstants.REFRESH;
import static io.harness.terraform.TerraformConstants.WORKSPACE;

import static software.wings.beans.LogColor.Yellow;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.EMPTY;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cli.CliHelper;
import io.harness.cli.CliResponse;
import io.harness.cli.LogCallbackOutputStream;
import io.harness.cli.TerraformCliErrorLogOutputStream;
import io.harness.exception.TerraformCommandExecutionException;
import io.harness.exception.runtime.TerraformCliRuntimeException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.NoopExecutionCallback;
import io.harness.logging.PlanHumanReadableOutputStream;
import io.harness.logging.PlanJsonLogOutputStream;
import io.harness.logging.PlanLogOutputStream;
import io.harness.terraform.beans.TerraformVersion;
import io.harness.terraform.request.TerraformApplyCommandRequest;
import io.harness.terraform.request.TerraformDestroyCommandRequest;
import io.harness.terraform.request.TerraformInitCommandRequest;
import io.harness.terraform.request.TerraformPlanCommandRequest;
import io.harness.terraform.request.TerraformRefreshCommandRequest;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.zeroturnaround.exec.stream.LogOutputStream;

@Slf4j
@Singleton
@OwnedBy(CDP)
public class TerraformClientImpl implements TerraformClient {
  public static final String TARGET_PARAM = "-target=";
  public static final String VAR_FILE_PARAM = "-var-file=";

  @Inject CliHelper cliHelper;

  @Nonnull
  @Override
  public CliResponse init(TerraformInitCommandRequest terraformInitCommandRequest, long timeoutInMillis,
      Map<String, String> envVariables, String scriptDirectory, @Nonnull LogCallback executionLogCallback)
      throws InterruptedException, TimeoutException, IOException {
    String additionalCliOption = getAdditionalCliOption(terraformInitCommandRequest.getAdditionalCliFlags(), INIT);

    String command = format("terraform init -input=false %s %s",
        isEmpty(terraformInitCommandRequest.getTfBackendConfigsFilePath())
            ? EMPTY
            : format("-backend-config=%s", terraformInitCommandRequest.getTfBackendConfigsFilePath()),
        additionalCliOption);

    /**
     * echo "no" is to prevent copying of state from local to remote by suppressing the
     * copy prompt. As of tf version 0.12.3
     * there is no way to provide this as a command line argument
     */
    String executionCommand = format("echo \"no\" | %s", command);
    return executeTerraformCLICommand(executionCommand, timeoutInMillis, envVariables, scriptDirectory,
        executionLogCallback, command, new LogCallbackOutputStream(executionLogCallback));
  }

  @Nonnull
  @Override
  public CliResponse destroy(TerraformDestroyCommandRequest terraformDestroyCommandRequest, long timeoutInMillis,
      Map<String, String> envVariables, String scriptDirectory, @Nonnull LogCallback executionLogCallback)
      throws InterruptedException, TimeoutException, IOException {
    String additionalCliOption =
        getAdditionalCliOption(terraformDestroyCommandRequest.getAdditionalCliFlags(), DESTROY);
    String command;

    if (terraformDestroyCommandRequest.isTerraformCloudCli()) {
      // terraform Cloud var files are located in script directory having this suffix .auto.tfvars, so we don't pass
      // them as arguments -var-file
      command = format("echo yes | terraform destroy %s %s %s",
          TerraformHelperUtils.getAutoApproveArgument(version(timeoutInMillis, scriptDirectory)),
          TerraformHelperUtils.generateCommandFlagsString(terraformDestroyCommandRequest.getTargets(), TARGET_PARAM),
          additionalCliOption);
    } else {
      command = format("terraform destroy %s %s %s %s",
          TerraformHelperUtils.getAutoApproveArgument(version(timeoutInMillis, scriptDirectory)),
          TerraformHelperUtils.generateCommandFlagsString(terraformDestroyCommandRequest.getTargets(), TARGET_PARAM),
          TerraformHelperUtils.generateCommandFlagsString(
              terraformDestroyCommandRequest.getVarFilePaths(), VAR_FILE_PARAM),
          additionalCliOption);
    }
    return executeTerraformCLICommand(command, timeoutInMillis, envVariables, scriptDirectory, executionLogCallback,
        command, new LogCallbackOutputStream(executionLogCallback));
  }

  @Nonnull
  @Override
  public CliResponse plan(TerraformPlanCommandRequest terraformPlanCommandRequest, long timeoutInMillis,
      Map<String, String> envVariables, String scriptDirectory, @Nonnull LogCallback executionLogCallback)
      throws InterruptedException, TimeoutException, IOException {
    String additionalCliOption = getAdditionalCliOption(terraformPlanCommandRequest.getAdditionalCliFlags(), PLAN);
    String command;
    if (terraformPlanCommandRequest.isDestroySet()) {
      if (terraformPlanCommandRequest.isTerraformCloudCli()) {
        // terraform Cloud var files are located in script directory having this suffix .auto.tfvars, so we don't pass
        // them as arguments -var-file
        command = format("terraform plan -input=false -detailed-exitcode -destroy %s %s",
            TerraformHelperUtils.generateCommandFlagsString(terraformPlanCommandRequest.getTargets(), TARGET_PARAM),
            additionalCliOption);
      } else {
        command = format("terraform plan -input=false -detailed-exitcode -destroy -out=tfdestroyplan %s %s %s",
            TerraformHelperUtils.generateCommandFlagsString(terraformPlanCommandRequest.getTargets(), TARGET_PARAM),
            TerraformHelperUtils.generateCommandFlagsString(
                terraformPlanCommandRequest.getVarFilePaths(), VAR_FILE_PARAM),
            additionalCliOption);
      }
    } else {
      if (terraformPlanCommandRequest.isTerraformCloudCli()) {
        command = format("terraform plan -input=false -detailed-exitcode %s %s",
            TerraformHelperUtils.generateCommandFlagsString(terraformPlanCommandRequest.getTargets(), TARGET_PARAM),
            additionalCliOption);
      } else {
        command = format("terraform plan -input=false -detailed-exitcode -out=tfplan %s %s %s",
            TerraformHelperUtils.generateCommandFlagsString(terraformPlanCommandRequest.getTargets(), TARGET_PARAM),
            TerraformHelperUtils.generateCommandFlagsString(
                terraformPlanCommandRequest.getVarFilePaths(), VAR_FILE_PARAM),
            additionalCliOption);
      }
    }

    if (isNotEmpty(terraformPlanCommandRequest.getVarParams())) {
      String loggingCommand = command + terraformPlanCommandRequest.getUiLogs();
      command = command + terraformPlanCommandRequest.getVarParams();

      return executeTerraformCLICommandWithDetailedExitCode(command, timeoutInMillis, envVariables, scriptDirectory,
          executionLogCallback, loggingCommand, new LogCallbackOutputStream(executionLogCallback));
    }

    return executeTerraformCLICommandWithDetailedExitCode(command, timeoutInMillis, envVariables, scriptDirectory,
        executionLogCallback, command, new LogCallbackOutputStream(executionLogCallback));
  }

  @Nonnull
  @Override
  public CliResponse refresh(TerraformRefreshCommandRequest terraformRefreshCommandRequest, long timeoutInMillis,
      Map<String, String> envVariables, String scriptDirectory, @Nonnull LogCallback executionLogCallback)
      throws InterruptedException, TimeoutException, IOException {
    String additionalCliOption =
        getAdditionalCliOption(terraformRefreshCommandRequest.getAdditionalCliFlags(), REFRESH);

    String command;
    command = "terraform refresh -input=false "
        + TerraformHelperUtils.generateCommandFlagsString(terraformRefreshCommandRequest.getTargets(), TARGET_PARAM)
        + TerraformHelperUtils.generateCommandFlagsString(
            terraformRefreshCommandRequest.getVarFilePaths(), VAR_FILE_PARAM)
        + additionalCliOption;

    if (isNotEmpty(terraformRefreshCommandRequest.getVarParams())) {
      String loggingCommand = command + terraformRefreshCommandRequest.getUiLogs();
      command = command + terraformRefreshCommandRequest.getVarParams();
      return executeTerraformCLICommand(command, timeoutInMillis, envVariables, scriptDirectory, executionLogCallback,
          loggingCommand, new LogCallbackOutputStream(executionLogCallback));
    }
    return executeTerraformCLICommand(command, timeoutInMillis, envVariables, scriptDirectory, executionLogCallback,
        command, new LogCallbackOutputStream(executionLogCallback));
  }

  @Nonnull
  @Override
  public CliResponse apply(TerraformApplyCommandRequest terraformApplyCommandRequest, long timeoutInMillis,
      Map<String, String> envVariables, String scriptDirectory, @Nonnull LogCallback executionLogCallback)
      throws InterruptedException, TimeoutException, IOException {
    String additionalCliOption = getAdditionalCliOption(terraformApplyCommandRequest.getAdditionalCliFlags(), APPLY);
    String command;
    if (terraformApplyCommandRequest.isTerraformCloudCli()) {
      // terraform Cloud var files are located in script directory having this suffix .auto.tfvars, so we don't pass
      // them as arguments -var-file
      command = format("echo yes | terraform apply %s %s",
          TerraformHelperUtils.generateCommandFlagsString(terraformApplyCommandRequest.getTargets(), TARGET_PARAM),
          additionalCliOption);
    } else {
      command =
          format("terraform apply -input=false %s %s", additionalCliOption, terraformApplyCommandRequest.getPlanName());
    }
    return executeTerraformCLICommand(command, timeoutInMillis, envVariables, scriptDirectory, executionLogCallback,
        command, new LogCallbackOutputStream(executionLogCallback));
  }

  @Nonnull
  @Override
  public CliResponse workspace(String workspace, boolean isExistingWorkspace, long timeoutInMillis,
      Map<String, String> envVariables, String scriptDirectory, @Nonnull LogCallback executionLogCallback,
      Map<String, String> additionalCliFlags) throws InterruptedException, TimeoutException, IOException {
    String additionalCliOption = getAdditionalCliOption(additionalCliFlags, WORKSPACE);
    String command = isExistingWorkspace ? "terraform workspace select " + workspace
                                         : format("terraform workspace new %s %s", additionalCliOption, workspace);
    return executeTerraformCLICommand(command, timeoutInMillis, envVariables, scriptDirectory, executionLogCallback,
        command, new LogCallbackOutputStream(executionLogCallback));
  }

  @Nonnull
  @Override
  public CliResponse getWorkspaceList(long timeoutInMillis, Map<String, String> envVariables, String scriptDirectory,
      @Nonnull LogCallback executionLogCallback) throws InterruptedException, TimeoutException, IOException {
    String command = "terraform workspace list";
    return executeTerraformCLICommand(command, timeoutInMillis, envVariables, scriptDirectory, executionLogCallback,
        command, new LogCallbackOutputStream(executionLogCallback));
  }

  @Nonnull
  @Override
  public CliResponse show(String planName, long timeoutInMillis, Map<String, String> envVariables,
      String scriptDirectory, @Nonnull LogCallback executionLogCallback,
      @Nonnull PlanJsonLogOutputStream planJsonLogOutputStream)
      throws InterruptedException, TimeoutException, IOException {
    TerraformVersion version = version(timeoutInMillis, scriptDirectory);
    if (!version.minVersion(0, 12)) {
      String messageFormat = "Terraform plan json export not supported in v%d.%d.%d. Minimum version is v0.12.x. "
          + "Skipping command.";
      String message = format(messageFormat, version.getMajor(), version.getMinor(), version.getPatch());
      executionLogCallback.saveExecutionLog(
          color("\n" + message + "\n", Yellow, Bold), WARN, CommandExecutionStatus.SKIPPED);
      planJsonLogOutputStream.setTfPlanShowJsonStatus(CommandExecutionStatus.SKIPPED);
      return CliResponse.builder().commandExecutionStatus(CommandExecutionStatus.SKIPPED).build();
    }
    planJsonLogOutputStream.setTfPlanShowJsonStatus(CommandExecutionStatus.SUCCESS);
    String command = "terraform show -json " + planName;
    return executeTerraformCLICommand(command, timeoutInMillis, envVariables, scriptDirectory, executionLogCallback,
        command, planJsonLogOutputStream);
  }

  @Nonnull
  @Override
  public CliResponse show(String planName, long timeoutInMillis, Map<String, String> envVariables,
      String scriptDirectory, @Nonnull LogCallback executionLogCallback,
      @Nonnull PlanLogOutputStream planLogOutputStream) throws InterruptedException, TimeoutException, IOException {
    TerraformVersion version = version(timeoutInMillis, scriptDirectory);
    String command = null;
    if (!version.minVersion(0, 12)) {
      String messageFormat = "Terraform plan json export not supported in v%d.%d.%d. Minimum version is v0.12.x. "
          + "Using regular (no json) terraform plan";
      String message = format(messageFormat, version.getMajor(), version.getMinor(), version.getPatch());
      executionLogCallback.saveExecutionLog(
          color("\n" + message + "\n", Yellow, Bold), WARN, CommandExecutionStatus.SKIPPED);
      command = format("terraform show %s", planName);
    } else {
      command = format("terraform show -json %s", planName);
    }

    return executeTerraformCLICommand(
        command, timeoutInMillis, envVariables, scriptDirectory, executionLogCallback, command, planLogOutputStream);
  }

  @Nonnull
  @Override
  @VisibleForTesting
  public CliResponse prepareHumanReadablePlan(String planName, long timeoutInMillis, Map<String, String> envVariables,
      String scriptDirectory, @Nonnull LogCallback executionLogCallback,
      @Nonnull PlanHumanReadableOutputStream planHumanReadableOutputStream)
      throws InterruptedException, TimeoutException, IOException {
    String command = null;
    String message = "Generating Human Readable Plan";
    executionLogCallback.saveExecutionLog(
        color("\n" + message + "\n", Yellow, Bold), WARN, CommandExecutionStatus.SKIPPED);
    command = format("terraform show %s", planName);

    return executeTerraformCLICommand(command, timeoutInMillis, envVariables, scriptDirectory, executionLogCallback,
        command, planHumanReadableOutputStream);
  }

  @Nonnull
  @Override
  public CliResponse output(String tfOutputsFile, long timeoutInMillis, Map<String, String> envVariables,
      String scriptDirectory, @Nonnull LogCallback executionLogCallback)
      throws InterruptedException, TimeoutException, IOException {
    String command = "terraform output -json > " + tfOutputsFile;
    return executeTerraformCLICommand(command, timeoutInMillis, envVariables, scriptDirectory, executionLogCallback,
        command, new LogCallbackOutputStream(executionLogCallback));
  }

  @NotNull
  @Override
  public TerraformVersion version(long timeoutInMillis, String scriptDirectory)
      throws InterruptedException, TimeoutException, IOException {
    String command = "terraform version";
    CliResponse response = cliHelper.executeCliCommand(
        command, timeoutInMillis, Collections.emptyMap(), scriptDirectory, new NoopExecutionCallback());
    if (response.getCommandExecutionStatus() == CommandExecutionStatus.SUCCESS) {
      return TerraformVersion.create(response.getOutput());
    }

    return TerraformVersion.createDefault();
  }

  @NotNull
  @Override
  public TerraformVersion version(String tfBinaryPath, long timeoutInMillis, String scriptDirectory)
      throws InterruptedException, TimeoutException, IOException {
    String command = tfBinaryPath + " version";
    CliResponse response = cliHelper.executeCliCommand(
        command, timeoutInMillis, Collections.emptyMap(), scriptDirectory, new NoopExecutionCallback());
    if (response.getCommandExecutionStatus() == CommandExecutionStatus.SUCCESS) {
      return TerraformVersion.create(response.getOutput());
    }

    return TerraformVersion.createDefault();
  }

  @VisibleForTesting
  CliResponse executeTerraformCLICommand(String command, long timeoutInMillis, Map<String, String> envVariables,
      String scriptDirectory, LogCallback executionLogCallBack, String loggingCommand, LogOutputStream logOutputStream)
      throws IOException, InterruptedException, TimeoutException, TerraformCommandExecutionException {
    CliResponse response = getCliResponse(
        command, timeoutInMillis, envVariables, scriptDirectory, executionLogCallBack, loggingCommand, logOutputStream);
    if (response.getCommandExecutionStatus() == CommandExecutionStatus.FAILURE) {
      throw new TerraformCliRuntimeException(
          format("Failed to execute terraform Command %s : Reason: %s", command, response.getError()), command,
          response.getError());
    }
    return response;
  }

  /**
   * -detailed-exitcode
   * 0 = Succeeded with empty diff (no changes)
   * 1 = Error
   * 2 = Succeeded with non-empty diff (changes present)
   */
  @VisibleForTesting
  CliResponse executeTerraformCLICommandWithDetailedExitCode(String command, long timeoutInMillis,
      Map<String, String> envVariables, String scriptDirectory, LogCallback executionLogCallBack, String loggingCommand,
      LogOutputStream logOutputStream)
      throws IOException, InterruptedException, TimeoutException, TerraformCommandExecutionException {
    CliResponse response = getCliResponse(
        command, timeoutInMillis, envVariables, scriptDirectory, executionLogCallBack, loggingCommand, logOutputStream);

    if (response.getCommandExecutionStatus() == CommandExecutionStatus.FAILURE) {
      if (response.getExitCode() != 1) {
        response.setCommandExecutionStatus(CommandExecutionStatus.SUCCESS);
      } else {
        throw new TerraformCliRuntimeException(
            format("Failed to execute terraform Command %s : Reason: %s", command, response.getError()), command,
            response.getError());
      }
    }
    return response;
  }

  @NotNull
  private CliResponse getCliResponse(String command, long timeoutInMillis, Map<String, String> envVariables,
      String scriptDirectory, LogCallback executionLogCallBack, String loggingCommand, LogOutputStream logOutputStream)
      throws IOException, InterruptedException, TimeoutException {
    if (!Files.exists(Paths.get(scriptDirectory))) {
      String noDirExistErrorMsg = format("Could not find provided terraform config folder [%s]", scriptDirectory);
      throw new TerraformCliRuntimeException(
          format("Failed to execute terraform Command %s : Reason: %s", command, noDirExistErrorMsg), command,
          noDirExistErrorMsg);
    }

    return cliHelper.executeCliCommand(command, timeoutInMillis, envVariables, scriptDirectory, executionLogCallBack,
        loggingCommand, logOutputStream, new TerraformCliErrorLogOutputStream(executionLogCallBack),
        SECONDS_TO_WAIT_FOR_GRACEFUL_SHUTDOWN);
  }

  private String getAdditionalCliOption(Map<String, String> additionalCliFlags, String tfCommand) {
    String additionalCliOption = EMPTY;
    if (additionalCliFlags != null) {
      additionalCliOption = additionalCliFlags.getOrDefault(tfCommand, EMPTY);
    }

    return additionalCliOption;
  }
}
