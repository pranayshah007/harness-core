/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories.executions;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.pipeline.executions.CDAccountExecutionMetadata;
import io.harness.cdng.pipeline.executions.CDAccountExecutionMetadata.CDAccountExecutionMetadataKeys;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.pms.plan.execution.AccountExecutionInfo;
import io.harness.springdata.PersistenceUtils;

import com.google.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PIPELINE)
public class CDAccountExecutionMetadataRepositoryCustomImpl implements CDAccountExecutionMetadataRepositoryCustom {
  private static final String LOCK_NAME_PREFIX = "ACCOUNT_EXECUTION_INFO_";
  private final MongoTemplate mongoTemplate;
  private final PersistentLocker persistentLocker;

  @Override
  public void updateAccountExecutionMetadata(String accountId, Long startTS) {
    Criteria criteria = Criteria.where(CDAccountExecutionMetadataKeys.accountId).is(accountId);
    Query query = new Query(criteria);
    // Since there can be parallel executions for a given account, update after taking a lock
    try (
        AcquiredLock<?> lock = persistentLocker.tryToAcquireLock(LOCK_NAME_PREFIX + accountId, Duration.ofMinutes(7))) {
      if (lock == null) {
        throw new InvalidRequestException("Could not acquire lock");
      }

      CDAccountExecutionMetadata accountExecutionMetadata =
          mongoTemplate.findOne(query, CDAccountExecutionMetadata.class);
      // If there is no entry, then create an entry in the db for the given account
      if (accountExecutionMetadata == null) {
        CDAccountExecutionMetadata newAccountExecutionMetadata =
            CDAccountExecutionMetadata.builder()
                .accountId(accountId)
                .executionCount(1L)
                .accountExecutionInfo(AccountExecutionInfo.builder().build())
                .build();
        mongoTemplate.save(newAccountExecutionMetadata);
        return;
      }
      accountExecutionMetadata.setExecutionCount(accountExecutionMetadata.getExecutionCount() + 1);
      // Increase count per month
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
      mongoTemplate.save(accountExecutionMetadata);
    }
  }

  @Override
  public boolean deleteForAccount(String accountId) {
    try {
      Criteria criteria = new Criteria();
      criteria.and(CDAccountExecutionMetadataKeys.accountId).is(accountId);

      Failsafe.with(getDeleteRetryPolicy(accountId))
          .get(() -> mongoTemplate.remove(new Query(criteria), CDAccountExecutionMetadata.class));
      return true;
    } catch (Exception e) {
      log.warn(format("Error while deleting CDAccountExecutionMetadata for Account [%s] : %s", accountId,
                   ExceptionUtils.getMessage(e)),
          e);
      return false;
    }
  }

  private RetryPolicy<Object> getDeleteRetryPolicy(String accountId) {
    return PersistenceUtils.getRetryPolicy(
        format("[Retrying]: Failed deleting CDAccountExecutionMetadata for account: [%s]; attempt: {}", accountId),
        format("[Failed]: Failed CDAccountExecutionMetadata for account: [%s]; attempt: {}", accountId));
  }
}
