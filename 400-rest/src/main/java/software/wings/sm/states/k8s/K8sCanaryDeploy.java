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
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.exception.WingsException.USER;

import static software.wings.sm.StateType.K8S_CANARY_DEPLOY;

import static java.lang.Integer.parseInt;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.FeatureName;
import io.harness.context.ContextElementType;
import io.harness.delegate.task.k8s.K8sTaskType;
import io.harness.exception.FailureType;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.k8s.model.K8sPod;
import io.harness.k8s.model.KubernetesResource;
import io.harness.logging.CommandExecutionStatus;
import io.harness.tasks.ResponseData;

import software.wings.api.InstanceElementListParam;
import software.wings.api.RancherClusterElement;
import software.wings.api.k8s.K8sElement;
import software.wings.api.k8s.K8sStateExecutionData;
import software.wings.beans.Activity;
import software.wings.beans.ContainerInfrastructureMapping;
import software.wings.beans.InstanceUnitType;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.command.CommandUnit;
import software.wings.delegatetasks.aws.AwsCommandHelper;
import software.wings.helpers.ext.container.ContainerDeploymentManagerHelper;
import software.wings.helpers.ext.k8s.request.K8sCanaryDeployTaskParameters;
import software.wings.helpers.ext.k8s.request.K8sCanaryDeployTaskParameters.K8sCanaryDeployTaskParametersBuilder;
import software.wings.helpers.ext.k8s.request.K8sDelegateManifestConfig;
import software.wings.helpers.ext.k8s.request.K8sTaskParameters;
import software.wings.helpers.ext.k8s.request.K8sValuesLocation;
import software.wings.helpers.ext.k8s.response.K8sCanaryDeployResponse;
import software.wings.helpers.ext.k8s.response.K8sTaskExecutionResponse;
import software.wings.helpers.ext.openshift.OpenShiftManagerService;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ApplicationManifestService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.states.utils.StateTimeoutUtils;
import software.wings.stencils.DefaultValue;
import software.wings.utils.ApplicationManifestUtils;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.reinert.jjschema.Attributes;
import com.google.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;

@JsonIgnoreProperties(ignoreUnknown = true)
@TargetModule(_870_CG_ORCHESTRATION)
@OwnedBy(CDP)
@BreakDependencyOn("software.wings.service.intfc.DelegateService")
public class K8sCanaryDeploy extends AbstractK8sState {
  @Inject private transient ActivityService activityService;
  @Inject private transient SecretManager secretManager;
  @Inject private transient SettingsService settingsService;
  @Inject private transient AppService appService;
  @Inject private transient InfrastructureMappingService infrastructureMappingService;
  @Inject private transient DelegateService delegateService;
  @Inject private ContainerDeploymentManagerHelper containerDeploymentManagerHelper;
  @Inject private transient ApplicationManifestService applicationManifestService;
  @Inject private transient AwsCommandHelper awsCommandHelper;
  @Inject private ApplicationManifestUtils applicationManifestUtils;
  @Inject private transient OpenShiftManagerService openShiftManagerService;
  @Inject private transient FeatureFlagService featureFlagService;

  public static final String K8S_CANARY_DEPLOY_COMMAND_NAME = "Canary Deploy";

  public K8sCanaryDeploy(String name) {
    super(name, K8S_CANARY_DEPLOY.name());
  }

  @Getter @Setter @Attributes(title = "Instances") private String instances;
  @Getter @Setter @Attributes(title = "Instance Unit Type") private InstanceUnitType instanceUnitType;
  @Getter @Setter @Attributes(title = "Timeout (Minutes)") @DefaultValue("10") private Integer stateTimeoutInMinutes;
  @Getter @Setter @Attributes(title = "Skip Dry Run") private boolean skipDryRun;
  @Getter @Setter @Attributes(title = "Export manifests") private boolean exportManifests;
  @Getter @Setter @Attributes(title = "Inherit manifests") private boolean inheritManifests;

  @Override
  public Integer getTimeoutMillis() {
    return StateTimeoutUtils.getTimeoutMillisFromMinutes(stateTimeoutInMinutes);
  }

  @Override
  public String commandName() {
    return K8S_CANARY_DEPLOY_COMMAND_NAME;
  }

  @Override
  public String stateType() {
    return getStateType();
  }

  @Override
  public void validateParameters(ExecutionContext context) {
    if (!exportManifests) {
      parseInt(context.renderExpression(this.instances));
    }
    validateK8sV2TypeServiceUsed(context);
  }

  @Override
  protected boolean shouldInheritManifest(ExecutionContext context) {
    return false;
  }

  @Override
  protected boolean shouldSaveManifest(ExecutionContext context) {
    return featureFlagService.isEnabled(
        FeatureName.MANIFEST_INHERIT_FROM_CANARY_TO_PRIMARY_PHASE, context.getAccountId());
  }

  @Override
  public ExecutionResponse execute(ExecutionContext context) {
    if (k8sStateHelper.isRancherInfraMapping(context)
        && !(Objects.nonNull(context.getContextElement())
            && context.getContextElement() instanceof RancherClusterElement)) {
      return k8sStateHelper.getInvalidInfraDefFailedResponse();
    }

    if (inheritManifests) {
      Activity activity = createK8sActivity(
          context, commandName(), stateType(), activityService, commandUnitList(false, context.getAccountId()));
      return executeK8sTask(context, activity.getUuid());
    }
    return executeWrapperWithManifest(this, context, K8sStateHelper.fetchSafeTimeoutInMillis(getTimeoutMillis()));
  }

  @Override
  public ExecutionResponse executeK8sTask(ExecutionContext context, String activityId) {
    Map<K8sValuesLocation, ApplicationManifest> appManifestMap = fetchApplicationManifests(context);
    ContainerInfrastructureMapping infraMapping = k8sStateHelper.fetchContainerInfrastructureMapping(context);
    storePreviousHelmDeploymentInfo(context, appManifestMap.get(K8sValuesLocation.Service));

    boolean isTimeoutFailureSupported =
        featureFlagService.isEnabled(SPG_CG_TIMEOUT_FAILURE_AT_WORKFLOW, context.getAccountId());

    K8sDelegateManifestConfig k8sDelegateManifestConfig =
        createDelegateManifestConfig(context, appManifestMap.get(K8sValuesLocation.Service));
    k8sDelegateManifestConfig.setShouldSaveManifest(shouldSaveManifest(context));
    K8sCanaryDeployTaskParametersBuilder builder = K8sCanaryDeployTaskParameters.builder();

    builder.exportManifests(exportManifests);
    if (inheritManifests) {
      List<KubernetesResource> kubernetesResources =
          k8sStateHelper.getResourcesFromSweepingOutput(context, getStateType());
      if (isEmpty(kubernetesResources)) {
        throw new InvalidRequestException("No kubernetes resources found to inherit", USER);
      }
      builder.inheritManifests(inheritManifests);
      builder.kubernetesResources(kubernetesResources);
    }

    K8sTaskParameters k8sTaskParameters =
        builder.activityId(activityId)
            .releaseName(fetchReleaseName(context, infraMapping))
            .commandName(K8S_CANARY_DEPLOY_COMMAND_NAME)
            .k8sTaskType(K8sTaskType.CANARY_DEPLOY)
            .instances(Integer.valueOf(context.renderExpression(this.instances)))
            .instanceUnitType(this.instanceUnitType)
            .timeoutIntervalInMin(stateTimeoutInMinutes)
            .timeoutSupported(isTimeoutFailureSupported)
            .k8sDelegateManifestConfig(k8sDelegateManifestConfig)
            .valuesYamlList(fetchRenderedValuesFiles(appManifestMap, context))
            .skipDryRun(skipDryRun)
            .skipVersioningForAllK8sObjects(
                appManifestMap.get(K8sValuesLocation.Service).getSkipVersioningForAllK8sObjects())
            .useLatestKustomizeVersion(isUseLatestKustomizeVersion(context.getAccountId()))
            .useNewKubectlVersion(featureFlagService.isEnabled(NEW_KUBECTL_VERSION, infraMapping.getAccountId()))
            .cleanUpIncompleteCanaryDeployRelease(true)
            .useDeclarativeRollback(
                featureFlagService.isEnabled(CDP_USE_K8S_DECLARATIVE_ROLLBACK, infraMapping.getAccountId()))
            .build();
    ExecutionResponse response = queueK8sDelegateTask(context, k8sTaskParameters, appManifestMap);
    if (!exportManifests) {
      saveK8sCanaryDeployRun(context);
    }
    return response;
  }

  @Override
  public ExecutionResponse handleAsyncResponse(ExecutionContext context, Map<String, ResponseData> response) {
    return handleAsyncResponseWrapper(this, context, response);
  }

  @Override
  public ExecutionResponse handleAsyncResponseForK8sTask(ExecutionContext context, Map<String, ResponseData> response) {
    WorkflowStandardParams workflowStandardParams = context.getContextElement(ContextElementType.STANDARD);
    String appId = workflowStandardParams.getAppId();
    K8sTaskExecutionResponse executionResponse = (K8sTaskExecutionResponse) response.values().iterator().next();

    ExecutionStatus executionStatus = executionResponse.getCommandExecutionStatus() == CommandExecutionStatus.SUCCESS
        ? ExecutionStatus.SUCCESS
        : ExecutionStatus.FAILED;

    K8sStateExecutionData stateExecutionData = (K8sStateExecutionData) context.getStateExecutionData();
    stateExecutionData.setStatus(executionStatus);
    stateExecutionData.setErrorMsg(executionResponse.getErrorMessage());

    String activityId = stateExecutionData.getActivityId();
    activityService.updateStatus(activityId, appId, executionStatus);

    if (ExecutionStatus.FAILED == executionStatus) {
      if (executionResponse.getK8sTaskResponse() instanceof K8sCanaryDeployResponse) {
        K8sCanaryDeployResponse k8sCanaryDeployResponse =
            (K8sCanaryDeployResponse) executionResponse.getK8sTaskResponse();

        if (isNotBlank(k8sCanaryDeployResponse.getCanaryWorkload())) {
          saveK8sElement(
              context, K8sElement.builder().canaryWorkload(k8sCanaryDeployResponse.getCanaryWorkload()).build());
        }
      }

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
          .stateExecutionData(context.getStateExecutionData())
          .build();
    }

    K8sCanaryDeployResponse k8sCanaryDeployResponse = (K8sCanaryDeployResponse) executionResponse.getK8sTaskResponse();

    if (k8sCanaryDeployResponse.getResources() != null) {
      k8sStateHelper.saveResourcesToSweepingOutput(context, k8sCanaryDeployResponse.getResources(), getStateType());
      stateExecutionData.setExportManifests(true);
      return ExecutionResponse.builder()
          .executionStatus(executionStatus)
          .stateExecutionData(stateExecutionData)
          .build();
    }

    Integer targetInstances = parseInt(context.renderExpression(this.instances));
    if (k8sCanaryDeployResponse.getCurrentInstances() != null) {
      targetInstances = k8sCanaryDeployResponse.getCurrentInstances();
    }

    stateExecutionData.setReleaseNumber(k8sCanaryDeployResponse.getReleaseNumber());
    stateExecutionData.setTargetInstances(targetInstances);
    stateExecutionData.setHelmChartInfo(k8sCanaryDeployResponse.getHelmChartInfo());

    final List<K8sPod> newPods = fetchNewPods(k8sCanaryDeployResponse.getK8sPodList());
    InstanceElementListParam instanceElementListParam = fetchInstanceElementListParam(newPods);

    stateExecutionData.setNewInstanceStatusSummaries(
        fetchInstanceStatusSummaries(instanceElementListParam.getInstanceElements(), executionStatus));

    if (shouldSaveManifest(context)) {
      if (null != k8sCanaryDeployResponse.getGitFetchFilesConfig()) {
        saveK8sApplicationManifestInfo(context, applicationManifestUtils.fetchServiceFromContext(context).getUuid(),
            k8sCanaryDeployResponse.getGitFetchFilesConfig());
      }
    }
    saveK8sElement(context,
        K8sElement.builder()
            .releaseName(stateExecutionData.getReleaseName())
            .releaseNumber(k8sCanaryDeployResponse.getReleaseNumber())
            .targetInstances(targetInstances)
            .isCanary(true)
            .canaryWorkload(k8sCanaryDeployResponse.getCanaryWorkload())
            .build());

    saveInstanceInfoToSweepingOutput(context, fetchInstanceElementList(k8sCanaryDeployResponse.getK8sPodList(), false),
        fetchInstanceDetails(k8sCanaryDeployResponse.getK8sPodList(), false));

    return ExecutionResponse.builder()
        .executionStatus(executionStatus)
        .stateExecutionData(context.getStateExecutionData())
        .contextElement(instanceElementListParam)
        .notifyElement(instanceElementListParam)
        .build();
  }

  @Override
  public void handleAbortEvent(ExecutionContext context) {}

  @Override
  public List<CommandUnit> commandUnitList(boolean remoteStoreType, String accountId) {
    return k8sStateHelper.getCommandUnits(remoteStoreType, accountId, isInheritManifests(), isExportManifests(), false);
  }

  @Override
  public Map<String, String> validateFields() {
    Map<String, String> invalidFields = new HashMap<>();
    if (exportManifests && inheritManifests) {
      invalidFields.put("Export manifests & inherit manifests", "Can't export and inherit manifests at the same time");
    }
    return invalidFields;
  }
}
