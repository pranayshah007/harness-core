/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.rancher;

import static io.harness.annotations.dev.HarnessModule._870_CG_ORCHESTRATION;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.FeatureName.SPG_CG_TIMEOUT_FAILURE_AT_WORKFLOW;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.delegate.beans.TaskData.DEFAULT_ASYNC_CALL_TIMEOUT;
import static io.harness.exception.ExceptionUtils.getMessage;

import static software.wings.delegatetasks.rancher.RancherResolveClustersTask.COMMAND_NAME;
import static software.wings.delegatetasks.rancher.RancherResolveClustersTask.COMMAND_UNIT_NAME;
import static software.wings.sm.StateExecutionData.StateExecutionDataBuilder.aStateExecutionData;
import static software.wings.sm.StateType.RANCHER_RESOLVE;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.SweepingOutputInstance;
import io.harness.data.structure.UUIDGenerator;
import io.harness.delegate.beans.TaskData;
import io.harness.exception.FailureType;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.tasks.ResponseData;

import software.wings.api.RancherClusterElement;
import software.wings.beans.Activity;
import software.wings.beans.Application;
import software.wings.beans.ClusterSelectionCriteriaEntry;
import software.wings.beans.Environment;
import software.wings.beans.RancherConfig;
import software.wings.beans.RancherKubernetesInfrastructureMapping;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TaskType;
import software.wings.beans.command.CommandUnitDetails;
import software.wings.beans.command.RancherDummyCommandUnit;
import software.wings.common.RancherK8sClusterProcessor.RancherClusterElementList;
import software.wings.delegatetasks.rancher.RancherResolveClustersResponse;
import software.wings.delegatetasks.rancher.RancherResolveClustersTaskParameters;
import software.wings.infra.InfrastructureDefinition;
import software.wings.infra.RancherKubernetesInfrastructure;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.InfrastructureDefinitionService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.State;
import software.wings.sm.StateExecutionData;
import software.wings.sm.states.k8s.K8sStateHelper;
import software.wings.sm.states.utils.StateTimeoutUtils;
import software.wings.stencils.DefaultValue;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.reinert.jjschema.Attributes;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@JsonIgnoreProperties(ignoreUnknown = true)
@TargetModule(_870_CG_ORCHESTRATION)
@OwnedBy(CDP)
@BreakDependencyOn("software.wings.service.intfc.DelegateService")
public class RancherResolveState extends State {
  @Inject private RancherStateHelper rancherStateHelper;
  @Inject private SweepingOutputService sweepingOutputService;
  @Inject private transient SettingsService settingsService;
  @Inject private transient DelegateService delegateService;
  @Inject private transient InfrastructureDefinitionService infrastructureDefinitionService;
  @Inject private transient ActivityService activityService;
  @Inject private SecretManager secretManager;
  @Inject private FeatureFlagService featureFlagService;
  @Inject K8sStateHelper k8sStateHelper;
  @Getter @Setter @Attributes(title = "Timeout (Minutes)") @DefaultValue("10") private Integer stateTimeoutInMinutes;

  private static final long MIN_TASK_TIMEOUT_IN_MINUTES = 1L;

  public RancherResolveState(String name) {
    super(name, RANCHER_RESOLVE.name());
  }

  @Override
  public ExecutionResponse execute(ExecutionContext context) {
    if (!k8sStateHelper.isRancherInfraMapping(context)) {
      return rancherStateHelper.getInvalidInfraDefFailedResponse();
    }

    try {
      Activity activity = createRancherActivity(context);

      RancherClusterElementList clusterElementList =
          sweepingOutputService.findSweepingOutput(context.prepareSweepingOutputInquiryBuilder()
                                                       .name(RancherClusterElementList.getSweepingOutputID(context))
                                                       .build());
      if (Objects.nonNull(clusterElementList)) {
        List<String> clusters = clusterElementList.getRancherClusterElements()
                                    .stream()
                                    .map(RancherClusterElement::getClusterName)
                                    .collect(Collectors.toList());
        log.info(
            "Possible duplicate execution of Rancher Resolve within a workflow. Resolved clusters list from Sweeping output: [{}]. Skipping this.",
            clusters);
        return ExecutionResponse.builder()
            .executionStatus(ExecutionStatus.SKIPPED)
            .stateExecutionData(aStateExecutionData()
                                    .withErrorMsg("Cluster Resolution is already done. Filtered clusters list: "
                                        + clusters + ". Skipping.")
                                    .build())
            .build();
      }

      return executeInternal(context, activity.getUuid());
    } catch (WingsException e) {
      throw e;
    } catch (Exception e) {
      throw new InvalidRequestException(getMessage(e), e);
    }
  }

  private ExecutionResponse executeInternal(ExecutionContext context, String activityId) {
    RancherKubernetesInfrastructureMapping rancherKubernetesInfrastructureMapping =
        rancherStateHelper.fetchRancherKubernetesInfrastructureMapping(context);

    InfrastructureDefinition infrastructureDefinition = infrastructureDefinitionService.getInfraDefById(
        context.getAccountId(), rancherKubernetesInfrastructureMapping.getInfrastructureDefinitionId());
    SettingAttribute settingAttribute =
        settingsService.get(rancherKubernetesInfrastructureMapping.getComputeProviderSettingId());
    RancherConfig rancherConfig = (RancherConfig) settingAttribute.getValue();

    List<EncryptedDataDetail> encryptedDataDetails =
        secretManager.getEncryptionDetails(rancherConfig, context.getAppId(), context.getWorkflowExecutionId());
    boolean isTimeoutFailureSupported =
        featureFlagService.isEnabled(SPG_CG_TIMEOUT_FAILURE_AT_WORKFLOW, context.getAccountId());

    RancherResolveClustersTaskParameters rancherResolveClustersTaskParameters =
        RancherResolveClustersTaskParameters.builder()
            .rancherConfig(rancherConfig)
            .encryptedDataDetails(encryptedDataDetails)
            .timeout(stateTimeoutInMinutes)
            .timeoutSupported(isTimeoutFailureSupported)
            .clusterSelectionCriteria(((RancherKubernetesInfrastructure) infrastructureDefinition.getInfrastructure())
                                          .getClusterSelectionCriteria())
            .activityId(activityId)
            .appId(context.getAppId())
            .build();

    renderExpressionsInRancherResolveClustersTaskParameters(context, rancherResolveClustersTaskParameters);

    String waitId = generateUuid();
    DelegateTask delegateTask = DelegateTask.builder()
                                    .accountId(context.getApp().getAccountId())
                                    .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, context.getApp().getUuid())
                                    .waitId(waitId)
                                    .data(TaskData.builder()
                                              .async(true)
                                              .taskType(TaskType.RANCHER_RESOLVE_CLUSTERS.name())
                                              .parameters(new Object[] {rancherResolveClustersTaskParameters})
                                              .timeout(getTimeoutValue() * 60 * 1000L)
                                              .build())
                                    .selectionLogsTrackingEnabled(true)
                                    .build();

    delegateService.queueTaskV2(delegateTask);
    StateExecutionData executionData = new RancherStateExecutionData(activityId);

    return ExecutionResponse.builder()
        .async(true)
        .correlationIds(Collections.singletonList(waitId))
        .stateExecutionData(executionData)
        .build();
  }

  private void renderExpressionsInRancherResolveClustersTaskParameters(
      ExecutionContext context, RancherResolveClustersTaskParameters rancherResolveClustersTaskParameters) {
    List<ClusterSelectionCriteriaEntry> clusterSelectionCriteria =
        rancherResolveClustersTaskParameters.getClusterSelectionCriteria();
    List<ClusterSelectionCriteriaEntry> renderedClusterSelectionCriteriaEntries = new ArrayList<>();

    if (clusterSelectionCriteria != null) {
      for (ClusterSelectionCriteriaEntry clusterSelectionCriteriaEntry : clusterSelectionCriteria) {
        renderedClusterSelectionCriteriaEntries.add(
            ClusterSelectionCriteriaEntry.builder()
                .labelName(context.renderExpression(clusterSelectionCriteriaEntry.getLabelName()))
                .labelValues(context.renderExpression(clusterSelectionCriteriaEntry.getLabelValues()))
                .build());
      }
    }

    rancherResolveClustersTaskParameters.setClusterSelectionCriteria(renderedClusterSelectionCriteriaEntries);
  }

  private long getTimeoutValue() {
    long timeoutMillis = DEFAULT_ASYNC_CALL_TIMEOUT;

    if (this.stateTimeoutInMinutes != null) {
      long taskTimeoutInMinutes;
      if (stateTimeoutInMinutes < MIN_TASK_TIMEOUT_IN_MINUTES) {
        taskTimeoutInMinutes = MIN_TASK_TIMEOUT_IN_MINUTES;
      } else {
        taskTimeoutInMinutes = stateTimeoutInMinutes;
      }

      timeoutMillis = taskTimeoutInMinutes * 60L * 1000L;
    }

    return timeoutMillis;
  }

  @Override
  public ExecutionResponse handleAsyncResponse(ExecutionContext context, Map<String, ResponseData> response) {
    RancherResolveClustersResponse executionResponse =
        (RancherResolveClustersResponse) response.values().iterator().next();

    activityService.updateStatus(((RancherStateExecutionData) context.getStateExecutionData()).getActivityId(),
        context.getAppId(), executionResponse.getExecutionStatus());

    if (ExecutionStatus.FAILED == executionResponse.getExecutionStatus()) {
      if (executionResponse.isTimeoutError()) {
        return ExecutionResponse.builder()
            .executionStatus(executionResponse.getExecutionStatus())
            .failureTypes(FailureType.TIMEOUT)
            .stateExecutionData(context.getStateExecutionData())
            .errorMessage("Timed out while waiting for task to complete")
            .build();
      }
      return ExecutionResponse.builder()
          .executionStatus(executionResponse.getExecutionStatus())
          .stateExecutionData(context.getStateExecutionData())
          .build();
    }
    List<String> clusterNames = executionResponse.getClusters();
    List<RancherClusterElement> clusterElements =
        clusterNames.stream()
            .map(name -> new RancherClusterElement(UUIDGenerator.generateUuid(), name))
            .collect(Collectors.toList());

    sweepingOutputService.save(context.prepareSweepingOutputBuilder(SweepingOutputInstance.Scope.WORKFLOW)
                                   .name(RancherClusterElementList.getSweepingOutputID(context))
                                   .value(new RancherClusterElementList(clusterElements))
                                   .build());

    return ExecutionResponse.builder().executionStatus(ExecutionStatus.SUCCESS).build();
  }

  @Override
  public Integer getTimeoutMillis() {
    return StateTimeoutUtils.getTimeoutMillisFromMinutes(stateTimeoutInMinutes);
  }

  @Override
  public void handleAbortEvent(ExecutionContext context) {
    // NoOp
  }

  private Activity createRancherActivity(ExecutionContext executionContext) {
    Application app = ((ExecutionContextImpl) executionContext).fetchRequiredApp();
    Environment env = ((ExecutionContextImpl) executionContext).fetchRequiredEnvironment();

    Activity activity = Activity.builder()
                            .applicationName(app.getName())
                            .appId(app.getUuid())
                            .commandName(COMMAND_NAME)
                            .type(Activity.Type.Command)
                            .workflowType(executionContext.getWorkflowType())
                            .workflowExecutionName(executionContext.getWorkflowExecutionName())
                            .stateExecutionInstanceId(executionContext.getStateExecutionInstanceId())
                            .stateExecutionInstanceName(executionContext.getStateExecutionInstanceName())
                            .commandType(getStateType())
                            .workflowExecutionId(executionContext.getWorkflowExecutionId())
                            .workflowId(executionContext.getWorkflowId())
                            .commandUnits(Collections.singletonList(new RancherDummyCommandUnit(COMMAND_UNIT_NAME)))
                            .status(ExecutionStatus.RUNNING)
                            .commandUnitType(CommandUnitDetails.CommandUnitType.RANCHER)
                            .environmentId(env.getUuid())
                            .environmentName(env.getName())
                            .environmentType(env.getEnvironmentType())
                            .build();

    return activityService.save(activity);
  }
}
