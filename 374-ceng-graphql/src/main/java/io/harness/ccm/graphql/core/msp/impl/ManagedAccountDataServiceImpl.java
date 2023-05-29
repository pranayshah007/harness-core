/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.graphql.core.msp.impl;

import static io.harness.ccm.graphql.utils.GraphQLUtils.DEFAULT_LIMIT;
import static io.harness.ccm.graphql.utils.GraphQLUtils.DEFAULT_OFFSET;
import static io.harness.ccm.views.graphql.QLCEViewFilterOperator.IN;

import io.harness.ccm.commons.entities.CCMField;
import io.harness.ccm.graphql.core.msp.intf.ManagedAccountDataService;
import io.harness.ccm.graphql.dto.perspectives.PerspectiveTrendStats;
import io.harness.ccm.graphql.query.perspectives.PerspectivesQuery;
import io.harness.ccm.graphql.utils.GraphQLToRESTHelper;
import io.harness.ccm.graphql.utils.RESTToGraphQLHelper;
import io.harness.ccm.msp.dao.MarginDetailsDao;
import io.harness.ccm.msp.entities.AmountDetails;
import io.harness.ccm.msp.entities.AmountTrendStats;
import io.harness.ccm.msp.entities.ManagedAccountStats;
import io.harness.ccm.msp.entities.ManagedAccountTimeSeriesData;
import io.harness.ccm.msp.entities.ManagedAccountsOverview;
import io.harness.ccm.msp.entities.MarginDetails;
import io.harness.ccm.views.dto.TimeSeriesDataPoints;
import io.harness.ccm.views.graphql.QLCEViewFieldInput;
import io.harness.ccm.views.graphql.QLCEViewFilter;
import io.harness.ccm.views.graphql.QLCEViewFilterWrapper;
import io.harness.ccm.views.graphql.QLCEViewPreferences;

import com.google.inject.Inject;
import io.leangen.graphql.execution.ResolutionEnvironment;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;
import java.util.stream.Collectors;

public class ManagedAccountDataServiceImpl implements ManagedAccountDataService {
  @Inject private PerspectivesQuery perspectivesQuery;
  @Inject private MarginDetailsDao marginDetailsDao;

  private static final long MAX_DAYS_IN_MONTH = 31;
  private static final long ONE_DAY_IN_MILLIS = 86400000;

  @Override
  public List<String> getEntityList(
      String managedAccountId, CCMField entity, String searchParam, Integer limit, Integer offset) {
    try {
      final ResolutionEnvironment env = GraphQLToRESTHelper.createResolutionEnv(managedAccountId);
      QLCEViewFieldInput entityConvertedToFieldInput = RESTToGraphQLHelper.getViewFieldInputFromCCMField(entity);
      List<QLCEViewFilterWrapper> filters = new ArrayList<>();
      filters.add(QLCEViewFilterWrapper.builder()
                      .idFilter(QLCEViewFilter.builder()
                                    .field(entityConvertedToFieldInput)
                                    .operator(IN)
                                    .values(Collections.singletonList("").toArray(new String[0]))
                                    .build())
                      .build());
      return perspectivesQuery
          .perspectiveFilters(Collections.emptyList(), filters, Collections.emptyList(), Collections.emptyList(), limit,
              offset, false, env)
          .getValues();
    } catch (Exception e) {
      return Collections.emptyList();
    }
  }

  @Override
  public ManagedAccountsOverview getTotalMarkupAndSpend(String mspAccountId) {
    List<MarginDetails> marginDetailsList = marginDetailsDao.list(mspAccountId);
    return ManagedAccountsOverview.builder()
        .totalMarkupAmount(getTotalMarkupAmountDetails(marginDetailsList))
        .totalSpend(getTotalSpendDetails(marginDetailsList))
        .build();
  }

  @Override
  public ManagedAccountStats getManagedAccountStats(String mspAccountId, long startTime, long endTime) {
    ManagedAccountsOverview totalMarkupAndSpend = getTotalMarkupAndSpend(mspAccountId);
    return getTotalMarkupAndSpendForPeriod(totalMarkupAndSpend, startTime, endTime);
  }

  private ManagedAccountStats getTotalMarkupAndSpendForPeriod(
      ManagedAccountsOverview totalMarkupAndSpend, long startTime, long endTime) {
    long startOfCurrentMonth = getStartOfCurrentMonth();
    long quarterStartThreshold = getQuarterStartThreshold();
    long diff = (endTime - startTime) / ONE_DAY_IN_MILLIS;
    double totalSpend;
    double totalMarkup;

    if (diff >= MAX_DAYS_IN_MONTH) {
      if (startTime < quarterStartThreshold) {
        totalMarkup = totalMarkupAndSpend.getTotalMarkupAmount().getLastQuarter();
        totalSpend = totalMarkupAndSpend.getTotalSpend().getLastQuarter();
      } else {
        totalMarkup = totalMarkupAndSpend.getTotalMarkupAmount().getCurrentQuarter();
        totalSpend = totalMarkupAndSpend.getTotalSpend().getCurrentQuarter();
      }
    } else {
      if (startTime == startOfCurrentMonth) {
        totalMarkup = totalMarkupAndSpend.getTotalMarkupAmount().getCurrentMonth();
        totalSpend = totalMarkupAndSpend.getTotalSpend().getCurrentMonth();
      } else {
        totalMarkup = totalMarkupAndSpend.getTotalMarkupAmount().getLastMonth();
        totalSpend = totalMarkupAndSpend.getTotalSpend().getLastQuarter();
      }
    }

    return ManagedAccountStats.builder()
        .totalSpendStats(AmountTrendStats.builder().currentPeriod(totalSpend).build())
        .totalMarkupStats(AmountTrendStats.builder().currentPeriod(totalMarkup).build())
        .build();
  }

  @Override
  public ManagedAccountStats getManagedAccountStats(
      String mspAccountId, String managedAccountId, long startTime, long endTime) {
    if (managedAccountId == null) {
      return getManagedAccountStats(mspAccountId, startTime, endTime);
    }
    final ResolutionEnvironment env = GraphQLToRESTHelper.createResolutionEnv(mspAccountId);

    PerspectiveTrendStats totalSpendStats =
        perspectivesQuery.perspectiveTrendStats(RESTToGraphQLHelper.getTimeFilters(startTime, endTime),
            Collections.emptyList(), RESTToGraphQLHelper.getCostAggregation(), false, env);

    PerspectiveTrendStats markupAmountStats =
        perspectivesQuery.perspectiveTrendStats(RESTToGraphQLHelper.getTimeFilters(startTime, endTime),
            Collections.emptyList(), RESTToGraphQLHelper.getMarkupAggregation(), false, env);

    return ManagedAccountStats.builder()
        .totalSpendStats(AmountTrendStats.builder()
                             .currentPeriod(totalSpendStats.getCost().getValue().doubleValue())
                             .trend(totalSpendStats.getCost().getStatsTrend().doubleValue())
                             .build())
        .totalMarkupStats(AmountTrendStats.builder()
                              .currentPeriod(markupAmountStats.getCost().getValue().doubleValue())
                              .trend(markupAmountStats.getCost().getStatsTrend().doubleValue())
                              .build())
        .build();
  }

  @Override
  public ManagedAccountTimeSeriesData getManagedAccountTimeSeriesData(
      String mspAccountId, String managedAccountId, long startTime, long endTime) {
    final ResolutionEnvironment env = GraphQLToRESTHelper.createResolutionEnv(managedAccountId);
    QLCEViewPreferences qlCEViewPreferences =
        QLCEViewPreferences.builder().includeOthers(false).includeUnallocatedCost(false).build();
    List<TimeSeriesDataPoints> totalSpendStats =
        perspectivesQuery
            .perspectiveTimeSeriesStats(RESTToGraphQLHelper.getCostAggregation(),
                RESTToGraphQLHelper.getTimeFilters(startTime, endTime),
                Collections.singletonList(RESTToGraphQLHelper.getGroupByDay()), Collections.emptyList(),
                (int) DEFAULT_LIMIT, (int) DEFAULT_OFFSET, qlCEViewPreferences, false, env)
            .getStats();

    List<TimeSeriesDataPoints> totalMarkupStats =
        perspectivesQuery
            .perspectiveTimeSeriesStats(RESTToGraphQLHelper.getMarkupAggregation(),
                RESTToGraphQLHelper.getTimeFilters(startTime, endTime),
                Collections.singletonList(RESTToGraphQLHelper.getGroupByDay()), Collections.emptyList(),
                (int) DEFAULT_LIMIT, (int) DEFAULT_OFFSET, qlCEViewPreferences, false, env)
            .getStats();
    return ManagedAccountTimeSeriesData.builder()
        .totalSpendStats(totalSpendStats)
        .totalMarkupStats(totalMarkupStats)
        .build();
  }

  private AmountDetails getTotalMarkupAmountDetails(List<MarginDetails> marginDetailsList) {
    return AmountDetails.builder()
        .currentMonth(marginDetailsList.stream()
                          .filter(marginDetails -> marginDetails.getMarkupAmountDetails() != null)
                          .map(marginDetails -> marginDetails.getMarkupAmountDetails().getCurrentMonth())
                          .collect(Collectors.toList())
                          .stream()
                          .mapToDouble(Double::doubleValue)
                          .sum())
        .lastMonth(marginDetailsList.stream()
                       .filter(marginDetails -> marginDetails.getMarkupAmountDetails() != null)
                       .map(marginDetails -> marginDetails.getMarkupAmountDetails().getLastMonth())
                       .collect(Collectors.toList())
                       .stream()
                       .mapToDouble(Double::doubleValue)
                       .sum())
        .currentQuarter(marginDetailsList.stream()
                            .filter(marginDetails -> marginDetails.getMarkupAmountDetails() != null)
                            .map(marginDetails -> marginDetails.getMarkupAmountDetails().getCurrentQuarter())
                            .collect(Collectors.toList())
                            .stream()
                            .mapToDouble(Double::doubleValue)
                            .sum())
        .lastQuarter(marginDetailsList.stream()
                         .filter(marginDetails -> marginDetails.getMarkupAmountDetails() != null)
                         .map(marginDetails -> marginDetails.getMarkupAmountDetails().getLastQuarter())
                         .collect(Collectors.toList())
                         .stream()
                         .mapToDouble(Double::doubleValue)
                         .sum())
        .build();
  }

  private AmountDetails getTotalSpendDetails(List<MarginDetails> marginDetailsList) {
    return AmountDetails.builder()
        .currentMonth(marginDetailsList.stream()
                          .filter(marginDetails -> marginDetails.getTotalSpendDetails() != null)
                          .map(marginDetails -> marginDetails.getTotalSpendDetails().getCurrentMonth())
                          .collect(Collectors.toList())
                          .stream()
                          .mapToDouble(Double::doubleValue)
                          .sum())
        .lastMonth(marginDetailsList.stream()
                       .filter(marginDetails -> marginDetails.getTotalSpendDetails() != null)
                       .map(marginDetails -> marginDetails.getTotalSpendDetails().getLastMonth())
                       .collect(Collectors.toList())
                       .stream()
                       .mapToDouble(Double::doubleValue)
                       .sum())
        .currentQuarter(marginDetailsList.stream()
                            .filter(marginDetails -> marginDetails.getTotalSpendDetails() != null)
                            .map(marginDetails -> marginDetails.getTotalSpendDetails().getCurrentQuarter())
                            .collect(Collectors.toList())
                            .stream()
                            .mapToDouble(Double::doubleValue)
                            .sum())
        .lastQuarter(marginDetailsList.stream()
                         .filter(marginDetails -> marginDetails.getTotalSpendDetails() != null)
                         .map(marginDetails -> marginDetails.getTotalSpendDetails().getLastQuarter())
                         .collect(Collectors.toList())
                         .stream()
                         .mapToDouble(Double::doubleValue)
                         .sum())
        .build();
  }

  private long getStartOfCurrentMonth() {
    Calendar startOfMonthCalendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
    startOfMonthCalendar.set(Calendar.DAY_OF_MONTH, 1);
    startOfMonthCalendar.set(Calendar.HOUR_OF_DAY, 0);
    startOfMonthCalendar.set(Calendar.MINUTE, 0);
    startOfMonthCalendar.set(Calendar.SECOND, 0);
    startOfMonthCalendar.set(Calendar.MILLISECOND, 0);
    return startOfMonthCalendar.getTimeInMillis();
  }

  private long getQuarterStartThreshold() {
    Calendar startOfMonthCalendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
    startOfMonthCalendar.set(Calendar.MONTH, -2);
    startOfMonthCalendar.set(Calendar.DAY_OF_MONTH, 1);
    startOfMonthCalendar.set(Calendar.HOUR_OF_DAY, 0);
    startOfMonthCalendar.set(Calendar.MINUTE, 0);
    startOfMonthCalendar.set(Calendar.SECOND, 0);
    startOfMonthCalendar.set(Calendar.MILLISECOND, 0);
    return startOfMonthCalendar.getTimeInMillis();
  }
}