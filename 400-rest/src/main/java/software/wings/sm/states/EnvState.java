/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;
import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.ExecutionStatus.FAILED;
import static io.harness.beans.ExecutionStatus.REJECTED;
import static io.harness.beans.ExecutionStatus.SUCCESS;
import static io.harness.beans.FeatureName.RESOLVE_DEPLOYMENT_TAGS_BEFORE_EXECUTION;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.ExecutionContext.MANAGER;
import static io.harness.exception.WingsException.USER;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.api.EnvStateExecutionData.Builder.anEnvStateExecutionData;
import static software.wings.beans.ExecutionCredential.ExecutionType.SSH;
import static software.wings.beans.SSHExecutionCredential.Builder.aSSHExecutionCredential;
import static software.wings.beans.alert.AlertType.DEPLOYMENT_FREEZE_EVENT;
import static software.wings.sm.ExecutionInterrupt.ExecutionInterruptBuilder.anExecutionInterrupt;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionInterruptType;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.FeatureName;
import io.harness.beans.OrchestrationWorkflowType;
import io.harness.beans.RepairActionCode;
import io.harness.beans.SweepingOutputInstance.Scope;
import io.harness.beans.WorkflowType;
import io.harness.context.ContextElementType;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.exception.DeploymentFreezeException;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.logging.ExceptionLogger;
import io.harness.logging.Misc;
import io.harness.tasks.ResponseData;

import software.wings.api.ArtifactCollectionExecutionData;
import software.wings.api.EnvStateExecutionData;
import software.wings.api.artifact.ServiceArtifactElement;
import software.wings.api.artifact.ServiceArtifactElements;
import software.wings.api.artifact.ServiceArtifactVariableElement;
import software.wings.api.artifact.ServiceArtifactVariableElements;
import software.wings.api.helm.ServiceHelmElement;
import software.wings.api.helm.ServiceHelmElements;
import software.wings.beans.Application;
import software.wings.beans.ArtifactVariable;
import software.wings.beans.DeploymentExecutionContext;
import software.wings.beans.EntityType;
import software.wings.beans.ExecutionArgs;
import software.wings.beans.HelmChartInputType;
import software.wings.beans.ManifestVariable;
import software.wings.beans.NameValuePair;
import software.wings.beans.VariableType;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.WorkflowExecution.WorkflowExecutionKeys;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.appmanifest.HelmChart;
import software.wings.beans.artifact.Artifact;
import software.wings.common.NotificationMessageResolver;
import software.wings.service.impl.WorkflowExecutionUpdate;
import software.wings.service.impl.deployment.checks.DeploymentFreezeUtils;
import software.wings.service.impl.workflow.WorkflowServiceHelper;
import software.wings.service.intfc.ApplicationManifestService;
import software.wings.service.intfc.ArtifactService;
import software.wings.service.intfc.ArtifactStreamServiceBindingService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.WorkflowService;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionInterrupt;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.ExecutionResponse.ExecutionResponseBuilder;
import software.wings.sm.State;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.StateType;
import software.wings.sm.WorkflowStandardParams;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.reinert.jjschema.Attributes;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.inject.Inject;
import com.google.inject.Injector;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.mongodb.morphia.annotations.Transient;

/**
 * A Env state to pause state machine execution.
 *
 * @author Rishi
 */
@OwnedBy(CDC)
@Attributes(title = "Env")
@Slf4j
@FieldNameConstants(innerTypeName = "EnvStateKeys")
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class EnvState extends State implements WorkflowState {
  public static final Integer ENV_STATE_TIMEOUT_MILLIS = 7 * 24 * 60 * 60 * 1000;

  // NOTE: This field should no longer be used. It contains incorrect/stale values.
  @Attributes(required = true, title = "Environment") @Setter @Deprecated private String envId;

  @Attributes(required = true, title = "Workflow") @Setter private String workflowId;

  @Setter @SchemaIgnore private String pipelineId;
  @Setter @SchemaIgnore private String pipelineStageElementId;
  @Setter @SchemaIgnore private int pipelineStageParallelIndex;
  @Setter @SchemaIgnore private String stageName;

  @JsonIgnore @SchemaIgnore private Map<String, String> workflowVariables;

  @Setter @SchemaIgnore List<String> runtimeInputVariables;
  @Setter @SchemaIgnore long timeout;
  @Setter @SchemaIgnore List<String> userGroupIds;
  @Setter @SchemaIgnore RepairActionCode timeoutAction;

  @Inject private Injector injector;
  @Transient @Inject private WorkflowService workflowService;
  @Transient @Inject private WorkflowExecutionService executionService;
  @Transient @Inject private WorkflowExecutionUpdate executionUpdate;
  @Transient @Inject private ArtifactService artifactService;
  @Transient @Inject private ArtifactStreamServiceBindingService artifactStreamServiceBindingService;
  @Transient @Inject private ApplicationManifestService applicationManifestService;
  @Transient @Inject private SweepingOutputService sweepingOutputService;
  @Transient @Inject private FeatureFlagService featureFlagService;
  @Transient @Inject private NotificationMessageResolver notificationMessageResolver;
  @Transient @Inject private DeploymentFreezeUtils deploymentFreezeUtils;

  @Getter @Setter @JsonIgnore private boolean disable;

  @Getter @Setter private String disableAssertion;
  @Getter @Setter private boolean continued;

  public EnvState(String name) {
    super(name, StateType.ENV_STATE.name());
  }

  /* (non-Javadoc)
   * @see software.wings.sm.State#execute(software.wings.sm.ExecutionContext)
   */
  @Override
  public ExecutionResponse execute(ExecutionContext context) {
    WorkflowStandardParams workflowStandardParams = context.getContextElement(ContextElementType.STANDARD);
    String appId = workflowStandardParams.getAppId();
    String accountId = context.getAccountId();
    Workflow workflow = workflowService.readWorkflowWithoutServices(appId, workflowId);
    EnvStateExecutionData envStateExecutionData = anEnvStateExecutionData().withWorkflowId(workflowId).build();
    if (workflow == null || workflow.getOrchestrationWorkflow() == null) {
      return ExecutionResponse.builder()
          .executionStatus(FAILED)
          .errorMessage("Workflow does not exist")
          .stateExecutionData(envStateExecutionData)
          .build();
    }

    DeploymentExecutionContext deploymentExecutionContext = (DeploymentExecutionContext) context;
    List<Artifact> artifacts = deploymentExecutionContext.getArtifacts();
    List<ArtifactVariable> artifactVariables = getArtifactVariables(deploymentExecutionContext, workflowStandardParams);
    artifactVariables.addAll(getArtifactVariablesFromArtifactInputs(workflowStandardParams));

    ExecutionArgs executionArgs = new ExecutionArgs();
    executionArgs.setWorkflowType(WorkflowType.ORCHESTRATION);
    executionArgs.setOrchestrationId(workflowId);
    executionArgs.setArtifacts(artifacts);
    executionArgs.setArtifactVariables(artifactVariables);
    executionArgs.setContinueRunningPipelinesDuringMigration(
        featureFlagService.isEnabled(FeatureName.FREEZE_DURING_MIGRATION, accountId));

    if (featureFlagService.isEnabled(FeatureName.HELM_CHART_AS_ARTIFACT, context.getAccountId())) {
      List<HelmChart> helmCharts = deploymentExecutionContext.getHelmCharts();
      List<ManifestVariable> manifestVariables = getManifestVariables(workflowStandardParams);
      manifestVariables.addAll(getManifestVariablesFromManifestInputs(workflowStandardParams));
      executionArgs.setHelmCharts(helmCharts);
      executionArgs.setManifestVariables(manifestVariables);
    }

    executionArgs.setExecutionCredential(aSSHExecutionCredential().withExecutionType(SSH).build());
    executionArgs.setTriggeredFromPipeline(true);
    executionArgs.setPipelineId(pipelineId);
    executionArgs.setPipelinePhaseElementId(context.getPipelineStageElementId());
    executionArgs.setPipelinePhaseParallelIndex(context.getPipelineStageParallelIndex());
    executionArgs.setStageName(context.getPipelineStageName());
    executionArgs.setWorkflowVariables(populatePipelineVariables(workflow, workflowStandardParams));
    executionArgs.setExcludeHostsWithSameArtifact(workflowStandardParams.isExcludeHostsWithSameArtifact());
    executionArgs.setNotifyTriggeredUserOnly(workflowStandardParams.isNotifyTriggeredUserOnly());

    envStateExecutionData.setOrchestrationWorkflowType(
        workflow.getOrchestrationWorkflow().getOrchestrationWorkflowType());
    try {
      WorkflowExecution execution = executionService.triggerOrchestrationExecution(
          appId, null, workflowId, context.getWorkflowExecutionId(), executionArgs, null);
      envStateExecutionData.setWorkflowExecutionId(execution.getUuid());
      return ExecutionResponse.builder()
          .async(true)
          .correlationIds(asList(execution.getUuid()))
          .stateExecutionData(envStateExecutionData)
          .build();
    } catch (DeploymentFreezeException dfe) {
      log.warn(dfe.getMessage());
      // TODO: Notification Handling for rejected pipelines
      if (!dfe.isMasterFreeze()) {
        Map<String, String> placeholderValues = getPlaceHolderValues(context);
        deploymentFreezeUtils.sendPipelineRejectionNotification(
            accountId, appId, dfe.getDeploymentFreezeIds(), placeholderValues);
      }
      return ExecutionResponse.builder()
          .executionStatus(REJECTED)
          .errorMessage(dfe.getMessage())
          .stateExecutionData(envStateExecutionData)
          .build();
    } catch (Exception e) {
      log.error("Failed to start workflow execution: ", e);
      String message = ExceptionUtils.getMessage(e);
      return ExecutionResponse.builder()
          .executionStatus(FAILED)
          .errorMessage(message)
          .stateExecutionData(envStateExecutionData)
          .build();
    }
  }

  private List<ArtifactVariable> getArtifactVariablesFromArtifactInputs(WorkflowStandardParams workflowStandardParams) {
    if (isEmpty(workflowStandardParams.getArtifactInputs())) {
      return new ArrayList<>();
    }

    return workflowStandardParams.getArtifactInputs()
        .stream()
        .map(artifactInput -> ArtifactVariable.builder().artifactInput(artifactInput).build())
        .collect(toList());
  }

  private List<ManifestVariable> getManifestVariablesFromManifestInputs(WorkflowStandardParams workflowStandardParams) {
    if (isEmpty(workflowStandardParams.getManifestInputs())) {
      return new ArrayList<>();
    }

    return workflowStandardParams.getManifestInputs()
        .stream()
        .map(manifestInput
            -> ManifestVariable.builder()
                   .appManifestId(manifestInput.getAppManifestId())
                   .value(manifestInput.getBuildNo())
                   .inputType(HelmChartInputType.VERSION)
                   .build())
        .collect(toList());
  }

  private Map<String, String> getPlaceHolderValues(ExecutionContext context) {
    Application app = Objects.requireNonNull(context.getApp());
    WorkflowExecution workflowExecution =
        executionService.fetchWorkflowExecution(app.getUuid(), context.getWorkflowExecutionId(),
            WorkflowExecutionKeys.createdAt, WorkflowExecutionKeys.triggeredBy, WorkflowExecutionKeys.status);

    notNullCheck("Pipeline execution in context " + context.getWorkflowExecutionId()
            + " doesn't exist in the specified application " + app.getUuid(),
        workflowExecution, USER);
    String userName = workflowExecution.getTriggeredBy() != null ? workflowExecution.getTriggeredBy().getName() : "";

    return notificationMessageResolver.getPlaceholderValues(context, userName, workflowExecution.getCreatedAt(),
        System.currentTimeMillis(), "", "rejected", "", REJECTED, DEPLOYMENT_FREEZE_EVENT);
  }

  private List<ManifestVariable> getManifestVariables(WorkflowStandardParams workflowStandardParams) {
    return Optional.ofNullable(workflowStandardParams.getWorkflowElement().getManifestVariables())
        .orElse(new ArrayList<>());
  }

  private List<ArtifactVariable> getArtifactVariables(
      DeploymentExecutionContext context, WorkflowStandardParams workflowStandardParams) {
    List<ArtifactVariable> artifactVariables;
    List<ServiceArtifactVariableElement> artifactVariableElements = context.getArtifactVariableElements();
    if (isEmpty(artifactVariableElements)) {
      artifactVariables = workflowStandardParams.getWorkflowElement().getArtifactVariables();
      return artifactVariables == null ? new ArrayList<>() : artifactVariables;
    }

    artifactVariables = new ArrayList<>();
    Map<String, ArtifactVariable> workflowVariablesMap = new HashMap<>();
    for (ServiceArtifactVariableElement savElement : artifactVariableElements) {
      Artifact artifact = artifactService.get(savElement.getUuid());
      if (artifact == null) {
        continue;
      }

      List<String> allowedList = new ArrayList<>();
      allowedList.add(artifact.getArtifactStreamId());

      EntityType entityType = savElement.getEntityType();
      String entityId = savElement.getEntityId();
      if (entityType == null) {
        entityType = EntityType.SERVICE;
        entityId = savElement.getServiceId();
      }

      switch (entityType) {
        case WORKFLOW:
          // Skip if workflow ids are different.
          if (!workflowId.equals(entityId)) {
            continue;
          }
          break;
        case ENVIRONMENT:
        case SERVICE:
          // Skip if entity id is blank.
          // envId/serviceId is not checked with workflow envIds/serviceIds here, it is checked in
          // WorkflowExecutionService.populateArtifacts.
          if (StringUtils.isBlank(savElement.getEntityId())) {
            continue;
          }
          break;
        default:
          // Skip any other entity type.
          continue;
      }

      ArtifactVariable artifactVariable = ArtifactVariable.builder()
                                              .type(VariableType.ARTIFACT)
                                              .name(savElement.getArtifactVariableName())
                                              .value(savElement.getUuid())
                                              .entityType(entityType)
                                              .entityId(entityId)
                                              .allowedList(allowedList)
                                              .build();

      if (EntityType.ENVIRONMENT == entityType) {
        List<ArtifactVariable> overriddenArtifactVariables = new ArrayList<>();
        if (!StringUtils.isBlank(savElement.getServiceId())) {
          overriddenArtifactVariables.add(ArtifactVariable.builder()
                                              .type(VariableType.ARTIFACT)
                                              .name(savElement.getArtifactVariableName())
                                              .value(savElement.getUuid())
                                              .entityType(EntityType.SERVICE)
                                              .entityId(savElement.getServiceId())
                                              .allowedList(allowedList)
                                              .build());
        }
        artifactVariable.setOverriddenArtifactVariables(overriddenArtifactVariables);
      }

      // NOTE: collisions here should be fixed later as we some artifact variables might get filtered out because of
      // envId/serviceId resolved later.
      if (EntityType.WORKFLOW == entityType) {
        workflowVariablesMap.put(artifactVariable.getName(), artifactVariable);
      } else {
        artifactVariables.add(artifactVariable);
      }
    }

    for (Entry<String, ArtifactVariable> entry : workflowVariablesMap.entrySet()) {
      String name = entry.getKey();
      List<ArtifactVariable> overriddenArtifactVariables =
          artifactVariables.stream()
              .filter(artifactVariable -> artifactVariable.getName().equals(name))
              .collect(toList());
      if (isNotEmpty(overriddenArtifactVariables)) {
        artifactVariables.removeIf(artifactVariable -> artifactVariable.getName().equals(name));
      }

      ArtifactVariable artifactVariable = entry.getValue();
      artifactVariable.setOverriddenArtifactVariables(overriddenArtifactVariables);
      artifactVariables.add(artifactVariable);
    }

    return artifactVariables;
  }

  private Map<String, String> populatePipelineVariables(
      Workflow workflow, WorkflowStandardParams workflowStandardParams) {
    return WorkflowServiceHelper.overrideWorkflowVariables(workflow.getOrchestrationWorkflow().getUserVariables(),
        workflowVariables, workflowStandardParams.getWorkflowVariables());
  }

  @Override
  public void parseProperties(Map<String, Object> properties) {
    boolean isDisabled = properties.get(EnvStateKeys.disable) != null && (boolean) properties.get(EnvStateKeys.disable);
    if (isDisabled && properties.get(EnvStateKeys.disableAssertion) == null) {
      properties.put(EnvStateKeys.disableAssertion, "true");
    }
    super.parseProperties(properties);
  }

  /**
   * Handle abort event.
   *
   * @param context the context
   */
  @Override
  public void handleAbortEvent(ExecutionContext context) {
    if (context == null || context.getStateExecutionData() == null) {
      return;
    }
    context.getStateExecutionData().setErrorMsg(
        "Workflow not completed within " + Misc.getDurationString(getTimeoutMillis()));
    try {
      EnvStateExecutionData envStateExecutionData = context.getStateExecutionData();
      if (envStateExecutionData != null && envStateExecutionData.getWorkflowExecutionId() != null) {
        ExecutionInterrupt executionInterrupt = anExecutionInterrupt()
                                                    .executionInterruptType(ExecutionInterruptType.ABORT_ALL)
                                                    .executionUuid(envStateExecutionData.getWorkflowExecutionId())
                                                    .appId(context.getAppId())
                                                    .build();
        executionService.triggerExecutionInterrupt(executionInterrupt);
      }
    } catch (WingsException exception) {
      ExceptionLogger.logProcessedMessages(exception, MANAGER, log);
    } catch (RuntimeException exception) {
      log.error("Could not abort workflows.", exception);
    }
  }

  @Override
  public ExecutionResponse handleAsyncResponse(ExecutionContext context, Map<String, ResponseData> response) {
    EnvExecutionResponseData responseData = (EnvExecutionResponseData) response.values().iterator().next();
    ExecutionResponseBuilder executionResponseBuilder =
        ExecutionResponse.builder().executionStatus(responseData.getStatus());

    if (responseData.getStatus() != SUCCESS) {
      return executionResponseBuilder.build();
    }

    EnvStateExecutionData stateExecutionData = context.getStateExecutionData();
    if (stateExecutionData.getOrchestrationWorkflowType() == OrchestrationWorkflowType.BUILD) {
      if (!featureFlagService.isEnabled(FeatureName.ARTIFACT_STREAM_REFACTOR, context.getAccountId())) {
        saveArtifactAndManifestElements(context, stateExecutionData);
      } else {
        saveArtifactVariableElements(context, stateExecutionData);
      }
    }

    if (context.getWorkflowType() == WorkflowType.PIPELINE
        && featureFlagService.isEnabled(RESOLVE_DEPLOYMENT_TAGS_BEFORE_EXECUTION, context.getApp().getAccountId())) {
      executionUpdate.setAppId(context.getAppId());
      executionUpdate.setWorkflowExecutionId(context.getWorkflowExecutionId());
      final String workflowId = context.getWorkflowId(); // this will be pipelineId in case of pipeline
      injector.injectMembers(executionUpdate);
      List<NameValuePair> resolvedTags = executionUpdate.resolveDeploymentTags(context, workflowId);
      executionUpdate.addTagsToWorkflowExecution(resolvedTags);
    }
    return executionResponseBuilder.build();
  }

  private void saveArtifactAndManifestElements(ExecutionContext context, EnvStateExecutionData stateExecutionData) {
    saveArtifactElements(context, stateExecutionData);
    saveHelmChartElements(context, stateExecutionData);
  }

  private void saveArtifactElements(ExecutionContext context, EnvStateExecutionData stateExecutionData) {
    List<Artifact> artifacts =
        executionService.getArtifactsCollected(context.getAppId(), stateExecutionData.getWorkflowExecutionId());
    if (isEmpty(artifacts)) {
      return;
    }

    List<ServiceArtifactElement> artifactElements = new ArrayList<>();
    artifacts.forEach(artifact
        -> artifactElements.add(
            ServiceArtifactElement.builder()
                .uuid(artifact.getUuid())
                .name(artifact.getDisplayName())
                .serviceIds(artifactStreamServiceBindingService.listServiceIds(artifact.getArtifactStreamId()))
                .build()));

    sweepingOutputService.save(
        context.prepareSweepingOutputBuilder(Scope.PIPELINE)
            .name(ServiceArtifactElements.SWEEPING_OUTPUT_NAME + context.getStateExecutionInstanceId())
            .value(ServiceArtifactElements.builder().artifactElements(artifactElements).build())
            .build());
  }

  private void saveHelmChartElements(ExecutionContext context, EnvStateExecutionData stateExecutionData) {
    List<HelmChart> charts =
        executionService.getManifestsCollected(context.getAppId(), stateExecutionData.getWorkflowExecutionId());
    if (isEmpty(charts)) {
      return;
    }

    List<ServiceHelmElement> helmElements = new ArrayList<>();
    charts.forEach(chart -> {
      ApplicationManifest manifest =
          applicationManifestService.getById(context.getAppId(), chart.getApplicationManifestId());
      String serviceId = null;
      if (manifest != null) {
        serviceId = manifest.getServiceId();
      }
      helmElements.add(ServiceHelmElement.builder()
                           .uuid(chart.getUuid())
                           .name(chart.getDisplayName())
                           .serviceIds(Collections.singletonList(serviceId))
                           .build());
    });

    sweepingOutputService.save(
        context.prepareSweepingOutputBuilder(Scope.PIPELINE)
            .name(ServiceHelmElements.SWEEPING_OUTPUT_NAME + context.getStateExecutionInstanceId())
            .value(ServiceHelmElements.builder().helmElements(helmElements).build())
            .build());
  }

  private void saveArtifactVariableElements(ExecutionContext context, EnvStateExecutionData stateExecutionData) {
    List<StateExecutionInstance> allStateExecutionInstances =
        executionService.getStateExecutionInstances(context.getAppId(), stateExecutionData.getWorkflowExecutionId());
    if (isEmpty(allStateExecutionInstances)) {
      return;
    }

    List<ServiceArtifactVariableElement> artifactVariableElements = new ArrayList<>();
    allStateExecutionInstances.forEach(stateExecutionInstance -> {
      if (!(stateExecutionInstance.fetchStateExecutionData() instanceof ArtifactCollectionExecutionData)) {
        return;
      }

      ArtifactCollectionExecutionData artifactCollectionExecutionData =
          (ArtifactCollectionExecutionData) stateExecutionInstance.fetchStateExecutionData();
      Artifact artifact = artifactService.get(artifactCollectionExecutionData.getArtifactId());
      artifactVariableElements.add(ServiceArtifactVariableElement.builder()
                                       .uuid(artifact.getUuid())
                                       .name(artifact.getDisplayName())
                                       .entityType(artifactCollectionExecutionData.getEntityType())
                                       .entityId(artifactCollectionExecutionData.getEntityId())
                                       .serviceId(artifactCollectionExecutionData.getServiceId())
                                       .artifactVariableName(artifactCollectionExecutionData.getArtifactVariableName())
                                       .build());
    });

    sweepingOutputService.save(
        context.prepareSweepingOutputBuilder(Scope.PIPELINE)
            .name(ServiceArtifactVariableElements.SWEEPING_OUTPUT_NAME + context.getStateExecutionInstanceId())
            .value(ServiceArtifactVariableElements.builder().artifactVariableElements(artifactVariableElements).build())
            .build());
  }

  @Deprecated
  public String getEnvId() {
    return envId;
  }
  public String getWorkflowId() {
    return workflowId;
  }
  public String getPipelineId() {
    return pipelineId;
  }
  public String getPipelineStageElementId() {
    return pipelineStageElementId;
  }
  public int getPipelineStageParallelIndex() {
    return pipelineStageParallelIndex;
  }
  public Map<String, String> getWorkflowVariables() {
    return workflowVariables;
  }

  public List<String> getRuntimeInputVariables() {
    return runtimeInputVariables;
  }

  public long getTimeout() {
    return timeout;
  }

  public List<String> getUserGroupIds() {
    return userGroupIds;
  }

  public RepairActionCode getTimeoutAction() {
    return timeoutAction;
  }

  public String getStageName() {
    return stageName;
  }

  @SchemaIgnore
  @Override
  public Integer getTimeoutMillis() {
    if (super.getTimeoutMillis() == null) {
      return ENV_STATE_TIMEOUT_MILLIS;
    }
    return super.getTimeoutMillis();
  }

  public void setWorkflowVariables(Map<String, String> workflowVariables) {
    this.workflowVariables = workflowVariables;
  }

  /**
   * The type Env execution response data.
   */
  public static class EnvExecutionResponseData implements DelegateResponseData {
    private String workflowExecutionId;
    private ExecutionStatus status;

    /**
     * Instantiates a new Env execution response data.
     *
     * @param workflowExecutionId the workflow execution id
     * @param status              the status
     */
    public EnvExecutionResponseData(String workflowExecutionId, ExecutionStatus status) {
      this.workflowExecutionId = workflowExecutionId;
      this.status = status;
    }

    /**
     * Gets workflow execution id.
     *
     * @return the workflow execution id
     */
    public String getWorkflowExecutionId() {
      return workflowExecutionId;
    }

    /**
     * Sets workflow execution id.
     *
     * @param workflowExecutionId the workflow execution id
     */
    public void setWorkflowExecutionId(String workflowExecutionId) {
      this.workflowExecutionId = workflowExecutionId;
    }

    /**
     * Gets status.
     *
     * @return the status
     */
    public ExecutionStatus getStatus() {
      return status;
    }

    /**
     * Sets status.
     *
     * @param status the status
     */
    public void setStatus(ExecutionStatus status) {
      this.status = status;
    }
  }
}
