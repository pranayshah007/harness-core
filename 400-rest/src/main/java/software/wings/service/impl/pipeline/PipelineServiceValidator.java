/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.pipeline;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.RepairActionCode.isPipelineRuntimeTimeoutAction;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.expression.ExpressionEvaluator.matchesVariablePattern;
import static io.harness.validation.Validator.nullCheckForInvalidRequest;

import static software.wings.sm.StateType.APPROVAL;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.ExplanationException;
import io.harness.exception.HintException;
import io.harness.exception.InvalidRequestException;

import software.wings.beans.EntityType;
import software.wings.beans.Pipeline;
import software.wings.beans.PipelineStage;
import software.wings.beans.PipelineStage.PipelineStageElement;
import software.wings.beans.RuntimeInputsConfig;
import software.wings.beans.Variable;
import software.wings.service.intfc.UserGroupService;
import software.wings.sm.states.ApprovalState;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

@OwnedBy(CDC)
@Singleton
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class PipelineServiceValidator {
  @Inject UserGroupService userGroupService;

  public boolean validateRuntimeInputsConfig(
      PipelineStageElement pipelineStageElement, String accountId, List<Variable> workflowVariables) {
    RuntimeInputsConfig runtimeInputsConfig = pipelineStageElement.getRuntimeInputsConfig();
    if (isEmpty(pipelineStageElement.getWorkflowVariables())) {
      pipelineStageElement.setRuntimeInputsConfig(null);
    }
    if (isEmpty(pipelineStageElement.getWorkflowVariables()) || runtimeInputsConfig == null
        || pipelineStageElement.checkDisableAssertion()) {
      return true;
    }

    if (isNotEmpty(runtimeInputsConfig.getRuntimeInputVariables())) {
      if (runtimeInputsConfig.getTimeout() < 60000) {
        throw new InvalidRequestException("Timeout value should be greater than 1 minute", USER);
      }
      nullCheckForInvalidRequest(runtimeInputsConfig.getTimeoutAction(), "Timeout Action cannot be null", USER);
      if (!isPipelineRuntimeTimeoutAction(runtimeInputsConfig.getTimeoutAction())) {
        throw new InvalidRequestException(
            "Timeout Action should be one of END_EXECUTION or CONTINUE_WITH_DEFAULTS", USER);
      }
      List<String> userGroupIds = runtimeInputsConfig.getUserGroupIds();
      if (isEmpty(userGroupIds)) {
        throw new InvalidRequestException("User groups should be present for Notification", USER);
      }
      for (String uid : userGroupIds) {
        if (userGroupService.get(accountId, uid) == null) {
          throw new InvalidRequestException("User group not found for given Id: " + uid, USER);
        }
      }
      validatePipelineStageRuntimeVariables(pipelineStageElement, workflowVariables);
    }
    return true;
  }

  private void validatePipelineStageRuntimeVariables(
      PipelineStageElement pipelineStageElement, List<Variable> workflowVariables) {
    Map<String, String> pseVariableValues = pipelineStageElement.getWorkflowVariables();
    List<String> runtimeVariables = pipelineStageElement.getRuntimeInputsConfig().getRuntimeInputVariables();
    runtimeVariables.removeIf(variable -> !pseVariableValues.containsKey(variable));
    String missing =
        runtimeVariables.stream().filter(variable -> !pseVariableValues.containsKey(variable)).findAny().orElse(null);
    if (missing != null) {
      throw new InvalidRequestException(String.format("Variable %s is marked runtime and is not provided", missing));
    }
    for (Map.Entry<String, String> variable : pseVariableValues.entrySet()) {
      String variableName = variable.getKey();
      if (runtimeVariables.contains(variableName)) {
        Variable workflowVar =
            workflowVariables.stream().filter(t -> t.getName().equals(variableName)).findFirst().orElse(null);
        if (workflowVar != null) {
          EntityType entityType = workflowVar.obtainEntityType();

          if (workflowVar.isFixed()) {
            throw new InvalidRequestException(
                String.format("Variable %s is a fixed Variable, Cannot be marked runtime", variableName));
          }

          if (!matchesVariablePattern(variable.getValue())) {
            throw new InvalidRequestException(
                String.format("Variable %s is marked runtime but the value isnt a valid expression", variableName));
          }

          if (entityType != null) {
            if (EntityType.SERVICE != entityType && EntityType.INFRASTRUCTURE_DEFINITION != entityType) {
              if (isNotEmpty(workflowVar.obtainRelatedField())) {
                List<String> relatedFields =
                    new ArrayList<>(Arrays.asList(workflowVar.obtainRelatedField().split(",")));
                if (isNotEmpty(relatedFields) && !runtimeVariables.containsAll(relatedFields)) {
                  relatedFields.removeIf(runtimeVariables::contains);
                  throw new InvalidRequestException(
                      String.format("Variable%s %s should be runtime as %s is marked runtime",
                          relatedFields.size() > 1 ? "s" : "", String.join(", ", relatedFields), variableName));
                }
              }
            }
          } else {
            if (variable.getValue().contains(".")) {
              throw new InvalidRequestException(String.format(
                  "Non entity var %s is marked Runtime, the value should be a new variable expression", variableName));
            }
          }
        }
      }
    }
  }

  public static void validateUserGroupExpression(Pipeline pipeline) {
    List<PipelineStage> pipelineStages = pipeline.getPipelineStages();
    if (pipelineStages != null) {
      for (PipelineStage pipelineStage : pipelineStages) {
        for (PipelineStageElement pse : pipelineStage.getPipelineStageElements()) {
          if (APPROVAL.name().equals(pse.getType())) {
            Map<String, Object> properties = pse.getProperties();
            if ((boolean) properties.getOrDefault("userGroupAsExpression", false)) {
              String userGroupExpression = (String) properties.get("userGroupExpression");
              if (isEmpty(userGroupExpression)) {
                throw new InvalidRequestException("User group expression is set but value is not provided", USER);
              }
              if (!matchesVariablePattern(userGroupExpression)) {
                throw new InvalidRequestException("User group expression: [" + userGroupExpression
                        + "] not in proper format, should start with ${ and end with }, only a-zA-Z0-9_- allowed",
                    USER);
              }
            }
          }
        }
      }
    }
  }

  public static boolean validateTemplateExpressions(Pipeline pipeline) {
    List<PipelineStage> pipelineStages = pipeline.getPipelineStages();
    if (pipelineStages != null) {
      for (PipelineStage pipelineStage : pipelineStages) {
        for (PipelineStageElement pse : pipelineStage.getPipelineStageElements()) {
          if (APPROVAL.name().equals(pse.getType())) {
            Map<String, Object> properties = pse.getProperties();
            List<Map<String, Object>> templateExpressions = null;
            Object obj = properties.get("templateExpressions");
            if (obj instanceof List) {
              templateExpressions = (List<Map<String, Object>>) obj;
            }
            if (!isEmpty(templateExpressions)) {
              for (Map<String, Object> templateExpression : templateExpressions) {
                if (templateExpression != null) {
                  String expression = (String) templateExpression.get("expression");
                  if (!matchesVariablePattern(expression)) {
                    throw new InvalidRequestException("Template variable:[" + expression
                            + "] not in proper format ,should start with ${ and end with }, only a-zA-Z0-9_- allowed",
                        USER);
                  }
                }
              }
            }
          }
        }
      }
    }
    return true;
  }

  public static void checkUniqueApprovalPublishedVariable(Pipeline pipeline) {
    if (isEmpty(pipeline.getPipelineStages())) {
      return;
    }
    Map<String, String> publishedVarToStage = new HashMap<>();
    for (PipelineStage pipelineStage : pipeline.getPipelineStages()) {
      PipelineStageElement stageElement = pipelineStage.getPipelineStageElements().get(0);
      if (APPROVAL.name().equals(stageElement.getType())) {
        String sweepingOutputName = ApprovalState.fetchAndTrimSweepingOutputName(stageElement.getProperties());
        if (isNotEmpty(sweepingOutputName)) {
          if (publishedVarToStage.containsKey(sweepingOutputName)) {
            throw new InvalidRequestException(
                format(
                    "You cannot use the same Publish Variable Name [%s] in multiple Approval stages. Publish Variable Name [%s] already used in stage [%s]",
                    sweepingOutputName, sweepingOutputName, publishedVarToStage.get(sweepingOutputName)),
                prepareExplanation(), USER);
          }
          publishedVarToStage.put(sweepingOutputName, pipelineStage.getName());
        }
      }
    }
  }

  @NotNull
  private static ExplanationException prepareExplanation() {
    return new ExplanationException("Each Publish Variable Name is used to distinguish one stage’s variables.",
        new HintException("Give each Approval stage a unique Publish Variable Name"));
  }
}
