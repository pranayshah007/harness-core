/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanExecutionMetadataService;
import io.harness.engine.executions.retry.ExecutionInfo;
import io.harness.engine.executions.retry.RetryGroup;
import io.harness.engine.executions.retry.RetryHistoryResponseDto;
import io.harness.engine.executions.retry.RetryInfo;
import io.harness.engine.executions.retry.RetryLatestExecutionResponseDto;
import io.harness.engine.executions.retry.RetryStageInfo;
import io.harness.engine.executions.retry.RetryStagesMetadataDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.execution.RetryStagesMetadata;
import io.harness.execution.StagesExecutionMetadata;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.plan.IdentityPlanNode;
import io.harness.plan.Node;
import io.harness.plan.Plan;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.merger.YamlConfig;
import io.harness.pms.merger.fqn.FQN;
import io.harness.pms.merger.helpers.InputSetMergeHelper;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.mappers.GitXCacheMapper;
import io.harness.pms.pipeline.service.PMSPipelineService;
import io.harness.pms.pipeline.service.PMSPipelineTemplateHelper;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.service.PMSExecutionService;
import io.harness.pms.plan.utils.PlanResourceUtility;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlUtils;
import io.harness.repositories.executions.PmsExecutionSummaryRepository;
import io.harness.template.yaml.TemplateRefHelper;
import io.harness.utils.PipelineGitXHelper;
import io.harness.utils.PipelineYamlUtils;

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
import org.springframework.data.util.CloseableIterator;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_GITX, HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class RetryExecutionHelper {
  private static final String LAST_STAGE_IDENTIFIER = "last_stage_identifier";
  private final NodeExecutionService nodeExecutionService;
  private final PlanExecutionMetadataService planExecutionMetadataService;
  private final PmsExecutionSummaryRepository pmsExecutionSummaryRespository;
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
    return status.equals(ExecutionStatus.ABORTED) || status.equals(ExecutionStatus.FAILED)
        || status.equals(ExecutionStatus.EXPIRED) || status.equals(ExecutionStatus.APPROVAL_REJECTED)
        || status.equals(ExecutionStatus.APPROVALREJECTED);
  }

  public RetryInfo validateRetry(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String planExecutionId, String loadFromCache) {
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

    if (EmptyPredicate.isNotEmpty(pipelineExecutionSummaryEntity.getRollbackModeExecutionId())) {
      return RetryInfo.builder()
          .isResumable(false)
          .errorMessage("This execution has undergone Pipeline Rollback, and hence cannot be retried.")
          .build();
    }

    PipelineGitXHelper.setupEntityDetails(pipelineExecutionSummaryEntity.getEntityGitDetails());

    Optional<PipelineEntity> optionalPipelineEntity =
        pmsPipelineService.getPipeline(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, false, false,
            false, GitXCacheMapper.parseLoadFromCacheHeaderParam(loadFromCache));
    if (optionalPipelineEntity.isEmpty()) {
      return RetryInfo.builder()
          .isResumable(false)
          .errorMessage(
              String.format("Pipeline with the given ID: %s does not exist or has been deleted", pipelineIdentifier))
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

    String updatedPipeline = optionalPipelineEntity.get().getYaml();

    Optional<PlanExecutionMetadata> byPlanExecutionId =
        planExecutionMetadataService.findByPlanExecutionId(planExecutionId);
    if (byPlanExecutionId.isEmpty()) {
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
          accountId, orgIdentifier, projectIdentifier, updatedPipeline, loadFromCache);
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
      List<String> identifierOfSkipStages, String pipelineVersion) throws IOException {
    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    JsonNode previousRootJsonNode = mapper.readTree(previousProcessedYaml);
    JsonNode currentRootJsonNode = mapper.readTree(currentProcessedYaml);

    if (previousRootJsonNode == null || currentRootJsonNode == null) {
      return currentProcessedYaml;
    }
    int stageCounter = 0;
    JsonNode stagesNode = PipelineYamlUtils.getStagesNodeFromRootNode(previousRootJsonNode, pipelineVersion);
    // When strategy is defined in the stage, in that case we might not run some stages(under the strategy for that
    // stage). So we need to update the uuid for strategy node and next node.
    boolean isStrategyNodeProcessed = false;
    for (JsonNode stage : stagesNode) {
      // stage is not a part of parallel group
      if (!PipelineYamlUtils.isParallelNode(stage, pipelineVersion)) {
        // if the stage does not belongs to the retry stages and is to be skipped, copy the stage node from the
        // previous processed yaml
        String stageIdentifier = PipelineYamlUtils.getIdentifierFromStageNode(stage, pipelineVersion);
        if (!retryStages.contains(stageIdentifier) && !isStrategyNodeProcessed) {
          identifierOfSkipStages.add(stageIdentifier);
          ((ArrayNode) PipelineYamlUtils.getStagesNodeFromRootNode(currentRootJsonNode, pipelineVersion))
              .set(stageCounter, stage);
          stageCounter = stageCounter + 1;
        } else {
          // need to copy only the uuid of the stage to be retry.
          JsonNode currentResumableStagejsonNode =
              PipelineYamlUtils.getStagesNodeFromRootNode(currentRootJsonNode, pipelineVersion).get(stageCounter);

          // if this is true then pipeline is being retried from previous stage. And previous node had the strategy
          // defined. So we just need to replace the UUID of stage node.
          if (isStrategyNodeProcessed) {
            ((ObjectNode) PipelineYamlUtils.getStageNodeFromStagesElement(
                 currentResumableStagejsonNode, pipelineVersion))
                .set(YAMLFieldNameConstants.UUID,
                    PipelineYamlUtils.getStageNodeFromStagesElement(stage, pipelineVersion)
                        .get(YAMLFieldNameConstants.UUID));
            break;
          }
          // Replacing all the UUIDs under the stage node. Strategy/Multi-deployment node's UUIDs will be replaced here.
          YamlUtils.replaceFieldInJsonNodeFromAnotherJsonNode(
              currentResumableStagejsonNode, stage, YAMLFieldNameConstants.UUID);
          stageCounter++;
          isStrategyNodeProcessed = true;
        }
      } else {
        // parallel group
        if (!isRetryStagesInParallelStages(PipelineYamlUtils.getStagesNodeFromParallelNode(stage, pipelineVersion),
                retryStages, identifierOfSkipStages, isStrategyNodeProcessed, pipelineVersion)
            && !isStrategyNodeProcessed) {
          // if the parallel group does not contain the retry stages, copy the whole parallel node
          ((ArrayNode) PipelineYamlUtils.getStagesNodeFromRootNode(currentRootJsonNode, pipelineVersion))
              .set(stageCounter, stage);
          stageCounter = stageCounter + 1;
        } else {
          // replace only those stages that needs to be skipped
          ((ArrayNode) PipelineYamlUtils.getStagesNodeFromRootNode(currentRootJsonNode, pipelineVersion))
              .set(stageCounter,
                  replaceStagesInParallelGroup(PipelineYamlUtils.getStagesNodeFromParallelNode(stage, pipelineVersion),
                      retryStages,
                      PipelineYamlUtils.getStagesNodeFromRootNode(currentRootJsonNode, pipelineVersion)
                          .get(stageCounter),
                      identifierOfSkipStages, isStrategyNodeProcessed, pipelineVersion));

          // replacing uuid for parallel node
          ((ObjectNode) PipelineYamlUtils.getStagesNodeFromRootNode(currentRootJsonNode, pipelineVersion)
                  .get(stageCounter))
              .set(YAMLFieldNameConstants.UUID, stage.get(YAMLFieldNameConstants.UUID));

          break;
        }
      }
    }
    return currentRootJsonNode.toString();
  }

  // Todo: Change here
  private JsonNode replaceStagesInParallelGroup(JsonNode parallelStage, List<String> retryStages,
      JsonNode currentParallelStageNode, List<String> identifierOfSkipStages, boolean isStrategyNodeProcessed,
      String pipelineVersion) {
    int stageCounter = 0;
    for (JsonNode stageNode : parallelStage) {
      String stageIdentifier = PipelineYamlUtils.getIdentifierFromStageNode(stageNode, pipelineVersion);
      if (!retryStages.contains(stageIdentifier) && !isStrategyNodeProcessed) {
        identifierOfSkipStages.add(stageIdentifier);
        ((ArrayNode) PipelineYamlUtils.getStagesNodeFromParallelNode(currentParallelStageNode, pipelineVersion))
            .set(stageCounter, stageNode);
      } else {
        // replace only the uuid of the retry parallel stage
        JsonNode currentResumableStagejsonNode = PipelineYamlUtils.getStageNodeFromStagesNode(
            (ArrayNode) PipelineYamlUtils.getStagesNodeFromParallelNode(currentParallelStageNode, pipelineVersion),
            stageCounter, pipelineVersion);
        // Replacing all the UUIDs under the stage node.
        YamlUtils.replaceFieldInJsonNodeFromAnotherJsonNode(currentResumableStagejsonNode,
            PipelineYamlUtils.getStageNodeFromStagesElement(stageNode, pipelineVersion), YAMLFieldNameConstants.UUID);
      }
      stageCounter++;
    }

    return currentParallelStageNode;
  }

  private boolean isRetryStagesInParallelStages(JsonNode parallelStage, List<String> retryStages,
      List<String> identifierOfSkipStages, boolean isStrategyNodeProcessed, String pipelineVersion) {
    List<String> stagesIdentifierInParallelNode = new ArrayList<>();
    for (JsonNode stageNode : parallelStage) {
      String stageIdentifier = PipelineYamlUtils.getIdentifierFromStageNode(stageNode, pipelineVersion);
      stagesIdentifierInParallelNode.add(stageIdentifier);
      if (retryStages.contains(stageIdentifier)) {
        return true;
      }
    }
    /*
    This whole parallel node will get copied. We need to copy the stage identifier in identifierForSkipStages
     */
    if (!isStrategyNodeProcessed) {
      identifierOfSkipStages.addAll(stagesIdentifierInParallelNode);
    }
    return false;
  }

  /**
   *
   * @param plan Initial plan created without considering retry
   * @param identifierOfSkipStages identifier of stages that are to be skipped during the retry.
   * @param previousExecutionId planExecutionId of the execution that is being retried.
   * @param stageIdentifiersToRetryWith stage identifiers of the stages from which the execution is being retried.
   * @return Returns the transformed Plan for the retry
   * This method operates on 3 kind of nodes:
   * 1. Nodes that belong to stages that are to be skipped: Convert all planNodes into IdentityNodes.
   * 2. Nodes that belong to the stages that are being retried: Only the strategy node that is parent of stage will be
   * converted into IdentityNode. Rest all will remain as planNodes.
   * 3. Nodes belong to subsequent stages: Will remain as planNodes and will be executed as normal execution.
   */
  public Plan transformPlan(Plan plan, List<String> identifierOfSkipStages, String previousExecutionId,
      List<String> stageIdentifiersToRetryWith) {
    List<Node> finalUpdatedPlanNodes = new ArrayList<>();
    // identifierOfSkipStages: previousStageIdentifiers we want to skip
    List<String> stageFqnForStagesToBeSkipped =
        nodeExecutionService.fetchStageFqnFromStageIdentifiers(previousExecutionId, identifierOfSkipStages);
    // Adding nodes to be skipped in the finalUpdatedPlanNodes list.
    finalUpdatedPlanNodes.addAll(handleNodesForStagesBeingSkipped(previousExecutionId, stageFqnForStagesToBeSkipped));

    // Get all nodes that will be re-executed.(Does not belong to the stages to be skipped)
    List<Node> planNodesToBeExecuted = plan.getPlanNodes()
                                           .stream()
                                           .filter(node -> !stageFqnForStagesToBeSkipped.contains(node.getStageFqn()))
                                           .collect(Collectors.toList());

    List<String> stageFqnForStagesBeingRetried =
        nodeExecutionService.fetchStageFqnFromStageIdentifiers(previousExecutionId, stageIdentifiersToRetryWith);
    List<Node> strategyNodes = new ArrayList<>();
    // Filtering the strategy nodes of stages that are being retried and populating the strategyNodes list with such
    // nodes. The nodes after filtering will remain as is and will not be converted into IdentityNodes.
    planNodesToBeExecuted =
        filterStrategyNodesForStagesBeingRetried(planNodesToBeExecuted, stageFqnForStagesBeingRetried, strategyNodes);

    // Adding nodes to be re-executed in the finalUpdatedPlanNodes list.
    finalUpdatedPlanNodes.addAll(planNodesToBeExecuted);

    // Adding nodes for the stages that are being retried.
    finalUpdatedPlanNodes.addAll(
        handleStrategyNodeForStagesBeingRetried(strategyNodes, stageFqnForStagesBeingRetried, previousExecutionId));

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

  private List<Node> handleNodesForStagesBeingSkipped(
      String previousExecutionId, List<String> stagesFqnForStageToBeSkipped) {
    List<Node> identityNodesList = new ArrayList<>();
    // NodeExecutionUuid -> Node for the nodes those belong to stages that will be skipped.
    Map<String, Node> nodeUuidToNodeExecutionUuid = nodeExecutionService.mapNodeExecutionIdWithPlanNodeForGivenStageFQN(
        previousExecutionId, stagesFqnForStageToBeSkipped);
    nodeUuidToNodeExecutionUuid.forEach((nodeExecutionUuid, planNode)
                                            -> identityNodesList.add(IdentityPlanNode.mapPlanNodeToIdentityNode(
                                                planNode, planNode.getStepType(), nodeExecutionUuid)));
    return identityNodesList;
  }
  private List<Node> filterStrategyNodesForStagesBeingRetried(
      List<Node> planNodes, List<String> stagesFqnToRetryWith, List<Node> strategyNodes) {
    List<Node> filteredPlanNodesList = new ArrayList<>();
    for (Node node : planNodes) {
      if (stagesFqnToRetryWith.contains(node.getStageFqn()) && node.getStepCategory() == StepCategory.STRATEGY) {
        strategyNodes.add(node);
      } else {
        filteredPlanNodesList.add(node);
      }
    }
    return filteredPlanNodesList;
  }
  public RetryHistoryResponseDto getRetryHistory(String rootParentId, String currentPlanExecutionId) {
    try (CloseableIterator<PipelineExecutionSummaryEntity> iterator =
             pmsExecutionSummaryRespository.fetchPipelineSummaryEntityFromRootParentIdUsingSecondaryMongo(
                 rootParentId)) {
      List<ExecutionInfo> executionInfos = new ArrayList<>();
      while (iterator.hasNext()) {
        executionInfos.add(convertToExecutionInfo(iterator.next()));
      }
      if (executionInfos.size() <= 1) {
        return RetryHistoryResponseDto.builder().errorMessage("Nothing to show in retry history").build();
      }
      String latestRetryExecutionId = executionInfos.get(0).getUuid();
      RetryStagesMetadata retryStagesMetadata =
          planExecutionMetadataService.getRetryStagesMetadata(currentPlanExecutionId);
      if (retryStagesMetadata != null) {
        return RetryHistoryResponseDto.builder()
            .executionInfos(executionInfos)
            .latestExecutionId(latestRetryExecutionId)
            .retryStagesMetadata(toRetryStagesMetadataDTO(retryStagesMetadata))
            .build();
      } else {
        return RetryHistoryResponseDto.builder()
            .executionInfos(executionInfos)
            .latestExecutionId(latestRetryExecutionId)
            .build();
      }
    }
  }

  private RetryStagesMetadataDTO toRetryStagesMetadataDTO(RetryStagesMetadata retryStagesMetadata) {
    return RetryStagesMetadataDTO.builder()
        .retryStagesIdentifier(retryStagesMetadata.getRetryStagesIdentifier())
        .skipStagesIdentifier(retryStagesMetadata.getSkipStagesIdentifier())
        .build();
  }

  private List<Node> handleStrategyNodeForStagesBeingRetried(
      List<Node> planNodes, List<String> stagesFqnToRetryWith, String previousExecutionId) {
    List<NodeExecution> strategyNodeExecutions =
        nodeExecutionService.fetchStrategyNodeExecutions(previousExecutionId, stagesFqnToRetryWith);
    List<Node> processedNodes = new ArrayList<>();
    for (Node node : planNodes) {
      // Find the strategyNodeExecution that belong to the node. And its on the stage.(Basically to check if node is of
      // type stage or not). We need to convert only the stage's strategy node into IdentityNode.
      Optional<NodeExecution> strategyNodeExecution =
          strategyNodeExecutions.stream()
              .filter(o -> node.getUuid().equals(o.getNodeId()))
              .filter(o -> AmbianceUtils.isCurrentStrategyLevelAtStage(o.getAmbiance()))
              .findFirst();
      if (strategyNodeExecution.isEmpty()) {
        processedNodes.add(node);
      } else {
        // This strategyNodeExecution is at the stage level. And the execution is being retried from this strategy
        // stage. And setting useAdviserObtainments true because we want that IdentityNodeExecutionStrategy to use the
        // original advisorsObtainments from the node.
        processedNodes.add(
            IdentityPlanNode.mapPlanNodeToIdentityNode(node, node.getStepType(), strategyNodeExecution.get().getUuid())
                .withUseAdviserObtainments(true));
      }
    }
    return processedNodes;
  }

  public RetryLatestExecutionResponseDto getRetryLatestExecutionId(String rootParentId) {
    try (CloseableIterator<PipelineExecutionSummaryEntity> iterator =
             pmsExecutionSummaryRespository.fetchPipelineSummaryEntityFromRootParentIdUsingSecondaryMongo(
                 rootParentId)) {
      if (iterator.hasNext()) {
        // We want more than one entry therefore checking if iterator has next or not.
        PipelineExecutionSummaryEntity entity = iterator.next();
        if (!iterator.hasNext()) {
          return RetryLatestExecutionResponseDto.builder()
              .errorMessage("This is not a part of retry execution")
              .build();
        }
        return RetryLatestExecutionResponseDto.builder().latestExecutionId(entity.getPlanExecutionId()).build();
      }
      return RetryLatestExecutionResponseDto.builder().errorMessage("This is not a part of retry execution").build();
    }
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

  private ExecutionInfo convertToExecutionInfo(PipelineExecutionSummaryEntity entity) {
    return ExecutionInfo.builder()
        .uuid(entity.getPlanExecutionId())
        .startTs(entity.getStartTs())
        .status(entity.getStatus())
        .endTs(entity.getEndTs())
        .build();
  }
}
