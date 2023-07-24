/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.usage.jobs;

import static io.harness.NGDateUtils.getCurrentMonthFirstDay;
import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.REGULAR;

import static java.time.Duration.ofSeconds;

import io.harness.CDLicenseDailyReportIteratorConfig;
import io.harness.NgIteratorConfig;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cd.CDLicenseType;
import io.harness.cdng.usage.CDLicenseUsageReportService;
import io.harness.cdng.usage.pojos.LicenseDailyUsage;
import io.harness.cdng.usage.task.CDLicenseReportAccounts;
import io.harness.cdng.usage.task.CDLicenseReportAccounts.CDLicenseReportAccountsKeys;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.filter.MorphiaFilterExpander;
import io.harness.mongo.iterator.provider.MorphiaPersistenceProvider;

import com.google.common.base.Stopwatch;
import com.google.inject.Inject;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class CDLicenseDailyReportIteratorHandler implements MongoPersistenceIterator.Handler<CDLicenseReportAccounts> {
  private static final String LOCK_KEY = "CD_LICENSE_DAILY_REPORT_ITERATOR:";
  private static final int LAST_12_MONTHS = 12;
  private static final Duration ACCEPTABLE_EXECUTION_TIME = ofSeconds(
      13 * 30); // It needs to be up to (12 + 1) * 30s due to 12 months' report plus report for the current month
  private static final int DEFAULT_THREAD_POOL_SIZE = 5;
  private static final long DEFAULT_TARGET_INTERVAL_IN_SECONDS = 12L * 60 * 60; // 12 hours
  public static int QUERY_ON_NUMBER_OF_DAYS = 30;
  public static int BULK_INSERT_LIMIT = 100;
  private static final CDLicenseDailyReportIteratorConfig DEFAULT_ITERATOR_CONFIG =
      CDLicenseDailyReportIteratorConfig.builder()
          .iteratorConfig(NgIteratorConfig.builder()
                              .threadPoolSize(DEFAULT_THREAD_POOL_SIZE)
                              .targetIntervalInSeconds(DEFAULT_TARGET_INTERVAL_IN_SECONDS)
                              .build())
          .bulkInsertLimit(BULK_INSERT_LIMIT)
          .queryOnDaysNumber(QUERY_ON_NUMBER_OF_DAYS)
          .build();

  private PersistenceIteratorFactory persistenceIteratorFactory;
  private MorphiaPersistenceProvider<CDLicenseReportAccounts> persistenceProvider;
  private PersistentLocker persistentLocker;
  private CDLicenseUsageReportService licenseUsageReportService;

  public void registerIterator(CDLicenseDailyReportIteratorConfig cdLicenseDailyReportIteratorConfig) {
    if (cdLicenseDailyReportIteratorConfig == null) {
      // iterator is enabled by default
      cdLicenseDailyReportIteratorConfig = DEFAULT_ITERATOR_CONFIG;
    }
    NgIteratorConfig ngIteratorConfig = cdLicenseDailyReportIteratorConfig.getIteratorConfig();
    if (ngIteratorConfig == null) {
      log.warn("Not set base iterator configs");
      return;
    }
    if (ngIteratorConfig.getThreadPoolSize() <= 0) {
      log.warn("Illegal {} thread pool size. Size has to be higher than zero, pool size: {}", this.getClass().getName(),
          ngIteratorConfig.getThreadPoolSize());
      return;
    }
    if (ngIteratorConfig.getTargetIntervalInSeconds() <= 0) {
      log.warn("Illegal {} target interval. Interval has to be higher than zero, target interval: {}",
          this.getClass().getName(), ngIteratorConfig.getTargetIntervalInSeconds());
      return;
    }
    if (cdLicenseDailyReportIteratorConfig.getBulkInsertLimit() <= 0) {
      log.warn("Illegal {} bulk insert limit. Bulk insert limit has to be higher than zero, bulk insert limit: {}",
          this.getClass().getName(), cdLicenseDailyReportIteratorConfig.getBulkInsertLimit());
      return;
    }
    if (cdLicenseDailyReportIteratorConfig.getQueryOnDaysNumber() <= 0) {
      log.warn(
          "Illegal {} query on number of days. Query on number of days has to be higher than zero, query days number: {}",
          this.getClass().getName(), cdLicenseDailyReportIteratorConfig.getQueryOnDaysNumber());
      return;
    }
    QUERY_ON_NUMBER_OF_DAYS = cdLicenseDailyReportIteratorConfig.getQueryOnDaysNumber();
    BULK_INSERT_LIMIT = cdLicenseDailyReportIteratorConfig.getBulkInsertLimit();

    log.info(
        "Created {} iterator, pool size: {}, target interval: {}s, bulk insert limit: {}, query on days number: {} ",
        this.getClass().getName(), ngIteratorConfig.getThreadPoolSize(), ngIteratorConfig.getTargetIntervalInSeconds(),
        cdLicenseDailyReportIteratorConfig.getBulkInsertLimit(),
        cdLicenseDailyReportIteratorConfig.getQueryOnDaysNumber());

    persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(
        PersistenceIteratorFactory.PumpExecutorOptions.builder()
            .name(this.getClass().getName())
            .poolSize(ngIteratorConfig.getThreadPoolSize())
            .interval(ofSeconds(ngIteratorConfig.getTargetIntervalInSeconds()))
            .build(),
        CDLicenseDailyReportIteratorHandler.class,
        MongoPersistenceIterator.<CDLicenseReportAccounts, MorphiaFilterExpander<CDLicenseReportAccounts>>builder()
            .clazz(CDLicenseReportAccounts.class)
            .fieldName(CDLicenseReportAccountsKeys.cdLicenseDailyReportIteration)
            .targetInterval(ofSeconds(ngIteratorConfig.getTargetIntervalInSeconds()))
            .acceptableExecutionTime(ACCEPTABLE_EXECUTION_TIME)
            .acceptableNoAlertDelay(ofSeconds(ngIteratorConfig.getTargetIntervalInSeconds() * 2))
            .handler(this)
            .schedulingType(REGULAR)
            .persistenceProvider(persistenceProvider)
            .redistribute(true));
  }

  @Override
  public void handle(CDLicenseReportAccounts deploymentAccounts) {
    String accountId = deploymentAccounts.getAccountIdentifier();
    try (AutoLogContext ignore = new AccountLogContext(accountId, AutoLogContext.OverrideBehavior.OVERRIDE_ERROR)) {
      Optional<CDLicenseType> cdLicenseType = licenseUsageReportService.getCDLicenseTypePerAccount(accountId);
      if (cdLicenseType.isEmpty()) {
        log.info("Not found CD license type for account: {}, hence skipping license report creation", accountId);
        return;
      }

      CDLicenseType licenseType = cdLicenseType.get();
      log.info("Running CD License daily report iterator for account: {}, licenseType: {}", accountId, licenseType);
      Stopwatch sw = Stopwatch.createStarted();
      createLicenseDailyReport(accountId, licenseType);
      log.info("Successfully created CD license daily report, time taken : {}ms, accountId: {}, licenseType: {}",
          sw.elapsed(TimeUnit.MILLISECONDS), accountId, licenseType);
    } catch (Exception ex) {
      log.warn("Failed to create CD license daily report, accountId: {}", accountId, ex);
    }
  }

  private void createLicenseDailyReport(final String accountId, CDLicenseType licenseType) {
    try (AcquiredLock lock = persistentLocker.tryToAcquireLock(LOCK_KEY + accountId, Duration.ofSeconds(120))) {
      if (lock == null) {
        log.error("Unable to acquire lock for creating CD license daily report, accountId: {}, licenseType: {}",
            accountId, licenseType);
        return;
      }

      // job needs to generate report from: (fromDate could be 12 months back or missing report days back)
      // to: one day prior the job is triggered
      LocalDate fromDate = setFromDate(accountId, licenseType);
      LocalDate toDate = LocalDate.now(ZoneOffset.UTC).minusDays(1);
      if (fromDate.isAfter(toDate)) {
        log.info("CD license daily report is already generated for day {}, fromDate: {}", toDate, fromDate);
        return;
      }
      List<LicenseDailyUsage> newLicenseDailyReport =
          licenseUsageReportService.generateLicenseDailyUsageReport(accountId, licenseType, fromDate, toDate);
      licenseUsageReportService.insertBatchLicenseDailyUsageRecords(accountId, licenseType, newLicenseDailyReport);
    }
  }

  @NotNull
  private LocalDate setFromDate(final String accountId, CDLicenseType licenseType) {
    Optional<LicenseDailyUsage> latestLicenseDailyUsageReport =
        licenseUsageReportService.getLatestLicenseDailyUsageRecord(accountId, licenseType);
    if (latestLicenseDailyUsageReport.isPresent()) {
      LocalDate latestLicenseUsageReportedDay = latestLicenseDailyUsageReport.get().getReportedDay();
      return latestLicenseUsageReportedDay.plusDays(1);
    } else {
      return getCurrentMonthFirstDay().minusMonths(LAST_12_MONTHS);
    }
  }
}
