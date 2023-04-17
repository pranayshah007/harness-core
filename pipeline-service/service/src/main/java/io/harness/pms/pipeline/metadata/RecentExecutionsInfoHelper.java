/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.metadata;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.PersistentLockException;
import io.harness.execution.PlanExecution;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.observer.Subject.Informant0;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.pipeline.PipelineMetadataV2;
import io.harness.pms.pipeline.PipelineMetadataV2.PipelineMetadataV2Keys;
import io.harness.pms.pipeline.RecentExecutionInfo;
import io.harness.pms.pipeline.service.PipelineMetadataService;
import io.harness.utils.ExecutionModeUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(PIPELINE)
@Singleton
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class RecentExecutionsInfoHelper {
  private PipelineMetadataService pipelineMetadataService;
  private PersistentLocker persistentLocker;

  public static int NUM_RECENT_EXECUTIONS = 10;

  /*
  These three steps will be taken behind a lock. This lock is shared between onExecutionStart and onExecutionUpdate
  1. Fetch the metadata
  2. If recent execution info is not there, then initialise a list with one element being the info for the execution in
  question. Else, add this new execution's info into the head of the list. Make sure that the list size is not greater
  than NUM_RECENT_EXECUTIONS
  3. make an update call to set this new recent executions list into the pipeline metadata
   */
  public void onExecutionStart(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, PlanExecution planExecution) {
    ExecutionMetadata executionMetadata = planExecution.getMetadata();
    if (ExecutionModeUtils.isRollbackMode(executionMetadata.getExecutionMode())) {
      return;
    }
    RecentExecutionInfo newExecutionInfo = RecentExecutionInfo.builder()
                                               .executionTriggerInfo(executionMetadata.getTriggerInfo())
                                               .planExecutionId(planExecution.getUuid())
                                               .status(Status.RUNNING)
                                               .startTs(planExecution.getStartTs())
                                               .runSequence(executionMetadata.getRunSequence())
                                               .build();
    Informant0<List<RecentExecutionInfo>> subject = (List<RecentExecutionInfo> recentExecutionInfoList) -> {
      if (recentExecutionInfoList == null) {
        recentExecutionInfoList = new LinkedList<>();
      } else if (recentExecutionInfoList.size() == NUM_RECENT_EXECUTIONS) {
        recentExecutionInfoList.remove(NUM_RECENT_EXECUTIONS - 1);
      }
      recentExecutionInfoList.add(0, newExecutionInfo);
      Criteria criteria =
          getCriteriaForPipelineMetadata(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier);
      Update update = getUpdateOperationForRecentExecutionInfo(recentExecutionInfoList);
      pipelineMetadataService.update(criteria, update);
    };
    updateMetadata(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, subject, planExecution);
  }

  /*
  These three steps will be taken behind a lock. This lock is shared between onExecutionStart and onExecutionUpdate
  1. Fetch the metadata
  2. If recent execution info for this execution is there, then update the status of that execution, and add an endTs if
  the execution has ended. If the info is not there, it means this execution has been going on for too long
  3. make an update call to set this new recent executions list into the pipeline metadata
   */

  /**
   * @param ambiance
   * @param planExecution -> planExecution has only entity metadata
   */
  public void onExecutionUpdate(Ambiance ambiance, PlanExecution planExecution) {
    ExecutionMetadata executionMetadata = ambiance.getMetadata();
    if (ExecutionModeUtils.isRollbackMode(executionMetadata.getExecutionMode())) {
      return;
    }
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);
    String pipelineIdentifier = executionMetadata.getPipelineIdentifier();
    String planExecutionId = planExecution.getUuid();
    Informant0<List<RecentExecutionInfo>> subject = (List<RecentExecutionInfo> recentExecutionInfoList) -> {
      if (recentExecutionInfoList == null) {
        return;
      }
      for (RecentExecutionInfo recentExecutionInfo : recentExecutionInfoList) {
        if (recentExecutionInfo.getPlanExecutionId().equals(planExecutionId)) {
          recentExecutionInfo.setStatus(planExecution.getStatus());
          Long endTsInPlanExecution = planExecution.getEndTs();
          if (endTsInPlanExecution != null && endTsInPlanExecution != 0L) {
            recentExecutionInfo.setEndTs(endTsInPlanExecution);
          }
          if (executionMetadata.getPipelineStageInfo().getHasParentPipeline()) {
            recentExecutionInfo.setParentStageInfo(executionMetadata.getPipelineStageInfo());
          }

          Criteria criteria =
              getCriteriaForPipelineMetadata(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier);
          Update update = getUpdateOperationForRecentExecutionInfo(recentExecutionInfoList);
          pipelineMetadataService.update(criteria, update);
          return;
        }
      }
    };
    updateMetadata(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, subject, planExecution);
  }

  void updateMetadata(String accountId, String orgIdentifier, String projectIdentifier, String pipelineIdentifier,
      Informant0<List<RecentExecutionInfo>> subject, PlanExecution planExecution) {
    String lockName = getLockName(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier);
    try (AcquiredLock<?> lock =
             persistentLocker.waitToAcquireLock(lockName, Duration.ofSeconds(1), Duration.ofSeconds(2))) {
      if (lock == null) {
        log.error(String.format(
            "Unable to acquire lock while updating Pipeline Metadata for Pipeline [%s] in Project [%s], Org [%s], Account [%s]",
            pipelineIdentifier, projectIdentifier, orgIdentifier, accountId));
        return;
      }

      Optional<PipelineMetadataV2> optionalPipelineMetadata =
          pipelineMetadataService.getMetadata(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier);
      if (!optionalPipelineMetadata.isPresent()) {
        log.error(
            String.format("Could not find Pipeline Metadata for Pipeline [%s] in Project [%s], Org [%s], Account [%s]",
                pipelineIdentifier, projectIdentifier, orgIdentifier, accountId));
        return;
      }
      PipelineMetadataV2 pipelineMetadata = optionalPipelineMetadata.get();
      List<RecentExecutionInfo> recentExecutionInfoList = pipelineMetadata.getRecentExecutionInfoList();
      // If the last element in recentExecutionInfoList is more recent than the PlanExecution. Then update is not
      // required.
      if (recentExecutionInfoList != null && recentExecutionInfoList.size() == NUM_RECENT_EXECUTIONS
          && recentExecutionInfoList.get(NUM_RECENT_EXECUTIONS - 1).getStartTs() > planExecution.getStartTs()) {
        return;
      }

      subject.inform(recentExecutionInfoList);
    } catch (PersistentLockException ex) {
      log.error(
          "RecentExecutionInfo could not be updated for the planExecution: {} to status {} in account: {}, org: {}, project: {}",
          planExecution.getUuid(), planExecution.getStatus(), accountId, orgIdentifier, projectIdentifier, ex);
    } catch (Exception ex) {
      log.error(
          "Could not update recentExecutions in PipelineMetadata for Pipeline {} and planExecutionId {} having account: {} org: {} project {}",
          pipelineIdentifier, planExecution.getUuid(), accountId, orgIdentifier, projectIdentifier, ex);
    }
  }

  String getLockName(String accountId, String orgIdentifier, String projectIdentifier, String pipelineIdentifier) {
    return String.format(
        "recentExecutionsInfo/%s/%s/%s/%s", accountId, orgIdentifier, projectIdentifier, pipelineIdentifier);
  }

  Criteria getCriteriaForPipelineMetadata(
      String accountId, String orgIdentifier, String projectIdentifier, String pipelineIdentifier) {
    return Criteria.where(PipelineMetadataV2Keys.accountIdentifier)
        .is(accountId)
        .and(PipelineMetadataV2Keys.orgIdentifier)
        .is(orgIdentifier)
        .and(PipelineMetadataV2Keys.projectIdentifier)
        .is(projectIdentifier)
        .and(PipelineMetadataV2Keys.identifier)
        .is(pipelineIdentifier);
  }

  Update getUpdateOperationForRecentExecutionInfo(List<RecentExecutionInfo> recentExecutionInfoList) {
    Update update = new Update();
    update.set(PipelineMetadataV2Keys.recentExecutionInfoList, recentExecutionInfoList);
    return update;
  }
}
