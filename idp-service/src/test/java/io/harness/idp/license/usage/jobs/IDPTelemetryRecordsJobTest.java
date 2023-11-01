/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.license.usage.jobs;

import static io.harness.rule.OwnerRule.SATHISH;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.license.usage.reporting.IDPTelemetryPublisher;
import io.harness.rule.Owner;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class IDPTelemetryRecordsJobTest extends CategoryTest {
  AutoCloseable openMocks;
  @InjectMocks IDPTelemetryRecordsJob idpTelemetryRecordsJob;
  @Mock ScheduledExecutorService executorService;
  @Mock IDPTelemetryPublisher publisher;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testScheduleTasks() {
    try (MockedStatic<Executors> ignored = Mockito.mockStatic(Executors.class)) {
      when(Executors.newSingleThreadScheduledExecutor(any())).thenReturn(executorService);
      when(executorService.scheduleAtFixedRate(
               any(Runnable.class), eq(300L), eq(TimeUnit.DAYS.toSeconds(1)), eq(TimeUnit.SECONDS)))
          .thenAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
          });
      doNothing().when(publisher).recordTelemetry();

      idpTelemetryRecordsJob.scheduleTasks();

      verify(executorService)
          .scheduleAtFixedRate(any(Runnable.class), eq(300L), eq(TimeUnit.DAYS.toSeconds(1)), eq(TimeUnit.SECONDS));
    }
  }

  @Test(expected = Exception.class)
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testScheduleTasksThrowsException() {
    try (MockedStatic<Executors> ignored = Mockito.mockStatic(Executors.class)) {
      when(Executors.newSingleThreadScheduledExecutor(any())).thenReturn(executorService);
      given(executorService.scheduleAtFixedRate(
                any(Runnable.class), eq(300L), eq(TimeUnit.DAYS.toSeconds(1)), eq(TimeUnit.SECONDS)))
          .willAnswer(invocation -> { throw new Exception("Exception Throw"); });
      doNothing().when(publisher).recordTelemetry();

      idpTelemetryRecordsJob.scheduleTasks();
    }
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }
}
