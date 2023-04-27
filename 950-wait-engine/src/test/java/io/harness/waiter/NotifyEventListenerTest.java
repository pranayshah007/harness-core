/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.waiter;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.PRASHANT;
import static io.harness.waiter.NotifyEvent.Builder.aNotifyEvent;
import static io.harness.waiter.TestNotifyEventListener.TEST_PUBLISHER;

import static com.google.common.collect.ImmutableMap.of;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.WaitEngineTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.tasks.ProgressData;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.data.mongodb.core.MongoTemplate;

@OwnedBy(HarnessTeam.PIPELINE)
public class NotifyEventListenerTest extends WaitEngineTestBase {
  private static AtomicInteger callCount;
  private static AtomicInteger progressCallCount;
  private static Map<String, ResponseData> responseMap;
  private static List<ProgressData> progressDataList;

  @Inject private NotifyEventListener notifyEventListener;
  @Inject private HPersistence hPersistence;
  @Inject private MongoTemplate mongoTemplate;
  @Inject private KryoSerializer kryoSerializer;

  @Before
  public void setupResponseMap() {
    callCount = new AtomicInteger(0);
    responseMap = new HashMap<>();
    progressCallCount = new AtomicInteger(0);
    progressDataList = new ArrayList<>();
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)

  public void testOnMessageWithMorphia() {
    String waitInstanceId = generateUuid();
    String correlationId = generateUuid();
    final WaitInstance waitInstance = populateWaitInstance(waitInstanceId, correlationId);
    hPersistence.save(waitInstance);
    TestResponseData responseData = TestResponseData.builder().responseString("MorphiaResponseData").build();
    final NotifyResponse response = populateNotifyResponse(correlationId, responseData);
    hPersistence.save(response);
    performTest(waitInstanceId, correlationId, responseData);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)

  @SpringWaiter
  public void testOnMessageWithSpring() {
    String waitInstanceId = generateUuid();
    String correlationId = generateUuid();
    final WaitInstance waitInstance = populateWaitInstance(waitInstanceId, correlationId);
    mongoTemplate.save(waitInstance);
    TestResponseData responseData = TestResponseData.builder().responseString("MorphiaResponseData").build();
    final NotifyResponse response = populateNotifyResponse(correlationId, responseData);
    mongoTemplate.save(response);
    performTest(waitInstanceId, correlationId, responseData);
  }

  public void performTest(String waitInstanceId, String correlationId, TestResponseData responseData) {
    notifyEventListener.onMessage(aNotifyEvent().waitInstanceId(waitInstanceId).build());

    assertThat(responseMap).hasSize(1).isEqualTo(of(correlationId, responseData));
    assertThat(callCount.get()).isEqualTo(1);
  }

  public NotifyResponse populateNotifyResponse(String correlationId, TestResponseData responseData) {
    return NotifyResponse.builder()
        .uuid(correlationId)
        .usingKryoWithoutReference(null)
        .responseData(kryoSerializer.asDeflatedBytes(responseData))
        .error(false)
        .build();
  }

  public WaitInstance populateWaitInstance(String waitInstanceId, String correlationId) {
    TestNotifyCallback notifyCallback = new TestNotifyCallback();
    TestProgressCallback progressCallback = new TestProgressCallback();
    return WaitInstance.builder()
        .uuid(waitInstanceId)
        .callback(notifyCallback)
        .progressCallback(progressCallback)
        .publisher(TEST_PUBLISHER)
        .correlationIds(Collections.singletonList(correlationId))
        .waitingOnCorrelationIds(Collections.singletonList(correlationId))
        .build();
  }

  public static class TestNotifyCallback implements OldNotifyCallback {
    @Override
    public void notify(Map<String, ResponseData> response) {
      callCount.incrementAndGet();
      responseMap.putAll(response);
    }

    @Override
    public void notifyError(Map<String, ResponseData> response) {
      // Do Nothing.
    }
  }

  public static class TestProgressCallback implements ProgressCallback {
    @Override
    public void notify(String correlationId, ProgressData progressData) {
      progressCallCount.incrementAndGet();
      progressDataList.add(progressData);
    }
  }
}
