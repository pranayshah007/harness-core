/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.usage.impl;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.ModuleType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cd.CDLicenseType;
import io.harness.cd.TimeScaleDAL;
import io.harness.cdng.usage.CDLicenseUsageDAL;
import io.harness.cdng.usage.pojos.LicenseDailyUsage;
import io.harness.cdng.usage.pojos.LicenseDateUsageFetchData;
import io.harness.exception.InvalidArgumentsException;
import io.harness.licensing.LicenseStatus;
import io.harness.licensing.beans.modules.CDModuleLicenseDTO;
import io.harness.licensing.beans.modules.CEModuleLicenseDTO;
import io.harness.licensing.services.LicenseService;
import io.harness.licensing.usage.params.filter.LicenseDateUsageReportType;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.timescaledb.tables.pojos.ServiceInstancesLicenseDailyReport;
import io.harness.timescaledb.tables.pojos.ServicesLicenseDailyReport;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class CDLicenseUsageReportServiceImplTest extends CategoryTest {
  private static final String ACCOUNT_ID = "ACCOUNT_ID";
  @Mock private LicenseService licenseService;
  @Mock private TimeScaleDAL timeScaleDAL;
  @Mock private CDLicenseUsageDAL licenseUsageDAL;
  @InjectMocks private CDLicenseUsageReportServiceImpl cdLicenseUsageReportService;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testGetCDLicenseTypePerAccountServices() {
    when(licenseService.getModuleLicenses(ACCOUNT_ID, ModuleType.CD))
        .thenReturn(List.of(
            CDModuleLicenseDTO.builder().cdLicenseType(CDLicenseType.SERVICES).status(LicenseStatus.ACTIVE).build()));
    Optional<CDLicenseType> cdLicenseTypePerAccount =
        cdLicenseUsageReportService.getCDLicenseTypePerAccount(ACCOUNT_ID);

    assertThat(cdLicenseTypePerAccount.isPresent()).isTrue();
    assertThat(cdLicenseTypePerAccount.get()).isEqualTo(CDLicenseType.SERVICES);
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testGetCDLicenseTypePerAccountServiceInstances() {
    when(licenseService.getModuleLicenses(ACCOUNT_ID, ModuleType.CD))
        .thenReturn(List.of(CDModuleLicenseDTO.builder()
                                .cdLicenseType(CDLicenseType.SERVICE_INSTANCES)
                                .status(LicenseStatus.ACTIVE)
                                .build()));
    Optional<CDLicenseType> cdLicenseTypePerAccount =
        cdLicenseUsageReportService.getCDLicenseTypePerAccount(ACCOUNT_ID);

    assertThat(cdLicenseTypePerAccount.isPresent()).isTrue();
    assertThat(cdLicenseTypePerAccount.get()).isEqualTo(CDLicenseType.SERVICE_INSTANCES);
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testGetCDLicenseTypePerAccountNotExistingLicense() {
    when(licenseService.getModuleLicenses(ACCOUNT_ID, ModuleType.CD))
        .thenReturn(List.of(CDModuleLicenseDTO.builder().build()));
    Optional<CDLicenseType> cdLicenseTypePerAccount =
        cdLicenseUsageReportService.getCDLicenseTypePerAccount(ACCOUNT_ID);

    assertThat(cdLicenseTypePerAccount.isPresent()).isFalse();
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testGetCDLicenseTypePerAccountNotValidCDLicenseTypes() {
    when(licenseService.getModuleLicenses(ACCOUNT_ID, ModuleType.CD))
        .thenReturn(List.of(
            CDModuleLicenseDTO.builder().cdLicenseType(CDLicenseType.SERVICES).status(LicenseStatus.ACTIVE).build(),
            CDModuleLicenseDTO.builder()
                .cdLicenseType(CDLicenseType.SERVICE_INSTANCES)
                .status(LicenseStatus.ACTIVE)
                .build()));

    assertThatThrownBy(() -> cdLicenseUsageReportService.getCDLicenseTypePerAccount(ACCOUNT_ID))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage(
            "Found more active CD license types on account: ACCOUNT_ID, licence types: SERVICES,SERVICE_INSTANCES");
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testGetCDLicenseTypePerAccountNotValidModule() {
    when(licenseService.getModuleLicenses(ACCOUNT_ID, ModuleType.CD))
        .thenReturn(List.of(CEModuleLicenseDTO.builder().build()));
    Optional<CDLicenseType> cdLicenseTypePerAccount =
        cdLicenseUsageReportService.getCDLicenseTypePerAccount(ACCOUNT_ID);

    assertThat(cdLicenseTypePerAccount.isPresent()).isFalse();
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testGetLatestLicenseDailyUsageRecordServices() {
    int licenseCount = 75;
    LocalDate reportedDay = LocalDate.parse("2023-07-05");
    ServicesLicenseDailyReport servicesLicenseDailyReport = new ServicesLicenseDailyReport();
    servicesLicenseDailyReport.setAccountId(ACCOUNT_ID);
    servicesLicenseDailyReport.setReportedDay(reportedDay);
    servicesLicenseDailyReport.setLicenseCount(licenseCount);

    when(timeScaleDAL.getLatestServicesLicenseDailyReportByAccountId(ACCOUNT_ID))
        .thenReturn(List.of(servicesLicenseDailyReport));

    Optional<LicenseDailyUsage> latestLicenseDailyUsageRecord =
        cdLicenseUsageReportService.getLatestLicenseDailyUsageRecord(ACCOUNT_ID, CDLicenseType.SERVICES);

    assertThat(latestLicenseDailyUsageRecord.isPresent()).isTrue();
    LicenseDailyUsage licenseDailyUsage = latestLicenseDailyUsageRecord.get();
    assertThat(licenseDailyUsage.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(licenseDailyUsage.getReportedDay()).isEqualTo(reportedDay);
    assertThat(licenseDailyUsage.getLicenseCount()).isEqualTo(licenseCount);
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testGetLatestLicenseDailyUsageRecordServicesEmptyList() {
    when(timeScaleDAL.getLatestServicesLicenseDailyReportByAccountId(ACCOUNT_ID)).thenReturn(new ArrayList<>());

    Optional<LicenseDailyUsage> latestLicenseDailyUsageRecord =
        cdLicenseUsageReportService.getLatestLicenseDailyUsageRecord(ACCOUNT_ID, CDLicenseType.SERVICES);

    assertThat(latestLicenseDailyUsageRecord.isPresent()).isFalse();
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testGetLatestLicenseDailyUsageRecordServiceInstances() {
    int licenseCount = 75;
    LocalDate reportedDay = LocalDate.parse("2023-07-05");
    ServiceInstancesLicenseDailyReport serviceInstancesLicenseDailyReport = new ServiceInstancesLicenseDailyReport();
    serviceInstancesLicenseDailyReport.setAccountId(ACCOUNT_ID);
    serviceInstancesLicenseDailyReport.setReportedDay(reportedDay);
    serviceInstancesLicenseDailyReport.setLicenseCount(licenseCount);

    when(timeScaleDAL.getLatestServiceInstancesLicenseDailyReportByAccountId(ACCOUNT_ID))
        .thenReturn(List.of(serviceInstancesLicenseDailyReport));

    Optional<LicenseDailyUsage> latestLicenseDailyUsageRecord =
        cdLicenseUsageReportService.getLatestLicenseDailyUsageRecord(ACCOUNT_ID, CDLicenseType.SERVICE_INSTANCES);

    assertThat(latestLicenseDailyUsageRecord.isPresent()).isTrue();
    LicenseDailyUsage licenseDailyUsage = latestLicenseDailyUsageRecord.get();
    assertThat(licenseDailyUsage.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(licenseDailyUsage.getReportedDay()).isEqualTo(reportedDay);
    assertThat(licenseDailyUsage.getLicenseCount()).isEqualTo(licenseCount);
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testGetLatestLicenseDailyUsageRecordServiceInstancesEmptyList() {
    when(timeScaleDAL.getLatestServiceInstancesLicenseDailyReportByAccountId(ACCOUNT_ID)).thenReturn(new ArrayList<>());

    Optional<LicenseDailyUsage> latestLicenseDailyUsageRecord =
        cdLicenseUsageReportService.getLatestLicenseDailyUsageRecord(ACCOUNT_ID, CDLicenseType.SERVICE_INSTANCES);

    assertThat(latestLicenseDailyUsageRecord.isPresent()).isFalse();
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testGenerateLicenseDailyUsageReportMonthly() {
    LocalDate fromDate = LocalDate.parse("2023-06-03");
    LocalDate toDate = LocalDate.parse("2023-07-05");
    ArgumentCaptor<LicenseDateUsageFetchData> licenseDateUsageFetchDataCaptorList =
        ArgumentCaptor.forClass(LicenseDateUsageFetchData.class);
    when(licenseUsageDAL.fetchLicenseDateUsage(any()))
        .thenReturn(
            List.of(LicenseDailyUsage.builder().accountId(ACCOUNT_ID).reportedDay(fromDate).licenseCount(1).build()))
        .thenReturn(
            List.of(LicenseDailyUsage.builder().accountId(ACCOUNT_ID).reportedDay(toDate).licenseCount(1).build()));

    List<LicenseDailyUsage> licenseDailyUsages = cdLicenseUsageReportService.generateLicenseDailyUsageReport(
        ACCOUNT_ID, CDLicenseType.SERVICES, fromDate, toDate);

    verify(licenseUsageDAL, times(2)).fetchLicenseDateUsage(licenseDateUsageFetchDataCaptorList.capture());
    List<LicenseDateUsageFetchData> licenseDateUsageFetchDataList = licenseDateUsageFetchDataCaptorList.getAllValues();
    LicenseDateUsageFetchData licenseDateUsageFetchData = licenseDateUsageFetchDataList.get(0);
    LicenseDateUsageFetchData licenseDateUsageFetchDataSecond = licenseDateUsageFetchDataList.get(1);

    assertThat(licenseDateUsageFetchData.getLicenseType()).isEqualTo(CDLicenseType.SERVICES);
    assertThat(licenseDateUsageFetchData.getReportType()).isEqualTo(LicenseDateUsageReportType.DAILY);
    assertThat(licenseDateUsageFetchData.getAccountIdentifier()).isEqualTo(ACCOUNT_ID);
    assertThat(licenseDateUsageFetchData.getFromDate()).isEqualTo(LocalDate.parse("2023-06-03"));
    assertThat(licenseDateUsageFetchData.getToDate()).isEqualTo(LocalDate.parse("2023-07-03"));

    assertThat(licenseDateUsageFetchDataSecond.getLicenseType()).isEqualTo(CDLicenseType.SERVICES);
    assertThat(licenseDateUsageFetchDataSecond.getReportType()).isEqualTo(LicenseDateUsageReportType.DAILY);
    assertThat(licenseDateUsageFetchDataSecond.getAccountIdentifier()).isEqualTo(ACCOUNT_ID);
    assertThat(licenseDateUsageFetchDataSecond.getFromDate()).isEqualTo(LocalDate.parse("2023-07-04"));
    assertThat(licenseDateUsageFetchDataSecond.getToDate()).isEqualTo(LocalDate.parse("2023-07-05"));

    LicenseDailyUsage licenseDailyUsage = licenseDailyUsages.get(0);
    LicenseDailyUsage licenseDailyUsageSecond = licenseDailyUsages.get(1);

    assertThat(licenseDailyUsage.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(licenseDailyUsage.getReportedDay()).isEqualTo(fromDate);
    assertThat(licenseDailyUsage.getLicenseCount()).isEqualTo(1);

    assertThat(licenseDailyUsageSecond.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(licenseDailyUsageSecond.getReportedDay()).isEqualTo(toDate);
    assertThat(licenseDailyUsageSecond.getLicenseCount()).isEqualTo(1);
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testGenerateLicenseDailyUsageReportDaily() {
    LocalDate fromDate = LocalDate.parse("2023-07-04");
    LocalDate toDate = LocalDate.parse("2023-07-05");
    ArgumentCaptor<LicenseDateUsageFetchData> licenseDateUsageFetchDataCaptorList =
        ArgumentCaptor.forClass(LicenseDateUsageFetchData.class);
    when(licenseUsageDAL.fetchLicenseDateUsage(any()))
        .thenReturn(
            List.of(LicenseDailyUsage.builder().accountId(ACCOUNT_ID).reportedDay(fromDate).licenseCount(1).build(),
                LicenseDailyUsage.builder().accountId(ACCOUNT_ID).reportedDay(toDate).licenseCount(1).build()));

    List<LicenseDailyUsage> licenseDailyUsages = cdLicenseUsageReportService.generateLicenseDailyUsageReport(
        ACCOUNT_ID, CDLicenseType.SERVICES, fromDate, toDate);

    verify(licenseUsageDAL, times(1)).fetchLicenseDateUsage(licenseDateUsageFetchDataCaptorList.capture());
    List<LicenseDateUsageFetchData> licenseDateUsageFetchDataList = licenseDateUsageFetchDataCaptorList.getAllValues();
    LicenseDateUsageFetchData licenseDateUsageFetchData = licenseDateUsageFetchDataList.get(0);

    assertThat(licenseDateUsageFetchData.getLicenseType()).isEqualTo(CDLicenseType.SERVICES);
    assertThat(licenseDateUsageFetchData.getReportType()).isEqualTo(LicenseDateUsageReportType.DAILY);
    assertThat(licenseDateUsageFetchData.getAccountIdentifier()).isEqualTo(ACCOUNT_ID);
    assertThat(licenseDateUsageFetchData.getFromDate()).isEqualTo(fromDate);
    assertThat(licenseDateUsageFetchData.getToDate()).isEqualTo(toDate);

    LicenseDailyUsage licenseDailyUsage = licenseDailyUsages.get(0);
    LicenseDailyUsage licenseDailyUsageSecond = licenseDailyUsages.get(1);

    assertThat(licenseDailyUsage.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(licenseDailyUsage.getReportedDay()).isEqualTo(fromDate);
    assertThat(licenseDailyUsage.getLicenseCount()).isEqualTo(1);

    assertThat(licenseDailyUsageSecond.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(licenseDailyUsageSecond.getReportedDay()).isEqualTo(toDate);
    assertThat(licenseDailyUsageSecond.getLicenseCount()).isEqualTo(1);
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testGenerateLicenseDailyUsageReportDailyIteration() {
    LocalDate fromDate = LocalDate.parse("2023-05-02");
    LocalDate toDate = LocalDate.parse("2023-07-05");
    ArgumentCaptor<LicenseDateUsageFetchData> licenseDateUsageFetchDataCaptorList =
        ArgumentCaptor.forClass(LicenseDateUsageFetchData.class);
    when(licenseUsageDAL.fetchLicenseDateUsage(any())).thenReturn(List.of());

    cdLicenseUsageReportService.generateLicenseDailyUsageReport(ACCOUNT_ID, CDLicenseType.SERVICES, fromDate, toDate);

    verify(licenseUsageDAL, times(3)).fetchLicenseDateUsage(licenseDateUsageFetchDataCaptorList.capture());
    List<LicenseDateUsageFetchData> licenseDateUsageFetchDataList = licenseDateUsageFetchDataCaptorList.getAllValues();
    LicenseDateUsageFetchData licenseDateUsageFetchData = licenseDateUsageFetchDataList.get(0);
    LicenseDateUsageFetchData licenseDateUsageFetchData2 = licenseDateUsageFetchDataList.get(1);
    LicenseDateUsageFetchData licenseDateUsageFetchData3 = licenseDateUsageFetchDataList.get(2);
    // (2023-05-02 - 2023-07-05) 1. 2023-05-02 - 2023-06-01 >> 2. 2023-06-02 - 2023-07-02 >> 3. 2023-07-03 - 2023-07-02
    assertThat(licenseDateUsageFetchData.getFromDate()).isEqualTo(LocalDate.parse("2023-05-02"));
    assertThat(licenseDateUsageFetchData.getToDate()).isEqualTo(LocalDate.parse("2023-06-01"));

    assertThat(licenseDateUsageFetchData2.getFromDate()).isEqualTo(LocalDate.parse("2023-06-02"));
    assertThat(licenseDateUsageFetchData2.getToDate()).isEqualTo(LocalDate.parse("2023-07-02"));

    assertThat(licenseDateUsageFetchData3.getFromDate()).isEqualTo(LocalDate.parse("2023-07-03"));
    assertThat(licenseDateUsageFetchData3.getToDate()).isEqualTo(LocalDate.parse("2023-07-05"));
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testInsertBulkLicenseDailyUsageRecordsServices() {
    LocalDate fromDate = LocalDate.parse("2023-06-03");
    LocalDate toDate = LocalDate.parse("2023-07-05");
    List<LicenseDailyUsage> licenseDailyUsageList =
        List.of(LicenseDailyUsage.builder().accountId(ACCOUNT_ID).reportedDay(fromDate).licenseCount(1).build(),
            LicenseDailyUsage.builder().accountId(ACCOUNT_ID).reportedDay(toDate).licenseCount(1).build());
    when(timeScaleDAL.insertBulkServicesLicenseDailyReport(any(), anyList())).thenReturn(1);

    cdLicenseUsageReportService.insertBatchLicenseDailyUsageRecords(
        ACCOUNT_ID, CDLicenseType.SERVICES, licenseDailyUsageList);

    verify(timeScaleDAL, times(1)).insertBulkServicesLicenseDailyReport(ACCOUNT_ID, licenseDailyUsageList);
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testInsertBulkLicenseDailyUsageRecordsServiceInstances() {
    LocalDate fromDate = LocalDate.parse("2023-06-03");
    LocalDate toDate = LocalDate.parse("2023-07-05");
    List<LicenseDailyUsage> licenseDailyUsageList =
        List.of(LicenseDailyUsage.builder().accountId(ACCOUNT_ID).reportedDay(fromDate).licenseCount(1).build(),
            LicenseDailyUsage.builder().accountId(ACCOUNT_ID).reportedDay(toDate).licenseCount(1).build());
    when(timeScaleDAL.insertBulkServiceInstancesLicenseDailyReport(any(), anyList())).thenReturn(1);

    cdLicenseUsageReportService.insertBatchLicenseDailyUsageRecords(
        ACCOUNT_ID, CDLicenseType.SERVICE_INSTANCES, licenseDailyUsageList);

    verify(timeScaleDAL, times(1)).insertBulkServiceInstancesLicenseDailyReport(ACCOUNT_ID, licenseDailyUsageList);
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testGetLicenseUsagePerDaysReportServices() {
    LocalDate fromDate = LocalDate.parse("2023-07-05");
    LocalDate toDate = LocalDate.parse("2023-07-05");
    int licenseCount = 75;
    LocalDate reportedDay = LocalDate.parse("2023-07-05");
    ServicesLicenseDailyReport servicesLicenseDailyReport = new ServicesLicenseDailyReport();
    servicesLicenseDailyReport.setAccountId(ACCOUNT_ID);
    servicesLicenseDailyReport.setReportedDay(reportedDay);
    servicesLicenseDailyReport.setLicenseCount(licenseCount);
    List<ServicesLicenseDailyReport> servicesLicenseDailyReports = List.of(servicesLicenseDailyReport);

    when(timeScaleDAL.listServicesLicenseDailyReportByAccountId(ACCOUNT_ID, fromDate, toDate))
        .thenReturn(servicesLicenseDailyReports);

    Map<String, Integer> licenseUsagePerDaysReport =
        cdLicenseUsageReportService.getLicenseUsagePerDaysReport(ACCOUNT_ID, CDLicenseType.SERVICES, fromDate, toDate);

    assertThat(licenseUsagePerDaysReport.size()).isEqualTo(1);
    Integer licenses = licenseUsagePerDaysReport.get("2023-07-05");
    assertThat(licenses).isEqualTo(licenseCount);
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testGetLicenseUsagePerDaysReportServiceInstances() {
    LocalDate fromDate = LocalDate.parse("2023-07-05");
    LocalDate toDate = LocalDate.parse("2023-07-05");
    int licenseCount = 75;
    LocalDate reportedDay = LocalDate.parse("2023-07-05");
    ServiceInstancesLicenseDailyReport serviceInstancesLicenseDailyReport = new ServiceInstancesLicenseDailyReport();
    serviceInstancesLicenseDailyReport.setAccountId(ACCOUNT_ID);
    serviceInstancesLicenseDailyReport.setReportedDay(reportedDay);
    serviceInstancesLicenseDailyReport.setLicenseCount(licenseCount);
    List<ServiceInstancesLicenseDailyReport> serviceInstancesLicenseDailyReportList =
        List.of(serviceInstancesLicenseDailyReport);

    when(timeScaleDAL.listServiceInstancesLicenseDailyReportByAccountId(ACCOUNT_ID, fromDate, toDate))
        .thenReturn(serviceInstancesLicenseDailyReportList);

    Map<String, Integer> licenseUsagePerDaysReport = cdLicenseUsageReportService.getLicenseUsagePerDaysReport(
        ACCOUNT_ID, CDLicenseType.SERVICE_INSTANCES, fromDate, toDate);

    assertThat(licenseUsagePerDaysReport.size()).isEqualTo(1);
    Integer licenses = licenseUsagePerDaysReport.get("2023-07-05");
    assertThat(licenses).isEqualTo(licenseCount);
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testGetLicenseUsagePerMonthsReportServices() {
    LocalDate fromDate = LocalDate.parse("2022-06-01");
    LocalDate toDate = LocalDate.parse("2023-06-01");
    fromDate.datesUntil(toDate, Period.ofMonths(1))
        .forEach(month
            -> when(timeScaleDAL.listServicesLicenseDailyReportByAccountId(any(), any(), any()))
                   .thenReturn(getDailyServiceUsageServices(month)));
    ArgumentCaptor<LocalDate> fromDateCaptor = ArgumentCaptor.forClass(LocalDate.class);
    ArgumentCaptor<LocalDate> toDateCaptor = ArgumentCaptor.forClass(LocalDate.class);

    Map<String, Integer> licenseUsagePerMonthsReport = cdLicenseUsageReportService.getLicenseUsagePerMonthsReport(
        ACCOUNT_ID, CDLicenseType.SERVICES, fromDate, toDate);

    verify(timeScaleDAL, times(12))
        .listServicesLicenseDailyReportByAccountId(any(), fromDateCaptor.capture(), toDateCaptor.capture());

    List<LocalDate> fromDateValues = fromDateCaptor.getAllValues();
    List<LocalDate> toDateValues = toDateCaptor.getAllValues();
    assertThat(fromDateValues)
        .contains(LocalDate.parse("2022-06-01"), LocalDate.parse("2022-07-01"), LocalDate.parse("2022-08-01"),
            LocalDate.parse("2022-09-01"), LocalDate.parse("2022-10-01"), LocalDate.parse("2022-11-01"),
            LocalDate.parse("2022-12-01"), LocalDate.parse("2023-01-01"), LocalDate.parse("2023-02-01"),
            LocalDate.parse("2023-03-01"), LocalDate.parse("2023-04-01"), LocalDate.parse("2023-05-01"));
    assertThat(toDateValues)
        .contains(LocalDate.parse("2022-06-30"), LocalDate.parse("2022-07-31"), LocalDate.parse("2022-08-31"),
            LocalDate.parse("2022-09-30"), LocalDate.parse("2022-10-31"), LocalDate.parse("2022-11-30"),
            LocalDate.parse("2022-12-31"), LocalDate.parse("2023-01-31"), LocalDate.parse("2023-02-28"),
            LocalDate.parse("2023-03-31"), LocalDate.parse("2023-04-30"), LocalDate.parse("2023-05-31"));

    assertThat(licenseUsagePerMonthsReport.size()).isEqualTo(12);
    Integer sumOfMonthlyPicks = licenseUsagePerMonthsReport.values().stream().reduce(0, Integer::sum);
    assertThat(sumOfMonthlyPicks).isEqualTo(12);
    assertThat(licenseUsagePerMonthsReport.keySet().toArray(new String[] {}))
        .isEqualTo(new String[] {"2022-06-01", "2022-07-01", "2022-08-01", "2022-09-01", "2022-10-01", "2022-11-01",
            "2022-12-01", "2023-01-01", "2023-02-01", "2023-03-01", "2023-04-01", "2023-05-01"});
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testGetLicenseUsagePerMonthsReportServiceInstances() {
    LocalDate fromDate = LocalDate.parse("2022-06-01");
    LocalDate toDate = LocalDate.parse("2023-06-01");
    fromDate.datesUntil(toDate, Period.ofMonths(1))
        .forEach(month
            -> when(timeScaleDAL.listServiceInstancesLicenseDailyReportByAccountId(any(), any(), any()))
                   .thenReturn(getDailyServiceUsageServiceInstances(month)));
    ArgumentCaptor<LocalDate> fromDateCaptor = ArgumentCaptor.forClass(LocalDate.class);
    ArgumentCaptor<LocalDate> toDateCaptor = ArgumentCaptor.forClass(LocalDate.class);

    Map<String, Integer> licenseUsagePerMonthsReport = cdLicenseUsageReportService.getLicenseUsagePerMonthsReport(
        ACCOUNT_ID, CDLicenseType.SERVICE_INSTANCES, fromDate, toDate);

    verify(timeScaleDAL, times(12))
        .listServiceInstancesLicenseDailyReportByAccountId(any(), fromDateCaptor.capture(), toDateCaptor.capture());

    List<LocalDate> fromDateValues = fromDateCaptor.getAllValues();
    List<LocalDate> toDateValues = toDateCaptor.getAllValues();
    assertThat(fromDateValues)
        .contains(LocalDate.parse("2022-06-01"), LocalDate.parse("2022-07-01"), LocalDate.parse("2022-08-01"),
            LocalDate.parse("2022-09-01"), LocalDate.parse("2022-10-01"), LocalDate.parse("2022-11-01"),
            LocalDate.parse("2022-12-01"), LocalDate.parse("2023-01-01"), LocalDate.parse("2023-02-01"),
            LocalDate.parse("2023-03-01"), LocalDate.parse("2023-04-01"), LocalDate.parse("2023-05-01"));
    assertThat(toDateValues)
        .contains(LocalDate.parse("2022-06-30"), LocalDate.parse("2022-07-31"), LocalDate.parse("2022-08-31"),
            LocalDate.parse("2022-09-30"), LocalDate.parse("2022-10-31"), LocalDate.parse("2022-11-30"),
            LocalDate.parse("2022-12-31"), LocalDate.parse("2023-01-31"), LocalDate.parse("2023-02-28"),
            LocalDate.parse("2023-03-31"), LocalDate.parse("2023-04-30"), LocalDate.parse("2023-05-31"));

    assertThat(licenseUsagePerMonthsReport.size()).isEqualTo(12);
    Integer sumOfMonthlyPicks = licenseUsagePerMonthsReport.values().stream().reduce(0, Integer::sum);
    assertThat(sumOfMonthlyPicks).isEqualTo(12);
    assertThat(licenseUsagePerMonthsReport.keySet().toArray(new String[] {}))
        .isEqualTo(new String[] {"2022-06-01", "2022-07-01", "2022-08-01", "2022-09-01", "2022-10-01", "2022-11-01",
            "2022-12-01", "2023-01-01", "2023-02-01", "2023-03-01", "2023-04-01", "2023-05-01"});
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testGetMonthlyPickLicenseUsage() {
    Map<String, Integer> dailyLicenseUsagePerMonth = getDailyServiceUsage();
    assertThat(cdLicenseUsageReportService.getMonthlyPickLicenseUsage(dailyLicenseUsagePerMonth)).isEqualTo(41);
  }

  private Map<String, Integer> getDailyServiceUsage() {
    Map<String, Integer> serviceInstancesUsage = new LinkedHashMap<>();
    serviceInstancesUsage.put("2022-01-01", 1);
    serviceInstancesUsage.put("2022-01-02", 2);
    serviceInstancesUsage.put("2022-01-03", 3);
    serviceInstancesUsage.put("2022-01-04", 4);
    serviceInstancesUsage.put("2022-01-05", 5);
    serviceInstancesUsage.put("2022-01-06", 1);
    serviceInstancesUsage.put("2022-01-07", 10);
    serviceInstancesUsage.put("2022-01-08", 41);
    return serviceInstancesUsage;
  }

  private List<ServiceInstancesLicenseDailyReport> getDailyServiceUsageServiceInstances(LocalDate month) {
    List<ServiceInstancesLicenseDailyReport> serviceInstancesUsage = new LinkedList<>();
    LocalDate toDate = month.plusMonths(1);
    month.datesUntil(toDate, Period.ofDays(1)).forEach(day -> {
      ServiceInstancesLicenseDailyReport serviceInstancesLicenseDailyReport = new ServiceInstancesLicenseDailyReport();
      serviceInstancesLicenseDailyReport.setAccountId(ACCOUNT_ID);
      serviceInstancesLicenseDailyReport.setReportedDay(day);
      serviceInstancesLicenseDailyReport.setLicenseCount(1);
      serviceInstancesUsage.add(serviceInstancesLicenseDailyReport);
    });

    return serviceInstancesUsage;
  }

  private List<ServicesLicenseDailyReport> getDailyServiceUsageServices(LocalDate month) {
    List<ServicesLicenseDailyReport> serviceInstancesUsage = new LinkedList<>();
    LocalDate toDate = month.plusMonths(1);
    month.datesUntil(toDate, Period.ofDays(1)).forEach(day -> {
      ServicesLicenseDailyReport serviceInstancesLicenseDailyReport = new ServicesLicenseDailyReport();
      serviceInstancesLicenseDailyReport.setAccountId(ACCOUNT_ID);
      serviceInstancesLicenseDailyReport.setReportedDay(day);
      serviceInstancesLicenseDailyReport.setLicenseCount(1);
      serviceInstancesUsage.add(serviceInstancesLicenseDailyReport);
    });

    return serviceInstancesUsage;
  }
}
