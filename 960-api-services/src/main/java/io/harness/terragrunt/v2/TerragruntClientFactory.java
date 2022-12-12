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
import io.harness.cli.CliHelper;
import io.harness.cli.CliResponse;
import io.harness.logging.NoopExecutionCallback;
import io.harness.serializer.JsonUtils;
import io.harness.terraform.beans.TerraformVersion;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Collections;
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

  @Inject private CliHelper cliHelper;

  public TerragruntClient getClient(String tgScriptDirectory, long timeoutInMillis) {
    String terragruntInfoJson = getTerragruntInfoJson(tgScriptDirectory, timeoutInMillis);
    String terraformPath = JsonUtils.jsonPath(terragruntInfoJson, TERRAGRUNT_INFO_TF_BINARY_JSON_PATH);
    return TerragruntClientImpl.builder()
        .terragruntInfoJson(terragruntInfoJson)
        .terraformVersion(getTerraformVersion(tgScriptDirectory, terraformPath, timeoutInMillis))
        .terragruntVersion(getTerragruntVersion(tgScriptDirectory, timeoutInMillis))
        .cliHelper(cliHelper)
        .build();
  }

  private Version getTerraformVersion(String tgScriptDirectory, String terraformPath, long timeout) {
    String command = format("%s version", terraformPath);
    String tfVersionOutput = executeLocalCommand(command, tgScriptDirectory, null, timeout);
    return createVersion(tfVersionOutput, TerraformVersion.TF_VERSION_REGEX);
  }

  private Version getTerragruntVersion(String tgScriptDirectory, long timeout) {
    String tgVersionOutput = executeLocalCommand(TerragruntCommandUtils.version(), tgScriptDirectory, null, timeout);
    return createVersion(tgVersionOutput, TG_VERSION_REGEX);
  }

  private String getTerragruntInfoJson(String tgScriptDirectory, long timeout) {
    return executeLocalCommand(TerragruntCommandUtils.info(), tgScriptDirectory, FALLBACK_TG_INFO_OUTPUT, timeout);
  }

  private String executeLocalCommand(String command, String pwd, String defaultOutput, long timeoutInMillis) {
    try {
      CliResponse result = cliHelper.executeCliCommand(
          command, timeoutInMillis, Collections.emptyMap(), pwd, new NoopExecutionCallback());
      if (result.getExitCode() != 0) {
        log.error(format("Command [%s] failed with exit code [%d] and error: %s", command, result.getExitCode(),
            result.getOutput()));
        return defaultOutput;
      }

      return result.getOutput();
    } catch (IOException | InterruptedException | TimeoutException e) {
      Thread.currentThread().interrupt();
      log.error(format("Exception while executing [%s]", command), e);
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