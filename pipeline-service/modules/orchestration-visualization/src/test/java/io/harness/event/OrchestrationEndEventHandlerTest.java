/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ALEXEI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.harness.OrchestrationVisualizationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.OrchestrationGraph;
import io.harness.cache.SpringMongoStore;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.execution.PlanExecution;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.TriggerType;
import io.harness.pms.contracts.plan.TriggeredBy;
import io.harness.repositories.orchestrationEventLog.OrchestrationEventLogRepository;
import io.harness.rule.Owner;
import io.harness.service.GraphGenerationService;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mockito;

/**
 * Test class for {@link OrchestrationEndGraphHandler}
 */
@OwnedBy(HarnessTeam.PIPELINE)
public class OrchestrationEndEventHandlerTest extends OrchestrationVisualizationTestBase {
  @Inject private SpringMongoStore mongoStore;

  @Inject PlanExecutionService planExecutionService;
  @Inject @InjectMocks GraphGenerationService graphGenerationService;

  private OrchestrationEndGraphHandler orchestrationEndEventHandler;

  @Before
  public void setUp() {
    ExecutorService executorService = Mockito.mock(ExecutorService.class);
    OrchestrationEventLogRepository orchestrationEventLogRepository = mock(OrchestrationEventLogRepository.class);
    orchestrationEndEventHandler =
        new OrchestrationEndGraphHandler(executorService, planExecutionService, graphGenerationService);
  }

  private static final ExecutionMetadata metadata =
      ExecutionMetadata.newBuilder()
          .setPipelineIdentifier(generateUuid())
          .setTriggerInfo(ExecutionTriggerInfo.newBuilder()
                              .setTriggerType(TriggerType.MANUAL)
                              .setTriggeredBy(TriggeredBy.newBuilder().setIdentifier("admin").build())
                              .build())
          .build();

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)

  @Ignore("Ignoring till injection issue is fixed")
  public void shouldUpdateGraphWithStatusAndEndTs() {
    PlanExecution planExecution = PlanExecution.builder()
                                      .uuid(generateUuid())
                                      .startTs(System.currentTimeMillis())
                                      .endTs(System.currentTimeMillis())
                                      .status(Status.SUCCEEDED)
                                      .metadata(metadata)
                                      .build();
    planExecutionService.save(planExecution);

    OrchestrationGraph orchestrationGraph = OrchestrationGraph.builder()
                                                .rootNodeIds(Lists.newArrayList(generateUuid()))
                                                .status(Status.RUNNING)
                                                .startTs(planExecution.getStartTs())
                                                .planExecutionId(planExecution.getUuid())
                                                .cacheKey(planExecution.getUuid())
                                                .cacheContextOrder(System.currentTimeMillis())
                                                .build();
    mongoStore.upsert(orchestrationGraph, Duration.ofDays(10));

    orchestrationEndEventHandler.onEnd(Ambiance.newBuilder().setPlanExecutionId(planExecution.getUuid()).build());

    OrchestrationGraph updatedGraph = graphGenerationService.getCachedOrchestrationGraph(planExecution.getUuid());
    assertThat(updatedGraph).isNotNull();
    assertThat(updatedGraph.getPlanExecutionId()).isEqualTo(planExecution.getUuid());
    assertThat(updatedGraph.getStartTs()).isEqualTo(planExecution.getStartTs());
    assertThat(updatedGraph.getEndTs()).isEqualTo(planExecution.getEndTs());
    assertThat(updatedGraph.getStatus()).isEqualTo(planExecution.getStatus());
  }
}
