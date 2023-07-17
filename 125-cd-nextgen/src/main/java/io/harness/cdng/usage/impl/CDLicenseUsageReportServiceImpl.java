/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.usage.impl;

import static io.harness.cdng.usage.jobs.CDLicenseDailyReportIteratorHandler.BULK_INSERT_LIMIT;
import static io.harness.cdng.usage.jobs.CDLicenseDailyReportIteratorHandler.QUERY_ON_NUMBER_OF_DAYS;
import static io.harness.cdng.usage.mapper.ServiceInstancesDateUsageMapper.buildDailyLicenseDateUsageFetchData;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.licensing.usage.beans.cd.CDLicenseUsageConstants.NOT_SUPPORTED_LICENSE_TYPE_MESSAGE;

import static java.lang.String.format;

import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cd.CDLicenseType;
import io.harness.cd.TimeScaleDAL;
import io.harness.cdlicense.exception.CgLicenseUsageException;
import io.harness.cdng.usage.CDLicenseUsageDAL;
import io.harness.cdng.usage.CDLicenseUsageReportService;
import io.harness.cdng.usage.pojos.LicenseDailyUsage;
import io.harness.cdng.usage.pojos.LicenseDateUsageFetchData;
import io.harness.exception.InvalidArgumentsException;
import io.harness.licensing.beans.modules.CDModuleLicenseDTO;
import io.harness.licensing.beans.modules.ModuleLicenseDTO;
import io.harness.licensing.services.LicenseService;
import io.harness.timescaledb.tables.pojos.ServiceInstancesLicenseDailyReport;
import io.harness.timescaledb.tables.pojos.ServicesLicenseDailyReport;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.TemporalAdjusters;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jooq.exception.DataAccessException;

@OwnedBy(HarnessTeam.CDP)
@Singleton
@Slf4j
public class CDLicenseUsageReportServiceImpl implements CDLicenseUsageReportService {
  private static final int DB_QUERY_MAX_RETRY = 3;

  @Inject private CDLicenseUsageDAL licenseUsageDAL;
  @Inject private TimeScaleDAL timeScaleDAL;
  @Inject private LicenseService licenseService;

  @Override
  public Optional<CDLicenseType> getCDLicenseTypePerAccount(String accountId) {
    List<ModuleLicenseDTO> moduleLicenses = licenseService.getModuleLicenses(accountId, ModuleType.CD);
    return moduleLicenses.stream()
        .filter(moduleLicenseDTO -> moduleLicenseDTO instanceof CDModuleLicenseDTO)
        .map(CDModuleLicenseDTO.class ::cast)
        .map(CDModuleLicenseDTO::getCdLicenseType)
        .filter(Objects::nonNull)
        .findFirst();
  }

  @Override
  public Optional<LicenseDailyUsage> getLatestLicenseDailyUsageRecord(String accountId, CDLicenseType licenseType) {
    if (CDLicenseType.SERVICES != licenseType && CDLicenseType.SERVICE_INSTANCES != licenseType) {
      throw new InvalidArgumentsException(format(NOT_SUPPORTED_LICENSE_TYPE_MESSAGE, licenseType));
    }

    log.info("Start getting latest license daily usage report, accountId: {}, licenseType: {}", accountId, licenseType);
    if (CDLicenseType.SERVICES == licenseType) {
      List<ServicesLicenseDailyReport> latestServicesLicenseDailyReport =
          timeScaleDAL.getLatestServicesLicenseDailyReportByAccountId(accountId);

      return findFirstServicesLicenseDailyReport(latestServicesLicenseDailyReport);
    } else {
      List<ServiceInstancesLicenseDailyReport> latestServiceInstancesLicenseDailyReport =
          timeScaleDAL.getLatestServiceInstancesLicenseDailyReportByAccountId(accountId);

      return findFirstServiceInstancesLicenseDailyReport(latestServiceInstancesLicenseDailyReport);
    }
  }

  @Override
  public List<LicenseDailyUsage> generateLicenseDailyUsageReport(
      String accountId, CDLicenseType licenseType, LocalDate fromDate, LocalDate toDate) {
    if (fromDate.isAfter(toDate)) {
      throw new InvalidArgumentsException(format("Found invalid time range for creating license daily usage report, "
              + "accountId: %s, fromDate: %s, toDate: %s",
          accountId, fromDate, toDate));
    }

    log.info("Start generating license daily usage report, accountId: {}, licenseType: {}, fromDate: {}, toDate: {}",
        accountId, licenseType, fromDate, toDate);
    // if report needs to be generated on monthly level, do query on DB month by month
    if (fromDate.plusDays(QUERY_ON_NUMBER_OF_DAYS).isBefore(toDate)) {
      Stopwatch sw = Stopwatch.createStarted();
      List<LicenseDailyUsage> licenseUsage = new LinkedList<>();
      LocalDate internalToDate = fromDate.minusDays(1);
      LocalDate internalFromDate;
      do {
        // licenseUsageDAL.fetchLicenseDateUsage(licenseDateUsageFetchData) fetch reports including start and end day,
        // hence we need internalToDate.plusDays(1) to exclude already generated report in previous iteration
        internalFromDate = internalToDate.plusDays(1);
        internalToDate = internalFromDate.plusDays(QUERY_ON_NUMBER_OF_DAYS).isAfter(toDate)
            ? toDate
            : internalFromDate.plusDays(QUERY_ON_NUMBER_OF_DAYS);

        LicenseDateUsageFetchData licenseDateUsageFetchData =
            buildDailyLicenseDateUsageFetchData(accountId, internalFromDate, internalToDate, licenseType);
        licenseUsage.addAll(licenseUsageDAL.fetchLicenseDateUsage(licenseDateUsageFetchData));
      } while (internalToDate.isBefore(toDate));
      log.info(
          "Successfully generated license {} days report, time taken : {}, accountId: {}, licenseType: {}, fromDate: {}, toDate: {} ",
          QUERY_ON_NUMBER_OF_DAYS, sw.elapsed(TimeUnit.MILLISECONDS), accountId, licenseType, fromDate, toDate);
      return licenseUsage;
    } else {
      LicenseDateUsageFetchData licenseDateUsageFetchData =
          buildDailyLicenseDateUsageFetchData(accountId, fromDate, toDate, licenseType);
      return licenseUsageDAL.fetchLicenseDateUsage(licenseDateUsageFetchData);
    }
  }

  @Override
  public void insertBatchLicenseDailyUsageRecords(
      String accountId, CDLicenseType licenseType, List<LicenseDailyUsage> licenseDailyReport) {
    if (isEmpty(licenseDailyReport)) {
      return;
    }
    if (CDLicenseType.SERVICES != licenseType && CDLicenseType.SERVICE_INSTANCES != licenseType) {
      throw new InvalidArgumentsException(format(NOT_SUPPORTED_LICENSE_TYPE_MESSAGE, licenseType));
    }

    List<List<LicenseDailyUsage>> partitionedLicenseDailyUsage = Lists.partition(licenseDailyReport, BULK_INSERT_LIMIT);
    log.info("Start inserting batch license usage report, total daily reports: {}, "
            + "partition number: {}, accountId: {}, licenseType: {}",
        licenseDailyReport.size(), partitionedLicenseDailyUsage.size(), accountId, licenseType);
    partitionedLicenseDailyUsage.forEach(batchPartition -> {
      if (CDLicenseType.SERVICES == licenseType) {
        insertBatchWithRetry(
            accountId, () -> timeScaleDAL.insertBulkServicesLicenseDailyReport(accountId, batchPartition), licenseType);
      } else {
        insertBatchWithRetry(accountId,
            () -> timeScaleDAL.insertBulkServiceInstancesLicenseDailyReport(accountId, batchPartition), licenseType);
      }
    });
  }

  @Override
  public Map<String, Integer> getLicenseUsagePerMonthsReport(
      String accountId, CDLicenseType licenseType, LocalDate fromMonth, LocalDate toMonth) {
    Map<String, Integer> licenseUsagePerMonths = new LinkedHashMap<>();
    log.info("Start getting monthly license usage report, accountId: {}, licenseType: {}, fromMonth: {}, toMonth: {}",
        accountId, licenseType, fromMonth, toMonth);
    fromMonth.datesUntil(toMonth, Period.ofMonths(1)).forEach(month -> {
      LocalDate fromDate = month.with(TemporalAdjusters.firstDayOfMonth());
      LocalDate toDate = month.with(TemporalAdjusters.lastDayOfMonth());
      Map<String, Integer> dailyLicenseUsagePerMonth =
          getLicenseUsagePerDaysReport(accountId, licenseType, fromDate, toDate);

      licenseUsagePerMonths.put(month.toString(), getMonthlyPickLicenseUsage(dailyLicenseUsagePerMonth));
    });

    return licenseUsagePerMonths;
  }

  @Override
  public Map<String, Integer> getLicenseUsagePerDaysReport(
      String accountId, CDLicenseType licenseType, LocalDate fromDay, LocalDate toDay) {
    if (CDLicenseType.SERVICES != licenseType && CDLicenseType.SERVICE_INSTANCES != licenseType) {
      throw new InvalidArgumentsException(format(NOT_SUPPORTED_LICENSE_TYPE_MESSAGE, licenseType));
    }
    if (fromDay.isAfter(toDay)) {
      throw new InvalidArgumentsException(
          format("Invalid time range for getting CD license usage per days, accountId: %s, licenseType: %s ", accountId,
              licenseType));
    }

    log.info("Start getting daily license usage report, accountId: {}, licenseType: {}, fromDay: {}, toDay: {}",
        accountId, licenseType, fromDay, toDay);
    if (CDLicenseType.SERVICES == licenseType) {
      List<ServicesLicenseDailyReport> servicesLicenseDailyReports =
          timeScaleDAL.listServicesLicenseDailyReportByAccountId(accountId, fromDay, toDay);

      return servicesLicenseDailyReports.stream().collect(Collectors.toMap(servicesDailyReport
          -> servicesDailyReport.getReportedDay().toString(),
          ServicesLicenseDailyReport::getLicenseCount, (v1, v2) -> {
            throw new InvalidArgumentsException("Reported day should be already unique in license daily report");
          }, LinkedHashMap::new));
    } else {
      List<ServiceInstancesLicenseDailyReport> serviceInstancesLicenseDailyReports =
          timeScaleDAL.listServiceInstancesLicenseDailyReportByAccountId(accountId, fromDay, toDay);

      return serviceInstancesLicenseDailyReports.stream().collect(Collectors.toMap(serviceInstancesDailyReport
          -> serviceInstancesDailyReport.getReportedDay().toString(),
          ServiceInstancesLicenseDailyReport::getLicenseCount, (v1, v2) -> {
            throw new InvalidArgumentsException("Reported day should be already unique in license daily report");
          }, LinkedHashMap::new));
    }
  }

  private Optional<LicenseDailyUsage> findFirstServicesLicenseDailyReport(
      List<ServicesLicenseDailyReport> latestServicesLicenseDailyReportByAccountId) {
    if (isEmpty(latestServicesLicenseDailyReportByAccountId)) {
      return Optional.empty();
    }

    ServicesLicenseDailyReport servicesLicenseDailyReport = latestServicesLicenseDailyReportByAccountId.get(0);
    return Optional.of(LicenseDailyUsage.builder()
                           .accountId(servicesLicenseDailyReport.getAccountId())
                           .reportedDay(servicesLicenseDailyReport.getReportedDay())
                           .licenseCount(servicesLicenseDailyReport.getLicenseCount())
                           .build());
  }

  private Optional<LicenseDailyUsage> findFirstServiceInstancesLicenseDailyReport(
      List<ServiceInstancesLicenseDailyReport> latestServiceInstancesLicenseDailyReportByAccountId) {
    if (isEmpty(latestServiceInstancesLicenseDailyReportByAccountId)) {
      return Optional.empty();
    }

    ServiceInstancesLicenseDailyReport serviceInstancesLicenseDailyReport =
        latestServiceInstancesLicenseDailyReportByAccountId.get(0);
    return Optional.of(LicenseDailyUsage.builder()
                           .accountId(serviceInstancesLicenseDailyReport.getAccountId())
                           .reportedDay(serviceInstancesLicenseDailyReport.getReportedDay())
                           .licenseCount(serviceInstancesLicenseDailyReport.getLicenseCount())
                           .build());
  }

  @VisibleForTesting
  Integer getMonthlyPickLicenseUsage(Map<String, Integer> dailyLicenseUsagePerMonth) {
    if (isEmpty(dailyLicenseUsagePerMonth)) {
      return 0;
    }
    return Collections.max(dailyLicenseUsagePerMonth.entrySet(), Map.Entry.comparingByValue()).getValue();
  }

  private void insertBatchWithRetry(
      String accountId, Supplier<Integer> insertBatchSupplier, CDLicenseType licenseType) {
    int retryCount = 1;
    boolean successfulInsert = false;
    while (!successfulInsert && retryCount <= 3) {
      try {
        Stopwatch sw = Stopwatch.createStarted();
        insertBatchSupplier.get();
        log.info("Successfully inserted batch licenseType: {}, accountId: {}, time taken: {}", licenseType, accountId,
            sw.elapsed(TimeUnit.MILLISECONDS));
        successfulInsert = true;
      } catch (DataAccessException ex) {
        if (retryCount == DB_QUERY_MAX_RETRY) {
          throw new CgLicenseUsageException(
              format(" Failed to insert batch licenseType: %s, after %s retries, accountId: %s", licenseType,
                  DB_QUERY_MAX_RETRY, accountId),
              ex);
        }
        log.warn("Failed to insert batch licenseType: {}, accountId: {} , retry : {}", licenseType, accountId,
            retryCount, ex);
        retryCount++;
      } catch (Exception ex) {
        throw new CgLicenseUsageException(
            format("Failed to insert batch licenseType: %s, accountId: %s", licenseType, accountId), ex);
      }
    }
  }
}
