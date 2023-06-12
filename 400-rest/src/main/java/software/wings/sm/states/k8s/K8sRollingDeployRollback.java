/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.k8s;

import static io.harness.annotations.dev.HarnessModule._870_CG_ORCHESTRATION;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.ExecutionStatus.SKIPPED;
import static io.harness.beans.FeatureName.CDP_USE_K8S_DECLARATIVE_ROLLBACK;
import static io.harness.beans.FeatureName.NEW_KUBECTL_VERSION;
import static io.harness.beans.FeatureName.PRUNE_KUBERNETES_RESOURCES;
import static io.harness.beans.FeatureName.SPG_CG_TIMEOUT_FAILURE_AT_WORKFLOW;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static software.wings.sm.StateExecutionData.StateExecutionDataBuilder.aStateExecutionData;
import static software.wings.sm.StateType.K8S_DEPLOYMENT_ROLLING_ROLLBACK;

import static com.google.common.base.MoreObjects.firstNonNull;
import static java.util.Collections.emptyList;
import static java.util.Objects.isNull;

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
import software.wings.api.k8s.K8sContextElement;
import software.wings.api.k8s.K8sHelmDeploymentElement;
import software.wings.api.k8s.K8sStateExecutionData;
import software.wings.beans.Activity;
import software.wings.beans.ContainerInfrastructureMapping;
import software.wings.beans.command.CommandUnit;
import software.wings.beans.command.K8sDummyCommandUnit;
import software.wings.delegatetasks.aws.AwsCommandHelper;
import software.wings.helpers.ext.container.ContainerDeploymentManagerHelper;
import software.wings.helpers.ext.k8s.request.K8sRollingDeployRollbackTaskParameters;
import software.wings.helpers.ext.k8s.request.K8sTaskParameters;
import software.wings.helpers.ext.k8s.response.K8sRollingDeployRollbackResponse;
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
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;

@JsonIgnoreProperties(ignoreUnknown = true)
@TargetModule(_870_CG_ORCHESTRATION)
@OwnedBy(CDP)
@BreakDependencyOn("software.wings.service.intfc.DelegateService")
public class K8sRollingDeployRollback extends AbstractK8sState {
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

  private static final Integer DEFAULT_TIMEOUT_MINS = 10;
  public static final String K8S_DEPLOYMENT_ROLLING_ROLLBACK_COMMAND_NAME = "Rolling Deployment Rollback";

  @Getter @Setter @Attributes(title = "Timeout (Minutes)") @DefaultValue("10") private Integer stateTimeoutInMinutes;

  public K8sRollingDeployRollback(String name) {
    super(name, K8S_DEPLOYMENT_ROLLING_ROLLBACK.name());
  }

  @Override
  public Integer getTimeoutMillis() {
    return StateTimeoutUtils.getTimeoutMillisFromMinutes(stateTimeoutInMinutes);
  }

  @Override
  public List<String> getDelegateSelectors(ExecutionContext context) {
    K8sContextElement k8sContextElement = context.getContextElement(ContextElementType.K8S);
    if (k8sContextElement == null) {
      return emptyList();
    }
    return k8sContextElement.getDelegateSelectors();
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
      K8sContextElement k8sContextElement = context.getContextElement(ContextElementType.K8S);
      ContainerInfrastructureMapping infraMapping = k8sStateHelper.fetchContainerInfrastructureMapping(context);

      if (isNull(k8sContextElement) || isEmpty(k8sContextElement.getReleaseName())) {
        return ExecutionResponse.builder()
            .executionStatus(SKIPPED)
            .stateExecutionData(aStateExecutionData().withErrorMsg("No context found for rollback. Skipping.").build())
            .build();
      }

      Activity activity = createK8sActivity(context, K8S_DEPLOYMENT_ROLLING_ROLLBACK_COMMAND_NAME, getStateType(),
          activityService, getRollbackCommandUnits(context.getAccountId()));

      K8sTaskParameters k8sTaskParameters =
          K8sRollingDeployRollbackTaskParameters.builder()
              .activityId(activity.getUuid())
              .releaseName(k8sContextElement.getReleaseName())
              .releaseNumber(k8sContextElement.getReleaseNumber())
              .commandName(K8S_DEPLOYMENT_ROLLING_ROLLBACK_COMMAND_NAME)
              .k8sTaskType(K8sTaskType.DEPLOYMENT_ROLLING_ROLLBACK)
              .timeoutIntervalInMin(firstNonNull(stateTimeoutInMinutes, DEFAULT_TIMEOUT_MINS))
              .timeoutSupported(isTimeoutFailureSupported)
              .delegateSelectors((k8sContextElement.getDelegateSelectors() == null)
                      ? null
                      : new HashSet<>(k8sContextElement.getDelegateSelectors()))
              .prunedResourcesIds(k8sContextElement.getPrunedResourcesIds() == null
                      ? emptyList()
                      : k8sContextElement.getPrunedResourcesIds())
              .isPruningEnabled(featureFlagService.isEnabled(PRUNE_KUBERNETES_RESOURCES, context.getAccountId()))
              .useLatestKustomizeVersion(isUseLatestKustomizeVersion(context.getAccountId()))
              .useNewKubectlVersion(featureFlagService.isEnabled(NEW_KUBECTL_VERSION, infraMapping.getAccountId()))
              .useDeclarativeRollback(
                  featureFlagService.isEnabled(CDP_USE_K8S_DECLARATIVE_ROLLBACK, infraMapping.getAccountId()))
              .build();

      return queueK8sDelegateTask(context, k8sTaskParameters, null);
    } catch (WingsException e) {
      throw e;
    } catch (Exception e) {
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
  }

  private List<CommandUnit> getRollbackCommandUnits(String accountId) {
    List<CommandUnit> rollingDeployRollbackCommandUnits = new ArrayList<>();
    rollingDeployRollbackCommandUnits.add(new K8sDummyCommandUnit(K8sCommandUnitConstants.Init));
    if (featureFlagService.isEnabled(PRUNE_KUBERNETES_RESOURCES, accountId)) {
      rollingDeployRollbackCommandUnits.add(new K8sDummyCommandUnit(K8sCommandUnitConstants.RecreatePrunedResource));
      rollingDeployRollbackCommandUnits.add(
          new K8sDummyCommandUnit(K8sCommandUnitConstants.DeleteFailedReleaseResources));
    }
    rollingDeployRollbackCommandUnits.add(new K8sDummyCommandUnit(K8sCommandUnitConstants.Rollback));
    rollingDeployRollbackCommandUnits.add(new K8sDummyCommandUnit(K8sCommandUnitConstants.WaitForSteadyState));
    return rollingDeployRollbackCommandUnits;
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

      activityService.updateStatus(fetchActivityId(context), appId, executionStatus);

      K8sHelmDeploymentElement k8SHelmDeploymentElement = fetchK8sHelmDeploymentElement(context);
      K8sStateExecutionData stateExecutionData = (K8sStateExecutionData) context.getStateExecutionData();
      stateExecutionData.setStatus(executionStatus);
      if (k8SHelmDeploymentElement != null) {
        stateExecutionData.setHelmChartInfo(k8SHelmDeploymentElement.getPreviousDeployedHelmChart());
      }

      K8sRollingDeployRollbackResponse k8sRollingRollbackResponse =
          (K8sRollingDeployRollbackResponse) executionResponse.getK8sTaskResponse();
      List<K8sPod> pods = Collections.emptyList();
      if (k8sRollingRollbackResponse != null) {
        pods = k8sRollingRollbackResponse.getK8sPodList();
      }

      InstanceElementListParam instanceElementListParam =
          InstanceElementListParam.builder().instanceElements(fetchInstanceElementList(pods, true)).build();
      stateExecutionData.setNewInstanceStatusSummaries(
          fetchInstanceStatusSummaries(instanceElementListParam.getInstanceElements(), executionStatus));
      saveInstanceInfoToSweepingOutput(context, fetchInstanceElementList(pods, true), fetchInstanceDetails(pods, true));

      if (executionResponse.isTimeoutError()) {
        return ExecutionResponse.builder()
            .executionStatus(executionStatus)
            .stateExecutionData(stateExecutionData)
            .failureTypes(FailureType.TIMEOUT)
            .errorMessage("Timed out while waiting for k8s task to complete")
            .build();
      }

      return ExecutionResponse.builder()
          .executionStatus(executionStatus)
          .stateExecutionData(stateExecutionData)
          .contextElement(instanceElementListParam)
          .notifyElement(instanceElementListParam)
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
  public void validateParameters(ExecutionContext context) {}

  @Override
  public String commandName() {
    return K8S_DEPLOYMENT_ROLLING_ROLLBACK_COMMAND_NAME;
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
