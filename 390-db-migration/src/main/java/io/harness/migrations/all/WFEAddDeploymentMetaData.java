/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.beans.ExecutionStatus;
import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;

import software.wings.beans.Account;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.WorkflowExecution.WorkflowExecutionKeys;
import software.wings.dl.WingsPersistence;
import software.wings.service.impl.WorkflowExecutionUpdate;
import software.wings.service.intfc.AccountService;

import com.google.inject.Inject;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.FindOptions;
import org.mongodb.morphia.query.Sort;

@Slf4j
public class WFEAddDeploymentMetaData implements Migration {
  @Inject WingsPersistence wingsPersistence;
  @Inject WorkflowExecutionUpdate workflowExecutionUpdate;
  @Inject AccountService accountService;

  @Override
  public void migrate() {
    long totalCount = 0L;
    long lastTimeStamp = 0L;
    try {
      FindOptions findOptions = new FindOptions();
      List<Account> accounts = accountService.getAccountsWithBasicInfo(false);
      for (Account account : accounts) {
        long count = 0L;
        try (HIterator<WorkflowExecution> iterator =
                 new HIterator<>(wingsPersistence.createQuery(WorkflowExecution.class, excludeAuthority)
                                     .field(WorkflowExecutionKeys.createdAt)
                                     .greaterThanOrEq(System.currentTimeMillis() - (180 * 24 * 3600 * 1000L))
                                     .field(WorkflowExecutionKeys.startTs)
                                     .exists()
                                     .field(WorkflowExecutionKeys.endTs)
                                     .exists()
                                     .field(WorkflowExecutionKeys.accountId)
                                     .equal(account.getUuid())
                                     .field(WorkflowExecutionKeys.status)
                                     .in(ExecutionStatus.finalStatuses())
                                     .order(Sort.descending(WorkflowExecutionKeys.createdAt))
                                     .fetch(findOptions))) {
          while (iterator.hasNext()) {
            WorkflowExecution workflowExecution = iterator.next();
            lastTimeStamp = workflowExecution.getCreatedAt();
            workflowExecutionUpdate.updateDeploymentInformation(workflowExecution);
            count++;
            totalCount++;
            if (count % 1000 == 0) {
              log.info("Completed migrating [{}] records for account:[{}]", count, account.getAccountName());
            }
          }
        } finally {
          log.info("Completed migrating [{}] records for account:[{}], lastTimeStamp=[{}]", count,
              account.getAccountName(), lastTimeStamp);
        }
      }
    } catch (Exception e) {
      log.warn("Failed to complete migration", e);
    } finally {
      log.info("Completed migrating [{}] records, lastTimeStamp=[{}]", totalCount, lastTimeStamp);
    }
  }
}
