/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.timeouts;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ALEXEI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.observers.NodeUpdateInfo;
import io.harness.execution.NodeExecution;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.rule.Owner;
import io.harness.timeout.TimeoutEngine;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import java.util.List;
import java.util.concurrent.ExecutorService;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.slf4j.LoggerFactory;

@OwnedBy(HarnessTeam.PIPELINE)
public class TimeoutInstanceRemoverTest extends OrchestrationTestBase {
  @Mock private ExecutorService executorService;
  @Mock private TimeoutEngine timeoutEngine;

  @Inject @InjectMocks private TimeoutInstanceRemover timeoutInstanceRemover;

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestOnNodeStatusUpdateWhenStatusExpired() {
    NodeExecution nodeExecution = NodeExecution.builder().uuid(generateUuid()).status(Status.EXPIRED).build();
    NodeUpdateInfo nodeUpdateInfo = NodeUpdateInfo.builder().nodeExecution(nodeExecution).build();

    timeoutInstanceRemover.onNodeStatusUpdate(nodeUpdateInfo);

    verifyZeroInteractions(timeoutEngine);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestOnNodeStatusUpdateWhenStatusIsNotFinal() {
    NodeExecution nodeExecution = NodeExecution.builder().uuid(generateUuid()).status(Status.RUNNING).build();
    NodeUpdateInfo nodeUpdateInfo = NodeUpdateInfo.builder().nodeExecution(nodeExecution).build();

    timeoutInstanceRemover.onNodeStatusUpdate(nodeUpdateInfo);

    verifyZeroInteractions(timeoutEngine);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestOnNodeStatusUpdate() {
    List<String> timeoutInstanceIds = ImmutableList.of(generateUuid());
    NodeExecution nodeExecution = NodeExecution.builder()
                                      .uuid(generateUuid())
                                      .ambiance(Ambiance.newBuilder().setPlanExecutionId(generateUuid()).build())
                                      .status(Status.SUCCEEDED)
                                      .timeoutInstanceIds(timeoutInstanceIds)
                                      .build();
    NodeUpdateInfo nodeUpdateInfo = NodeUpdateInfo.builder().nodeExecution(nodeExecution).build();

    doNothing().when(timeoutEngine).deleteTimeouts(anyList());

    Logger logger = (Logger) LoggerFactory.getLogger(TimeoutInstanceRemover.class);
    ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
    listAppender.start();

    logger.addAppender(listAppender);

    timeoutInstanceRemover.onNodeStatusUpdate(nodeUpdateInfo);

    assertThat(listAppender.list)
        .extracting(ILoggingEvent::getFormattedMessage, ILoggingEvent::getLevel)
        .containsExactly(
            tuple(String.format("Timeout instances %s are removed successfully", timeoutInstanceIds), Level.INFO));

    verify(timeoutEngine).deleteTimeouts(any());
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestOnNodeStatusUpdateWithException() {
    List<String> timeoutInstanceIds = ImmutableList.of(generateUuid());
    NodeExecution nodeExecution = NodeExecution.builder()
                                      .uuid(generateUuid())
                                      .ambiance(Ambiance.newBuilder().setPlanExecutionId(generateUuid()).build())
                                      .status(Status.SUCCEEDED)
                                      .timeoutInstanceIds(timeoutInstanceIds)
                                      .build();
    NodeUpdateInfo nodeUpdateInfo = NodeUpdateInfo.builder().nodeExecution(nodeExecution).build();

    doAnswer(invocation -> { throw new Exception(); }).when(timeoutEngine).deleteTimeouts(anyList());

    Logger logger = (Logger) LoggerFactory.getLogger(TimeoutInstanceRemover.class);
    ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
    listAppender.start();

    logger.addAppender(listAppender);

    timeoutInstanceRemover.onNodeStatusUpdate(nodeUpdateInfo);

    assertThat(listAppender.list)
        .extracting(ILoggingEvent::getFormattedMessage, ILoggingEvent::getLevel)
        .containsExactly(tuple("Failed to delete timeout instances " + timeoutInstanceIds, Level.ERROR));

    verify(timeoutEngine).deleteTimeouts(any());
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void getInformExecutorService() {
    assertThat(timeoutInstanceRemover.getInformExecutorService()).isEqualTo(executorService);
  }
}
