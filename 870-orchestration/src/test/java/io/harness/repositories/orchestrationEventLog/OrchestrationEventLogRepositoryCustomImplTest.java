/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.orchestrationEventLog;

import static io.harness.rule.OwnerRule.SOUMYAJIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.beans.OrchestrationEventLog;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.MongoTemplate;

public class OrchestrationEventLogRepositoryCustomImplTest {
  @InjectMocks OrchestrationEventLogRepositoryCustomImpl orchImplClient;
  @Mock MongoTemplate mongoTemplate;

  String planExecutionID = "executionId";
  long lastUpdatedAt = 10;
  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = SOUMYAJIT)
  @Category(UnitTests.class)
  public void validateFindUnprocessedEvents() throws ExecutionException {
    OrchestrationEventLog newlog = OrchestrationEventLog.builder().build();
    List<OrchestrationEventLog> eventLogs = new ArrayList<>();
    eventLogs.add(newlog);
    doReturn(eventLogs).when(mongoTemplate).find(any(), any());
    List<OrchestrationEventLog> unprocessedEvents =
        orchImplClient.findUnprocessedEvents(planExecutionID, lastUpdatedAt);
    assertThat(unprocessedEvents).isEqualTo(eventLogs);
  }

  @Test
  @Owner(developers = SOUMYAJIT)
  @Category(UnitTests.class)
  public void validateDeleteLogsForGivenPlanExecutionId() throws ExecutionException {
    doReturn(null).when(mongoTemplate).remove(any(), eq(OrchestrationEventLog.class));
    orchImplClient.deleteLogsForGivenPlanExecutionId(planExecutionID);
    verify(mongoTemplate, times(1)).remove(any(), eq(OrchestrationEventLog.class));
  }
}
