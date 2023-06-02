/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.orchestrationEventLog;

import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.SOUMYAJIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import io.harness.OrchestrationTestBase;
import io.harness.beans.OrchestrationEventLog;
import io.harness.beans.OrchestrationEventLog.OrchestrationEventLogKeys;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

public class OrchestrationEventLogRepositoryCustomImplTest extends OrchestrationTestBase {
  @Mock MongoTemplate mongoTemplateMock;
  @InjectMocks OrchestrationEventLogRepositoryCustomImpl orchestrationRepository;
  @Inject MongoTemplate mongoTemplate;

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
    on(orchestrationRepository).set("mongoTemplate", mongoTemplateMock);
    List<OrchestrationEventLog> eventLogs = new ArrayList<>();
    eventLogs.add(newlog);
    doReturn(eventLogs).when(mongoTemplateMock).find(any(), any());
    List<OrchestrationEventLog> unprocessedEvents =
        orchestrationRepository.findUnprocessedEvents(planExecutionID, lastUpdatedAt, 1000);
    assertThat(unprocessedEvents).isEqualTo(eventLogs);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testDeleteAllOrchestrationLogEvents() {
    on(orchestrationRepository).set("mongoTemplate", mongoTemplate);
    OrchestrationEventLog log1 = OrchestrationEventLog.builder()
                                     .id(UUIDGenerator.generateUuid())
                                     .planExecutionId(planExecutionID)
                                     .createdAt(System.currentTimeMillis())
                                     .build();
    OrchestrationEventLog log2 = OrchestrationEventLog.builder()
                                     .id(UUIDGenerator.generateUuid())
                                     .planExecutionId("EXECUTION_2")
                                     .createdAt(System.currentTimeMillis())
                                     .build();
    OrchestrationEventLog log3 = OrchestrationEventLog.builder()
                                     .id(UUIDGenerator.generateUuid())
                                     .planExecutionId(planExecutionID)
                                     .createdAt(System.currentTimeMillis())
                                     .build();
    List<OrchestrationEventLog> eventLogs = new ArrayList<>();
    eventLogs.add(log1);
    eventLogs.add(log2);
    eventLogs.add(log3);
    mongoTemplate.insertAll(eventLogs);
    orchestrationRepository.deleteAllOrchestrationLogEvents(Set.of(planExecutionID));

    Criteria criteria = where(OrchestrationEventLogKeys.planExecutionId).in("EXECUTION_2");
    Query query = new Query(criteria);
    List<OrchestrationEventLog> orchestrationEventLogs = mongoTemplate.find(query, OrchestrationEventLog.class);
    assertThat(orchestrationEventLogs.size()).isEqualTo(1);
  }
}
