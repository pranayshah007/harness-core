/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.kustomize;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.kustomize.KustomizeConstants.COMMAND_FLAGS;
import static io.harness.kustomize.KustomizeConstants.KUSTOMIZE_BINARY_PATH;
import static io.harness.kustomize.KustomizeConstants.KUSTOMIZE_BUILD_COMMAND;
import static io.harness.kustomize.KustomizeConstants.KUSTOMIZE_BUILD_COMMAND_WITH_PLUGINS;
import static io.harness.kustomize.KustomizeConstants.KUSTOMIZE_COMMAND_TIMEOUT;
import static io.harness.kustomize.KustomizeConstants.KUSTOMIZE_DIR_PATH;
import static io.harness.kustomize.KustomizeConstants.KUSTOMIZE_PLUGIN_FLAG;
import static io.harness.kustomize.KustomizeConstants.KUSTOMIZE_PLUGIN_FLAG_LATEST;
import static io.harness.kustomize.KustomizeConstants.KUSTOMIZE_PLUGIN_FLAG_VERSION_LT_4_0_1;
import static io.harness.kustomize.KustomizeConstants.KUSTOMIZE_VERSION_V4_0_0;
import static io.harness.kustomize.KustomizeConstants.XDG_CONFIG_HOME;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.version.Version;
import io.harness.cli.CliHelper;
import io.harness.cli.CliResponse;
import io.harness.logging.LogCallback;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nonnull;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@Builder
@OwnedBy(CDP)
@Slf4j
public class KustomizeClientImpl implements KustomizeClient {
  private String kustomizeBinaryPath;
  private Version version;
  private Map<String, String> commandFlags;
  private CliHelper cliHelper;

  @Override
  public CliResponse build(String manifestFilesDir, String kustomizeDirPath, @Nonnull LogCallback executionLogCallback)
      throws InterruptedException, TimeoutException, IOException {
    String buildCommandFlag = getBuildCmdFlag(commandFlags);
    String kustomizeBuildCommand = KUSTOMIZE_BUILD_COMMAND.replace(KUSTOMIZE_BINARY_PATH, kustomizeBinaryPath)
                                       .replace(KUSTOMIZE_DIR_PATH, kustomizeDirPath)
                                       .replace(COMMAND_FLAGS, buildCommandFlag);
    return cliHelper.executeCliCommand(kustomizeBuildCommand, KUSTOMIZE_COMMAND_TIMEOUT, Collections.emptyMap(),
        manifestFilesDir, executionLogCallback);
  }

  @Override
  public CliResponse buildWithPlugins(String manifestFilesDir, String kustomizeDirPath, @Nonnull String pluginPath,
      @Nonnull LogCallback executionLogCallback) throws InterruptedException, TimeoutException, IOException {
    String buildCommandFlag = getBuildCmdFlag(commandFlags);
    String kustomizePluginFlag = version.compareTo(Version.parse(KUSTOMIZE_VERSION_V4_0_0)) > 0
        ? KUSTOMIZE_PLUGIN_FLAG_LATEST
        : KUSTOMIZE_PLUGIN_FLAG_VERSION_LT_4_0_1;

    String kustomizeBuildCommand =
        KUSTOMIZE_BUILD_COMMAND_WITH_PLUGINS.replace(KUSTOMIZE_BINARY_PATH, kustomizeBinaryPath)
            .replace(KUSTOMIZE_DIR_PATH, kustomizeDirPath)
            .replace(XDG_CONFIG_HOME, pluginPath)
            .replace(KUSTOMIZE_PLUGIN_FLAG, kustomizePluginFlag)
            .replace(COMMAND_FLAGS, buildCommandFlag);
    return cliHelper.executeCliCommand(kustomizeBuildCommand, KUSTOMIZE_COMMAND_TIMEOUT, Collections.emptyMap(),
        manifestFilesDir, executionLogCallback);
  }

  public String getBuildCmdFlag(Map<String, String> commandFlags) {
    if (commandFlags == null) {
      return "";
    }
    return commandFlags.containsKey(KustomizeCommand.BUILD.name()) ? commandFlags.get(KustomizeCommand.BUILD.name())
                                                                   : "";
  }
}
