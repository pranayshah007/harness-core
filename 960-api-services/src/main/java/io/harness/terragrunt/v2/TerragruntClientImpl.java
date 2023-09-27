/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.terragrunt.v2;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.logging.LogLevel.WARN;
import static io.harness.provision.TerraformConstants.SECONDS_TO_WAIT_FOR_GRACEFUL_SHUTDOWN;
import static io.harness.provision.TerragruntConstants.TARGET_FORMAT;
import static io.harness.provision.TerragruntConstants.VAR_FILE_FORMAT;
import static io.harness.terragrunt.v2.TerragruntV2Contants.APPLY;
import static io.harness.terragrunt.v2.TerragruntV2Contants.DESTROY;
import static io.harness.terragrunt.v2.TerragruntV2Contants.INIT;
import static io.harness.terragrunt.v2.TerragruntV2Contants.PLAN;
import static io.harness.terragrunt.v2.TerragruntV2Contants.WORKSPACE;

import static software.wings.beans.LogHelper.color;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.EMPTY;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.version.Version;
import io.harness.cli.CliCommandRequest;
import io.harness.cli.CliHelper;
import io.harness.cli.CliResponse;
import io.harness.cli.LogCallbackOutputStream;
import io.harness.cli.TerraformCliErrorLogOutputStream;
import io.harness.exception.HintException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.serializer.JsonUtils;
import io.harness.terragrunt.v2.request.AbstractTerragruntCliRequest;
import io.harness.terragrunt.v2.request.TerragruntApplyCliRequest;
import io.harness.terragrunt.v2.request.TerragruntCliRequest;
import io.harness.terragrunt.v2.request.TerragruntDestroyCliRequest;
import io.harness.terragrunt.v2.request.TerragruntOutputCliRequest;
import io.harness.terragrunt.v2.request.TerragruntPlanCliRequest;
import io.harness.terragrunt.v2.request.TerragruntRunType;
import io.harness.terragrunt.v2.request.TerragruntShowCliRequest;
import io.harness.terragrunt.v2.request.TerragruntWorkspaceCliRequest;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.zeroturnaround.exec.stream.LogOutputStream;

@Builder
@AllArgsConstructor(onConstructor = @__({ @Nonnull }))
@Slf4j
@OwnedBy(CDP)
public class TerragruntClientImpl implements TerragruntClient {
  private static final Pattern TF_LOG_LINE_PATTERN =
      Pattern.compile("\\[(?:TRACE|DEBUG|INFO|WARN|ERROR|CRITICAL)\\]\\s?(.+?)?:");
  private static final Version MIN_TF_SHOW_JSON_VERSION = Version.parse("0.12");
  private static final Version MIN_TF_AUTO_APPROVE_VERSION = Version.parse("0.15");

  private String terragruntInfoJson;
  private Version terragruntVersion;
  private Version terraformVersion;
  private CliHelper cliHelper;

  @Nonnull
  @Override
  public CliResponse init(@Nonnull TerragruntCliRequest request, @Nonnull LogCallback logCallback)
      throws InterruptedException, TimeoutException, IOException {
    String additionalCliOptions = getAdditionalCliOption(request.getArgs().getAdditionalCliArgs(), INIT);
    String command = TerragruntCommandUtils.init(
        request.getArgs().getBackendConfigFile(), request.getRunType(), additionalCliOptions);
    log.info("Execute terragrunt init: {}", command);
    return executeCliCommand(command, request, logCallback, new LogCallbackOutputStream(logCallback));
  }

  @NotNull
  @Override
  public CliResponse workspace(@NotNull TerragruntWorkspaceCliRequest request, @NotNull LogCallback logCallback)
      throws InterruptedException, TimeoutException, IOException {
    CliResponse workspaceListResponse = executeCliCommand(TerragruntCommandUtils.workspaceList(request.getRunType()),
        request, logCallback, new LogCallbackOutputStream(logCallback));
    if (CommandExecutionStatus.SUCCESS != workspaceListResponse.getCommandExecutionStatus()) {
      return workspaceListResponse;
    }

    Set<String> existingWorkspaces = parseWorkspaceList(workspaceListResponse.getOutput());
    if (existingWorkspaces.contains(request.getWorkspace())) {
      String command = TerragruntCommandUtils.workspaceSelect(request.getWorkspace(), request.getRunType());
      log.info("Workspace {} exists in workspaces set: {}", request.getWorkspace(), existingWorkspaces);
      return executeCliCommand(command, request, logCallback, new LogCallbackOutputStream(logCallback));
    } else {
      log.info("No workspace {} found in workspaces set {}", request.getWorkspace(), existingWorkspaces);
      logCallback.saveExecutionLog(format("Workspace %s doesn't exist, create a new one", request.getWorkspace()));
      String additionalCliOptions = getAdditionalCliOption(request.getArgs().getAdditionalCliArgs(), WORKSPACE);
      String command =
          TerragruntCommandUtils.workspaceNew(request.getWorkspace(), request.getRunType(), additionalCliOptions);
      return executeCliCommand(command, request, logCallback, new LogCallbackOutputStream(logCallback));
    }
  }

  @Nonnull
  @Override
  public CliResponse refresh(@NotNull TerragruntCliRequest request, @NotNull LogCallback logCallback)
      throws InterruptedException, TimeoutException, IOException {
    String targetArgs = getTargetArgs(request.getArgs().getTargets());
    String varArgs = getVarArgs(request.getArgs().getVarFiles());
    String command = TerragruntRunType.RUN_ALL == request.getRunType()
        ? TerragruntCommandUtils.runAllRefresh(targetArgs, varArgs)
        : TerragruntCommandUtils.refresh(targetArgs, varArgs);
    log.info("Execute terragrunt refresh: {}", command);
    return executeCliCommand(command, request, logCallback, new LogCallbackOutputStream(logCallback));
  }

  @NotNull
  @Override
  public CliResponse plan(@NotNull TerragruntPlanCliRequest request, @NotNull LogCallback logCallback)
      throws InterruptedException, TimeoutException, IOException {
    String targetArgs = getTargetArgs(request.getArgs().getTargets());
    String varArgs = getVarArgs(request.getArgs().getVarFiles());
    String additionalCliOptions = getAdditionalCliOption(request.getArgs().getAdditionalCliArgs(), PLAN);

    String command = TerragruntRunType.RUN_ALL == request.getRunType()
        ? TerragruntCommandUtils.runAllPlan(targetArgs, varArgs, request.isDestroy(), additionalCliOptions)
        : TerragruntCommandUtils.plan(targetArgs, varArgs, request.isDestroy(), additionalCliOptions);
    log.info("Execute terragrunt plan: {}", command);
    return executeCliCommand(command, request, logCallback, new LogCallbackOutputStream(logCallback));
  }

  @NotNull
  @Override
  public CliResponse apply(@NotNull TerragruntApplyCliRequest request, @NotNull LogCallback logCallback)
      throws InterruptedException, TimeoutException, IOException {
    String targetArgs = getTargetArgs(request.getArgs().getTargets());
    String varArgs = getVarArgs(request.getArgs().getVarFiles());
    String additionalCliOptions = getAdditionalCliOption(request.getArgs().getAdditionalCliArgs(), APPLY);
    String command = TerragruntRunType.RUN_ALL == request.getRunType()
        ? TerragruntCommandUtils.runAllApply(targetArgs, varArgs, additionalCliOptions)
        : TerragruntCommandUtils.apply(request.getTerraformPlanName(), additionalCliOptions);

    log.info("Execute terragrunt apply: {}", command);
    return executeCliCommand(command, request, logCallback, new LogCallbackOutputStream(logCallback));
  }

  @NotNull
  @Override
  public CliResponse output(@NotNull TerragruntOutputCliRequest request, @NotNull LogCallback logCallback)
      throws InterruptedException, TimeoutException, IOException {
    String command = TerragruntRunType.RUN_ALL == request.getRunType()
        ? TerragruntCommandUtils.runAllOutput(request.getTerraformOutputsFile())
        : TerragruntCommandUtils.output(request.getTerraformOutputsFile());
    log.info("Execute terragrunt output: {}", command);
    return executeCliCommand(command, request, logCallback, new LogCallbackOutputStream(logCallback));
  }

  @NotNull
  @Override
  public CliResponse destroy(@NotNull TerragruntDestroyCliRequest request, @NotNull LogCallback logCallback)
      throws InterruptedException, TimeoutException, IOException {
    String targetArgs = getTargetArgs(request.getArgs().getTargets());
    String varArgs = getVarArgs(request.getArgs().getVarFiles());
    String additionalCliOptions = getAdditionalCliOption(request.getArgs().getAdditionalCliArgs(), DESTROY);

    String command = TerragruntRunType.RUN_ALL == request.getRunType()
        ? TerragruntCommandUtils.runAllDestroy(
            getAutoApproveArgument(terraformVersion), targetArgs, varArgs, additionalCliOptions)
        : TerragruntCommandUtils.destroy(
            getAutoApproveArgument(terraformVersion), targetArgs, varArgs, additionalCliOptions);

    log.info("Execute terragrunt destroy: {}", command);
    return executeCliCommand(command, request, logCallback, new LogCallbackOutputStream(logCallback));
  }

  @NotNull
  @Override
  public CliResponse show(@NotNull TerragruntShowCliRequest request, @NotNull LogCallback logCallback)
      throws IOException, InterruptedException, TimeoutException {
    if (request.isJson() && terraformVersion.compareTo(MIN_TF_SHOW_JSON_VERSION) < 0) {
      log.warn("Terragrunt export json is not supported for terraform version: {}", terraformVersion);
      String message = format(
          "Command terragrunt show -json is not supported by terraform version '%s'. Minimum required version is v0.12.x.",
          terraformVersion);
      logCallback.saveExecutionLog(message, WARN);
      return CliResponse.builder().commandExecutionStatus(CommandExecutionStatus.SKIPPED).output(message).build();
    }

    String command = TerragruntRunType.RUN_ALL == request.getRunType()
        ? TerragruntCommandUtils.runAllShow(request.isJson(), request.getPlanName())
        : TerragruntCommandUtils.show(request.isJson(), request.getPlanName());
    log.info("Execute terragrunt show: {}", command);
    return executeCliCommand(command, request, logCallback, request.getOutputStream());
  }

  @NotNull
  @Override
  public String terragruntWorkingDirectory() {
    try {
      return JsonUtils.jsonPath(terragruntInfoJson, "WorkingDir");
    } catch (Exception e) {
      throw new HintException("Please check your terragrunt configuration and path configuration",
          NestedExceptionUtils.hintWithExplanationException("Or check if terragrunt/terraform is installed on delegate",
              "Unable to detect terragrunt working directory in terragrunt info json:" + terragruntInfoJson,
              new InvalidRequestException("Unable to detect terragrunt working directory: " + e.getMessage())));
    }
  }

  private String getTargetArgs(List<String> targets) {
    StringBuilder targetArgs = new StringBuilder();
    if (isNotEmpty(targets)) {
      for (String target : targets) {
        targetArgs.append(format(TARGET_FORMAT, target));
      }
    }

    return targetArgs.toString();
  }

  private String getVarArgs(List<String> varFiles) {
    StringBuilder varArgs = new StringBuilder();
    if (isNotEmpty(varFiles)) {
      for (String varFile : varFiles) {
        varArgs.append(format(VAR_FILE_FORMAT, varFile));
      }
    }

    return varArgs.toString();
  }

  private CliResponse executeCliCommand(
      String command, AbstractTerragruntCliRequest cliRequest, LogCallback logCallback, LogOutputStream outputStream)
      throws IOException, InterruptedException, TimeoutException {
    logCallback.saveExecutionLog(color(command, LogColor.White, LogWeight.Bold), INFO, CommandExecutionStatus.RUNNING);
    CliCommandRequest request =
        CliCommandRequest.builder()
            .command(command)
            .timeoutInMillis(cliRequest.getTimeoutInMillis())
            .envVariables(cliRequest.getEnvVars())
            .directory(cliRequest.getWorkingDirectory())
            .logCallback(logCallback)
            .loggingCommand(command)
            .logOutputStream(outputStream)
            .errorLogOutputStream(new TerraformCliErrorLogOutputStream(logCallback, cliRequest.isSkipColorLogs()))
            .secondsToWaitForGracefulShutdown(SECONDS_TO_WAIT_FOR_GRACEFUL_SHUTDOWN)
            .build();

    return cliHelper.executeCliCommand(request);
  }

  private Set<String> parseWorkspaceList(String workspaceOutput) {
    log.info("Parse workspaces list: {}", workspaceOutput.replaceAll("\n", "\\\\n"));
    String[] outputs = StringUtils.split(workspaceOutput, "\n");
    Set<String> workspaces = new HashSet<>();
    for (String output : outputs) {
      if (output.charAt(0) == '*') {
        output = output.substring(1);
      }
      output = output.trim();
      workspaces.add(output);
    }
    return workspaces;
  }

  private String getAutoApproveArgument(Version version) {
    return version.compareTo(MIN_TF_AUTO_APPROVE_VERSION) < 0 ? "-force" : "-auto-approve";
  }

  private String getAdditionalCliOption(Map<String, String> additionalCliArgs, String tgCommand) {
    String additionalCliOption = EMPTY;
    if (additionalCliArgs != null) {
      additionalCliOption = additionalCliArgs.getOrDefault(tgCommand, EMPTY);
    }
    return additionalCliOption;
  }
}
