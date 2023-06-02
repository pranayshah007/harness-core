/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.billing;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.ccm.billing.GcpBillingService;
import io.harness.ccm.billing.graphql.CloudBillingAggregate;
import io.harness.ccm.billing.graphql.CloudBillingFilter;
import io.harness.ccm.billing.graphql.CloudBillingGroupBy;

import software.wings.graphql.datafetcher.AbstractStatsDataFetcher;
import software.wings.graphql.schema.type.aggregation.QLData;
import software.wings.graphql.schema.type.aggregation.billing.QLBillingSortCriteria;
import software.wings.graphql.schema.type.aggregation.billing.QLBillingStatsInfo;
import software.wings.graphql.schema.type.aggregation.billing.QLBillingTrendStats;
import software.wings.service.intfc.ce.CeAccountExpirationChecker;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import graphql.schema.DataFetchingEnvironment;
import java.math.BigDecimal;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.math3.stat.regression.SimpleRegression;

@TargetModule(HarnessModule._375_CE_GRAPHQL)
@OwnedBy(CE)
public class GcpBillingTrendStatsDataFetcher extends AbstractStatsDataFetcher<CloudBillingAggregate, CloudBillingFilter,
    CloudBillingGroupBy, QLBillingSortCriteria> {
  @Inject GcpBillingService gcpBillingService;
  @Inject CeAccountExpirationChecker accountChecker;

  private static final String BILLING_GCP_TOTAL_COST_LABEL = "Total Cost";
  private static final String BILLING_GCP_TOTAL_COST_DESCRIPTION = "of %s - %s";

  private static final String BILLING_GCP_COST_TREND_LABEL = "Cost Trend";
  private static final String BILLING_GCP_TREND_COST_DESCRIPTION = "$%s over %s - %s";

  private static final String BILLING_GCP_FORECAST_COST_LABEL = "Forecasted total cost";
  private static final String BILLING_GCP_FORECAST_COST_DESCRIPTION = "of %s - %s";

  @Override
  protected QLData fetch(String accountId, CloudBillingAggregate aggregateFunction, List<CloudBillingFilter> filters,
      List<CloudBillingGroupBy> groupBy, List<QLBillingSortCriteria> sort,
      DataFetchingEnvironment dataFetchingEnvironment) {
    accountChecker.checkIsCeEnabled(accountId);
    Preconditions.checkArgument(!isEmpty(filters), "Missing filters.");
    // find the start date from the conditions
    Optional<CloudBillingFilter> startTimeFilter = filters.stream().filter(f -> f.getStartTime() != null).findFirst();
    if (!startTimeFilter.isPresent() || startTimeFilter.get().getStartTime() == null) {
      throw new IllegalArgumentException("Missing filters on start date.");
    }
    Date startDate = new Date(startTimeFilter.get().getStartTime().getValue().longValue());
    // find the end date from the conditions
    Optional<CloudBillingFilter> endTimeFilter = filters.stream().filter(f -> f.getEndTime() != null).findFirst();
    if (!endTimeFilter.isPresent() || endTimeFilter.get().getEndTime() == null) {
      throw new IllegalArgumentException("Missing filters on end date.");
    }
    Date endDate = new Date(endTimeFilter.get().getEndTime().getValue().longValue());

    BigDecimal totalCost = gcpBillingService.getTotalCost(
        filters.stream().map(CloudBillingFilter::toCondition).collect(Collectors.toList()));

    SimpleRegression regression = gcpBillingService.getSimpleRegression(
        filters.stream().map(CloudBillingFilter::toCondition).collect(Collectors.toList()), startDate, endDate);

    BigDecimal costTrend = gcpBillingService.getCostTrend(regression, startDate, endDate);
    // get the start and end date of the forecast period
    // the start date of forecast period is one day after end date
    Calendar c = Calendar.getInstance();
    c.setTime(endDate);
    c.add(Calendar.DATE, 1);
    Date forecastStartDate = c.getTime();
    c.add(Calendar.MONTH, 1);
    Date forecastEndDate = c.getTime();
    BigDecimal costForecast = gcpBillingService.getCostEstimate(regression, forecastStartDate, forecastEndDate);

    return QLBillingTrendStats.builder()
        .totalCost(QLBillingStatsInfo.builder()
                       .statsLabel(BILLING_GCP_TOTAL_COST_LABEL)
                       .statsValue(String.valueOf(totalCost))
                       .statsDescription(BILLING_GCP_TOTAL_COST_DESCRIPTION)
                       .build())
        .costTrend(QLBillingStatsInfo.builder()
                       .statsLabel(BILLING_GCP_COST_TREND_LABEL)
                       .statsValue(String.valueOf(costTrend))
                       .statsDescription(BILLING_GCP_TREND_COST_DESCRIPTION)
                       .build())
        .forecastCost(QLBillingStatsInfo.builder()
                          .statsLabel(BILLING_GCP_FORECAST_COST_LABEL)
                          .statsValue(String.valueOf(costForecast))
                          .statsDescription(BILLING_GCP_FORECAST_COST_DESCRIPTION)
                          .build())
        .build();
  }

  @Override
  protected QLData postFetch(String accountId, List<CloudBillingGroupBy> groupByList, QLData qlData) {
    return null;
  }

  @Override
  public String getEntityType() {
    return null;
  }
}
