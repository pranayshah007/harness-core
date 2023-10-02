/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.kustomize;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.chartmuseum.ChartMuseumConstants.VERSION_PATTERN;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.version.Version;
import io.harness.cli.CliHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.kubectl.KubectlFactory;
import io.harness.k8s.model.K8sDelegateTaskParams;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import lombok.extern.slf4j.Slf4j;
import org.zeroturnaround.exec.ProcessResult;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@Slf4j
@Singleton
@OwnedBy(CDP)
public class KustomizeClientFactory {
  private static final Version KUSTOMIZE_DEFAULT_VERSION = Version.parse("0.0.1");
  private static final String VERSION = "version";
  @Inject private CliHelper cliHelper;

  public KustomizeClient getClient(K8sDelegateTaskParams k8sDelegateTaskParams, Map<String, String> cmdFlags) {
    String kustomizeBinaryPath = k8sDelegateTaskParams.getKustomizeBinaryPath();
    if (kustomizeBinaryPath != null) {
      String versionCmd = kustomizeBinaryPath + " " + VERSION;
      Version version = getVersion(versionCmd);
      return KustomizeClientImpl.builder()
          .kustomizeBinaryPath(kustomizeBinaryPath)
          .commandFlags(cmdFlags)
          .version(version)
          .cliHelper(cliHelper)
          .build();
    }

    Kubectl kubectlClient = KubectlFactory.getKubectlClient(k8sDelegateTaskParams.getKubectlPath(),
        k8sDelegateTaskParams.getKubeconfigPath(), k8sDelegateTaskParams.getWorkingDirectory());
    if (kubectlClient.getVersion().compareTo(Version.parse("1.14")) >= 0) {
      return KubectlKustomizeClientImpl.builder()
          .kubectl(kubectlClient)
          .commandFlags(cmdFlags)
          .cliHelper(cliHelper)
          .build();
    }

    throw NestedExceptionUtils.hintWithExplanationException(
        format(KustomizeExceptionConstants.KUSOTMIZE_OR_KUBECTL_VERSION_MISSING_HINT, "1.14"),
        format(KustomizeExceptionConstants.KUSOTMIZE_OR_KUBECTL_VERSION_MISSING_EXPL, "1.14"),
        new InvalidRequestException(
            format("This version of Kubectl [%s] doesn't support kustomize.", kubectlClient.getVersion().toString()),
            WingsException.USER));
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
