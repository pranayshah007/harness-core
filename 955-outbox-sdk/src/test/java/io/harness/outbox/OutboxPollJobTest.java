/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.outbox;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.KARAN;

import static java.util.Collections.singletonList;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.maintenance.MaintenanceController;
import io.harness.ng.core.Resource;
import io.harness.outbox.api.OutboxEventHandler;
import io.harness.outbox.api.OutboxService;
import io.harness.outbox.monitor.OutboxMetricsServiceImpl;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;

@OwnedBy(PL)
public class OutboxPollJobTest extends CategoryTest {
  private OutboxService outboxService;
  private OutboxEventHandler outboxEventHandler;
  private PersistentLocker persistentLocker;
  private OutboxEventPollJob outboxEventPollJob;

  private static final String OUTBOX_POLL_JOB_LOCK = "OUTBOX_POLL_JOB_LOCK";
  private OutboxMetricsServiceImpl outboxMetricsServiceImpl;

  @Before
  public void setup() {
    outboxService = mock(OutboxService.class);
    outboxEventHandler = mock(OutboxEventHandler.class);
    persistentLocker = mock(PersistentLocker.class);
    outboxMetricsServiceImpl = mock(OutboxMetricsServiceImpl.class);
    outboxEventPollJob = new OutboxEventPollJob(outboxService, outboxEventHandler, persistentLocker,
        OutboxPollConfiguration.builder().maximumRetryAttemptsForAnEvent(2).lockId("LOCK_ID").build(),
        outboxMetricsServiceImpl);
    MaintenanceController.forceMaintenance(false);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testSuccessfulHandling() {
    when(persistentLocker.tryToAcquireLock(eq(OUTBOX_POLL_JOB_LOCK + "_"
                                               + "LOCK_ID"),
             any()))
        .thenReturn(mock(AcquiredLock.class));
    String id = randomAlphabetic(10);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType("emptyEvent")
                                  .blocked(false)
                                  .id(id)
                                  .resource(Resource.builder().type("resource").build())
                                  .createdAt(123456789L)
                                  .build();
    when(outboxService.list(any())).thenReturn(singletonList(outboxEvent));
    when(outboxEventHandler.handle(outboxEvent)).thenReturn(true);
    when(outboxService.delete(id)).thenReturn(true);
    outboxEventPollJob.run();
    verify(outboxService, times(1)).delete(id);
    verify(outboxEventHandler, times(1)).handle(any());
    verify(outboxService, times(0)).update(any());
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testUnSuccessfulHandling() {
    when(persistentLocker.tryToAcquireLock(eq(OUTBOX_POLL_JOB_LOCK + "_"
                                               + "LOCK_ID"),
             any()))
        .thenReturn(mock(AcquiredLock.class));
    String id = randomAlphabetic(10);
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .eventType("emptyEvent")
                                  .blocked(false)
                                  .id(id)
                                  .resource(Resource.builder().type("resource").build())
                                  .createdAt(123456789L)
                                  .build();
    when(outboxService.list(any())).thenReturn(singletonList(outboxEvent));
    when(outboxEventHandler.handle(outboxEvent)).thenReturn(false);
    final ArgumentCaptor<OutboxEvent> outboxEventArgumentCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
    outboxEventPollJob.run();
    verify(outboxService, times(0)).delete(any());
    verify(outboxEventHandler, times(3)).handle(any());
    verify(outboxService, times(1)).update(outboxEventArgumentCaptor.capture());
    OutboxEvent updateOutboxEvent = outboxEventArgumentCaptor.getValue();
    assertEquals(id, updateOutboxEvent.getId());
    assertTrue(updateOutboxEvent.getBlocked());
    assertNotNull(updateOutboxEvent.getNextUnblockAttemptAt());
  }
}
