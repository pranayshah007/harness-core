/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.OrchestrationWorkflowType.BUILD;
import static io.harness.beans.OrchestrationWorkflowType.CANARY;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.exception.WingsException.USER;

import static software.wings.beans.CanaryOrchestrationWorkflow.CanaryOrchestrationWorkflowBuilder.aCanaryOrchestrationWorkflow;
import static software.wings.beans.EntityType.APPDYNAMICS_APPID;
import static software.wings.beans.EntityType.APPDYNAMICS_CONFIGID;
import static software.wings.beans.EntityType.APPDYNAMICS_TIERID;
import static software.wings.beans.EntityType.APPLICATION_MANIFEST;
import static software.wings.beans.EntityType.ARTIFACT_STREAM;
import static software.wings.beans.EntityType.CF_AWS_CONFIG_ID;
import static software.wings.beans.EntityType.ELK_CONFIGID;
import static software.wings.beans.EntityType.ELK_INDICES;
import static software.wings.beans.EntityType.ENVIRONMENT;
import static software.wings.beans.EntityType.GCP_CONFIG;
import static software.wings.beans.EntityType.GIT_CONFIG;
import static software.wings.beans.EntityType.HELM_GIT_CONFIG_ID;
import static software.wings.beans.EntityType.INFRASTRUCTURE_DEFINITION;
import static software.wings.beans.EntityType.INFRASTRUCTURE_MAPPING;
import static software.wings.beans.EntityType.JENKINS_SERVER;
import static software.wings.beans.EntityType.NEWRELIC_APPID;
import static software.wings.beans.EntityType.NEWRELIC_CONFIGID;
import static software.wings.beans.EntityType.NEWRELIC_MARKER_APPID;
import static software.wings.beans.EntityType.NEWRELIC_MARKER_CONFIGID;
import static software.wings.beans.EntityType.SERVICE;
import static software.wings.beans.EntityType.SPLUNK_CONFIGID;
import static software.wings.beans.EntityType.SS_SSH_CONNECTION_ATTRIBUTE;
import static software.wings.beans.EntityType.SS_WINRM_CONNECTION_ATTRIBUTE;
import static software.wings.beans.EntityType.SUMOLOGIC_CONFIGID;
import static software.wings.beans.EntityType.USER_GROUP;
import static software.wings.beans.Graph.Builder.aGraph;
import static software.wings.beans.GraphLink.Builder.aLink;
import static software.wings.common.WorkflowConstants.PHASE_NAME_PREFIX;
import static software.wings.common.WorkflowConstants.WORKFLOW_VALIDATION_MESSAGE;
import static software.wings.common.WorkflowConstants.phaseNamePattern;
import static software.wings.service.impl.workflow.WorkflowServiceHelper.ROLLBACK_PREFIX;
import static software.wings.sm.rollback.RollbackStateMachineGenerator.STAGING_PHASE_NAME;
import static software.wings.sm.rollback.RollbackStateMachineGenerator.WHITE_SPACE;

import static java.lang.String.format;
import static java.lang.String.join;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.OrchestrationWorkflowType;
import io.harness.exception.InvalidRequestException;

import software.wings.api.DeploymentType;
import software.wings.beans.Graph.Builder;
import software.wings.beans.concurrency.ConcurrencyStrategy;
import software.wings.sm.TransitionType;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.google.common.base.Joiner;
import dev.morphia.annotations.Transient;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(CDC)
@JsonTypeName("CANARY")
@Slf4j
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class CanaryOrchestrationWorkflow extends CustomOrchestrationWorkflow {
  public CanaryOrchestrationWorkflow() {
    setOrchestrationWorkflowType(CANARY);
  }

  private PhaseStep preDeploymentSteps = new PhaseStep(PhaseStepType.PRE_DEPLOYMENT);

  // This is a nullable field
  private PhaseStep rollbackProvisioners;

  // This is a nullable field
  private PhaseStep rollbackProvisionersReverse;

  @JsonIgnore private List<String> workflowPhaseIds = new ArrayList<>();

  @JsonIgnore private Map<String, WorkflowPhase> workflowPhaseIdMap = new HashMap<>();

  private Map<String, WorkflowPhase> rollbackWorkflowPhaseIdMap = new HashMap<>();

  @Transient private List<WorkflowPhase> workflowPhases = new ArrayList<>();

  private PhaseStep postDeploymentSteps = new PhaseStep(PhaseStepType.POST_DEPLOYMENT);

  private List<NotificationRule> notificationRules = new ArrayList<>();

  private List<FailureStrategy> failureStrategies = new ArrayList<>();

  private List<Variable> systemVariables = new ArrayList<>();

  private List<Variable> userVariables = new ArrayList<>();

  private List<Variable> derivedVariables = new ArrayList<>();

  private Set<EntityType> requiredEntityTypes;

  private static final String WORKFLOW_INFRADEFINITION_VALIDATION_MESSAGE =
      "Some phases %s Infrastructure Definition are found to be invalid/incomplete.";

  @Transient @JsonIgnore private List<String> templateVariables = new ArrayList<>();

  @Override
  public boolean equals(Object o) {
    return super.equals(o) && true;
  }

  public PhaseStep getPreDeploymentSteps() {
    return preDeploymentSteps;
  }

  public void setPreDeploymentSteps(PhaseStep preDeploymentSteps) {
    this.preDeploymentSteps = preDeploymentSteps;
  }

  public PhaseStep getRollbackProvisioners() {
    return rollbackProvisioners;
  }

  public void setRollbackProvisioners(PhaseStep rollbackProvisioners) {
    this.rollbackProvisioners = rollbackProvisioners;
  }

  public PhaseStep getRollbackProvisionersReverse() {
    return rollbackProvisionersReverse;
  }

  public void setRollbackProvisionersReverse(PhaseStep rollbackProvisionersReverse) {
    this.rollbackProvisionersReverse = rollbackProvisionersReverse;
  }

  public List<WorkflowPhase> getWorkflowPhases() {
    return workflowPhases;
  }

  public void setWorkflowPhases(List<WorkflowPhase> workflowPhases) {
    this.workflowPhases = workflowPhases;
  }

  public PhaseStep getPostDeploymentSteps() {
    return postDeploymentSteps;
  }

  public void setPostDeploymentSteps(PhaseStep postDeploymentSteps) {
    this.postDeploymentSteps = postDeploymentSteps;
  }

  @Override
  public List<NotificationRule> getNotificationRules() {
    return notificationRules;
  }

  @Override
  public void setNotificationRules(List<NotificationRule> notificationRules) {
    this.notificationRules = notificationRules;
  }

  public List<FailureStrategy> getFailureStrategies() {
    return failureStrategies;
  }

  public void setFailureStrategies(List<FailureStrategy> failureStrategies) {
    this.failureStrategies = failureStrategies;
  }

  public List<Variable> getSystemVariables() {
    return systemVariables;
  }

  public void setSystemVariables(List<Variable> systemVariables) {
    this.systemVariables = systemVariables;
  }

  @Override
  public List<String> getTemplateVariables() {
    return templateVariables;
  }

  @Override
  public List<Variable> getUserVariables() {
    return userVariables;
  }

  public void setUserVariables(List<Variable> userVariables) {
    this.userVariables = userVariables;
  }

  public List<Variable> getDerivedVariables() {
    return derivedVariables;
  }

  public void setDerivedVariables(List<Variable> derivedVariables) {
    this.derivedVariables = derivedVariables;
  }

  public List<String> getWorkflowPhaseIds() {
    return workflowPhaseIds;
  }

  public void setWorkflowPhaseIds(List<String> workflowPhaseIds) {
    this.workflowPhaseIds = workflowPhaseIds;
  }

  public Map<String, WorkflowPhase> getWorkflowPhaseIdMap() {
    return workflowPhaseIdMap;
  }

  public void setWorkflowPhaseIdMap(Map<String, WorkflowPhase> workflowPhaseIdMap) {
    this.workflowPhaseIdMap = workflowPhaseIdMap;
  }

  public Map<String, WorkflowPhase> getRollbackWorkflowPhaseIdMap() {
    return rollbackWorkflowPhaseIdMap;
  }

  public void setRollbackWorkflowPhaseIdMap(Map<String, WorkflowPhase> rollbackWorkflowPhaseIdMap) {
    this.rollbackWorkflowPhaseIdMap = rollbackWorkflowPhaseIdMap;
  }

  @Override
  public Set<EntityType> getRequiredEntityTypes() {
    return requiredEntityTypes;
  }

  @Override
  public void setRequiredEntityTypes(Set<EntityType> requiredEntityTypes) {
    this.requiredEntityTypes = requiredEntityTypes;
  }

  @Override
  public List<String> getServiceIds() {
    List<WorkflowPhase> phases = new ArrayList<>();
    for (String workflowPhaseId : workflowPhaseIds) {
      WorkflowPhase workflowPhase = workflowPhaseIdMap.get(workflowPhaseId);
      phases.add(workflowPhase);
    }
    return phases.stream()
        .filter(workflowPhase -> workflowPhase.getServiceId() != null)
        .map(WorkflowPhase::getServiceId)
        .distinct()
        .collect(toList());
  }

  @Override
  public List<String> getInfraMappingIds() {
    List<WorkflowPhase> phases = new ArrayList<>();
    for (String workflowPhaseId : workflowPhaseIds) {
      WorkflowPhase workflowPhase = workflowPhaseIdMap.get(workflowPhaseId);
      phases.add(workflowPhase);
    }
    return phases.stream()
        .filter(workflowPhase -> workflowPhase.getInfraMappingId() != null)
        .map(WorkflowPhase::getInfraMappingId)
        .distinct()
        .collect(toList());
  }

  @Override
  public List<String> getInfraDefinitionIds() {
    List<WorkflowPhase> phases = new ArrayList<>();
    for (String workflowPhaseId : workflowPhaseIds) {
      WorkflowPhase workflowPhase = workflowPhaseIdMap.get(workflowPhaseId);
      phases.add(workflowPhase);
    }
    return phases.stream()
        .filter(workflowPhase -> workflowPhase.getInfraDefinitionId() != null)
        .map(WorkflowPhase::getInfraDefinitionId)
        .distinct()
        .collect(toList());
  }

  @Override
  public void setCloneMetadata(Map<String, String> serviceIdMapping) {
    if (workflowPhaseIdMap == null || serviceIdMapping == null) {
      return;
    }
    workflowPhaseIdMap.values().forEach(workflowPhase -> {
      String serviceId = workflowPhase.getServiceId();
      if (serviceId != null) {
        if (serviceIdMapping.containsKey(serviceId)) {
          workflowPhase.setServiceId(serviceIdMapping.get(serviceId));
        }
      }
      workflowPhase.setInfraMappingId(null);
      workflowPhase.setInfraMappingName(null);
    });

    if (rollbackWorkflowPhaseIdMap == null) {
      return;
    }
    rollbackWorkflowPhaseIdMap.values().forEach(rollbackPhase -> {
      String serviceId = rollbackPhase.getServiceId();
      if (serviceId != null) {
        if (serviceIdMapping.containsKey(serviceId)) {
          rollbackPhase.setServiceId(serviceIdMapping.get(serviceId));
        }
      }
      rollbackPhase.setInfraMappingId(null);
      rollbackPhase.setInfraMappingName(null);
    });
  }
  /**
   * Invoked before inserting document in mongo by morphia.
   */
  @Override
  public void onSave() {
    populatePhaseStepIds(preDeploymentSteps);
    if (rollbackProvisioners != null) {
      populatePhaseStepIds(rollbackProvisioners);
    }
    if (rollbackProvisionersReverse != null) {
      populatePhaseStepIds(rollbackProvisionersReverse);
    }
    if (workflowPhases != null) {
      workflowPhaseIds = new ArrayList<>();
      workflowPhaseIdMap = new HashMap<>();

      int i = 0;
      for (WorkflowPhase workflowPhase : workflowPhases) {
        ++i;
        if (isBlank(workflowPhase.getName()) || phaseNamePattern.matcher(workflowPhase.getName()).matches()) {
          workflowPhase.setName(PHASE_NAME_PREFIX + i);
        }
        workflowPhaseIds.add(workflowPhase.getUuid());
        workflowPhaseIdMap.put(workflowPhase.getUuid(), workflowPhase);
        populatePhaseStepIds(workflowPhase);

        WorkflowPhase rollbackPhase = rollbackWorkflowPhaseIdMap.get(workflowPhase.getUuid());
        if (rollbackPhase != null) {
          rollbackPhase.setName(ROLLBACK_PREFIX + workflowPhase.getName());
          rollbackPhase.setPhaseNameForRollback(workflowPhase.getName());
          rollbackPhase.setTemplateExpressions(workflowPhase.getTemplateExpressions());
          populatePhaseStepIds(rollbackPhase);
        }
      }
    }
    populatePhaseStepIds(postDeploymentSteps);
    setGraph(generateGraph());
  }

  /**
   * Invoked after loading document from mongo by morphia.
   */
  @Override
  public void onLoad(Workflow workflow) {
    populatePhaseSteps(preDeploymentSteps, getGraph());
    if (rollbackProvisioners != null) {
      populatePhaseSteps(rollbackProvisioners, getGraph());
    }
    if (rollbackProvisionersReverse != null) {
      populatePhaseSteps(rollbackProvisionersReverse, getGraph());
    }

    // cleanup relatedField, infraId,serviceId,envId from metadata as they should be runtime.
    cleanupMetadata(userVariables);
    // update related field for envId var.
    if (workflow.checkEnvironmentTemplatized()) {
      updateRelatedFieldEnv(workflow);
    }

    workflowPhases = new ArrayList<>();
    for (String workflowPhaseId : workflowPhaseIds) {
      WorkflowPhase workflowPhase = workflowPhaseIdMap.get(workflowPhaseId);
      workflowPhases.add(workflowPhase);
      workflowPhase.getPhaseSteps().forEach(phaseStep -> populatePhaseSteps(phaseStep, getGraph()));

      // update infra Id field service
      try {
        if (workflowPhase.checkServiceTemplatized()) {
          updateMetadataService(workflow, workflowPhase);
        }

        if (workflowPhase.checkInfraDefinitionTemplatized()) {
          updateMetadataInfraDefinition(workflow, workflowPhase);
        }
      } catch (InvalidRequestException e) {
        setValid(false);
        setValidationMessage(e.getMessage());
      }
    }
    if (rollbackWorkflowPhaseIdMap != null) {
      rollbackWorkflowPhaseIdMap.values().forEach(workflowPhase -> {
        workflowPhase.getPhaseSteps().forEach(phaseStep -> { populatePhaseSteps(phaseStep, getGraph()); });
      });
    }
    populatePhaseSteps(postDeploymentSteps, getGraph());
    if (concurrencyStrategy == null && BUILD != getOrchestrationWorkflowType()) {
      setConcurrencyStrategy(ConcurrencyStrategy.builder().build());
    }
    userVariables = reorderUserVariables(userVariables);
  }

  @Override
  public void setTransientFields(Workflow workflow) {
    workflow.setEnvTemplatized(workflow.fetchEnvTemplatizedName() != null);

    for (String workflowPhaseId : workflowPhaseIds) {
      WorkflowPhase workflowPhase = workflowPhaseIdMap.get(workflowPhaseId);
      boolean srvTemplatised = workflowPhase.checkServiceTemplatized();
      boolean infraTemplatised;
      infraTemplatised = workflowPhase.checkInfraDefinitionTemplatized();
      workflowPhase.setSrvTemplatised(srvTemplatised);
      workflowPhase.setInfraTemplatised(infraTemplatised);
      WorkflowPhase rollbackPhase = rollbackWorkflowPhaseIdMap.get(workflowPhaseId);
      if (rollbackPhase != null) {
        rollbackPhase.setSrvTemplatised(srvTemplatised);
        rollbackPhase.setInfraTemplatised(infraTemplatised);
      }
    }
  }

  private void cleanupMetadata(List<Variable> userVariables) {
    for (Variable variable : userVariables) {
      if (variable.getType() == VariableType.ENTITY) {
        Map<String, Object> metadata = variable.getMetadata();
        if (isNotEmpty(metadata) && metadata.get(Variable.ENTITY_TYPE) != null) {
          if (metadata.get(Variable.ENTITY_TYPE).equals(SERVICE.toString())
              || metadata.get(Variable.ENTITY_TYPE).equals(ENVIRONMENT.toString())
              || metadata.get(Variable.ENTITY_TYPE).equals(INFRASTRUCTURE_DEFINITION.toString())) {
            metadata.remove(Variable.RELATED_FIELD);
            metadata.remove(Variable.INFRA_ID);
            metadata.remove(Variable.SERVICE_ID);
            metadata.remove(Variable.ENV_ID);
            metadata.remove(Variable.DEPLOYMENT_TYPE);
          }
        }
      }
    }
  }

  private void updateMetadataService(Workflow workflow, WorkflowPhase workflowPhase) {
    String serviceVarName = workflowPhase.fetchServiceTemplatizedName();
    Variable variable = contains(userVariables, serviceVarName);
    if (variable != null && variable.getMetadata() != null) {
      // add deployment type
      addDeploymentTypeService(workflowPhase, variable);

      // add infra ID if infra is not templatised
      if (!workflowPhase.checkInfraDefinitionTemplatized()) {
        String storedInfraValue = (String) variable.getMetadata().get(Variable.INFRA_ID);
        String currentInfraId = workflowPhase.getInfraDefinitionId();
        if (isEmpty(storedInfraValue)) {
          variable.getMetadata().put(Variable.INFRA_ID, currentInfraId);
        } else if (!Arrays.asList(storedInfraValue.split(",")).contains(currentInfraId)) {
          String joinedInfraVal = join(",", storedInfraValue, currentInfraId);
          variable.getMetadata().put(Variable.INFRA_ID, joinedInfraVal);
        }
      } else {
        // add infra variable names in related field
        String infraVarName = workflowPhase.fetchInfraDefinitionTemplatizedName();
        String storedRelatedField = (String) variable.getMetadata().get(Variable.RELATED_FIELD);
        if (isEmpty(storedRelatedField)) {
          variable.getMetadata().put(Variable.RELATED_FIELD, infraVarName);
        } else if (!Arrays.asList(storedRelatedField.split(",")).contains(infraVarName)) {
          String joinedRelatedField = join(",", storedRelatedField, infraVarName);
          variable.getMetadata().put(Variable.RELATED_FIELD, joinedRelatedField);
        }
      }

      if (isEmpty((String) variable.getMetadata().get(Variable.INFRA_ID))) {
        variable.getMetadata().remove(Variable.INFRA_ID);
      }
    }
  }

  private void updateMetadataInfraDefinition(Workflow workflow, WorkflowPhase workflowPhase) {
    String infraVarName = workflowPhase.fetchInfraDefinitionTemplatizedName();
    Variable variable = contains(userVariables, infraVarName);
    if (variable != null && variable.getMetadata() != null) {
      // add env Id if environment is not templatised
      if (!workflow.checkEnvironmentTemplatized()) {
        variable.getMetadata().put(Variable.ENV_ID, workflow.getEnvId());
      } else {
        variable.getMetadata().remove(Variable.ENV_ID);
      }

      if (!workflowPhase.checkServiceTemplatized()) {
        String storedServiceValue = (String) variable.getMetadata().get(Variable.SERVICE_ID);
        String currentServiceId = workflowPhase.getServiceId();
        if (isEmpty(storedServiceValue)) {
          variable.getMetadata().put(Variable.SERVICE_ID, currentServiceId);
        } else if (!Arrays.asList(storedServiceValue.split(",")).contains(currentServiceId)) {
          String joinedServiceVal = join(",", storedServiceValue, currentServiceId);
          variable.getMetadata().put(Variable.SERVICE_ID, joinedServiceVal);
        }
      } else {
        // add service variable names in related field
        String serviceVarName = workflowPhase.fetchServiceTemplatizedName();
        String storedRelatedField = (String) variable.getMetadata().get(Variable.RELATED_FIELD);
        if (isEmpty(storedRelatedField)) {
          variable.getMetadata().put(Variable.RELATED_FIELD, serviceVarName);
        } else if (!Arrays.asList(storedRelatedField.split(",")).contains(serviceVarName)) {
          String joinedRelatedField = join(",", storedRelatedField, serviceVarName);
          variable.getMetadata().put(Variable.RELATED_FIELD, joinedRelatedField);
        }
      }

      if (isEmpty((String) variable.getMetadata().get(Variable.SERVICE_ID))) {
        variable.getMetadata().remove(Variable.SERVICE_ID);
      }

      // add deployment type
      addDeploymentTypeInfra(workflowPhase, variable);
    }
  }

  private void addDeploymentTypeInfra(WorkflowPhase workflowPhase, Variable variable) {
    if (workflowPhase.getDeploymentType() != null) {
      DeploymentType newDeploymentType = workflowPhase.getDeploymentType();
      if (variable.getMetadata().get(Variable.DEPLOYMENT_TYPE) != null) {
        DeploymentType storedDeploymentType =
            DeploymentType.valueOf(String.valueOf(variable.getMetadata().get(Variable.DEPLOYMENT_TYPE)));
        if (storedDeploymentType != newDeploymentType) {
          throw new InvalidRequestException("Cannot use same variable ${" + variable.getName()
                  + "} for different deployment Types: " + storedDeploymentType.getDisplayName() + ", "
                  + newDeploymentType.getDisplayName(),
              USER);
        }
      } else {
        variable.getMetadata().put(Variable.DEPLOYMENT_TYPE, workflowPhase.getDeploymentType().name());
      }
    }
  }

  private void addDeploymentTypeService(WorkflowPhase workflowPhase, Variable variable) {
    if (workflowPhase.getDeploymentType() != null) {
      String newDeploymentType = workflowPhase.getDeploymentType().name();
      String storedDeploymentType = (String) variable.getMetadata().get(Variable.DEPLOYMENT_TYPE);
      if (isEmpty(storedDeploymentType)) {
        variable.getMetadata().put(Variable.DEPLOYMENT_TYPE, newDeploymentType);
      } else if (!Arrays.asList(storedDeploymentType.split(",")).contains(newDeploymentType)) {
        String joinedDeployementType = join(",", storedDeploymentType, newDeploymentType);
        variable.getMetadata().put(Variable.DEPLOYMENT_TYPE, joinedDeployementType);
      }
    }
  }

  private void updateRelatedFieldEnv(Workflow workflow) {
    String envVarName = workflow.fetchEnvTemplatizedName();
    Variable variable = contains(userVariables, envVarName);
    if (variable != null && variable.getMetadata() != null) {
      variable.getMetadata().put(Variable.RELATED_FIELD, Joiner.on(",").join(fetchInfraDefVariableNames()));
    }
  }

  private List<String> fetchInfraDefVariableNames() {
    return userVariables.stream()
        .filter(t -> t.obtainEntityType() != null && t.obtainEntityType() == INFRASTRUCTURE_DEFINITION)
        .map(Variable::getName)
        .collect(toList());
  }

  /**
   * Re orders the user variables first by Entity - Environment, Service - Service Infra
   */
  public static List<Variable> reorderUserVariables(List<Variable> userVariables) {
    List<Variable> reorderVariables = new ArrayList<>();
    if (userVariables != null) {
      // First get all Entity type user variables
      List<Variable> entityVariables = getEntityVariables(userVariables);
      List<Variable> nonEntityVariables = getNonEntityVariables(userVariables);
      if (entityVariables != null) {
        // Environment, Service and Infra Variables
        addEnvServiceInfraVariables(reorderVariables, entityVariables);

        addVariablesOfType(reorderVariables, entityVariables, CF_AWS_CONFIG_ID);
        addVariablesOfType(reorderVariables, entityVariables, HELM_GIT_CONFIG_ID);
        // AppDynamic state user variables
        addAppDUserVariables(reorderVariables, entityVariables);
        // NewRelic state user variables
        addNewRelicUserVariables(reorderVariables, entityVariables);
        // NewRelic Marker State user variables
        addNewRelicMarkerUserVariables(reorderVariables, entityVariables);
        // Add elk variables
        addElkUserVariables(reorderVariables, entityVariables);

        List<EntityType> entityTypeOrder = Arrays.asList(SUMOLOGIC_CONFIGID, SPLUNK_CONFIGID,
            SS_SSH_CONNECTION_ATTRIBUTE, SS_WINRM_CONNECTION_ATTRIBUTE, USER_GROUP, GCP_CONFIG, GIT_CONFIG,
            JENKINS_SERVER, ARTIFACT_STREAM, APPLICATION_MANIFEST);

        entityTypeOrder.forEach(entityType -> addVariablesOfType(reorderVariables, entityVariables, entityType));
        if (nonEntityVariables != null) {
          reorderVariables.addAll(nonEntityVariables);
        }
      }
    }
    return reorderVariables;
  }

  private static void addVariablesOfType(
      List<Variable> reorderVariables, List<Variable> entityVariables, EntityType entityType) {
    for (Variable variable : entityVariables) {
      if (entityType == variable.obtainEntityType()) {
        reorderVariables.add(variable);
      }
    }
  }

  private static List<Variable> getNonEntityVariables(List<Variable> userVariables) {
    return userVariables.stream().filter(variable -> variable.obtainEntityType() == null).collect(toList());
  }

  private static void addEnvServiceInfraVariables(List<Variable> reorderVariables, List<Variable> entityVariables) {
    for (Variable variable : entityVariables) {
      EntityType entityType = variable.obtainEntityType();
      if (ENVIRONMENT == entityType) {
        reorderVariables.add(variable);
        break;
      }
    }
    for (Variable variable : entityVariables) {
      if (SERVICE == variable.obtainEntityType()) {
        if (reorderVariables.stream().noneMatch(variable1 -> variable1.getName().equals(variable.getName()))) {
          reorderVariables.add(variable);
          addRelatedEntity(entityVariables, reorderVariables, variable, INFRASTRUCTURE_MAPPING);
        }
      }
    }
    addRemainingEntity(reorderVariables, entityVariables, INFRASTRUCTURE_MAPPING);
    addRemainingEntity(reorderVariables, entityVariables, INFRASTRUCTURE_DEFINITION);
  }

  private static void addAppDUserVariables(List<Variable> reorderVariables, List<Variable> entityVariables) {
    for (Variable variable : entityVariables) {
      if (APPDYNAMICS_CONFIGID == variable.obtainEntityType()) {
        reorderVariables.add(variable);
        entityVariables.stream()
            .filter(var
                -> APPDYNAMICS_APPID == var.obtainEntityType() && var.getName().equals(variable.obtainRelatedField()))
            .findFirst()
            .ifPresent((Variable e) -> {
              reorderVariables.add(e);
              entityVariables.stream()
                  .filter(variable1
                      -> APPDYNAMICS_TIERID == variable1.obtainEntityType()
                          && variable1.getName().equals(e.obtainRelatedField()))
                  .findFirst()
                  .ifPresent(reorderVariables::add);
            });
      }
    }
    for (Variable variable : entityVariables) {
      if (APPDYNAMICS_APPID == variable.obtainEntityType()) {
        if (reorderVariables.stream().noneMatch(variable1 -> variable1.getName().equals(variable.getName()))) {
          reorderVariables.add(variable);
          addRelatedEntity(entityVariables, reorderVariables, variable, APPDYNAMICS_TIERID);
        }
      }
    }
    addRemainingEntity(reorderVariables, entityVariables, APPDYNAMICS_TIERID);
  }

  private static void addNewRelicUserVariables(List<Variable> reorderVariables, List<Variable> entityVariables) {
    for (Variable variable : entityVariables) {
      if (NEWRELIC_CONFIGID == variable.obtainEntityType()) {
        reorderVariables.add(variable);
        addRelatedEntity(entityVariables, reorderVariables, variable, NEWRELIC_APPID);
      }
    }
    addRemainingEntity(reorderVariables, entityVariables, NEWRELIC_APPID);
  }

  private static void addNewRelicMarkerUserVariables(List<Variable> reorderVariables, List<Variable> entityVariables) {
    for (Variable variable : entityVariables) {
      if (NEWRELIC_MARKER_CONFIGID == variable.obtainEntityType()) {
        reorderVariables.add(variable);
        addRelatedEntity(entityVariables, reorderVariables, variable, NEWRELIC_MARKER_APPID);
      }
    }
    addRemainingEntity(reorderVariables, entityVariables, NEWRELIC_MARKER_APPID);
  }

  private static void addElkUserVariables(List<Variable> reorderVariables, List<Variable> entityVariables) {
    for (Variable variable : entityVariables) {
      if (ELK_CONFIGID == variable.obtainEntityType()) {
        reorderVariables.add(variable);
        addRelatedEntity(entityVariables, reorderVariables, variable, ELK_INDICES);
      }
    }
    addRemainingEntity(reorderVariables, entityVariables, ELK_INDICES);
  }

  private static void addRemainingEntity(
      List<Variable> reorderVariables, List<Variable> entityVariables, EntityType entityType) {
    for (Variable variable : entityVariables) {
      if (variable.obtainEntityType() != null && variable.obtainEntityType() == entityType) {
        if (reorderVariables.stream().noneMatch(variable1 -> variable1.getName().equals(variable.getName()))) {
          reorderVariables.add(variable);
        }
      }
    }
  }

  private static void addRelatedEntity(
      List<Variable> entityVariables, List<Variable> reorderVariables, Variable variable, EntityType entityType) {
    entityVariables.stream()
        .filter(variable1
            -> variable1.obtainEntityType() != null && variable1.obtainEntityType() == entityType
                && variable1.getName().equals(variable.obtainRelatedField()))
        .findFirst()
        .ifPresent(reorderVariables::add);
  }

  @Override
  public void updateUserVariables() {
    List<Variable> newVariables = new ArrayList<>();
    if (userVariables != null) {
      // First get all Entity type user variables
      List<Variable> entityVariables = getEntityVariables(userVariables);
      List<Variable> nonEntityVariables = getNonEntityVariables(userVariables);
      if (entityVariables != null) {
        for (Variable variable : entityVariables) {
          if (templateVariables.contains(variable.getName())) {
            newVariables.add(variable);
          }
        }
      }
      newVariables.addAll(nonEntityVariables);
    }
    userVariables = newVariables;
  }

  private Variable contains(List<Variable> variables, String name) {
    return variables.stream().filter(variable -> variable.getName().equals(name)).findFirst().orElse(null);
  }

  private static List<Variable> getEntityVariables(List<Variable> userVariables) {
    return userVariables.stream().filter(variable -> variable.obtainEntityType() != null).collect(toList());
  }

  public void populatePhaseStepIds(WorkflowPhase workflowPhase) {
    if (isEmpty(workflowPhase.getPhaseSteps())) {
      return;
    }
    workflowPhase.getPhaseSteps().forEach(this::populatePhaseStepIds);
  }

  public void populatePhaseStepIds(PhaseStep phaseStep) {
    if (phaseStep.getSteps() == null) {
      log.error("Incorrect arguments to populate phaseStepIds: {}", phaseStep);
      return;
    }
    phaseStep.setStepsIds(phaseStep.getSteps().stream().map(GraphNode::getId).collect(toList()));
  }

  private void populatePhaseSteps(PhaseStep phaseStep, Graph graph) {
    if (phaseStep == null || phaseStep.getUuid() == null || graph == null || graph.getSubworkflows() == null
        || graph.getSubworkflows().get(phaseStep.getUuid()) == null) {
      log.error("Incorrect arguments to populate phaseStep: {}, graph: {}", phaseStep, graph);
      return;
    }
    if (isEmpty(phaseStep.getStepsIds())) {
      return;
    }

    Graph subWorkflowGraph = graph.getSubworkflows().get(phaseStep.getUuid());
    if (subWorkflowGraph == null) {
      log.info("No subworkflow found for the phaseStep: {}", phaseStep);
      return;
    }

    Map<String, GraphNode> nodesMap = subWorkflowGraph.getNodesMap();
    phaseStep.setSteps(phaseStep.getStepsIds().stream().map(nodesMap::get).collect(toList()));
  }

  public Graph generateGraph() {
    String id1 = preDeploymentSteps.getUuid();
    String id2;
    GraphNode preDeploymentNode = preDeploymentSteps.generatePhaseStepNode();
    preDeploymentNode.setOrigin(true);
    Builder graphBuilder =
        aGraph().addNodes(preDeploymentNode).addSubworkflow(id1, preDeploymentSteps.generateSubworkflow(null));

    if (rollbackProvisioners != null) {
      GraphNode rollbackProvisionersNode = rollbackProvisioners.generatePhaseStepNode();
      graphBuilder.addNodes(rollbackProvisionersNode)
          .addSubworkflow(rollbackProvisioners.getUuid(), rollbackProvisioners.generateSubworkflow(null));
    }
    if (rollbackProvisionersReverse != null) {
      GraphNode rollbackProvisionersReverseNode = rollbackProvisionersReverse.generatePhaseStepNode();
      graphBuilder.addNodes(rollbackProvisionersReverseNode)
          .addSubworkflow(rollbackProvisionersReverse.getUuid(), rollbackProvisionersReverse.generateSubworkflow(null));
    }

    if (workflowPhases != null) {
      for (WorkflowPhase workflowPhase : workflowPhases) {
        id2 = workflowPhase.getUuid();
        graphBuilder.addNodes(workflowPhase.generatePhaseNode())
            .addLinks(aLink()
                          .withId(generateUuid())
                          .withFrom(id1)
                          .withTo(id2)
                          .withType(TransitionType.SUCCESS.name())
                          .build())
            .addSubworkflows(workflowPhase.generateSubworkflows());

        if (rollbackWorkflowPhaseIdMap != null && rollbackWorkflowPhaseIdMap.get(workflowPhase.getUuid()) != null) {
          GraphNode rollbackNode = rollbackWorkflowPhaseIdMap.get(workflowPhase.getUuid()).generatePhaseNode();
          graphBuilder.addNodes(rollbackNode)
              .addSubworkflows(rollbackWorkflowPhaseIdMap.get(workflowPhase.getUuid()).generateSubworkflows());
        }
        id1 = id2;
      }
    }
    id2 = postDeploymentSteps.getUuid();
    graphBuilder.addNodes(postDeploymentSteps.generatePhaseStepNode())
        .addLinks(
            aLink().withId(generateUuid()).withFrom(id1).withTo(id2).withType(TransitionType.SUCCESS.name()).build())
        .addSubworkflow(id2, postDeploymentSteps.generateSubworkflow(null));

    return graphBuilder.build();
  }

  @Override
  public boolean validate() {
    setValid(true);
    setValidationMessage(null);
    if (workflowPhases != null) {
      validateInternal();
      validateDefinitions();
    }
    return isValid();
  }

  private void validateInternal() {
    String invalid = "";
    List<String> invalidChildren = workflowPhases.stream()
                                       .filter(workflowPhase -> !workflowPhase.validate())
                                       .map(WorkflowPhase::getName)
                                       .collect(toList());
    if (isNotEmpty(invalidChildren)) {
      setValid(false);
      invalid += invalidChildren.toString();
    }

    if (preDeploymentSteps != null && preDeploymentSteps.getSteps() != null) {
      List<String> invalidChildrenPreDeployment = (preDeploymentSteps.getSteps().stream())
                                                      .filter(preDeploymentStep -> !preDeploymentStep.validate())
                                                      .map(GraphNode::getName)
                                                      .collect(toList());
      if (isNotEmpty(invalidChildrenPreDeployment)) {
        setValid(false);
        invalid += invalidChildrenPreDeployment.toString();
      }
    }

    if (postDeploymentSteps != null && postDeploymentSteps.getSteps() != null) {
      List<String> invalidChildrenPostDeployment = (postDeploymentSteps.getSteps().stream())
                                                       .filter(postDeploymentStep -> !postDeploymentStep.validate())
                                                       .map(GraphNode::getName)
                                                       .collect(toList());
      if (isNotEmpty(invalidChildrenPostDeployment)) {
        setValid(false);
        invalid += invalidChildrenPostDeployment.toString();
      }
    }

    if (rollbackWorkflowPhaseIdMap != null) {
      List<String> invalidRollbacks = new ArrayList<>();
      for (WorkflowPhase workflowPhase : workflowPhases) {
        WorkflowPhase rollbackPhase = rollbackWorkflowPhaseIdMap.get(workflowPhase.getUuid());
        if (rollbackPhase != null) {
          if (!rollbackPhase.validate()) {
            invalidRollbacks.add(rollbackPhase.getName());
          }
        }
      }
      if (!invalidRollbacks.isEmpty()) {
        setValid(false);
        if (!invalid.isEmpty()) {
          invalid += ", ";
        }
        invalid += invalidRollbacks.toString();
      }
    }
    if (!invalid.isEmpty()) {
      setValidationMessage(format(WORKFLOW_VALIDATION_MESSAGE, invalid));
    }
  }

  private void validateDefinitions() {
    if (workflowPhases != null) {
      List<String> invalidInfraPhaseIds = new ArrayList<>();
      for (WorkflowPhase phase : workflowPhases) {
        if (phase == null || phase.checkInfraDefinitionTemplatized()) {
          continue;
        }
        if (isEmpty(phase.getInfraDefinitionId())) {
          invalidInfraPhaseIds.add(phase.getName());
        }
      }
      if (isNotEmpty(invalidInfraPhaseIds)) {
        setValid(false);
        setValidationMessage(format(WORKFLOW_INFRADEFINITION_VALIDATION_MESSAGE, invalidInfraPhaseIds.toString()));
      }
    }
  }

  public boolean serviceRepeat(WorkflowPhase workflowPhase) {
    if (workflowPhaseIds == null) {
      return false;
    }
    for (String phaseId : workflowPhaseIds) {
      WorkflowPhase existingPhase = workflowPhaseIdMap.get(phaseId);
      if (isNotEmpty(existingPhase.getServiceId()) && existingPhase.getServiceId().equals(workflowPhase.getServiceId())
          && existingPhase.getDeploymentType() == workflowPhase.getDeploymentType()
          && existingPhase.getInfraDefinitionId() != null
          && existingPhase.getInfraDefinitionId().equals(workflowPhase.getInfraDefinitionId())) {
        return true;
      }
    }
    return false;
  }

  public boolean containsPhaseWithName(String phaseName) {
    if (workflowPhaseIds == null) {
      return false;
    }
    for (String phaseId : workflowPhaseIds) {
      WorkflowPhase existingPhase = workflowPhaseIdMap.get(phaseId);
      if (StringUtils.equals(phaseName, existingPhase.getName())) {
        return true;
      }
    }
    return false;
  }

  @Override
  public OrchestrationWorkflow cloneInternal() {
    return aCanaryOrchestrationWorkflow()
        .withGraph(getGraph())
        .withPreDeploymentSteps(getPreDeploymentSteps())
        .withRollbackProvisioners(getRollbackProvisioners())
        .withRollbackProvisionersReverse(getRollbackProvisionersReverse())
        .withWorkflowPhases(getWorkflowPhases())
        .withWorkflowPhaseIds(getWorkflowPhaseIds())
        .withWorkflowPhaseIdMap(getWorkflowPhaseIdMap())
        .withPostDeploymentSteps(getPostDeploymentSteps())
        .withRollbackWorkflowPhaseIdMap(getRollbackWorkflowPhaseIdMap())
        .withNotificationRules(getNotificationRules())
        .withFailureStrategies(getFailureStrategies())
        .withSystemVariables(getSystemVariables())
        .withUserVariables(getUserVariables())
        .withDerivedVariables(getDerivedVariables())
        .withRequiredEntityTypes(getRequiredEntityTypes())
        .withOrchestrationWorkflowType(getOrchestrationWorkflowType())
        .withConcurrencyStrategy(getConcurrencyStrategy())
        .build();
  }

  @Override
  public List<String> getTemplatizedServiceIds() {
    List<String> templatizedServiceIds = new ArrayList<>();
    if (workflowPhases == null) {
      return templatizedServiceIds;
    }
    for (WorkflowPhase workflowPhase : workflowPhases) {
      List<TemplateExpression> templateExpressions = workflowPhase.getTemplateExpressions();
      if (templateExpressions != null) {
        if (templateExpressions.stream().anyMatch(
                templateExpression -> templateExpression.getFieldName().equals("serviceId"))) {
          if (workflowPhase.getServiceId() != null) {
            templatizedServiceIds.add(workflowPhase.getServiceId());
          }
        }
      }
    }
    return templatizedServiceIds.stream().distinct().collect(toList());
  }

  @Override
  public List<String> getTemplatizedInfraMappingIds() {
    List<String> templatizedInfraMappingIds = new ArrayList<>();
    if (workflowPhases == null) {
      return templatizedInfraMappingIds;
    }
    for (WorkflowPhase workflowPhase : workflowPhases) {
      List<TemplateExpression> templateExpressions = workflowPhase.getTemplateExpressions();
      if (templateExpressions != null) {
        if (templateExpressions.stream().anyMatch(
                templateExpression -> templateExpression.getFieldName().equals("infraMappingId"))) {
          if (workflowPhase.getInfraMappingId() != null) {
            templatizedInfraMappingIds.add(workflowPhase.getInfraMappingId());
          }
        }
      }
    }
    return templatizedInfraMappingIds.stream().distinct().collect(toList());
  }

  @Override
  public List<String> getTemplatizedInfraDefinitionIds() {
    List<String> templatizedInfraDefinitionIds = new ArrayList<>();
    if (workflowPhases == null) {
      return templatizedInfraDefinitionIds;
    }
    for (WorkflowPhase workflowPhase : workflowPhases) {
      List<TemplateExpression> templateExpressions = workflowPhase.getTemplateExpressions();
      if (templateExpressions != null) {
        if (templateExpressions.stream().anyMatch(
                templateExpression -> templateExpression.getFieldName().equals("infraDefinitionId"))) {
          if (workflowPhase.getInfraDefinitionId() != null) {
            templatizedInfraDefinitionIds.add(workflowPhase.getInfraDefinitionId());
          }
        }
      }
    }
    return templatizedInfraDefinitionIds.stream().distinct().collect(toList());
  }

  /**
   * Checks if service templatized or not
   * @return
   */
  @Override
  public boolean isServiceTemplatized() {
    if (workflowPhases != null) {
      for (WorkflowPhase workflowPhase : workflowPhases) {
        if (workflowPhase.checkServiceTemplatized()) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Checks if Inframapping templatized or not
   * @return
   */
  @Override
  public boolean isInfraMappingTemplatized() {
    if (workflowPhases != null) {
      for (WorkflowPhase workflowPhase : workflowPhases) {
        List<TemplateExpression> templateExpressions = workflowPhase.getTemplateExpressions();
        if (templateExpressions != null) {
          if (templateExpressions.stream().anyMatch(
                  templateExpression -> templateExpression.getFieldName().equals("infraMappingId"))) {
            return true;
          }
        }
      }
    }
    return false;
  }

  /**
   * Checks if InfraDefinition templatized or not
   * @return
   */
  @Override
  public boolean isInfraDefinitionTemplatized() {
    if (workflowPhases != null) {
      for (WorkflowPhase workflowPhase : workflowPhases) {
        List<TemplateExpression> templateExpressions = workflowPhase.getTemplateExpressions();
        if (templateExpressions != null) {
          if (templateExpressions.stream().anyMatch(
                  templateExpression -> templateExpression.getFieldName().equals("infraDefinitionId"))) {
            return true;
          }
        }
      }
    }
    return false;
  }

  public boolean checkLastPhaseForOnDemandRollback(@NotNull String phaseName) {
    if (isNotEmpty(workflowPhases)) {
      return phaseName.equals(
          STAGING_PHASE_NAME + WHITE_SPACE + workflowPhases.get(workflowPhases.size() - 1).getName());
    }
    return false;
  }

  public static final class CanaryOrchestrationWorkflowBuilder {
    private Graph graph;
    private PhaseStep preDeploymentSteps = new PhaseStep(PhaseStepType.PRE_DEPLOYMENT);
    private List<String> workflowPhaseIds = new ArrayList<>();
    private Map<String, WorkflowPhase> workflowPhaseIdMap = new HashMap<>();
    private Map<String, WorkflowPhase> rollbackWorkflowPhaseIdMap = new HashMap<>();
    private List<WorkflowPhase> workflowPhases = new ArrayList<>();
    private PhaseStep postDeploymentSteps = new PhaseStep(PhaseStepType.POST_DEPLOYMENT);
    private List<NotificationRule> notificationRules = new ArrayList<>();
    private ConcurrencyStrategy concurrencyStrategy;
    private List<FailureStrategy> failureStrategies = new ArrayList<>();
    private List<Variable> systemVariables = new ArrayList<>();
    private List<Variable> userVariables = new ArrayList<>();
    private List<Variable> derivedVariables = new ArrayList<>();
    private Set<EntityType> requiredEntityTypes;
    private OrchestrationWorkflowType orchestrationWorkflowType = CANARY;
    private PhaseStep rollbackProvisioners;
    private PhaseStep rollbackProvisionersReverse;

    private CanaryOrchestrationWorkflowBuilder() {}

    public static CanaryOrchestrationWorkflowBuilder aCanaryOrchestrationWorkflow() {
      return new CanaryOrchestrationWorkflowBuilder();
    }

    public CanaryOrchestrationWorkflowBuilder withGraph(Graph graph) {
      this.graph = graph;
      return this;
    }

    public CanaryOrchestrationWorkflowBuilder withPreDeploymentSteps(PhaseStep preDeploymentSteps) {
      this.preDeploymentSteps = preDeploymentSteps;
      return this;
    }

    public CanaryOrchestrationWorkflowBuilder withRollbackProvisioners(PhaseStep rollbackProvisioners) {
      this.rollbackProvisioners = rollbackProvisioners;
      return this;
    }

    public CanaryOrchestrationWorkflowBuilder withRollbackProvisionersReverse(PhaseStep rollbackProvisionersReverse) {
      this.rollbackProvisionersReverse = rollbackProvisionersReverse;
      return this;
    }

    public CanaryOrchestrationWorkflowBuilder withWorkflowPhaseIds(List<String> workflowPhaseIds) {
      this.workflowPhaseIds = workflowPhaseIds;
      return this;
    }

    public CanaryOrchestrationWorkflowBuilder withWorkflowPhases(List<WorkflowPhase> workflowPhases) {
      this.workflowPhases = workflowPhases;
      return this;
    }

    public CanaryOrchestrationWorkflowBuilder withWorkflowPhaseIdMap(Map<String, WorkflowPhase> workflowPhaseIdMap) {
      this.workflowPhaseIdMap = workflowPhaseIdMap;
      return this;
    }

    public CanaryOrchestrationWorkflowBuilder withRollbackWorkflowPhaseIdMap(
        Map<String, WorkflowPhase> rollbackWorkflowPhaseIdMap) {
      this.rollbackWorkflowPhaseIdMap = rollbackWorkflowPhaseIdMap;
      return this;
    }

    public CanaryOrchestrationWorkflowBuilder addWorkflowPhase(WorkflowPhase workflowPhase) {
      this.workflowPhases.add(workflowPhase);
      return this;
    }

    public CanaryOrchestrationWorkflowBuilder withPostDeploymentSteps(PhaseStep postDeploymentSteps) {
      this.postDeploymentSteps = postDeploymentSteps;
      return this;
    }

    public CanaryOrchestrationWorkflowBuilder withNotificationRules(List<NotificationRule> notificationRules) {
      this.notificationRules = notificationRules;
      return this;
    }

    public CanaryOrchestrationWorkflowBuilder withConcurrencyStrategy(ConcurrencyStrategy concurrencyStrategy) {
      this.concurrencyStrategy = concurrencyStrategy;
      return this;
    }

    public CanaryOrchestrationWorkflowBuilder withFailureStrategies(List<FailureStrategy> failureStrategies) {
      this.failureStrategies = failureStrategies;
      return this;
    }

    public CanaryOrchestrationWorkflowBuilder withSystemVariables(List<Variable> systemVariables) {
      this.systemVariables = systemVariables;
      return this;
    }

    public CanaryOrchestrationWorkflowBuilder withUserVariables(List<Variable> userVariables) {
      this.userVariables = userVariables;
      return this;
    }

    public CanaryOrchestrationWorkflowBuilder withDerivedVariables(List<Variable> derivedVariables) {
      this.derivedVariables = derivedVariables;
      return this;
    }

    public CanaryOrchestrationWorkflowBuilder withRequiredEntityTypes(Set<EntityType> requiredEntityTypes) {
      this.requiredEntityTypes = requiredEntityTypes;
      return this;
    }

    public CanaryOrchestrationWorkflowBuilder withOrchestrationWorkflowType(
        OrchestrationWorkflowType orchestrationWorkflowType) {
      this.orchestrationWorkflowType = orchestrationWorkflowType;
      return this;
    }

    public CanaryOrchestrationWorkflow build() {
      CanaryOrchestrationWorkflow canaryOrchestrationWorkflow = new CanaryOrchestrationWorkflow();
      canaryOrchestrationWorkflow.setGraph(graph);
      canaryOrchestrationWorkflow.setPreDeploymentSteps(preDeploymentSteps);
      canaryOrchestrationWorkflow.setWorkflowPhaseIds(workflowPhaseIds);
      canaryOrchestrationWorkflow.setWorkflowPhaseIdMap(workflowPhaseIdMap);
      canaryOrchestrationWorkflow.setRollbackWorkflowPhaseIdMap(rollbackWorkflowPhaseIdMap);
      canaryOrchestrationWorkflow.setWorkflowPhases(workflowPhases);
      canaryOrchestrationWorkflow.setPostDeploymentSteps(postDeploymentSteps);
      canaryOrchestrationWorkflow.setNotificationRules(notificationRules);
      canaryOrchestrationWorkflow.setFailureStrategies(failureStrategies);
      canaryOrchestrationWorkflow.setSystemVariables(systemVariables);
      canaryOrchestrationWorkflow.setUserVariables(userVariables);
      canaryOrchestrationWorkflow.setDerivedVariables(derivedVariables);
      canaryOrchestrationWorkflow.setRequiredEntityTypes(requiredEntityTypes);
      canaryOrchestrationWorkflow.setOrchestrationWorkflowType(orchestrationWorkflowType);
      canaryOrchestrationWorkflow.setConcurrencyStrategy(concurrencyStrategy);
      canaryOrchestrationWorkflow.setRollbackProvisioners(rollbackProvisioners);
      canaryOrchestrationWorkflow.setRollbackProvisionersReverse(rollbackProvisionersReverse);
      return canaryOrchestrationWorkflow;
    }
  }
}
