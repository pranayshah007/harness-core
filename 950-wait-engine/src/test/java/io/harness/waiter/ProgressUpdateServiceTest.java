/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.waiter;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ASHISHSANODIA;
import static io.harness.rule.OwnerRule.SAHIL;
import static io.harness.waiter.TestNotifyEventListener.TEST_PUBLISHER;

import static java.lang.System.currentTimeMillis;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.WaitEngineTestBase;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.threading.Morpheus;
import io.harness.waiter.persistence.PersistenceWrapper;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import org.junit.Test;
import org.junit.experimental.categories.Category;
public class ProgressUpdateServiceTest extends WaitEngineTestBase {
  String waitInstanceId = generateUuid();
  String correlationId = generateUuid();
  @Inject private PersistenceWrapper persistenceWrapper;
  @Inject private KryoSerializer kryoSerializer;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  @Inject private WaitInstanceService waitInstanceService;

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)

  public void testModifyAndFetchWaitInstanceForNoExistingResponse() throws InterruptedException {
    final WaitInstance waitInstance = WaitInstance.builder()
                                          .uuid(waitInstanceId)
                                          .callback(new TestNotifyCallback())
                                          .progressCallback(new TestProgressCallback())
                                          .publisher(TEST_PUBLISHER)
                                          .correlationIds(Collections.singletonList(correlationId))
                                          .waitingOnCorrelationIds(Collections.singletonList(correlationId))
                                          .build();
    persistenceWrapper.save(waitInstance);

    persistenceWrapper.save(ProgressUpdate.builder()
                                .uuid(generateUuid())
                                .correlationId(correlationId)
                                .createdAt(currentTimeMillis() - 1000)
                                .expireProcessing(currentTimeMillis() + 60000)
                                .progressData(kryoSerializer.asDeflatedBytes(
                                    StringNotifyProgressData.builder().data("progress1-" + generateUuid()).build()))
                                .build());

    persistenceWrapper.save(ProgressUpdate.builder()
                                .uuid(generateUuid())
                                .correlationId(correlationId)
                                .createdAt(currentTimeMillis())
                                .expireProcessing(currentTimeMillis())
                                .progressData(kryoSerializer.asDeflatedBytes(
                                    StringNotifyProgressData.builder().data("progress1-" + generateUuid()).build()))
                                .build());

    Morpheus.sleep(Duration.ofMinutes(2));
    assertThat(waitInstanceService.fetchForProcessingProgressUpdate(new HashSet<>(), currentTimeMillis())).isNull();
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)

  public void testModifyAndFetchWaitInstanceForNoExistingResponseUsingKryoWithoutReference()
      throws InterruptedException {
    final WaitInstance waitInstance = WaitInstance.builder()
                                          .uuid(waitInstanceId)
                                          .callback(new TestNotifyCallback())
                                          .progressCallback(new TestProgressCallback())
                                          .publisher(TEST_PUBLISHER)
                                          .correlationIds(Collections.singletonList(correlationId))
                                          .waitingOnCorrelationIds(Collections.singletonList(correlationId))
                                          .build();
    persistenceWrapper.save(waitInstance);

    persistenceWrapper.save(ProgressUpdate.builder()
                                .uuid(generateUuid())
                                .correlationId(correlationId)
                                .createdAt(currentTimeMillis() - 1000)
                                .expireProcessing(currentTimeMillis() + 60000)
                                .usingKryoWithoutReference(true)
                                .progressData(referenceFalseKryoSerializer.asDeflatedBytes(
                                    StringNotifyProgressData.builder().data("progress1-" + generateUuid()).build()))
                                .build());

    persistenceWrapper.save(ProgressUpdate.builder()
                                .uuid(generateUuid())
                                .correlationId(correlationId)
                                .createdAt(currentTimeMillis())
                                .expireProcessing(currentTimeMillis())
                                .usingKryoWithoutReference(true)
                                .progressData(referenceFalseKryoSerializer.asDeflatedBytes(
                                    StringNotifyProgressData.builder().data("progress1-" + generateUuid()).build()))
                                .build());

    Morpheus.sleep(Duration.ofMinutes(2));
    assertThat(waitInstanceService.fetchForProcessingProgressUpdate(new HashSet<>(), currentTimeMillis())).isNull();
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)

  public void testModifyAndFetchWaitInstanceForNoExistingResponseAndNullUsingKryoWithoutReference()
      throws InterruptedException {
    final WaitInstance waitInstance = WaitInstance.builder()
                                          .uuid(waitInstanceId)
                                          .callback(new TestNotifyCallback())
                                          .progressCallback(new TestProgressCallback())
                                          .publisher(TEST_PUBLISHER)
                                          .correlationIds(Collections.singletonList(correlationId))
                                          .waitingOnCorrelationIds(Collections.singletonList(correlationId))
                                          .build();
    persistenceWrapper.save(waitInstance);

    persistenceWrapper.save(ProgressUpdate.builder()
                                .uuid(generateUuid())
                                .correlationId(correlationId)
                                .createdAt(currentTimeMillis() - 1000)
                                .expireProcessing(currentTimeMillis() + 60000)
                                .usingKryoWithoutReference(null)
                                .progressData(kryoSerializer.asDeflatedBytes(
                                    StringNotifyProgressData.builder().data("progress1-" + generateUuid()).build()))
                                .build());

    persistenceWrapper.save(ProgressUpdate.builder()
                                .uuid(generateUuid())
                                .correlationId(correlationId)
                                .createdAt(currentTimeMillis())
                                .expireProcessing(currentTimeMillis())
                                .usingKryoWithoutReference(null)
                                .progressData(kryoSerializer.asDeflatedBytes(
                                    StringNotifyProgressData.builder().data("progress1-" + generateUuid()).build()))
                                .build());

    Morpheus.sleep(Duration.ofMinutes(2));
    assertThat(waitInstanceService.fetchForProcessingProgressUpdate(new HashSet<>(), currentTimeMillis())).isNull();
  }
}
