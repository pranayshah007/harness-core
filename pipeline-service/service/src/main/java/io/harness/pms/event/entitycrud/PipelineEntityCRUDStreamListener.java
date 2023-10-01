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

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
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
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.ngtriggers.service.NGTriggerEventsService;
import io.harness.ngtriggers.service.NGTriggerService;
import io.harness.pms.pipeline.service.PipelineMetadataService;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.service.PmsExecutionSummaryService;
import io.harness.pms.preflight.service.PreflightService;
import io.harness.pms.utils.CompletableFutures;
import io.harness.pms.utils.NGPipelineSettingsConstant;
import io.harness.remote.client.NGRestUtils;
import io.harness.service.GraphGenerationService;
import io.harness.steps.barriers.service.BarrierService;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.util.CloseableIterator;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
@Slf4j
@Singleton
public class PipelineEntityCRUDStreamListener implements MessageListener {
  // Max batch size of planExecutionIds to delete related metadata, so that delete records are in limited range
  private static final int MAX_DELETION_BATCH_PROCESSING = 500;

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
  private final NGSettingsClient ngSettingsClient;
  private final ExecutorService pipelineExecutorService;

  @Inject
  public PipelineEntityCRUDStreamListener(NGTriggerService ngTriggerService,
      PipelineMetadataService pipelineMetadataService, PmsExecutionSummaryService pmsExecutionSummaryService,
      BarrierService barrierService, PreflightService preflightService,
      PmsSweepingOutputService pmsSweepingOutputService, PmsOutcomeService pmsOutcomeService,
      InterruptService interruptService, GraphGenerationService graphGenerationService,
      NodeExecutionService nodeExecutionService, NGTriggerEventsService ngTriggerEventsService,
      PlanExecutionService planExecutionService, PlanExpansionService planExpansionService,
      NGSettingsClient ngSettingsClient, @Named("PipelineExecutorService") ExecutorService pipelineExecutorService) {
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
    this.ngSettingsClient = ngSettingsClient;
    this.pipelineExecutorService = pipelineExecutorService;
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
    boolean retainPipelineExecutionDetailsAfterDelete = false;
    try {
      retainPipelineExecutionDetailsAfterDelete = Boolean.parseBoolean(
          NGRestUtils
              .getResponse(ngSettingsClient.getSetting(
                  NGPipelineSettingsConstant.DO_NOT_DELETE_PIPELINE_EXECUTION_DETAILS.getName(), accountId, null, null))
              .getValue());
    } catch (Exception ex) {
      log.warn(String.format("Could not fetch setting: %s",
                   NGPipelineSettingsConstant.DO_NOT_DELETE_PIPELINE_EXECUTION_DETAILS.getName()),
          ex);
    }
    deletePipelineMetadataDetails(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier);
    log.info(String.format("Processed deleting metadata for "
            + "given pipeline %s in accountId [%s] and orgIdentifier [%s] and projectIdentifier [%s]",
        pipelineIdentifier, accountId, orgIdentifier, projectIdentifier));
    deletePipelineExecutionsDetails(
        accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, retainPipelineExecutionDetailsAfterDelete);
    log.info(String.format("Processed deleting execution details for "
            + "given pipeline %s in accountId [%s] and orgIdentifier [%s] and projectIdentifier [%s]",
        pipelineIdentifier, accountId, orgIdentifier, projectIdentifier));
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
  private void deletePipelineExecutionsDetails(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, boolean retainPipelineExecutionDetailsAfterDelete) {
    Set<String> toBeDeletedPlanExecutions = new HashSet<>();

    try (CloseableIterator<PipelineExecutionSummaryEntity> iterator =
             pmsExecutionSummaryService.fetchPlanExecutionIdsFromAnalytics(
                 accountId, orgIdentifier, projectIdentifier, pipelineIdentifier)) {
      while (iterator.hasNext()) {
        toBeDeletedPlanExecutions.add(iterator.next().getPlanExecutionId());

        // If max deletion batch is reached, delete all its related entities
        // We don't want to delete all executions for a pipeline together as total delete could be very high
        if (toBeDeletedPlanExecutions.size() >= MAX_DELETION_BATCH_PROCESSING) {
          deletePipelineExecutionsDetailsInternal(toBeDeletedPlanExecutions, retainPipelineExecutionDetailsAfterDelete);
          toBeDeletedPlanExecutions.clear();
        }
      }
    }

    if (EmptyPredicate.isNotEmpty(toBeDeletedPlanExecutions)) {
      deletePipelineExecutionsDetailsInternal(toBeDeletedPlanExecutions, retainPipelineExecutionDetailsAfterDelete);
    }
  }

  @VisibleForTesting
  // Internal method which deletes all execution metadata for given planExecutions
  void deletePipelineExecutionsDetailsInternal(
      Set<String> planExecutionsToDelete, boolean retainPipelineExecutionDetailsAfterDelete) {
    CompletableFutures<Void> completableFutures = new CompletableFutures<>(pipelineExecutorService);

    completableFutures.supplyAsync(() -> { // Deletes the barrierInstances
      barrierService.deleteAllForGivenPlanExecutionId(planExecutionsToDelete);
      return null;
    });

    completableFutures.supplyAsync(() -> {
      // Delete sweepingOutput
      pmsSweepingOutputService.deleteAllSweepingOutputInstances(planExecutionsToDelete);
      return null;
    });

    completableFutures.supplyAsync(() -> {
      // Delete outcome instances
      pmsOutcomeService.deleteAllOutcomesInstances(planExecutionsToDelete);
      return null;
    });

    completableFutures.supplyAsync(() -> {
      // Delete all interrupts
      interruptService.deleteAllInterrupts(planExecutionsToDelete);
      return null;
    });

    completableFutures.supplyAsync(() -> {
      // Delete all graph metadata
      graphGenerationService.deleteAllGraphMetadataForGivenExecutionIds(
          planExecutionsToDelete, retainPipelineExecutionDetailsAfterDelete);
      return null;
    });

    completableFutures.supplyAsync(() -> {
      // Delete nodeExecutions and its metadata
      nodeExecutionService.deleteAllNodeExecutionAndMetadata(planExecutionsToDelete);
      return null;
    });

    completableFutures.supplyAsync(() -> {
      // Delete all planExecutions and its metadata
      planExecutionService.deleteAllPlanExecutionAndMetadata(
          planExecutionsToDelete, retainPipelineExecutionDetailsAfterDelete);
      return null;
    });

    completableFutures.supplyAsync(() -> {
      planExpansionService.deleteAllExpansions(planExecutionsToDelete);
      return null;
    });

    try {
      // waiting for all futures to get complete
      completableFutures.allOf().get(1, TimeUnit.HOURS);
    } catch (Exception e) {
      log.error("Error in processing delete event for pipeline");
    }
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
