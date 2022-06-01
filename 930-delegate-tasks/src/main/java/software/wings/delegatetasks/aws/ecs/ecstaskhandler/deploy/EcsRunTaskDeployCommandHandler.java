/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.aws.ecs.ecstaskhandler.deploy;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.threading.Morpheus.sleep;

import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;

import static java.lang.String.format;
import static java.time.Duration.ofSeconds;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.concurrent.HTimeLimiter;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.CommandExecutionException;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.TimeoutException;
import io.harness.exception.WingsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogLevel;
import io.harness.logging.Misc;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.AwsConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.delegatetasks.aws.ecs.ecstaskhandler.EcsCommandTaskHandler;
import software.wings.helpers.ext.ecs.request.EcsCommandRequest;
import software.wings.helpers.ext.ecs.request.EcsRunTaskDeployRequest;
import software.wings.helpers.ext.ecs.response.EcsCommandExecutionResponse;
import software.wings.helpers.ext.ecs.response.EcsRunTaskDeployResponse;
import software.wings.service.impl.AwsHelperService;

import com.amazonaws.services.ecs.model.Container;
import com.amazonaws.services.ecs.model.DesiredStatus;
import com.amazonaws.services.ecs.model.RegisterTaskDefinitionRequest;
import com.amazonaws.services.ecs.model.RunTaskRequest;
import com.amazonaws.services.ecs.model.RunTaskResult;
import com.amazonaws.services.ecs.model.Task;
import com.amazonaws.services.ecs.model.TaskDefinition;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDP)
@Singleton
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class EcsRunTaskDeployCommandHandler extends EcsCommandTaskHandler {
  @Inject private EcsDeployCommandTaskHelper ecsDeployCommandTaskHelper;
  @Inject private AwsHelperService awsHelperService = new AwsHelperService();
  @Inject private TimeLimiter timeLimiter;

  @Override
  public EcsCommandExecutionResponse executeTaskInternal(EcsCommandRequest ecsCommandRequest,
      List<EncryptedDataDetail> encryptedDataDetails, ExecutionLogCallback executionLogCallback) {
    EcsRunTaskDeployResponse ecsRunTaskDeployResponse = ecsDeployCommandTaskHelper.getEmptyRunTaskDeployResponse();
    ecsRunTaskDeployResponse.setCommandExecutionStatus(CommandExecutionStatus.RUNNING);

    if (!(ecsCommandRequest instanceof EcsRunTaskDeployRequest)) {
      ecsRunTaskDeployResponse.setCommandExecutionStatus(CommandExecutionStatus.FAILURE);
      ecsRunTaskDeployResponse.setOutput("Invalid Request Type: Expected was : EcsRunTaskDeployRequest");
      return EcsCommandExecutionResponse.builder()
          .commandExecutionStatus(CommandExecutionStatus.FAILURE)
          .ecsCommandResponse(ecsRunTaskDeployResponse)
          .build();
    }

    try {
      EcsRunTaskDeployRequest ecsRunTaskDeployRequest = (EcsRunTaskDeployRequest) ecsCommandRequest;

      SettingAttribute cloudProviderSetting =
          aSettingAttribute().withValue(ecsRunTaskDeployRequest.getAwsConfig()).build();

      ecsRunTaskDeployResponse.setNewRegisteredRunTaskDefinitions(new ArrayList<>());
      ecsRunTaskDeployResponse.setPreviousRegisteredRunTaskDefinitions(new ArrayList<>());
      ecsRunTaskDeployResponse.setPreviousRunTaskArns(new ArrayList<>());
      ecsRunTaskDeployResponse.setNewRunTaskArns(new ArrayList<>());

      if (ecsRunTaskDeployRequest.isEcsRegisterTaskDefinitionTagsEnabled()) {
        executeTaskDefinitionsParseAsRegisterTaskDefinitionRequest(ecsRunTaskDeployRequest, encryptedDataDetails,
            executionLogCallback, cloudProviderSetting, ecsRunTaskDeployResponse);
      } else {
        executeTaskDefinitions(ecsRunTaskDeployRequest, encryptedDataDetails, executionLogCallback,
            cloudProviderSetting, ecsRunTaskDeployResponse);
      }

      if (CommandExecutionStatus.FAILURE.equals(ecsRunTaskDeployResponse.getCommandExecutionStatus())) {
        executionLogCallback.saveExecutionLog(
            "Stopping further triggers of run tasks because some tasks failed to trigger.", LogLevel.ERROR,
            CommandExecutionStatus.FAILURE);
        throw new Exception("Failed to trigger ecs run tasks.");
      }

      if (!ecsRunTaskDeployRequest.isSkipSteadyStateCheck()) {
        executionLogCallback.saveExecutionLog("Starting the the steady state check.", LogLevel.INFO);
        waitAndDoSteadyStateCheck(ecsRunTaskDeployResponse.getNewRunTaskArns(),
            ecsRunTaskDeployRequest.getServiceSteadyStateTimeout(), cloudProviderSetting, ecsCommandRequest.getRegion(),
            ecsCommandRequest.getCluster(), encryptedDataDetails, executionLogCallback);
      } else {
        executionLogCallback.saveExecutionLog(
            "Skipping the steady state check as expected.", LogLevel.INFO, CommandExecutionStatus.SUCCESS);
      }

      ecsRunTaskDeployResponse.setCommandExecutionStatus(CommandExecutionStatus.SUCCESS);
    } catch (TimeoutException ex) {
      prepareFailureResponse(executionLogCallback, ecsRunTaskDeployResponse, ex);
      if (ecsCommandRequest.isTimeoutErrorSupported()) {
        ecsRunTaskDeployResponse.setTimeoutFailure(true);
      }
    } catch (Exception ex) {
      prepareFailureResponse(executionLogCallback, ecsRunTaskDeployResponse, ex);
    }

    return EcsCommandExecutionResponse.builder()
        .commandExecutionStatus(ecsRunTaskDeployResponse.getCommandExecutionStatus())
        .ecsCommandResponse(ecsRunTaskDeployResponse)
        .build();
  }

  private void prepareFailureResponse(
      ExecutionLogCallback executionLogCallback, EcsRunTaskDeployResponse ecsRunTaskDeployResponse, Exception ex) {
    log.error("Completed operation with errors");
    log.error(ExceptionUtils.getMessage(ex), ex);
    Misc.logAllMessages(ex, executionLogCallback);

    ecsRunTaskDeployResponse.setCommandExecutionStatus(CommandExecutionStatus.FAILURE);
    ecsRunTaskDeployResponse.setOutput(ExceptionUtils.getMessage(ex));
  }

  private void executeTaskDefinitionsParseAsRegisterTaskDefinitionRequest(
      EcsRunTaskDeployRequest ecsRunTaskDeployRequest, List<EncryptedDataDetail> encryptedDataDetails,
      ExecutionLogCallback executionLogCallback, SettingAttribute cloudProviderSetting,
      EcsRunTaskDeployResponse ecsRunTaskDeployResponse) {
    ecsRunTaskDeployRequest.getListTaskDefinitionJson().forEach(ecsRunTaskDef -> {
      RegisterTaskDefinitionRequest registerTaskDefinitionRequest =
          ecsDeployCommandTaskHelper.createRunTaskRegisterTaskDefinitionRequest(
              ecsRunTaskDef, ecsRunTaskDeployRequest.getRunTaskFamilyName());

      List<Task> listPreExistingTasks =
          ecsDeployCommandTaskHelper.getExistingTasks(ecsRunTaskDeployRequest.getAwsConfig(),
              ecsRunTaskDeployRequest.getCluster(), ecsRunTaskDeployRequest.getRegion(),
              registerTaskDefinitionRequest.getFamily(), encryptedDataDetails, executionLogCallback);
      int numOldTasDefsToDeregister = listPreExistingTasks.size() - 4 > 0 ? listPreExistingTasks.size() - 4 : 0;
      List<String> taskDefArnsToDeregister =
          listPreExistingTasks.stream()
              .sorted((a, b) -> { return a.getVersion() - b.getVersion() > 0 ? 1 : -1; })
              .limit(numOldTasDefsToDeregister)
              .map(t -> t.getTaskDefinitionArn())
              .collect(Collectors.toList());
      ecsDeployCommandTaskHelper.deregisterTaskDefinitions(ecsRunTaskDeployRequest.getAwsConfig(),
          ecsRunTaskDeployRequest.getRegion(), taskDefArnsToDeregister, encryptedDataDetails, executionLogCallback);

      List<String> listPreExistingTaskDefinitionArn =
          listPreExistingTasks.stream().map(t -> t.getTaskDefinitionArn()).collect(Collectors.toList());
      List<String> listPreExistingTaskArns =
          listPreExistingTasks.stream().map(t -> t.getTaskArn()).collect(Collectors.toList());
      ecsRunTaskDeployResponse.getPreviousRegisteredRunTaskDefinitions().addAll(listPreExistingTaskDefinitionArn);
      ecsRunTaskDeployResponse.getPreviousRunTaskArns().addAll(listPreExistingTaskArns);

      TaskDefinition registeredRunTaskDefinition =
          ecsDeployCommandTaskHelper.registerRunTaskDefinitionWithRegisterTaskDefinitionRequest(cloudProviderSetting,
              registerTaskDefinitionRequest, ecsRunTaskDeployRequest.getLaunchType(),
              ecsRunTaskDeployRequest.getRegion(), encryptedDataDetails, executionLogCallback);
      executionLogCallback.saveExecutionLog(format("Task with family name %s is registered => %s",
          registeredRunTaskDefinition.getFamily(), registeredRunTaskDefinition.getTaskDefinitionArn()));
      RunTaskRequest runTaskRequest =
          ecsDeployCommandTaskHelper.createAwsRunTaskRequest(registeredRunTaskDefinition, ecsRunTaskDeployRequest);

      executionLogCallback.saveExecutionLog(format("Triggering ECS run task %s in cluster %s",
                                                runTaskRequest.getTaskDefinition(), runTaskRequest.getCluster()),
          LogLevel.INFO);
      RunTaskResult runTaskResult = ecsDeployCommandTaskHelper.triggerRunTask(
          ecsRunTaskDeployRequest.getRegion(), cloudProviderSetting, encryptedDataDetails, runTaskRequest);
      if (!EmptyPredicate.isEmpty(runTaskResult.getFailures())) {
        ecsRunTaskDeployResponse.setCommandExecutionStatus(CommandExecutionStatus.FAILURE);
      }

      executionLogCallback.saveExecutionLog(format("%d Tasks were triggered sucessfully and %d failures were recieved.",
          runTaskResult.getTasks().size(), runTaskResult.getFailures().size()));
      runTaskResult.getTasks().forEach(
          t -> executionLogCallback.saveExecutionLog(format("Task => %s succeeded", t.getTaskArn())));
      runTaskResult.getFailures().forEach(f -> {
        executionLogCallback.saveExecutionLog(
            format("%s failed with reason => %s \nDetails: %s", f.getArn(), f.getReason(), f.getDetail()),
            LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      });
      List<Task> triggeredTasks = runTaskResult.getTasks();

      ecsRunTaskDeployResponse.getNewRegisteredRunTaskDefinitions().add(
          registeredRunTaskDefinition.getTaskDefinitionArn());
      ecsRunTaskDeployResponse.getNewRunTaskArns().addAll(
          triggeredTasks.stream().map(t -> t.getTaskArn()).collect(Collectors.toList()));
    });
  }

  private void executeTaskDefinitions(EcsRunTaskDeployRequest ecsRunTaskDeployRequest,
      List<EncryptedDataDetail> encryptedDataDetails, ExecutionLogCallback executionLogCallback,
      SettingAttribute cloudProviderSetting, EcsRunTaskDeployResponse ecsRunTaskDeployResponse) {
    ecsRunTaskDeployRequest.getListTaskDefinitionJson().forEach(ecsRunTaskDef -> {
      TaskDefinition runTaskDefinition = ecsDeployCommandTaskHelper.createRunTaskDefinition(
          ecsRunTaskDef, ecsRunTaskDeployRequest.getRunTaskFamilyName());

      List<Task> listPreExistingTasks =
          ecsDeployCommandTaskHelper.getExistingTasks(ecsRunTaskDeployRequest.getAwsConfig(),
              ecsRunTaskDeployRequest.getCluster(), ecsRunTaskDeployRequest.getRegion(), runTaskDefinition.getFamily(),
              encryptedDataDetails, executionLogCallback);
      int numOldTasDefsToDeregister = listPreExistingTasks.size() - 4 > 0 ? listPreExistingTasks.size() - 4 : 0;
      List<String> taskDefArnsToDeregister =
          listPreExistingTasks.stream()
              .sorted((a, b) -> { return a.getVersion() - b.getVersion() > 0 ? 1 : -1; })
              .limit(numOldTasDefsToDeregister)
              .map(t -> t.getTaskDefinitionArn())
              .collect(Collectors.toList());
      ecsDeployCommandTaskHelper.deregisterTaskDefinitions(ecsRunTaskDeployRequest.getAwsConfig(),
          ecsRunTaskDeployRequest.getRegion(), taskDefArnsToDeregister, encryptedDataDetails, executionLogCallback);

      List<String> listPreExistingTaskDefinitionArn =
          listPreExistingTasks.stream().map(t -> t.getTaskDefinitionArn()).collect(Collectors.toList());
      List<String> listPreExistingTaskArns =
          listPreExistingTasks.stream().map(t -> t.getTaskArn()).collect(Collectors.toList());
      ecsRunTaskDeployResponse.getPreviousRegisteredRunTaskDefinitions().addAll(listPreExistingTaskDefinitionArn);
      ecsRunTaskDeployResponse.getPreviousRunTaskArns().addAll(listPreExistingTaskArns);

      TaskDefinition registeredRunTaskDefinition = ecsDeployCommandTaskHelper.registerRunTaskDefinition(
          cloudProviderSetting, runTaskDefinition, ecsRunTaskDeployRequest.getLaunchType(),
          ecsRunTaskDeployRequest.getRegion(), encryptedDataDetails, executionLogCallback);
      executionLogCallback.saveExecutionLog(format("Task with family name %s is registered => %s",
          registeredRunTaskDefinition.getFamily(), registeredRunTaskDefinition.getTaskDefinitionArn()));
      RunTaskRequest runTaskRequest =
          ecsDeployCommandTaskHelper.createAwsRunTaskRequest(registeredRunTaskDefinition, ecsRunTaskDeployRequest);

      executionLogCallback.saveExecutionLog(format("Triggering ECS run task %s in cluster %s",
                                                runTaskRequest.getTaskDefinition(), runTaskRequest.getCluster()),
          LogLevel.INFO);
      RunTaskResult runTaskResult = ecsDeployCommandTaskHelper.triggerRunTask(
          ecsRunTaskDeployRequest.getRegion(), cloudProviderSetting, encryptedDataDetails, runTaskRequest);
      if (!EmptyPredicate.isEmpty(runTaskResult.getFailures())) {
        ecsRunTaskDeployResponse.setCommandExecutionStatus(CommandExecutionStatus.FAILURE);
      }

      executionLogCallback.saveExecutionLog(format("%d Tasks were triggered sucessfully and %d failures were recieved.",
          runTaskResult.getTasks().size(), runTaskResult.getFailures().size()));
      runTaskResult.getTasks().forEach(
          t -> executionLogCallback.saveExecutionLog(format("Task => %s succeeded", t.getTaskArn())));
      runTaskResult.getFailures().forEach(f -> {
        executionLogCallback.saveExecutionLog(
            format("%s failed with reason => %s \nDetails: %s", f.getArn(), f.getReason(), f.getDetail()),
            LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      });
      List<Task> triggeredTasks = runTaskResult.getTasks();

      ecsRunTaskDeployResponse.getNewRegisteredRunTaskDefinitions().add(
          registeredRunTaskDefinition.getTaskDefinitionArn());
      ecsRunTaskDeployResponse.getNewRunTaskArns().addAll(
          triggeredTasks.stream().map(t -> t.getTaskArn()).collect(Collectors.toList()));
    });
  }

  private void waitAndDoSteadyStateCheck(List<String> triggeredRunTaskArns, Long timeout,
      SettingAttribute cloudProviderSetting, String region, String clusterName,
      List<EncryptedDataDetail> encryptedDataDetails, ExecutionLogCallback executionLogCallback) {
    final AwsConfig awsConfig =
        awsHelperService.validateAndGetAwsConfig(cloudProviderSetting, encryptedDataDetails, false);
    try {
      HTimeLimiter.callInterruptible21(timeLimiter, Duration.ofMinutes(timeout), () -> {
        while (true) {
          List<Task> tasks = ecsDeployCommandTaskHelper.getTasksFromTaskArn(
              awsConfig, clusterName, region, triggeredRunTaskArns, encryptedDataDetails, executionLogCallback);
          List<Task> notInStoppedStateTasks = tasks.stream()
                                                  .filter(t -> !t.getLastStatus().equals(DesiredStatus.STOPPED.name()))
                                                  .collect(Collectors.toList());

          List<Task> tasksWithFailedContainers =
              tasks.stream()
                  .filter(
                      task -> task.getContainers().stream().anyMatch(container -> isEcsTaskContainerFailed(container)))
                  .collect(Collectors.toList());
          if (EmptyPredicate.isNotEmpty(tasksWithFailedContainers)) {
            String errorMsg =
                tasksWithFailedContainers.stream()
                    .flatMap(
                        task -> task.getContainers().stream().filter(container -> isEcsTaskContainerFailed(container)))
                    .map(container
                        -> container.getTaskArn() + " => " + container.getContainerArn()
                            + " => exit code : " + container.getExitCode())
                    .collect(Collectors.joining("\n"));
            executionLogCallback.saveExecutionLog(
                "Containers in some tasks failed and are showing non zero exit code\n" + errorMsg, LogLevel.ERROR,
                CommandExecutionStatus.FAILURE);
            throw new CommandExecutionException(
                "Containers in some tasks failed and are showing non zero exit code\n " + errorMsg);
          }

          if (EmptyPredicate.isEmpty(notInStoppedStateTasks)) {
            return true;
          }

          String taskStatusLog = tasks.stream()
                                     .map(task
                                         -> format("%s : %s : %s : %s", task.getTaskDefinitionArn(),
                                             task.getLastStatus(), task.getStopCode(), task.getStoppedReason()))
                                     .collect(Collectors.joining("\n"));
          executionLogCallback.saveExecutionLog(format("%d tasks have not completed", notInStoppedStateTasks.size()));
          executionLogCallback.saveExecutionLog(taskStatusLog);
          sleep(ofSeconds(10));
        }
      });
    } catch (UncheckedTimeoutException e) {
      executionLogCallback.saveExecutionLog(
          "Timed out waiting for run tasks to complete", LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      throw new TimeoutException(
          "Timed out waiting for tasks to be in running state", "Timeout", e, WingsException.EVERYBODY);
    } catch (WingsException e) {
      executionLogCallback.saveExecutionLog(
          "Got Some exception while waiting for tasks to complete", LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      throw e;
    } catch (Exception e) {
      executionLogCallback.saveExecutionLog(
          "Got Some exception while waiting for tasks to complete", LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      throw new InvalidRequestException("Error while waiting for run tasks to complete", e);
    }
    executionLogCallback.saveExecutionLog(
        "All Tasks completed successfully.", LogLevel.INFO, CommandExecutionStatus.SUCCESS);
  }

  public boolean isEcsTaskContainerFailed(Container container) {
    return (container.getExitCode() != null && container.getExitCode() != 0)
        || (container.getLastStatus() != null && container.getLastStatus().equals("STOPPED")
            && container.getExitCode() == null);
  }
}
