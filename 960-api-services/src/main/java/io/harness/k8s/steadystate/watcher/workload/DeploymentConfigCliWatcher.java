/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.steadystate.watcher.workload;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.clienttools.ClientTool.OC;
import static io.harness.k8s.kubectl.Utils.encloseWithQuotesIfNeeded;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;

import io.harness.annotations.dev.OwnedBy;
import io.harness.configuration.KubernetesCliCommandType;
import io.harness.delegate.clienttools.InstallUtils;
import io.harness.exception.KubernetesCliTaskRuntimeException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.k8s.K8sConstants;
import io.harness.k8s.ProcessResponse;
import io.harness.k8s.kubectl.Utils;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.k8s.steadystate.model.K8sStatusWatchDTO;
import io.harness.logging.LogCallback;

import com.google.common.collect.Maps;
import com.google.inject.Singleton;
import java.io.ByteArrayOutputStream;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.zeroturnaround.exec.ProcessResult;
import org.zeroturnaround.exec.stream.LogOutputStream;

@Singleton
@Slf4j
@OwnedBy(CDP)
public class DeploymentConfigCliWatcher implements WorkloadWatcher {
  @Override
  public boolean watchRolloutStatus(K8sStatusWatchDTO k8SStatusWatchDTO, KubernetesResourceId resourceId,
      LogCallback executionLogCallback) throws Exception {
    String statusFormat = k8SStatusWatchDTO.getStatusFormat();
    K8sDelegateTaskParams k8sDelegateTaskParams = k8SStatusWatchDTO.getK8sDelegateTaskParams();

    try (ByteArrayOutputStream errorCaptureStream = new ByteArrayOutputStream();
         LogOutputStream statusErrorStream =
             new LogOutputStream() {
               @SneakyThrows
               @Override
               protected void processLine(String line) {
                 executionLogCallback.saveExecutionLog(
                     format(statusFormat, "Status", resourceId.getName(), line), ERROR);
                 errorCaptureStream.write(line.getBytes(UTF_8));
               }
             };
         LogOutputStream statusInfoStream =
             new LogOutputStream() {
               @Override
               protected void processLine(String line) {
                 executionLogCallback.saveExecutionLog(
                     format(statusFormat, "Status", resourceId.getName(), line), INFO);
               }
             }) {
      ProcessResult result;
      String printableExecutedCommand;

      String ocPath = null;
      try {
        ocPath = InstallUtils.getLatestVersionPath(OC);
      } catch (Exception ex) {
        log.warn("Unable to fetch OC binary path from delegate. Kindly ensure it is configured as env variable." + ex);
      }
      String rolloutStatusCommand =
          getRolloutStatusCommandForDeploymentConfig(ocPath, k8sDelegateTaskParams.getKubeconfigPath(), resourceId);

      printableExecutedCommand = rolloutStatusCommand.substring(rolloutStatusCommand.indexOf("oc --kubeconfig"));
      executionLogCallback.saveExecutionLog(printableExecutedCommand + "\n");
      result = Utils.executeScript(k8sDelegateTaskParams.getWorkingDirectory(), rolloutStatusCommand, statusInfoStream,
          statusErrorStream, Maps.newHashMap());

      boolean success = 0 == result.getExitValue();
      if (!success) {
        log.warn(result.outputUTF8());
        if (k8SStatusWatchDTO.isErrorFrameworkEnabled()) {
          ProcessResponse processResponse =
              ProcessResponse.builder()
                  .errorMessage(ExceptionMessageSanitizer.sanitizeMessage(errorCaptureStream.toString()))
                  .processResult(result)
                  .printableCommand(printableExecutedCommand)
                  .kubectlPath(k8sDelegateTaskParams.getKubectlPath())
                  .build();
          throw new KubernetesCliTaskRuntimeException(processResponse, KubernetesCliCommandType.STEADY_STATE_CHECK);
        }
      }

      return success;
    }
  }

  public String getRolloutStatusCommandForDeploymentConfig(
      String ocPath, String kubeConfigPath, KubernetesResourceId resourceId) {
    String namespace = "";
    if (StringUtils.isNotBlank(resourceId.getNamespace())) {
      namespace = "--namespace=" + resourceId.getNamespace() + " ";
    }

    return K8sConstants.ocRolloutStatusCommand
        .replace("{OC_COMMAND_PREFIX}", getOcCommandPrefix(ocPath, kubeConfigPath))
        .replace("{RESOURCE_ID}", resourceId.kindNameRef())
        .replace("{NAMESPACE}", namespace);
  }

  public static String getOcCommandPrefix(String ocPath, String kubeConfigPath) {
    StringBuilder command = new StringBuilder(128);

    if (StringUtils.isNotBlank(ocPath)) {
      command.append(encloseWithQuotesIfNeeded(ocPath));
    } else {
      command.append("oc");
    }

    if (StringUtils.isNotBlank(kubeConfigPath)) {
      command.append(" --kubeconfig=").append(encloseWithQuotesIfNeeded(kubeConfigPath));
    }

    return command.toString();
  }
}
