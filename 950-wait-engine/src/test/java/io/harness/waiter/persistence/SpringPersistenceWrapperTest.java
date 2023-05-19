/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.waiter.persistence;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.PRASHANT;
import static io.harness.waiter.TestNotifyEventListener.TEST_PUBLISHER;
import static io.harness.waiter.WaitInstanceService.MAX_CALLBACK_PROCESSING_TIME;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import io.harness.WaitEngineTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.timeout.TimeoutEngine;
import io.harness.waiter.NotifyResponse;
import io.harness.waiter.NotifyResponse.NotifyResponseKeys;
import io.harness.waiter.TestNotifyCallback;
import io.harness.waiter.TestProgressCallback;
import io.harness.waiter.WaitInstance;
import io.harness.waiter.WaitInstance.WaitInstanceKeys;

import com.google.inject.Inject;
import com.mongodb.client.result.DeleteResult;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import org.joor.Reflect;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

@OwnedBy(HarnessTeam.PIPELINE)
public class SpringPersistenceWrapperTest extends WaitEngineTestBase {
  @Mock TimeoutEngine timeoutEngine;
  @Mock MongoTemplate mongoTemplateMock;
  @Inject MongoTemplate mongoTemplate;
  @Inject SpringPersistenceWrapper persistenceWrapper;

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)

  public void testDeleteWaitInstancesWithinBatchSize() {
    Reflect.on(persistenceWrapper).set("timeoutEngine", timeoutEngine);
    Reflect.on(persistenceWrapper).set("mongoTemplate", mongoTemplateMock);
    String correlationId = generateUuid();
    List<String> correlationIds = new LinkedList<>();
    for (int i = 0; i < 100; i++) {
      correlationIds.add(String.valueOf(i));
    }
    correlationIds.add(correlationId);
    Query query = query(where(WaitInstanceKeys.correlationIds).in(correlationIds));
    query.fields().include(WaitInstanceKeys.timeoutInstanceId);

    Mockito.doReturn(Collections.emptyList()).when(mongoTemplateMock).findAllAndRemove(query, WaitInstance.class);
    DeleteResult acknowledged = DeleteResult.acknowledged(0);
    Mockito.doReturn(acknowledged)
        .when(mongoTemplateMock)
        .remove(query(where(NotifyResponseKeys.uuid).in(new ArrayList<>(correlationIds))), NotifyResponse.class);

    persistenceWrapper.deleteWaitInstancesAndMetadata(correlationIds);
    Mockito.verify(timeoutEngine, Mockito.times(1)).deleteTimeouts(Collections.emptyList());
    Mockito.verify(mongoTemplateMock, Mockito.times(1)).findAllAndRemove(query, WaitInstance.class);
    Mockito.verify(mongoTemplateMock, Mockito.times(1))
        .remove(query(where(NotifyResponseKeys.uuid).in(new ArrayList<>(correlationIds))), NotifyResponse.class);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)

  public void testSave() {
    String waitInstanceId = generateUuid();
    String correlationId = generateUuid();
    final WaitInstance waitInstance = WaitInstance.builder()
                                          .uuid(waitInstanceId)
                                          .callback(new TestNotifyCallback())
                                          .progressCallback(new TestProgressCallback())
                                          .publisher(TEST_PUBLISHER)
                                          .correlationIds(Collections.singletonList(correlationId))
                                          .waitingOnCorrelationIds(Collections.singletonList(correlationId))
                                          .build();

    String savedInstanceId = persistenceWrapper.save(waitInstance);
    assertThat(savedInstanceId).isEqualTo(waitInstanceId);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)

  public void testFetchForProcessingWaitInstance() {
    String waitInstanceId = generateUuid();
    String correlationId = generateUuid();
    final WaitInstance waitInstance = WaitInstance.builder()
                                          .uuid(waitInstanceId)
                                          .callback(new TestNotifyCallback())
                                          .progressCallback(new TestProgressCallback())
                                          .publisher(TEST_PUBLISHER)
                                          .correlationIds(Collections.singletonList(correlationId))
                                          .waitingOnCorrelationIds(Collections.singletonList(correlationId))
                                          .build();
    mongoTemplate.save(waitInstance);

    long now = System.currentTimeMillis();
    persistenceWrapper.fetchForProcessingWaitInstance(waitInstanceId, now);
    WaitInstance processedWaitInstance =
        mongoTemplate.findOne(query(where(WaitInstanceKeys.uuid).is(waitInstanceId)), WaitInstance.class);
    assertThat(processedWaitInstance.getCallbackProcessingAt())
        .isEqualTo(now + MAX_CALLBACK_PROCESSING_TIME.toMillis());
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)

  public void testModifyAndFetchWaitInstance() {
    String waitInstanceId = generateUuid();
    String correlationId = generateUuid();
    final WaitInstance waitInstance = WaitInstance.builder()
                                          .uuid(waitInstanceId)
                                          .callback(new TestNotifyCallback())
                                          .progressCallback(new TestProgressCallback())
                                          .publisher(TEST_PUBLISHER)
                                          .correlationIds(Collections.singletonList(correlationId))
                                          .waitingOnCorrelationIds(Collections.singletonList(correlationId))
                                          .build();
    mongoTemplate.save(waitInstance);
    WaitInstance modifiedWaitInstance = persistenceWrapper.modifyAndFetchWaitInstance(correlationId);
    assertThat(modifiedWaitInstance.getCorrelationIds()).containsExactly(correlationId);
    assertThat(modifiedWaitInstance.getWaitingOnCorrelationIds()).isEmpty();
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)

  public void testModifyAndFetchWaitInstanceForNoExistingResponse() {
    String waitInstanceId = generateUuid();
    String correlationId = generateUuid();
    final WaitInstance waitInstance = WaitInstance.builder()
                                          .uuid(waitInstanceId)
                                          .callback(new TestNotifyCallback())
                                          .progressCallback(new TestProgressCallback())
                                          .publisher(TEST_PUBLISHER)
                                          .correlationIds(Collections.singletonList(correlationId))
                                          .waitingOnCorrelationIds(Collections.singletonList(correlationId))
                                          .build();
    mongoTemplate.save(waitInstance);
    WaitInstance modifiedWaitInstance = persistenceWrapper.modifyAndFetchWaitInstanceForExistingResponse(
        waitInstanceId, Collections.singletonList(correlationId));
    assertThat(modifiedWaitInstance).isNull();
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)

  public void testModifyAndFetchWaitInstanceForExistingResponse() {
    String waitInstanceId = generateUuid();
    String correlationId = generateUuid();
    final WaitInstance waitInstance = WaitInstance.builder()
                                          .uuid(waitInstanceId)
                                          .callback(new TestNotifyCallback())
                                          .progressCallback(new TestProgressCallback())
                                          .publisher(TEST_PUBLISHER)
                                          .correlationIds(Collections.singletonList(correlationId))
                                          .waitingOnCorrelationIds(Collections.singletonList(correlationId))
                                          .build();
    mongoTemplate.save(waitInstance);

    final NotifyResponse response =
        NotifyResponse.builder().uuid(correlationId).responseData(new byte[] {}).error(false).build();
    mongoTemplate.save(response);

    WaitInstance modifiedWaitInstance = persistenceWrapper.modifyAndFetchWaitInstanceForExistingResponse(
        waitInstanceId, Collections.singletonList(correlationId));
    assertThat(modifiedWaitInstance).isNotNull();
    assertThat(modifiedWaitInstance.getWaitingOnCorrelationIds()).isEmpty();
    assertThat(modifiedWaitInstance.getCorrelationIds()).containsExactly(correlationId);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)

  public void testFetchWaitInstances() {
    String waitInstanceId1 = generateUuid();
    String waitInstanceId2 = generateUuid();
    String correlationId1 = generateUuid();
    String correlationId2 = generateUuid();
    final WaitInstance waitInstance1 = WaitInstance.builder()
                                           .uuid(waitInstanceId1)
                                           .callback(new TestNotifyCallback())
                                           .progressCallback(new TestProgressCallback())
                                           .publisher(TEST_PUBLISHER)
                                           .correlationIds(Arrays.asList(correlationId1, correlationId2))
                                           .waitingOnCorrelationIds(Arrays.asList(correlationId1, correlationId2))
                                           .build();

    final WaitInstance waitInstance2 = WaitInstance.builder()
                                           .uuid(waitInstanceId2)
                                           .callback(new TestNotifyCallback())
                                           .progressCallback(new TestProgressCallback())
                                           .publisher(TEST_PUBLISHER)
                                           .correlationIds(Arrays.asList(correlationId1))
                                           .waitingOnCorrelationIds(Arrays.asList(correlationId1))
                                           .build();
    mongoTemplate.save(waitInstance1);
    mongoTemplate.save(waitInstance2);

    List<WaitInstance> waitInstances = persistenceWrapper.fetchWaitInstances(correlationId1);
    assertThat(waitInstances).isNotEmpty();
    assertThat(waitInstances).hasSize(2);
    assertThat(waitInstances.stream().map(WaitInstance::getUuid).collect(Collectors.toList()))
        .containsExactlyInAnyOrder(waitInstanceId1, waitInstanceId2);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)

  public void shouldTestFetchNotifyResponseKeys() {
    long now = System.currentTimeMillis();
    long queryTime = now - Duration.ofSeconds(10).toMillis();

    NotifyResponse response1 = NotifyResponse.builder()
                                   .uuid(generateUuid())
                                   .createdAt(now - Duration.ofSeconds(20).toMillis())
                                   .responseData(new byte[] {})
                                   .error(false)
                                   .build();

    NotifyResponse response2 =
        NotifyResponse.builder().uuid(generateUuid()).createdAt(now).responseData(new byte[] {}).error(false).build();

    mongoTemplate.save(response1);
    mongoTemplate.save(response2);

    List<String> keyList = persistenceWrapper.fetchNotifyResponseKeys(queryTime);
    assertThat(keyList).hasSize(1);
    assertThat(keyList).containsExactly(response1.getUuid());
  }
}
