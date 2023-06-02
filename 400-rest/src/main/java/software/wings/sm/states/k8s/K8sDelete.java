/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.k8s;

import static io.harness.annotations.dev.HarnessModule._870_CG_ORCHESTRATION;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.FeatureName.CDP_USE_K8S_DECLARATIVE_ROLLBACK;
import static io.harness.beans.FeatureName.NEW_KUBECTL_VERSION;
import static io.harness.beans.FeatureName.SPG_CG_TIMEOUT_FAILURE_AT_WORKFLOW;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.sm.StateType.K8S_DELETE;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionStatus;
import io.harness.context.ContextElementType;
import io.harness.data.validator.Trimmed;
import io.harness.delegate.task.k8s.K8sTaskType;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.FailureType;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.k8s.K8sCommandUnitConstants;
import io.harness.logging.CommandExecutionStatus;
import io.harness.tasks.ResponseData;

import software.wings.api.RancherClusterElement;
import software.wings.api.k8s.K8sCanaryDeleteServiceElement;
import software.wings.api.k8s.K8sStateExecutionData;
import software.wings.beans.Activity;
import software.wings.beans.ContainerInfrastructureMapping;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.command.CommandUnit;
import software.wings.beans.command.K8sDummyCommandUnit;
import software.wings.helpers.ext.k8s.request.K8sDeleteTaskParameters;
import software.wings.helpers.ext.k8s.request.K8sTaskParameters;
import software.wings.helpers.ext.k8s.request.K8sValuesLocation;
import software.wings.helpers.ext.k8s.response.K8sTaskExecutionResponse;
import software.wings.service.intfc.ActivityService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.states.utils.StateTimeoutUtils;
import software.wings.stencils.DefaultValue;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.reinert.jjschema.Attributes;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@JsonIgnoreProperties(ignoreUnknown = true)
@Slf4j
@TargetModule(_870_CG_ORCHESTRATION)
@OwnedBy(CDP)
@BreakDependencyOn("software.wings.service.intfc.DelegateService")
public class K8sDelete extends AbstractK8sState {
  @Inject private transient ActivityService activityService;
  @Inject private transient FeatureFlagService featureFlagService;

  public static final String K8S_DELETE_COMMAND_NAME = "Delete";

  public K8sDelete(String name) {
    super(name, K8S_DELETE.name());
  }

  @Getter @Setter @Attributes(title = "Resources") private String resources;
  @Getter @Setter private boolean deleteNamespacesForRelease;

  @Trimmed @Getter @Setter @Attributes(title = "File paths") private String filePaths;
  @Attributes(title = "Timeout (Minutes)") @DefaultValue("10") @Getter @Setter private Integer stateTimeoutInMinutes;

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

    if (needsManifest()) {
      return executeWithManifest(context);
    } else {
      return executeWithoutManifest(context);
    }
  }

  private boolean needsManifest() {
    return isNotEmpty(filePaths);
  }

  private ExecutionResponse executeWithManifest(ExecutionContext context) {
    return executeWrapperWithManifest(this, context, K8sStateHelper.fetchSafeTimeoutInMillis(getTimeoutMillis()));
  }

  private ExecutionResponse executeWithoutManifest(ExecutionContext context) {
    try {
      ContainerInfrastructureMapping infraMapping = k8sStateHelper.fetchContainerInfrastructureMapping(context);

      Activity activity = createActivity(context);

      boolean wasCanaryDeployedPrev = checkIfCanaryDeployedPrev(context);

      K8sTaskParameters k8sTaskParameters =
          K8sDeleteTaskParameters.builder()
              .activityId(activity.getUuid())
              .releaseName(fetchReleaseName(context, infraMapping))
              .commandName(K8S_DELETE_COMMAND_NAME)
              .k8sTaskType(K8sTaskType.DELETE)
              .resources(context.renderExpression(this.resources))
              .deleteNamespacesForRelease(deleteNamespacesForRelease)
              .timeoutIntervalInMin(stateTimeoutInMinutes)
              .k8sCanaryDelete(wasCanaryDeployedPrev)
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

  @Override
  public ExecutionResponse handleAsyncResponse(ExecutionContext context, Map<String, ResponseData> response) {
    return handleAsyncResponseWrapper(this, context, response);
  }

  @Override
  public void validateParameters(ExecutionContext context) {
    // This function is not being used anywhere for k8sDelete but we need to have this because of the interface.
  }

  @Override
  public Map<String, String> validateFields() {
    Map<String, String> invalidFields = new HashMap<>();

    if (isBlank(resources) && isBlank(filePaths)) {
      invalidFields.put("resources", "Resources must not be blank");
    }

    return invalidFields;
  }

  @Override
  public String commandName() {
    return K8S_DELETE_COMMAND_NAME;
  }

  @Override
  public String stateType() {
    return getStateType();
  }

  @Override
  public List<CommandUnit> commandUnitList(boolean remoteStoreType, String accountId) {
    List<CommandUnit> applyCommandUnits = new ArrayList<>();

    if (remoteStoreType) {
      applyCommandUnits.add(new K8sDummyCommandUnit(K8sCommandUnitConstants.FetchFiles));
    }

    applyCommandUnits.add(new K8sDummyCommandUnit(K8sCommandUnitConstants.Init));
    applyCommandUnits.add(new K8sDummyCommandUnit(K8sCommandUnitConstants.Delete));

    return applyCommandUnits;
  }

  @Override
  public ExecutionResponse executeK8sTask(ExecutionContext context, String activityId) {
    Map<K8sValuesLocation, ApplicationManifest> appManifestMap = fetchApplicationManifests(context);
    ContainerInfrastructureMapping infraMapping = k8sStateHelper.fetchContainerInfrastructureMapping(context);

    boolean isTimeoutFailureSupported =
        featureFlagService.isEnabled(SPG_CG_TIMEOUT_FAILURE_AT_WORKFLOW, context.getAccountId());

    renderStateVariables(context);
    K8sTaskParameters k8sTaskParameters;
    k8sTaskParameters = K8sDeleteTaskParameters.builder()
                            .activityId(activityId)
                            .releaseName(fetchReleaseName(context, infraMapping))
                            .commandName(K8S_DELETE_COMMAND_NAME)
                            .k8sTaskType(K8sTaskType.DELETE)
                            .resources(resources)
                            .deleteNamespacesForRelease(deleteNamespacesForRelease)
                            .filePaths(filePaths)
                            .timeoutIntervalInMin(stateTimeoutInMinutes)
                            .timeoutSupported(isTimeoutFailureSupported)
                            .k8sDelegateManifestConfig(
                                createDelegateManifestConfig(context, appManifestMap.get(K8sValuesLocation.Service)))
                            .valuesYamlList(fetchRenderedValuesFiles(appManifestMap, context))
                            .useLatestKustomizeVersion(isUseLatestKustomizeVersion(context.getAccountId()))
                            .build();
    return queueK8sDelegateTask(context, k8sTaskParameters, appManifestMap);
  }

  @Override
  public ExecutionResponse handleAsyncResponseForK8sTask(ExecutionContext context, Map<String, ResponseData> response) {
    try {
      WorkflowStandardParams workflowStandardParams = context.getContextElement(ContextElementType.STANDARD);
      String appId = workflowStandardParams.getAppId();
      K8sTaskExecutionResponse executionResponse = (K8sTaskExecutionResponse) response.values().iterator().next();

      ExecutionStatus executionStatus = executionResponse.getCommandExecutionStatus() == CommandExecutionStatus.SUCCESS
          ? ExecutionStatus.SUCCESS
          : ExecutionStatus.FAILED;

      activityService.updateStatus(fetchActivityId(context), appId, executionStatus);

      K8sStateExecutionData stateExecutionData = (K8sStateExecutionData) context.getStateExecutionData();
      stateExecutionData.setStatus(executionStatus);

      if (executionResponse.isTimeoutError()) {
        return ExecutionResponse.builder()
            .executionStatus(executionStatus)
            .failureTypes(FailureType.TIMEOUT)
            .errorMessage("Timed out while waiting for k8s task to complete")
            .build();
      }

      return ExecutionResponse.builder()
          .executionStatus(executionStatus)
          .stateExecutionData(context.getStateExecutionData())
          .build();
    } catch (WingsException e) {
      throw e;
    } catch (Exception e) {
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
  }

  @Override
  public void handleAbortEvent(ExecutionContext context) {}

  private void renderStateVariables(ExecutionContext context) {
    // If both resources and filePaths are present then the resources get priority over filePaths.
    if (isNotBlank(resources)) {
      resources = context.renderExpression(resources);
    }
    if (isNotBlank(filePaths)) {
      filePaths = context.renderExpression(filePaths);
    }
  }

  private Activity createActivity(ExecutionContext context) {
    return createK8sActivity(context, K8S_DELETE_COMMAND_NAME, getStateType(), activityService,
        ImmutableList.of(new K8sDummyCommandUnit(K8sCommandUnitConstants.Init),
            new K8sDummyCommandUnit(K8sCommandUnitConstants.Delete)));
  }

  private boolean checkIfCanaryDeployedPrev(ExecutionContext context) {
    K8sCanaryDeleteServiceElement k8sCanaryDeleteServiceElement = fetchK8sCanaryDeleteServiceElement(context);
    boolean checkCanaryDeploy = false;
    if (k8sCanaryDeleteServiceElement != null && k8sCanaryDeleteServiceElement.getPreviousDeployedK8sCanary()) {
      checkCanaryDeploy = true;
    }
    return checkCanaryDeploy;
  }
}
