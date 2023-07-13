/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.usage.jobs;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cd.CDLicenseType;
import io.harness.cdng.usage.CDLicenseUsageReportService;
import io.harness.cdng.usage.pojos.LicenseDailyUsage;
import io.harness.cdng.usage.task.CDLicenseReportAccounts;
import io.harness.entities.DeploymentAccounts;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.lock.PersistentLocker;
import io.harness.lock.noop.AcquiredNoopLock;
import io.harness.mongo.iterator.provider.MorphiaPersistenceProvider;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class CDLicenseDailyReportIteratorHandlerTest extends CategoryTest {
  private static final String ACCOUNT_ID = "ACCOUNT_ID";
  @Mock private PersistenceIteratorFactory persistenceIteratorFactory;
  @Mock private MorphiaPersistenceProvider<DeploymentAccounts> persistenceProvider;
  @Mock private PersistentLocker persistentLocker;
  @Mock private CDLicenseUsageReportService licenseUsageReportService;
  @InjectMocks private CDLicenseDailyReportIteratorHandler cdLicenseDailyReportIteratorHandler;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);

    when(persistentLocker.tryToAcquireLock(any(), any())).thenReturn(AcquiredNoopLock.builder().build());
    when(licenseUsageReportService.getCDLicenseTypePerAccount(ACCOUNT_ID))
        .thenReturn(Optional.of(CDLicenseType.SERVICES));
    List<LicenseDailyUsage> licenseDailyUsageList = List.of(LicenseDailyUsage.builder().build());
    when(licenseUsageReportService.generateLicenseDailyUsageReport(
             eq(ACCOUNT_ID), eq(CDLicenseType.SERVICES), any(), any()))
        .thenReturn(licenseDailyUsageList);
    doNothing()
        .when(licenseUsageReportService)
        .insertBatchLicenseDailyUsageRecords(ACCOUNT_ID, CDLicenseType.SERVICES, licenseDailyUsageList);
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testHandleDailyReport() {
    when(licenseUsageReportService.getLatestLicenseDailyUsageRecord(ACCOUNT_ID, CDLicenseType.SERVICES))
        .thenReturn(Optional.of(LicenseDailyUsage.builder()
                                    .accountId(ACCOUNT_ID)
                                    .reportedDay(LocalDate.parse("2023-07-06"))
                                    .licenseCount(1)
                                    .build()));
    ArgumentCaptor<LocalDate> fromDateCaptor = ArgumentCaptor.forClass(LocalDate.class);

    cdLicenseDailyReportIteratorHandler.handle(CDLicenseReportAccounts.builder().accountIdentifier(ACCOUNT_ID).build());

    verify(licenseUsageReportService, times(1))
        .generateLicenseDailyUsageReport(any(), any(), fromDateCaptor.capture(), any());

    LocalDate fromDateCaptorValue = fromDateCaptor.getValue();
    assertThat(fromDateCaptorValue).isEqualTo(LocalDate.parse("2023-07-07"));
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testHandleMonthlyReport() {
    when(licenseUsageReportService.getLatestLicenseDailyUsageRecord(ACCOUNT_ID, CDLicenseType.SERVICES))
        .thenReturn(Optional.empty());
    ArgumentCaptor<LocalDate> fromDateCaptor = ArgumentCaptor.forClass(LocalDate.class);

    cdLicenseDailyReportIteratorHandler.handle(CDLicenseReportAccounts.builder().accountIdentifier(ACCOUNT_ID).build());

    verify(licenseUsageReportService, times(1))
        .generateLicenseDailyUsageReport(any(), any(), fromDateCaptor.capture(), any());

    LocalDate fromDateCaptorValue = fromDateCaptor.getValue();
    assertThat(fromDateCaptorValue.plusMonths(11).isBefore(LocalDate.now())).isTrue();
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testHandleMonthlyReportNotFoundLicense() {
    when(licenseUsageReportService.getCDLicenseTypePerAccount(ACCOUNT_ID)).thenReturn(Optional.empty());
    when(licenseUsageReportService.getLatestLicenseDailyUsageRecord(ACCOUNT_ID, CDLicenseType.SERVICES))
        .thenReturn(Optional.empty());

    cdLicenseDailyReportIteratorHandler.handle(CDLicenseReportAccounts.builder().accountIdentifier(ACCOUNT_ID).build());

    verify(licenseUsageReportService, times(0)).generateLicenseDailyUsageReport(any(), any(), any(), any());
  }
}
