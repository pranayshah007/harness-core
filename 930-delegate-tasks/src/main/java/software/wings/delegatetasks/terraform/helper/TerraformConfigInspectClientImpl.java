/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.terraform.helper;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.clienttools.ClientTool.TERRAFORM_CONFIG_INSPECT;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.SPACE;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.data.structure.HarnessStringUtils;
import io.harness.delegate.clienttools.InstallUtils;
import io.harness.delegate.clienttools.TerraformConfigInspectVersion;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.exception.WingsException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;

import software.wings.delegatetasks.terraform.TerraformConfigInspectClient;

import com.google.inject.Singleton;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;
import org.zeroturnaround.exec.ProcessExecutor;

@Slf4j
@Singleton
@TargetModule(HarnessModule._960_API_SERVICES)
@OwnedBy(CDP)
public class TerraformConfigInspectClientImpl implements TerraformConfigInspectClient {
  private static final String jsonArg = "--json";

  @Override
  public List<String> parseFieldsUnderBlock(
      String directory, String category, TerraformConfigInspectVersion terraformConfigInspectVersion) {
    try {
      String config = executeTerraformInspect(directory, terraformConfigInspectVersion);
      JSONObject parsedConfig = new JSONObject(config);
      JSONObject blockVariables = parsedConfig.getJSONObject(category);
      List<String> fields = new ArrayList<>(blockVariables.keySet());
      if (!fields.isEmpty()) {
        return fields;
      }
      Optional<String> parsingError = parseError(parsedConfig);
      parsingError.ifPresent(error -> { throw new InvalidRequestException(error, WingsException.USER); });
      return fields;
    } catch (JSONException err) {
      throw new InvalidRequestException(
          ExceptionMessageSanitizer.sanitizeException(err).getMessage(), WingsException.USER);
    }
  }

  String executeTerraformInspect(
      final String directory, final TerraformConfigInspectVersion terraformConfigInspectVersion) {
    String cmd = getPath(terraformConfigInspectVersion);
    return executeShellCommand(HarnessStringUtils.join(SPACE, cmd, jsonArg, directory));
  }

  private String getPath(TerraformConfigInspectVersion terraformConfigInspectVersion) {
    try {
      return InstallUtils.getPath(TERRAFORM_CONFIG_INSPECT, terraformConfigInspectVersion);
    } catch (Exception e) {
      return InstallUtils.getLatestVersionPath(TERRAFORM_CONFIG_INSPECT);
    }
  }

  /*
   Return non empty optional<String> if parsing failed
   Json JsonConfig will have a diagnostic block which would contain errors/warnings if any
   */
  private Optional<String> parseError(JSONObject jsonConfig) {
    if (jsonConfig.isNull(BLOCK_TYPE.DIAGNOSTICS.name().toLowerCase())) {
      return Optional.empty();
    }
    try {
      // Returning only the first block of error here
      JSONObject errorBlock = jsonConfig.getJSONArray(BLOCK_TYPE.DIAGNOSTICS.name().toLowerCase()).getJSONObject(0);
      String errorType = errorBlock.getString(BLOCK_TYPE.SEVERITY.name().toLowerCase());
      if (errorType.equalsIgnoreCase(ERROR_TYPE.ERROR.name().toLowerCase())) {
        String error = errorBlock.getString(BLOCK_TYPE.DETAIL.name().toLowerCase());
        JSONObject positionDetails = errorBlock.getJSONObject(BLOCK_TYPE.POS.name().toLowerCase());
        if (positionDetails != null) {
          String fileName =
              Paths.get(positionDetails.getString(BLOCK_TYPE.FILENAME.name().toLowerCase())).getFileName().toString();
          int line = positionDetails.getInt(BLOCK_TYPE.LINE.name().toLowerCase());
          error = isNotEmpty(fileName) ? error + "\nFile: " + fileName + SPACE + "Line: " + line : error;
        }
        return Optional.of(error);
      }
    } catch (JSONException e) {
      log.error("Could not parse tf-config-inspect output for errors {}", jsonConfig.toString());
    }
    return Optional.empty();
  }

  private String executeShellCommand(Map<String, String> env, String cmd) {
    ProcessExecutor executor = new ProcessExecutor();
    if (env != null) {
      executor.environment(env);
    }
    try {
      return executor.command("/bin/sh", "-c", cmd).readOutput(true).execute().outputUTF8().replace("\n", "");
    } catch (Exception ex) {
      throw new UnexpectedException(format("Could not execute command %s, env %s", cmd, env));
    }
  }

  String executeShellCommand(String cmd) {
    return executeShellCommand(null, cmd);
  }
}
