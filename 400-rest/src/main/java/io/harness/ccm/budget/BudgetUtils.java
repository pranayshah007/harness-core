/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.budget;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.budget.BudgetScopeType.APPLICATION;
import static io.harness.ccm.budget.BudgetScopeType.CLUSTER;
import static io.harness.ccm.budget.BudgetScopeType.PERSPECTIVE;
import static io.harness.ccm.views.graphql.QLCEViewTimeFilterOperator.AFTER;
import static io.harness.ccm.views.graphql.QLCEViewTimeFilterOperator.BEFORE;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EnvironmentType;
import io.harness.ccm.commons.entities.billing.Budget;
import io.harness.ccm.views.entities.ViewFieldIdentifier;
import io.harness.ccm.views.graphql.QLCEViewAggregateOperation;
import io.harness.ccm.views.graphql.QLCEViewAggregation;
import io.harness.ccm.views.graphql.QLCEViewFieldInput;
import io.harness.ccm.views.graphql.QLCEViewFilterWrapper;
import io.harness.ccm.views.graphql.QLCEViewGroupBy;
import io.harness.ccm.views.graphql.QLCEViewMetadataFilter;
import io.harness.ccm.views.graphql.QLCEViewTimeFilter;
import io.harness.ccm.views.graphql.QLCEViewTimeFilterOperator;
import io.harness.ccm.views.graphql.QLCEViewTimeGroupType;
import io.harness.ccm.views.graphql.QLCEViewTimeSeriesData;
import io.harness.ccm.views.graphql.QLCEViewTimeTruncGroupBy;
import io.harness.ccm.views.graphql.QLCEViewTrendInfo;
import io.harness.ccm.views.service.ViewsBillingService;
import io.harness.ccm.views.service.impl.ViewsBillingServiceImpl;
import io.harness.exception.InvalidRequestException;

import software.wings.beans.Environment;
import software.wings.beans.Environment.EnvironmentKeys;
import software.wings.dl.WingsPersistence;
import software.wings.graphql.datafetcher.billing.QLBillingAmountData;
import software.wings.graphql.datafetcher.billing.QLCCMAggregateOperation;
import software.wings.graphql.datafetcher.billing.QLCCMAggregationFunction;
import software.wings.graphql.datafetcher.budget.BudgetTimescaleQueryHelper;
import software.wings.graphql.schema.type.aggregation.QLIdFilter;
import software.wings.graphql.schema.type.aggregation.QLIdOperator;
import software.wings.graphql.schema.type.aggregation.QLTimeFilter;
import software.wings.graphql.schema.type.aggregation.QLTimeOperator;
import software.wings.graphql.schema.type.aggregation.billing.QLBillingDataFilter;
import software.wings.graphql.schema.type.aggregation.billing.QLCCMTimeSeriesAggregation;
import software.wings.graphql.schema.type.aggregation.billing.QLTimeGroupType;

import com.google.inject.Inject;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@TargetModule(HarnessModule._375_CE_GRAPHQL)
@OwnedBy(CE)
public class BudgetUtils {
  @Inject WingsPersistence wingsPersistence;
  @Inject BudgetTimescaleQueryHelper budgetTimescaleQueryHelper;
  @Inject ViewsBillingService viewsBillingService;
  @Inject ViewsBillingServiceImpl viewsBillingServiceImpl;
  private String DEFAULT_TIMEZONE = "GMT";
  private static final long ONE_DAY_MILLIS = 86400000;
  private static final long OBSERVATION_PERIOD = 29 * ONE_DAY_MILLIS;

  public QLCCMAggregationFunction makeBillingAmtAggregation() {
    return QLCCMAggregationFunction.builder()
        .operationType(QLCCMAggregateOperation.SUM)
        .columnName("billingamount")
        .build();
  }

  public QLCCMTimeSeriesAggregation makeStartTimeEntityGroupBy() {
    return QLCCMTimeSeriesAggregation.builder().timeGroupType(QLTimeGroupType.MONTH).build();
  }

  public double getForecastCost(Budget budget) {
    if (isPerspectiveBudget(budget)) {
      return getForecastCostForPerspectiveBudget(budget);
    }
    List<QLBillingDataFilter> filters = new ArrayList<>();
    filters.add(getBudgetScopeFilter(budget));
    addAdditionalFiltersBasedOnScope(budget, filters);
    filters.add(getStartTimeFilterForForecasting());
    filters.add(getEndOfMonthFilterForCurrentBillingCycle());
    QLBillingAmountData billingAmountData =
        budgetTimescaleQueryHelper.getBudgetCostData(budget.getAccountId(), makeBillingAmtAggregation(), filters);
    Instant endInstant = getEndInstant(filters);
    BigDecimal forecastCost = null;
    if (billingAmountData != null) {
      forecastCost = getNewForecastCost(billingAmountData, endInstant);
    }
    if (forecastCost == null) {
      return 0L;
    }
    return forecastCost.doubleValue();
  }

  public double getActualCost(Budget budget, String cloudProviderTable) {
    if (isPerspectiveBudget(budget)) {
      return getActualCostForPerspectiveBudget(budget, cloudProviderTable);
    }
    List<QLBillingDataFilter> filters = new ArrayList<>();
    filters.add(getBudgetScopeFilter(budget));
    addAdditionalFiltersBasedOnScope(budget, filters);
    filters.add(getStartOfMonthFilterForCurrentBillingCycle());
    filters.add(getEndOfMonthFilterForCurrentBillingCycle());
    QLBillingAmountData billingAmountData =
        budgetTimescaleQueryHelper.getBudgetCostData(budget.getAccountId(), makeBillingAmtAggregation(), filters);
    if (billingAmountData == null) {
      return 0;
    }
    return billingAmountData.getCost().doubleValue();
  }

  public BigDecimal getNewForecastCost(QLBillingAmountData billingAmountData, Instant endInstant) {
    if (billingAmountData == null) {
      return null;
    }
    Instant currentTime = Instant.now();
    if (currentTime.isAfter(endInstant)) {
      return null;
    }

    BigDecimal totalBillingAmount = billingAmountData.getCost();
    long actualTimeDiffMillis =
        (endInstant.plus(1, ChronoUnit.SECONDS).toEpochMilli()) - billingAmountData.getMaxStartTime();

    long billingTimeDiffMillis = ONE_DAY_MILLIS;
    if (billingAmountData.getMaxStartTime() != billingAmountData.getMinStartTime()) {
      billingTimeDiffMillis =
          billingAmountData.getMaxStartTime() - billingAmountData.getMinStartTime() + ONE_DAY_MILLIS;
    }
    if (billingTimeDiffMillis < OBSERVATION_PERIOD) {
      return null;
    }

    return totalBillingAmount.multiply(
        new BigDecimal(actualTimeDiffMillis).divide(new BigDecimal(billingTimeDiffMillis), 2, RoundingMode.HALF_UP));
  }

  public double getLastMonthCost(Budget budget, String cloudProviderTable) {
    if (isPerspectiveBudget(budget)) {
      return getLastMonthCostForPerspectiveBudget(budget, cloudProviderTable);
    }
    List<QLBillingDataFilter> filters = new ArrayList<>();
    filters.add(getBudgetScopeFilter(budget));
    addAdditionalFiltersBasedOnScope(budget, filters);
    filters.add(getTimeFilter(getStartOfMonth(true), QLTimeOperator.AFTER));
    filters.add(getTimeFilter(getStartOfMonth(false) - ONE_DAY_MILLIS, QLTimeOperator.BEFORE));
    QLBillingAmountData billingAmountData =
        budgetTimescaleQueryHelper.getBudgetCostData(budget.getAccountId(), makeBillingAmtAggregation(), filters);
    if (billingAmountData == null) {
      return 0;
    }
    return billingAmountData.getCost().doubleValue();
  }

  // Methods for adding various filters in budget total cost and forecast cost calculation

  public void addAdditionalFiltersBasedOnScope(Budget budget, List<QLBillingDataFilter> filters) {
    if (isApplicationScopePresent(budget)) {
      addEnvironmentIdFilter(budget, filters);
    } else {
      addInstanceTypeFilter(filters);
    }
  }

  private QLBillingDataFilter getStartOfMonthFilterForCurrentBillingCycle() {
    ZoneId zoneId = ZoneId.of(DEFAULT_TIMEZONE);
    LocalDate today = LocalDate.now(zoneId);
    ZonedDateTime zdtStart = today.withDayOfMonth(1).atStartOfDay(zoneId);
    long startOfMonth = zdtStart.toEpochSecond() * 1000;
    return getTimeFilter(startOfMonth, QLTimeOperator.AFTER);
  }

  private QLBillingDataFilter getEndOfMonthFilterForCurrentBillingCycle() {
    ZoneId zoneId = ZoneId.of(DEFAULT_TIMEZONE);
    LocalDate today = LocalDate.now(zoneId);
    Calendar cal = Calendar.getInstance();
    int daysInMonth = cal.getActualMaximum(Calendar.DATE);
    ZonedDateTime zdtStart = today.withDayOfMonth(daysInMonth).atStartOfDay(zoneId);
    long endTime = zdtStart.toEpochSecond() * 1000 + ONE_DAY_MILLIS - 1000;
    return getTimeFilter(endTime, QLTimeOperator.BEFORE);
  }

  private QLBillingDataFilter getStartTimeFilterForForecasting() {
    long startTime = getStartOfCurrentDay() - 30 * ONE_DAY_MILLIS;
    return getTimeFilter(startTime, QLTimeOperator.AFTER);
  }

  private boolean isApplicationScopePresent(Budget budget) {
    if (budget != null && budget.getScope().getBudgetScopeType().equals(APPLICATION)) {
      return true;
    }
    return false;
  }

  private void addInstanceTypeFilter(List<QLBillingDataFilter> filters) {
    String[] instanceTypeValues = {"ECS_TASK_FARGATE", "ECS_CONTAINER_INSTANCE", "K8S_NODE", "K8S_POD_FARGATE"};
    filters.add(QLBillingDataFilter.builder()
                    .instanceType(QLIdFilter.builder().operator(QLIdOperator.IN).values(instanceTypeValues).build())
                    .build());
  }

  private void addEnvironmentIdFilter(Budget budget, List<QLBillingDataFilter> filters) {
    ApplicationBudgetScope scope = (ApplicationBudgetScope) budget.getScope();
    if (!scope.getEnvironmentType().toString().equals("ALL")) {
      String[] appIds = scope.getApplicationIds();
      String env = scope.getEnvironmentType().toString();
      EnvironmentType environmentType;
      if (env.equals("NON_PROD")) {
        environmentType = EnvironmentType.NON_PROD;
      } else {
        environmentType = EnvironmentType.PROD;
      }
      List<String> envIds = getEnvIdsByAppsAndType(Arrays.asList(appIds), environmentType);
      filters.add(
          QLBillingDataFilter.builder()
              .environment(QLIdFilter.builder().operator(QLIdOperator.IN).values(envIds.toArray(new String[0])).build())
              .build());
    }
  }

  public List<String> getEnvIdsByAppsAndType(List<String> appIds, EnvironmentType environmentType) {
    List<Environment> environments = wingsPersistence.createQuery(Environment.class)
                                         .field(EnvironmentKeys.appId)
                                         .in(appIds)
                                         .filter(EnvironmentKeys.environmentType, environmentType)
                                         .asList();
    return environments.stream().map(Environment::getUuid).collect(Collectors.toList());
  }

  // Utility methods related to start time and end time

  private Instant getEndInstant(List<QLBillingDataFilter> filters) {
    return Instant.ofEpochMilli(getEndTimeFilter(filters).getValue().longValue());
  }

  private QLTimeFilter getEndTimeFilter(List<QLBillingDataFilter> filters) {
    Optional<QLBillingDataFilter> endTimeDataFilter =
        filters.stream().filter(qlBillingDataFilter -> qlBillingDataFilter.getEndTime() != null).findFirst();
    if (endTimeDataFilter.isPresent()) {
      return endTimeDataFilter.get().getEndTime();
    } else {
      endTimeDataFilter = filters.stream()
                              .filter(qlBillingDataFilter
                                  -> qlBillingDataFilter.getStartTime() != null
                                      && qlBillingDataFilter.getStartTime().getOperator() == QLTimeOperator.BEFORE)
                              .findFirst();
      if (endTimeDataFilter.isPresent()) {
        return endTimeDataFilter.get().getStartTime();
      } else {
        throw new InvalidRequestException("End time cannot be null");
      }
    }
  }

  public boolean isStartOfMonth() {
    long startOfDay = getStartOfCurrentDay();
    ZoneId zoneId = ZoneId.of(DEFAULT_TIMEZONE);
    LocalDate today = LocalDate.now(zoneId);
    ZonedDateTime zdtStart = today.withDayOfMonth(1).atStartOfDay(zoneId);
    long startOfMonth = zdtStart.toEpochSecond() * 1000;
    return startOfMonth == startOfDay;
  }

  private long getStartOfCurrentDay() {
    ZoneId zoneId = ZoneId.of(DEFAULT_TIMEZONE);
    LocalDate today = LocalDate.now(zoneId);
    ZonedDateTime zdtStart = today.atStartOfDay(zoneId);
    return zdtStart.toEpochSecond() * 1000;
  }

  private long getStartOfMonth(boolean prevMonth) {
    Calendar c = Calendar.getInstance();
    c.setTimeZone(TimeZone.getTimeZone(DEFAULT_TIMEZONE));
    c.set(Calendar.DAY_OF_MONTH, 1);
    c.set(Calendar.HOUR_OF_DAY, 0);
    c.set(Calendar.MINUTE, 0);
    c.set(Calendar.SECOND, 0);
    c.set(Calendar.MILLISECOND, 0);
    if (prevMonth) {
      c.add(Calendar.MONTH, -1);
    }
    return c.getTimeInMillis();
  }

  private QLBillingDataFilter getTimeFilter(long timeStamp, QLTimeOperator operator) {
    return QLBillingDataFilter.builder()
        .startTime(QLTimeFilter.builder().operator(operator).value(timeStamp).build())
        .build();
  }

  // Method for checking if alert was sent in this month

  public boolean isAlertSentInCurrentMonth(long crossedAt) {
    ZoneId zoneId = ZoneId.of(DEFAULT_TIMEZONE);
    LocalDate today = LocalDate.now(zoneId);
    ZonedDateTime zdtStart = today.withDayOfMonth(1).atStartOfDay(zoneId);
    long startOfMonth = zdtStart.toEpochSecond() * 1000;
    long startOfDay = getStartOfCurrentDay();
    if (startOfDay == startOfMonth) {
      return true;
    }
    crossedAt -= ONE_DAY_MILLIS;
    return startOfMonth <= crossedAt;
  }

  public void updateBudgetCosts(Budget budget, String cloudProviderTable) {
    try {
      Double actualCost = getActualCost(budget, cloudProviderTable);
      Double forecastCost = actualCost + getForecastCost(budget);
      Double lastMonthCost = getLastMonthCost(budget, cloudProviderTable);

      budget.setActualCost(actualCost);
      budget.setForecastCost(forecastCost);
      budget.setLastMonthCost(lastMonthCost);
    } catch (Exception e) {
      log.error("Error occurred while updating costs of budget: {}, Exception : {}", budget.getUuid(), e);
    }
  }

  public boolean isPerspectiveBudget(Budget budget) {
    return budget.getScope().getBudgetScopeType().equals(PERSPECTIVE);
  }

  public QLCEViewFilterWrapper getPerspectiveTimeFilter(long timestamp, QLCEViewTimeFilterOperator operator) {
    return QLCEViewFilterWrapper.builder()
        .timeFilter(QLCEViewTimeFilter.builder()
                        .field(QLCEViewFieldInput.builder()
                                   .fieldId("startTime")
                                   .fieldName("startTime")
                                   .identifier(ViewFieldIdentifier.COMMON)
                                   .identifierName(ViewFieldIdentifier.COMMON.getDisplayName())
                                   .build())
                        .operator(operator)
                        .value(timestamp)
                        .build())
        .build();
  }

  public QLCEViewFilterWrapper getViewFilter(String viewId) {
    return QLCEViewFilterWrapper.builder()
        .viewMetadataFilter(QLCEViewMetadataFilter.builder().viewId(viewId).isPreview(false).build())
        .build();
  }

  public List<QLCEViewAggregation> getPerspectiveTotalCostAggregation() {
    return Collections.singletonList(
        QLCEViewAggregation.builder().columnName("cost").operationType(QLCEViewAggregateOperation.SUM).build());
  }

  public List<QLCEViewTimeSeriesData> getPerspectiveBudgetMonthlyCostData(
      String viewId, String accountId, long startTime) {
    List<QLCEViewAggregation> aggregationFunction = getPerspectiveTotalCostAggregation();
    List<QLCEViewGroupBy> groupBy = new ArrayList<>();
    groupBy.add(
        QLCEViewGroupBy.builder()
            .timeTruncGroupBy(QLCEViewTimeTruncGroupBy.builder().resolution(QLCEViewTimeGroupType.MONTH).build())
            .build());
    List<QLCEViewFilterWrapper> filters = new ArrayList<>();
    filters.add(getViewFilter(viewId));
    filters.add(getPerspectiveTimeFilter(startTime, AFTER));
    return viewsBillingServiceImpl.convertToQLViewTimeSeriesData(
        viewsBillingService.getTimeSeriesStats(
            accountId, filters, groupBy, aggregationFunction, Collections.emptyList()),
        accountId, groupBy);
  }

  private double getActualCostForPerspectiveBudget(Budget budget, String cloudProviderTable) {
    String viewId = budget.getScope().getEntityIds().get(0);
    long startTime = getStartOfMonthFilterForCurrentBillingCycle().getStartTime().getValue().longValue();
    long endTime = getEndOfMonthFilterForCurrentBillingCycle().getStartTime().getValue().longValue();
    List<QLCEViewFilterWrapper> filters = new ArrayList<>();
    filters.add(getViewFilter(viewId));
    filters.add(getPerspectiveTimeFilter(startTime, AFTER));
    filters.add(getPerspectiveTimeFilter(endTime, BEFORE));
    return getCostForPerspectiveBudget(budget, filters);
  }

  private double getLastMonthCostForPerspectiveBudget(Budget budget, String cloudProviderTable) {
    String viewId = budget.getScope().getEntityIds().get(0);
    long startTime = getStartOfMonth(true);
    long endTime = getStartOfMonth(false) - ONE_DAY_MILLIS;
    List<QLCEViewFilterWrapper> filters = new ArrayList<>();
    filters.add(getViewFilter(viewId));
    filters.add(getPerspectiveTimeFilter(startTime, AFTER));
    filters.add(getPerspectiveTimeFilter(endTime, BEFORE));
    return getCostForPerspectiveBudget(budget, filters);
  }

  private double getCostForPerspectiveBudget(Budget budget, List<QLCEViewFilterWrapper> filters) {
    QLCEViewTrendInfo trendData = viewsBillingService.getTrendStatsData(filters, getPerspectiveTotalCostAggregation());
    return trendData.getValue().doubleValue();
  }

  private double getForecastCostForPerspectiveBudget(Budget budget) {
    String viewId = budget.getScope().getEntityIds().get(0);
    long startTime = getStartTimeFilterForForecasting().getStartTime().getValue().longValue();
    long endTime = getEndOfMonthFilterForCurrentBillingCycle().getStartTime().getValue().longValue();
    log.info("Start time for forecast cost: {}", startTime);
    log.info("End time for forecast cost: {}", endTime);
    List<QLCEViewFilterWrapper> filters = new ArrayList<>();
    filters.add(getViewFilter(viewId));
    filters.add(getPerspectiveTimeFilter(startTime, AFTER));
    filters.add(getPerspectiveTimeFilter(endTime, BEFORE));

    QLCEViewTrendInfo trendData = viewsBillingService.getTrendStatsData(filters, getPerspectiveTotalCostAggregation());
    Instant endInstant = Instant.ofEpochMilli(endTime);
    QLBillingAmountData billingAmountData = QLBillingAmountData.builder()
                                                .cost(BigDecimal.valueOf(trendData.getValue().doubleValue()))
                                                .maxStartTime(getStartOfCurrentDay() - ONE_DAY_MILLIS)
                                                .minStartTime(startTime)
                                                .build();
    log.info("Billing data: {}", billingAmountData);
    BigDecimal forecastCost = null;
    if (billingAmountData != null) {
      forecastCost = getNewForecastCost(billingAmountData, endInstant);
    }
    if (forecastCost == null) {
      return 0L;
    }
    return forecastCost.doubleValue();
  }

  public boolean isBudgetBasedOnGivenView(Budget budget, String viewId) {
    if (isPerspectiveBudget(budget)) {
      return budget.getScope().getEntityIds().get(0).equals(viewId);
    }
    return false;
  }

  public QLBillingDataFilter getBudgetScopeFilter(Budget budget) {
    BudgetScope scope = budget.getScope();
    switch (scope.getBudgetScopeType()) {
      case APPLICATION:
        return QLBillingDataFilter.builder()
            .application(QLIdFilter.builder()
                             .operator(QLIdOperator.IN)
                             .values(scope.getEntityIds().toArray(new String[0]))
                             .build())
            .build();
      case CLUSTER:
        return QLBillingDataFilter.builder()
            .cluster(QLIdFilter.builder()
                         .operator(QLIdOperator.IN)
                         .values(scope.getEntityIds().toArray(new String[0]))
                         .build())
            .build();
      case PERSPECTIVE:
        return QLBillingDataFilter.builder()
            .view(QLIdFilter.builder()
                      .operator(QLIdOperator.IN)
                      .values(scope.getEntityIds().toArray(new String[0]))
                      .build())
            .build();
      default:
        return null;
    }
  }

  public AlertThreshold[] getSortedAlertThresholds(AlertThresholdBase costType, AlertThreshold[] alertThresholds) {
    List<AlertThreshold> alerts = new ArrayList<>();
    for (AlertThreshold alertThreshold : alertThresholds) {
      if (alertThreshold.getBasedOn() == costType) {
        alerts.add(alertThreshold);
      }
    }
    alerts.sort(Comparator.comparing(AlertThreshold::getPercentage).reversed());
    return alerts.toArray(new AlertThreshold[0]);
  }
}
