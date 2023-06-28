/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.k8s;

import static io.harness.annotations.dev.HarnessModule._870_CG_ORCHESTRATION;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.FeatureName.NEW_KUBECTL_VERSION;
import static io.harness.beans.FeatureName.SPG_CG_TIMEOUT_FAILURE_AT_WORKFLOW;

import static software.wings.sm.StateType.K8S_SCALE;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionStatus;
import io.harness.context.ContextElementType;
import io.harness.delegate.task.k8s.K8sTaskType;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.FailureType;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.k8s.K8sCommandUnitConstants;
import io.harness.k8s.model.K8sPod;
import io.harness.logging.CommandExecutionStatus;
import io.harness.tasks.ResponseData;

import software.wings.api.InstanceElementListParam;
import software.wings.api.RancherClusterElement;
import software.wings.api.k8s.K8sElement;
import software.wings.api.k8s.K8sStateExecutionData;
import software.wings.beans.Activity;
import software.wings.beans.ContainerInfrastructureMapping;
import software.wings.beans.InstanceUnitType;
import software.wings.beans.command.CommandUnit;
import software.wings.beans.command.K8sDummyCommandUnit;
import software.wings.delegatetasks.aws.AwsCommandHelper;
import software.wings.helpers.ext.container.ContainerDeploymentManagerHelper;
import software.wings.helpers.ext.k8s.request.K8sScaleTaskParameters;
import software.wings.helpers.ext.k8s.request.K8sTaskParameters;
import software.wings.helpers.ext.k8s.response.K8sScaleResponse;
import software.wings.helpers.ext.k8s.response.K8sTaskExecutionResponse;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ApplicationManifestService;
import software.wings.service.intfc.ConfigService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.ServiceTemplateService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.states.utils.StateTimeoutUtils;
import software.wings.stencils.DefaultValue;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.reinert.jjschema.Attributes;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;

@JsonIgnoreProperties(ignoreUnknown = true)
@TargetModule(_870_CG_ORCHESTRATION)
@OwnedBy(CDP)
@BreakDependencyOn("software.wings.service.intfc.DelegateService")
public class K8sScale extends AbstractK8sState {
  @Inject private transient ConfigService configService;
  @Inject private transient ServiceTemplateService serviceTemplateService;
  @Inject private transient ActivityService activityService;
  @Inject private transient SecretManager secretManager;
  @Inject private transient SettingsService settingsService;
  @Inject private transient AppService appService;
  @Inject private transient InfrastructureMappingService infrastructureMappingService;
  @Inject private transient DelegateService delegateService;
  @Inject private ContainerDeploymentManagerHelper containerDeploymentManagerHelper;
  @Inject private transient ApplicationManifestService applicationManifestService;
  @Inject private transient AwsCommandHelper awsCommandHelper;
  @Inject private transient FeatureFlagService featureFlagService;

  public static final String K8S_SCALE_COMMAND_NAME = "Scale";

  public K8sScale(String name) {
    super(name, K8S_SCALE.name());
  }

  @Getter @Setter @Attributes(title = "Workload") private String workload;
  @Getter @Setter @Attributes(title = "Instances") private String instances;
  @Getter @Setter @Attributes(title = "Instance Unit Type") private InstanceUnitType instanceUnitType;
  @Getter @Setter @Attributes(title = "Skip steady state check") private boolean skipSteadyStateCheck;
  @Getter @Setter @Attributes(title = "Timeout (Minutes)") @DefaultValue("10") private Integer stateTimeoutInMinutes;

  @Override
  public Integer getTimeoutMillis() {
    return StateTimeoutUtils.getTimeoutMillisFromMinutes(stateTimeoutInMinutes);
  }

  @Override
  public ExecutionResponse execute(ExecutionContext context) {
    if (k8sStateHelper.isRancherInfraMapping(context)
        && !(Objects.nonNull(context.getContextElement())
            && context.getContextElement() instanceof RancherClusterElement)) {
      return k8sStateHelper.getInvalidInfraDefFailedResponse();
    }
    boolean isTimeoutFailureSupported =
        featureFlagService.isEnabled(SPG_CG_TIMEOUT_FAILURE_AT_WORKFLOW, context.getAccountId());

    try {
      ContainerInfrastructureMapping infraMapping = k8sStateHelper.fetchContainerInfrastructureMapping(context);

      Integer maxInstances = null;
      K8sElement k8sElement = k8sStateHelper.fetchK8sElement(context);
      if (k8sElement != null) {
        maxInstances = k8sElement.getTargetInstances();
      }

      Activity activity = createActivity(context);

      K8sTaskParameters k8sTaskParameters =
          K8sScaleTaskParameters.builder()
              .activityId(activity.getUuid())
              .releaseName(fetchReleaseName(context, infraMapping))
              .commandName(K8S_SCALE_COMMAND_NAME)
              .k8sTaskType(K8sTaskType.SCALE)
              .workload(context.renderExpression(this.workload))
              .instances(Integer.valueOf(context.renderExpression(this.instances)))
              .instanceUnitType(this.instanceUnitType)
              .maxInstances(maxInstances)
              .skipSteadyStateCheck(this.skipSteadyStateCheck)
              .timeoutIntervalInMin(stateTimeoutInMinutes)
              .timeoutSupported(isTimeoutFailureSupported)
              .useLatestKustomizeVersion(isUseLatestKustomizeVersion(context.getAccountId()))
              .useNewKubectlVersion(featureFlagService.isEnabled(NEW_KUBECTL_VERSION, infraMapping.getAccountId()))
              .build();

      return queueK8sDelegateTask(context, k8sTaskParameters, null);
    } catch (WingsException e) {
      throw e;
    } catch (Exception e) {
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
  }

  @Override
  public ExecutionResponse handleAsyncResponse(ExecutionContext context, Map<String, ResponseData> response) {
    try {
      WorkflowStandardParams workflowStandardParams = context.getContextElement(ContextElementType.STANDARD);
      String appId = workflowStandardParams.getAppId();
      K8sTaskExecutionResponse executionResponse = (K8sTaskExecutionResponse) response.values().iterator().next();

      ExecutionStatus executionStatus = executionResponse.getCommandExecutionStatus() == CommandExecutionStatus.SUCCESS
          ? ExecutionStatus.SUCCESS
          : ExecutionStatus.FAILED;

      K8sScaleResponse k8sScaleResponse = (K8sScaleResponse) executionResponse.getK8sTaskResponse();

      activityService.updateStatus(fetchActivityId(context), appId, executionStatus);

      K8sStateExecutionData stateExecutionData = (K8sStateExecutionData) context.getStateExecutionData();
      stateExecutionData.setStatus(executionStatus);
      stateExecutionData.setErrorMsg(executionResponse.getErrorMessage());

      if (executionResponse.isTimeoutError()) {
        return ExecutionResponse.builder()
            .executionStatus(executionStatus)
            .failureTypes(FailureType.TIMEOUT)
            .stateExecutionData(stateExecutionData)
            .errorMessage("Timed out while waiting for k8s task to complete")
            .build();
      }

      if (ExecutionStatus.FAILED == executionStatus) {
        return ExecutionResponse.builder()
            .executionStatus(executionStatus)
            .stateExecutionData(context.getStateExecutionData())
            .build();
      }

      final List<K8sPod> newPods = fetchNewPods(k8sScaleResponse.getK8sPodList());
      InstanceElementListParam instanceElementListParam = fetchInstanceElementListParam(newPods);

      stateExecutionData.setNewInstanceStatusSummaries(
          fetchInstanceStatusSummaries(instanceElementListParam.getInstanceElements(), executionStatus));

      return ExecutionResponse.builder()
          .executionStatus(executionStatus)
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

  private Activity createActivity(ExecutionContext context) {
    if (this.skipSteadyStateCheck) {
      return createK8sActivity(context, K8S_SCALE_COMMAND_NAME, getStateType(), activityService,
          ImmutableList.of(new K8sDummyCommandUnit(K8sCommandUnitConstants.Init),
              new K8sDummyCommandUnit(K8sCommandUnitConstants.Scale)));
    } else {
      return createK8sActivity(context, K8S_SCALE_COMMAND_NAME, getStateType(), activityService,
          ImmutableList.of(new K8sDummyCommandUnit(K8sCommandUnitConstants.Init),
              new K8sDummyCommandUnit(K8sCommandUnitConstants.Scale),
              new K8sDummyCommandUnit(K8sCommandUnitConstants.WaitForSteadyState)));
    }
  }

  @Override
  public void handleAbortEvent(ExecutionContext context) {}

  @Override
  public void validateParameters(ExecutionContext context) {}

  @Override
  public String commandName() {
    return K8S_SCALE_COMMAND_NAME;
  }

  @Override
  public String stateType() {
    return getStateType();
  }

  @Override
  public List<CommandUnit> commandUnitList(boolean remoteStoreType, String accountId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public ExecutionResponse executeK8sTask(ExecutionContext context, String activityId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public ExecutionResponse handleAsyncResponseForK8sTask(ExecutionContext context, Map<String, ResponseData> response) {
    throw new UnsupportedOperationException();
  }
}
