/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.utils;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.filesystem.FileIo.createDirectoryIfDoesNotExist;
import static io.harness.filesystem.FileIo.deleteDirectoryAndItsContentIfExists;
import static io.harness.k8s.model.ServiceHookContext.CUSTOM_WORKLOADS;
import static io.harness.k8s.model.ServiceHookContext.GOOGLE_APPLICATION_CREDENTIALS;
import static io.harness.k8s.model.ServiceHookContext.KUBE_CONFIG;
import static io.harness.k8s.model.ServiceHookContext.MANAGED_WORKLOADS;
import static io.harness.k8s.model.ServiceHookContext.WORKLOADS_LIST;
import static io.harness.logging.CommandExecutionStatus.RUNNING;

import static software.wings.beans.LogHelper.color;

import static java.lang.String.format;
import static java.util.Collections.emptyList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.utils.ServiceHookDTO;
import io.harness.exception.KubernetesTaskException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.k8s.model.KubernetesResource;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.k8s.model.ServiceHookAction;
import io.harness.k8s.model.ServiceHookType;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.shell.ExecuteCommandResponse;
import io.harness.shell.ScriptProcessExecutor;
import io.harness.shell.ScriptType;
import io.harness.shell.ShellExecutorConfig;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;
import software.wings.beans.ServiceHookDelegateConfig;

import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.ListUtils;

@Slf4j
@OwnedBy(CDP)
public class ServiceHookHandler {
  @Getter private Map<String, String> context;
  @Getter private List<ServiceHookDelegateConfig> hooks;
  private static final String failureMessage =
      "Failed to apply hook of type: %s , identifier: %s for action: %s in directory: %s";
  private long commandTimeout;
  private static final String HOOKS_FOLDER = ".__harness_internal_hooks";

  public ServiceHookHandler(List<ServiceHookDelegateConfig> hooks, ServiceHookDTO serviceHookTaskParams, long timeout) {
    this.hooks = hooks;
    this.context = new HashMap<>();
    Set<String> envPathsSet = getAvailableToolPaths(serviceHookTaskParams);
    context.put("PATH", generatePath(envPathsSet));
    context.put(GOOGLE_APPLICATION_CREDENTIALS.getContextName(),
        isNotEmpty(serviceHookTaskParams.getGcpKeyFilePath()) ? serviceHookTaskParams.getGcpKeyFilePath() : null);
    context.put(KUBE_CONFIG.getContextName(),
        isNotEmpty(serviceHookTaskParams.getKubeconfigPath()) ? serviceHookTaskParams.getKubeconfigPath() : null);
    commandTimeout = timeout;
  }

  public void execute(ServiceHookType type, ServiceHookAction action, String workingDirectory, LogCallback logCallback)
      throws InterruptedException {
    List<ServiceHookDelegateConfig> hooksToApply = requiredHooks(action.getActionName(), type.getName());
    if (isEmpty(hooksToApply)) {
      return;
    }
    logCallback.saveExecutionLog(
        color(format("\nExecuting %s for Action: %s \n", type.getName(), action.getActionName()), LogColor.White,
            LogWeight.Bold));
    for (ServiceHookDelegateConfig serviceHook : hooksToApply) {
      String directory =
          Paths
              .get(workingDirectory, HOOKS_FOLDER, format("%s_%s", serviceHook.getIdentifier(), action.getActionName()))
              .toString();
      logCallback.saveExecutionLog(format("\033[3mExecuting hook:  %s\033[0m", serviceHook.getIdentifier()));
      StringBuilder errorLog = new StringBuilder();
      ExecuteCommandResponse executeCommandResponse = executeCommand(context, serviceHook.getContent(), directory,
          format(failureMessage, type.getName(), serviceHook.getIdentifier(), action.getActionName(), directory),
          logCallback);
      if (errorLog.length() > 0) {
        log.error("Error output stream:\n{}", errorLog);
      }

      if (CommandExecutionStatus.SUCCESS != executeCommandResponse.getStatus()) {
        throw NestedExceptionUtils.hintWithExplanationException(
            format("Check hook %s script", serviceHook.getIdentifier()),
            format("Hook %s failed with error: %s\n", serviceHook.getIdentifier(),
                executeCommandResponse.getCommandExecutionData()),
            new KubernetesTaskException(format(
                failureMessage, type.getName(), serviceHook.getIdentifier(), action.getActionName(), directory)));
      }
      try {
        deleteDirectoryAndItsContentIfExists(directory);
      } catch (IOException e) {
        log.error("Unable to delete hook directory", e);
      }
      logCallback.saveExecutionLog(color(
          format("\033[3mHook %s executed successfully \033[0m \n\n", serviceHook.getIdentifier()), LogColor.White));
    }
    logCallback.saveExecutionLog(
        color(format("Successfully Executed %s for Action: %s \n", type.getName(), action.getActionName()),
            LogColor.White, LogWeight.Bold));
  }

  private ExecuteCommandResponse executeCommand(
      Map<String, String> envVars, String command, String directoryPath, String errorMessage, LogCallback logCallback) {
    ScriptProcessExecutor scriptProcessExecutor = createProcessExecutor(directoryPath, envVars, logCallback);

    return executeCommand(scriptProcessExecutor, errorMessage, directoryPath, command);
  }

  private ExecuteCommandResponse executeCommand(
      ScriptProcessExecutor processExecutor, String errorMessage, String directoryPath, String script) {
    try {
      createDirectoryIfDoesNotExist(Paths.get(String.valueOf(directoryPath)));
      final ExecuteCommandResponse executeCommandResponse =
          processExecutor.executeCommandString(script, emptyList(), Collections.emptyList(), null, false);
      return executeCommandResponse;
    } catch (IOException e) {
      // Not setting the cause here because it carries forward the commands which can contain passwords
      throw new KubernetesTaskException(format("[IO exception] %s", errorMessage));
    }
  }

  private ScriptProcessExecutor createProcessExecutor(
      String directoryPath, Map<String, String> envVars, LogCallback logCallback) {
    final ShellExecutorConfig shellExecutorConfig = ShellExecutorConfig.builder()
                                                        .environment(envVars)
                                                        .workingDirectory(directoryPath)
                                                        .scriptType(ScriptType.BASH)
                                                        .closeLogStream(false)
                                                        .build();

    final ScriptProcessExecutor executor = createExecutor(shellExecutorConfig, logCallback);

    return executor;
  }

  @VisibleForTesting
  private Set<String> getAvailableToolPaths(ServiceHookDTO serviceHookTaskParams) {
    Set<String> envPathSet = new HashSet<>();
    if (isNotEmpty(serviceHookTaskParams.getHelmPath())) {
      envPathSet.add(serviceHookTaskParams.getHelmPath());
    }
    if (isNotEmpty(serviceHookTaskParams.getKustomizeBinaryPath())) {
      envPathSet.add(serviceHookTaskParams.getKustomizeBinaryPath());
    }
    if (isNotEmpty(serviceHookTaskParams.getKubectlPath())) {
      envPathSet.add(serviceHookTaskParams.getKubectlPath());
    }
    if (isNotEmpty(serviceHookTaskParams.getOcPath())) {
      envPathSet.add(serviceHookTaskParams.getOcPath());
    }
    return envPathSet;
  }

  private Optional<String> getPathForTool(String toolLocation) {
    if (isNotEmpty(toolLocation)) {
      Path toolBinaryPath = Paths.get(toolLocation);
      Path parentPath = toolBinaryPath.getParent();
      if (parentPath != null) {
        return Optional.of(parentPath.toString());
      }
    }
    return Optional.empty();
  }

  @VisibleForTesting
  List<ServiceHookDelegateConfig> requiredHooks(String action, String type) {
    if (isEmpty(hooks)) {
      return null;
    }
    return hooks.stream()
        .filter(hook -> type.equals(hook.getHookType()) && hook.getServiceHookActions().contains(action))
        .collect(Collectors.toList());
  }

  private void saveExecutionLog(String line, LogLevel level, LogCallback logCallback) {
    logCallback.saveExecutionLog(line, level, RUNNING, false);
  }

  private String generatePath(Set<String> envPathsSet) {
    String envPath = envPathsSet.stream()
                         .map(this::getPathForTool)
                         .filter(Optional::isPresent)
                         .map(Optional::get)
                         .collect(Collectors.joining(":"));
    return format("%s:%s", envPath, isNotEmpty(System.getenv("PATH")) ? System.getenv("PATH") : "");
  }

  public void addWorkloadContextForHooks(
      List<KubernetesResource> managedWorkloads, List<KubernetesResource> customWorkloads) {
    addToContext(MANAGED_WORKLOADS.getContextName(), managedWorkloads);
    addToContext(CUSTOM_WORKLOADS.getContextName(), customWorkloads);
    addToContext(WORKLOADS_LIST.getContextName(), ListUtils.union(managedWorkloads, customWorkloads));
  }

  public void addToContext(String key, List<KubernetesResource> workloads) {
    if (isEmpty(workloads)) {
      return;
    }
    String workloadContext = workloads.stream()
                                 .map(KubernetesResource::getResourceId)
                                 .map(KubernetesResourceId::getName)
                                 .collect(Collectors.joining(","));
    context.put(key, workloadContext);
  }

  ScriptProcessExecutor createExecutor(ShellExecutorConfig config, LogCallback logCallback) {
    boolean saveExecutionLog = logCallback != null;
    return new ScriptProcessExecutor(logCallback, saveExecutionLog, config);
  }

  public void addToContext(String key, String value) {
    context.put(key, value);
  }
}