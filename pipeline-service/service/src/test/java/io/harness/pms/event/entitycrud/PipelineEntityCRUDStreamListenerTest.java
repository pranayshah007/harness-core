/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.event.entitycrud;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.CREATE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DELETE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.PIPELINE_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.USER_ENTITY;
import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.MEET;

import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.PipelineServiceTestHelper;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.engine.interrupts.InterruptService;
import io.harness.engine.pms.data.PmsOutcomeService;
import io.harness.engine.pms.data.PmsSweepingOutputService;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.expansion.PlanExpansionService;
import io.harness.ngtriggers.service.NGTriggerEventsService;
import io.harness.ngtriggers.service.NGTriggerService;
import io.harness.pms.contracts.interrupts.InterruptEvent;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.pms.pipeline.service.PipelineMetadataService;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.service.PmsExecutionSummaryService;
import io.harness.pms.preflight.service.PreflightService;
import io.harness.rule.Owner;
import io.harness.service.GraphGenerationService;
import io.harness.steps.barriers.service.BarrierService;

import com.google.protobuf.ByteString;
import com.google.protobuf.StringValue;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.util.CloseableIterator;

@OwnedBy(PIPELINE)
public class PipelineEntityCRUDStreamListenerTest extends CategoryTest {
  @Mock private NGTriggerService ngTriggerService;
  @Mock private NGTriggerEventsService ngTriggerEventsService;
  @Mock private PipelineMetadataService pipelineMetadataService;
  @Mock private PmsExecutionSummaryService pmsExecutionSummaryService;
  @Mock private BarrierService barrierService;
  @Mock private PreflightService preflightService;
  @Mock private PmsSweepingOutputService pmsSweepingOutputService;
  @Mock private PmsOutcomeService pmsOutcomeService;
  @Mock private InterruptService interruptService;
  @Mock private GraphGenerationService graphGenerationService;
  @Mock private NodeExecutionService nodeExecutionService;
  @Mock private PlanExecutionService planExecutionService;
  @Mock private PlanExpansionService planExpansionService;
  @InjectMocks PipelineEntityCRUDStreamListener pipelineEntityCRUDStreamListener;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testEmptyHandleMessage() {
    Message message = Message.newBuilder().build();
    assertTrue(pipelineEntityCRUDStreamListener.handleMessage(message));
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testNonPipelineEntityEventHandleMessage() {
    // Action type is not delete and even entity type is not pipeline
    Message message =
        Message.newBuilder()
            .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                            .putMetadata(ENTITY_TYPE, USER_ENTITY)
                            .putMetadata(ACTION, CREATE_ACTION)
                            .setData(InterruptEvent.newBuilder().setType(InterruptType.ABORT).build().toByteString())
                            .build())
            .build();
    assertTrue(pipelineEntityCRUDStreamListener.handleMessage(message));
    // Zero interaction with any one of pipeline metadata delete
    verify(ngTriggerService, times(0)).deleteAllForPipeline(any(), any(), any(), any());

    // Zero interaction with any one of pipeline execution delete
    verify(pmsExecutionSummaryService, times(0)).fetchPlanExecutionIdsFromAnalytics(any(), any(), any(), any());

    // Action type is not delete but entity is pipeline
    message =
        Message.newBuilder()
            .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                            .putMetadata(ENTITY_TYPE, PIPELINE_ENTITY)
                            .putMetadata(ACTION, CREATE_ACTION)
                            .setData(InterruptEvent.newBuilder().setType(InterruptType.ABORT).build().toByteString())
                            .build())
            .build();
    assertTrue(pipelineEntityCRUDStreamListener.handleMessage(message));
    // Zero interaction with any one of pipeline metadata delete
    verify(ngTriggerService, times(0)).deleteAllForPipeline(any(), any(), any(), any());

    // Zero interaction with any one of pipeline execution delete
    verify(pmsExecutionSummaryService, times(0)).fetchPlanExecutionIdsFromAnalytics(any(), any(), any(), any());

    // Data is not parsable into EntityChangeDTO
    message = Message.newBuilder()
                  .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                                  .putMetadata(ENTITY_TYPE, PIPELINE_ENTITY)
                                  .putMetadata(ACTION, CREATE_ACTION)
                                  .setData(ByteString.copyFromUtf8("Dummy"))
                                  .build())
                  .build();
    Message finalMessage = message;
    assertThatCode(() -> pipelineEntityCRUDStreamListener.handleMessage(finalMessage))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testHandleMessageWithZeroExecutions() {
    String ACCOUNT_ID = "accountId";
    String ORG_ID = "orgId";
    String PROJECT_ID = "projectId";
    String PIPELINE_ID = "pipelineId";

    Message message = Message.newBuilder()
                          .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                                          .putMetadata(ENTITY_TYPE, PIPELINE_ENTITY)
                                          .putMetadata(ACTION, DELETE_ACTION)
                                          .setData(EntityChangeDTO.newBuilder()
                                                       .setAccountIdentifier(StringValue.of(ACCOUNT_ID))
                                                       .setOrgIdentifier(StringValue.of(ORG_ID))
                                                       .setProjectIdentifier(StringValue.of(PROJECT_ID))
                                                       .setIdentifier(StringValue.of(PIPELINE_ID))
                                                       .build()
                                                       .toByteString())
                                          .build())
                          .build();

    CloseableIterator<PipelineExecutionSummaryEntity> executionsIterator =
        PipelineServiceTestHelper.createCloseableIterator(Collections.emptyIterator());
    doReturn(executionsIterator)
        .when(pmsExecutionSummaryService)
        .fetchPlanExecutionIdsFromAnalytics(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID);

    assertTrue(pipelineEntityCRUDStreamListener.handleMessage(message));

    // Verify pipeline metadata delete
    verify(ngTriggerService, times(1)).deleteAllForPipeline(any(), any(), any(), any());
    verify(pipelineMetadataService, times(1)).deletePipelineMetadata(any(), any(), any(), any());
    verify(preflightService, times(1)).deleteAllPreflightEntityForGivenPipeline(any(), any(), any(), any());

    // Execution ids call only once as empty list
    verify(pmsExecutionSummaryService, times(1)).fetchPlanExecutionIdsFromAnalytics(any(), any(), any(), any());
    // Verify execution delete calls
    verify(barrierService, times(0)).deleteAllForGivenPlanExecutionId(any());
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testHandleMessageWithExecutionsMoreThanBatchSize() {
    String ACCOUNT_ID = "accountId";
    String ORG_ID = "orgId";
    String PROJECT_ID = "projectId";
    String PIPELINE_ID = "pipelineId";

    Message message = Message.newBuilder()
                          .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                                          .putMetadata(ENTITY_TYPE, PIPELINE_ENTITY)
                                          .putMetadata(ACTION, DELETE_ACTION)
                                          .setData(EntityChangeDTO.newBuilder()
                                                       .setAccountIdentifier(StringValue.of(ACCOUNT_ID))
                                                       .setOrgIdentifier(StringValue.of(ORG_ID))
                                                       .setProjectIdentifier(StringValue.of(PROJECT_ID))
                                                       .setIdentifier(StringValue.of(PIPELINE_ID))
                                                       .build()
                                                       .toByteString())
                                          .build())
                          .build();

    List<PipelineExecutionSummaryEntity> executionIds = new LinkedList<>();
    for (int i = 0; i < 100; i++) {
      PipelineExecutionSummaryEntity entity =
          PipelineExecutionSummaryEntity.builder().planExecutionId(String.valueOf(i)).build();
      executionIds.add(entity);
    }

    CloseableIterator<PipelineExecutionSummaryEntity> executionsIterator =
        PipelineServiceTestHelper.createCloseableIterator(executionIds.iterator());
    doReturn(executionsIterator)
        .when(pmsExecutionSummaryService)
        .fetchPlanExecutionIdsFromAnalytics(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID);

    assertTrue(pipelineEntityCRUDStreamListener.handleMessage(message));

    // Verify pipeline metadata delete as its called only once
    verify(ngTriggerService, times(1)).deleteAllForPipeline(any(), any(), any(), any());
    verify(pipelineMetadataService, times(1)).deletePipelineMetadata(any(), any(), any(), any());
    verify(preflightService, times(1)).deleteAllPreflightEntityForGivenPipeline(any(), any(), any(), any());

    // Execution ids call only once as empty list
    verify(pmsExecutionSummaryService, times(1)).fetchPlanExecutionIdsFromAnalytics(any(), any(), any(), any());
    // Verify execution delete calls
    verify(barrierService, times(2)).deleteAllForGivenPlanExecutionId(any());
    // Verify Delete sweepingOutput
    verify(pmsSweepingOutputService, times(2)).deleteAllSweepingOutputInstances(any());
    // Verify Delete outcome instances
    verify(pmsOutcomeService, times(2)).deleteAllOutcomesInstances(any());
    // Verify Delete all interrupts
    verify(interruptService, times(2)).deleteAllInterrupts(any());
    // Verify graph metadata delete
    verify(graphGenerationService, times(2)).deleteAllGraphMetadataForGivenExecutionIds(any());
    // Verify nodeExecutions and its metadata delete
    verify(nodeExecutionService, times(100)).deleteAllNodeExecutionAndMetadata(any());
    // Verify planExecutions and its metadata delete
    verify(planExecutionService, times(2)).deleteAllPlanExecutionAndMetadata(any());
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testHandleMessageWithExecutionsLessThanBatchSize() {
    String ACCOUNT_ID = "accountId";
    String ORG_ID = "orgId";
    String PROJECT_ID = "projectId";
    String PIPELINE_ID = "pipelineId";

    Message message = Message.newBuilder()
                          .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                                          .putMetadata(ENTITY_TYPE, PIPELINE_ENTITY)
                                          .putMetadata(ACTION, DELETE_ACTION)
                                          .setData(EntityChangeDTO.newBuilder()
                                                       .setAccountIdentifier(StringValue.of(ACCOUNT_ID))
                                                       .setOrgIdentifier(StringValue.of(ORG_ID))
                                                       .setProjectIdentifier(StringValue.of(PROJECT_ID))
                                                       .setIdentifier(StringValue.of(PIPELINE_ID))
                                                       .build()
                                                       .toByteString())
                                          .build())
                          .build();

    List<PipelineExecutionSummaryEntity> executionIds = new LinkedList<>();
    for (int i = 0; i < 40; i++) {
      PipelineExecutionSummaryEntity entity =
          PipelineExecutionSummaryEntity.builder().planExecutionId(String.valueOf(i)).build();
      executionIds.add(entity);
    }

    CloseableIterator<PipelineExecutionSummaryEntity> executionsIterator =
        PipelineServiceTestHelper.createCloseableIterator(executionIds.iterator());
    doReturn(executionsIterator)
        .when(pmsExecutionSummaryService)
        .fetchPlanExecutionIdsFromAnalytics(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID);

    assertTrue(pipelineEntityCRUDStreamListener.handleMessage(message));

    // Verify pipeline metadata delete as its called only once
    verify(ngTriggerService, times(1)).deleteAllForPipeline(any(), any(), any(), any());
    verify(pipelineMetadataService, times(1)).deletePipelineMetadata(any(), any(), any(), any());
    verify(preflightService, times(1)).deleteAllPreflightEntityForGivenPipeline(any(), any(), any(), any());

    // Execution ids call only once as empty list
    verify(pmsExecutionSummaryService, times(1)).fetchPlanExecutionIdsFromAnalytics(any(), any(), any(), any());
    // Verify execution delete calls
    verify(barrierService, times(1)).deleteAllForGivenPlanExecutionId(any());
    // Verify Delete sweepingOutput
    verify(pmsSweepingOutputService, times(1)).deleteAllSweepingOutputInstances(any());
    // Verify Delete outcome instances
    verify(pmsOutcomeService, times(1)).deleteAllOutcomesInstances(any());
    // Verify Delete all interrupts
    verify(interruptService, times(1)).deleteAllInterrupts(any());
    // Verify graph metadata delete
    verify(graphGenerationService, times(1)).deleteAllGraphMetadataForGivenExecutionIds(any());
    // Verify nodeExecutions and its metadata delete
    verify(nodeExecutionService, times(40)).deleteAllNodeExecutionAndMetadata(any());
    // Verify planExecutions and its metadata delete
    verify(planExecutionService, times(1)).deleteAllPlanExecutionAndMetadata(any());
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testTriggerEventDeletionOnPipelineDeletion() {
    String ACCOUNT_ID = "accountId";
    String ORG_ID = "orgId";
    String PROJECT_ID = "projectId";
    String PIPELINE_ID = "pipelineId";

    Message message = Message.newBuilder()
                          .setMessage(io.harness.eventsframework.producer.Message.newBuilder()
                                          .putMetadata(ENTITY_TYPE, PIPELINE_ENTITY)
                                          .putMetadata(ACTION, DELETE_ACTION)
                                          .setData(EntityChangeDTO.newBuilder()
                                                       .setAccountIdentifier(StringValue.of(ACCOUNT_ID))
                                                       .setOrgIdentifier(StringValue.of(ORG_ID))
                                                       .setProjectIdentifier(StringValue.of(PROJECT_ID))
                                                       .setIdentifier(StringValue.of(PIPELINE_ID))
                                                       .build()
                                                       .toByteString())
                                          .build())
                          .build();
    List<PipelineExecutionSummaryEntity> executionIds = new LinkedList<>();
    for (int i = 0; i < 40; i++) {
      PipelineExecutionSummaryEntity entity =
          PipelineExecutionSummaryEntity.builder().planExecutionId(String.valueOf(i)).build();
      executionIds.add(entity);
    }

    CloseableIterator<PipelineExecutionSummaryEntity> executionsIterator =
        PipelineServiceTestHelper.createCloseableIterator(executionIds.iterator());
    doReturn(executionsIterator)
        .when(pmsExecutionSummaryService)
        .fetchPlanExecutionIdsFromAnalytics(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID);

    assertTrue(pipelineEntityCRUDStreamListener.handleMessage(message));
    verify(ngTriggerEventsService, times(1)).deleteAllForPipeline(any(), any(), any(), any());
  }
}