/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.billing;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.rule.OwnerRule.HANTANG;
import static io.harness.rule.OwnerRule.ROHIT;

import static com.google.cloud.bigquery.StandardSQLTypeName.FLOAT64;
import static com.google.cloud.bigquery.StandardSQLTypeName.STRING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Percentage.withPercentage;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.ccm.bigQuery.BigQueryService;
import io.harness.rule.Owner;

import com.google.cloud.Timestamp;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.EmptyTableResult;
import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.Schema;
import com.google.cloud.bigquery.StandardSQLTypeName;
import com.google.cloud.bigquery.TableResult;
import com.healthmarketscience.sqlbuilder.BinaryCondition;
import com.healthmarketscience.sqlbuilder.Condition;
import com.healthmarketscience.sqlbuilder.FunctionCall;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.List;
import org.apache.commons.math3.stat.regression.SimpleRegression;
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
public class GcpBillingServiceImplTest extends CategoryTest {
  private SimpleRegression regression;
  @Mock TableResult tableResult;
  @Mock BigQuery bigQuery;
  @Mock BigQueryService bigQueryService;
  @InjectMocks GcpBillingServiceImpl gcpBillingService;
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  private static Calendar calendar1;
  private static Calendar calendar2;
  List<Condition> conditions = new ArrayList<>();

  @Before
  public void setUp() throws InterruptedException {
    calendar1 = new GregorianCalendar(2020, Calendar.JANUARY, 1);
    calendar2 = new GregorianCalendar(2020, Calendar.JANUARY, 31);
    Condition condition1 =
        BinaryCondition.greaterThanOrEq(RawBillingTableSchema.startTime, Timestamp.of(calendar1.getTime()));
    Condition condition2 =
        BinaryCondition.greaterThanOrEq(RawBillingTableSchema.endTime, Timestamp.of(calendar2.getTime()));
    Condition condition3 = BinaryCondition.equalTo(RawBillingTableSchema.gcpProjectId, "projectId");
    conditions.add(condition1);
    conditions.add(condition2);
    conditions.add(condition3);

    regression = new SimpleRegression();
    double[][] observations = {{10000, 1}, {10001, 2}, {10002, 3}};
    regression.addData(observations);

    when(bigQueryService.get()).thenReturn(bigQuery);
    when(bigQuery.query(any(QueryJobConfiguration.class))).thenReturn(tableResult);
  }

  @Test(expected = IllegalArgumentException.class)
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void shouldGetTotalCost() {
    when(gcpBillingService.getTotalCost(conditions))
        .thenThrow(new IllegalArgumentException("Unexpected result from this query."));
    // todo verify the query
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void shouldGetCostTrend() {
    BigDecimal trend = gcpBillingService.getCostTrend(regression, calendar1.getTime(), calendar2.getTime());
    assertThat(trend).isCloseTo(BigDecimal.valueOf(4), withPercentage(1));
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void shouldGetCostForecast() {
    BigDecimal forecast = gcpBillingService.getCostEstimate(regression, calendar1.getTime(), calendar2.getTime());
    assertThat(forecast).isStrictlyBetween(BigDecimal.valueOf(200000), BigDecimal.valueOf(250000));
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void shouldGetGcpBillingTimeSeriesStats() {
    List<Object> gcpBillingGroupby = Collections.EMPTY_LIST;
    FunctionCall aggregateFunction = FunctionCall.sum().addColumnParams(RawBillingTableSchema.cost);

    GcpBillingTimeSeriesStatsDTO timeSeriesStats =
        gcpBillingService.getGcpBillingTimeSeriesStats(aggregateFunction, gcpBillingGroupby, conditions);
    // todo add assertion on the query
    assertThat(timeSeriesStats).isNull();
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void shouldGetGcpBillingEntityStats() throws InterruptedException {
    tableResult = new EmptyTableResult(Schema.of(Field.newBuilder("sum_cost", (StandardSQLTypeName) FLOAT64).build(),
        Field.newBuilder("sku_description", (StandardSQLTypeName) STRING).build(),
        Field.newBuilder("sku_id", (StandardSQLTypeName) STRING).build(),
        Field.newBuilder("service_description", (StandardSQLTypeName) STRING).build()));

    when(bigQuery.query(any(QueryJobConfiguration.class))).thenReturn(tableResult);

    List<Object> gcpBillingGroupby = Arrays.asList(RawBillingTableSchema.gcpSkuId,
        RawBillingTableSchema.gcpSkuDescription, RawBillingTableSchema.gcpProduct, RawBillingTableSchema.cost);

    GcpBillingEntityStatsDTO entityStats =
        gcpBillingService.getGcpBillingEntityStats(null, gcpBillingGroupby, conditions);
    assertThat(entityStats).isNull();
  }
}
