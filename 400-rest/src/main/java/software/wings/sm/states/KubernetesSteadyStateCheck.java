/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.beans.EnvironmentType.ALL;
import static io.harness.beans.OrchestrationWorkflowType.BUILD;
import static io.harness.delegate.beans.TaskData.DEFAULT_ASYNC_CALL_TIMEOUT;
import static io.harness.exception.WingsException.USER;

import static software.wings.beans.CGConstants.GLOBAL_ENV_ID;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.FeatureName;
import io.harness.context.ContextElementType;
import io.harness.delegate.beans.TaskData;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.tasks.ResponseData;

import software.wings.api.InstanceElement;
import software.wings.api.InstanceElementListParam;
import software.wings.api.KubernetesSteadyStateCheckExecutionData;
import software.wings.beans.Activity;
import software.wings.beans.Activity.ActivityBuilder;
import software.wings.beans.Activity.Type;
import software.wings.beans.Application;
import software.wings.beans.ContainerInfrastructureMapping;
import software.wings.beans.Environment;
import software.wings.beans.SyncTaskContext;
import software.wings.beans.TaskType;
import software.wings.beans.command.CommandUnitDetails.CommandUnitType;
import software.wings.beans.container.KubernetesSteadyStateCheckParams;
import software.wings.beans.container.Label;
import software.wings.helpers.ext.container.ContainerDeploymentManagerHelper;
import software.wings.helpers.ext.container.ContainerMasterUrlHelper;
import software.wings.service.impl.ContainerServiceParams;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.InstanceStatusSummary;
import software.wings.sm.State;
import software.wings.sm.StateType;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.WorkflowStandardParamsExtensionService;
import software.wings.stencils.DefaultValue;

import com.github.reinert.jjschema.Attributes;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.collections4.CollectionUtils;

public class KubernetesSteadyStateCheck extends State {
  public static final String KUBERNETES_STEADY_STATE_CHECK = "Steady State Check";
  public static final long DEFAULT_SYNC_CALL_TIMEOUT = 60 * 1000; // 1 minute

  @Inject private transient AppService appService;
  @Inject private transient InfrastructureMappingService infrastructureMappingService;
  @Inject private transient DelegateService delegateService;
  @Inject private transient ActivityService activityService;
  @Inject private transient ContainerDeploymentManagerHelper containerDeploymentManagerHelper;
  @Inject private transient ContainerMasterUrlHelper containerMasterUrlHelper;
  @Inject private transient WorkflowStandardParamsExtensionService workflowStandardParamsExtensionService;
  @Inject private transient FeatureFlagService featureFlagService;

  @Getter @Setter @Attributes(title = "Labels") private List<Label> labels = Lists.newArrayList();

  public static final String KUBERNETES_STEADY_STATE_CHECK_COMMAND_NAME = "Kubernetes Steady State Check";

  public KubernetesSteadyStateCheck(String name) {
    super(name, StateType.KUBERNETES_STEADY_STATE_CHECK.name());
  }

  @Attributes(title = "Timeout (ms)")
  @DefaultValue("" + DEFAULT_ASYNC_CALL_TIMEOUT)
  @Override
  public Integer getTimeoutMillis() {
    return super.getTimeoutMillis();
  }

  @Override
  public ExecutionResponse execute(ExecutionContext context) {
    try {
      WorkflowStandardParams workflowStandardParams = context.getContextElement(ContextElementType.STANDARD);
      Application app = appService.get(context.getAppId());
      Environment env = workflowStandardParamsExtensionService.getEnv(workflowStandardParams);
      ContainerInfrastructureMapping containerInfraMapping =
          (ContainerInfrastructureMapping) infrastructureMappingService.get(
              app.getUuid(), context.fetchInfraMappingId());

      if (featureFlagService.isEnabled(FeatureName.DEPRECATE_K8S_STEADY_STATE_CHECK_STEP, app.getAccountId())) {
        throw new InvalidRequestException(
            "The Steady State Check step is based on old k8s V1 and is NOT SUPPORTED ANYMORE", USER);
      }

      if (CollectionUtils.isEmpty(labels)) {
        throw new InvalidRequestException("Labels cannot be empty.");
      }

      labels.forEach(label -> label.setValue(context.renderExpression(label.getValue())));

      Map<String, String> labelMap = labels.stream().collect(Collectors.toMap(Label::getName, Label::getValue));

      ContainerServiceParams containerServiceParams =
          containerDeploymentManagerHelper.getContainerServiceParams(containerInfraMapping, "", context);

      if (containerMasterUrlHelper.masterUrlRequired(containerInfraMapping)) {
        boolean masterUrlPresent = containerMasterUrlHelper.fetchMasterUrlAndUpdateInfraMapping(containerInfraMapping,
            containerServiceParams, getSyncContext(context, containerInfraMapping), context.getWorkflowExecutionId());
        if (!masterUrlPresent) {
          throw new InvalidRequestException("No Valid Master Url for" + containerInfraMapping.getClass().getName()
                  + "Id : " + containerInfraMapping.getUuid(),
              USER);
        }
      }

      Activity activity = createActivity(context);
      KubernetesSteadyStateCheckParams kubernetesSteadyStateCheckParams =
          KubernetesSteadyStateCheckParams.builder()
              .accountId(app.getAccountId())
              .appId(app.getUuid())
              .activityId(activity.getUuid())
              .commandName(KUBERNETES_STEADY_STATE_CHECK_COMMAND_NAME)
              .containerServiceParams(containerServiceParams)
              .labels(labelMap)
              .timeoutMillis(defaultIfNullTimeout(DEFAULT_ASYNC_CALL_TIMEOUT))
              .build();
      DelegateTask delegateTask =
          DelegateTask.builder()
              .accountId(app.getAccountId())
              .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, app.getUuid())
              .waitId(activity.getUuid())
              .data(TaskData.builder()
                        .async(true)
                        .taskType(TaskType.KUBERNETES_STEADY_STATE_CHECK_TASK.name())
                        .parameters(new Object[] {kubernetesSteadyStateCheckParams})
                        .timeout(defaultIfNullTimeout(DEFAULT_ASYNC_CALL_TIMEOUT))
                        .build())
              .setupAbstraction(Cd1SetupFields.ENV_ID_FIELD, env.getUuid())
              .setupAbstraction(Cd1SetupFields.ENV_TYPE_FIELD, env.getEnvironmentType().name())
              .setupAbstraction(Cd1SetupFields.INFRASTRUCTURE_MAPPING_ID_FIELD, containerInfraMapping.getUuid())
              .setupAbstraction(Cd1SetupFields.SERVICE_ID_FIELD, containerInfraMapping.getServiceId())
              .selectionLogsTrackingEnabled(isSelectionLogsTrackingForTasksEnabled())
              .description("Kubernetes steady state check")
              .build();
      String delegateTaskId = delegateService.queueTask(delegateTask);

      appendDelegateTaskDetails(context, delegateTask);
      return ExecutionResponse.builder()
          .async(true)
          .correlationIds(singletonList(activity.getUuid()))
          .stateExecutionData(
              KubernetesSteadyStateCheckExecutionData.builder()
                  .activityId(activity.getUuid())
                  .labels(labels)
                  .commandName(KUBERNETES_STEADY_STATE_CHECK_COMMAND_NAME)
                  .namespace(kubernetesSteadyStateCheckParams.getContainerServiceParams().getNamespace())
                  .build())
          .delegateTaskId(delegateTaskId)
          .build();
    } catch (WingsException e) {
      throw e;
    } catch (Exception e) {
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
  }

  @Override
  public void handleAbortEvent(ExecutionContext context) {}

  @Override
  public ExecutionResponse handleAsyncResponse(ExecutionContext context, Map<String, ResponseData> response) {
    try {
      WorkflowStandardParams workflowStandardParams = context.getContextElement(ContextElementType.STANDARD);
      String appId = workflowStandardParams.getAppId();
      String activityId = response.keySet().iterator().next();
      KubernetesSteadyStateCheckResponse executionResponse =
          (KubernetesSteadyStateCheckResponse) response.values().iterator().next();
      activityService.updateStatus(activityId, appId, executionResponse.getExecutionStatus());

      KubernetesSteadyStateCheckExecutionData stateExecutionData =
          (KubernetesSteadyStateCheckExecutionData) context.getStateExecutionData();
      stateExecutionData.setStatus(executionResponse.getExecutionStatus());

      List<InstanceStatusSummary> instanceStatusSummaries = containerDeploymentManagerHelper.getInstanceStatusSummaries(
          context, executionResponse.getContainerInfoList());
      stateExecutionData.setNewInstanceStatusSummaries(instanceStatusSummaries);

      List<InstanceElement> instanceElements =
          instanceStatusSummaries.stream().map(InstanceStatusSummary::getInstanceElement).collect(toList());
      InstanceElementListParam instanceElementListParam =
          InstanceElementListParam.builder().instanceElements(instanceElements).build();

      return ExecutionResponse.builder()
          .executionStatus(executionResponse.getExecutionStatus())
          .stateExecutionData(context.getStateExecutionData())
          .contextElement(instanceElementListParam)
          .notifyElement(instanceElementListParam)
          .build();
    } catch (WingsException e) {
      throw e;
    } catch (Exception e) {
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
  }

  protected Activity createActivity(ExecutionContext executionContext) {
    Application app = ((ExecutionContextImpl) executionContext).fetchRequiredApp();
    InstanceElement instanceElement = executionContext.getContextElement(ContextElementType.INSTANCE);
    ActivityBuilder activityBuilder = Activity.builder()
                                          .applicationName(app.getName())
                                          .appId(app.getUuid())
                                          .commandName(KUBERNETES_STEADY_STATE_CHECK_COMMAND_NAME)
                                          .type(Type.Command)
                                          .workflowType(executionContext.getWorkflowType())
                                          .workflowExecutionName(executionContext.getWorkflowExecutionName())
                                          .stateExecutionInstanceId(executionContext.getStateExecutionInstanceId())
                                          .stateExecutionInstanceName(executionContext.getStateExecutionInstanceName())
                                          .commandType(getStateType())
                                          .workflowExecutionId(executionContext.getWorkflowExecutionId())
                                          .workflowId(executionContext.getWorkflowId())
                                          .commandUnits(Collections.emptyList())
                                          .status(ExecutionStatus.RUNNING)
                                          .commandUnitType(CommandUnitType.KUBERNETES_STEADY_STATE_CHECK);
    if (executionContext.getOrchestrationWorkflowType() != null
        && executionContext.getOrchestrationWorkflowType() == BUILD) {
      activityBuilder.environmentId(GLOBAL_ENV_ID).environmentName(GLOBAL_ENV_ID).environmentType(ALL);
    } else {
      Environment env = ((ExecutionContextImpl) executionContext).fetchRequiredEnvironment();
      activityBuilder.environmentId(env.getUuid())
          .environmentName(env.getName())
          .environmentType(env.getEnvironmentType());
    }
    if (instanceElement != null) {
      activityBuilder.serviceTemplateId(instanceElement.getServiceTemplateElement().getUuid())
          .serviceTemplateName(instanceElement.getServiceTemplateElement().getName())
          .serviceId(instanceElement.getServiceTemplateElement().getServiceElement().getUuid())
          .serviceName(instanceElement.getServiceTemplateElement().getServiceElement().getName())
          .serviceInstanceId(instanceElement.getUuid())
          .hostName(instanceElement.getHost().getHostName());
    }
    Activity activity = activityBuilder.build();
    return activityService.save(activity);
  }

  private SyncTaskContext getSyncContext(
      ExecutionContext context, ContainerInfrastructureMapping containerInfrastructureMapping) {
    return SyncTaskContext.builder()
        .accountId(context.getAccountId())
        .appId(context.getAppId())
        .envId(containerInfrastructureMapping.getEnvId())
        .infrastructureMappingId(containerInfrastructureMapping.getUuid())
        .timeout(DEFAULT_SYNC_CALL_TIMEOUT * 2)
        .build();
  }

  @Override
  public boolean isSelectionLogsTrackingForTasksEnabled() {
    return true;
  }
}
