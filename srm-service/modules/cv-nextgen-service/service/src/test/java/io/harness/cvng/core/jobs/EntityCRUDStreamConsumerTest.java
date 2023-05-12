/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.jobs;

import static io.harness.rule.OwnerRule.ABHIJITH;

import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.harness.CvNextGenTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.api.Consumer;
import io.harness.queue.QueueController;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CV)
public class EntityCRUDStreamConsumerTest extends CvNextGenTestBase {
  @Inject private EntityCRUDStreamConsumer entityCRUDStreamConsumer;

  @Mock private Consumer consumer;
  @Mock private QueueController queueController;

  @Before
  public void setup() throws IllegalAccessException {
    MockitoAnnotations.initMocks(this);
    when(queueController.isNotPrimary()).thenReturn(false);
    FieldUtils.writeField(entityCRUDStreamConsumer, "queueController", queueController, true);
    FieldUtils.writeField(entityCRUDStreamConsumer, "consumer", consumer, true);
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testStreamConsumerOnPrimaryMachine() throws InterruptedException {
    runConsumerFor100ms();
    verify(consumer, atLeast(1)).read(Mockito.any());
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testStreamConsumerOnNonPrimaryMachine() throws InterruptedException {
    when(queueController.isNotPrimary()).thenReturn(true);
    runConsumerFor100ms();
    verifyNoInteractions(consumer);
  }

  private void runConsumerFor100ms() throws InterruptedException {
    Thread t = new Thread(entityCRUDStreamConsumer);
    t.start();
    TimeUnit.MILLISECONDS.sleep(100);
    t.interrupt();
  }
}
