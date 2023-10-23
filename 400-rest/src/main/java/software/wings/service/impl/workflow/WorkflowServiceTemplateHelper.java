/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.workflow;

import static io.harness.annotations.dev.HarnessModule._870_CG_ORCHESTRATION;
import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.OrchestrationWorkflowType.BASIC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.govern.Switch.unhandled;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.EntityType.ENVIRONMENT;
import static software.wings.beans.EntityType.INFRASTRUCTURE_DEFINITION;
import static software.wings.beans.EntityType.INFRASTRUCTURE_MAPPING;
import static software.wings.beans.EntityType.SERVICE;
import static software.wings.sm.StateType.APPROVAL;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.OrchestrationWorkflowType;
import io.harness.exception.InvalidRequestException;

import software.wings.api.DeploymentType;
import software.wings.beans.CanaryOrchestrationWorkflow;
import software.wings.beans.EntityType;
import software.wings.beans.GraphNode;
import software.wings.beans.OrchestrationWorkflow;
import software.wings.beans.PhaseStep;
import software.wings.beans.Service;
import software.wings.beans.TemplateExpression;
import software.wings.beans.Variable;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowPhase;
import software.wings.beans.template.Template;
import software.wings.beans.template.TemplateHelper;
import software.wings.beans.workflow.StepSkipStrategy;
import software.wings.beans.workflow.StepSkipStrategy.Scope;
import software.wings.expression.ManagerExpressionEvaluator;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.template.TemplateService;
import software.wings.sm.StateType;
import software.wings.sm.StepType;
import software.wings.sm.states.HelmDeployState.HelmDeployStateKeys;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

@OwnedBy(CDC)
@TargetModule(_870_CG_ORCHESTRATION)
@Singleton
public class WorkflowServiceTemplateHelper {
  private static final String ENV_VAR_DESC = "Variable for Environment entity";
  private static final String SERVICE_VAR_DESC = "Variable for Service entity";
  private static final String SERVICE_INFRA_VAR_DESC = "Variable for Service Infra-structure entity";
  private static final String INFRADEF_VAR_DESC = "Variable for Infrastructure Definition entity";
  private static final String APPD_SERVER_VAR_DESC = "Variable for AppDynamics Server entity";
  private static final String APPD_APP_VAR_DESC = "Variable for AppDynamics Application entity";
  private static final String APPD_TIER_VAR_DESC = "Variable for AppDynamics Tier entity";
  private static final String ELK_SERVER_VAR_DESC = "Variable for Elastic Search Server entity";
  private static final String ELK_INDICES_VAR_DESC = "Variable for Elastic Search Indices entity";
  private static final String CF_AWSCONFIG_VAR_DESC = "Variable for AWS Config entity";
  private static final String HELM_GITCONFIG_VAR_DESC = "Variable for Helm Git Config entity";
  private static final String SSH_CONNECTION_ATTRIBUTE_DESC = "Variable for SSH Connection Attribute entity";
  private static final String USER_GROUP_DESC = "Variable for User Group entity";
  private static final String WINRM_CONNECTION_ATTRIBUTE_DESC = "Variable for WINRM Connection Attribute entity";
  private static final String GCP_CONFIG_VAR_DESC = "Variable for Google Cloud Platform configuration entity";
  private static final String GIT_CONFIG_VAR_DESC = "Variable for Git ConnectorDisconnectHandler configuration entity";
  private static final String JENKINS_SERVER_VAR_DESC = "Variable for Jenkins server configuration entity";
  private static final String TIMEOUT_PROPERTY_KEY = "timeoutMillis";
  private static final String ARTIFACT_SOURCE_VAR_DESC = "Variable for Artifact source configuration entity";
  protected static final Integer MAXIMUM_TIMEOUT = 2147400049;

  @Inject private TemplateService templateService;
  @Inject private TemplateHelper templateHelper;
  @Inject private ServiceResourceService serviceResourceService;

  public void validatePhaseStepsProperties(PhaseStep phaseStep) {
    phaseStep.getSteps().forEach(step -> {
      if (step.getType().equals(APPROVAL.name())) {
        Object timeoutMillis = step.getProperties().get(TIMEOUT_PROPERTY_KEY);
        if (timeoutMillis != null) {
          try {
            Integer timeoutMillisNumber = Integer.valueOf(timeoutMillis.toString());
            if (timeoutMillisNumber > MAXIMUM_TIMEOUT) {
              throw new InvalidRequestException("Value exceeded maximum timeout of 3w 3d 20h 30m.");
            } else if (timeoutMillisNumber < 0) {
              throw new InvalidRequestException("Timeout value should be positive");
            }
          } catch (NumberFormatException ex) {
            throw new InvalidRequestException(
                "Invalid value for timeoutMillis or value exceeded maximum timeout of 3w 3d 20h 30m.");
          }
        }
      }
    });
  }

  public void validateWorkflowStepsProperties(Workflow newWorkflow) {
    OrchestrationWorkflow orchestrationWorkflow = newWorkflow.getOrchestrationWorkflow();
    if (orchestrationWorkflow instanceof CanaryOrchestrationWorkflow) {
      CanaryOrchestrationWorkflow canaryOrchestrationWorkflow =
          (CanaryOrchestrationWorkflow) newWorkflow.getOrchestrationWorkflow();
      canaryOrchestrationWorkflow.getWorkflowPhases().forEach(
          workflowPhase -> workflowPhase.getPhaseSteps().forEach(this::validatePhaseStepsProperties));
    }
  }

  public void updateLinkedPhaseStepTemplate(PhaseStep phaseStep, PhaseStep oldPhaseStep) {
    updateLinkedPhaseStepTemplate(phaseStep, oldPhaseStep, false);
  }

  public void populatePropertiesFromWorkflow(Workflow workflow) {
    if (!(workflow.getOrchestrationWorkflow() instanceof CanaryOrchestrationWorkflow)) {
      return;
    }
    // load Linked Pre-deployment steps
    CanaryOrchestrationWorkflow canaryOrchestrationWorkflow =
        (CanaryOrchestrationWorkflow) workflow.getOrchestrationWorkflow();
    populatePropertiesFromWorkflow(canaryOrchestrationWorkflow.getPreDeploymentSteps());

    // load Linked Post-deployment steps
    populatePropertiesFromWorkflow(canaryOrchestrationWorkflow.getPostDeploymentSteps());

    // Update Workflow Phase steps
    if (isNotEmpty(canaryOrchestrationWorkflow.getWorkflowPhases())) {
      for (WorkflowPhase workflowPhase : canaryOrchestrationWorkflow.getWorkflowPhases()) {
        List<PhaseStep> phaseSteps = workflowPhase.getPhaseSteps();
        if (isNotEmpty(phaseSteps)) {
          for (PhaseStep phaseStep : phaseSteps) {
            populatePropertiesFromWorkflow(phaseStep);
          }
        }
      }
    }

    // Update Rollback Phase Steps
    if (canaryOrchestrationWorkflow.getRollbackWorkflowPhaseIdMap() != null) {
      canaryOrchestrationWorkflow.getRollbackWorkflowPhaseIdMap().values().forEach(workflowPhase -> {
        if (isNotEmpty(workflowPhase.getPhaseSteps())) {
          for (PhaseStep phaseStep : workflowPhase.getPhaseSteps()) {
            populatePropertiesFromWorkflow(phaseStep);
          }
        }
      });
    }
  }

  private void populatePropertiesFromWorkflow(PhaseStep phaseStep) {
    if (phaseStep != null && phaseStep.getSteps() != null) {
      for (GraphNode step : phaseStep.getSteps()) {
        if (step.getTemplateUuid() != null) {
          Template template = templateService.get(step.getTemplateUuid(), step.getTemplateVersion());
          notNullCheck("Template does not exist", template, USER);
          GraphNode templateStep =
              (GraphNode) templateService.constructEntityFromTemplate(template, EntityType.WORKFLOW);
          List<String> templateProperties = templateService.fetchTemplateProperties(template);
          if (templateProperties != null) {
            if (!"COMMAND".equals(step.getType())) {
              if ((StateType.SHELL_SCRIPT.name().equals(step.getType()) || StateType.HTTP.name().equals(step.getType()))
                  && step.getProperties().containsKey(TIMEOUT_PROPERTY_KEY)
                  && (!(step.getProperties().get(TIMEOUT_PROPERTY_KEY).equals(Integer.valueOf(0))))) {
                templateProperties.remove(TIMEOUT_PROPERTY_KEY);
                templateStep.getProperties().remove(TIMEOUT_PROPERTY_KEY);
              }
              step.getProperties().keySet().removeAll(templateProperties);
              step.getProperties().putAll(templateStep.getProperties());
            }
          }
        }
      }
    }
  }

  public void updateLinkedPhaseStepTemplate(PhaseStep phaseStep, PhaseStep oldPhaseStep, boolean fromYaml) {
    validatePhaseStepsProperties(phaseStep);
    if (phaseStep != null) {
      StepSkipStrategy.validateStepSkipStrategies(phaseStep.getStepSkipStrategies());
    }
    if (phaseStep != null && phaseStep.getSteps() != null) {
      compareOldNewPropertiesAndUpdateStepIds(oldPhaseStep, phaseStep, fromYaml);
      phaseStep.getSteps().stream().filter(step -> step.getTemplateUuid() != null).forEach((GraphNode step) -> {
        Template template = templateService.get(step.getTemplateUuid(), step.getTemplateVersion());
        notNullCheck("Template does not exist", template, USER);
        GraphNode templateStep = (GraphNode) templateService.constructEntityFromTemplate(template, EntityType.WORKFLOW);
        step.setTemplateVariables(
            templateHelper.overrideVariables(templateStep.getTemplateVariables(), step.getTemplateVariables()));
      });
    }
  }

  private void compareOldNewPropertiesAndUpdateStepIds(
      PhaseStep oldPhaseStep, PhaseStep newPhaseStep, boolean fromYaml) {
    if (oldPhaseStep != null && oldPhaseStep.getSteps() != null) {
      newPhaseStep.setUuid(oldPhaseStep.getUuid());
      final Function<GraphNode, String> keyDecider = gn -> fromYaml ? gn.getName() : gn.getId();
      final Map<String, GraphNode> oldGraphNodeMap = oldPhaseStep.getSteps().stream().collect(
          Collectors.toMap(keyDecider, Function.identity(), (key1, key2) -> null));
      Map<String, String> oldStepIdToNewStepIdMap = new HashMap<>();
      newPhaseStep.getSteps().forEach(step -> {
        GraphNode oldStep = oldGraphNodeMap.get(keyDecider.apply(step));
        if (oldStep != null) {
          // On workflow update, step id should be same as old step.
          if (!step.getId().equals(oldStep.getId())) {
            oldStepIdToNewStepIdMap.put(step.getId(), oldStep.getId());
            step.setId(oldStep.getId());
          }
          checkStepProperties(oldStep, step);
        }
      });
      updateStepSkipAssertionsWithNewStepIds(newPhaseStep, oldStepIdToNewStepIdMap);
    }
  }

  private void updateStepSkipAssertionsWithNewStepIds(
      PhaseStep newPhaseStep, Map<String, String> oldStepIdToNewStepIdMap) {
    List<StepSkipStrategy> stepSkipStrategies = newPhaseStep.getStepSkipStrategies();
    if (isNotEmpty(stepSkipStrategies)) {
      for (StepSkipStrategy stepSkipStrategy : stepSkipStrategies) {
        List<String> stepIds = null;
        if (stepSkipStrategy.getScope() == Scope.SPECIFIC_STEPS && isNotEmpty(stepSkipStrategy.getStepIds())) {
          stepIds = new ArrayList<>();
          for (String stepId : stepSkipStrategy.getStepIds()) {
            String newStepId = oldStepIdToNewStepIdMap.get(stepId);
            if (isNotEmpty(newStepId)) {
              stepIds.add(newStepId);
            } else {
              stepIds.add(stepId);
            }
          }
        }
        stepSkipStrategy.setStepIds(stepIds);
      }
    }
  }

  private void checkStepProperties(GraphNode oldNode, GraphNode newNode) {
    if (StepType.HELM_DEPLOY.toString().equals(oldNode.getType()) && oldNode.getType().equals(newNode.getType())) {
      String oldValue = (String) oldNode.getProperties().get(HelmDeployStateKeys.helmReleaseNamePrefix);
      if (isNotEmpty(oldValue)) {
        newNode.getProperties().put(HelmDeployStateKeys.helmReleaseNamePrefix, oldValue);
      }
    }
  }

  public void updateLinkedWorkflowPhases(
      List<WorkflowPhase> workflowPhases, List<WorkflowPhase> existingWorkflowPhases, boolean fromYaml) {
    for (WorkflowPhase workflowPhase : workflowPhases) {
      WorkflowPhase oldWorkflowPhase = existingWorkflowPhases == null
          ? null
          : existingWorkflowPhases.stream()
                .filter(existingWorkflowPhase -> workflowPhase.getUuid().equals(existingWorkflowPhase.getUuid()))
                .findFirst()
                .orElse(null);
      updateLinkedWorkflowPhaseTemplate(workflowPhase, oldWorkflowPhase, fromYaml);
    }
  }

  public void updateLinkedWorkflowPhaseTemplate(WorkflowPhase workflowPhase, WorkflowPhase oldWorkflowPhase) {
    updateLinkedWorkflowPhaseTemplate(workflowPhase, oldWorkflowPhase, false);
  }

  public void updateLinkedWorkflowPhaseTemplate(
      WorkflowPhase workflowPhase, WorkflowPhase oldWorkflowPhase, boolean fromYaml) {
    if (oldWorkflowPhase == null) {
      return;
    }
    if (workflowPhase != null) {
      if (workflowPhase.getPhaseSteps() != null) {
        workflowPhase.getPhaseSteps()
            .stream()
            .filter(phaseStep -> oldWorkflowPhase.getPhaseSteps() != null)
            .forEach((PhaseStep phaseStep) -> {
              PhaseStep oldPhaseStep =
                  oldWorkflowPhase.getPhaseSteps()
                      .stream()
                      .filter(fromYaml ? phaseStep1
                          -> phaseStep.getPhaseStepType() != null
                              && phaseStep.getPhaseStepType() == phaseStep1.getPhaseStepType()
                              && phaseStep.getName() != null && phaseStep.getName().equals(phaseStep1.getName())
                                       : phaseStep1
                          -> phaseStep.getUuid() != null && phaseStep.getUuid().equals(phaseStep1.getUuid()))
                      .findFirst()
                      .orElse(null);
              if (oldPhaseStep != null) {
                // On workflow update, phase step id should not change.
                phaseStep.setUuid(oldPhaseStep.getUuid());
              }
              updateLinkedPhaseStepTemplate(phaseStep, oldPhaseStep, fromYaml);
            });
      }
    }
  }

  public void addLinkedWorkflowPhaseTemplate(WorkflowPhase workflowPhase) {
    if (workflowPhase != null) {
      if (workflowPhase.getPhaseSteps() != null) {
        workflowPhase.getPhaseSteps().forEach((PhaseStep phaseStep) -> updateLinkedPhaseStepTemplate(phaseStep, null));
      }
    }
  }

  /**
   * Set template expressions to phase from workflow level
   *
   * @param templateExpressions
   * @param workflowPhase
   */
  public static void setTemplateExpresssionsToPhase(
      List<TemplateExpression> templateExpressions, WorkflowPhase workflowPhase) {
    if (workflowPhase == null) {
      return;
    }

    List<TemplateExpression> phaseTemplateExpressions = new ArrayList<>();
    addTemplateExpressions(templateExpressions, phaseTemplateExpressions);
    workflowPhase.setTemplateExpressions(phaseTemplateExpressions);
  }

  private static void addTemplateExpressions(
      List<TemplateExpression> sourceTemplateExpressions, List<TemplateExpression> targetTemplateExpressions) {
    TemplateExpression serviceExpression = getTemplateExpression(sourceTemplateExpressions, "serviceId");
    TemplateExpression infraMappingExpression = getTemplateExpression(sourceTemplateExpressions, "infraMappingId");
    TemplateExpression infraDefinitionExpression =
        getTemplateExpression(sourceTemplateExpressions, "infraDefinitionId");
    if (serviceExpression != null) {
      targetTemplateExpressions.add(serviceExpression);
    }
    if (infraMappingExpression != null) {
      targetTemplateExpressions.add(infraMappingExpression);
    }
    if (infraDefinitionExpression != null) {
      targetTemplateExpressions.add(infraDefinitionExpression);
    }
  }

  /**
   * Set template expressions to phase from workflow level
   *
   * @param workflow
   * @param workflowPhase
   */
  public static void setTemplateExpresssionsFromPhase(Workflow workflow, WorkflowPhase workflowPhase) {
    List<TemplateExpression> templateExpressions = workflow.getTemplateExpressions();
    TemplateExpression envExpression = getTemplateExpression(templateExpressions, "envId");
    // Reset template expressions
    templateExpressions = new ArrayList<>();
    if (envExpression != null) {
      templateExpressions.add(envExpression);
    }
    if (workflowPhase != null) {
      List<TemplateExpression> phaseTemplateExpressions = workflowPhase.getTemplateExpressions();
      if (isEmpty(phaseTemplateExpressions)) {
        phaseTemplateExpressions = new ArrayList<>();
      }
      // It means, user templatizing it from phase level
      addTemplateExpressions(phaseTemplateExpressions, templateExpressions);
      validateTemplateExpressions(templateExpressions);

      workflow.setTemplateExpressions(templateExpressions);
    }
  }

  public static void validateTemplateExpressions(List<TemplateExpression> templateExpressions) {
    // Validate combinations
    TemplateExpression envExpression =
        WorkflowServiceTemplateHelper.getTemplateExpression(templateExpressions, "envId");
    TemplateExpression infraExpression = getTemplateExpression(templateExpressions, "infraDefinitionId");

    // It means nullifying both Service and InfraMappings .. throw an error if environment is templatized
    // Infra not present
    if (envExpression != null) {
      if (infraExpression == null) {
        throw new InvalidRequestException(
            "Infra Definition cannot be de-templatized because Environment is templatized", USER);
      }
    }
  }

  public static TemplateExpression getTemplateExpression(
      List<TemplateExpression> templateExpressions, String fieldName) {
    return templateExpressions == null
        ? null
        : templateExpressions.stream()
              .filter(templateExpression -> templateExpression.getFieldName().equals(fieldName))
              .findAny()
              .orElse(null);
  }

  public static String getInframappingExpressionName(DeploymentType deploymentType, String expression) {
    switch (deploymentType) {
      case SSH:
        return expression + "_SSH";
      case KUBERNETES:
        return expression + "_Kubernetes";
      case ECS:
        return expression + "_ECS";
      case AWS_LAMBDA:
        return expression + "_AWS_Lambda";
      case AMI:
        return expression + "_AMI";
      case AWS_CODEDEPLOY:
        return expression + "_AWS_CodeDeploy";
      case HELM:
        return expression + "_HELM";
      case WINRM:
        return expression + "_WINRM";
      case PCF:
        return expression + "_PCF";
      default:
        unhandled(deploymentType);
    }
    return expression;
  }

  public static String getTemplatizedEnvVariableName(List<Variable> variables) {
    if (isNotEmpty(variables)) {
      return variables.stream()
          .filter((Variable variable) -> ENVIRONMENT == variable.obtainEntityType())
          .map(Variable::getName)
          .findFirst()
          .orElse(null);
    }
    return null;
  }

  public static Variable getEnvVariable(List<Variable> variables) {
    if (isNotEmpty(variables)) {
      return variables.stream()
          .filter((Variable variable) -> ENVIRONMENT == variable.obtainEntityType())
          .findFirst()
          .orElse(null);
    }
    return null;
  }

  /***
   * Templatizes the service infra if environment templatized for Phase
   */
  public static void templatizeServiceInfra(OrchestrationWorkflow orchestrationWorkflow, WorkflowPhase workflowPhase,
      List<TemplateExpression> phaseTemplateExpressions, Service service) {
    if (isEmpty(orchestrationWorkflow.getUserVariables())) {
      return;
    }
    List<String> serviceInfraVariables =
        getServiceInfrastructureWorkflowVariables(orchestrationWorkflow.getUserVariables());

    Map<String, Object> metaData = new HashMap<>();
    metaData.put(Variable.ENTITY_TYPE, INFRASTRUCTURE_MAPPING.name());
    if (service.getArtifactType() != null) {
      metaData.put(Variable.ARTIFACT_TYPE, service.getArtifactType().name());
    }
    String expression = "${ServiceInfra";
    int i = 1;
    for (String serviceInfraVariable : serviceInfraVariables) {
      if (serviceInfraVariable.startsWith("ServiceInfra")) {
        i++;
      }
    }
    expression =
        WorkflowServiceTemplateHelper.getInframappingExpressionName(workflowPhase.getDeploymentType(), expression);
    if (i != 1) {
      expression = expression + i;
    }
    expression = expression + "}";
    phaseTemplateExpressions.add(
        TemplateExpression.builder().fieldName("infraMappingId").metadata(metaData).expression(expression).build());
    orchestrationWorkflow.addToUserVariables(
        phaseTemplateExpressions, StateType.PHASE.name(), workflowPhase.getName(), null);
  }

  /***
   * Templatizes the service infra if environment templatized for Phase
   */
  public static void templatizeInfraDefinition(OrchestrationWorkflow orchestrationWorkflow, WorkflowPhase workflowPhase,
      List<TemplateExpression> phaseTemplateExpressions, Service service) {
    if (isEmpty(orchestrationWorkflow.getUserVariables())) {
      return;
    }
    List<String> infraDefinitionVariables =
        getInfraDefinitionWorkflowVariables(orchestrationWorkflow.getUserVariables());

    Map<String, Object> metaData = new HashMap<>();
    metaData.put(Variable.ENTITY_TYPE, INFRASTRUCTURE_DEFINITION.name());
    if (service.getArtifactType() != null) {
      metaData.put(Variable.ARTIFACT_TYPE, service.getArtifactType().name());
    }
    String expression = "${InfraDefinition";
    int i = 1;
    for (String infraDefinitionVariable : infraDefinitionVariables) {
      if (infraDefinitionVariable.startsWith("InfraDefinition")) {
        i++;
      }
    }
    expression =
        WorkflowServiceTemplateHelper.getInframappingExpressionName(workflowPhase.getDeploymentType(), expression);
    if (i != 1) {
      expression = expression + i;
    }
    expression = expression + "}";
    phaseTemplateExpressions.add(
        TemplateExpression.builder().fieldName("infraDefinitionId").metadata(metaData).expression(expression).build());
    orchestrationWorkflow.addToUserVariables(
        phaseTemplateExpressions, StateType.PHASE.name(), workflowPhase.getName(), null);
  }

  public static String getInfraDefExpressionFromInfraMappingExpression(String infraMappingExpression) {
    String infraDefExpression;
    if (infraMappingExpression.startsWith("${ServiceInfra")) {
      return infraMappingExpression.replace("ServiceInfra", "InfraDefinition");
    }
    return infraMappingExpression;
  }

  public static String getInfraDefVariableNameFromInfraMappingVariableName(String variableName) {
    String infraDefExpression;
    if (variableName.startsWith("ServiceInfra")) {
      return variableName.replace("ServiceInfra", "InfraDefinition");
    }
    return variableName;
  }

  public static List<String> getServiceInfrastructureWorkflowVariables(List<Variable> variables) {
    if (isEmpty(variables)) {
      return new ArrayList<>();
    }
    return variables.stream()
        .filter(
            variable -> variable.obtainEntityType() != null && variable.obtainEntityType() == INFRASTRUCTURE_MAPPING)
        .map(Variable::getName)
        .distinct()
        .collect(toList());
  }

  public static List<String> getInfraDefinitionWorkflowVariables(List<Variable> variables) {
    if (isEmpty(variables)) {
      return new ArrayList<>();
    }
    return variables.stream()
        .filter(
            variable -> variable.obtainEntityType() != null && variable.obtainEntityType() == INFRASTRUCTURE_DEFINITION)
        .map(Variable::getName)
        .distinct()
        .collect(toList());
  }

  public static List<Variable> getInfraDefCompleteWorkflowVariables(List<Variable> variables) {
    if (isEmpty(variables)) {
      return new ArrayList<>();
    }
    return variables.stream()
        .filter(
            variable -> variable.obtainEntityType() != null && variable.obtainEntityType() == INFRASTRUCTURE_DEFINITION)
        .distinct()
        .collect(toList());
  }

  public static List<String> getServiceWorkflowVariables(List<Variable> variables) {
    if (isEmpty(variables)) {
      return new ArrayList<>();
    }
    return variables.stream()
        .filter(variable -> variable.obtainEntityType() != null && variable.obtainEntityType() == SERVICE)
        .map(Variable::getName)
        .distinct()
        .collect(toList());
  }

  /***
   *
   * @param templateExpressions
   * @return
   */
  public static boolean isEnvironmentTemplatized(List<TemplateExpression> templateExpressions) {
    if (templateExpressions == null) {
      return false;
    }
    return templateExpressions.stream().anyMatch(
        templateExpression -> templateExpression.getFieldName().equals("envId"));
  }

  public static boolean isInfraTemplatized(List<TemplateExpression> templateExpressions) {
    if (templateExpressions == null) {
      return false;
    }
    return templateExpressions.stream().anyMatch(
        templateExpression -> templateExpression.getFieldName().equals("infraMappingId"));
  }

  public static boolean isInfraDefinitionTemplatized(List<TemplateExpression> templateExpressions) {
    if (templateExpressions == null) {
      return false;
    }
    return templateExpressions.stream().anyMatch(
        templateExpression -> templateExpression.getFieldName().equals("infraDefinitionId"));
  }

  public static void transformEnvTemplateExpressions(Workflow workflow, OrchestrationWorkflow orchestrationWorkflow) {
    if (isNotEmpty(workflow.getTemplateExpressions())) {
      Optional<TemplateExpression> envExpression =
          workflow.getTemplateExpressions()
              .stream()
              .filter(templateExpression -> templateExpression.getFieldName().equals("envId"))
              .findAny();
      if (envExpression.isPresent()) {
        orchestrationWorkflow.addToUserVariables(asList(envExpression.get()));
      }
    }
  }

  public static String getName(String expression) {
    return ManagerExpressionEvaluator.getName(expression);
  }

  public static String validateAndGetVariable(String variable, EntityType entityType) {
    if (variable.startsWith("workflow.variables.")) {
      variable = variable.replace("workflow.variables.", "");
    }
    Matcher matcher = ManagerExpressionEvaluator.variableNamePattern.matcher(variable);
    if (entityType != null) {
      if (!matcher.matches()) {
        throw new InvalidRequestException("Template variable:[" + variable
                + "] is not valid, should start with ${ and end with }, can have a-z,A-Z,0-9,-_",
            USER);
      }
    }
    return variable;
  }

  public static String getVariableDescription(
      EntityType entityType, OrchestrationWorkflowType orchestrationWorkflowType, String stateName) {
    if (entityType != null) {
      switch (entityType) {
        case ENVIRONMENT:
          return ENV_VAR_DESC;
        case SERVICE:
          return BASIC == orchestrationWorkflowType ? SERVICE_VAR_DESC : SERVICE_VAR_DESC + " in " + stateName;
        case INFRASTRUCTURE_MAPPING:
          return BASIC == orchestrationWorkflowType ? SERVICE_INFRA_VAR_DESC
                                                    : SERVICE_INFRA_VAR_DESC + " in " + stateName;
        case INFRASTRUCTURE_DEFINITION:
          return BASIC == orchestrationWorkflowType ? INFRADEF_VAR_DESC : INFRADEF_VAR_DESC + " in " + stateName;
        case APPDYNAMICS_CONFIGID:
          return APPD_SERVER_VAR_DESC + " in " + stateName;
        case APPDYNAMICS_APPID:
          return APPD_APP_VAR_DESC + " in " + stateName;
        case APPDYNAMICS_TIERID:
          return APPD_TIER_VAR_DESC + " in " + stateName;
        case ELK_CONFIGID:
          return ELK_SERVER_VAR_DESC + " in " + stateName;
        case ELK_INDICES:
          return ELK_INDICES_VAR_DESC + " in " + stateName;
        case CF_AWS_CONFIG_ID:
          return CF_AWSCONFIG_VAR_DESC + " in " + stateName;
        case HELM_GIT_CONFIG_ID:
          return HELM_GITCONFIG_VAR_DESC + " in " + stateName;
        case SS_SSH_CONNECTION_ATTRIBUTE:
          return SSH_CONNECTION_ATTRIBUTE_DESC + " in " + stateName;
        case USER_GROUP:
          return USER_GROUP_DESC + " in " + stateName;
        case SS_WINRM_CONNECTION_ATTRIBUTE:
          return WINRM_CONNECTION_ATTRIBUTE_DESC + " in " + stateName;
        case GCP_CONFIG:
          return GCP_CONFIG_VAR_DESC + " in " + stateName;
        case GIT_CONFIG:
          return GIT_CONFIG_VAR_DESC + " in " + stateName;
        case JENKINS_SERVER:
          return JENKINS_SERVER_VAR_DESC + " in " + stateName;
        case ARTIFACT_STREAM:
          return ARTIFACT_SOURCE_VAR_DESC + " in " + stateName;
        default:
          return "";
      }
    }
    return "";
  }

  public void setServiceTemplateExpressionMetadata(Workflow workflow, OrchestrationWorkflow orchestrationWorkflow) {
    // Get Workflow Template Expressions
    setArtifactType(workflow.getServiceId(), workflow.fetchServiceTemplateExpression());
    // Find serviceTemplateExpression -> setMetadata
    setServiceTemplateExpressionMetadata(orchestrationWorkflow);
  }

  private void setServiceTemplateExpressionMetadata(OrchestrationWorkflow orchestrationWorkflow) {
    if (orchestrationWorkflow instanceof CanaryOrchestrationWorkflow) {
      CanaryOrchestrationWorkflow canaryOrchestrationWorkflow = (CanaryOrchestrationWorkflow) orchestrationWorkflow;
      // Go over all phases
      List<WorkflowPhase> workflowPhases = canaryOrchestrationWorkflow.getWorkflowPhases();
      if (isNotEmpty(workflowPhases)) {
        for (WorkflowPhase workflowPhase : workflowPhases) {
          setArtifactType(workflowPhase.getServiceId(), workflowPhase.fetchServiceTemplateExpression());
        }
      }
    }
  }

  private void setArtifactType(String serviceId, TemplateExpression serviceTemplateExpression) {
    if (serviceTemplateExpression != null) {
      // Check if ServiceTemplate Expression contains ArtifactType return it
      final Map<String, Object> metadata = serviceTemplateExpression.getMetadata();
      if (metadata == null || metadata.get(Variable.ARTIFACT_TYPE) != null || serviceId == null) {
        return;
      }
      Service service = serviceResourceService.get(serviceId);
      if (service == null || service.getArtifactType() == null) {
        return;
      }
      metadata.put(Variable.ARTIFACT_TYPE, service.getArtifactType().name());
    }
  }
}
