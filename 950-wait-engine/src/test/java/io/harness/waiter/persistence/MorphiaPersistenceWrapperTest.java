/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.waiter.persistence;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.PRASHANT;
import static io.harness.waiter.TestNotifyEventListener.TEST_PUBLISHER;
import static io.harness.waiter.WaitInstanceService.MAX_CALLBACK_PROCESSING_TIME;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.WaitEngineTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.waiter.NotifyResponse;
import io.harness.waiter.TestNotifyCallback;
import io.harness.waiter.TestProgressCallback;
import io.harness.waiter.WaitInstance;

import com.google.inject.Inject;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.DEL)
public class MorphiaPersistenceWrapperTest extends WaitEngineTestBase {
  @Inject MorphiaPersistenceWrapper persistenceWrapper;
  @Inject HPersistence hPersistence;

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
    hPersistence.save(waitInstance);

    long now = System.currentTimeMillis();
    persistenceWrapper.fetchForProcessingWaitInstance(waitInstanceId, now);
    WaitInstance processedWaitInstance = hPersistence.get(WaitInstance.class, waitInstanceId);
    assertThat(processedWaitInstance.getCallbackProcessingAt())
        .isEqualTo(now + MAX_CALLBACK_PROCESSING_TIME.toMillis());
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
    hPersistence.save(waitInstance1);
    hPersistence.save(waitInstance2);

    List<WaitInstance> waitInstances = persistenceWrapper.fetchWaitInstances(correlationId1);
    assertThat(waitInstances).isNotEmpty();
    assertThat(waitInstances).hasSize(2);
    assertThat(waitInstances.stream().map(WaitInstance::getUuid).collect(Collectors.toList()))
        .containsExactlyInAnyOrder(waitInstanceId1, waitInstanceId2);
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
    hPersistence.save(waitInstance);
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
    hPersistence.save(waitInstance);
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
    hPersistence.save(waitInstance);

    final NotifyResponse response =
        NotifyResponse.builder().uuid(correlationId).responseData(new byte[] {}).error(false).build();
    hPersistence.save(response);

    WaitInstance modifiedWaitInstance = persistenceWrapper.modifyAndFetchWaitInstanceForExistingResponse(
        waitInstanceId, Collections.singletonList(correlationId));
    assertThat(modifiedWaitInstance).isNotNull();
    assertThat(modifiedWaitInstance.getWaitingOnCorrelationIds()).isEmpty();
    assertThat(modifiedWaitInstance.getCorrelationIds()).containsExactly(correlationId);
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

    hPersistence.save(response1);
    hPersistence.save(response2);

    List<String> keyList = persistenceWrapper.fetchNotifyResponseKeys(queryTime);
    assertThat(keyList).hasSize(1);
    assertThat(keyList).containsExactly(response1.getUuid());
  }
}
