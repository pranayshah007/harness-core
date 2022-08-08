/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.workflow;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.CanaryWorkflowExecutionAdvisor.ROLLBACK_PROVISIONERS;
import static software.wings.beans.CanaryWorkflowExecutionAdvisor.ROLLBACK_PROVISIONERS_REVERSE;
import static software.wings.service.impl.yaml.handler.workflow.PhaseStepYamlHandler.PHASE_STEP_PROPERTY_NAME;

import static java.util.Collections.emptyList;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.FeatureName;
import io.harness.beans.WorkflowType;
import io.harness.exception.HarnessException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;

import software.wings.beans.Application;
import software.wings.beans.CanaryOrchestrationWorkflow;
import software.wings.beans.EntityType;
import software.wings.beans.Environment;
import software.wings.beans.FailureStrategy;
import software.wings.beans.GraphNode;
import software.wings.beans.NotificationRule;
import software.wings.beans.PhaseStep;
import software.wings.beans.PhaseStep.PhaseStepBuilder;
import software.wings.beans.PhaseStepType;
import software.wings.beans.TemplateExpression;
import software.wings.beans.Variable;
import software.wings.beans.VariableYaml;
import software.wings.beans.Workflow;
import software.wings.beans.Workflow.WorkflowBuilder;
import software.wings.beans.WorkflowPhase;
import software.wings.beans.WorkflowPhase.Yaml;
import software.wings.beans.workflow.StepSkipStrategy;
import software.wings.beans.yaml.Change;
import software.wings.beans.yaml.ChangeContext;
import software.wings.beans.yaml.YamlConstants;
import software.wings.beans.yaml.YamlType;
import software.wings.service.impl.yaml.handler.BaseYamlHandler;
import software.wings.service.impl.yaml.handler.YamlHandlerFactory;
import software.wings.service.impl.yaml.handler.notification.NotificationRulesYamlHandler;
import software.wings.service.impl.yaml.handler.template.TemplateExpressionYamlHandler;
import software.wings.service.impl.yaml.handler.variable.VariableYamlHandler;
import software.wings.service.impl.yaml.service.YamlHelper;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.WorkflowService;
import software.wings.yaml.workflow.BuildWorkflowYaml;
import software.wings.yaml.workflow.StepYaml;
import software.wings.yaml.workflow.WorkflowYaml;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Data;

/**
 * @author rktummala on 10/27/17
 */
@OwnedBy(CDC)
@TargetModule(HarnessModule._955_CG_YAML)
public abstract class WorkflowYamlHandler<Y extends WorkflowYaml> extends BaseYamlHandler<Y, Workflow> {
  @Inject WorkflowService workflowService;
  @Inject YamlHelper yamlHelper;
  @Inject YamlHandlerFactory yamlHandlerFactory;
  @Inject EnvironmentService environmentService;
  @Inject AppService appService;
  @Inject FeatureFlagService featureFlagService;

  protected abstract void setOrchestrationWorkflow(WorkflowInfo workflowInfo, WorkflowBuilder workflowBuilder);

  @Override
  public Workflow upsertFromYaml(ChangeContext<Y> changeContext, List<ChangeContext> changeSetContext)
      throws HarnessException {
    String accountId = changeContext.getChange().getAccountId();
    String yamlFilePath = changeContext.getChange().getFilePath();
    Workflow previousWorkflow = get(accountId, yamlFilePath);

    WorkflowBuilder workflowBuilder = WorkflowBuilder.aWorkflow();
    toBean(changeContext, changeSetContext, workflowBuilder, previousWorkflow);

    workflowBuilder.syncFromGit(changeContext.getChange().isSyncFromGit());
    Workflow current;

    if (previousWorkflow != null) {
      previousWorkflow.setSyncFromGit(changeContext.getChange().isSyncFromGit());
      workflowBuilder.uuid(previousWorkflow.getUuid());
      current = workflowService.updateLinkedWorkflow(workflowBuilder.build(), previousWorkflow, true);
    } else {
      current = workflowService.createWorkflow(workflowBuilder.build());
    }

    changeContext.setEntity(current);
    return current;
  }

  private void toBean(ChangeContext<Y> changeContext, List<ChangeContext> changeContextList, WorkflowBuilder workflow,
      Workflow previousWorkflow) throws HarnessException {
    WorkflowYaml yaml = changeContext.getYaml();
    Change change = changeContext.getChange();

    String appId = yamlHelper.getAppId(change.getAccountId(), change.getFilePath());
    notNullCheck("Could not locate app info in file path:" + change.getFilePath(), appId, USER);

    // Environment can be null in cloned workflows
    Environment environment = environmentService.getEnvironmentByName(appId, yaml.getEnvName());

    String envId = environment != null ? environment.getUuid() : null;

    try {
      WorkflowPhaseYamlHandler phaseYamlHandler = yamlHandlerFactory.getYamlHandler(YamlType.PHASE);

      // phases
      List<WorkflowPhase> phaseList = Lists.newArrayList();
      if (yaml.getPhases() != null) {
        phaseList =
            yaml.getPhases()
                .stream()
                .map(workflowPhaseYaml -> {
                  try {
                    ChangeContext.Builder clonedContextBuilder =
                        cloneFileChangeContext(changeContext, workflowPhaseYaml);
                    ChangeContext clonedContext = clonedContextBuilder.build();
                    clonedContext.getEntityIdMap().put(EntityType.ENVIRONMENT.name(), envId);
                    clonedContext.getProperties().put(YamlConstants.IS_ROLLBACK, false);
                    clonedContext.getProperties().put("IS_BUILD", yaml instanceof BuildWorkflowYaml);

                    WorkflowPhase workflowPhase = phaseYamlHandler.upsertFromYaml(clonedContext, changeContextList);
                    String workflowPhaseId = getPreviousWorkflowPhaseId(workflowPhase.getName(), previousWorkflow);
                    if (workflowPhaseId != null) {
                      workflowPhase.setUuid(workflowPhaseId);
                    }
                    return workflowPhase;
                  } catch (HarnessException e) {
                    throw new WingsException(e);
                  }
                })
                .collect(toList());
      }

      Map<String, WorkflowPhase> workflowPhaseMap =
          phaseList.stream().collect(Collectors.toMap(WorkflowPhase::getName, identity()));

      // rollback phases
      Map<String, WorkflowPhase> rollbackPhaseMap = new HashMap<>();
      if (yaml.getRollbackPhases() != null) {
        List<WorkflowPhase> rollbackPhaseList =
            yaml.getRollbackPhases()
                .stream()
                .map(workflowPhaseYaml -> {
                  try {
                    ChangeContext.Builder clonedContextBuilder =
                        cloneFileChangeContext(changeContext, workflowPhaseYaml);
                    ChangeContext clonedContext = clonedContextBuilder.build();
                    clonedContext.getEntityIdMap().put(EntityType.ENVIRONMENT.name(), envId);
                    clonedContext.getProperties().put(YamlConstants.IS_ROLLBACK, true);
                    clonedContext.getProperties().put("IS_BUILD", yaml instanceof BuildWorkflowYaml);

                    WorkflowPhase workflowPhase = phaseYamlHandler.upsertFromYaml(clonedContext, changeContextList);
                    String workflowPhaseId = getPreviousWorkflowPhaseId(workflowPhase.getName(), previousWorkflow);
                    if (workflowPhaseId != null) {
                      workflowPhase.setUuid(workflowPhaseId);
                    }

                    return workflowPhase;
                  } catch (HarnessException e) {
                    throw new WingsException(e);
                  }
                })
                .collect(toList());
        rollbackPhaseList.forEach(rollbackPhase -> {
          WorkflowPhase workflowPhase = workflowPhaseMap.get(rollbackPhase.getPhaseNameForRollback());
          if (workflowPhase != null) {
            rollbackPhaseMap.put(workflowPhase.getUuid(), rollbackPhase);
          }
        });
      }

      // user variables
      List<Variable> userVariables = Lists.newArrayList();
      if (yaml.getUserVariables() != null) {
        VariableYamlHandler variableYamlHandler = yamlHandlerFactory.getYamlHandler(YamlType.VARIABLE);
        userVariables = yaml.getUserVariables()
                            .stream()
                            .map(userVariable -> {
                              try {
                                ChangeContext.Builder clonedContext =
                                    cloneFileChangeContext(changeContext, userVariable);
                                return variableYamlHandler.upsertFromYaml(clonedContext.build(), previousWorkflow);
                              } catch (InvalidRequestException e) {
                                throw new InvalidRequestException(e.getMessage(), e);
                              }
                            })
                            .collect(toList());
      }

      // template expressions
      List<TemplateExpression> templateExpressions = Lists.newArrayList();
      if (yaml.getTemplateExpressions() != null) {
        TemplateExpressionYamlHandler templateExprYamlHandler =
            yamlHandlerFactory.getYamlHandler(YamlType.TEMPLATE_EXPRESSION);

        templateExpressions =
            yaml.getTemplateExpressions()
                .stream()
                .map(templateExpr -> {
                  try {
                    ChangeContext.Builder clonedContext = cloneFileChangeContext(changeContext, templateExpr);
                    return templateExprYamlHandler.upsertFromYaml(clonedContext.build(), changeContextList);
                  } catch (HarnessException e) {
                    throw new WingsException(e);
                  }
                })
                .collect(toList());
      }

      StepYamlHandler stepYamlHandler = yamlHandlerFactory.getYamlHandler(YamlType.STEP);

      // Pre-deployment steps
      PhaseStepBuilder preDeploymentSteps = PhaseStepBuilder.aPhaseStep(PhaseStepType.PRE_DEPLOYMENT);

      if (yaml.getPreDeploymentSteps() != null) {
        List<GraphNode> stepList =
            yaml.getPreDeploymentSteps()
                .stream()
                .map(stepYaml -> {
                  try {
                    ChangeContext.Builder clonedContext = cloneFileChangeContext(changeContext, stepYaml);
                    return stepYamlHandler.upsertFromYaml(clonedContext.build(), changeContextList);
                  } catch (HarnessException e) {
                    throw new WingsException(e);
                  }
                })
                .collect(toList());
        preDeploymentSteps.addAllSteps(stepList).build();
      }

      // Post-deployment steps
      PhaseStepBuilder postDeploymentSteps = PhaseStepBuilder.aPhaseStep(PhaseStepType.POST_DEPLOYMENT);

      if (yaml.getPostDeploymentSteps() != null) {
        List<GraphNode> postDeployStepList =
            yaml.getPostDeploymentSteps()
                .stream()
                .map(stepYaml -> {
                  try {
                    ChangeContext.Builder clonedContext = cloneFileChangeContext(changeContext, stepYaml);
                    return stepYamlHandler.upsertFromYaml(clonedContext.build(), changeContextList);
                  } catch (HarnessException e) {
                    throw new WingsException(e);
                  }
                })
                .collect(toList());
        postDeploymentSteps.addAllSteps(postDeployStepList).build();
      }

      // Failure strategies
      List<FailureStrategy> failureStrategies =
          getFailureStrategiesFromYaml(changeContext, changeContextList, yaml.getFailureStrategies());
      List<FailureStrategy> preDeploymentFailureStrategies =
          getFailureStrategiesFromYaml(changeContext, changeContextList, yaml.getPreDeploymentFailureStrategy());
      List<FailureStrategy> postDeploymentFailureStrategies =
          getFailureStrategiesFromYaml(changeContext, changeContextList, yaml.getPostDeploymentFailureStrategy());
      preDeploymentSteps.withFailureStrategies(preDeploymentFailureStrategies);
      postDeploymentSteps.withFailureStrategies(postDeploymentFailureStrategies);

      PhaseStep preDeploymentStepsFinal = preDeploymentSteps.build();
      PhaseStep postDeploymentStepsFinal = postDeploymentSteps.build();

      // Step Skip strategies
      List<StepSkipStrategy> preDeploymentStepSkipStrategies = getStepSkipStrategiesFromYaml(
          changeContext, changeContextList, yaml.getPreDeploymentStepSkipStrategy(), preDeploymentStepsFinal);
      List<StepSkipStrategy> postDeploymentStepSkipStrategies = getStepSkipStrategiesFromYaml(
          changeContext, changeContextList, yaml.getPostDeploymentStepSkipStrategy(), postDeploymentStepsFinal);
      preDeploymentStepsFinal.setStepSkipStrategies(preDeploymentStepSkipStrategies);
      postDeploymentStepsFinal.setStepSkipStrategies(postDeploymentStepSkipStrategies);

      // Notification rules
      List<NotificationRule> notificationRules = Lists.newArrayList();
      if (yaml.getNotificationRules() != null) {
        NotificationRulesYamlHandler notificationRuleYamlHandler =
            yamlHandlerFactory.getYamlHandler(YamlType.NOTIFICATION_RULE);
        notificationRules =
            yaml.getNotificationRules()
                .stream()
                .map(notificationRule -> {
                  try {
                    ChangeContext.Builder clonedContext = cloneFileChangeContext(changeContext, notificationRule);
                    return notificationRuleYamlHandler.upsertFromYaml(clonedContext.build(), changeContextList);
                  } catch (HarnessException e) {
                    throw new WingsException(e);
                  }
                })
                .collect(toList());
      }

      PhaseStep rollbackProvisioners = null, rollbackProvisionersReverse = null;
      if (isNotEmpty(preDeploymentStepsFinal.getSteps())) {
        rollbackProvisioners = workflowService.generateRollbackProvisioners(
            preDeploymentStepsFinal, PhaseStepType.ROLLBACK_PROVISIONERS, ROLLBACK_PROVISIONERS);
        String accountId = appService.getAccountIdByAppId(appId);
        if (featureFlagService.isEnabled(FeatureName.ROLLBACK_PROVISIONER_AFTER_PHASES, accountId)) {
          // Generate rollbackProvisionerReverse step to support ROLLBACK_PROVISIONER_AFTER_PHASES failure strategy
          rollbackProvisionersReverse = workflowService.generateRollbackProvisionersReverse(
              preDeploymentStepsFinal, PhaseStepType.ROLLBACK_PROVISIONERS, ROLLBACK_PROVISIONERS_REVERSE);
        }
      }

      WorkflowInfo workflowInfo = WorkflowInfo.builder()
                                      .failureStrategies(failureStrategies)
                                      .notificationRules(notificationRules)
                                      .postDeploymentSteps(postDeploymentStepsFinal)
                                      .preDeploymentSteps(preDeploymentStepsFinal)
                                      .rollbackPhaseMap(rollbackPhaseMap)
                                      .userVariables(userVariables)
                                      .phaseList(phaseList)
                                      .concurrencyStrategy(yaml.getConcurrencyStrategy())
                                      .rollbackProvisioners(rollbackProvisioners)
                                      .rollbackProvisionersReverse(rollbackProvisionersReverse)
                                      .build();
      setOrchestrationWorkflow(workflowInfo, workflow);

      String name = yamlHelper.getNameFromYamlFilePath(changeContext.getChange().getFilePath());
      workflow.appId(appId)
          .description(yaml.getDescription())
          .envId(envId)
          .name(name)
          .templateExpressions(templateExpressions)
          .templatized(yaml.isTemplatized())
          .workflowType(WorkflowType.ORCHESTRATION);

    } catch (WingsException ex) {
      throw new HarnessException(ex);
    }
  }

  private List<FailureStrategy> getFailureStrategiesFromYaml(ChangeContext<Y> changeContext,
      List<ChangeContext> changeContextList, List<FailureStrategy.Yaml> failureStrategyYaml) {
    if (isEmpty(failureStrategyYaml)) {
      return emptyList();
    }
    FailureStrategyYamlHandler failureStrategyYamlHandler =
        yamlHandlerFactory.getYamlHandler(YamlType.FAILURE_STRATEGY);
    return failureStrategyYaml.stream()
        .map(failureStrategy -> {
          try {
            ChangeContext.Builder clonedContext = cloneFileChangeContext(changeContext, failureStrategy);
            return failureStrategyYamlHandler.upsertFromYaml(clonedContext.build(), changeContextList);
          } catch (HarnessException e) {
            throw new WingsException(e);
          }
        })
        .collect(toList());
  }

  private List<StepSkipStrategy> getStepSkipStrategiesFromYaml(ChangeContext<Y> changeContext,
      List<ChangeContext> changeContextList, List<StepSkipStrategy.Yaml> stepSkipStrategyYaml, PhaseStep phaseStep) {
    if (isEmpty(stepSkipStrategyYaml)) {
      return emptyList();
    }

    StepSkipStrategyYamlHandler stepSkipStrategyYamlHandler =
        yamlHandlerFactory.getYamlHandler(YamlType.STEP_SKIP_STRATEGY);
    List<StepSkipStrategy> stepSkipStrategies =
        stepSkipStrategyYaml.stream()
            .map(stepSkipStrategy -> {
              ChangeContext<StepSkipStrategy.Yaml> clonedContext =
                  cloneFileChangeContext(changeContext, stepSkipStrategy).build();
              clonedContext.getProperties().put(PHASE_STEP_PROPERTY_NAME, phaseStep);
              return stepSkipStrategyYamlHandler.upsertFromYaml(clonedContext, changeContextList);
            })
            .collect(toList());
    StepSkipStrategy.validateStepSkipStrategies(stepSkipStrategies);
    return stepSkipStrategies;
  }

  private String getPreviousWorkflowPhaseId(String name, Workflow previous) {
    if (previous == null || previous.getOrchestrationWorkflow() == null) {
      return null;
    }

    CanaryOrchestrationWorkflow orchestrationWorkflow =
        (CanaryOrchestrationWorkflow) previous.getOrchestrationWorkflow();
    List<WorkflowPhase> workflowPhaseList = orchestrationWorkflow.getWorkflowPhases()
                                                .stream()
                                                .filter(phase -> name.equals(phase.getName()))
                                                .collect(Collectors.toList());
    int size = workflowPhaseList.size();
    // If size is greater than, we don't know what to pick, so we return null.
    if (size == 1) {
      return workflowPhaseList.get(0).getUuid();
    } else {
      return null;
    }
  }

  protected void toYaml(Y yaml, Workflow workflow, String appId) {
    // Environment can be null in case of incomplete cloned workflows
    String envName = null;
    if (isNotBlank(workflow.getEnvId())) {
      if (!workflow.checkEnvironmentTemplatized()) {
        Environment environment = environmentService.get(appId, workflow.getEnvId());
        envName = environment != null ? environment.getName() : null;
      }
    }

    CanaryOrchestrationWorkflow orchestrationWorkflow =
        (CanaryOrchestrationWorkflow) workflow.getOrchestrationWorkflow();
    List<WorkflowPhase> workflowPhases = orchestrationWorkflow.getWorkflowPhases();

    // phases
    WorkflowPhaseYamlHandler phaseYamlHandler = yamlHandlerFactory.getYamlHandler(YamlType.PHASE);
    List<WorkflowPhase.Yaml> phaseYamlList =
        workflowPhases.stream().map(workflowPhase -> phaseYamlHandler.toYaml(workflowPhase, appId)).collect(toList());

    // rollback phases
    Map<String, WorkflowPhase> rollbackWorkflowPhaseIdMap = orchestrationWorkflow.getRollbackWorkflowPhaseIdMap();
    List<WorkflowPhase.Yaml> rollbackPhaseYamlList = Lists.newArrayList();
    orchestrationWorkflow.getWorkflowPhaseIds().forEach(workflowPhaseId -> {
      WorkflowPhase rollbackPhase = rollbackWorkflowPhaseIdMap.get(workflowPhaseId);
      if (rollbackPhase != null) {
        Yaml rollbackPhaseYaml = phaseYamlHandler.toYaml(rollbackPhase, appId);
        rollbackPhaseYamlList.add(rollbackPhaseYaml);
      }
    });

    // user variables
    List<Variable> userVariables = orchestrationWorkflow.getUserVariables();
    VariableYamlHandler variableYamlHandler = yamlHandlerFactory.getYamlHandler(YamlType.VARIABLE);
    List<VariableYaml> variableYamlList =
        userVariables.stream().map(userVariable -> variableYamlHandler.toYaml(userVariable, appId)).collect(toList());

    // template expressions
    TemplateExpressionYamlHandler templateExpressionYamlHandler =
        yamlHandlerFactory.getYamlHandler(YamlType.TEMPLATE_EXPRESSION);
    List<TemplateExpression> templateExpressions = workflow.getTemplateExpressions();
    List<TemplateExpression.Yaml> templateExprYamlList = null;
    if (templateExpressions != null) {
      templateExprYamlList =
          templateExpressions.stream()
              .map(templateExpression -> templateExpressionYamlHandler.toYaml(templateExpression, appId))
              .collect(toList());
    }

    StepYamlHandler stepYamlHandler = yamlHandlerFactory.getYamlHandler(YamlType.STEP);
    // Pre-deployment steps
    PhaseStep preDeploymentSteps = orchestrationWorkflow.getPreDeploymentSteps();
    List<StepYaml> preDeployStepsYamlList =
        preDeploymentSteps.getSteps().stream().map(step -> stepYamlHandler.toYaml(step, appId)).collect(toList());

    // Post-deployment steps
    PhaseStep postDeploymentSteps = orchestrationWorkflow.getPostDeploymentSteps();
    List<StepYaml> postDeployStepsYamlList =
        postDeploymentSteps.getSteps().stream().map(step -> stepYamlHandler.toYaml(step, appId)).collect(toList());

    // Failure strategies
    FailureStrategyYamlHandler failureStrategyYamlHandler =
        yamlHandlerFactory.getYamlHandler(YamlType.FAILURE_STRATEGY);
    List<FailureStrategy> failureStrategies = orchestrationWorkflow.getFailureStrategies();
    List<FailureStrategy.Yaml> failureStrategyYamlList =
        failureStrategies.stream()
            .map(failureStrategy -> failureStrategyYamlHandler.toYaml(failureStrategy, appId))
            .collect(toList());

    // Notification rules
    NotificationRulesYamlHandler notificationRuleYamlHandler =
        yamlHandlerFactory.getYamlHandler(YamlType.NOTIFICATION_RULE);
    List<NotificationRule> notificationRules = orchestrationWorkflow.getNotificationRules();
    List<NotificationRule.Yaml> notificationRuleYamlList =
        notificationRules.stream()
            .map(notificationRule -> notificationRuleYamlHandler.toYaml(notificationRule, appId))
            .collect(toList());

    // Pre Deployment Failure Strategy
    List<FailureStrategy.Yaml> preDeploymentFailureStrategyYaml =
        emptyIfNull(orchestrationWorkflow.getPreDeploymentSteps().getFailureStrategies())
            .stream()
            .map(failureStrategy -> failureStrategyYamlHandler.toYaml(failureStrategy, appId))
            .collect(toList());

    // Post Deployment Failure Strategy
    List<FailureStrategy.Yaml> postDeploymentFailureStrategyYaml =
        emptyIfNull(orchestrationWorkflow.getPostDeploymentSteps().getFailureStrategies())
            .stream()
            .map(failureStrategy -> failureStrategyYamlHandler.toYaml(failureStrategy, appId))
            .collect(toList());

    // Step Skip Strategy
    StepSkipStrategyYamlHandler stepSkipStrategyYamlHandler =
        yamlHandlerFactory.getYamlHandler(YamlType.STEP_SKIP_STRATEGY);

    // Pre Deployment Step Skip Strategy
    List<StepSkipStrategy.Yaml> preDeploymentStepSkipStrategyYaml =
        emptyIfNull(orchestrationWorkflow.getPreDeploymentSteps().getStepSkipStrategies())
            .stream()
            .map(stepSkipStrategy -> {
              stepSkipStrategy.setPhaseStep(preDeploymentSteps);
              return stepSkipStrategyYamlHandler.toYaml(stepSkipStrategy, appId);
            })
            .collect(toList());

    // Post Deployment Step Skip Strategy
    List<StepSkipStrategy.Yaml> postDeploymentStepSkipStrategyYaml =
        emptyIfNull(orchestrationWorkflow.getPostDeploymentSteps().getStepSkipStrategies())
            .stream()
            .map(stepSkipStrategy -> {
              stepSkipStrategy.setPhaseStep(postDeploymentSteps);
              return stepSkipStrategyYamlHandler.toYaml(stepSkipStrategy, appId);
            })
            .collect(toList());

    yaml.setDescription(workflow.getDescription());
    yaml.setEnvName(envName);
    yaml.setTemplateExpressions(templateExprYamlList);
    yaml.setTemplatized(workflow.isTemplatized());
    yaml.setType(orchestrationWorkflow.getOrchestrationWorkflowType().name());
    yaml.setPhases(phaseYamlList);
    yaml.setRollbackPhases(rollbackPhaseYamlList);
    yaml.setUserVariables(variableYamlList);
    yaml.setPreDeploymentSteps(preDeployStepsYamlList);
    yaml.setPostDeploymentSteps(postDeployStepsYamlList);
    yaml.setNotificationRules(notificationRuleYamlList);
    yaml.setFailureStrategies(failureStrategyYamlList);
    yaml.setHarnessApiVersion(getHarnessApiVersion());
    if (orchestrationWorkflow.getConcurrencyStrategy() != null) {
      yaml.setConcurrencyStrategy(orchestrationWorkflow.getConcurrencyStrategy().getUnitType().name());
    }
    yaml.setPreDeploymentFailureStrategy(preDeploymentFailureStrategyYaml);
    yaml.setPostDeploymentFailureStrategy(postDeploymentFailureStrategyYaml);
    yaml.setPreDeploymentStepSkipStrategy(preDeploymentStepSkipStrategyYaml);
    yaml.setPostDeploymentStepSkipStrategy(postDeploymentStepSkipStrategyYaml);

    updateYamlWithAdditionalInfo(workflow, appId, yaml);
  }

  @Override
  public Class getYamlClass() {
    return WorkflowYaml.class;
  }

  @Override
  public Workflow get(String accountId, String yamlFilePath) {
    return yamlHelper.getWorkflow(accountId, yamlFilePath);
  }

  @Override
  public void delete(ChangeContext<Y> changeContext) throws HarnessException {
    String accountId = changeContext.getChange().getAccountId();
    String yamlFilePath = changeContext.getChange().getFilePath();
    Optional<Application> optionalApplication = yamlHelper.getApplicationIfPresent(accountId, yamlFilePath);
    if (!optionalApplication.isPresent()) {
      return;
    }

    Workflow workflow = yamlHelper.getWorkflowByAppIdYamlPath(optionalApplication.get().getUuid(), yamlFilePath);
    if (workflow != null) {
      workflowService.deleteByYamlGit(
          workflow.getAppId(), workflow.getUuid(), changeContext.getChange().isSyncFromGit());
    }
  }

  @Data
  @Builder
  protected static class WorkflowInfo {
    private List<FailureStrategy> failureStrategies;
    private List<NotificationRule> notificationRules;
    private PhaseStep preDeploymentSteps;
    private PhaseStep postDeploymentSteps;
    private Map<String, WorkflowPhase> rollbackPhaseMap;
    private List<Variable> userVariables;
    private List<WorkflowPhase> phaseList;
    private String concurrencyStrategy;
    private PhaseStep rollbackProvisioners;
    private PhaseStep rollbackProvisionersReverse;
  }
}
