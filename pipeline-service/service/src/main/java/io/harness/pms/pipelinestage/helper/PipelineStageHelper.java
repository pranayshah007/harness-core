/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipelinestage.helper;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.gitcaching.GitCachingConstants.BOOLEAN_FALSE_VALUE;

import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.pms.data.PmsEngineExpressionService;
import io.harness.exception.InvalidRequestException;
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
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.dto.ChildExecutionDetailDTO;
import io.harness.pms.plan.execution.beans.dto.ChildExecutionDetailDTO.ChildExecutionDetailDTOBuilder;
import io.harness.pms.plan.execution.beans.dto.GraphLayoutNodeDTO;
import io.harness.pms.plan.execution.beans.dto.PipelineExecutionDetailDTO;
import io.harness.pms.plan.execution.beans.dto.PipelineExecutionDetailDTO.PipelineExecutionDetailDTOBuilder;
import io.harness.pms.plan.execution.service.PMSExecutionService;
import io.harness.pms.rbac.PipelineRbacPermissions;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.utils.YamlPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
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

  public void validateNestedChainedPipeline(PipelineEntity entity) {
    TemplateMergeResponseDTO templateMergeResponseDTO =
        pmsPipelineTemplateHelper.resolveTemplateRefsInPipeline(entity, BOOLEAN_FALSE_VALUE);

    containsPipelineStage(templateMergeResponseDTO.getMergedPipelineYaml());
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
      throw new InvalidRequestException("Nested pipeline is not supported");
    }
  }

  public void validateResource(
      AccessControlClient accessControlClient, Ambiance ambiance, PipelineStageStepParameters stepParameters) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(ambiance.getSetupAbstractions().get("accountId"),
                                                  stepParameters.getOrg(), stepParameters.getProject()),
        Resource.of("PIPELINE", stepParameters.getPipeline()), PipelineRbacPermissions.PIPELINE_EXECUTE);
  }

  public String getInputSetYaml(YamlField pipelineInputs) {
    String inputSetYaml = "";
    if (pipelineInputs != null) {
      JsonNode inputJsonNode = pipelineInputs.getNode().getCurrJsonNode();
      YamlUtils.removeUuid(inputJsonNode);
      Map<String, JsonNode> map = new HashMap<>();
      map.put(YAMLFieldNameConstants.PIPELINE, inputJsonNode);
      inputSetYaml = YamlPipelineUtils.writeYamlString(map);
    }
    return inputSetYaml;
  }

  public PipelineExecutionDetailDTO getResponseDTOWithChildGraph(String accountId, String childStageNodeId,
      PipelineExecutionSummaryEntity executionSummaryEntity, EntityGitDetails entityGitDetails,
      NodeExecution nodeExecution) {
    String childExecutionId = nodeExecution.getExecutableResponses().get(0).getAsync().getCallbackIds(0);
    PmsStepParameters parameters = nodeExecution.getResolvedParams();

    String orgId = parameters.get(PipelineStageStepParametersKeys.org).toString();
    String projectId = parameters.get(PipelineStageStepParametersKeys.project).toString();
    return getExecutionDetailDTO(
        accountId, childStageNodeId, executionSummaryEntity, entityGitDetails, childExecutionId, orgId, projectId);
  }

  private PipelineExecutionDetailDTO getExecutionDetailDTO(String accountId, String childStageNodeId,
      PipelineExecutionSummaryEntity executionSummaryEntity, EntityGitDetails entityGitDetails, String childExecutionId,
      String orgId, String projectId) {
    PipelineExecutionSummaryEntity executionSummaryEntityForChild =
        pmsExecutionService.getPipelineExecutionSummaryEntity(accountId, orgId, projectId, childExecutionId, false);

    // access control on child pipeline
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of("PIPELINE", executionSummaryEntityForChild.getPipelineIdentifier()),
        PipelineRbacPermissions.PIPELINE_VIEW);

    EntityGitDetails entityGitDetailsForChild;
    if (executionSummaryEntity.getEntityGitDetails() == null) {
      entityGitDetailsForChild =
          pmsGitSyncHelper.getEntityGitDetailsFromBytes(executionSummaryEntityForChild.getGitSyncBranchContext());
    } else {
      entityGitDetailsForChild = executionSummaryEntityForChild.getEntityGitDetails();
    }

    // Top graph of parent execution
    PipelineExecutionDetailDTOBuilder pipelineStageGraphBuilder =
        PipelineExecutionDetailDTO.builder().pipelineExecutionSummary(
            PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntity, entityGitDetails));

    ChildExecutionDetailDTO childGraph =
        getChildGraph(childStageNodeId, childExecutionId, executionSummaryEntityForChild, entityGitDetailsForChild);
    return pipelineStageGraphBuilder.childGraph(childGraph).build();
  }

  private ChildExecutionDetailDTO getChildGraph(String childStageNodeId, String childExecutionId,
      PipelineExecutionSummaryEntity executionSummaryEntityForChild, EntityGitDetails entityGitDetailsForChild) {
    // Top graph for child execution
    ChildExecutionDetailDTOBuilder childGraphBuilder = ChildExecutionDetailDTO.builder().pipelineExecutionSummary(
        PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntityForChild, entityGitDetailsForChild));

    // if child stage node id is not null, add bottom graph for child execution
    if (childStageNodeId != null) {
      childGraphBuilder.executionGraph(ExecutionGraphMapper.toExecutionGraph(
          pmsExecutionService.getOrchestrationGraph(childStageNodeId, childExecutionId, null),
          executionSummaryEntityForChild));
    }
    return childGraphBuilder.build();
  }

  public boolean validateGraphToGenerate(Map<String, GraphLayoutNodeDTO> graphLayoutNodeDTO, String stageNodeId) {
    // Validates nodeType which should be Pipeline
    return graphLayoutNodeDTO.containsKey(stageNodeId)
        && graphLayoutNodeDTO.get(stageNodeId).getNodeType().equals(StepSpecTypeConstants.PIPELINE_STAGE);
  }

  public PipelineStageOutcome resolveOutputVariables(
      Map<String, ParameterField<String>> map, NodeExecution nodeExecution) {
    Map<String, String> resolvedMap = new HashMap<>();

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

    return new PipelineStageOutcome((Map<String, Object>) pmsEngineExpressionService.resolve(
        nodeExecution.getAmbiance(), resolvedMap, ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED));
  }
}
