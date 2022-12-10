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
import static io.harness.provision.TerragruntConstants.TARGET_FORMAT;
import static io.harness.provision.TerragruntConstants.VAR_FILE_FORMAT;

import static software.wings.beans.LogHelper.color;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.version.Version;
import io.harness.cli.CliHelper;
import io.harness.cli.CliResponse;
import io.harness.cli.LogCallbackOutputStream;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.terragrunt.v2.request.AbstractTerragruntCliRequest;
import io.harness.terragrunt.v2.request.TerragruntCliRequest;
import io.harness.terragrunt.v2.request.TerragruntRunType;
import io.harness.terragrunt.v2.request.TerragruntShowCliRequest;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.jetbrains.annotations.NotNull;
import org.zeroturnaround.exec.stream.LogOutputStream;

@Builder
@OwnedBy(CDP)
@AllArgsConstructor(onConstructor = @__({ @Nonnull }))
public class TerragruntClientImpl implements TerragruntClient {
  private static final Pattern TF_LOG_LINE_PATTERN =
      Pattern.compile("\\[(?:TRACE|DEBUG|INFO|WARN|ERROR|CRITICAL)\\]\\s?(.+?)?:");
  private static final Version MIN_TF_SHOW_JSON_VERSION = Version.parse("0.12");

  private Version terragruntVersion;
  private Version terraformVersion;
  private CliHelper cliHelper;

  @Nonnull
  @Override
  public CliResponse init(@Nonnull TerragruntCliRequest request, @Nonnull LogCallback logCallback)
      throws InterruptedException, TimeoutException, IOException {
    String command = TerragruntCommandUtils.init(request.getArgs().getBackendConfigFile());
    return executeCliCommand(command, request, logCallback, new LogCallbackOutputStream(logCallback));
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

    return executeCliCommand(command, request, logCallback, new LogCallbackOutputStream(logCallback));
  }

  @NotNull
  @Override
  public CliResponse plan(@NotNull TerragruntCliRequest request, LogOutputStream planOutputStream,
      @NotNull LogCallback logCallback) throws InterruptedException, TimeoutException, IOException {
    String targetArgs = getTargetArgs(request.getArgs().getTargets());
    String varArgs = getVarArgs(request.getArgs().getVarFiles());
    String command = TerragruntRunType.RUN_ALL == request.getRunType()
        ? TerragruntCommandUtils.runAllPlan(targetArgs, varArgs)
        : TerragruntCommandUtils.plan(targetArgs, varArgs);
    return executeCliCommand(command, request, logCallback, planOutputStream);
  }

  @NotNull
  @Override
  public CliResponse show(@NotNull TerragruntShowCliRequest request, @NotNull LogCallback logCallback)
      throws IOException, InterruptedException, TimeoutException {
    if (request.isJson() && terraformVersion.compareTo(MIN_TF_SHOW_JSON_VERSION) < 0) {
      String message = format(
          "Command terragrunt show -json is not supported by terraform version '%s'. Minimum required version is v0.12.x.",
          terraformVersion);
      logCallback.saveExecutionLog(message, WARN);
      return CliResponse.builder().commandExecutionStatus(CommandExecutionStatus.SKIPPED).output(message).build();
    }

    String command = TerragruntRunType.RUN_ALL == request.getRunType()
        ? TerragruntCommandUtils.runAllShow(request.isJson(), request.getPlanName())
        : TerragruntCommandUtils.show(request.isJson(), request.getPlanName());
    return executeCliCommand(command, request, logCallback, request.getOutputStream());
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
    return cliHelper.executeCliCommand(command, cliRequest.getTimeoutInMillis(), cliRequest.getEnvVars(),
        cliRequest.getWorkingDirectory(), logCallback, command, outputStream,
        logLine -> isNotEmpty(logLine) && !TF_LOG_LINE_PATTERN.matcher(logLine).find());
  }
}
