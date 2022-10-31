/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.shellscript.provisioner;

import static io.harness.logging.LogLevel.INFO;

import static software.wings.beans.Log.Builder.aLog;

import static java.util.Collections.emptyList;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionStatus;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.AbstractDelegateRunnableTask;
import io.harness.delegate.task.TaskParameters;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.shell.ExecuteCommandResponse;
import io.harness.shell.ScriptProcessExecutor;
import io.harness.shell.ScriptType;
import io.harness.shell.ShellExecutorConfig;

import software.wings.api.shellscript.provision.ShellScriptProvisionExecutionData;
import software.wings.beans.shellscript.provisioner.ShellScriptProvisionParameters;
import software.wings.core.local.executors.ShellExecutorFactory;
import software.wings.delegatetasks.DelegateLogService;
import software.wings.service.intfc.security.EncryptionService;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class ShellScriptProvisionTask extends AbstractDelegateRunnableTask {
  @Inject private DelegateLogService logService;
  @Inject private ShellExecutorFactory shellExecutorFactory;
  @Inject private EncryptionService encryptionService;

  public ShellScriptProvisionTask(DelegateTaskPackage delegateTaskPackage,
      ILogStreamingTaskClient logStreamingTaskClient, Consumer<DelegateTaskResponse> consumer,
      BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    return null;
  }

  @Override
  public ShellScriptProvisionExecutionData run(TaskParameters taskParameters) {
    File outputFile = null;
    try {
      ShellScriptProvisionParameters parameters = (ShellScriptProvisionParameters) taskParameters;
      String outputPath = getOutputPath(parameters.getWorkflowExecutionId());
      saveExecutionLog(parameters,
          "\"" + parameters.getOutputPathKey() + "\" has been initialized to \"" + outputPath + "\"",
          CommandExecutionStatus.RUNNING);

      HashMap<String, String> variablesMap =
          getCombinedVariablesMap(parameters.getTextVariables(), parameters.getEncryptedVariables());
      variablesMap.put(parameters.getOutputPathKey(), outputPath);

      outputFile = createNewFile(outputPath);

      ShellExecutorConfig shellExecutorConfig = ShellExecutorConfig.builder()
                                                    .accountId(parameters.getAccountId())
                                                    .appId(parameters.getAppId())
                                                    .commandUnitName(parameters.getCommandUnit())
                                                    .executionId(parameters.getActivityId())
                                                    .environment(variablesMap)
                                                    .scriptType(ScriptType.BASH)
                                                    .build();
      ScriptProcessExecutor executor = shellExecutorFactory.getExecutor(shellExecutorConfig);
      ExecuteCommandResponse executeCommandResponse =
          executor.executeCommandString(parameters.getScriptBody(), emptyList(), emptyList(), null);

      saveExecutionLog(parameters, "Execution finished with status: " + executeCommandResponse.getStatus(),
          executeCommandResponse.getStatus());
      if (executeCommandResponse.getStatus() == CommandExecutionStatus.FAILURE) {
        return ShellScriptProvisionExecutionData.builder().executionStatus(ExecutionStatus.FAILED).build();
      }

      try {
        return ShellScriptProvisionExecutionData.builder()
            .executionStatus(ExecutionStatus.SUCCESS)
            .output(new String(Files.readAllBytes(Paths.get(outputPath)), Charsets.UTF_8))
            .build();
      } catch (IOException e) {
        throw new WingsException("Error occurred while reading output file", e);
      }
    } catch (Exception e) {
      log.error("Error occurred in the task", e);
      return ShellScriptProvisionExecutionData.builder()
          .executionStatus(ExecutionStatus.FAILED)
          .errorMsg(ExceptionUtils.getMessage(e))
          .build();
    } finally {
      FileUtils.deleteQuietly(outputFile);
    }
  }

  @VisibleForTesting
  HashMap<String, String> getCombinedVariablesMap(
      Map<String, String> textVariables, Map<String, EncryptedDataDetail> encryptedVariables) {
    HashMap<String, String> envMap = new HashMap<>();
    if (EmptyPredicate.isNotEmpty(textVariables)) {
      envMap.putAll(textVariables);
    }
    if (EmptyPredicate.isNotEmpty(encryptedVariables)) {
      for (Entry<String, EncryptedDataDetail> encryptedVariable : encryptedVariables.entrySet()) {
        envMap.put(encryptedVariable.getKey(),
            String.valueOf(encryptionService.getDecryptedValue(encryptedVariable.getValue(), false)));
      }
    }
    return envMap;
  }

  private String getOutputPath(String entityId) {
    return Paths.get("").toAbsolutePath().toString() + "/shellScriptProvisioner/" + entityId + "/output.json";
  }

  private File createNewFile(String path) {
    File file = new File(path);
    boolean mkdirs = file.getParentFile().mkdirs();
    if (!mkdirs && !file.getParentFile().exists()) {
      throw new WingsException("Unable to create directory for output file");
    }
    try {
      file.createNewFile();
    } catch (IOException e) {
      throw new WingsException("Error occurred in creating output file", e);
    }
    return file;
  }

  private void saveExecutionLog(
      ShellScriptProvisionParameters parameters, String line, CommandExecutionStatus commandExecutionStatus) {
    logService.save(parameters.getAccountId(),
        aLog()
            .appId(parameters.getAppId())
            .activityId(parameters.getActivityId())
            .logLevel(INFO)
            .logLine(line)
            .executionResult(commandExecutionStatus)
            .commandUnitName(parameters.getCommandUnit())
            .build());
  }
}
