/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.FeatureName.SPG_CG_LIST_RESUMED_PIPELINES;
import static io.harness.beans.OrchestrationWorkflowType.BUILD;
import static io.harness.beans.WorkflowType.ORCHESTRATION;
import static io.harness.beans.WorkflowType.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.exception.WingsException.USER;
import static io.harness.expression.ExpressionEvaluator.matchesVariablePattern;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.VariableType.ENTITY;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.CreatedByType;
import io.harness.beans.ExecutionCause;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.OrchestrationWorkflowType;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.ExpressionEvaluator;
import io.harness.ff.FeatureFlagService;

import software.wings.api.CanaryWorkflowStandardParams;
import software.wings.api.DeploymentType;
import software.wings.api.WorkflowElement;
import software.wings.beans.CanaryOrchestrationWorkflow;
import software.wings.beans.ExecutionArgs;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.Pipeline;
import software.wings.beans.PipelineStage;
import software.wings.beans.PipelineStage.PipelineStageElement;
import software.wings.beans.RuntimeInputsConfig;
import software.wings.beans.Service;
import software.wings.beans.Variable;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.appmanifest.HelmChart;
import software.wings.beans.deployment.WorkflowVariablesMetadata;
import software.wings.dl.WingsPersistence;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.intfc.InfrastructureDefinitionService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.PipelineService;
import software.wings.service.intfc.StateExecutionService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.WorkflowService;
import software.wings.sm.StateExecutionData;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.StateExecutionInstance.StateExecutionInstanceKeys;
import software.wings.sm.StateMachine;
import software.wings.sm.WorkflowStandardParams;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.query.Query;
import dev.morphia.query.Sort;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@OwnedBy(CDC)
@Singleton
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class WorkflowExecutionServiceHelper {
  @Inject private WorkflowExecutionService workflowExecutionService;
  @Inject private WorkflowService workflowService;
  @Inject private InfrastructureDefinitionService infrastructureDefinitionService;
  @Inject private InfrastructureMappingService infrastructureMappingService;
  @Inject private PipelineService pipelineService;
  @Inject static FeatureFlagService featureFlagService;
  @Inject private WingsPersistence wingsPersistence;
  @Inject private StateExecutionService stateExecutionService;

  public WorkflowVariablesMetadata fetchWorkflowVariables(
      String appId, ExecutionArgs executionArgs, String workflowExecutionId) {
    List<Variable> workflowVariables = fetchWorkflowVariables(appId, executionArgs);
    if (isBlank(workflowExecutionId) || isEmpty(workflowVariables)) {
      return new WorkflowVariablesMetadata(workflowVariables);
    }
    WorkflowExecution workflowExecution = workflowExecutionService.getWorkflowExecution(appId, workflowExecutionId);
    if (workflowExecution == null || workflowExecution.getExecutionArgs() == null
        || executionArgs.getWorkflowType() != workflowExecution.getWorkflowType()
        || (ORCHESTRATION == workflowExecution.getWorkflowType()
            && !executionArgs.getOrchestrationId().equals(workflowExecution.getWorkflowId()))
        || (PIPELINE == workflowExecution.getWorkflowType()
            && (workflowExecution.getPipelineExecution() == null
                || workflowExecution.getPipelineExecution().getPipeline() == null
                || !executionArgs.getPipelineId().equals(workflowExecution.getPipelineExecution().getPipelineId())))) {
      return new WorkflowVariablesMetadata(workflowVariables);
    }

    Map<String, String> oldWorkflowVariablesValueMap = workflowExecution.getExecutionArgs().getWorkflowVariables();
    if (isEmpty(oldWorkflowVariablesValueMap)) {
      boolean changed = false;
      for (Variable variable : workflowVariables) {
        if (!Boolean.TRUE.equals(variable.getRuntimeInput()) && variable.isMandatory()) {
          changed = true;
          break;
        }
      }

      return new WorkflowVariablesMetadata(workflowVariables, changed);
    }

    boolean changed = populateWorkflowVariablesValues(workflowVariables, new HashMap<>(oldWorkflowVariablesValueMap));
    return new WorkflowVariablesMetadata(workflowVariables, changed);
  }

  @NotNull
  public Workflow obtainWorkflow(@NotNull String appId, @NotNull String workflowId) {
    Workflow workflow = workflowService.readWorkflow(appId, workflowId);
    notNullCheck("Error reading workflow. Might be deleted", workflow);
    notNullCheck("Error reading workflow. Might be deleted", workflow.getOrchestrationWorkflow());
    if (!workflow.getOrchestrationWorkflow().isValid()) {
      throw new InvalidRequestException("Workflow requested for execution is not valid/complete.");
    }

    return workflow;
  }

  @NotNull
  public WorkflowExecution obtainExecution(Workflow workflow, StateMachine stateMachine, String resolveEnvId,
      String pipelineExecutionId, @NotNull ExecutionArgs executionArgs) {
    WorkflowExecution workflowExecution = WorkflowExecution.builder().build();
    workflowExecution.setAppId(workflow.getAppId());
    workflowExecution.setAccountId(workflow.getAccountId());

    if (resolveEnvId != null) {
      workflowExecution.setEnvId(resolveEnvId);
      workflowExecution.setEnvIds(Collections.singletonList(resolveEnvId));
    }

    workflowExecution.setWorkflowId(workflow.getUuid());
    workflowExecution.setWorkflowIds(asList(workflow.getUuid()));
    workflowExecution.setName(workflow.getName());
    workflowExecution.setWorkflowType(ORCHESTRATION);
    workflowExecution.setOrchestrationType(workflow.getOrchestrationWorkflow().getOrchestrationWorkflowType());
    workflowExecution.setConcurrencyStrategy(workflow.getOrchestrationWorkflow().getConcurrencyStrategy());
    workflowExecution.setStateMachine(stateMachine);
    workflowExecution.setPipelineExecutionId(pipelineExecutionId);
    workflowExecution.setExecutionArgs(executionArgs);
    workflowExecution.setStageName(executionArgs.getStageName());

    Map<String, String> workflowVariables = executionArgs.getWorkflowVariables();
    List<Service> services = workflowService.getResolvedServices(workflow, workflowVariables);
    if (isNotEmpty(services)) {
      workflowExecution.setServiceIds(services.stream().map(Service::getUuid).collect(toList()));
      if (services.size() == 1) {
        Service targetService = services.get(0);
        boolean useSweepingOutput = targetService.getDeploymentType() == DeploymentType.SSH
            || targetService.getDeploymentType() == DeploymentType.WINRM;
        workflowExecution.setUseSweepingOutputs(useSweepingOutput);
      }
    }

    workflowExecution.setInfraDefinitionIds(
        workflowService.getResolvedInfraDefinitionIds(workflow, workflowVariables, resolveEnvId));
    workflowExecution.setCloudProviderIds(infrastructureDefinitionService.fetchCloudProviderIds(
        workflow.getAppId(), workflowExecution.getInfraDefinitionIds()));
    return workflowExecution;
  }

  @NotNull
  public WorkflowStandardParams obtainWorkflowStandardParams(
      String appId, String envId, @NotNull ExecutionArgs executionArgs, Workflow workflow) {
    WorkflowStandardParams stdParams;
    if (workflow.getOrchestrationWorkflow().getOrchestrationWorkflowType() == OrchestrationWorkflowType.CANARY
        || workflow.getOrchestrationWorkflow().getOrchestrationWorkflowType() == OrchestrationWorkflowType.BASIC
        || workflow.getOrchestrationWorkflow().getOrchestrationWorkflowType() == OrchestrationWorkflowType.ROLLING
        || workflow.getOrchestrationWorkflow().getOrchestrationWorkflowType() == OrchestrationWorkflowType.MULTI_SERVICE
        || workflow.getOrchestrationWorkflow().getOrchestrationWorkflowType() == BUILD
        || workflow.getOrchestrationWorkflow().getOrchestrationWorkflowType() == OrchestrationWorkflowType.BLUE_GREEN) {
      stdParams = new CanaryWorkflowStandardParams();

      if (workflow.getOrchestrationWorkflow() instanceof CanaryOrchestrationWorkflow) {
        CanaryOrchestrationWorkflow canaryOrchestrationWorkflow =
            (CanaryOrchestrationWorkflow) workflow.getOrchestrationWorkflow();
        if (canaryOrchestrationWorkflow.getUserVariables() != null) {
          stdParams.setWorkflowElement(WorkflowElement.builder()
                                           .variables(fetchWorkflowVariablesFromOrchestrationWorkflow(
                                               canaryOrchestrationWorkflow, executionArgs))
                                           .build());
        }
        if (isNotEmpty(canaryOrchestrationWorkflow.getWorkflowPhaseIds())
            && canaryOrchestrationWorkflow.getRollbackWorkflowPhaseIdMap().get(
                   canaryOrchestrationWorkflow.getWorkflowPhaseIds().get(0))
                != null) {
          stdParams.setLastDeployPhaseId(canaryOrchestrationWorkflow.getWorkflowPhaseIds().get(
              canaryOrchestrationWorkflow.getWorkflowPhaseIds().size() - 1));
          stdParams.setLastRollbackPhaseId(canaryOrchestrationWorkflow.getRollbackWorkflowPhaseIdMap()
                                               .get(canaryOrchestrationWorkflow.getWorkflowPhaseIds().get(0))
                                               .getUuid());
        }
      }
    } else {
      stdParams = new WorkflowStandardParams();
    }

    stdParams.setAppId(appId);
    stdParams.setEnvId(envId);
    if (isNotEmpty(executionArgs.getArtifacts())) {
      stdParams.setArtifactIds(executionArgs.getArtifacts().stream().map(Artifact::getUuid).collect(toList()));
    }

    if (isNotEmpty(executionArgs.getHelmCharts())) {
      stdParams.setHelmChartIds(
          executionArgs.getHelmCharts().stream().map(HelmChart::getUuid).filter(Objects::nonNull).collect(toList()));
    }

    stdParams.setExecutionCredential(executionArgs.getExecutionCredential());

    stdParams.setExcludeHostsWithSameArtifact(executionArgs.isExcludeHostsWithSameArtifact());
    stdParams.setNotifyTriggeredUserOnly(executionArgs.isNotifyTriggeredUserOnly());
    stdParams.setExecutionHosts(executionArgs.getHosts());
    return stdParams;
  }

  private Map<String, Object> fetchWorkflowVariablesFromOrchestrationWorkflow(
      CanaryOrchestrationWorkflow orchestrationWorkflow, ExecutionArgs executionArgs) {
    Map<String, Object> variables = new HashMap<>();
    if (orchestrationWorkflow.getUserVariables() == null) {
      return variables;
    }
    for (Variable variable : orchestrationWorkflow.getUserVariables()) {
      if (variable.isFixed()) {
        setVariables(variable.getName(), variable.getValue(), variables);
        continue;
      }

      // no input from user
      if (executionArgs == null || isEmpty(executionArgs.getWorkflowVariables())
          || isBlank(executionArgs.getWorkflowVariables().get(variable.getName()))) {
        if (variable.isMandatory() && isBlank(variable.getValue())) {
          throw new InvalidRequestException(
              "Workflow variable [" + variable.getName() + "] is mandatory for execution", USER);
        }
        if (isBlank(variable.getValue())) {
          setVariables(variable.getName(), "", variables);
        } else {
          setVariables(variable.getName(), variable.getValue(), variables);
        }
        continue;
      }
      // Verify for allowed values
      if (isNotEmpty(variable.getAllowedValues())) {
        if (isNotEmpty(variable.getValue())) {
          if (!variable.getAllowedList().contains(variable.getValue())) {
            throw new InvalidRequestException("Workflow variable value [" + variable.getValue()
                + " is not in Allowed Values [" + variable.getAllowedList() + "]");
          }
        }
      }
      setVariables(variable.getName(), executionArgs.getWorkflowVariables().get(variable.getName()), variables);
    }
    return variables;
  }

  private void setVariables(String key, Object value, Map<String, Object> variableMap) {
    if (!isNull(key)) {
      variableMap.put(key, value);
    }
  }

  private boolean isNull(String string) {
    return isEmpty(string) || string.equals("null");
  }

  private List<Variable> fetchWorkflowVariables(String appId, ExecutionArgs executionArgs) {
    // Fetch workflow variables without any value.
    List<Variable> workflowVariables;
    if (ORCHESTRATION == executionArgs.getWorkflowType()) {
      Workflow workflow = workflowService.readWorkflowWithoutServices(appId, executionArgs.getOrchestrationId());
      workflowVariables = (workflow == null || workflow.getOrchestrationWorkflow() == null)
          ? null
          : workflow.getOrchestrationWorkflow().getUserVariables();
    } else {
      workflowVariables = pipelineService.getPipelineVariables(appId, executionArgs.getPipelineId());
    }
    return (workflowVariables == null) ? Collections.emptyList() : workflowVariables;
  }

  /**
   * Populate workflow variables with values based on old workflow variables.
   * Current Behaviour:
   * - Don't set values if variable name not present in oldWorkflowVariablesMap
   * - Don't set values if variable corresponding to name is not of the same type and possibly entity type
   * - Otherwise set values
   * - Finally if value is not found for any entity-type variable or there is an entity-variable not present in current
   * workflow variables, all entity-type variables are reset. This is to make the behaviour simple and predictable for
   * now. This can made more intelligent later on.
   *
   * @param workflowVariables       current workflow variables - non-empty
   * @param oldWorkflowVariablesMap workflow variables from an older execution - non-empty
   * @return true if entity-type variables are reset, otherwise false
   */
  private boolean populateWorkflowVariablesValues(
      List<Variable> workflowVariables, Map<String, String> oldWorkflowVariablesMap) {
    // NOTE: workflowVariables and oldWorkflowVariablesMap are not empty.
    // oldEntityVariableNames is a set of all the names of old ENTITY workflow variables.
    if (oldWorkflowVariablesMap == null) {
      oldWorkflowVariablesMap = new HashMap<>();
    }
    boolean resetEntities = false;
    for (Variable variable : workflowVariables) {
      String name = variable.getName();
      boolean isEntity = ENTITY == variable.getType();
      boolean oldVariablePresent = oldWorkflowVariablesMap.containsKey(name);
      if (!oldVariablePresent && !Boolean.TRUE.equals(variable.getRuntimeInput())) {
        // A new workflow variable.
        if (isEntity) {
          // If there is a new workflow variable of type ENTITY set resetEntities = true.
          resetEntities = true;
        }
        continue;
      }
      String oldValue = oldWorkflowVariablesMap.get(name);
      if (!StringUtils.isBlank(oldValue)
          && (EmptyPredicate.isEmpty(variable.getAllowedList()) || variable.getAllowedList().contains(oldValue))) {
        // not updating value if variable itself is updated
        variable.setValue(oldValue);
      }
      // This is never a noop as we have already dealt with the case that this workflow variable is new.
      oldWorkflowVariablesMap.remove(name);
    }

    // resetEntities is true if there are one or more new ENTITY workflow variables.
    // oldEntityVariableNames is not empty when one or more ENTITY variables have been removed.
    if (resetEntities || isNotEmpty(oldWorkflowVariablesMap)) {
      resetEntityWorkflowVariables(workflowVariables);
      return true;
    }
    return false;
  }

  private void resetEntityWorkflowVariables(List<Variable> workflowVariables) {
    // NOTE: workflowVariables are not empty.
    for (Variable variable : workflowVariables) {
      if (ENTITY == variable.getType()) {
        variable.setValue(null);
      }
    }
  }

  public static boolean calculateCdPageCandidate(
      String pipelineExecutionId, String pipelineResumeId, boolean latestPipelineResume, String accountId) {
    boolean cdPageCandidate;
    if (isNotEmpty(pipelineResumeId) && verifyResumedPipelinesFF(accountId)) {
      cdPageCandidate = latestPipelineResume;
    } else {
      // For a pipeline which is not resumed at all LatestPipelineResume is false and pipelineResumeId is null.
      cdPageCandidate = true;
    }

    if (!isEmpty(pipelineExecutionId)) {
      cdPageCandidate = false;
    }
    return cdPageCandidate;
  }

  private static boolean verifyResumedPipelinesFF(String accountId) {
    // KEEP THE SAME BEHAVIOR AS BEFORE THE FF
    if (featureFlagService == null) {
      return true;
    }
    // EVALUATE THE FF
    return featureFlagService.isNotEnabled(SPG_CG_LIST_RESUMED_PIPELINES, accountId);
  }

  public WorkflowVariablesMetadata fetchWorkflowVariablesForRunningExecution(
      String appId, String workflowExecutionId, String pipelineStageElementId) {
    List<Variable> workflowVariables =
        fetchWorkflowVariablesRunningPipeline(appId, workflowExecutionId, pipelineStageElementId);
    return new WorkflowVariablesMetadata(workflowVariables);
  }

  private List<Variable> fetchWorkflowVariablesRunningPipeline(
      String appId, String pipelineExecutionId, String pipelineStageElementId) {
    WorkflowExecution pipelineExecution = workflowExecutionService.getWorkflowExecution(appId, pipelineExecutionId);

    notNullCheck("No Executions found for given PipelineExecutionId " + pipelineExecutionId, pipelineExecution);
    String pipelineId = pipelineExecution.getWorkflowId();
    Pipeline pipeline = pipelineService.readPipelineWithVariables(appId, pipelineId);

    notNullCheck("No pipeline associated with given executionId:  " + pipelineExecutionId, pipeline);

    List<PipelineStage> pipelineStages = pipeline.getPipelineStages();
    if (isEmpty(pipelineStages)) {
      throw new InvalidRequestException("Given Pipeline does not contain any Stages", USER);
    }
    for (PipelineStage pipelineStage : pipelineStages) {
      PipelineStageElement pipelineStageElement = pipelineStage.getPipelineStageElements().get(0);
      if (pipelineStageElement.getUuid().equals(pipelineStageElementId)) {
        String workflowId = (String) pipelineStageElement.getProperties().get("workflowId");
        if (isEmpty(workflowId)) {
          throw new InvalidRequestException(
              String.format("No workflow found in pipelineStage: %s for given stageElementId: %s ",
                  pipelineStageElement.getName(), pipelineStageElementId));
        }

        Workflow workflow = workflowService.readWorkflow(appId, workflowId);
        notNullCheck(
            "Not able to load workflow associated with given PipelineStageElementId: " + pipelineStageElementId,
            workflow);
        notNullCheck(
            "Not able to load workflow associated with given PipelineStageElementId: " + pipelineStageElementId,
            workflow.getOrchestrationWorkflow());
        List<Variable> variables = workflow.getOrchestrationWorkflow().getUserVariables();
        if (isEmpty(variables)) {
          return variables;
        }

        RuntimeInputsConfig runtimeInputsConfig = pipelineStageElement.getRuntimeInputsConfig();
        if (runtimeInputsConfig == null || isEmpty(runtimeInputsConfig.getRuntimeInputVariables())) {
          return new ArrayList<>();
        }

        List<Variable> vars = pipeline.getPipelineVariables();
        ExecutionArgs args = pipelineExecution.getExecutionArgs();
        if (args.getWorkflowVariables() == null) {
          args.setWorkflowVariables(new HashMap<>());
        }
        Map<String, String> resolvedVariablesValues = pipelineStageElement.getWorkflowVariables();
        return CanaryOrchestrationWorkflow.reorderUserVariables(
            getRuntimeVariablesForStage(args, runtimeInputsConfig, variables, vars, resolvedVariablesValues));
      }
    }
    throw new InvalidRequestException(
        " No PipelineStage found for given PipelineStageElementId: " + pipelineStageElementId);
  }

  @VisibleForTesting
  public static List<Variable> getRuntimeVariablesForStage(ExecutionArgs args, RuntimeInputsConfig runtimeInputsConfig,
      List<Variable> workflowVariables, List<Variable> pipelineVariables, Map<String, String> resolvedVariablesValues) {
    List<Variable> runtimeVariables =
        workflowVariables.stream()
            .filter(t -> runtimeInputsConfig.getRuntimeInputVariables().contains(t.getName()))
            .collect(toList());

    Set<String> pipelineVarsName = runtimeVariables.stream()
                                       .map(v -> ExpressionEvaluator.getName(resolvedVariablesValues.get(v.getName())))
                                       .collect(Collectors.toSet());

    Set<String> runtimePipelineVarNames = pipelineVariables.stream()
                                              .filter(v -> Boolean.TRUE.equals(v.getRuntimeInput()))
                                              .map(Variable::getName)
                                              .collect(Collectors.toSet());
    Map<String, String> wfVariables = new HashMap<>();
    for (Map.Entry<String, String> entry : args.getWorkflowVariables().entrySet()) {
      if (!runtimePipelineVarNames.contains(entry.getKey())) {
        wfVariables.put(entry.getKey(), entry.getValue());
      }
    }

    for (Variable variable : pipelineVariables) {
      String defaultValuePresent = args.getWorkflowVariables().get(variable.getName());
      if (isNotEmpty(defaultValuePresent) && isNotNewVar(defaultValuePresent)) {
        variable.setValue(defaultValuePresent);
      }
      if (variable.obtainEntityType() != null) {
        PipelineServiceImpl.setParentAndRelatedFieldsForRuntime(
            pipelineVariables, wfVariables, variable.getName(), variable, variable.obtainEntityType());
      }
    }

    return pipelineVariables.stream().filter(v -> pipelineVarsName.contains(v.getName())).collect(toList());
  }

  private static boolean isNotNewVar(String defaultValuePresent) {
    return !(matchesVariablePattern(defaultValuePresent) && !defaultValuePresent.contains("."));
  }

  @NonNull
  public List<InfrastructureMapping> getInfraMappings(Workflow workflow, Map<String, String> workflowVariables) {
    if (workflow == null || workflow.getOrchestrationWorkflow() == null) {
      return new ArrayList<>();
    }

    CanaryOrchestrationWorkflow orchestrationWorkflow =
        (CanaryOrchestrationWorkflow) workflow.getOrchestrationWorkflow();

    return orchestrationWorkflow.getWorkflowPhases()
        .stream()
        .map(phase
            -> infrastructureMappingService.getInfraMappingWithDeploymentType(workflow.getAppId(),
                workflowService.getResolvedServiceIdFromPhase(phase, workflowVariables),
                workflowService.getResolvedInfraDefinitionIdFromPhase(phase, workflowVariables)))
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  public String fetchFailureDetails(String appId, String workflowExecutionId) {
    Map<String, StringJoiner> executionDetails = new LinkedHashMap<>();
    HashSet<String> parentInstances = new HashSet<>();
    StringJoiner failureMessage = new StringJoiner(", ");

    List<StateExecutionInstance> allExecutionInstances = fetchAllFailedExecutionInstances(appId, workflowExecutionId);

    prepareFailedPhases(allExecutionInstances, parentInstances, executionDetails);
    prepareFailedSteps(appId, workflowExecutionId, allExecutionInstances, parentInstances, executionDetails);

    if (isNotEmpty(executionDetails)) {
      executionDetails.forEach((id, message) -> failureMessage.add(message.toString()));
    }

    return failureMessage.toString();
  }

  private void prepareFailedSteps(String appId, String workflowExecutionId,
      List<StateExecutionInstance> allExecutionInstances, HashSet<String> parentInstances,
      Map<String, StringJoiner> executionDetails) {
    for (StateExecutionInstance stateExecutionInstance : allExecutionInstances) {
      String failureDetails = "";
      if (!parentInstances.contains(stateExecutionInstance.getUuid())) {
        String errorMessage = "";
        Map<String, StateExecutionData> stateExecutionMap = stateExecutionInstance.getStateExecutionMap();
        if (stateExecutionMap != null) {
          if (stateExecutionMap.containsKey(stateExecutionInstance.getStateName())) {
            errorMessage = stateExecutionMap.get(stateExecutionInstance.getStateName()).getErrorMsg();
          } else if (stateExecutionMap.containsKey(stateExecutionInstance.getDisplayName())) {
            errorMessage = stateExecutionMap.get(stateExecutionInstance.getDisplayName()).getErrorMsg();
          } else {
            errorMessage = "";
          }
        }
        if (isNotEmpty(errorMessage)) {
          failureDetails = String.format("%s failed - %s ", stateExecutionInstance.getDisplayName(), errorMessage);
        } else {
          failureDetails = String.format("%s failed", stateExecutionInstance.getDisplayName());
        }
      }
      StateExecutionInstance phaseExecution = stateExecutionService.fetchCurrentPhaseStateExecutionInstance(
          appId, workflowExecutionId, stateExecutionInstance.getUuid());
      StateExecutionInstance phaseStepExecution = stateExecutionService.fetchCurrentPhaseStepStateExecutionInstance(
          appId, workflowExecutionId, stateExecutionInstance.getUuid());
      if (phaseExecution != null && isNotEmpty(failureDetails)
          && executionDetails.get(phaseExecution.getUuid()) != null) {
        executionDetails.get(phaseExecution.getUuid()).add(failureDetails);
      } else if (phaseStepExecution != null && isNotEmpty(failureDetails)) {
        executionDetails.put(stateExecutionInstance.getUuid(),
            new StringJoiner("").add(
                String.format("%s failed: [%s]", phaseStepExecution.getDisplayName(), failureDetails)));
      } else if (isNotEmpty(failureDetails)) {
        // Error Handling for Rejected Pipeline
        executionDetails.put(
            generateUuid(), new StringJoiner("").add(String.format("WorkflowExecutionFailed : [%s]", failureDetails)));
      }
    }
  }

  private void prepareFailedPhases(List<StateExecutionInstance> allExecutionInstances, HashSet<String> parentInstances,
      Map<String, StringJoiner> executionDetails) {
    for (StateExecutionInstance stateExecutionInstance : allExecutionInstances) {
      if (stateExecutionInstance.getStateType().equals("PHASE")) {
        executionDetails.put(stateExecutionInstance.getUuid(),
            new StringJoiner(", ", String.format("%s failed: [", stateExecutionInstance.getDisplayName()), "]"));
      }
      parentInstances.add(stateExecutionInstance.getParentInstanceId());
    }
  }

  private List<StateExecutionInstance> fetchAllFailedExecutionInstances(String appId, String workflowExecutionId) {
    Query<StateExecutionInstance> query = wingsPersistence.createQuery(StateExecutionInstance.class)
                                              .filter(StateExecutionInstanceKeys.appId, appId)
                                              .filter(StateExecutionInstanceKeys.executionUuid, workflowExecutionId)
                                              .field(StateExecutionInstanceKeys.status)
                                              .in(ExecutionStatus.resumableStatuses)
                                              .order(Sort.ascending(StateExecutionInstanceKeys.endTs));

    return query.project(StateExecutionInstanceKeys.uuid, true)
        .project(StateExecutionInstanceKeys.stateType, true)
        .project(StateExecutionInstanceKeys.displayName, true)
        .project(StateExecutionInstanceKeys.parentInstanceId, true)
        .project(StateExecutionInstanceKeys.stateName, true)
        .project(StateExecutionInstanceKeys.stateExecutionMap, true)
        .asList();
  }

  private Map<String, List<StateExecutionInstance>> fetchFailedExecutionInstanceMap(
      String appId, List<String> workflowExecutionIds) {
    Query<StateExecutionInstance> query = wingsPersistence.createQuery(StateExecutionInstance.class)
                                              .filter(StateExecutionInstanceKeys.appId, appId)
                                              .field(StateExecutionInstanceKeys.executionUuid)
                                              .in(workflowExecutionIds)
                                              .field(StateExecutionInstanceKeys.status)
                                              .in(ExecutionStatus.resumableStatuses)
                                              .order(Sort.ascending(StateExecutionInstanceKeys.endTs));

    List<StateExecutionInstance> stateExecutionInstances =
        query.project(StateExecutionInstanceKeys.uuid, true)
            .project(StateExecutionInstanceKeys.stateType, true)
            .project(StateExecutionInstanceKeys.displayName, true)
            .project(StateExecutionInstanceKeys.parentInstanceId, true)
            .project(StateExecutionInstanceKeys.stateName, true)
            .project(StateExecutionInstanceKeys.stateExecutionMap, true)
            .project(StateExecutionInstanceKeys.executionUuid, true)
            .asList();

    if (stateExecutionInstances == null) {
      return new HashMap<>();
    }

    return stateExecutionInstances.stream().collect(Collectors.groupingBy(StateExecutionInstance::getExecutionUuid));
  }

  public void populateFailureDetailsWithStepInfo(WorkflowExecution workflowExecution) {
    Map<String, StringJoiner> executionDetails = new LinkedHashMap<>();
    HashSet<String> parentInstances = new HashSet<>();
    StringJoiner failureMessage = new StringJoiner(", ");
    StringJoiner failedStepNames = new StringJoiner(", ");
    StringJoiner failedStepTypes = new StringJoiner(", ");

    List<StateExecutionInstance> allExecutionInstances =
        fetchAllFailedExecutionInstances(workflowExecution.getAppId(), workflowExecution.getUuid());

    prepareFailedPhases(allExecutionInstances, parentInstances, executionDetails);
    prepareFailedSteps(workflowExecution.getAppId(), workflowExecution.getUuid(), allExecutionInstances,
        parentInstances, executionDetails);

    if (isNotEmpty(executionDetails)) {
      executionDetails.forEach((id, message) -> {
        failureMessage.add(message.toString());
        allExecutionInstances.stream().filter(sei -> id.equals(sei.getUuid())).findFirst().ifPresent(sei -> {
          failedStepNames.add(sei.getStateName());
          failedStepTypes.add(sei.getStateType());
        });
      });
    }

    workflowExecution.setFailureDetails(failureMessage.toString());
    workflowExecution.setFailedStepNames(failedStepNames.toString());
    workflowExecution.setFailedStepTypes(failedStepTypes.toString());
  }

  public List<WorkflowExecution> populateFailureDetailsWithStepInfo(
      String appId, List<WorkflowExecution> workflowExecutions) {
    Map<String, List<StateExecutionInstance>> stateExecutionInstanceMap = fetchFailedExecutionInstanceMap(
        appId, workflowExecutions.stream().map(WorkflowExecution::getUuid).collect(toList()));
    List<WorkflowExecution> workflowExecutionsWithFailureDetails = new ArrayList<>();
    stateExecutionInstanceMap.forEach((executionId, stateExecutionInstances) -> {
      Map<String, StringJoiner> executionDetails = new LinkedHashMap<>();
      HashSet<String> parentInstances = new HashSet<>();
      StringJoiner failureMessage = new StringJoiner(", ");
      StringJoiner failedStepNames = new StringJoiner(", ");
      StringJoiner failedStepTypes = new StringJoiner(", ");
      try {
        prepareFailedPhases(stateExecutionInstances, parentInstances, executionDetails);
        prepareFailedSteps(appId, executionId, stateExecutionInstances, parentInstances, executionDetails);

        if (isNotEmpty(executionDetails)) {
          executionDetails.forEach((id, message) -> {
            failureMessage.add(message.toString());
            stateExecutionInstances.stream().filter(sei -> id.equals(sei.getUuid())).findFirst().ifPresent(sei -> {
              failedStepNames.add(sei.getStateName());
              failedStepTypes.add(sei.getStateType());
            });
          });
        }

        WorkflowExecution requiredWorkflowExecution =
            workflowExecutions.stream().filter(we -> executionId.equals(we.getUuid())).findFirst().orElse(null);
        if (requiredWorkflowExecution != null) {
          requiredWorkflowExecution.setFailureDetails(failureMessage.toString());
          requiredWorkflowExecution.setFailedStepNames(failedStepNames.toString());
          requiredWorkflowExecution.setFailedStepTypes(failedStepTypes.toString());
        }
        workflowExecutionsWithFailureDetails.add(requiredWorkflowExecution);
      } catch (Exception e) {
        log.error("Unable to fetch failure details for appId {}, executionId {}", executionId, appId, e);
      }
    });
    return workflowExecutionsWithFailureDetails;
  }

  public static String getCause(WorkflowExecution workflowExecution) {
    if (workflowExecution.getPipelineExecutionId() != null) {
      return ExecutionCause.ExecutedAlongPipeline.name();
    } else {
      CreatedByType createdByType = workflowExecution.getCreatedByType();
      if (CreatedByType.API_KEY == createdByType) {
        return ExecutionCause.ExecutedByAPIKey.name();
      } else if (workflowExecution.getDeploymentTriggerId() != null) {
        return ExecutionCause.ExecutedByTrigger.name();
      } else if (workflowExecution.getTriggeredBy() != null) {
        return ExecutionCause.ExecutedByUser.name();
      }
    }
    return null;
  }
}
