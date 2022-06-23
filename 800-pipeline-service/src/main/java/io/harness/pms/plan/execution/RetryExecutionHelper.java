/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanExecutionMetadataService;
import io.harness.engine.executions.retry.ExecutionInfo;
import io.harness.engine.executions.retry.RetryGroup;
import io.harness.engine.executions.retry.RetryHistoryResponseDto;
import io.harness.engine.executions.retry.RetryInfo;
import io.harness.engine.executions.retry.RetryLatestExecutionResponseDto;
import io.harness.engine.executions.retry.RetryStageInfo;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.execution.StagesExecutionMetadata;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.plan.IdentityPlanNode;
import io.harness.plan.Node;
import io.harness.plan.Plan;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.merger.YamlConfig;
import io.harness.pms.merger.fqn.FQN;
import io.harness.pms.merger.helpers.InputSetMergeHelper;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.service.PMSPipelineService;
import io.harness.pms.pipeline.service.PMSPipelineTemplateHelper;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.service.PMSExecutionService;
import io.harness.pms.plan.utils.PlanResourceUtility;
import io.harness.repositories.executions.PmsExecutionSummaryRespository;
import io.harness.template.yaml.TemplateRefHelper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class RetryExecutionHelper {
  private static final String LAST_STAGE_IDENTIFIER = "last_stage_identifier";
  private final NodeExecutionService nodeExecutionService;
  private final PlanExecutionMetadataService planExecutionMetadataService;
  private final PmsExecutionSummaryRespository pmsExecutionSummaryRespository;
  private final PMSPipelineService pmsPipelineService;
  private final PMSExecutionService pmsExecutionService;
  private final PMSPipelineTemplateHelper pmsPipelineTemplateHelper;

  public List<String> fetchOnlyFailedStages(List<RetryStageInfo> info, List<String> retryStagesIdentifier) {
    List<String> onlyFailedStage = new ArrayList<>();
    for (int i = 0; i < info.size(); i++) {
      RetryStageInfo stageInfo = info.get(i);
      String stageIdentifier = stageInfo.getIdentifier();
      if (!retryStagesIdentifier.contains(stageIdentifier)) {
        throw new InvalidRequestException("Run only failed stages is applicable only for failed parallel group stages");
      }
      if (isFailedStatus(stageInfo.getStatus())) {
        onlyFailedStage.add(stageInfo.getIdentifier());
      }
    }
    if (onlyFailedStage.size() == 0) {
      throw new InvalidRequestException("No failed stage found in parallel group");
    }
    return onlyFailedStage;
  }
  public List<String> fetchOnlyFailedStages(String previousExecutionId, List<String> retryStagesIdentifier) {
    RetryInfo retryInfo = getRetryInfo(getStageDetails(previousExecutionId));
    if (retryInfo != null) {
      List<RetryStageInfo> info = retryInfo.getGroups().get(retryInfo.getGroups().size() - 1).getInfo();
      return fetchOnlyFailedStages(info, retryStagesIdentifier);
    }
    throw new InvalidRequestException("Pipeline is updated, cannot resume");
  }

  public boolean isFailedStatus(ExecutionStatus status) {
    if (status.equals(ExecutionStatus.ABORTED) || status.equals(ExecutionStatus.FAILED)
        || status.equals(ExecutionStatus.EXPIRED) || status.equals(ExecutionStatus.APPROVAL_REJECTED)
        || status.equals(ExecutionStatus.APPROVALREJECTED)) {
      return true;
    }
    return false;
  }

  public RetryInfo validateRetry(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String planExecutionId) {
    Optional<PipelineEntity> updatedPipelineEntity =
        pmsPipelineService.get(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, false);

    if (!updatedPipelineEntity.isPresent()) {
      return RetryInfo.builder()
          .isResumable(false)
          .errorMessage(
              String.format("Pipeline with the given ID: %s does not exist or has been deleted", pipelineIdentifier))
          .build();
    }

    // Checking if this is the latest execution
    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity =
        pmsExecutionService.getPipelineExecutionSummaryEntity(
            accountId, orgIdentifier, projectIdentifier, planExecutionId, false);
    if (!pipelineExecutionSummaryEntity.isLatestExecution()) {
      return RetryInfo.builder()
          .isResumable(false)
          .errorMessage(
              "This execution is not the latest of all retried execution. You can only retry the latest execution.")
          .build();
    }

    boolean inTimeLimit =
        PlanResourceUtility.validateInTimeLimitForRetry(pipelineExecutionSummaryEntity.getCreatedAt());
    if (!inTimeLimit) {
      return RetryInfo.builder()
          .isResumable(false)
          .errorMessage("Execution is more than 30 days old. Cannot retry")
          .build();
    }

    String updatedPipeline = updatedPipelineEntity.get().getYaml();

    Optional<PlanExecutionMetadata> byPlanExecutionId =
        planExecutionMetadataService.findByPlanExecutionId(planExecutionId);
    if (!byPlanExecutionId.isPresent()) {
      return RetryInfo.builder()
          .isResumable(false)
          .errorMessage("No Plan Execution exists for id " + planExecutionId)
          .build();
    }
    PlanExecutionMetadata planExecutionMetadata = byPlanExecutionId.get();
    String executedPipeline = planExecutionMetadata.getYaml();
    TemplateMergeResponseDTO templateMergeResponseDTO = null;
    // if pipeline is having templates we need to use resolved yaml
    if (TemplateRefHelper.hasTemplateRef(updatedPipeline)) {
      templateMergeResponseDTO = pmsPipelineTemplateHelper.resolveTemplateRefsInPipeline(
          accountId, orgIdentifier, projectIdentifier, updatedPipeline);
      if (templateMergeResponseDTO != null) {
        updatedPipeline = isNotEmpty(templateMergeResponseDTO.getMergedPipelineYaml())
            ? templateMergeResponseDTO.getMergedPipelineYaml()
            : updatedPipeline;
      }
    }
    StagesExecutionMetadata stagesExecutionMetadata = planExecutionMetadata.getStagesExecutionMetadata();
    if (stagesExecutionMetadata != null && stagesExecutionMetadata.isStagesExecution()) {
      updatedPipeline =
          InputSetMergeHelper.removeNonRequiredStages(updatedPipeline, stagesExecutionMetadata.getStageIdentifiers());
    }
    return getRetryStages(updatedPipeline, executedPipeline, planExecutionId);
  }

  public boolean validateRetry(String updatedYaml, String executedYaml) {
    // compare fqn
    if (isEmpty(updatedYaml) || isEmpty(executedYaml)) {
      return false;
    }

    YamlConfig updatedConfig = new YamlConfig(updatedYaml);
    YamlConfig executedConfig = new YamlConfig(executedYaml);

    Map<FQN, Object> fqnToValueMapUpdatedYaml = updatedConfig.getFqnToValueMap();
    Map<FQN, Object> fqnToValueMapExecutedYaml = executedConfig.getFqnToValueMap();

    List<String> updateStageIdentifierList = new ArrayList<>();
    for (FQN fqn : fqnToValueMapUpdatedYaml.keySet()) {
      if (fqn.isStageIdentifier()) {
        updateStageIdentifierList.add(fqn.display());
      }
    }

    List<String> executedStageIdentifierList = new ArrayList<>();
    for (FQN fqn : fqnToValueMapExecutedYaml.keySet()) {
      if (fqn.isStageIdentifier()) {
        executedStageIdentifierList.add(fqn.display());
      }
    }

    if (!updateStageIdentifierList.equals(executedStageIdentifierList)) {
      return false;
    }
    return true;
  }

  public RetryInfo getRetryStages(String updatedYaml, String executedYaml, String planExecutionId) {
    if (isEmpty(planExecutionId)) {
      return null;
    }
    boolean isResumable = validateRetry(updatedYaml, executedYaml);
    if (!isResumable) {
      return RetryInfo.builder()
          .isResumable(isResumable)
          .errorMessage("Adding, deleting or changing the name of the stage identifier is not allowed for retrying")
          .build();
    }
    List<RetryStageInfo> stageDetails = getStageDetails(planExecutionId);

    return getRetryInfo(stageDetails);
  }

  public RetryInfo getRetryInfo(List<RetryStageInfo> stageDetails) {
    HashMap<String, List<RetryStageInfo>> mapNextIdWithStageInfo = new LinkedHashMap<>();
    for (RetryStageInfo stageDetail : stageDetails) {
      String nextId = stageDetail.getNextId();
      if (isEmpty(nextId)) {
        nextId = LAST_STAGE_IDENTIFIER;
      }
      List<RetryStageInfo> stageList = mapNextIdWithStageInfo.getOrDefault(nextId, new ArrayList<>());
      stageList.add(stageDetail);
      mapNextIdWithStageInfo.put(nextId, stageList);
    }
    List<RetryGroup> retryGroupList = new ArrayList<>();
    for (Map.Entry<String, List<RetryStageInfo>> entry : mapNextIdWithStageInfo.entrySet()) {
      retryGroupList.add(RetryGroup.builder().info(entry.getValue()).build());
    }
    return RetryInfo.builder().isResumable(true).groups(retryGroupList).build();
  }

  public List<RetryStageInfo> getStageDetails(String planExecutionId) {
    return nodeExecutionService.getStageDetailFromPlanExecutionId(planExecutionId);
  }

  public String retryProcessedYaml(String previousProcessedYaml, String currentProcessedYaml, List<String> retryStages,
      List<String> identifierOfSkipStages) throws IOException {
    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    JsonNode previousRootJsonNode = mapper.readTree(previousProcessedYaml);
    JsonNode currentRootJsonNode = mapper.readTree(currentProcessedYaml);

    if (previousRootJsonNode == null || currentRootJsonNode == null) {
      return currentProcessedYaml;
    }
    int stageCounter = 0;
    JsonNode stagesNode = previousRootJsonNode.get("pipeline").get("stages");
    for (JsonNode stage : stagesNode) {
      // stage is not a part of parallel group
      if (stage.get("stage") != null) {
        // if the stage does not belongs to the retry stages and is to be skipped, copy the stage node from the
        // previous processed yaml
        JsonNode previousExecutionStageNodeJson = stage.get("stage");
        String stageIdentifier = previousExecutionStageNodeJson.get("identifier").textValue();
        if (!retryStages.contains(stageIdentifier)) {
          identifierOfSkipStages.add(stageIdentifier);
          ((ArrayNode) currentRootJsonNode.get("pipeline").get("stages")).set(stageCounter, stage);
          stageCounter = stageCounter + 1;
        } else {
          // need to copy only the uuid of the stage to be retry.
          JsonNode currentResumableStagejsonNode =
              currentRootJsonNode.get("pipeline").get("stages").get(stageCounter).get("stage");
          ((ObjectNode) currentResumableStagejsonNode).set("__uuid", previousExecutionStageNodeJson.get("__uuid"));
          // here onwards we need to retry the pipeline, no further copy of nodes required
          break;
        }
      } else {
        // parallel group
        if (!isRetryStagesInParallelStages(stage.get("parallel"), retryStages, identifierOfSkipStages)) {
          // if the parallel group does not contain the retry stages, copy the whole parallel node
          ((ArrayNode) currentRootJsonNode.get("pipeline").get("stages")).set(stageCounter, stage);
          stageCounter = stageCounter + 1;
        } else {
          // replace only those stages that needs to be skipped
          ((ArrayNode) currentRootJsonNode.get("pipeline").get("stages"))
              .set(stageCounter,
                  replaceStagesInParallelGroup(stage.get("parallel"), retryStages,
                      currentRootJsonNode.get("pipeline").get("stages").get(stageCounter), identifierOfSkipStages));

          // replacing uuid for parallel node
          ((ObjectNode) ((ArrayNode) currentRootJsonNode.get("pipeline").get("stages")).get(stageCounter))
              .set("__uuid", stage.get("__uuid"));

          break;
        }
      }
    }
    return currentRootJsonNode.toString();
  }

  private JsonNode replaceStagesInParallelGroup(JsonNode parallelStage, List<String> retryStages,
      JsonNode currentParallelStageNode, List<String> identifierOfSkipStages) {
    int stageCounter = 0;
    for (JsonNode stageNode : parallelStage) {
      String stageIdentifier = stageNode.get("stage").get("identifier").textValue();
      if (!retryStages.contains(stageIdentifier)) {
        identifierOfSkipStages.add(stageIdentifier);
        ((ArrayNode) currentParallelStageNode.get("parallel")).set(stageCounter, stageNode);
      } else {
        // replace only the uuid of the retry parallel stage
        JsonNode currentResumableStagejsonNode =
            currentParallelStageNode.get("parallel").get(stageCounter).get("stage");
        ((ObjectNode) currentResumableStagejsonNode).set("__uuid", stageNode.get("stage").get("__uuid"));
      }
      stageCounter++;
    }

    return currentParallelStageNode;
  }

  private boolean isRetryStagesInParallelStages(
      JsonNode parallelStage, List<String> retryStages, List<String> identifierOfSkipStages) {
    List<String> stagesIdentifierInParallelNode = new ArrayList<>();
    for (JsonNode stageNode : parallelStage) {
      String stageIdentifier = stageNode.get("stage").get("identifier").textValue();
      stagesIdentifierInParallelNode.add(stageIdentifier);
      if (retryStages.contains(stageIdentifier)) {
        return true;
      }
    }
    /*
    This whole parallel node will get copied. We need to copy the stage identifier in identifierForSkipStages
     */
    identifierOfSkipStages.addAll(stagesIdentifierInParallelNode);
    return false;
  }

  public Plan transformPlan(Plan plan, List<String> identifierOfSkipStages, String previousExecutionId) {
    List<Node> planNodes = plan.getPlanNodes();

    /*
    Fetching stageFqn from previousExecutionId and stage
     */
    // TODO: add a condition: stagesFqn size should be equal to the size of identifierOfSkipStages
    List<String> stagesFqn =
        nodeExecutionService.fetchStageFqnFromStageIdentifiers(previousExecutionId, identifierOfSkipStages);

    /*
    NodeExecutionUuid -> Node
     */
    List<Node> updatedPlanNodes = new ArrayList<>();
    Map<String, Node> nodeUuidToNodeExecutionUuid =
        nodeExecutionService.mapNodeExecutionIdWithPlanNodeForGivenStageFQN(previousExecutionId, stagesFqn);

    // filtering nodes which need to be resumed/retried
    updatedPlanNodes =
        planNodes.stream().filter(node -> !stagesFqn.contains(node.getStageFqn())).collect(Collectors.toList());

    // converting planNode to IdentityNode
    List<Node> finalUpdatedPlanNodes = updatedPlanNodes;
    nodeUuidToNodeExecutionUuid.forEach((nodeExecutionUuid, planNode)
                                            -> finalUpdatedPlanNodes.add(IdentityPlanNode.mapPlanNodeToIdentityNode(
                                                planNode, planNode.getStepType(), nodeExecutionUuid)));

    return Plan.builder()
        .uuid(plan.getUuid())
        .planNodes(finalUpdatedPlanNodes)
        .startingNodeId(plan.getStartingNodeId())
        .setupAbstractions(plan.getSetupAbstractions())
        .graphLayoutInfo(plan.getGraphLayoutInfo())
        .validUntil(plan.getValidUntil())
        .valid(plan.isValid())
        .errorResponse(plan.getErrorResponse())
        .build();
  }

  public RetryHistoryResponseDto getRetryHistory(String rootParentId) {
    List<PipelineExecutionSummaryEntity> entities =
        pmsExecutionSummaryRespository.fetchPipelineSummaryEntityFromRootParentId(rootParentId);

    if (entities.size() <= 1) {
      return RetryHistoryResponseDto.builder().errorMessage("Nothing to show in retry history").build();
    }
    List<ExecutionInfo> executionInfos = fetchExecutionInfoFromPipelineEntities(entities);

    String latestRetryExecutionId = executionInfos.get(0).getUuid();

    return RetryHistoryResponseDto.builder()
        .executionInfos(executionInfos)
        .latestExecutionId(latestRetryExecutionId)
        .build();
  }

  public RetryLatestExecutionResponseDto getRetryLatestExecutionId(String rootParentId) {
    List<PipelineExecutionSummaryEntity> entities =
        pmsExecutionSummaryRespository.fetchPipelineSummaryEntityFromRootParentId(rootParentId);

    if (entities.size() <= 1) {
      return RetryLatestExecutionResponseDto.builder().errorMessage("This is not a part of retry execution").build();
    }

    String latestRetryExecutionId = entities.get(0).getPlanExecutionId();

    return RetryLatestExecutionResponseDto.builder().latestExecutionId(latestRetryExecutionId).build();
  }

  private List<ExecutionInfo> fetchExecutionInfoFromPipelineEntities(
      List<PipelineExecutionSummaryEntity> summaryEntityList) {
    return summaryEntityList.stream()
        .map(entity -> {
          return ExecutionInfo.builder()
              .uuid(entity.getPlanExecutionId())
              .startTs(entity.getStartTs())
              .status(entity.getStatus())
              .endTs(entity.getEndTs())
              .build();
        })
        .collect(Collectors.toList());
  }
}
