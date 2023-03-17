/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.event.entitycrud;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DELETE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.PIPELINE_ENTITY;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.engine.interrupts.InterruptService;
import io.harness.engine.pms.data.PmsOutcomeService;
import io.harness.engine.pms.data.PmsSweepingOutputService;
import io.harness.eventsframework.EntityChangeLogContext;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.expansion.PlanExpansionService;
import io.harness.ng.core.event.MessageListener;
import io.harness.ngtriggers.service.NGTriggerEventsService;
import io.harness.ngtriggers.service.NGTriggerService;
import io.harness.pms.pipeline.service.PipelineMetadataService;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.service.PmsExecutionSummaryService;
import io.harness.pms.preflight.service.PreflightService;
import io.harness.service.GraphGenerationService;
import io.harness.steps.barriers.service.BarrierService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.util.CloseableIterator;

@OwnedBy(PIPELINE)
@Slf4j
@Singleton
public class PipelineEntityCRUDStreamListener implements MessageListener {
  // Max batch size of planExecutionIds to delete related metadata, so that delete records are in limited range
  private static final int MAX_DELETION_BATCH_PROCESSING = 50;

  private final NGTriggerService ngTriggerService;
  private final PipelineMetadataService pipelineMetadataService;
  private final PmsExecutionSummaryService pmsExecutionSummaryService;
  private final BarrierService barrierService;
  private final PreflightService preflightService;
  private final PmsSweepingOutputService pmsSweepingOutputService;
  private final PmsOutcomeService pmsOutcomeService;
  private final InterruptService interruptService;
  private final GraphGenerationService graphGenerationService;
  private final NodeExecutionService nodeExecutionService;
  private final NGTriggerEventsService ngTriggerEventsService;
  private final PlanExecutionService planExecutionService;

  private final PlanExpansionService planExpansionService;

  @Inject
  public PipelineEntityCRUDStreamListener(NGTriggerService ngTriggerService,
      PipelineMetadataService pipelineMetadataService, PmsExecutionSummaryService pmsExecutionSummaryService,
      BarrierService barrierService, PreflightService preflightService,
      PmsSweepingOutputService pmsSweepingOutputService, PmsOutcomeService pmsOutcomeService,
      InterruptService interruptService, GraphGenerationService graphGenerationService,
      NodeExecutionService nodeExecutionService, NGTriggerEventsService ngTriggerEventsService,
      PlanExecutionService planExecutionService, PlanExpansionService planExpansionService) {
    this.ngTriggerService = ngTriggerService;
    this.pipelineMetadataService = pipelineMetadataService;
    this.pmsExecutionSummaryService = pmsExecutionSummaryService;
    this.barrierService = barrierService;
    this.preflightService = preflightService;
    this.pmsSweepingOutputService = pmsSweepingOutputService;
    this.pmsOutcomeService = pmsOutcomeService;
    this.interruptService = interruptService;
    this.graphGenerationService = graphGenerationService;
    this.nodeExecutionService = nodeExecutionService;
    this.planExecutionService = planExecutionService;
    this.ngTriggerEventsService = ngTriggerEventsService;
    this.planExpansionService = planExpansionService;
  }

  @Override
  public boolean handleMessage(Message message) {
    if (message != null && message.hasMessage()) {
      Map<String, String> metadataMap = message.getMessage().getMetadataMap();
      if (metadataMap != null && metadataMap.get(ENTITY_TYPE) != null
          && PIPELINE_ENTITY.equals(metadataMap.get(ENTITY_TYPE))) {
        EntityChangeDTO entityChangeDTO;
        try {
          entityChangeDTO = EntityChangeDTO.parseFrom(message.getMessage().getData());
        } catch (InvalidProtocolBufferException e) {
          throw new InvalidRequestException(
              String.format("Exception in unpacking EntityChangeDTO for key %s", message.getId()), e);
        }
        String action = metadataMap.get(ACTION);
        if (action != null) {
          return processPipelineEntityChangeEvent(entityChangeDTO, action);
        }
      }
    }
    return true;
  }

  private boolean processPipelineEntityChangeEvent(EntityChangeDTO entityChangeDTO, String action) {
    switch (action) {
      case DELETE_ACTION:
        if (checkIfAnyRequiredFieldIsNotEmpty(entityChangeDTO)) {
          try (EntityChangeLogContext logContext = new EntityChangeLogContext(entityChangeDTO)) {
            return processDeleteEvent(entityChangeDTO);
          }
        } else {
          return true;
        }
      default:
    }
    return true;
  }

  /**
   * Delete the entities in background which can be slow processing and will not impact other operations for the
   * customers.
   * Mainly for deleting executions, metadata, background entities etc.
   * @param entityChangeDTO
   * @return
   */
  private boolean processDeleteEvent(EntityChangeDTO entityChangeDTO) {
    String accountId = entityChangeDTO.getAccountIdentifier().getValue();
    String orgIdentifier = entityChangeDTO.getOrgIdentifier().getValue();
    String projectIdentifier = entityChangeDTO.getProjectIdentifier().getValue();
    String pipelineIdentifier = entityChangeDTO.getIdentifier().getValue();

    deletePipelineMetadataDetails(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier);
    deletePipelineExecutionsDetails(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier);

    log.info("Processed deleting metadata and execution details for given pipeline");

    return true;
  }

  // Delete all pipeline metadata details related to given pipeline identifier.
  private void deletePipelineMetadataDetails(
      String accountId, String orgIdentifier, String projectIdentifier, String pipelineIdentifier) {
    // Delete all triggers, ignore any error
    ngTriggerService.deleteAllForPipeline(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier);
    // Delete trigger event history
    ngTriggerEventsService.deleteAllForPipeline(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier);
    // Delete the pipeline metadata to delete run-sequence, etc.
    pipelineMetadataService.deletePipelineMetadata(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier);

    // Deletes all related preflight data
    preflightService.deleteAllPreflightEntityForGivenPipeline(
        accountId, orgIdentifier, projectIdentifier, pipelineIdentifier);
  }

  // Delete all execution related details using all planExecution for given pipelineIdentifier.
  private void deletePipelineExecutionsDetails(
      String accountId, String orgIdentifier, String projectIdentifier, String pipelineIdentifier) {
    Set<String> toBeDeletedPlanExecutions = new HashSet<>();

    try (CloseableIterator<PipelineExecutionSummaryEntity> iterator =
             pmsExecutionSummaryService.fetchPlanExecutionIdsFromAnalytics(
                 accountId, orgIdentifier, projectIdentifier, pipelineIdentifier)) {
      while (iterator.hasNext()) {
        toBeDeletedPlanExecutions.add(iterator.next().getPlanExecutionId());

        // If max deletion batch is reached, delete all its related entities
        // We don't want to delete all executions for a pipeline together as total delete could be very high
        if (toBeDeletedPlanExecutions.size() >= MAX_DELETION_BATCH_PROCESSING) {
          deletePipelineExecutionsDetailsInternal(toBeDeletedPlanExecutions);
          toBeDeletedPlanExecutions.clear();
        }
      }
    }

    if (EmptyPredicate.isNotEmpty(toBeDeletedPlanExecutions)) {
      deletePipelineExecutionsDetailsInternal(toBeDeletedPlanExecutions);
    }
  }

  // Internal method which deletes all execution metadata for given planExecutions
  private void deletePipelineExecutionsDetailsInternal(Set<String> planExecutionsToDelete) {
    // Deletes the barrierInstances
    barrierService.deleteAllForGivenPlanExecutionId(planExecutionsToDelete);
    // Delete sweepingOutput
    pmsSweepingOutputService.deleteAllSweepingOutputInstances(planExecutionsToDelete);
    // Delete outcome instances
    pmsOutcomeService.deleteAllOutcomesInstances(planExecutionsToDelete);
    // Delete all interrupts
    interruptService.deleteAllInterrupts(planExecutionsToDelete);
    // Delete all graph metadata
    graphGenerationService.deleteAllGraphMetadataForGivenExecutionIds(planExecutionsToDelete);
    // Delete nodeExecutions and its metadata
    for (String planExecutionToDelete : planExecutionsToDelete) {
      nodeExecutionService.deleteAllNodeExecutionAndMetadata(planExecutionToDelete);
    }
    // Delete all planExecutions and its metadata
    planExecutionService.deleteAllPlanExecutionAndMetadata(planExecutionsToDelete);
    planExpansionService.deleteAllExpansions(planExecutionsToDelete);
  }

  private boolean checkIfAnyRequiredFieldIsNotEmpty(EntityChangeDTO entityChangeDTO) {
    String accountId = entityChangeDTO.getAccountIdentifier().getValue();
    String orgIdentifier = entityChangeDTO.getOrgIdentifier().getValue();
    String projectIdentifier = entityChangeDTO.getProjectIdentifier().getValue();
    String pipelineIdentifier = entityChangeDTO.getIdentifier().getValue();
    if (EmptyPredicate.isEmpty(accountId) || EmptyPredicate.isEmpty(orgIdentifier)
        || EmptyPredicate.isEmpty(projectIdentifier) || EmptyPredicate.isEmpty(pipelineIdentifier)) {
      log.warn("Either of required fields for Pipeline Delete event is empty - " + entityChangeDTO);
      return false;
    }
    return true;
  }
}
