/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipelinestage.helper;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.pms.pipelinestage.step.PipelineStageStep.NESTED_CHAINING_ERROR;
import static io.harness.pms.pipelinestage.step.PipelineStageStep.NESTED_CHAINING_HINT;

import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.pms.data.PmsEngineExpressionService;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.execution.NodeExecution;
import io.harness.expression.common.ExpressionMode;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.data.stepparameters.PmsStepParameters;
import io.harness.pms.gitsync.PmsGitSyncHelper;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.mappers.ExecutionGraphMapper;
import io.harness.pms.pipeline.mappers.PipelineExecutionSummaryDtoMapper;
import io.harness.pms.pipeline.service.PMSPipelineTemplateHelper;
import io.harness.pms.pipelinestage.PipelineStageStepParameters;
import io.harness.pms.pipelinestage.PipelineStageStepParameters.PipelineStageStepParametersKeys;
import io.harness.pms.pipelinestage.outcome.PipelineStageOutcome;
import io.harness.pms.pipelinestage.v1.helper.PipelineStageHelperV1;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.dto.ChildExecutionDetailDTO;
import io.harness.pms.plan.execution.beans.dto.ChildExecutionDetailDTO.ChildExecutionDetailDTOBuilder;
import io.harness.pms.plan.execution.beans.dto.GraphLayoutNodeDTO;
import io.harness.pms.plan.execution.service.PMSExecutionService;
import io.harness.pms.rbac.PipelineRbacPermissions;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.PipelineVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.yaml.core.failurestrategy.FailureStrategyConfig;
import io.harness.yaml.core.failurestrategy.NGFailureActionTypeConstants;
import io.harness.yaml.utils.JsonPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PIPELINE)
public class PipelineStageHelper {
  @Inject private PMSPipelineTemplateHelper pmsPipelineTemplateHelper;
  @Inject private final PMSExecutionService pmsExecutionService;
  @Inject private final PmsGitSyncHelper pmsGitSyncHelper;
  @Inject private final AccessControlClient accessControlClient;
  @Inject private PmsEngineExpressionService pmsEngineExpressionService;
  @Inject private final PipelineStageHelperV1 pipelineStageHelperV1;

  private static String NESTED_ERROR_EXCEPTION_HINT = "Pipeline setup configuration issue for pipeline stage";
  private static String NESTED_ERROR_EXCEPTION =
      "The referred pipeline [%s] invokes a child pipeline, so it cannot be included within another pipeline. Nested Pipeline Chaining is not supported";
  private final List<String> actionTypeNotSupported = Arrays.asList(NGFailureActionTypeConstants.RETRY,
      NGFailureActionTypeConstants.PIPELINE_ROLLBACK, NGFailureActionTypeConstants.MANUAL_INTERVENTION);

  public void validateNestedChainedPipeline(PipelineEntity pipelineEntity, String stageName) {
    try {
      validateNestedChainedPipeline(pipelineEntity);
    } catch (Exception e) {
      log.error("Error during nested chaining validation ", e);
      throw NestedExceptionUtils.hintWithExplanationException(
          String.format(NESTED_ERROR_EXCEPTION_HINT, stageName), e.getMessage(), null);
    }
  }

  public void validateNestedChainedPipeline(
      PipelineEntity pipelineEntity, String stageName, String parentPipelineIdentifier) {
    try {
      validateNestedChainedPipeline(pipelineEntity);
    } catch (Exception e) {
      log.error("Error during nested chaining validation ", e);
      throw NestedExceptionUtils.hintWithExplanationException(
          String.format(NESTED_CHAINING_HINT, parentPipelineIdentifier),
          String.format(NESTED_CHAINING_ERROR, pipelineEntity.getIdentifier()));
    }
  }

  public void validateNestedChainedPipeline(PipelineEntity entity) {
    TemplateMergeResponseDTO templateMergeResponseDTO =
        pmsPipelineTemplateHelper.resolveTemplateRefsInPipeline(entity, "true");
    String pipelineVersion = entity.getHarnessVersion();
    switch (pipelineVersion) {
      case PipelineVersion.V0:
        containsPipelineStage(templateMergeResponseDTO.getMergedPipelineYaml());
        break;
      case PipelineVersion.V1:
        pipelineStageHelperV1.containsPipelineStage(templateMergeResponseDTO.getMergedPipelineYaml());
        break;
      default:
        throw new InvalidRequestException(String.format("Child pipeline version: %s not supported", pipelineVersion));
    }
  }

  private void containsPipelineStage(String yaml) {
    try {
      YamlField pipelineYamlField = YamlUtils.readTree(yaml);
      List<YamlNode> stages = pipelineYamlField.getNode()
                                  .getField(YAMLFieldNameConstants.PIPELINE)
                                  .getNode()
                                  .getField(YAMLFieldNameConstants.STAGES)
                                  .getNode()
                                  .asArray();
      for (YamlNode yamlNode : stages) {
        if (yamlNode.getField(YAMLFieldNameConstants.STAGE) != null) {
          containsPipelineStageInStageNode(yamlNode);
        } else if (yamlNode.getField(YAMLFieldNameConstants.PARALLEL) != null) {
          containsPipelineStageInParallelNode(yamlNode);
        }
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void containsPipelineStageInParallelNode(YamlNode yamlNode) {
    List<YamlNode> stageInParallel = yamlNode.getField(YAMLFieldNameConstants.PARALLEL).getNode().asArray();
    for (YamlNode stage : stageInParallel) {
      if (stage.getField(YAMLFieldNameConstants.STAGE) != null) {
        containsPipelineStageInStageNode(stage);
      } else {
        throw new InvalidRequestException("Parallel stages contains entity other than stage");
      }
    }
  }

  private void containsPipelineStageInStageNode(YamlNode yamlNode) {
    if (yamlNode.getField(YAMLFieldNameConstants.STAGE) != null
        && yamlNode.getField(YAMLFieldNameConstants.STAGE).getNode() != null
        && yamlNode.getField(YAMLFieldNameConstants.STAGE).getNode().getType().equals("Pipeline")) {
      throw new InvalidRequestException(
          String.format(NESTED_ERROR_EXCEPTION, yamlNode.getField(YAMLFieldNameConstants.STAGE).getNode().getName()));
    }
  }

  public void validateResource(
      AccessControlClient accessControlClient, Ambiance ambiance, PipelineStageStepParameters stepParameters) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(ambiance.getSetupAbstractions().get("accountId"),
                                                  stepParameters.getOrg(), stepParameters.getProject()),
        Resource.of("PIPELINE", stepParameters.getPipeline()), PipelineRbacPermissions.PIPELINE_EXECUTE);
  }

  public String getInputSetYaml(YamlField pipelineInputs, String pipelineVersion) {
    switch (pipelineVersion) {
      case PipelineVersion.V0:
        return getInputSetYaml(pipelineInputs);
      case PipelineVersion.V1:
        return pipelineStageHelperV1.getInputSet(pipelineInputs);
      default:
        throw new InvalidRequestException(String.format("Child pipeline version: %s not supported", pipelineVersion));
    }
  }

  public JsonNode getInputSetJsonNode(YamlField pipelineInputs, String pipelineVersion) {
    switch (pipelineVersion) {
      case PipelineVersion.V0:
        return getInputSetJsonNode(pipelineInputs);
      case PipelineVersion.V1:
        return pipelineStageHelperV1.getInputSetJsonNode(pipelineInputs);
      default:
        throw new InvalidRequestException(String.format("Child pipeline version: %s not supported", pipelineVersion));
    }
  }

  private String getInputSetYaml(YamlField pipelineInputs) {
    String inputSetYaml = "";
    if (pipelineInputs != null) {
      Map<String, JsonNode> map = getInputSetMapInternal(pipelineInputs);
      inputSetYaml = YamlUtils.writeYamlString(map);
    }
    return inputSetYaml;
  }

  private JsonNode getInputSetJsonNode(YamlField pipelineInputs) {
    JsonNode inputJsonNode = null;
    if (pipelineInputs != null) {
      Map<String, JsonNode> map = getInputSetMapInternal(pipelineInputs);
      inputJsonNode = JsonPipelineUtils.asTree(map);
    }
    return inputJsonNode;
  }

  private Map<String, JsonNode> getInputSetMapInternal(YamlField pipelineInputs) {
    // Deep copy is required to prevent any concurrentException as we are reading yaml in other places. This is caught
    // via PIE-8733
    JsonNode inputJsonNode = pipelineInputs.getNode().getCurrJsonNode().deepCopy();
    YamlUtils.removeUuid(inputJsonNode);
    Map<String, JsonNode> map = new HashMap<>();
    map.put(YAMLFieldNameConstants.PIPELINE, inputJsonNode);
    return map;
  }

  public ChildExecutionDetailDTO getChildGraph(String accountId, String childStageNodeId,
      EntityGitDetails entityGitDetails, NodeExecution nodeExecution, String stageNodeExecutionId) {
    String childExecutionId = nodeExecution.getExecutableResponses().get(0).getAsync().getCallbackIds(0);
    PmsStepParameters parameters = nodeExecution.getResolvedParams();

    String orgId = parameters.get(PipelineStageStepParametersKeys.org).toString();
    String projectId = parameters.get(PipelineStageStepParametersKeys.project).toString();
    return getChildGraph(
        accountId, childStageNodeId, entityGitDetails, childExecutionId, orgId, projectId, stageNodeExecutionId);
  }

  private ChildExecutionDetailDTO getChildGraph(String accountId, String childStageNodeId,
      EntityGitDetails entityGitDetails, String childExecutionId, String orgId, String projectId,
      String stageNodeExecutionId) {
    PipelineExecutionSummaryEntity executionSummaryEntityForChild =
        pmsExecutionService.getPipelineExecutionSummaryEntity(accountId, orgId, projectId, childExecutionId, false);

    // access control on child pipeline
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of("PIPELINE", executionSummaryEntityForChild.getPipelineIdentifier()),
        PipelineRbacPermissions.PIPELINE_VIEW);

    EntityGitDetails entityGitDetailsForChild;
    if (entityGitDetails == null) {
      entityGitDetailsForChild =
          pmsGitSyncHelper.getEntityGitDetailsFromBytes(executionSummaryEntityForChild.getGitSyncBranchContext());
    } else {
      entityGitDetailsForChild = executionSummaryEntityForChild.getEntityGitDetails();
    }

    return getChildGraph(childStageNodeId, childExecutionId, executionSummaryEntityForChild, entityGitDetailsForChild,
        stageNodeExecutionId);
  }

  private ChildExecutionDetailDTO getChildGraph(String childStageNodeId, String childExecutionId,
      PipelineExecutionSummaryEntity executionSummaryEntityForChild, EntityGitDetails entityGitDetailsForChild,
      String stageNodeExecutionId) {
    // Top graph for child execution
    ChildExecutionDetailDTOBuilder childGraphBuilder = ChildExecutionDetailDTO.builder().pipelineExecutionSummary(
        PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntityForChild, entityGitDetailsForChild));

    // if child stage node id is not null, add bottom graph for child execution
    if (childStageNodeId != null) {
      childGraphBuilder.executionGraph(ExecutionGraphMapper.toExecutionGraph(
          pmsExecutionService.getOrchestrationGraph(childStageNodeId, childExecutionId, stageNodeExecutionId),
          executionSummaryEntityForChild));
    }
    return childGraphBuilder.build();
  }

  public boolean validateChildGraphToGenerate(Map<String, GraphLayoutNodeDTO> graphLayoutNodeDTO, String stageNodeId) {
    // Validates nodeType which should be Pipeline
    return graphLayoutNodeDTO.containsKey(stageNodeId)
        && graphLayoutNodeDTO.get(stageNodeId).getNodeType().equals(StepSpecTypeConstants.PIPELINE_STAGE);
  }

  public PipelineStageOutcome resolveOutputVariables(Map<String, ParameterField<String>> map, Ambiance ambiance) {
    Map<String, Object> resolvedMap = resolveOutputVariables(map);

    return new PipelineStageOutcome((Map<String, Object>) pmsEngineExpressionService.resolve(
        ambiance, resolvedMap, ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED));
  }

  public Map<String, Object> resolveOutputVariables(Map<String, ParameterField<String>> map) {
    Map<String, Object> resolvedMap = new HashMap<>();

    for (Map.Entry<String, ParameterField<String>> entry : map.entrySet()) {
      String expression;
      ParameterField<String> valueField = entry.getValue();
      if (valueField.getExpressionValue() != null) {
        expression = valueField.getExpressionValue();
      } else {
        expression = valueField.getValue();
      }

      resolvedMap.put(entry.getKey(), expression);
    }
    return resolvedMap;
  }

  public void validateFailureStrategy(ParameterField<List<FailureStrategyConfig>> failureStrategies) {
    if (ParameterField.isNotNull(failureStrategies) && isNotEmpty(failureStrategies.getValue())) {
      for (FailureStrategyConfig failureStrategyConfig : failureStrategies.getValue()) {
        if (actionTypeNotSupported.contains(failureStrategyConfig.getOnFailure().getAction().getType().getYamlName())) {
          throw new InvalidRequestException(String.format("Action %s is not supported in pipeline stage",
              failureStrategyConfig.getOnFailure().getAction().getType()));
        }
      }
    }
  }
}
