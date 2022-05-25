/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.pipeline;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.sm.StateType.ENV_LOOP_STATE;
import static software.wings.sm.StateType.ENV_STATE;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;

import software.wings.beans.EntityType;
import software.wings.beans.Pipeline;
import software.wings.beans.PipelineStage;
import software.wings.beans.PipelineStage.PipelineStageElement;
import software.wings.beans.RuntimeInputsConfig;
import software.wings.beans.Variable;
import software.wings.beans.Workflow;
import software.wings.expression.ManagerExpressionEvaluator;
import software.wings.sm.states.EnvState.EnvStateKeys;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.utils.URIBuilder;

@OwnedBy(CDC)
@Slf4j
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class PipelineServiceHelper {
  private PipelineServiceHelper() {}

  public static void updateLoopingInfo(
      PipelineStage pipelineStage, Workflow workflow, List<String> infraDefinitionIds) {
    PipelineStageElement pipelineStageElement = pipelineStage.getPipelineStageElements().get(0);
    if (pipelineStageElement.checkDisableAssertion()) {
      return;
    }
    List<Variable> userVariables = workflow.getOrchestrationWorkflow().getUserVariables();
    if (EmptyPredicate.isEmpty(userVariables)) {
      return;
    }
    List<Variable> infraDefVariables = userVariables.stream()
                                           .filter(t -> EntityType.INFRASTRUCTURE_DEFINITION == t.obtainEntityType())
                                           .collect(Collectors.toList());
    if (EmptyPredicate.isEmpty(infraDefVariables) || infraDefVariables.size() > 1) {
      return;
    }

    RuntimeInputsConfig runtimeInputsConfig = pipelineStageElement.getRuntimeInputsConfig();
    String infraVarNameInPipelineStage = infraDefVariables.get(0).getName();

    if (runtimeInputsConfig != null && isNotEmpty(runtimeInputsConfig.getRuntimeInputVariables())
        && runtimeInputsConfig.getRuntimeInputVariables().contains(infraVarNameInPipelineStage)) {
      pipelineStage.setLooped(true);
      pipelineStage.setLoopedVarName(infraVarNameInPipelineStage);
      return;
    }

    String infraValueInPipelineStage = pipelineStageElement.getWorkflowVariables().get(infraVarNameInPipelineStage);
    if (EmptyPredicate.isEmpty(infraValueInPipelineStage)) {
      throw new InvalidRequestException(
          "No value supplied in pipeline for infra variable: " + infraVarNameInPipelineStage);
    }
    if (infraValueInPipelineStage.contains(",")) {
      pipelineStage.setLooped(true);
      pipelineStage.setLoopedVarName(infraVarNameInPipelineStage);
      List<String> infraIdsInLoop = Arrays.asList(infraValueInPipelineStage.trim().split("\\s*,\\s*"));
      infraIdsInLoop.stream().filter(infraId -> !infraDefinitionIds.contains(infraId)).forEach(infraDefinitionIds::add);
    }
  }

  public static void updatePipelineWithLoopedState(Pipeline pipeline) {
    if (pipeline.getInfraDefinitionIds() == null) {
      pipeline.setInfraDefinitionIds(new ArrayList<>());
    }
    for (PipelineStage pipelineStage : pipeline.getPipelineStages()) {
      if (pipelineStage.isLooped()) {
        PipelineStageElement pse = pipelineStage.getPipelineStageElements().get(0);
        if (ENV_STATE.getType().equals(pse.getType())) {
          String varLooped = pipelineStage.getLoopedVarName();
          Map<String, String> pipelineStageVariableValues = pse.getWorkflowVariables();
          RuntimeInputsConfig runtimeInputsConfig = pse.getRuntimeInputsConfig();
          List<String> runtimeVariablesInStage =
              runtimeInputsConfig != null ? runtimeInputsConfig.getRuntimeInputVariables() : new ArrayList<>();

          List<String> loopedValues = new ArrayList<>();
          // In case an Infra var is marked runtime, we assume the stage to be looped.
          // The default value can be empty, or a single value as well.
          if (isNotEmpty(runtimeVariablesInStage) && runtimeVariablesInStage.contains(varLooped)) {
            if (isNotEmpty(pipelineStageVariableValues) && pipelineStageVariableValues.containsKey(varLooped)) {
              String defaultValue = pipelineStageVariableValues.get(varLooped);
              if (isNotEmpty(defaultValue)) {
                loopedValues = Arrays.asList(pipelineStageVariableValues.get(varLooped).trim().split("\\s*,\\s*"));
              }
            }
          } else {
            if (EmptyPredicate.isEmpty(pipelineStageVariableValues)
                || !pipelineStageVariableValues.containsKey(varLooped)
                || !pipelineStageVariableValues.get(varLooped).contains(",")) {
              throw new InvalidRequestException("Pipeline stage marked as loop, but doesnt have looping config");
            }
            loopedValues = Arrays.asList(pipelineStageVariableValues.get(varLooped).trim().split("\\s*,\\s*"));
          }
          pse.setType(ENV_LOOP_STATE.getType());
          pse.getProperties().put("loopedValues", loopedValues);
          pse.getProperties().put("loopedVarName", varLooped);
          pipeline.getInfraDefinitionIds().addAll(loopedValues);
        }
      }
    }
  }

  public static List<String> getEnvironmentIdsForParallelIndex(Pipeline pipeline, int parallelIndex) {
    List<String> envIds = new ArrayList<>();
    boolean envIdsCollected = false;
    for (PipelineStage pipelineStage : pipeline.getPipelineStages()) {
      if (isNotEmpty(pipelineStage.getPipelineStageElements())) {
        for (PipelineStageElement pse : pipelineStage.getPipelineStageElements()) {
          if (pse.getParallelIndex() == parallelIndex) {
            String envId = resolveEnvIdForPipelineStage(
                pse.getProperties(), pse.getWorkflowVariables(), pipeline.getPipelineVariables());
            if (envId != null && !ManagerExpressionEvaluator.matchesVariablePattern(envId)) {
              envIds.add(envId);
            }
          } else if (pse.getParallelIndex() > parallelIndex) {
            envIdsCollected = true;
            break;
          }
        }
        if (envIdsCollected) {
          break;
        }
      }
    }
    return envIds;
  }

  public static String resolveEnvIdForPipelineStage(Map<String, Object> pipelineStageElementProperties,
      Map<String, String> workflowVariables, List<Variable> pipelineVariables) {
    String envId = (String) pipelineStageElementProperties.get(EnvStateKeys.envId);
    if (!ManagerExpressionEvaluator.matchesVariablePattern(envId)) {
      return envId;
    }
    if (pipelineVariables == null) {
      return null;
    }
    String variableValue = envId.substring(2, envId.length() - 1);
    String workflowVariableName = pipelineVariables.stream()
                                      .filter(var -> var.getName().equals(variableValue))
                                      .findFirst()
                                      .map(Variable::getValue)
                                      .orElse("");
    return workflowVariables.get(workflowVariableName);
  }

  public static String generatePipelineExecutionUrl(
      String accountId, String appId, String pipelineExecutionId, String baseUrl) {
    return buildAbsoluteUrl(format("/account/%s/app/%s/pipeline-execution/%s/workflow-execution/undefined/details",
                                accountId, appId, pipelineExecutionId),
        baseUrl);
  }

  public static String buildAbsoluteUrl(String fragment, String baseUrl) {
    if (!baseUrl.endsWith("/")) {
      baseUrl += "/";
    }
    try {
      URIBuilder uriBuilder = new URIBuilder(baseUrl);
      uriBuilder.setFragment(fragment);
      return uriBuilder.toString();
    } catch (URISyntaxException e) {
      log.error("Bad URI syntax", e);
      return baseUrl;
    }
  }
}
