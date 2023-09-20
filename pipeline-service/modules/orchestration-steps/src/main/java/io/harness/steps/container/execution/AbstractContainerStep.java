/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository,
 * also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.container.execution;

import static io.harness.beans.FeatureName.CDS_USE_DELEGATE_BIJOU_API_CONTAINER_STEPS;
import static io.harness.plancreator.NGCommonUtilPlanCreationConstants.STEP_GROUP;
import static io.harness.steps.TaskRequestsUtils.prepareExecuteTaskRequest;

import static java.util.Collections.singletonList;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.common.ParameterFieldHelper;
import io.harness.data.structure.CollectionUtils;
import io.harness.delegate.TaskSelector;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.ci.k8s.K8sTaskExecutionResponse;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.task.TaskParameters;
import io.harness.encryption.Scope;
import io.harness.engine.pms.data.PmsSweepingOutputService;
import io.harness.engine.pms.data.RawOptionalSweepingOutput;
import io.harness.engine.pms.tasks.TaskExecutor;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.CIDelegateTaskExecutor;
import io.harness.helper.SerializedResponseDataHelper;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.execution.tasks.TaskCategory;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.plugin.ContainerStepExecutionResponseHelper;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.container.utils.ConnectorUtils;
import io.harness.steps.container.utils.ContainerSpecUtils;
import io.harness.steps.executable.AsyncExecutableWithRbac;
import io.harness.steps.plugin.ContainerStepSpec;
import io.harness.steps.plugin.infrastructure.ContainerK8sInfra;
import io.harness.steps.plugin.infrastructure.ContainerStepInfra;
import io.harness.steps.shellscript.ShellScriptHelperService;
import io.harness.steps.shellscript.ShellScriptInlineSource;
import io.harness.steps.shellscript.ShellScriptSourceWrapper;
import io.harness.steps.shellscript.ShellScriptStepParameters;
import io.harness.steps.shellscript.ShellType;
import io.harness.tasks.BinaryResponseData;
import io.harness.tasks.ResponseData;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.waiter.WaitNotifyEngine;
import io.harness.yaml.core.timeout.Timeout;

import software.wings.beans.TaskType;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_COMMON_STEPS})
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public abstract class AbstractContainerStep implements AsyncExecutableWithRbac<StepElementParameters> {
  @Inject private ContainerRunStepHelper containerRunStepHelper;
  @Inject private SerializedResponseDataHelper serializedResponseDataHelper;
  @Inject private WaitNotifyEngine waitNotifyEngine;
  @Inject private CIDelegateTaskExecutor taskExecutor;
  @Inject private ContainerStepExecutionResponseHelper containerStepExecutionResponseHelper;
  @Inject private PmsFeatureFlagService featureFlagService;
  @Inject private ConnectorUtils connectorUtils;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  @Inject private Map<TaskCategory, TaskExecutor> taskExecutorMap;
  @Inject private PmsSweepingOutputService pmsSweepingOutputService;
  @Inject private ShellScriptHelperService shellScriptHelperService;

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    // done in last step
  }

  @Override
  public AsyncExecutableResponse executeAsyncAfterRbac(
      Ambiance ambiance, StepElementParameters stepElementParameters, StepInputPackage inputPackage) {
    log.info("Starting run in container step");
    ContainerStepSpec containerStepInfo = (ContainerStepSpec) stepElementParameters.getSpec();
    String accountId = AmbianceUtils.getAccountId(ambiance);
    List<Level> levelsList = ambiance.getLevelsList();
    long startTs = System.currentTimeMillis() - Duration.ofMinutes(10).toMillis(); // defaulting to 10 mins.
    for (int i = levelsList.size() - 1; i >= 0; i--) {
      if (levelsList.get(i).getGroup().equals(STEP_GROUP)) {
        startTs = levelsList.get(i).getStartTs();
        break;
      }
    }

    long timeout =
        Timeout.fromString((String) stepElementParameters.getTimeout().fetchFinalValue()).getTimeoutInMillis()
        - (System.currentTimeMillis() - startTs);
    timeout = Math.max(timeout, 100);
    log.info("Timeout for container step left {}", timeout);
    List<TaskSelector> delegateSelectors = new ArrayList<>();

    if (featureFlagService.isEnabled(
            AmbianceUtils.getAccountId(ambiance), FeatureName.CD_CONTAINER_STEP_DELEGATE_SELECTOR)
        && ContainerStepInfra.Type.KUBERNETES_DIRECT.equals(containerStepInfo.getInfrastructure().getType())) {
      ConnectorDetails k8sConnector = connectorUtils.getConnectorDetails(
          AmbianceUtils.getNgAccess(ambiance), getK8sConnectorRef(containerStepInfo));
      delegateSelectors =
          ContainerSpecUtils.mergeStepAndConnectorOriginDelegateSelectors(containerStepInfo, k8sConnector);
    }

    if (featureFlagService.isEnabled(
            AmbianceUtils.getAccountId(ambiance), CDS_USE_DELEGATE_BIJOU_API_CONTAINER_STEPS)) {
      TaskExecutor taskExecutor = taskExecutorMap.get(TaskCategory.DELEGATE_TASK_V2);
      RawOptionalSweepingOutput infraRefIdOutput =
          pmsSweepingOutputService.resolveOptional(ambiance, RefObjectUtils.getSweepingOutputRefObject("infraRefId"));
      if (!infraRefIdOutput.isFound()) {
        throw new InvalidRequestException("Not found k8sInfra infraRefId");
      }
      String infraRefId = infraRefIdOutput.getOutput();
      String queueExecuteTaskId = taskExecutor.queueExecuteTask(
          prepareExecuteTaskRequest(ambiance, getTaskData(), referenceFalseKryoSerializer, timeout,
              TaskCategory.DELEGATE_TASK_V2, true, delegateSelectors, Scope.PROJECT, infraRefId),
          Duration.ofSeconds(0));

      return AsyncExecutableResponse.newBuilder()
          .addCallbackIds(queueExecuteTaskId)
          .addAllLogKeys(CollectionUtils.emptyIfNull(singletonList(getLogPrefix(ambiance))))
          .build();
    }

    String parkedTaskId = taskExecutor.queueParkedDelegateTask(ambiance, timeout, accountId, delegateSelectors);
    TaskData runStepTaskData = containerRunStepHelper.getRunStepTask(ambiance, containerStepInfo,
        AmbianceUtils.getAccountId(ambiance), getLogPrefix(ambiance), timeout, parkedTaskId);
    String liteEngineTaskId = taskExecutor.queueTask(ambiance, runStepTaskData, accountId, delegateSelectors);
    log.info("Created parked task {} and lite engine task {} for  step {}", parkedTaskId, liteEngineTaskId,
        containerStepInfo.getIdentifier());

    return AsyncExecutableResponse.newBuilder()
        .addCallbackIds(parkedTaskId)
        .addCallbackIds(liteEngineTaskId)
        .addAllLogKeys(CollectionUtils.emptyIfNull(singletonList(getLogPrefix(ambiance))))
        .build();
  }

  private TaskData getTaskData() {
    //    Object params =
    //            referenceFalseKryoSerializer.asInflatedObject(submitTaskRequest.getDetails().getKryoParameters().toByteArray());

    // get from stepElementParameters >> shell script task params
    ShellScriptStepParameters stepParameters =
        ShellScriptStepParameters.infoBuilder()
            .shellType(ShellType.Bash)
            .onDelegate(ParameterField.createValueField(false))
            .source(ShellScriptSourceWrapper.builder()
                        .spec(ShellScriptInlineSource.builder()
                                  .script(ParameterField.createValueField("echo 'POC FOR BIJOU API'"))
                                  .build())
                        .type("Inline")
                        .build())
            .uuid("unique_uuid")
            .build();

    return TaskData.builder()
        .async(true)
        .parameters(new Object[] {stepParameters})
        .taskType(TaskType.SHELL_SCRIPT_TASK_NG.name())
        .timeout(StepUtils.getTimeoutMillis(null, StepUtils.DEFAULT_STEP_TIMEOUT))
        .build();
  }

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }

  @Override
  public void handleForCallbackId(Ambiance ambiance, StepElementParameters containerStepInfo,
      List<String> allCallbackIds, String callbackId, ResponseData responseData) {
    responseData = serializedResponseDataHelper.deserialize(responseData);
    Object response = responseData;
    if (responseData instanceof BinaryResponseData) {
      response = referenceFalseKryoSerializer.asInflatedObject(((BinaryResponseData) responseData).getData());
    }
    if (response instanceof K8sTaskExecutionResponse
        && (((K8sTaskExecutionResponse) response).getCommandExecutionStatus() == CommandExecutionStatus.FAILURE
            || ((K8sTaskExecutionResponse) response).getCommandExecutionStatus() == CommandExecutionStatus.SKIPPED)) {
      abortTasks(allCallbackIds, callbackId);
    }
    if (response instanceof ErrorNotifyResponseData) {
      abortTasks(allCallbackIds, callbackId);
    }
  }

  @Override
  public StepResponse handleAsyncResponse(
      Ambiance ambiance, StepElementParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    StepResponse.StepOutcome outcome = produceOutcome(ambiance, stepParameters);
    return containerStepExecutionResponseHelper.handleAsyncResponseInternal(ambiance, responseDataMap, outcome);
  }

  public StepResponse.StepOutcome produceOutcome(Ambiance ambiance, StepElementParameters stepParameters) {
    return null;
  }

  private String getLogPrefix(Ambiance ambiance) {
    return LogStreamingStepClientFactory.getLogBaseKey(ambiance, StepCategory.STEP.name());
  }

  private void abortTasks(List<String> allCallbackIds, String callbackId) {
    List<String> callBackIds =
        allCallbackIds.stream().filter(cid -> !cid.equals(callbackId)).collect(Collectors.toList());
    callBackIds.forEach(callbackId1
        -> waitNotifyEngine.doneWith(callbackId1,
            ErrorNotifyResponseData.builder()
                .errorMessage("Delegate is not able to connect to created build farm")
                .build()));
  }

  private String getK8sConnectorRef(ContainerStepSpec containerStepInfo) {
    ContainerK8sInfra containerK8sInfra = (ContainerK8sInfra) containerStepInfo.getInfrastructure();
    return containerK8sInfra.getSpec().getConnectorRef().getValue();
  }
}
