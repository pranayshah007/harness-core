/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ci.execution.CIAccountExecutionMetadata;
import io.harness.ci.execution.CIAccountExecutionMetadata.CIAccountExecutionMetadataKeys;
import io.harness.exception.InvalidRequestException;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.pms.plan.execution.AccountExecutionInfo;

import com.google.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(CI)
public class CIAccountExecutionMetadataRepositoryCustomImpl implements CIAccountExecutionMetadataRepositoryCustom {
  private static final String LOCK_NAME_PREFIX = "CI_ACCOUNT_EXECUTION_INFO_";
  private final MongoTemplate mongoTemplate;
  private final PersistentLocker persistentLocker;
  private static final String LOCK_NAME_PREFIX_DAILY = "CI_ACCOUNT_EXECUTION_INFO_DAILY_";

  @Override
  public void updateAccountExecutionMetadata(String accountId, Long startTS) {
    Criteria criteria = Criteria.where(CIAccountExecutionMetadataKeys.accountId).is(accountId);
    Query query = new Query(criteria);
    // Since there can be parallel executions for a given account, update after taking a lock
    try (
        AcquiredLock<?> lock = persistentLocker.tryToAcquireLock(LOCK_NAME_PREFIX + accountId, Duration.ofMinutes(2))) {
      if (lock == null) {
        throw new InvalidRequestException("Could not acquire lock");
      }

      CIAccountExecutionMetadata accountExecutionMetadata =
          mongoTemplate.findOne(query, CIAccountExecutionMetadata.class);
      // If there is no entry, then create an entry in the db for the given account
      if (accountExecutionMetadata == null) {
        CIAccountExecutionMetadata newAccountExecutionMetadata =
            CIAccountExecutionMetadata.builder()
                .accountId(accountId)
                .executionCount(1L)
                .accountExecutionInfo(AccountExecutionInfo.builder().build())
                .build();
        mongoTemplate.save(newAccountExecutionMetadata);
        return;
      }
      long currentCount = accountExecutionMetadata.getExecutionCount();
      accountExecutionMetadata.setExecutionCount(currentCount + 1);
      // Increase count per month
      if (currentCount > 2500) {
        updateCIMonthlyBuilds(accountExecutionMetadata, startTS);
      }
      mongoTemplate.save(accountExecutionMetadata);
    }
  }

  @Override
  public void updateCIDailyBuilds(String accountId, Long startTS) {
    Criteria criteria = Criteria.where(CIAccountExecutionMetadataKeys.accountId).is(accountId);
    Query query = new Query(criteria);
    try (AcquiredLock<?> lock = persistentLocker.waitToAcquireLockOptional(
             LOCK_NAME_PREFIX_DAILY + accountId, Duration.ofSeconds(20), Duration.ofSeconds(10))) {
      if (lock == null) {
        throw new InvalidRequestException("Could not acquire lock");
      }
      CIAccountExecutionMetadata accountExecutionMetadata =
          mongoTemplate.findOne(query, CIAccountExecutionMetadata.class);
      // If there is no entry, then create an entry in the db for the given account
      LocalDate startDate = Instant.ofEpochMilli(startTS).atZone(ZoneId.systemDefault()).toLocalDate();
      if (accountExecutionMetadata == null) {
        Map<String, Long> countPerDay = new HashMap<>();
        countPerDay.put(YearMonth.of(startDate.getYear(), startDate.getMonth()) + "-" + startDate.getDayOfMonth(), 1L);
        CIAccountExecutionMetadata newAccountExecutionMetadata =
            CIAccountExecutionMetadata.builder()
                .accountId(accountId)
                .executionCount(1L)
                .accountExecutionInfo(AccountExecutionInfo.builder().countPerDay(countPerDay).build())
                .build();
        mongoTemplate.save(newAccountExecutionMetadata);
        lock.release();
        return;
      }

      AccountExecutionInfo accountExecutionInfo;
      if (accountExecutionMetadata.getAccountExecutionInfo() != null) {
        accountExecutionInfo = accountExecutionMetadata.getAccountExecutionInfo();
      } else {
        accountExecutionInfo = AccountExecutionInfo.builder().build();
        accountExecutionMetadata.setAccountExecutionInfo(accountExecutionInfo);
      }

      Long countOfDay;
      if (accountExecutionInfo.getCountPerDay() != null) {
        countOfDay = accountExecutionInfo.getCountPerDay().getOrDefault(
            YearMonth.of(startDate.getYear(), startDate.getMonth()).toString() + "-" + startDate.getDayOfMonth(), 0L);
        countOfDay = countOfDay + 1;
        accountExecutionInfo.getCountPerDay().put(
            YearMonth.of(startDate.getYear(), startDate.getMonth()) + "-" + startDate.getDayOfMonth(), countOfDay);
        accountExecutionMetadata.setAccountExecutionInfo(accountExecutionInfo);
      } else {
        Map<String, Long> countPerDay = new HashMap<>();
        countPerDay.put(YearMonth.of(startDate.getYear(), startDate.getMonth()) + "-" + startDate.getDayOfMonth(), 1L);
        accountExecutionInfo.setCountPerDay(countPerDay);
        accountExecutionMetadata.setAccountExecutionInfo(accountExecutionInfo);
      }

      mongoTemplate.save(accountExecutionMetadata);
    }
  }

  private void updateCIMonthlyBuilds(CIAccountExecutionMetadata accountExecutionMetadata, Long startTS) {
    AccountExecutionInfo accountExecutionInfo;
    if (accountExecutionMetadata.getAccountExecutionInfo() != null) {
      accountExecutionInfo = accountExecutionMetadata.getAccountExecutionInfo();
    } else {
      accountExecutionInfo = AccountExecutionInfo.builder().build();
      accountExecutionMetadata.setAccountExecutionInfo(accountExecutionInfo);
    }
    LocalDate startDate = Instant.ofEpochMilli(startTS).atZone(ZoneId.systemDefault()).toLocalDate();
    Long countOfMonth = accountExecutionInfo.getCountPerMonth().getOrDefault(
        YearMonth.of(startDate.getYear(), startDate.getMonth()).toString(), 0L);
    countOfMonth = countOfMonth + 1;
    accountExecutionInfo.getCountPerMonth().put(
        YearMonth.of(startDate.getYear(), startDate.getMonth()).toString(), countOfMonth);
    accountExecutionMetadata.setAccountExecutionInfo(accountExecutionInfo);
  }
}
