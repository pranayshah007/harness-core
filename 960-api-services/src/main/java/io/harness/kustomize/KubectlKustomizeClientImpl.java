/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.kustomize;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.kustomize.KustomizeConstants.KUSTOMIZE_COMMAND_TIMEOUT;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.version.Version;
import io.harness.cli.CliHelper;
import io.harness.cli.CliResponse;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.k8s.kubectl.Kubectl;
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
public class KubectlKustomizeClientImpl implements KustomizeClient {
  private Kubectl kubectl;
  private Map<String, String> commandFlags;
  private CliHelper cliHelper;

  @Override
  public CliResponse build(String manifestFilesDir, String kustomizeDirPath, @Nonnull LogCallback executionLogCallback)
      throws InterruptedException, TimeoutException, IOException {
    String kustomizeCommand = kubectl.kustomize(kustomizeDirPath).commandFlags(commandFlags).command();
    return cliHelper.executeCliCommand(
        kustomizeCommand, KUSTOMIZE_COMMAND_TIMEOUT, Collections.emptyMap(), manifestFilesDir, executionLogCallback);
  }

  @Override
  public CliResponse buildWithPlugins(String manifestFilesDir, String kustomizeDirPath, @Nonnull String pluginPath,
      @Nonnull LogCallback executionLogCallback) throws InterruptedException, TimeoutException, IOException {
    if (kubectl.getVersion().compareTo(Version.parse("1.21")) < 0) {
      throw NestedExceptionUtils.hintWithExplanationException(
          format(KustomizeExceptionConstants.KUSOTMIZE_OR_KUBECTL_VERSION_MISSING_HINT, "1.21"),
          format(KustomizeExceptionConstants.KUSOTMIZE_OR_KUBECTL_VERSION_MISSING_EXPL, "1.21"),
          new InvalidRequestException(format("This version of Kubectl [%s] doesn't support kustomize with plugins",
                                          kubectl.getVersion().toString()),
              WingsException.USER));
    }
    String kustomizeCommand =
        kubectl.kustomize(kustomizeDirPath).commandFlags(commandFlags).withPlugin(pluginPath).command();

    return cliHelper.executeCliCommand(
        kustomizeCommand, KUSTOMIZE_COMMAND_TIMEOUT, Collections.emptyMap(), manifestFilesDir, executionLogCallback);
  }
}
