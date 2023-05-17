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
import io.harness.ccm.graphql.query.perspectives.PerspectivesQuery;
import io.harness.ccm.graphql.utils.GraphQLToRESTHelper;
import io.harness.ccm.graphql.utils.RESTToGraphQLHelper;
import io.harness.ccm.msp.dao.MarginDetailsDao;
import io.harness.ccm.msp.entities.*;
import io.harness.ccm.views.dto.DataPoint;
import io.harness.ccm.views.dto.TimeSeriesDataPoints;
import io.harness.ccm.views.graphql.QLCEViewFieldInput;
import io.harness.ccm.views.graphql.QLCEViewFilter;
import io.harness.ccm.views.graphql.QLCEViewFilterWrapper;
import io.harness.ccm.views.graphql.QLCEViewPreferences;

import com.google.inject.Inject;
import io.leangen.graphql.execution.ResolutionEnvironment;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ManagedAccountDataServiceImpl implements ManagedAccountDataService {
  @Inject private PerspectivesQuery perspectivesQuery;
  @Inject private MarginDetailsDao marginDetailsDao;

  @Override
  public List<String> getEntityList(String managedAccountId, CCMField entity, Integer limit, Integer offset) {
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
  public ManagedAccountsOverview getTotalMarkupAndSpend(String mspAccountId, String managedAccountId) {
    MarginDetails marginDetails = marginDetailsDao.getMarginDetailsForAccount(mspAccountId, managedAccountId);
    return ManagedAccountsOverview.builder()
        .totalMarkupAmount(getTotalMarkupAmountDetails(Collections.singletonList(marginDetails)))
        .totalSpend(getTotalSpendDetails(Collections.singletonList(marginDetails)))
        .build();
  }

  @Override
  public ManagedAccountStats getManagedAccountStats(
      String mspAccountId, String managedAccountId, long startTime, long endTime) {
    final ResolutionEnvironment env = GraphQLToRESTHelper.createResolutionEnv(mspAccountId);

    return ManagedAccountStats.builder().build();
  }

  @Override
  public ManagedAccountStats getMockManagedAccountStats(
      String mspAccountId, String managedAccountId, long startTime, long endTime) {
    return ManagedAccountStats.builder()
        .totalSpendStats(AmountTrendStats.builder().currentPeriod(1369.34).trend(10.4).build())
        .totalMarkupStats(AmountTrendStats.builder().currentPeriod(254.22).trend(7.4).build())
        .build();
  }

  @Override
  public ManagedAccountTimeSeriesData getManagedAccountTimeSeriesData(
      String mspAccountId, String managedAccountId, long startTime, long endTime) {
    final ResolutionEnvironment env = GraphQLToRESTHelper.createResolutionEnv(mspAccountId);
    QLCEViewPreferences qlCEViewPreferences =
        QLCEViewPreferences.builder().includeOthers(false).includeUnallocatedCost(false).build();
    List<TimeSeriesDataPoints> totalSpendStats =
        perspectivesQuery
            .perspectiveTimeSeriesStats(RESTToGraphQLHelper.getCostAggregation(),
                RESTToGraphQLHelper.getTimeFilters(startTime, endTime),
                Collections.singletonList(RESTToGraphQLHelper.getGroupByDay()), Collections.emptyList(),
                (int) DEFAULT_LIMIT, (int) DEFAULT_OFFSET, qlCEViewPreferences, false, env)
            .getStats();
    List<TimeSeriesDataPoints> totalMarkupStats = getMockMarkupData(totalSpendStats);
    return ManagedAccountTimeSeriesData.builder()
        .totalSpendStats(totalSpendStats)
        .totalMarkupStats(totalMarkupStats)
        .build();
  }

  List<TimeSeriesDataPoints> getMockMarkupData(List<TimeSeriesDataPoints> totalSpendStats) {
    List<TimeSeriesDataPoints> totalMarkupStats = new ArrayList<>();
    totalSpendStats.forEach(dataPoint
        -> totalMarkupStats.add(TimeSeriesDataPoints.builder()
                                    .time(dataPoint.getTime())
                                    .values(getUpdatedDataPoint(dataPoint.getValues()))
                                    .build()));
    return totalMarkupStats;
  }

  List<DataPoint> getUpdatedDataPoint(List<DataPoint> dataPoints) {
    List<DataPoint> updatedDataPoints = new ArrayList<>();
    dataPoints.forEach(dataPoint
        -> updatedDataPoints.add(
            DataPoint.builder().key(dataPoint.getKey()).value(dataPoint.getValue().doubleValue() * 0.2).build()));
    return updatedDataPoints;
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
}
