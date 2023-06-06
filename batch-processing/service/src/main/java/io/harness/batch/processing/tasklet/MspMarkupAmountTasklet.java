/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.tasklet;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.msp.entities.AmountDetails.AmountDetailsBuilder;

import io.harness.annotations.dev.OwnedBy;
import io.harness.batch.processing.ccm.CCMJobConstants;
import io.harness.ccm.commons.beans.JobConstants;
import io.harness.ccm.graphql.core.msp.intf.ManagedAccountDataService;
import io.harness.ccm.msp.dto.ManagedAccount;
import io.harness.ccm.msp.entities.AmountDetails;
import io.harness.ccm.msp.entities.ManagedAccountStats;
import io.harness.ccm.msp.service.intf.ManagedAccountService;
import io.harness.ccm.msp.service.intf.MarginDetailsService;

import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Pair;

@Slf4j
@OwnedBy(CE)
public class MspMarkupAmountTasklet implements Tasklet {
  @Autowired private ManagedAccountService managedAccountService;
  @Autowired private MarginDetailsService marginDetailsService;
  @Autowired private ManagedAccountDataService managedAccountDataService;
  private static final String DEFAULT_TIMEZONE = "GMT";

  @Override
  public RepeatStatus execute(StepContribution stepContribution, ChunkContext chunkContext) throws Exception {
    final JobConstants jobConstants = CCMJobConstants.fromContext(chunkContext);
    String mspAccountId = jobConstants.getAccountId();
    List<ManagedAccount> managedAccounts = managedAccountService.list(mspAccountId);
    if (managedAccounts != null) {
      for (ManagedAccount managedAccount : managedAccounts) {
        String managedAccountId = managedAccount.getAccountId();
        AmountDetailsBuilder markupAmountDetailsBuilder = AmountDetails.builder();
        AmountDetailsBuilder totalSpendDetailsBuilder = AmountDetails.builder();
        for (Interval interval : Interval.values()) {
          Pair<Long, Long> startAndEndTimes = getInterval(interval);
          ManagedAccountStats stats = managedAccountDataService.getManagedAccountStats(
              mspAccountId, managedAccountId, startAndEndTimes.getFirst(), startAndEndTimes.getSecond());
          switch (interval) {
            case CURRENT_MONTH:
              markupAmountDetailsBuilder.currentMonth(stats.getTotalMarkupStats().getCurrentPeriod());
              totalSpendDetailsBuilder.currentMonth(stats.getTotalSpendStats().getCurrentPeriod());
              break;
            case CURRENT_QUARTER:
              markupAmountDetailsBuilder.currentQuarter(stats.getTotalMarkupStats().getCurrentPeriod());
              totalSpendDetailsBuilder.currentQuarter(stats.getTotalSpendStats().getCurrentPeriod());
              break;
            case LAST_MONTH:
              markupAmountDetailsBuilder.lastMonth(stats.getTotalMarkupStats().getCurrentPeriod());
              totalSpendDetailsBuilder.lastMonth(stats.getTotalSpendStats().getCurrentPeriod());
              break;
            case LAST_QUARTER:
              markupAmountDetailsBuilder.lastQuarter(stats.getTotalMarkupStats().getCurrentPeriod());
              totalSpendDetailsBuilder.lastQuarter(stats.getTotalSpendStats().getCurrentPeriod());
              break;
            default:
              throw new IllegalArgumentException("Invalid interval");
          }
        }
        AmountDetails markupAmountDetails = markupAmountDetailsBuilder.build();
        AmountDetails totalSpendDetails = totalSpendDetailsBuilder.build();
        log.info("markupAmountDetails: {}, totalSpendDetails: {}", markupAmountDetails, totalSpendDetails);
        marginDetailsService.updateMarkupAmount(mspAccountId, managedAccountId, markupAmountDetails, totalSpendDetails);
      }
    }
    return null;
  }

  private enum Interval { CURRENT_MONTH, CURRENT_QUARTER, LAST_MONTH, LAST_QUARTER }

  private static Pair<Long, Long> getInterval(Interval interval) {
    Calendar startCalendar = Calendar.getInstance(TimeZone.getTimeZone(DEFAULT_TIMEZONE));
    Calendar endCalendar = Calendar.getInstance(TimeZone.getTimeZone(DEFAULT_TIMEZONE));

    switch (interval) {
      case CURRENT_MONTH:
        startCalendar.set(Calendar.DAY_OF_MONTH, 1);
        startCalendar.set(Calendar.HOUR_OF_DAY, 0);
        startCalendar.set(Calendar.MINUTE, 0);
        startCalendar.set(Calendar.SECOND, 0);
        startCalendar.set(Calendar.MILLISECOND, 0);

        endCalendar.add(Calendar.MONTH, 1);
        endCalendar.set(Calendar.DAY_OF_MONTH, 1);
        endCalendar.set(Calendar.HOUR_OF_DAY, 0);
        endCalendar.set(Calendar.MINUTE, 0);
        endCalendar.set(Calendar.SECOND, 0);
        endCalendar.set(Calendar.MILLISECOND, 0);
        endCalendar.add(Calendar.DAY_OF_MONTH, -1);
        break;

      case CURRENT_QUARTER:
        int currentMonth = startCalendar.get(Calendar.MONTH);
        startCalendar.set(Calendar.MONTH, currentMonth - (currentMonth % 3));
        startCalendar.set(Calendar.DAY_OF_MONTH, 1);
        startCalendar.set(Calendar.HOUR_OF_DAY, 0);
        startCalendar.set(Calendar.MINUTE, 0);
        startCalendar.set(Calendar.SECOND, 0);
        startCalendar.set(Calendar.MILLISECOND, 0);

        endCalendar.setTimeInMillis(startCalendar.getTimeInMillis());
        endCalendar.add(Calendar.MONTH, 3);
        endCalendar.add(Calendar.DAY_OF_MONTH, -1);
        endCalendar.set(Calendar.HOUR_OF_DAY, 23);
        endCalendar.set(Calendar.MINUTE, 59);
        endCalendar.set(Calendar.SECOND, 59);
        endCalendar.set(Calendar.MILLISECOND, 999);
        break;

      case LAST_MONTH:
        startCalendar.add(Calendar.MONTH, -1);
        startCalendar.set(Calendar.DAY_OF_MONTH, 1);
        startCalendar.set(Calendar.HOUR_OF_DAY, 0);
        startCalendar.set(Calendar.MINUTE, 0);
        startCalendar.set(Calendar.SECOND, 0);
        startCalendar.set(Calendar.MILLISECOND, 0);

        endCalendar.setTimeInMillis(startCalendar.getTimeInMillis());
        endCalendar.add(Calendar.MONTH, 1);
        endCalendar.add(Calendar.DAY_OF_MONTH, -1);
        endCalendar.set(Calendar.HOUR_OF_DAY, 23);
        endCalendar.set(Calendar.MINUTE, 59);
        endCalendar.set(Calendar.SECOND, 59);
        endCalendar.set(Calendar.MILLISECOND, 999);
        break;

      case LAST_QUARTER:
        int lastQuarterMonth = startCalendar.get(Calendar.MONTH) - 3;
        startCalendar.set(Calendar.MONTH, lastQuarterMonth - (lastQuarterMonth % 3));
        startCalendar.set(Calendar.DAY_OF_MONTH, 1);
        startCalendar.set(Calendar.HOUR_OF_DAY, 0);
        startCalendar.set(Calendar.MINUTE, 0);
        startCalendar.set(Calendar.SECOND, 0);
        startCalendar.set(Calendar.MILLISECOND, 0);

        endCalendar.setTimeInMillis(startCalendar.getTimeInMillis());
        endCalendar.add(Calendar.MONTH, 3);
        endCalendar.add(Calendar.DAY_OF_MONTH, -1);
        endCalendar.set(Calendar.HOUR_OF_DAY, 23);
        endCalendar.set(Calendar.MINUTE, 59);
        endCalendar.set(Calendar.SECOND, 59);
        endCalendar.set(Calendar.MILLISECOND, 999);
        break;

      default:
        throw new IllegalArgumentException("Invalid interval");
    }

    long startEpochMilli = startCalendar.getTimeInMillis();
    long endEpochMilli = endCalendar.getTimeInMillis();

    return Pair.of(startEpochMilli, endEpochMilli);
  }
}
