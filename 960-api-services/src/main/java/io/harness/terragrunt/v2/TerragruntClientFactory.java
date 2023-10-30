/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.terragrunt.v2;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.provision.TerragruntConstants.TERRAGRUNT_INFO_TF_BINARY_JSON_PATH;

import static java.lang.String.format;
import static java.util.regex.Pattern.CASE_INSENSITIVE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.version.Version;
import io.harness.cli.CliCommandRequest;
import io.harness.cli.CliHelper;
import io.harness.cli.CliResponse;
import io.harness.cli.EmptyLogOutputStream;
import io.harness.cli.TerraformCliErrorLogOutputStream;
import io.harness.exception.runtime.TerragruntCliRuntimeException;
import io.harness.logging.LogCallback;
import io.harness.logging.NoopExecutionCallback;
import io.harness.serializer.JsonUtils;
import io.harness.terraform.beans.TerraformVersion;
import io.harness.terragrunt.v2.request.TerragruntRunType;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@Singleton
@OwnedBy(CDP)
public class TerragruntClientFactory {
  public static final Pattern TG_VERSION_REGEX = Pattern.compile("v(\\d+).(\\d+).(\\d+)", CASE_INSENSITIVE);
  private static final String FALLBACK_TG_INFO_OUTPUT = "{}";
  private static final Version MIN_FALLBACK_VERSION = Version.parse("0.0.1");
  private static final String TERRAFORM_BINARY_VALUE = "terraform";

  @Inject private CliHelper cliHelper;

  public TerragruntClient getClient(String tgScriptDirectory, long timeoutInMillis, LogCallback logCallback,
      String runType, Map<String, String> ennVars, boolean skipColorLogs) {
    String terragruntInfoJson = "{}";
    String terraformPath = TERRAFORM_BINARY_VALUE;
    if (TerragruntRunType.RUN_MODULE.name().equalsIgnoreCase(runType)) {
      // When run-all from outside concrete module we don't need to run terragrunt terragrunt-info, because there might
      // be no terragrunt.hcl
      terragruntInfoJson =
          getTerragruntInfoJson(tgScriptDirectory, timeoutInMillis, logCallback, ennVars, skipColorLogs);
      try {
        terraformPath = JsonUtils.jsonPath(terragruntInfoJson, TERRAGRUNT_INFO_TF_BINARY_JSON_PATH);
      } catch (Exception e) {
        terraformPath = TERRAFORM_BINARY_VALUE;
      }
    }

    return TerragruntClientImpl.builder()
        .terragruntInfoJson(terragruntInfoJson)
        .terraformVersion(
            getTerraformVersion(tgScriptDirectory, terraformPath, timeoutInMillis, logCallback, ennVars, skipColorLogs))
        .terragruntVersion(
            getTerragruntVersion(tgScriptDirectory, timeoutInMillis, logCallback, ennVars, skipColorLogs))
        .cliHelper(cliHelper)
        .build();
  }

  private Version getTerraformVersion(String tgScriptDirectory, String terraformPath, long timeout,
      LogCallback logCallback, Map<String, String> envVars, boolean skipColorLogs) {
    String command = format("%s version", terraformPath);
    CliCommandRequest request =
        CliCommandRequest.builder()
            .command(command)
            .timeoutInMillis(timeout)
            .envVariables(envVars)
            .directory(tgScriptDirectory)
            .logCallback(new NoopExecutionCallback())
            .loggingCommand(command)
            .logOutputStream(new EmptyLogOutputStream())
            .errorLogOutputStream(new TerraformCliErrorLogOutputStream(logCallback, skipColorLogs))
            .secondsToWaitForGracefulShutdown(0)
            .build();
    String tfVersionOutput = executeLocalCommand(request, null);
    return createVersion(tfVersionOutput, TerraformVersion.TF_VERSION_REGEX);
  }

  private Version getTerragruntVersion(String tgScriptDirectory, long timeout, LogCallback logCallback,
      Map<String, String> envVars, boolean skipColorLogs) {
    CliCommandRequest request =
        CliCommandRequest.builder()
            .command(TerragruntCommandUtils.version())
            .timeoutInMillis(timeout)
            .envVariables(envVars)
            .directory(tgScriptDirectory)
            .logCallback(new NoopExecutionCallback())
            .loggingCommand(TerragruntCommandUtils.version())
            .logOutputStream(new EmptyLogOutputStream())
            .errorLogOutputStream(new TerraformCliErrorLogOutputStream(logCallback, skipColorLogs))
            .secondsToWaitForGracefulShutdown(0)
            .build();

    String tgVersionOutput = executeLocalCommand(request, null);
    return createVersion(tgVersionOutput, TG_VERSION_REGEX);
  }

  private String getTerragruntInfoJson(String tgScriptDirectory, long timeout, LogCallback logCallback,
      Map<String, String> envVars, boolean skipColorLogs) {
    CliCommandRequest request =
        CliCommandRequest.builder()
            .command(TerragruntCommandUtils.info())
            .timeoutInMillis(timeout)
            .envVariables(envVars)
            .directory(tgScriptDirectory)
            .logCallback(new NoopExecutionCallback())
            .loggingCommand(TerragruntCommandUtils.info())
            .logOutputStream(new EmptyLogOutputStream())
            .errorLogOutputStream(new TerraformCliErrorLogOutputStream(logCallback, skipColorLogs))
            .secondsToWaitForGracefulShutdown(0)
            .build();

    return executeLocalCommand(request, FALLBACK_TG_INFO_OUTPUT);
  }

  private String executeLocalCommand(CliCommandRequest request, String defaultOutput) {
    try {
      CliResponse result = cliHelper.executeCliCommand(request);

      if (result.getExitCode() != 0) {
        log.error(format("Command [%s] failed with exit code [%d] and error: %s", request.getCommand(),
            result.getExitCode(), result.getOutput()));
        throw new TerragruntCliRuntimeException(
            format("Failed to execute terraform Command %s : Reason: %s", request.getCommand(), result.getError()),
            request.getCommand(), result.getError());
      }

      return result.getOutput();
    }

    catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.error(format("Exception while executing [%s]", request.getCommand()), e);
      throw new TerragruntCliRuntimeException("Thread was interrupted:", e);
    } catch (IOException | TimeoutException e) {
      log.error(format("Exception while executing [%s]", request.getCommand()), e);
      return defaultOutput;
    }
  }

  private static Version createVersion(String output, Pattern versionPattern) {
    if (output == null) {
      return MIN_FALLBACK_VERSION;
    }

    Matcher matcher = versionPattern.matcher(output);

    if (!matcher.find()) {
      return MIN_FALLBACK_VERSION;
    }

    String matcherResult = matcher.group(0);
    String version = null;
    if (StringUtils.containsIgnoreCase(output, "terraform") || StringUtils.containsIgnoreCase(output, "terragrunt")) {
      version = matcherResult.replace("v", "");
    }
    return Version.parse(version != null ? version : matcherResult);
  }
}