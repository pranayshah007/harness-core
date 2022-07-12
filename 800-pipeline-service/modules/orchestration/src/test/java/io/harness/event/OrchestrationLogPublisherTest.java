/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event;

import static io.harness.data.structure.HarnessStringUtils.emptyIfNull;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ALEXEI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.OrchestrationEventLog;
import io.harness.category.element.UnitTests;
import io.harness.engine.observers.NodeUpdateInfo;
import io.harness.engine.observers.StepDetailsUpdateInfo;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.execution.NodeExecution;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.events.OrchestrationEventType;
import io.harness.pms.contracts.visualisation.log.OrchestrationLogEvent;
import io.harness.repositories.orchestrationEventLog.OrchestrationEventLogRepository;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableMap;
import java.sql.Date;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.PIPELINE)
public class OrchestrationLogPublisherTest extends OrchestrationTestBase {
  private static final String planExecutionId = generateUuid();
  private static final String nodeExecutionId = generateUuid();
  private static final OrchestrationLogEvent orchestrationLogEvent =
      OrchestrationLogEvent.newBuilder().setPlanExecutionId(planExecutionId).build();

  @Mock private OrchestrationEventLogRepository repository;
  @Mock private Producer producer;
  @Mock private OrchestrationLogConfiguration orchestrationLogConfiguration;
  @InjectMocks private OrchestrationLogPublisher publisher;

  @Before
  public void setUp() throws IllegalAccessException {
    FieldUtils.writeField(publisher, "producer", producer, true);
    FieldUtils.writeField(publisher, "orchestrationEventLogRepository", repository, true);

    when(orchestrationLogConfiguration.isShouldUseBatching()).thenReturn(false);
    when(producer.send(any())).thenReturn(null);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestOnNodeStatusUpdate() {
    NodeUpdateInfo nodeUpdateInfo = NodeUpdateInfo.builder().nodeExecution(getNodeExecution()).build();
    OrchestrationEventLog orchestrationEventLog =
        getOrchestrationEventLog(OrchestrationEventType.NODE_EXECUTION_STATUS_UPDATE);

    when(repository.save(any())).thenReturn(orchestrationEventLog);

    publisher.onNodeStatusUpdate(nodeUpdateInfo);
    shouldTestOnNodeInternally(OrchestrationEventType.NODE_EXECUTION_STATUS_UPDATE);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void onPlanStatusUpdate() {
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(planExecutionId)
                            .addLevels(Level.newBuilder().setRuntimeId(nodeExecutionId).build())
                            .build();
    OrchestrationEventLog orchestrationEventLog =
        getOrchestrationEventLog(OrchestrationEventType.PLAN_EXECUTION_STATUS_UPDATE);

    when(repository.save(any())).thenReturn(orchestrationEventLog);

    publisher.onPlanStatusUpdate(ambiance);
    shouldTestOnNodeInternally(OrchestrationEventType.PLAN_EXECUTION_STATUS_UPDATE);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestOnNodeUpdate() {
    NodeUpdateInfo nodeUpdateInfo = NodeUpdateInfo.builder().nodeExecution(getNodeExecution()).build();
    OrchestrationEventLog orchestrationEventLog =
        getOrchestrationEventLog(OrchestrationEventType.NODE_EXECUTION_UPDATE);

    when(repository.save(any())).thenReturn(orchestrationEventLog);

    publisher.onNodeUpdate(nodeUpdateInfo);
    shouldTestOnNodeInternally(OrchestrationEventType.NODE_EXECUTION_UPDATE);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void onStepDetailsUpdate() {
    StepDetailsUpdateInfo stepDetailsUpdateInfo =
        StepDetailsUpdateInfo.builder().planExecutionId(planExecutionId).nodeExecutionId(nodeExecutionId).build();

    OrchestrationEventLog orchestrationEventLog = getOrchestrationEventLog(OrchestrationEventType.STEP_DETAILS_UPDATE);

    when(repository.save(any())).thenReturn(orchestrationEventLog);

    publisher.onStepDetailsUpdate(stepDetailsUpdateInfo);
    shouldTestOnNodeInternally(OrchestrationEventType.STEP_DETAILS_UPDATE);
  }

  private void shouldTestOnNodeInternally(OrchestrationEventType eventType) {
    ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
    verify(producer).send(messageArgumentCaptor.capture());

    Message message = messageArgumentCaptor.getValue();
    assertThat(message.getData()).isEqualTo(orchestrationLogEvent.toByteString());
    assertThat(message.getMetadataMap())
        .isEqualTo(ImmutableMap.of("nodeExecutionId", emptyIfNull(nodeExecutionId), "planExecutionId", planExecutionId,
            "eventType", eventType.name()));
  }

  private OrchestrationEventLog getOrchestrationEventLog(OrchestrationEventType eventType) {
    return OrchestrationEventLog.builder()
        .createdAt(System.currentTimeMillis())
        .nodeExecutionId(nodeExecutionId)
        .orchestrationEventType(eventType)
        .planExecutionId(planExecutionId)
        .validUntil(Date.from(OffsetDateTime.now().plus(Duration.ofDays(14)).toInstant()))
        .build();
  }

  private NodeExecution getNodeExecution() {
    return NodeExecution.builder()
        .uuid(nodeExecutionId)
        .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionId).build())
        .status(Status.SUCCEEDED)
        .build();
  }
}
