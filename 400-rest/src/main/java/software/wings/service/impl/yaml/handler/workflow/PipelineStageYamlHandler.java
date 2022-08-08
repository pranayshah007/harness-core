/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.workflow;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.EntityType.ENVIRONMENT;
import static software.wings.beans.PipelineStage.Yaml;
import static software.wings.expression.ManagerExpressionEvaluator.matchesVariablePattern;
import static software.wings.sm.states.ApprovalState.APPROVAL_STATE_TYPE_VARIABLE;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ErrorCode;
import io.harness.exception.HarnessException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;

import software.wings.beans.EntityType;
import software.wings.beans.Environment;
import software.wings.beans.Pipeline;
import software.wings.beans.PipelineStage;
import software.wings.beans.PipelineStage.PipelineStageElement;
import software.wings.beans.PipelineStage.WorkflowVariable;
import software.wings.beans.RuntimeInputsConfig;
import software.wings.beans.SkipCondition;
import software.wings.beans.Variable;
import software.wings.beans.Workflow;
import software.wings.beans.security.UserGroup;
import software.wings.beans.yaml.Change;
import software.wings.beans.yaml.ChangeContext;
import software.wings.service.impl.yaml.WorkflowYAMLHelper;
import software.wings.service.impl.yaml.handler.BaseYamlHandler;
import software.wings.service.impl.yaml.service.YamlHelper;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.UserGroupService;
import software.wings.service.intfc.WorkflowService;
import software.wings.sm.StateType;
import software.wings.sm.states.ApprovalState.ApprovalStateKeys;
import software.wings.sm.states.ApprovalState.ApprovalStateType;
import software.wings.sm.states.EnvState.EnvStateKeys;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * @author rktummala on 11/2/17
 */
@OwnedBy(CDC)
@Singleton
@Slf4j
public class PipelineStageYamlHandler extends BaseYamlHandler<Yaml, PipelineStage> {
  private static final String USER_GROUPS = "userGroups";
  @Inject YamlHelper yamlHelper;
  @Inject WorkflowService workflowService;
  @Inject EnvironmentService environmentService;
  @Inject WorkflowYAMLHelper workflowYAMLHelper;
  @Inject UserGroupService userGroupService;
  @Inject AppService appService;
  @Inject ApprovalStepYamlBuilder approvalStepYamlBuilder;

  private static final List<String> irrelevantApprovalStateFields =
      Arrays.asList(ApprovalStateKeys.disableAssertion, ApprovalStateKeys.disable, EnvStateKeys.pipelineId,
          EnvStateKeys.pipelineStageElementId, EnvStateKeys.pipelineStageParallelIndex);

  @VisibleForTesting
  public PipelineStage toBean(ChangeContext<Yaml> context) {
    Yaml yaml = context.getYaml();
    Change change = context.getChange();

    String appId = yamlHelper.getAppId(change.getAccountId(), change.getFilePath());
    notNullCheck("Could not retrieve valid app from path: " + change.getFilePath(), appId, USER);
    String accountId = appService.getAccountIdByAppId(appId);

    PipelineStage stage = PipelineStage.builder().build();
    stage.setName(yaml.getStageName());
    stage.setParallel(yaml.isParallel());

    String stageElementId = null;
    Pipeline previous = yamlHelper.getPipeline(change.getAccountId(), change.getFilePath());
    if (previous != null) {
      Map<String, String> entityIdMap = context.getEntityIdMap();
      if (entityIdMap != null) {
        stageElementId = entityIdMap.remove(yaml.getName());
      }
    }

    String disableAssertion = yaml.getSkipCondition() != null ? yaml.getSkipCondition().fetchDisableAssertion() : null;
    PipelineStageElement pipelineStageElement =
        PipelineStageElement.builder().disableAssertion(disableAssertion).build();
    boolean skipAlways = pipelineStageElement.checkDisableAssertion();
    Map<String, Object> properties = new HashMap<>();
    Map<String, String> workflowVariablesPse = new LinkedHashMap<>();
    if (!StateType.APPROVAL.name().equals(yaml.getType())) {
      generateBeanForEnvStage(yaml, change, appId, skipAlways, properties, workflowVariablesPse);
    } else {
      generateBeanForApprovalStage(yaml, appId, properties);
    }

    RuntimeInputsConfig.Yaml yamlConfig = yaml.getRuntimeInputs();
    RuntimeInputsConfig inputsConfig = null;
    if (yamlConfig != null) {
      inputsConfig = RuntimeInputsConfig.builder()
                         .timeout(yamlConfig.getTimeout())
                         .timeoutAction(yamlConfig.getTimeoutAction())
                         .runtimeInputVariables(yamlConfig.getRuntimeInputVariables())
                         .userGroupIds(getUserGroupUuids(yamlConfig.getUserGroupNames(), accountId))
                         .build();
    }
    pipelineStageElement = PipelineStageElement.builder()
                               .runtimeInputsConfig(inputsConfig)
                               .uuid(stageElementId)
                               .disableAssertion(disableAssertion)
                               .name(yaml.getName())
                               .type(yaml.getType())
                               .properties(properties)
                               .workflowVariables(workflowVariablesPse)
                               .build();

    stage.setPipelineStageElements(Lists.newArrayList(pipelineStageElement));
    return stage;
  }

  private void generateBeanForApprovalStage(Yaml yaml, String appId, Map<String, Object> properties) {
    Map<String, Object> yamlProperties = yaml.getProperties();
    String accountId = appService.getAccountIdByAppId(appId);

    if (yamlProperties != null) {
      yamlProperties.forEach((name, value) -> {
        if (!shouldBeIgnored(name)) {
          if (USER_GROUPS.equals(name)) {
            properties.put(name, getUserGroupUuids((List<String>) value, accountId, yamlProperties));
          } else {
            approvalStepYamlBuilder.convertNameToIdForKnownTypes(
                name, value, properties, appId, accountId, yamlProperties);
          }
        }
      });
    }
  }

  private void generateBeanForEnvStage(Yaml yaml, Change change, String appId, boolean skipAlways,
      Map<String, Object> properties, Map<String, String> workflowVariablesPse) {
    Workflow workflow;
    workflow = workflowService.readWorkflowByName(appId, yaml.getWorkflowName());
    notNullCheck("Invalid workflow with the given name:" + yaml.getWorkflowName(), workflow, USER);
    properties.put("workflowId", workflow.getUuid());

    String envId = resolveEnvironmentId(yaml, appId, properties, workflow);
    List<Variable> workflowVariables = workflow.getOrchestrationWorkflow().getUserVariables();
    if (isNotEmpty(yaml.getWorkflowVariables()) && isNotEmpty(workflowVariables)) {
      for (WorkflowVariable variable : yaml.getWorkflowVariables()) {
        String variableName = variable.getName();
        Variable variableOrg =
            workflowVariables.stream().filter(t -> t.getName().equals(variableName)).findFirst().orElse(null);
        if (variableOrg == null) {
          continue;
        }
        String entityType = variable.getEntityType();
        String variableValue = variable.getValue();
        String workflowVariableValueForBean = workflowYAMLHelper.getWorkflowVariableValueBean(
            change.getAccountId(), envId, appId, entityType, variableValue, skipAlways, variableOrg);
        workflowVariablesPse.put(variableName, Optional.ofNullable(workflowVariableValueForBean).orElse(""));
      }
    }
    log.info("The pipeline env stage properties for appId {} wrokflowId {} are {}", appId, workflow.getUuid(),
        String.valueOf(properties));
  }

  private List<String> getUserGroupUuids(
      List<String> userGroupNameList, String accountId, Map<String, Object> yamlProperties) {
    if (isEmpty(userGroupNameList)) {
      if (yamlProperties.get("templateExpressions") == null) {
        throw new InvalidRequestException("user groups cannot be empty in non templatized approval");
      }
    }
    return getUserGroupUuids(userGroupNameList, accountId);
  }

  private List<String> getUserGroupUuids(List<String> userGroupNameList, String accountId) {
    if (userGroupNameList == null) {
      return null;
    }
    List<String> userGroupUuids = new ArrayList<>();
    for (String userGroupName : userGroupNameList) {
      UserGroup userGroup = userGroupService.fetchUserGroupByName(accountId, userGroupName);
      notNullCheck("User group " + userGroupName + " doesn't exist", userGroup);
      userGroupUuids.add(userGroup.getUuid());
    }
    return userGroupUuids;
  }

  private String resolveEnvironmentId(Yaml yaml, String appId, Map<String, Object> properties, Workflow workflow) {
    String envId = null;

    if (workflow.checkEnvironmentTemplatized()) {
      log.info("Workflow environment templatized. Workflow envId of appId {} and workflowId {} is {}", appId,
          workflow.getUuid(), workflow.getEnvId());

      if (isNotEmpty(yaml.getWorkflowVariables())) {
        WorkflowVariable workflowEnvVariable =
            yaml.getWorkflowVariables()
                .stream()
                .filter((WorkflowVariable variable) -> ENVIRONMENT.name().equals(variable.getEntityType()))
                .findFirst()
                .orElse(null);

        if (workflowEnvVariable != null) {
          if (matchesVariablePattern(workflowEnvVariable.getValue())) {
            log.info("Environment parameterized in pipeline and the value is {}", workflowEnvVariable.getValue());
            properties.put("envId", workflowEnvVariable.getValue());
          } else {
            Environment environment =
                environmentService.getEnvironmentByName(appId, workflowEnvVariable.getValue(), false);
            notNullCheck("Environment [" + workflowEnvVariable.getValue() + "] does not exist", environment, USER);

            envId = environment.getUuid();
            properties.put("envId", envId);
          }
        }
      }
    }

    if (isBlank(envId)) {
      envId = workflow.getEnvId();
      properties.put("envId", envId);
    }

    return envId;
  }

  @Override
  public Yaml toYaml(PipelineStage bean, String appId) {
    String accountId = appService.getAccountIdByAppId(appId);
    PipelineStageElement stageElement = bean.getPipelineStageElements().get(0);
    notNullCheck("Pipeline stage element is null", stageElement, USER);

    Map<String, Object> outputProperties = new HashMap<>();
    String workflowName = null;

    List<PipelineStage.WorkflowVariable> pipelineStageVariables = new ArrayList<>();
    if (!StateType.APPROVAL.name().equals(stageElement.getType())) {
      workflowName = generateYamlAndGetNameForEnvStage(appId, stageElement, pipelineStageVariables);
    } else {
      generateYamlForApprovalStage(appId, stageElement, outputProperties);
    }

    RuntimeInputsConfig inputsConfig = stageElement.getRuntimeInputsConfig();
    RuntimeInputsConfig.Yaml yamlConfig = null;
    if (inputsConfig != null) {
      yamlConfig = RuntimeInputsConfig.Yaml.builder()
                       .timeout(inputsConfig.getTimeout())
                       .timeoutAction(inputsConfig.getTimeoutAction())
                       .runtimeInputVariables(inputsConfig.getRuntimeInputVariables())
                       .userGroupNames(getUserGroupNames(inputsConfig.getUserGroupIds(), accountId))
                       .build();
    }

    return Yaml.builder()
        .name(stageElement.getName())
        .stageName(bean.getName())
        .skipCondition(SkipCondition.getInstanceForAssertion(stageElement.getDisableAssertion()))
        .parallel(bean.isParallel())
        .type(stageElement.getType())
        .workflowName(workflowName)
        .workflowVariables(pipelineStageVariables)
        .properties(outputProperties.isEmpty() ? null : outputProperties)
        .runtimeInputs(yamlConfig)
        .build();
  }

  @VisibleForTesting
  void generateYamlForApprovalStage(
      String appId, PipelineStageElement stageElement, Map<String, Object> outputProperties) {
    Map<String, Object> properties = stageElement.getProperties();

    if (properties != null) {
      // Removing the disableAssertion field in approval stage properties as the expression is already displayed as
      // skipCondition
      properties.keySet().removeAll(irrelevantApprovalStateFields);
      if (ApprovalStateType.SERVICENOW.name().equals(properties.get(APPROVAL_STATE_TYPE_VARIABLE))) {
        Map<String, Object> snowParams =
            (Map<String, Object>) ((Map<String, Object>) properties.get(ApprovalStateKeys.approvalStateParams))
                .get("serviceNowApprovalParams");
        if (snowParams.containsKey("approval") || snowParams.containsKey("rejection")) {
          snowParams.keySet().removeAll(Arrays.asList("approvalValue", "rejectionValue", "approvalField",
              "rejectionField", "approvalOperator", "rejectionOperator"));
        }
      }
      properties.forEach((name, value) -> {
        if (!shouldBeIgnored(name)) {
          if (USER_GROUPS.equals(name)) {
            outputProperties.put(name,
                ("userGroups".equals(name) && value instanceof List)
                    ? getUserGroupNames((List<String>) value, appService.getAccountIdByAppId(appId))
                    : value);
          } else {
            approvalStepYamlBuilder.convertIdToNameForKnownTypes(name, value, outputProperties, appId, properties);
          }
        }
      });
    }
  }

  private String generateYamlAndGetNameForEnvStage(
      String appId, PipelineStageElement stageElement, List<WorkflowVariable> pipelineStageVariables) {
    String workflowName;
    Map<String, Object> properties = stageElement.getProperties();
    notNullCheck("Pipeline stage element is null", properties, USER);

    String workflowId = (String) properties.get("workflowId");
    notNullCheck("Workflow id is null in stage properties", workflowId, USER);

    Workflow workflow = workflowService.readWorkflow(appId, workflowId);
    notNullCheck("Workflow id is null in stage properties", workflowId, USER);

    workflowName = workflow.getName();

    Map<String, String> workflowVariables = stageElement.getWorkflowVariables();
    notNullCheck("Pipeline stage element is null", workflowVariables, USER);

    List<Variable> userVariables = workflow.getOrchestrationWorkflow().getUserVariables();
    if (isEmpty(userVariables)) {
      userVariables = new ArrayList<>();
    }
    Map<String, Variable> nameVariableMap =
        userVariables.stream().collect(Collectors.toMap(Variable::getName, Function.identity()));
    for (Map.Entry<String, String> entry : workflowVariables.entrySet()) {
      Variable variable = nameVariableMap.get(entry.getKey());
      if (variable != null) {
        EntityType entityType = variable.obtainEntityType();
        WorkflowVariable workflowVariable = WorkflowVariable.builder()
                                                .name(entry.getKey())
                                                .entityType(entityType != null ? entityType.name() : null)
                                                .build();
        String entryValue = entry.getValue();
        String variableValue = workflowYAMLHelper.getWorkflowVariableValueYaml(
            appId, entryValue, entityType, stageElement.checkDisableAssertion());
        workflowVariable.setValue(variableValue != null ? variableValue : "");
        pipelineStageVariables.add(workflowVariable);
      }
    }
    return workflowName;
  }

  private List<String> getUserGroupNames(List<String> userGroupList, String accountId) {
    List<String> userGroupNames = new ArrayList<>();
    for (String userGroupId : userGroupList) {
      UserGroup userGroup = userGroupService.get(accountId, userGroupId);
      userGroupNames.add(userGroup != null ? userGroup.getName() : userGroupId);
    }
    return userGroupNames;
  }

  private boolean shouldBeIgnored(String name) {
    if (isEmpty(name)) {
      return true;
    }

    switch (name) {
      case "id":
      case "parentId":
      case "subWorkflowId":
      case "groupName":
        return true;
      default:
        return false;
    }
  }

  @Override
  public PipelineStage upsertFromYaml(ChangeContext<Yaml> changeContext, List<ChangeContext> changeSetContext)
      throws HarnessException {
    return toBean(changeContext);
  }

  @Override
  public Class getYamlClass() {
    return Yaml.class;
  }

  @Override
  public PipelineStage get(String accountId, String yamlFilePath) {
    throw new WingsException(ErrorCode.UNSUPPORTED_OPERATION_EXCEPTION);
  }

  @Override
  public void delete(ChangeContext<Yaml> changeContext) {
    // Do nothing
  }
}
