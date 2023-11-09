/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.license.usage.jobs;

import static io.harness.idp.common.DateUtils.ZONE_ID_IST;
import static io.harness.rule.OwnerRule.SATHISH;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.license.usage.service.IDPModuleLicenseUsage;
import io.harness.rule.Owner;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
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
public class LicenseUsageDailyCountJobTest extends CategoryTest {
  AutoCloseable openMocks;
  @InjectMocks private LicenseUsageDailyCountJob job;
  @Mock private ScheduledExecutorService executorService;
  @Mock IDPModuleLicenseUsage idpModuleLicenseUsage;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testStart() throws Exception {
    try (MockedStatic<Executors> ignored = Mockito.mockStatic(Executors.class)) {
      when(Executors.newSingleThreadScheduledExecutor(any())).thenReturn(executorService);
      long midnight = LocalDateTime.now(ZoneId.of(ZONE_ID_IST))
                          .until(LocalDate.now(ZoneId.of(ZONE_ID_IST)).plusDays(1).atStartOfDay(), ChronoUnit.MINUTES);
      when(executorService.scheduleAtFixedRate(
               any(Runnable.class), eq(midnight + 3), eq(TimeUnit.DAYS.toMinutes(1)), eq(TimeUnit.MINUTES)))
          .thenAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
          });

      job.start();

      verify(executorService)
          .scheduleAtFixedRate(
              any(Runnable.class), eq(midnight + 3), eq(TimeUnit.DAYS.toMinutes(1)), eq(TimeUnit.MINUTES));
    }
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testLicenseUsageDailyCountJob() {
    job.run();
    verify(idpModuleLicenseUsage).licenseUsageDailyCountAggregationPerAccount();
  }

  @Test(expected = Exception.class)
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testLicenseUsageDailyCountJobThrowsException() {
    willAnswer(invocation -> { throw new Exception("Exception Throw"); })
        .given(idpModuleLicenseUsage)
        .licenseUsageDailyCountAggregationPerAccount();
    job.run();
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testStop() throws Exception {
    job.stop();
    verify(executorService).shutdownNow();
    verify(executorService).awaitTermination(30, TimeUnit.SECONDS);
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }
}
