/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.billing.preaggregated;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.billing.preaggregated.PreAggregateConstants.entityCloudProviderConst;
import static io.harness.ccm.billing.preaggregated.PreAggregateConstants.entityConstantAwsBlendedCost;
import static io.harness.ccm.billing.preaggregated.PreAggregateConstants.entityConstantAwsInstanceType;
import static io.harness.ccm.billing.preaggregated.PreAggregateConstants.entityConstantAwsLinkedAccount;
import static io.harness.ccm.billing.preaggregated.PreAggregateConstants.entityConstantAwsNoInstanceType;
import static io.harness.ccm.billing.preaggregated.PreAggregateConstants.entityConstantAwsNoLinkedAccount;
import static io.harness.ccm.billing.preaggregated.PreAggregateConstants.entityConstantAwsNoService;
import static io.harness.ccm.billing.preaggregated.PreAggregateConstants.entityConstantAwsNoUsageType;
import static io.harness.ccm.billing.preaggregated.PreAggregateConstants.entityConstantAwsService;
import static io.harness.ccm.billing.preaggregated.PreAggregateConstants.entityConstantAwsUnBlendedCost;
import static io.harness.ccm.billing.preaggregated.PreAggregateConstants.entityConstantAwsUsageType;
import static io.harness.ccm.billing.preaggregated.PreAggregateConstants.entityConstantGcpCost;
import static io.harness.ccm.billing.preaggregated.PreAggregateConstants.entityConstantGcpNoProduct;
import static io.harness.ccm.billing.preaggregated.PreAggregateConstants.entityConstantGcpNoProjectId;
import static io.harness.ccm.billing.preaggregated.PreAggregateConstants.entityConstantGcpNoSku;
import static io.harness.ccm.billing.preaggregated.PreAggregateConstants.entityConstantGcpNoSkuId;
import static io.harness.ccm.billing.preaggregated.PreAggregateConstants.entityConstantGcpProduct;
import static io.harness.ccm.billing.preaggregated.PreAggregateConstants.entityConstantGcpProjectId;
import static io.harness.ccm.billing.preaggregated.PreAggregateConstants.entityConstantGcpSku;
import static io.harness.ccm.billing.preaggregated.PreAggregateConstants.entityConstantGcpSkuId;
import static io.harness.ccm.billing.preaggregated.PreAggregateConstants.entityConstantNoRegion;
import static io.harness.ccm.billing.preaggregated.PreAggregateConstants.entityConstantRegion;
import static io.harness.ccm.billing.preaggregated.PreAggregateConstants.entityNoCloudProviderConst;
import static io.harness.ccm.billing.preaggregated.PreAggregateConstants.maxPreAggStartTimeConstant;
import static io.harness.ccm.billing.preaggregated.PreAggregateConstants.minPreAggStartTimeConstant;
import static io.harness.rule.OwnerRule.ROHIT;

import static com.google.cloud.bigquery.FieldValue.Attribute.PRIMITIVE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.ccm.billing.graphql.CloudBillingFilter;
import io.harness.ccm.billing.graphql.CloudBillingIdFilter;
import io.harness.ccm.billing.graphql.CloudBillingTimeFilter;
import io.harness.ccm.billing.preaggregated.PreAggregatedCostData.PreAggregatedCostDataBuilder;
import io.harness.rule.Owner;

import software.wings.graphql.datafetcher.billing.BillingDataHelper;
import software.wings.graphql.schema.type.aggregation.QLIdOperator;
import software.wings.graphql.schema.type.aggregation.QLTimeOperator;
import software.wings.graphql.schema.type.aggregation.billing.QLBillingStatsInfo;

import com.google.cloud.Timestamp;
import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.FieldList;
import com.google.cloud.bigquery.FieldValue;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.StandardSQLTypeName;
import com.google.cloud.bigquery.TableResult;
import com.healthmarketscience.sqlbuilder.BinaryCondition;
import com.healthmarketscience.sqlbuilder.Condition;
import com.healthmarketscience.sqlbuilder.FunctionCall;
import com.healthmarketscience.sqlbuilder.SelectQuery;
import com.healthmarketscience.sqlbuilder.SqlObject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
public class PreAggregatedBillingDataHelperTest extends CategoryTest {
  @Mock FieldValueList row;
  @Mock TableResult tableResult;
  @Mock BillingDataHelper billingDataHelper;
  @InjectMocks PreAggregatedBillingDataHelper dataHelper;
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  private static Calendar calendar;
  List<Condition> conditions = new ArrayList<>();
  List<SqlObject> aggregates = new ArrayList<>();
  private String ENTRY_POINT = "entryPoint";
  private static final String CLOUD_PROVIDER = "AWS";
  private static final String BLENDED_COST_LABEL = "blended Cost";
  private static final String AWS_ACCOUNT_NAME = "awsAccountName";
  private static final long currentMillis = 1589328000000L;
  private static final long MIN_START_TIME = 0L;
  private static final long MAX_START_TIME = currentMillis;
  private static final Double COST = 1.4433;
  private static final Double UNBLENDED_COST = 2.0;
  private static final Double BLENDED_COST = 1.0;
  private static final Double GCP_COST = 3.0;
  private List<CloudBillingFilter> filters = new ArrayList<>();
  private static final Map<String, PreAggregatedCostData> idToPrevCostMap = new HashMap<>();
  private static Instant trendStartTime;
  private static PreAggregatedCostData preAggregatedCostData;

  @Before
  public void setup() {
    calendar = new GregorianCalendar(2020, Calendar.JANUARY, 1);
    Condition condition1 =
        BinaryCondition.greaterThanOrEq(PreAggregatedTableSchema.startTime, Timestamp.of(calendar.getTime()));
    Condition condition2 = BinaryCondition.equalTo(PreAggregatedTableSchema.awsServiceCode, "serviceCode");
    conditions.add(condition1);
    conditions.add(condition2);

    FunctionCall aggregateFunction = FunctionCall.sum().addColumnParams(PreAggregatedTableSchema.awsBlendedCost);
    aggregates.add(aggregateFunction);

    when(row.get(entityConstantRegion)).thenReturn(FieldValue.of(PRIMITIVE, entityConstantRegion));
    when(row.get(entityConstantAwsLinkedAccount)).thenReturn(FieldValue.of(PRIMITIVE, entityConstantAwsLinkedAccount));
    when(row.get(entityConstantAwsService)).thenReturn(FieldValue.of(PRIMITIVE, entityConstantAwsService));
    when(row.get(entityConstantAwsUsageType)).thenReturn(FieldValue.of(PRIMITIVE, entityConstantAwsUsageType));
    when(row.get(entityConstantAwsInstanceType)).thenReturn(FieldValue.of(PRIMITIVE, null));
    when(row.get(entityConstantGcpProduct)).thenReturn(FieldValue.of(PRIMITIVE, entityConstantGcpProduct));

    when(row.get(minPreAggStartTimeConstant)).thenReturn(FieldValue.of(PRIMITIVE, "0"));
    when(row.get(maxPreAggStartTimeConstant)).thenReturn(FieldValue.of(PRIMITIVE, "1586895998"));
    when(row.get(entityConstantAwsBlendedCost)).thenReturn(FieldValue.of(PRIMITIVE, "1.0"));
    when(row.get(entityConstantAwsUnBlendedCost)).thenReturn(FieldValue.of(PRIMITIVE, "2.0"));
    when(row.get(entityConstantGcpCost)).thenReturn(FieldValue.of(PRIMITIVE, "3.0"));

    trendStartTime = Instant.ofEpochSecond(1589155199);
    filters.addAll(
        Arrays.asList(getPreAggStartTimeFilter(currentMillis - 86400000), getPreAggEndTimeFilter(currentMillis)));
    preAggregatedCostData =
        PreAggregatedCostData.builder().cost(COST).maxStartTime(MIN_START_TIME).minStartTime(MAX_START_TIME).build();
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void getQuery() {
    List<Object> groupBy = Arrays.asList(PreAggregatedTableSchema.awsUsageAccountId,
        PreAggregatedTableSchema.awsServiceCode, PreAggregatedTableSchema.awsUsageType,
        PreAggregatedTableSchema.awsInstanceType, PreAggregatedTableSchema.region);
    SelectQuery selectQuery = dataHelper.getQuery(aggregates, groupBy, conditions, Collections.emptyList(), true);
    String query = selectQuery.toString();
    assertThat(query.contains(entityConstantRegion)).isTrue();
    assertThat(query.contains(entityConstantAwsLinkedAccount)).isTrue();
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void shouldProcessDataPointAndAppendToList() {
    FieldList fieldList = FieldList.of(Field.newBuilder(entityConstantRegion, StandardSQLTypeName.STRING).build(),
        Field.newBuilder(entityConstantAwsLinkedAccount, StandardSQLTypeName.STRING).build(),
        Field.newBuilder(entityConstantAwsService, StandardSQLTypeName.STRING).build(),
        Field.newBuilder(entityConstantAwsUsageType, StandardSQLTypeName.STRING).build(),
        Field.newBuilder(entityConstantAwsInstanceType, StandardSQLTypeName.STRING).build(),
        Field.newBuilder(entityConstantAwsBlendedCost, StandardSQLTypeName.FLOAT64).build(),
        Field.newBuilder(entityConstantAwsUnBlendedCost, StandardSQLTypeName.FLOAT64).build());

    when(billingDataHelper.getRoundedDoubleValue(UNBLENDED_COST)).thenReturn(UNBLENDED_COST);
    when(billingDataHelper.getRoundedDoubleValue(BLENDED_COST)).thenReturn(BLENDED_COST);
    when(billingDataHelper.getRoundedDoubleValue(GCP_COST)).thenReturn(GCP_COST);

    List<PreAggregateBillingEntityDataPoint> dataPointList = new ArrayList<>();
    dataHelper.processDataPointAndAppendToList(fieldList, row, dataPointList,
        Collections.singletonMap(entityConstantAwsLinkedAccount, AWS_ACCOUNT_NAME), idToPrevCostMap, filters,
        trendStartTime);
    assertThat(dataPointList.size()).isEqualTo(1);
    assertThat(dataPointList.get(0).getRegion()).isEqualTo(entityConstantRegion);
    assertThat(dataPointList.get(0).getAwsInstanceType()).isEqualTo(entityConstantAwsNoInstanceType);
    assertThat(dataPointList.get(0).getAwsLinkedAccount())
        .isEqualTo(AWS_ACCOUNT_NAME + " (" + entityConstantAwsLinkedAccount + ")");
    assertThat(dataPointList.get(0).getAwsUsageType()).isEqualTo(entityConstantAwsUsageType);
    assertThat(dataPointList.get(0).getAwsService()).isEqualTo(entityConstantAwsService);
    assertThat(dataPointList.get(0).getAwsUnblendedCost()).isEqualTo(UNBLENDED_COST);
    assertThat(dataPointList.get(0).getAwsBlendedCost()).isEqualTo(BLENDED_COST);
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void testGetDefaultValueMethod() {
    Field field = Field.newBuilder(entityConstantRegion, StandardSQLTypeName.STRING).build();
    String fieldConst = dataHelper.getDefaultValue(field);
    assertThat(fieldConst).isEqualTo(entityConstantNoRegion);

    field = Field.newBuilder(entityConstantAwsLinkedAccount, StandardSQLTypeName.STRING).build();
    fieldConst = dataHelper.getDefaultValue(field);
    assertThat(fieldConst).isEqualTo(entityConstantAwsNoLinkedAccount);

    field = Field.newBuilder(entityConstantAwsService, StandardSQLTypeName.STRING).build();
    fieldConst = dataHelper.getDefaultValue(field);
    assertThat(fieldConst).isEqualTo(entityConstantAwsNoService);

    field = Field.newBuilder(entityConstantAwsUsageType, StandardSQLTypeName.STRING).build();
    fieldConst = dataHelper.getDefaultValue(field);
    assertThat(fieldConst).isEqualTo(entityConstantAwsNoUsageType);

    field = Field.newBuilder(entityConstantAwsUsageType, StandardSQLTypeName.STRING).build();
    fieldConst = dataHelper.getDefaultValue(field);
    assertThat(fieldConst).isEqualTo(entityConstantAwsNoUsageType);

    field = Field.newBuilder(entityConstantGcpProjectId, StandardSQLTypeName.STRING).build();
    fieldConst = dataHelper.getDefaultValue(field);
    assertThat(fieldConst).isEqualTo(entityConstantGcpNoProjectId);

    field = Field.newBuilder(entityConstantGcpProduct, StandardSQLTypeName.STRING).build();
    fieldConst = dataHelper.getDefaultValue(field);
    assertThat(fieldConst).isEqualTo(entityConstantGcpNoProduct);

    field = Field.newBuilder(entityConstantGcpSku, StandardSQLTypeName.STRING).build();
    fieldConst = dataHelper.getDefaultValue(field);
    assertThat(fieldConst).isEqualTo(entityConstantGcpNoSku);

    field = Field.newBuilder(entityConstantGcpSkuId, StandardSQLTypeName.STRING).build();
    fieldConst = dataHelper.getDefaultValue(field);
    assertThat(fieldConst).isEqualTo(entityConstantGcpNoSkuId);

    field = Field.newBuilder(entityCloudProviderConst, StandardSQLTypeName.STRING).build();
    fieldConst = dataHelper.getDefaultValue(field);
    assertThat(fieldConst).isEqualTo(entityNoCloudProviderConst);
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void shouldProcessTrendDataAndAppendToList() {
    FieldList trendFieldList =
        FieldList.of(Field.newBuilder(maxPreAggStartTimeConstant, StandardSQLTypeName.STRING).build(),
            Field.newBuilder(minPreAggStartTimeConstant, StandardSQLTypeName.STRING).build(),
            Field.newBuilder(entityConstantAwsBlendedCost, StandardSQLTypeName.FLOAT64).build(),
            Field.newBuilder(entityConstantAwsUnBlendedCost, StandardSQLTypeName.FLOAT64).build(),
            Field.newBuilder(entityConstantGcpCost, StandardSQLTypeName.FLOAT64).build());

    PreAggregatedCostDataBuilder unBlendedCostDataBuilder = PreAggregatedCostData.builder();
    PreAggregatedCostDataBuilder blendedCostDataBuilder = PreAggregatedCostData.builder();
    PreAggregatedCostDataBuilder costDataBuilder = PreAggregatedCostData.builder();

    when(billingDataHelper.getRoundedDoubleValue(UNBLENDED_COST)).thenReturn(UNBLENDED_COST);
    when(billingDataHelper.getRoundedDoubleValue(BLENDED_COST)).thenReturn(BLENDED_COST);
    when(billingDataHelper.getRoundedDoubleValue(GCP_COST)).thenReturn(GCP_COST);

    dataHelper.processTrendDataAndAppendToList(
        trendFieldList, row, blendedCostDataBuilder, unBlendedCostDataBuilder, costDataBuilder);
    PreAggregatedCostData blendedCost = blendedCostDataBuilder.build();
    PreAggregatedCostData unBlendedCost = unBlendedCostDataBuilder.build();
    PreAggregatedCostData costData = costDataBuilder.build();

    assertThat(blendedCost.getCost()).isEqualTo(BLENDED_COST);
    assertThat(unBlendedCost.getCost()).isEqualTo(UNBLENDED_COST);
    assertThat(costData.getCost()).isEqualTo(GCP_COST);
    assertThat(blendedCost.getMinStartTime()).isEqualTo(0L);
    assertThat(unBlendedCost.getMaxStartTime()).isEqualTo(1586895998000000L);
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void getCostBillingStatsTest() {
    PreAggregatedCostData blendedCostData =
        PreAggregatedCostData.builder().cost(COST).maxStartTime(MAX_START_TIME).minStartTime(MIN_START_TIME).build();
    List<CloudBillingFilter> filters = new ArrayList<>();
    filters.addAll(Arrays.asList(getCloudProviderFilter(new String[] {CLOUD_PROVIDER}), getPreAggStartTimeFilter(0L),
        getPreAggEndTimeFilter(currentMillis)));

    when(billingDataHelper.getRoundedDoubleValue(COST)).thenReturn(1.44);
    when(billingDataHelper.isYearRequired(any(), any())).thenReturn(true);
    doCallRealMethod().when(billingDataHelper).formatNumber(anyDouble());
    when(billingDataHelper.getTotalCostFormattedDate(Instant.ofEpochMilli(0L), true)).thenReturn("01 January, 1970");
    when(billingDataHelper.getTotalCostFormattedDate(Instant.ofEpochMilli(MAX_START_TIME / 1000), true))
        .thenReturn("01 January, 2020");
    QLBillingStatsInfo stats = dataHelper.getCostBillingStats(
        blendedCostData, preAggregatedCostData, filters, BLENDED_COST_LABEL, trendStartTime);
    assertThat(stats.getStatsDescription()).isEqualTo("of 01 January, 1970 - 01 January, 2020");
    assertThat(stats.getStatsLabel()).isEqualTo("blended Cost");
    assertThat(stats.getStatsValue()).isEqualTo("$1.44");
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void processDataPrevDataAndAppendToListTest() {
    FieldList fieldList = FieldList.of(Field.newBuilder(maxPreAggStartTimeConstant, StandardSQLTypeName.STRING).build(),
        Field.newBuilder(minPreAggStartTimeConstant, StandardSQLTypeName.STRING).build(),
        Field.newBuilder(entityConstantGcpProduct, StandardSQLTypeName.FLOAT64).build(),
        Field.newBuilder(entityConstantGcpCost, StandardSQLTypeName.FLOAT64).build());

    dataHelper.processDataPrevDataAndAppendToList(fieldList, row, idToPrevCostMap);

    assertThat(idToPrevCostMap.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void PreconditionsValidationTest() {
    when(tableResult.getTotalRows()).thenReturn(10L);
    boolean preconditionsValidation = dataHelper.preconditionsValidation(tableResult, ENTRY_POINT);
    assertThat(preconditionsValidation).isFalse();
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void PreconditionsValidationNegetiveCaseTest() {
    when(tableResult.getTotalRows()).thenReturn(0L);
    boolean preconditionsValidation = dataHelper.preconditionsValidation(tableResult, ENTRY_POINT);
    assertThat(preconditionsValidation).isTrue();
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
