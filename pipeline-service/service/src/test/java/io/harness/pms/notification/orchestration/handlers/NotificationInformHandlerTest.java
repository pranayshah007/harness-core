/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.notification.orchestration.handlers;

import static io.harness.rule.OwnerRule.BRIJESH;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.notification.PipelineEventType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.notification.NotificationHelper;
import io.harness.rule.Owner;

import java.util.concurrent.ExecutorService;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class NotificationInformHandlerTest extends CategoryTest {
  @Mock ExecutorService executorService;
  @Mock NotificationHelper notificationHelper;
  @InjectMocks NotificationInformHandler notificationInformHandler;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testAllMethods() {
    Ambiance ambiance = Ambiance.getDefaultInstance();
    ArgumentCaptor<PipelineEventType> argumentCaptor = ArgumentCaptor.forClass(PipelineEventType.class);
    notificationInformHandler.onSuccess(ambiance);
    verify(notificationHelper, times(1)).sendNotification(any(), argumentCaptor.capture(), any(), any());
    assertEquals(argumentCaptor.getValue(), PipelineEventType.PIPELINE_SUCCESS);
    notificationInformHandler.onFailure(ambiance);
    verify(notificationHelper, times(2)).sendNotification(any(), argumentCaptor.capture(), any(), any());
    assertEquals(argumentCaptor.getValue(), PipelineEventType.PIPELINE_FAILED);
    notificationInformHandler.onPause(ambiance);
    verify(notificationHelper, times(3)).sendNotification(any(), argumentCaptor.capture(), any(), any());
    assertEquals(argumentCaptor.getValue(), PipelineEventType.PIPELINE_PAUSED);
    notificationInformHandler.onEnd(ambiance);
    verify(notificationHelper, times(4)).sendNotification(any(), argumentCaptor.capture(), any(), any());
    assertEquals(argumentCaptor.getValue(), PipelineEventType.PIPELINE_END);
  }
}
