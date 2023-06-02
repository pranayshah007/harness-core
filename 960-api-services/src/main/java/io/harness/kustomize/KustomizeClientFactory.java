/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.kustomize;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.chartmuseum.ChartMuseumConstants.VERSION_PATTERN;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.version.Version;
import io.harness.cli.CliHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import lombok.extern.slf4j.Slf4j;
import org.zeroturnaround.exec.ProcessResult;

@Slf4j
@Singleton
@OwnedBy(CDP)
public class KustomizeClientFactory {
  private static final Version KUSTOMIZE_DEFAULT_VERSION = Version.parse("0.0.1");
  private static final String VERSION = "version";
  @Inject private CliHelper cliHelper;

  public KustomizeClient getClient(String kustomizeBinaryPath, Map<String, String> cmdFlags) {
    String versionCmd = kustomizeBinaryPath + " " + VERSION;
    Version version = getVersion(versionCmd);
    return KustomizeClientImpl.builder()
        .kustomizeBinaryPath(kustomizeBinaryPath)
        .commandFlags(cmdFlags)
        .version(version)
        .cliHelper(cliHelper)
        .build();
  }

  public Version getVersion(String command) {
    try {
      ProcessResult versionResult = cliHelper.executeCommand(command);

      if (versionResult.getExitValue() != 0) {
        log.warn("Failed to get kustomize version. Exit code: {}, output: {}", versionResult.getExitValue(),
            versionResult.hasOutput() ? versionResult.outputUTF8() : "no output");
        return KUSTOMIZE_DEFAULT_VERSION;
      }

      if (versionResult.hasOutput()) {
        String versionOutput = versionResult.outputUTF8();
        Matcher versionMatcher = VERSION_PATTERN.matcher(versionOutput);
        if (!versionMatcher.find()) {
          log.warn("No valid KUSTOMIZE version present in output: {}", versionOutput);
          return KUSTOMIZE_DEFAULT_VERSION;
        }

        return Version.parse(versionMatcher.group(1));
      }

    } catch (IOException | TimeoutException e) {
      log.error("Failed to get kustomize version", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.error("Failed to get kustomize version", e);
    }

    return KUSTOMIZE_DEFAULT_VERSION;
  }
}
