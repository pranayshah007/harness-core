/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import io.harness.event.handler.impl.segment.SegmentGroupEventJobService;
import io.harness.migrations.Migration;

import software.wings.beans.Account;
import software.wings.dl.WingsPersistence;
import software.wings.scheduler.events.segment.SegmentGroupEventJobContext;
import software.wings.service.intfc.AccountService;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ScheduleSegmentPublishJob implements Migration {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private AccountService accountService;

  @Override
  public void migrate() {
    try {
      // delete entries if any
      wingsPersistence.delete(wingsPersistence.createQuery(SegmentGroupEventJobContext.class));

      List<Account> accounts = accountService.getAccountsWithBasicInfo(false);
      List<List<Account>> accountLists = Lists.partition(accounts, SegmentGroupEventJobService.ACCOUNT_BATCH_SIZE);

      Instant nextIteration = Instant.now();

      // schedules a job for each subList. So, one job will have a list of accountIds
      for (List<Account> accountList : accountLists) {
        List<String> accountIds = accountList.stream().map(Account::getUuid).collect(Collectors.toList());

        nextIteration = nextIteration.plus(30, ChronoUnit.MINUTES);
        SegmentGroupEventJobContext jobContext =
            new SegmentGroupEventJobContext(nextIteration.toEpochMilli(), accountIds);
        wingsPersistence.save(jobContext);
      }

    } catch (Exception e) {
      log.error("Exception scheduling segment job", e);
    }
  }
}
