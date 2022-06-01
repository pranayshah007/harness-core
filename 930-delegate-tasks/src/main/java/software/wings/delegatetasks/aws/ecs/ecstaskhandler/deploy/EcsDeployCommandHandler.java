/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.aws.ecs.ecstaskhandler.deploy;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.pcf.ResizeStrategy.RESIZE_NEW_FIRST;
import static io.harness.logging.LogLevel.ERROR;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.container.ContainerInfo;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.TimeoutException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.Misc;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.api.ContainerServiceData;
import software.wings.beans.command.EcsResizeParams;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.beans.command.ResizeCommandUnitExecutionData;
import software.wings.beans.command.ResizeCommandUnitExecutionData.ResizeCommandUnitExecutionDataBuilder;
import software.wings.cloudprovider.aws.AwsClusterService;
import software.wings.delegatetasks.aws.ecs.ecstaskhandler.EcsCommandTaskHandler;
import software.wings.helpers.ext.ecs.request.EcsCommandRequest;
import software.wings.helpers.ext.ecs.request.EcsServiceDeployRequest;
import software.wings.helpers.ext.ecs.response.EcsCommandExecutionResponse;
import software.wings.helpers.ext.ecs.response.EcsServiceDeployResponse;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class EcsDeployCommandHandler extends EcsCommandTaskHandler {
  @Inject private EcsDeployCommandTaskHelper ecsDeployCommandTaskHelper;
  @Inject private AwsClusterService awsClusterService;
  static final String DASH_STRING = "----------";

  @Override
  public EcsCommandExecutionResponse executeTaskInternal(EcsCommandRequest ecsCommandRequest,
      List<EncryptedDataDetail> encryptedDataDetails, ExecutionLogCallback executionLogCallback) {
    EcsCommandExecutionResponse executionResponse = EcsCommandExecutionResponse.builder().build();
    EcsServiceDeployResponse ecsServiceDeployResponse = ecsDeployCommandTaskHelper.getEmptyEcsServiceDeployResponse();
    executionResponse.setEcsCommandResponse(ecsServiceDeployResponse);

    ResizeCommandUnitExecutionDataBuilder executionDataBuilder = ResizeCommandUnitExecutionData.builder();

    try {
      EcsServiceDeployRequest request = null;
      if (ecsCommandRequest instanceof EcsServiceDeployRequest) {
        request = (EcsServiceDeployRequest) ecsCommandRequest;
      } else {
        ecsServiceDeployResponse.setCommandExecutionStatus(CommandExecutionStatus.FAILURE);
        ecsServiceDeployResponse.setOutput("Invalid request Type, expected EcsServiceDeployRequest");
        executionResponse.setErrorMessage("Invalid request Type, expected EcsServiceDeployRequest");
        return executionResponse;
      }

      EcsResizeParams resizeParams = request.getEcsResizeParams();

      final boolean deployingToHundredPercent = ecsDeployCommandTaskHelper.getDeployingToHundredPercent(resizeParams);
      ContextData contextData = ContextData.builder()
                                    .encryptedDataDetails(encryptedDataDetails)
                                    .deployingToHundredPercent(deployingToHundredPercent)
                                    .resizeParams(resizeParams)
                                    .awsConfig(request.getAwsConfig())
                                    .blueGreen(request.isBlueGreen())
                                    .build();

      List<ContainerServiceData> newInstanceDataList;
      List<ContainerServiceData> oldInstanceDataList;

      if (resizeParams.isRollback()) {
        // Rollback
        Map<String, Integer> originalServiceCounts =
            ecsDeployCommandTaskHelper.listOfStringArrayToMap(resizeParams.getOriginalServiceCounts());

        newInstanceDataList = resizeParams.getNewInstanceData();
        oldInstanceDataList = resizeParams.getOldInstanceData();

        if (resizeParams.isRollbackAllPhases()) {
          // Roll back to original counts
          executionLogCallback.saveExecutionLog("** Rolling back all phases at once **\n");
          if (isNotEmpty(originalServiceCounts)) {
            newInstanceDataList = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : originalServiceCounts.entrySet()) {
              newInstanceDataList.add(
                  ContainerServiceData.builder().desiredCount(entry.getValue()).name(entry.getKey()).build());
            }
          }
          if (isNotEmpty(oldInstanceDataList)) {
            ecsDeployCommandTaskHelper.setDesiredToOriginal(oldInstanceDataList, originalServiceCounts);
          }
        }
      } else {
        newInstanceDataList = new ArrayList<>();
        ContainerServiceData newInstanceData =
            ecsDeployCommandTaskHelper.getNewInstanceData(contextData, executionLogCallback);
        newInstanceDataList.add(newInstanceData);
        oldInstanceDataList = ecsDeployCommandTaskHelper.getOldInstanceData(contextData, newInstanceData);
      }

      executionDataBuilder.newInstanceData(newInstanceDataList).oldInstanceData(oldInstanceDataList);

      boolean resizeNewFirst = resizeParams.getResizeStrategy() == RESIZE_NEW_FIRST;
      List<ContainerServiceData> firstDataList = resizeNewFirst ? newInstanceDataList : oldInstanceDataList;
      List<ContainerServiceData> secondDataList = resizeNewFirst ? oldInstanceDataList : newInstanceDataList;

      if (contextData.getResizeParams().isEcsAutoscalarRedesignEnabled()) {
        handleAutoScalarBeforeResize(contextData, executionLogCallback);
        resizeInstancesRedesigned(contextData, firstDataList, executionDataBuilder, executionLogCallback,
            resizeNewFirst, ecsCommandRequest.isTimeoutErrorSupported());
        resizeInstancesRedesigned(contextData, secondDataList, executionDataBuilder, executionLogCallback,
            !resizeNewFirst, ecsCommandRequest.isTimeoutErrorSupported());
        handleAutoScalarAfterResize(contextData, executionLogCallback, newInstanceDataList);
      } else {
        resizeInstances(contextData, firstDataList, executionDataBuilder, executionLogCallback, resizeNewFirst,
            ecsCommandRequest.isTimeoutErrorSupported());
        resizeInstances(contextData, secondDataList, executionDataBuilder, executionLogCallback, !resizeNewFirst,
            ecsCommandRequest.isTimeoutErrorSupported());
      }
    } catch (TimeoutException ex) {
      log.error(ex.getMessage());
      log.error(ExceptionUtils.getMessage(ex), ex);
      executionLogCallback.saveExecutionLog(ex.getMessage(), ERROR);
      ecsServiceDeployResponse.setCommandExecutionStatus(CommandExecutionStatus.FAILURE);
      ecsServiceDeployResponse.setOutput(ex.getMessage());
      if (ecsCommandRequest.isTimeoutErrorSupported()) {
        ecsServiceDeployResponse.setTimeoutFailure(true);
      }
    } catch (Exception ex) {
      log.error(ExceptionUtils.getMessage(ex), ex);
      Misc.logAllMessages(ex, executionLogCallback);
      log.error("Completed operation with errors");
      executionLogCallback.saveExecutionLog(format("Completed operation with errors%n%s%n", DASH_STRING), ERROR);
      ecsServiceDeployResponse.setCommandExecutionStatus(CommandExecutionStatus.FAILURE);
      ecsServiceDeployResponse.setOutput(ExceptionUtils.getMessage(ex));
    } finally {
      copyExexcutionDataIntoResponse(executionResponse, executionDataBuilder);
    }

    return executionResponse;
  }

  private void handleAutoScalarBeforeResize(ContextData contextData, ExecutionLogCallback executionLogCallback) {
    if (contextData.getResizeParams().isRollback()) {
      ecsDeployCommandTaskHelper.deleteAutoScalarForNewService(contextData, executionLogCallback);
    } else {
      ecsDeployCommandTaskHelper.deregisterAutoScalarsIfExists(contextData, executionLogCallback);
    }
  }

  private void handleAutoScalarAfterResize(ContextData contextData, ExecutionLogCallback executionLogCallback,
      List<ContainerServiceData> newInstanceDataList) {
    if (contextData.getResizeParams().isRollback()) {
      if (contextData.getResizeParams().isRollbackAllPhases() || contextData.getResizeParams().isLastDeployPhase()) {
        ecsDeployCommandTaskHelper.restoreAutoScalarConfigsRedesigned(contextData, executionLogCallback);
      }
    } else {
      if (contextData.getResizeParams().isLastDeployPhase()) {
        ecsDeployCommandTaskHelper.createAutoScalarConfigIfServiceReachedMaxSizeRedesigned(
            contextData, newInstanceDataList.get(0), executionLogCallback);
      }
    }
  }

  private void copyExexcutionDataIntoResponse(
      EcsCommandExecutionResponse executionResponse, ResizeCommandUnitExecutionDataBuilder executionDataBuilder) {
    ResizeCommandUnitExecutionData executionData = executionDataBuilder.build();
    EcsServiceDeployResponse ecsServiceDeployResponse =
        (EcsServiceDeployResponse) executionResponse.getEcsCommandResponse();
    ecsServiceDeployResponse.setContainerInfos(executionData.getContainerInfos());
    ecsServiceDeployResponse.setNewInstanceData(executionData.getNewInstanceData());
    ecsServiceDeployResponse.setOldInstanceData(executionData.getOldInstanceData());
    ecsServiceDeployResponse.setPreviousContainerInfos(executionData.getPreviousContainerInfos());
    executionResponse.setCommandExecutionStatus(ecsServiceDeployResponse.getCommandExecutionStatus());
    executionResponse.setErrorMessage(ecsServiceDeployResponse.getOutput());
  }

  private void resizeInstances(ContextData contextData, List<ContainerServiceData> instanceData,
      ResizeCommandUnitExecutionDataBuilder executionDataBuilder, ExecutionLogCallback executionLogCallback,
      boolean isUpsize, boolean timeoutErrorSupported) {
    if (isNotEmpty(instanceData)) {
      List<ContainerInfo> containerInfos =
          instanceData.stream()
              .flatMap(data -> executeResize(contextData, data, executionLogCallback, timeoutErrorSupported).stream())
              .collect(toList());
      if (isUpsize) {
        executionDataBuilder.containerInfos(
            containerInfos.stream().filter(ContainerInfo::isNewContainer).collect(toList()));
      } else {
        containerInfos.forEach(containerInfo -> containerInfo.setNewContainer(false));
        executionDataBuilder.previousContainerInfos(containerInfos);
      }
      ecsDeployCommandTaskHelper.logContainerInfos(containerInfos, executionLogCallback);
      log.info("Successfully completed resize operation");
      executionLogCallback.saveExecutionLog(format("Completed operation%n%s%n", DASH_STRING));
    }
  }

  private void resizeInstancesRedesigned(ContextData contextData, List<ContainerServiceData> instanceData,
      ResizeCommandUnitExecutionDataBuilder executionDataBuilder, ExecutionLogCallback executionLogCallback,
      boolean isUpsize, boolean timeoutErrorSupported) {
    if (isNotEmpty(instanceData)) {
      List<ContainerInfo> containerInfos =
          instanceData.stream()
              .flatMap(data
                  -> executeResizeRedesigned(contextData, data, executionLogCallback, timeoutErrorSupported).stream())
              .collect(toList());
      if (isUpsize) {
        executionDataBuilder.containerInfos(
            containerInfos.stream().filter(ContainerInfo::isNewContainer).collect(toList()));
      } else {
        containerInfos.forEach(containerInfo -> containerInfo.setNewContainer(false));
        executionDataBuilder.previousContainerInfos(containerInfos);
      }
      ecsDeployCommandTaskHelper.logContainerInfos(containerInfos, executionLogCallback);
      log.info("Successfully completed resize operation");
      executionLogCallback.saveExecutionLog(format("Completed operation%n%s%n", DASH_STRING));
    }
  }

  private List<ContainerInfo> executeResizeRedesigned(ContextData contextData,
      ContainerServiceData containerServiceData, ExecutionLogCallback executionLogCallback,
      boolean timeoutErrorSupported) {
    EcsResizeParams resizeParams = contextData.getResizeParams();
    return awsClusterService.resizeCluster(resizeParams.getRegion(), contextData.getSettingAttribute(),
        contextData.getEncryptedDataDetails(), resizeParams.getClusterName(), containerServiceData.getName(),
        containerServiceData.getPreviousCount(), containerServiceData.getDesiredCount(),
        resizeParams.getServiceSteadyStateTimeout(), executionLogCallback, timeoutErrorSupported);
  }

  public List<ContainerInfo> executeResize(ContextData contextData, ContainerServiceData containerServiceData,
      ExecutionLogCallback executionLogCallback, boolean timeoutErrorSupported) {
    EcsResizeParams resizeParams = contextData.getResizeParams();

    // As a part of Rollback, restore AutoScalingConfig if required
    if (resizeParams.isRollback()) {
      ecsDeployCommandTaskHelper.restoreAutoScalarConfigs(contextData, containerServiceData, executionLogCallback);
    } else {
      ecsDeployCommandTaskHelper.deregisterAutoScalarsIfExists(contextData, executionLogCallback);
    }

    List<ContainerInfo> containerInfos = awsClusterService.resizeCluster(resizeParams.getRegion(),
        contextData.getSettingAttribute(), contextData.getEncryptedDataDetails(), resizeParams.getClusterName(),
        containerServiceData.getName(), containerServiceData.getPreviousCount(), containerServiceData.getDesiredCount(),
        resizeParams.getServiceSteadyStateTimeout(), executionLogCallback, timeoutErrorSupported);

    ecsDeployCommandTaskHelper.createAutoScalarConfigIfServiceReachedMaxSize(
        contextData, containerServiceData, executionLogCallback);

    return containerInfos;
  }
}
