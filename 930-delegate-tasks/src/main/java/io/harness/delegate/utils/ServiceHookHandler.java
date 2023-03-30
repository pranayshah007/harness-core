/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.utils;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.filesystem.FileIo.createDirectoryIfDoesNotExist;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.exception.KubernetesTaskException;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.ServiceHookAction;
import io.harness.k8s.model.ServiceHookType;
import io.harness.logging.LogCallback;

import com.google.common.util.concurrent.UncheckedTimeoutException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;

public class ServiceHookHandler {
  private Map<String, String> context;
  private Map<String, String> hooks;
  final String errorMessage = "Unable to run %s of action %s";

  public ServiceHookHandler(Map<String, String> hooks, K8sDelegateTaskParams k8sDelegateTaskParams) {
    this.hooks = hooks;
    this.context = new HashMap<>();
    Set<String> envPathsSet = getAvailablePaths(k8sDelegateTaskParams);
    String envPath = envPathsSet.stream()
                         .map(this::getPathForTool)
                         .filter(Optional::isPresent)
                         .map(Optional::get)
                         .collect(Collectors.joining(":"));
    context.put("PATH", envPath + ":" + (isNotEmpty(System.getenv("PATH")) ? System.getenv("PATH") : ""));
  }

  public void addContext(String key, String value) {
    context.put(key, value);
  }

  public int execute(
      ServiceHookType type, ServiceHookAction action, int order, String workingDirectory, LogCallback logCallback) {
    Map<String, String> hooksToApply = requiredHooks(action.getActionName(), type.getName());
    AtomicInteger currOrder = new AtomicInteger(order);
    hooksToApply.forEach((k, v) -> {
      String directory = Paths.get(workingDirectory, ".__harness_internal_hooks", currOrder.toString()).toString();
      logCallback.saveExecutionLog(format("Starting %s ", type.getName()));
      ProcessResult processResult =
          executeCommand(context, v, directory, format(errorMessage, type.getName(), action.getActionName()));
      logCallback.saveExecutionLog(format("Hook output %s", processResult.getOutput().getUTF8()));
      currOrder.getAndIncrement();
    });
    return currOrder.get();
  }

  private ProcessResult executeCommand(
      Map<String, String> envVars, String command, String directoryPath, String errorMessage) {
    ProcessExecutor processExecutor = createProcessExecutor(command, directoryPath, envVars);

    return executeCommand(processExecutor, errorMessage);
  }

  private ProcessResult executeCommand(ProcessExecutor processExecutor, String errorMessage) {
    errorMessage = isEmpty(errorMessage) ? "" : errorMessage;
    try {
      createDirectoryIfDoesNotExist(Paths.get(String.valueOf(processExecutor.getDirectory())));
      return processExecutor.execute();
    } catch (IOException e) {
      // Not setting the cause here because it carries forward the commands which can contain passwords
      throw new KubernetesTaskException(format("[IO exception] %s", errorMessage));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new KubernetesTaskException(format("[Interrupted] %s", errorMessage));
    } catch (TimeoutException | UncheckedTimeoutException e) {
      throw new KubernetesTaskException(format("[Timed out] %s", errorMessage));
    }
  }

  private ProcessExecutor createProcessExecutor(String command, String directoryPath, Map<String, String> envVars) {
    return new ProcessExecutor()
        .directory(isNotBlank(directoryPath) ? new File(directoryPath) : null)
        .timeout(5, TimeUnit.MINUTES)
        .commandSplit(command)
        .environment(envVars)
        .readOutput(true);
  }

  private Set<String> getAvailablePaths(K8sDelegateTaskParams k8sDelegateTaskParams) {
    Set<String> envPathSet = new HashSet<>();
    if (isNotEmpty(k8sDelegateTaskParams.getHelmPath())) {
      envPathSet.add(k8sDelegateTaskParams.getHelmPath());
    }
    if (isNotEmpty(k8sDelegateTaskParams.getKustomizeBinaryPath())) {
      envPathSet.add(k8sDelegateTaskParams.getKustomizeBinaryPath());
    }
    if (isNotEmpty(k8sDelegateTaskParams.getKubectlPath())) {
      envPathSet.add(k8sDelegateTaskParams.getKubectlPath());
    }
    if (isNotEmpty(k8sDelegateTaskParams.getGcpKeyFilePath())) {
      envPathSet.add(k8sDelegateTaskParams.getGcpKeyFilePath());
    }
    if (isNotEmpty(k8sDelegateTaskParams.getOcPath())) {
      envPathSet.add(k8sDelegateTaskParams.getOcPath());
    }
    return envPathSet;
  }

  private Optional<String> getPathForTool(String toolLocation) {
    if (isNotEmpty(toolLocation)) {
      Path toolBinaryPath = Paths.get(toolLocation);
      Path parentPath = toolBinaryPath.getParent();
      if (isNotEmpty(String.valueOf(parentPath))) {
        return Optional.of(parentPath.toString());
      }
    }
    return Optional.empty();
  }

  private Map<String, String> requiredHooks(String action, String type) {
    Map<String, String> requiredHooks = new HashMap<>();
    hooks.forEach((k, v) -> {
      if (k.contains(action) && k.contains(type)) {
        requiredHooks.put(k, v);
      }
    });
    return requiredHooks;
  }
}
