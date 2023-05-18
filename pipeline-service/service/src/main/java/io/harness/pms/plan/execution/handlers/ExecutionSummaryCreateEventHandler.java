/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.handlers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.engine.executions.plan.PlanService;
import io.harness.engine.executions.retry.RetryExecutionMetadata;
import io.harness.engine.observers.OrchestrationStartObserver;
import io.harness.engine.observers.beans.OrchestrationStartInfo;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.execution.StagesExecutionMetadata;
import io.harness.notification.PipelineEventType;
import io.harness.plan.Plan;
import io.harness.plancreator.strategy.StrategyType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.EdgeLayoutList;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.contracts.plan.GraphLayoutNode;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.gitsync.PmsGitSyncHelper;
import io.harness.pms.merger.helpers.InputSetTemplateHelper;
import io.harness.pms.notification.NotificationHelper;
import io.harness.pms.pipeline.ExecutionSummaryInfo;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.mappers.GraphLayoutDtoMapper;
import io.harness.pms.pipeline.metadata.RecentExecutionsInfoHelper;
import io.harness.pms.pipeline.service.PMSPipelineService;
import io.harness.pms.plan.creation.NodeTypeLookupService;
import io.harness.pms.plan.execution.StoreTypeMapper;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys;
import io.harness.pms.plan.execution.beans.dto.GraphLayoutNodeDTO;
import io.harness.pms.plan.execution.service.PmsExecutionSummaryService;
import io.harness.pms.yaml.YAMLFieldNameConstants;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
public class ExecutionSummaryCreateEventHandler implements OrchestrationStartObserver {
  private static List<String> INTERNAL_NODE_TYPES = Lists.newArrayList(YAMLFieldNameConstants.PARALLEL);
  private final PMSPipelineService pmsPipelineService;
  private final PlanService planService;
  private final PlanExecutionService planExecutionService;
  private final NodeTypeLookupService nodeTypeLookupService;
  private final PmsGitSyncHelper pmsGitSyncHelper;
  private final NotificationHelper notificationHelper;
  private final RecentExecutionsInfoHelper recentExecutionsInfoHelper;
  private final PmsExecutionSummaryService pmsExecutionSummaryService;

  @Inject
  public ExecutionSummaryCreateEventHandler(PMSPipelineService pmsPipelineService, PlanService planService,
      PlanExecutionService planExecutionService, NodeTypeLookupService nodeTypeLookupService,
      PmsGitSyncHelper pmsGitSyncHelper, NotificationHelper notificationHelper,
      RecentExecutionsInfoHelper recentExecutionsInfoHelper, PmsExecutionSummaryService pmsExecutionSummaryService) {
    this.pmsPipelineService = pmsPipelineService;
    this.planService = planService;
    this.planExecutionService = planExecutionService;
    this.nodeTypeLookupService = nodeTypeLookupService;

    this.pmsGitSyncHelper = pmsGitSyncHelper;
    this.notificationHelper = notificationHelper;
    this.recentExecutionsInfoHelper = recentExecutionsInfoHelper;
    this.pmsExecutionSummaryService = pmsExecutionSummaryService;
  }

  @Override
  public void onStart(OrchestrationStartInfo orchestrationStartInfo) {
    Ambiance ambiance = orchestrationStartInfo.getAmbiance();
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String projectId = AmbianceUtils.getProjectIdentifier(ambiance);
    String orgId = AmbianceUtils.getOrgIdentifier(ambiance);
    String planExecutionId = ambiance.getPlanExecutionId();
    PlanExecution planExecution = planExecutionService.get(planExecutionId);

    ExecutionMetadata metadata = planExecution.getMetadata();
    String pipelineId = metadata.getPipelineIdentifier();
    Optional<PipelineEntity> pipelineEntity =
        pmsPipelineService.getPipeline(accountId, orgId, projectId, pipelineId, false, false);
    if (pipelineEntity.isEmpty()) {
      return;
    }

    // RetryInfo
    String rootExecutionId = planExecutionId;
    String parentExecutionId = planExecutionId;
    if (metadata.getRetryInfo().getIsRetry()) {
      rootExecutionId = metadata.getRetryInfo().getRootExecutionId();
      parentExecutionId = metadata.getRetryInfo().getParentRetryId();

      // updating isLatest and canRetry
      Update update = new Update();
      update.set(PlanExecutionSummaryKeys.isLatestExecution, false);
      pmsExecutionSummaryService.update(parentExecutionId, update);
    }

    recentExecutionsInfoHelper.onExecutionStart(accountId, orgId, projectId, pipelineId, planExecution);

    updateExecutionInfoInPipelineEntity(
        accountId, orgId, projectId, pipelineId, pipelineEntity.get().getExecutionSummaryInfo(), planExecutionId);
    Plan plan = planService.fetchPlan(ambiance.getPlanId());
    Map<String, GraphLayoutNode> layoutNodeMap = new HashMap<>(plan.getGraphLayoutInfo().getLayoutNodesMap());
    String startingNodeId = plan.getGraphLayoutInfo().getStartingNodeId();

    if (ambiance.getMetadata().getExecutionMode() == ExecutionMode.POST_EXECUTION_ROLLBACK) {
      startingNodeId = ambiance.getMetadata().getPostExecutionRollbackInfo(0).getPostExecutionRollbackStageId();
      GraphLayoutNode layoutNode = layoutNodeMap.get(startingNodeId);
      layoutNodeMap.put(startingNodeId,
          layoutNode.toBuilder()
              .setEdgeLayoutList(
                  EdgeLayoutList.newBuilder()
                      .addAllCurrentNodeChildren(layoutNode.getEdgeLayoutList().getCurrentNodeChildrenList())
                      .build())
              .build());
    }
    Map<String, GraphLayoutNodeDTO> layoutNodeDTOMap = new HashMap<>();
    Set<String> modules = new LinkedHashSet<>();
    for (Map.Entry<String, GraphLayoutNode> entry : layoutNodeMap.entrySet()) {
      GraphLayoutNodeDTO graphLayoutNodeDTO = GraphLayoutDtoMapper.toDto(entry.getValue());
      if (INTERNAL_NODE_TYPES.contains(entry.getValue().getNodeType())
          || Arrays.stream(StrategyType.values())
                 .anyMatch(type -> type.name().equals(entry.getValue().getNodeType()))) {
        layoutNodeDTOMap.put(entry.getKey(), graphLayoutNodeDTO);
        continue;
      }
      String moduleName = nodeTypeLookupService.findNodeTypeServiceName(entry.getValue().getNodeType());
      graphLayoutNodeDTO.setModule(moduleName);
      Map<String, LinkedHashMap<String, Object>> moduleInfo = new HashMap<>();
      moduleInfo.put(moduleName, new LinkedHashMap<>());
      graphLayoutNodeDTO.setModuleInfo(moduleInfo);
      layoutNodeDTOMap.put(entry.getKey(), graphLayoutNodeDTO);
      modules.add(moduleName);
    }

    PlanExecutionMetadata planExecutionMetadata = orchestrationStartInfo.getPlanExecutionMetadata();
    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity =
        PipelineExecutionSummaryEntity.builder()
            .layoutNodeMap(layoutNodeDTOMap)
            .runSequence(metadata.getRunSequence())
            .pipelineIdentifier(pipelineId)
            .startingNodeId(startingNodeId)
            .planExecutionId(planExecutionId)
            .name(pipelineEntity.get().getName())
            .inputSetYaml(planExecutionMetadata.getInputSetYaml())
            .pipelineTemplate(getPipelineTemplate(pipelineEntity.get(), planExecutionMetadata))
            .internalStatus(planExecution.getStatus())
            .status(ExecutionStatus.getExecutionStatus(planExecution.getStatus()))
            .startTs(planExecution.getStartTs())
            .startingNodeId(startingNodeId)
            .accountId(accountId)
            .projectIdentifier(projectId)
            .orgIdentifier(orgId)
            .executionTriggerInfo(metadata.getTriggerInfo())
            .parentStageInfo(ambiance.getMetadata().getPipelineStageInfo())
            .entityGitDetails(pmsGitSyncHelper.getEntityGitDetailsFromBytes(metadata.getGitSyncBranchContext()))
            .tags(pipelineEntity.get().getTags())
            .modules(new ArrayList<>(modules))
            .isLatestExecution(true)
            .retryExecutionMetadata(RetryExecutionMetadata.builder()
                                        .parentExecutionId(parentExecutionId)
                                        .rootExecutionId(rootExecutionId)
                                        .build())
            .allowStagesExecution(planExecutionMetadata.isStagesExecutionAllowed())
            .governanceMetadata(planExecution.getGovernanceMetadata())
            .stagesExecutionMetadata(planExecutionMetadata.getStagesExecutionMetadata())
            .storeType(StoreTypeMapper.fromPipelineStoreType(metadata.getPipelineStoreType()))
            .executionInputConfigured(orchestrationStartInfo.getPlanExecutionMetadata().getExecutionInputConfigured())
            .connectorRef(
                EmptyPredicate.isEmpty(metadata.getPipelineConnectorRef()) ? null : metadata.getPipelineConnectorRef())
            .executionMode(metadata.getExecutionMode())
            .build();
    pmsExecutionSummaryService.save(pipelineExecutionSummaryEntity);
    notificationHelper.sendNotification(
        orchestrationStartInfo.getAmbiance(), PipelineEventType.PIPELINE_START, null, null);
  }

  private String getPipelineTemplate(PipelineEntity pipelineEntity, PlanExecutionMetadata planExecutionMetadata) {
    StagesExecutionMetadata stagesExecutionMetadata = planExecutionMetadata.getStagesExecutionMetadata();
    if (stagesExecutionMetadata != null && stagesExecutionMetadata.isStagesExecution()) {
      return InputSetTemplateHelper.createTemplateFromPipelineForGivenStages(
          pipelineEntity.getYaml(), stagesExecutionMetadata.getStageIdentifiers());
    }
    return InputSetTemplateHelper.createTemplateFromPipeline(pipelineEntity.getYaml());
  }

  private void updateExecutionInfoInPipelineEntity(String accountId, String orgId, String projectId, String pipelineId,
      ExecutionSummaryInfo executionSummaryInfo, String planExecutionId) {
    if (executionSummaryInfo == null) {
      executionSummaryInfo = ExecutionSummaryInfo.builder().build();
    }
    executionSummaryInfo.setLastExecutionStatus(ExecutionStatus.RUNNING);
    Map<String, Integer> deploymentsMap = executionSummaryInfo.getDeployments();
    Date todaysDate = new Date();
    SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy");
    String strDate = formatter.format(todaysDate);
    if (deploymentsMap.containsKey(strDate)) {
      deploymentsMap.put(strDate, deploymentsMap.get(strDate) + 1);
    } else {
      deploymentsMap.put(strDate, 1);
    }
    executionSummaryInfo.setDeployments(deploymentsMap);
    executionSummaryInfo.setLastExecutionTs(todaysDate.getTime());
    executionSummaryInfo.setLastExecutionId(planExecutionId);
    pmsPipelineService.saveExecutionInfo(accountId, orgId, projectId, pipelineId, executionSummaryInfo);
  }
}
