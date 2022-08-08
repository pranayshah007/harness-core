/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.beans.EnvironmentType.ALL;
import static io.harness.beans.ExecutionStatus.SKIPPED;
import static io.harness.beans.OrchestrationWorkflowType.BUILD;
import static io.harness.delegate.beans.TaskData.DEFAULT_ASYNC_CALL_TIMEOUT;
import static io.harness.exception.WingsException.USER;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.CGConstants.GLOBAL_ENV_ID;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.PRIMARY_SERVICE_NAME_EXPRESSION;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.STAGE_SERVICE_NAME_EXPRESSION;
import static software.wings.sm.StateExecutionData.StateExecutionDataBuilder.aStateExecutionData;

import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.SweepingOutputInstance;
import io.harness.beans.SweepingOutputInstance.Scope;
import io.harness.beans.TriggeredBy;
import io.harness.context.ContextElementType;
import io.harness.delegate.beans.TaskData;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.k8s.KubernetesConvention;
import io.harness.serializer.KryoSerializer;
import io.harness.tasks.ResponseData;

import software.wings.api.ContainerServiceElement;
import software.wings.api.InstanceElement;
import software.wings.api.KubernetesSwapServiceSelectorsExecutionData;
import software.wings.api.PhaseElement;
import software.wings.api.RancherClusterElement;
import software.wings.api.k8s.K8sSwapServiceElement;
import software.wings.beans.Activity;
import software.wings.beans.Activity.ActivityBuilder;
import software.wings.beans.Activity.Type;
import software.wings.beans.Application;
import software.wings.beans.ContainerInfrastructureMapping;
import software.wings.beans.Environment;
import software.wings.beans.SyncTaskContext;
import software.wings.beans.TaskType;
import software.wings.beans.command.CommandUnitDetails.CommandUnitType;
import software.wings.beans.container.KubernetesSwapServiceSelectorsParams;
import software.wings.helpers.ext.container.ContainerDeploymentManagerHelper;
import software.wings.helpers.ext.container.ContainerMasterUrlHelper;
import software.wings.service.impl.ContainerServiceParams;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.sweepingoutput.SweepingOutputInquiry;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.State;
import software.wings.sm.StateType;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.WorkflowStandardParamsExtensionService;
import software.wings.sm.states.k8s.K8sStateHelper;

import com.github.reinert.jjschema.Attributes;
import com.google.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class KubernetesSwapServiceSelectors extends State {
  public static final long DEFAULT_SYNC_CALL_TIMEOUT = 60 * 1000; // 1 minute
  private static final String K8S_SWAP_SERVICE_ELEMENT = "k8sSwapServiceElement";

  @Inject private SecretManager secretManager;
  @Inject private SettingsService settingsService;
  @Inject private transient AppService appService;
  @Inject private transient InfrastructureMappingService infrastructureMappingService;
  @Inject private transient DelegateService delegateService;
  @Inject private transient ActivityService activityService;
  @Inject private ContainerDeploymentManagerHelper containerDeploymentManagerHelper;
  @Inject private transient K8sStateHelper k8sStateHelper;
  @Inject private ContainerMasterUrlHelper containerMasterUrlHelper;
  @Inject private SweepingOutputService sweepingOutputService;
  @Inject private KryoSerializer kryoSerializer;
  @Inject private WorkflowStandardParamsExtensionService workflowStandardParamsExtensionService;

  @Getter @Setter @Attributes(title = "Service One") private String service1;

  @Getter @Setter @Attributes(title = "Service Two") private String service2;

  public static final String KUBERNETES_SWAP_SERVICE_SELECTORS_COMMAND_NAME = "Kubernetes Swap Service Selectors";

  public KubernetesSwapServiceSelectors(String name) {
    super(name, StateType.KUBERNETES_SWAP_SERVICE_SELECTORS.name());
  }

  @Override
  public ExecutionResponse execute(ExecutionContext context) {
    try {
      return executeInternal(context);
    } catch (InvalidRequestException e) {
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
      return handleAsyncInternal(context, response);
    } catch (WingsException e) {
      throw e;
    } catch (Exception e) {
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
  }

  private ExecutionResponse handleAsyncInternal(ExecutionContext context, Map<String, ResponseData> response) {
    WorkflowStandardParams workflowStandardParams = context.getContextElement(ContextElementType.STANDARD);
    String appId = workflowStandardParams.getAppId();
    String activityId = response.keySet().iterator().next();
    KubernetesSwapServiceSelectorsResponse executionResponse =
        (KubernetesSwapServiceSelectorsResponse) response.values().iterator().next();
    activityService.updateStatus(activityId, appId, executionResponse.getExecutionStatus());

    KubernetesSwapServiceSelectorsExecutionData stateExecutionData =
        (KubernetesSwapServiceSelectorsExecutionData) context.getStateExecutionData();
    stateExecutionData.setStatus(executionResponse.getExecutionStatus());

    if (ExecutionStatus.SUCCESS == executionResponse.getExecutionStatus()) {
      K8sSwapServiceElement k8sSwapServiceElement = getK8sSwapServiceElement(context);
      if (k8sSwapServiceElement == null) {
        saveK8sSwapServiceElement(context, K8sSwapServiceElement.builder().swapDone(true).build());
      }
    }

    return ExecutionResponse.builder()
        .executionStatus(executionResponse.getExecutionStatus())
        .stateExecutionData(context.getStateExecutionData())
        .build();
  }

  protected Activity createActivity(ExecutionContext executionContext) {
    InstanceElement instanceElement = executionContext.getContextElement(ContextElementType.INSTANCE);
    WorkflowStandardParams workflowStandardParams = executionContext.getContextElement(ContextElementType.STANDARD);
    notNullCheck("workflowStandardParams", workflowStandardParams, USER);
    notNullCheck("currentUser", workflowStandardParams.getCurrentUser(), USER);

    Application app = workflowStandardParamsExtensionService.fetchRequiredApp(workflowStandardParams);
    Environment env = workflowStandardParamsExtensionService.fetchRequiredEnv(workflowStandardParams);

    ActivityBuilder activityBuilder = Activity.builder()
                                          .applicationName(app.getName())
                                          .appId(app.getUuid())
                                          .commandName(KUBERNETES_SWAP_SERVICE_SELECTORS_COMMAND_NAME)
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
                                          .commandUnitType(CommandUnitType.KUBERNETES_SWAP_SERVICE_SELECTORS)
                                          .triggeredBy(TriggeredBy.builder()
                                                           .email(workflowStandardParams.getCurrentUser().getEmail())
                                                           .name(workflowStandardParams.getCurrentUser().getName())
                                                           .build());

    if (executionContext.getOrchestrationWorkflowType() != null
        && executionContext.getOrchestrationWorkflowType() == BUILD) {
      activityBuilder.environmentId(GLOBAL_ENV_ID).environmentName(GLOBAL_ENV_ID).environmentType(ALL);
    } else {
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

  private static String getRenderedServiceName(
      ExecutionContext context, String baseServiceName, String serviceNameExpression) {
    if (StringUtils.equals(PRIMARY_SERVICE_NAME_EXPRESSION, serviceNameExpression)) {
      if (StringUtils.isEmpty(baseServiceName)) {
        throw new InvalidRequestException(
            "Service Name cannot to inferred from context. You have to specify a valid service Name instead of expression: "
            + serviceNameExpression);
      }
      return KubernetesConvention.getPrimaryServiceName(KubernetesConvention.getKubernetesServiceName(baseServiceName));
    }

    if (StringUtils.equals(STAGE_SERVICE_NAME_EXPRESSION, serviceNameExpression)) {
      if (StringUtils.isEmpty(baseServiceName)) {
        throw new InvalidRequestException(
            "Service Name cannot to inferred from context. You have to specify a valid service Name instead of expression: "
            + serviceNameExpression);
      }
      return KubernetesConvention.getStageServiceName(KubernetesConvention.getKubernetesServiceName(baseServiceName));
    }

    return KubernetesConvention.getKubernetesServiceName(context.renderExpression(serviceNameExpression));
  }

  private ExecutionResponse executeInternal(ExecutionContext context) {
    if (StringUtils.isEmpty(service1) || StringUtils.isEmpty(service2)) {
      throw new InvalidRequestException("Service Name cannot be empty");
    }

    // this is needed to have ${k8s) in context
    k8sStateHelper.fetchK8sElement(context);

    PhaseElement phaseElement = context.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM);
    WorkflowStandardParams workflowStandardParams = context.getContextElement(ContextElementType.STANDARD);
    Application app = appService.get(context.getAppId());
    Environment env = workflowStandardParamsExtensionService.getEnv(workflowStandardParams);
    ContainerInfrastructureMapping containerInfraMapping =
        (ContainerInfrastructureMapping) infrastructureMappingService.get(app.getUuid(), context.fetchInfraMappingId());

    if (isRollback()) {
      K8sSwapServiceElement k8sSwapServiceElement = getK8sSwapServiceElement(context);
      if (k8sSwapServiceElement == null || !k8sSwapServiceElement.isSwapDone()) {
        return ExecutionResponse.builder()
            .executionStatus(SKIPPED)
            .stateExecutionData(aStateExecutionData()
                                    .withErrorMsg("Services were not swapped in the deployment phase. Skipping.")
                                    .build())
            .build();
      }
    }

    ContainerServiceElement containerElement =
        context.<ContainerServiceElement>getContextElementList(ContextElementType.CONTAINER_SERVICE)
            .stream()
            .filter(cse -> phaseElement.getDeploymentType().equals(cse.getDeploymentType().name()))
            .filter(cse -> context.fetchInfraMappingId().equals(cse.getInfraMappingId()))
            .findFirst()
            .orElse(ContainerServiceElement.builder().build());

    String baseServiceName = containerElement.getControllerNamePrefix();
    String renderedService1 = getRenderedServiceName(context, baseServiceName, service1);
    String renderedService2 = getRenderedServiceName(context, baseServiceName, service2);

    Activity activity = createActivity(context);

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

    KubernetesSwapServiceSelectorsParams kubernetesSwapServiceSelectorsParams =
        KubernetesSwapServiceSelectorsParams.builder()
            .accountId(app.getAccountId())
            .appId(app.getUuid())
            .activityId(activity.getUuid())
            .commandName(KUBERNETES_SWAP_SERVICE_SELECTORS_COMMAND_NAME)
            .containerServiceParams(containerServiceParams)
            .service1(renderedService1)
            .service2(renderedService2)
            .build();
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(app.getAccountId())
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, app.getUuid())
            .waitId(activity.getUuid())
            .data(TaskData.builder()
                      .async(true)
                      .taskType(TaskType.KUBERNETES_SWAP_SERVICE_SELECTORS_TASK.name())
                      .parameters(new Object[] {kubernetesSwapServiceSelectorsParams})
                      .timeout(defaultIfNullTimeout(DEFAULT_ASYNC_CALL_TIMEOUT))
                      .build())
            .setupAbstraction(Cd1SetupFields.ENV_ID_FIELD, env.getUuid())
            .setupAbstraction(Cd1SetupFields.ENV_TYPE_FIELD, env.getEnvironmentType().name())
            .setupAbstraction(Cd1SetupFields.INFRASTRUCTURE_MAPPING_ID_FIELD, containerInfraMapping.getUuid())
            .setupAbstraction(Cd1SetupFields.SERVICE_ID_FIELD, containerInfraMapping.getServiceId())
            .selectionLogsTrackingEnabled(isSelectionLogsTrackingForTasksEnabled())
            .description("Kubernetes swap service selectors task execution")
            .build();
    String delegateTaskId = delegateService.queueTask(delegateTask);

    appendDelegateTaskDetails(context, delegateTask);
    return ExecutionResponse.builder()
        .async(true)
        .correlationIds(Arrays.asList(activity.getUuid()))
        .stateExecutionData(KubernetesSwapServiceSelectorsExecutionData.builder()
                                .activityId(activity.getUuid())
                                .service1(renderedService1)
                                .service2(renderedService2)
                                .commandName(KUBERNETES_SWAP_SERVICE_SELECTORS_COMMAND_NAME)
                                .build())
        .delegateTaskId(delegateTaskId)
        .build();
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

  private K8sSwapServiceElement getK8sSwapServiceElement(ExecutionContext context) {
    SweepingOutputInquiry sweepingOutputInquiry =
        context.prepareSweepingOutputInquiryBuilder().name(getSweepingOutputName(context)).build();
    SweepingOutputInstance result = sweepingOutputService.find(sweepingOutputInquiry);
    if (result == null) {
      return null;
    }
    return (K8sSwapServiceElement) kryoSerializer.asInflatedObject(result.getOutput());
  }

  private void saveK8sSwapServiceElement(ExecutionContext context, K8sSwapServiceElement k8sSwapServiceElement) {
    sweepingOutputService.save(context.prepareSweepingOutputBuilder(Scope.WORKFLOW)
                                   .name(getSweepingOutputName(context))
                                   .output(kryoSerializer.asDeflatedBytes(k8sSwapServiceElement))
                                   .build());
  }

  private String getSweepingOutputName(ExecutionContext context) {
    return Objects.nonNull(context.getContextElement()) && context.getContextElement() instanceof RancherClusterElement
        ? K8S_SWAP_SERVICE_ELEMENT + "-" + ((RancherClusterElement) context.getContextElement()).getUuid()
        : K8S_SWAP_SERVICE_ELEMENT;
  }

  @Override
  public boolean isSelectionLogsTrackingForTasksEnabled() {
    return true;
  }
}