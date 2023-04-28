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
import static io.harness.k8s.model.ServiceHookContext.GOOGLE_APPLICATION_CREDENTIALS;
import static io.harness.k8s.model.ServiceHookContext.KUBE_CONFIG;
import static io.harness.k8s.model.ServiceHookContext.MANIFEST_FILES_DIRECTORY;
import static io.harness.k8s.model.ServiceHookContext.MANIFEST_FILE_OUTPUT_PATH;
import static io.harness.k8s.model.ServiceHookContext.WORKLOADS_LIST;
import static io.harness.logging.CommandExecutionStatus.RUNNING;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;

import static software.wings.beans.LogHelper.color;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.utils.ServiceHookDTO;
import io.harness.exception.KubernetesTaskException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.k8s.model.ServiceHookAction;
import io.harness.k8s.model.ServiceHookType;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;
import software.wings.beans.ServiceHookDelegateConfig;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;
import org.zeroturnaround.exec.stream.LogOutputStream;

@Slf4j
@OwnedBy(CDP)
public class ServiceHookHandler {
  @Getter private Map<String, String> context;
  @Getter private List<ServiceHookDelegateConfig> hooks;
  private static String failureMessage =
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

  public void addToContext(String key, String value) {
    context.put(key, value);
  }

  public void addToContext(String key, List<String> value) {
    String commaSeparatedValues = String.join(",", value);
    context.put(key, commaSeparatedValues);
  }

  public void applyServiceHooks(ServiceHookType type, ServiceHookAction action, String workingDirectory,
      LogCallback logCallback, String contextDirectory) throws InterruptedException {
    switch (action) {
      case FETCH_FILES:
        addToContext(MANIFEST_FILES_DIRECTORY.getContextName(), contextDirectory);
        break;
      case TEMPLATE_MANIFEST:
        addToContext(MANIFEST_FILE_OUTPUT_PATH.getContextName(), contextDirectory);
        break;
      case STEADY_STATE_CHECK:
        addToContext(WORKLOADS_LIST.getContextName(), contextDirectory);
        break;
      default:
        break;
    }
    execute(type, action, workingDirectory, logCallback);
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
      ProcessResult processResult;
      processResult = executeCommand(context, serviceHook.getContent(), directory,
          format(failureMessage, type.getName(), serviceHook.getIdentifier(), action.getActionName(), directory),
          errorLog, logCallback);
      if (errorLog.length() > 0) {
        log.error("Error output stream:\n{}", errorLog);
      }

      if (processResult.getExitValue() != 0) {
        throw NestedExceptionUtils.hintWithExplanationException(
            format("Check hook %s script", serviceHook.getIdentifier()),
            format("Hook %s failed with error: %s\n", serviceHook.getIdentifier(), processResult.getOutput().getUTF8()),
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

  private ProcessResult executeCommand(Map<String, String> envVars, String command, String directoryPath,
      String errorMessage, StringBuilder errorLog, LogCallback logCallback) throws InterruptedException {
    ProcessExecutor processExecutor = createProcessExecutor(command, directoryPath, envVars, errorLog, logCallback);

    return executeCommand(processExecutor, errorMessage);
  }

  private ProcessResult executeCommand(ProcessExecutor processExecutor, String errorMessage)
      throws InterruptedException {
    try {
      createDirectoryIfDoesNotExist(Paths.get(String.valueOf(processExecutor.getDirectory())));
      return processExecutor.execute();
    } catch (IOException e) {
      // Not setting the cause here because it carries forward the commands which can contain passwords
      throw new KubernetesTaskException(format("[IO exception] %s", errorMessage));
    } catch (InterruptedException e) {
      throw e;
    } catch (TimeoutException | UncheckedTimeoutException e) {
      throw new KubernetesTaskException(format("[Timed out] %s", errorMessage));
    }
  }

  private ProcessExecutor createProcessExecutor(String command, String directoryPath, Map<String, String> envVars,
      StringBuilder errorLog, LogCallback logCallback) {
    return new ProcessExecutor()
        .directory(isNotBlank(directoryPath) ? new File(directoryPath) : null)
        .timeout(commandTimeout, TimeUnit.MILLISECONDS)
        .commandSplit(command)
        .environment(envVars)
        .readOutput(true)
        .redirectOutput(new LogOutputStream() {
          @Override
          protected void processLine(String line) {
            saveExecutionLog(line, INFO, logCallback);
          }
        })
        .redirectError(new LogOutputStream() {
          @Override
          protected void processLine(String line) {
            errorLog.append(line);
            errorLog.append('\n');
            saveExecutionLog(line, ERROR, logCallback);
          }
        });
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
}