/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.billing.preaggregated;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.rule.OwnerRule.ROHIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.ccm.bigQuery.BigQueryService;
import io.harness.ccm.billing.graphql.CloudBillingFilter;
import io.harness.ccm.billing.graphql.CloudBillingIdFilter;
import io.harness.ccm.billing.graphql.CloudBillingTimeFilter;
import io.harness.ccm.commons.entities.billing.CECloudAccount;
import io.harness.ccm.setup.CECloudAccountDao;
import io.harness.rule.Owner;

import software.wings.graphql.datafetcher.billing.QLEntityData;
import software.wings.graphql.schema.type.aggregation.QLIdOperator;
import software.wings.graphql.schema.type.aggregation.QLTimeOperator;
import software.wings.graphql.schema.type.aggregation.billing.QLBillingStatsInfo;

import com.google.cloud.Timestamp;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.TableResult;
import com.healthmarketscience.sqlbuilder.BinaryCondition;
import com.healthmarketscience.sqlbuilder.Condition;
import com.healthmarketscience.sqlbuilder.CustomSql;
import com.healthmarketscience.sqlbuilder.SelectQuery;
import com.healthmarketscience.sqlbuilder.SqlObject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@TargetModule(HarnessModule._375_CE_GRAPHQL)
@OwnedBy(CE)
public class PreAggregateBillingServiceImplTest extends CategoryTest {
  @Mock BigQuery bigQuery;
  @Mock TableResult tableResult;
  @Mock SelectQuery selectQuery;
  @Mock BigQueryService bigQueryService;
  @Mock PreAggregatedBillingDataHelper dataHelper;
  @Mock CECloudAccountDao ceCloudAccountDao;
  @InjectMocks PreAggregateBillingServiceImpl preAggregateBillingService;
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  private static final long currentMillis = 1589328000000L;
  private static final String TABLE_NAME = "tableName";
  private static final String SERVICE_NAME = "service";
  private static final String CLOUD_PROVIDER = "AWS";
  private static final String TOTAL_COST_LABEL = "Total Cost";
  private static final String STATS_LABEL = "statsLabel";
  private static final String STATS_VALUE = "statsValue";
  private static final String STATS_DESCRIPTION = "statsDescription";
  private static final Double TREND = 2.44;
  private static final Double TOTAL_COST = 100.0;
  private static final String ACCOUNT_ID = "accountId";
  private static final String AWS_ACCOUNT_NAME = "awsAccountName";
  private static final String AWS_ACCOUNT_ID = "awsAccountId";
  private static final String ID = "id";
  private static final String NAME = "name";
  private static final String TYPE = "type";
  private static final String leftJoinTemplate = " LEFT JOIN UNNEST(%s) as %s";
  private static final String labels = "labels";
  private static final long MIN_START_TIME = 0L;
  private static final long MAX_START_TIME = currentMillis;
  private static final Double COST = 1.0;
  private static Calendar calendar1;
  private static Calendar calendar2;
  private List<Condition> conditions = new ArrayList<>();
  private List<Object> groupByObjects = new ArrayList<>();
  private List<CloudBillingFilter> filters = new ArrayList<>();
  private List<SqlObject> leftJoinList = null;

  @Before
  public void setup() throws InterruptedException {
    calendar1 = new GregorianCalendar(2020, Calendar.JANUARY, 1);
    calendar2 = new GregorianCalendar(2020, Calendar.JANUARY, 31);
    leftJoinList = Collections.singletonList(new CustomSql(String.format(leftJoinTemplate, labels, labels)));
    Condition startTimeCondition =
        BinaryCondition.greaterThanOrEq(PreAggregatedTableSchema.startTime, Timestamp.of(calendar1.getTime()));
    Condition endTimeCondition =
        BinaryCondition.lessThanOrEq(PreAggregatedTableSchema.startTime, Timestamp.of(calendar2.getTime()));
    conditions.addAll(Arrays.asList(startTimeCondition, endTimeCondition));
    Map<String, PreAggregatedCostData> idToPrevCostMap = new HashMap<>();

    when(bigQueryService.get()).thenReturn(bigQuery);
    when(bigQuery.query(any(QueryJobConfiguration.class))).thenReturn(tableResult);
    when(dataHelper.getQuery(any(), any(), any(), any(), anyBoolean())).thenReturn(selectQuery);
    when(dataHelper.getQuery(any(), any(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean()))
        .thenReturn(selectQuery);
    when(selectQuery.toString()).thenReturn(PreAggregatedTableSchema.defaultTableName);
    when(dataHelper.convertToPreAggregatesTimeSeriesData(tableResult))
        .thenReturn(PreAggregateBillingTimeSeriesStatsDTO.builder().stats(null).build());
    doCallRealMethod().when(dataHelper).getTrendFilters(filters);
    doCallRealMethod().when(dataHelper).getStartTimeFilter(anyList());
    doCallRealMethod().when(dataHelper).getEndTimeFilter(anyList());
    doCallRealMethod().when(dataHelper).getStartTimeBillingFilter(any());
    doCallRealMethod().when(dataHelper).getEndTimeBillingFilter(any());
    doCallRealMethod().when(dataHelper).filtersToConditions(anyList());

    when(ceCloudAccountDao.getByAWSAccountId(ACCOUNT_ID))
        .thenReturn(Collections.singletonList(
            CECloudAccount.builder().infraAccountId(AWS_ACCOUNT_ID).accountName(AWS_ACCOUNT_NAME).build()));
    Map<String, String> awsLinkedAccountMap = Collections.singletonMap(AWS_ACCOUNT_ID, AWS_ACCOUNT_NAME);

    when(dataHelper.convertToIdToPrevBillingData(tableResult)).thenReturn(idToPrevCostMap);
    Instant trendStartTime = Instant.ofEpochSecond(1589155199);

    when(dataHelper.convertToPreAggregatesEntityData(
             tableResult, awsLinkedAccountMap, idToPrevCostMap, filters, Instant.ofEpochSecond(1589155199)))
        .thenReturn(
            PreAggregateBillingEntityStatsDTO.builder()
                .stats(Arrays.asList(
                    PreAggregateBillingEntityDataPoint.builder().awsService(SERVICE_NAME).awsBlendedCost(COST).build()))
                .build());

    // Setup for Trend Stats Test
    PreAggregatedCostData blendedCostData =
        PreAggregatedCostData.builder().cost(COST).maxStartTime(MIN_START_TIME).minStartTime(MAX_START_TIME).build();
    filters.addAll(Arrays.asList(getCloudProviderFilter(new String[] {CLOUD_PROVIDER}),
        getPreAggStartTimeFilter(currentMillis - 86400000), getPreAggEndTimeFilter(currentMillis)));

    when(dataHelper.convertToAggregatedCostData(tableResult))
        .thenReturn(PreAggregatedCostDataStats.builder().blendedCost(blendedCostData).build());

    PreAggregateCloudOverviewDataPoint preAggregateCloudOverviewDataPoint =
        PreAggregateCloudOverviewDataPoint.builder().name(NAME).cost(TOTAL_COST).trend(TREND).build();

    when(dataHelper.convertToPreAggregatesOverview(tableResult, idToPrevCostMap, filters, trendStartTime))
        .thenReturn(PreAggregateCloudOverviewDataDTO.builder()
                        .totalCost(TOTAL_COST)
                        .data(Collections.singletonList(preAggregateCloudOverviewDataPoint))
                        .build());

    when(dataHelper.getCostBillingStats(blendedCostData, blendedCostData, filters, TOTAL_COST_LABEL, trendStartTime))
        .thenReturn(QLBillingStatsInfo.builder()
                        .statsValue(STATS_VALUE)
                        .statsDescription(STATS_DESCRIPTION)
                        .statsLabel(STATS_LABEL)
                        .build());

    Set<QLEntityData> awsRegionSet = new HashSet<>();
    QLEntityData entityData = QLEntityData.builder().id(ID).name(NAME).type(TYPE).build();
    awsRegionSet.add(entityData);

    when(dataHelper.convertToPreAggregatesFilterValue(tableResult, awsLinkedAccountMap))
        .thenReturn(PreAggregateFilterValuesDTO.builder()
                        .data(Arrays.asList(PreAggregatedFilterValuesDataPoint.builder().region(awsRegionSet).build()))
                        .build());
    groupByObjects.add(PreAggregatedTableSchema.awsUsageAccountId);
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void getPreAggregateBillingTimeSeriesStats() {
    PreAggregateBillingTimeSeriesStatsDTO stats = preAggregateBillingService.getPreAggregateBillingTimeSeriesStats(
        null, groupByObjects, null, Collections.emptyList(), TABLE_NAME, null);
    assertThat(stats.getStats()).isEqualTo(null);
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void getPreAggregateBillingTimeSeriesStatsWithLeftJoin() {
    PreAggregateBillingTimeSeriesStatsDTO stats = preAggregateBillingService.getPreAggregateBillingTimeSeriesStats(
        null, groupByObjects, null, Collections.emptyList(), TABLE_NAME, leftJoinList);
    assertThat(stats.getStats()).isEqualTo(null);
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void getPreAggregateEntitySeriesStats() {
    PreAggregateBillingEntityStatsDTO stats = preAggregateBillingService.getPreAggregateBillingEntityStats(
        ACCOUNT_ID, null, groupByObjects, null, Collections.emptyList(), TABLE_NAME, filters, null);
    assertThat(stats.getStats()).isNotNull();
    assertThat(stats.getStats().get(0).getAwsService()).isEqualTo(SERVICE_NAME);
    assertThat(stats.getStats().get(0).getAwsBlendedCost()).isEqualTo(COST);
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void getPreAggregateEntitySeriesStatsWithLeftJoin() {
    PreAggregateBillingEntityStatsDTO stats = preAggregateBillingService.getPreAggregateBillingEntityStats(
        ACCOUNT_ID, null, groupByObjects, null, Collections.emptyList(), TABLE_NAME, filters, leftJoinList);
    assertThat(stats.getStats()).isNotNull();
    assertThat(stats.getStats().get(0).getAwsService()).isEqualTo(SERVICE_NAME);
    assertThat(stats.getStats().get(0).getAwsBlendedCost()).isEqualTo(COST);
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void getPreAggregateBillingOverviewTest() {
    PreAggregateCloudOverviewDataDTO stats = preAggregateBillingService.getPreAggregateBillingOverview(
        null, groupByObjects, null, Collections.emptyList(), TABLE_NAME, filters, null);
    assertThat(stats.getData()).isNotNull();
    assertThat(stats.getTotalCost()).isEqualTo(TOTAL_COST);
    assertThat(stats.getData().get(0).getTrend()).isEqualTo(TREND);
    assertThat(stats.getData().get(0).getName()).isEqualTo(NAME);
    assertThat(stats.getData().get(0).getCost()).isEqualTo(TOTAL_COST);
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void getPreAggregateBillingOverviewNegativeCase() throws InterruptedException {
    when(bigQuery.query(any(QueryJobConfiguration.class))).thenThrow(new InterruptedException());
    PreAggregateCloudOverviewDataDTO stats = preAggregateBillingService.getPreAggregateBillingOverview(
        null, groupByObjects, null, Collections.emptyList(), TABLE_NAME, filters, null);
    assertThat(stats).isNull();
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void getPreAggregateEntitySeriesStatsNegativeCase() throws InterruptedException {
    when(bigQuery.query(any(QueryJobConfiguration.class))).thenThrow(new InterruptedException());
    PreAggregateBillingEntityStatsDTO stats = preAggregateBillingService.getPreAggregateBillingEntityStats(
        ACCOUNT_ID, null, groupByObjects, null, Collections.emptyList(), TABLE_NAME, filters, null);
    assertThat(stats).isNull();
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void getPreAggregateBillingTimeSeriesStatsNegativeCase() throws InterruptedException {
    when(bigQuery.query(any(QueryJobConfiguration.class))).thenThrow(new InterruptedException());
    PreAggregateBillingTimeSeriesStatsDTO stats = preAggregateBillingService.getPreAggregateBillingTimeSeriesStats(
        null, groupByObjects, null, Collections.emptyList(), TABLE_NAME, null);
    assertThat(stats).isNull();
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void getPreAggregateBillingTrendStats() {
    PreAggregateBillingTrendStatsDTO stats =
        preAggregateBillingService.getPreAggregateBillingTrendStats(null, conditions, TABLE_NAME, filters, null);
    assertThat(stats.getBlendedCost()).isNotNull();
    assertThat(stats.getBlendedCost().getStatsValue()).isEqualTo(STATS_VALUE);
    assertThat(stats.getBlendedCost().getStatsLabel()).isEqualTo(STATS_LABEL);
    assertThat(stats.getBlendedCost().getStatsDescription()).isEqualTo(STATS_DESCRIPTION);
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void getPreAggregateBillingTrendStatsWithLeftJoin() {
    PreAggregateBillingTrendStatsDTO stats = preAggregateBillingService.getPreAggregateBillingTrendStats(
        null, conditions, TABLE_NAME, filters, leftJoinList);
    assertThat(stats.getBlendedCost()).isNotNull();
    assertThat(stats.getBlendedCost().getStatsValue()).isEqualTo(STATS_VALUE);
    assertThat(stats.getBlendedCost().getStatsLabel()).isEqualTo(STATS_LABEL);
    assertThat(stats.getBlendedCost().getStatsDescription()).isEqualTo(STATS_DESCRIPTION);
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void getPreAggregateBillingTrendStatsNegativeCase() throws InterruptedException {
    when(bigQuery.query(any(QueryJobConfiguration.class))).thenThrow(new InterruptedException());
    PreAggregateBillingTrendStatsDTO stats =
        preAggregateBillingService.getPreAggregateBillingTrendStats(null, conditions, TABLE_NAME, filters, null);
    assertThat(stats).isEqualTo(PreAggregateBillingTrendStatsDTO.builder().build());
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void getPreAggregateFilterValueStatsTest() {
    PreAggregateFilterValuesDTO stats = preAggregateBillingService.getPreAggregateFilterValueStats(
        ACCOUNT_ID, groupByObjects, null, TABLE_NAME, null, 10, 0);
    assertThat(stats.getData().get(0)).isNotNull();
    assertThat(stats.getData().get(0).getRegion().size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void getPreAggregateFilterValueStatsTestNegativeCase() throws InterruptedException {
    when(bigQuery.query(any(QueryJobConfiguration.class))).thenThrow(new InterruptedException());
    PreAggregateFilterValuesDTO stats = preAggregateBillingService.getPreAggregateFilterValueStats(
        ACCOUNT_ID, groupByObjects, null, TABLE_NAME, null, 10, 0);
    assertThat(stats).isNull();
  }

  private CloudBillingFilter getCloudProviderFilter(String[] cloudProvider) {
    CloudBillingFilter cloudBillingFilter = new CloudBillingFilter();
    cloudBillingFilter.setCloudProvider(
        CloudBillingIdFilter.builder().operator(QLIdOperator.IN).values(cloudProvider).build());
    return cloudBillingFilter;
  }

  private CloudBillingFilter getPreAggStartTimeFilter(Long filterTime) {
    CloudBillingFilter cloudBillingFilter = new CloudBillingFilter();
    cloudBillingFilter.setPreAggregatedTableStartTime(
        CloudBillingTimeFilter.builder().operator(QLTimeOperator.AFTER).value(filterTime).build());
    return cloudBillingFilter;
  }

  private CloudBillingFilter getPreAggEndTimeFilter(Long filterTime) {
    CloudBillingFilter cloudBillingFilter = new CloudBillingFilter();
    cloudBillingFilter.setPreAggregatedTableEndTime(
        CloudBillingTimeFilter.builder().operator(QLTimeOperator.BEFORE).value(filterTime).build());
    return cloudBillingFilter;
  }
}
