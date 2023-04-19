/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.pricing.gcp.bigquery.impl;

import static io.harness.batch.processing.pricing.gcp.bigquery.BQConst.computeProductFamily;
import static io.harness.batch.processing.pricing.gcp.bigquery.BQConst.cost;
import static io.harness.batch.processing.pricing.gcp.bigquery.BQConst.effectiveCost;
import static io.harness.batch.processing.pricing.gcp.bigquery.BQConst.networkProductFamily;
import static io.harness.batch.processing.pricing.gcp.bigquery.BQConst.productFamily;
import static io.harness.batch.processing.pricing.gcp.bigquery.BQConst.resourceId;
import static io.harness.batch.processing.pricing.gcp.bigquery.BQConst.serviceCode;
import static io.harness.batch.processing.pricing.gcp.bigquery.BQConst.usageType;
import static io.harness.rule.OwnerRule.HITESH;

import static com.google.cloud.bigquery.FieldValue.Attribute.PRIMITIVE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.batch.processing.config.BillingDataPipelineConfig;
import io.harness.batch.processing.pricing.gcp.bigquery.BigQueryHelperServiceImpl;
import io.harness.batch.processing.pricing.vmpricing.VMInstanceBillingData;
import io.harness.category.element.UnitTests;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.FieldList;
import com.google.cloud.bigquery.FieldValue;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.Schema;
import com.google.cloud.bigquery.StandardSQLTypeName;
import com.google.cloud.bigquery.TableResult;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

@Slf4j
@RunWith(MockitoJUnitRunner.class)
public class BigQueryHelperServiceImplTest extends CategoryTest {
  @InjectMocks @Spy private BigQueryHelperServiceImpl bigQueryHelperService;
  @Mock private BatchMainConfig mainConfig;
  @Mock BigQuery bigQuery;
  @Mock FieldValueList row;
  @Mock TableResult tableResult;
  @Mock FeatureFlagService featureFlagService;

  private final String DATA_SET_ID = "dataSetId";
  private final String RESOURCE_ID = "resourceId1";
  private final String GCP_PROJECTID = "gcpProjectId";
  private final String networkCost = "10.0";
  private final String computeCost = "20.0";
  private final String cpuCost = "12.0";
  private final String memoryCost = "14.0";
  private final String cpuCostDecimal = "3.5";
  private final String memoryCostDecimal = "4.5";
  private final String effCost = "30.0";
  private final Instant NOW = Instant.now();
  private final Instant START_TIME = NOW.minus(1, ChronoUnit.HOURS);
  private final Instant END_TIME = NOW;

  private final String ACCOUNT_ID = "AAAAAS6BRkSPKdIE5F";

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testGetAwsEC2BillingData() throws InterruptedException {
    BillingDataPipelineConfig billingDataPipelineConfig =
        BillingDataPipelineConfig.builder().gcpProjectId(GCP_PROJECTID).build();

    when(mainConfig.getBillingDataPipelineConfig()).thenReturn(billingDataPipelineConfig);
    doReturn(bigQuery).when(bigQueryHelperService).getBigQueryService();

    FieldList fieldList = FieldList.of(Field.newBuilder(resourceId, StandardSQLTypeName.STRING).build(),
        Field.newBuilder(serviceCode, StandardSQLTypeName.STRING).build(),
        Field.newBuilder(productFamily, StandardSQLTypeName.STRING).build(),
        Field.newBuilder(cost, StandardSQLTypeName.FLOAT64).build(),
        Field.newBuilder(effectiveCost, StandardSQLTypeName.FLOAT64).build());
    List<FieldValue> fieldValues = new ArrayList<>();
    fieldValues.add(FieldValue.of(PRIMITIVE, RESOURCE_ID));
    fieldValues.add(FieldValue.of(PRIMITIVE, serviceCode));
    fieldValues.add(FieldValue.of(PRIMITIVE, networkProductFamily));
    fieldValues.add(FieldValue.of(PRIMITIVE, networkCost));
    fieldValues.add(FieldValue.of(PRIMITIVE, null));
    FieldValueList valueList = FieldValueList.of(fieldValues, fieldList);
    FieldValueList fieldValueList = FieldValueList.of(valueList, fieldList);
    List<FieldValue> fieldValuesCompute = new ArrayList<>();
    fieldValuesCompute.add(FieldValue.of(PRIMITIVE, RESOURCE_ID));
    fieldValuesCompute.add(FieldValue.of(PRIMITIVE, serviceCode));
    fieldValuesCompute.add(FieldValue.of(PRIMITIVE, computeProductFamily));
    fieldValuesCompute.add(FieldValue.of(PRIMITIVE, computeCost));
    fieldValuesCompute.add(FieldValue.of(PRIMITIVE, null));
    FieldValueList valueListCompute = FieldValueList.of(fieldValuesCompute, fieldList);
    FieldValueList fieldValueListCompute = FieldValueList.of(valueListCompute, fieldList);
    Iterable<FieldValueList> fieldValueListIterator = Arrays.asList(fieldValueList, fieldValueListCompute);
    doReturn(fieldList).when(bigQueryHelperService).getFieldList(any());
    doReturn(fieldValueListIterator).when(bigQueryHelperService).getFieldValueLists(any());
    when(tableResult.getSchema()).thenReturn(Schema.of(fieldList));

    List<String> resourceIds = Collections.singletonList(RESOURCE_ID);
    Map<String, VMInstanceBillingData> resourceBillingData = new HashMap<>();
    resourceBillingData.put(RESOURCE_ID,
        VMInstanceBillingData.builder().resourceId(RESOURCE_ID).networkCost(10.0).computeCost(20.0).build());
    Map<String, VMInstanceBillingData> awsEC2BillingData =
        bigQueryHelperService.getAwsEC2BillingData(resourceIds, START_TIME, END_TIME, DATA_SET_ID, ACCOUNT_ID);
    assertThat(awsEC2BillingData).isEqualTo(resourceBillingData);
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testGetAwsEC2BillingDataForRI() throws InterruptedException {
    BillingDataPipelineConfig billingDataPipelineConfig =
        BillingDataPipelineConfig.builder().gcpProjectId(GCP_PROJECTID).build();

    when(mainConfig.getBillingDataPipelineConfig()).thenReturn(billingDataPipelineConfig);
    doReturn(bigQuery).when(bigQueryHelperService).getBigQueryService();

    FieldList fieldList = FieldList.of(Field.newBuilder(resourceId, StandardSQLTypeName.STRING).build(),
        Field.newBuilder(serviceCode, StandardSQLTypeName.STRING).build(),
        Field.newBuilder(productFamily, StandardSQLTypeName.STRING).build(),
        Field.newBuilder(cost, StandardSQLTypeName.FLOAT64).build(),
        Field.newBuilder(effectiveCost, StandardSQLTypeName.FLOAT64).build());
    List<FieldValue> fieldValues = new ArrayList<>();
    fieldValues.add(FieldValue.of(PRIMITIVE, RESOURCE_ID));
    fieldValues.add(FieldValue.of(PRIMITIVE, serviceCode));
    fieldValues.add(FieldValue.of(PRIMITIVE, networkProductFamily));
    fieldValues.add(FieldValue.of(PRIMITIVE, networkCost));
    fieldValues.add(FieldValue.of(PRIMITIVE, null));
    FieldValueList valueList = FieldValueList.of(fieldValues, fieldList);
    FieldValueList fieldValueList = FieldValueList.of(valueList, fieldList);
    List<FieldValue> fieldValuesCompute = new ArrayList<>();
    fieldValuesCompute.add(FieldValue.of(PRIMITIVE, RESOURCE_ID));
    fieldValuesCompute.add(FieldValue.of(PRIMITIVE, serviceCode));
    fieldValuesCompute.add(FieldValue.of(PRIMITIVE, computeProductFamily));
    fieldValuesCompute.add(FieldValue.of(PRIMITIVE, computeCost));
    fieldValuesCompute.add(FieldValue.of(PRIMITIVE, effCost));
    FieldValueList valueListCompute = FieldValueList.of(fieldValuesCompute, fieldList);
    FieldValueList fieldValueListCompute = FieldValueList.of(valueListCompute, fieldList);
    Iterable<FieldValueList> fieldValueListIterator = Arrays.asList(fieldValueList, fieldValueListCompute);
    doReturn(fieldList).when(bigQueryHelperService).getFieldList(any());
    doReturn(fieldValueListIterator).when(bigQueryHelperService).getFieldValueLists(any());
    when(tableResult.getSchema()).thenReturn(Schema.of(fieldList));

    List<String> resourceIds = Collections.singletonList(RESOURCE_ID);
    Map<String, VMInstanceBillingData> resourceBillingData = new HashMap<>();
    resourceBillingData.put(RESOURCE_ID,
        VMInstanceBillingData.builder().resourceId(RESOURCE_ID).networkCost(10.0).computeCost(30.0).build());
    Map<String, VMInstanceBillingData> awsEC2BillingData =
        bigQueryHelperService.getAwsEC2BillingData(resourceIds, START_TIME, END_TIME, DATA_SET_ID, ACCOUNT_ID);
    assertThat(awsEC2BillingData).isEqualTo(resourceBillingData);
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testGetAwsEKSBilligData() throws InterruptedException {
    BillingDataPipelineConfig billingDataPipelineConfig =
        BillingDataPipelineConfig.builder().gcpProjectId(GCP_PROJECTID).build();

    when(mainConfig.getBillingDataPipelineConfig()).thenReturn(billingDataPipelineConfig);
    doReturn(bigQuery).when(bigQueryHelperService).getBigQueryService();

    FieldList fieldList = FieldList.of(Field.newBuilder(resourceId, StandardSQLTypeName.STRING).build(),
        Field.newBuilder(cost, StandardSQLTypeName.FLOAT64).build(),
        Field.newBuilder(usageType, StandardSQLTypeName.STRING).build());
    List<FieldValue> fieldValueCPU = new ArrayList<>();
    fieldValueCPU.add(FieldValue.of(PRIMITIVE, RESOURCE_ID));
    fieldValueCPU.add(FieldValue.of(PRIMITIVE, cpuCost));
    fieldValueCPU.add(FieldValue.of(PRIMITIVE, "USW2-Fargate-vCPU-Hours:perCPU"));
    FieldValueList valueList = FieldValueList.of(fieldValueCPU, fieldList);
    FieldValueList fieldValueList = FieldValueList.of(valueList, fieldList);
    List<FieldValue> fieldValueMemory = new ArrayList<>();
    fieldValueMemory.add(FieldValue.of(PRIMITIVE, RESOURCE_ID));
    fieldValueMemory.add(FieldValue.of(PRIMITIVE, memoryCost));
    fieldValueMemory.add(FieldValue.of(PRIMITIVE, "USW2-Fargate-GB-Hours"));
    FieldValueList valueListCompute = FieldValueList.of(fieldValueMemory, fieldList);
    FieldValueList fieldValueListCompute = FieldValueList.of(valueListCompute, fieldList);
    Iterable<FieldValueList> fieldValueListIterator = Arrays.asList(fieldValueList, fieldValueListCompute);
    doReturn(fieldList).when(bigQueryHelperService).getFieldList(any());
    doReturn(fieldValueListIterator).when(bigQueryHelperService).getFieldValueLists(any());
    when(tableResult.getSchema()).thenReturn(Schema.of(fieldList));

    List<String> resourceIds = Collections.singletonList(RESOURCE_ID);
    Map<String, VMInstanceBillingData> resourceBillingData = new HashMap<>();
    resourceBillingData.put(RESOURCE_ID,
        VMInstanceBillingData.builder()
            .resourceId(RESOURCE_ID)
            .cpuCost(12.0)
            .memoryCost(14.0)
            .computeCost(26.0)
            .build());
    Map<String, VMInstanceBillingData> awsEKSFargateBillingData =
        bigQueryHelperService.getEKSFargateBillingData(resourceIds, START_TIME, END_TIME, DATA_SET_ID);
    assertThat(awsEKSFargateBillingData).isEqualTo(resourceBillingData);
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testGetAwsEKSBilligData2() throws InterruptedException {
    BillingDataPipelineConfig billingDataPipelineConfig =
        BillingDataPipelineConfig.builder().gcpProjectId(GCP_PROJECTID).build();

    when(mainConfig.getBillingDataPipelineConfig()).thenReturn(billingDataPipelineConfig);
    doReturn(bigQuery).when(bigQueryHelperService).getBigQueryService();

    FieldList fieldList = FieldList.of(Field.newBuilder(resourceId, StandardSQLTypeName.STRING).build(),
        Field.newBuilder(cost, StandardSQLTypeName.FLOAT64).build(),
        Field.newBuilder(usageType, StandardSQLTypeName.STRING).build());
    List<FieldValue> fieldValueMemory = new ArrayList<>();
    fieldValueMemory.add(FieldValue.of(PRIMITIVE, RESOURCE_ID));
    fieldValueMemory.add(FieldValue.of(PRIMITIVE, memoryCostDecimal));
    fieldValueMemory.add(FieldValue.of(PRIMITIVE, "USW2-Fargate-GB-Hours"));
    FieldValueList valueListCompute = FieldValueList.of(fieldValueMemory, fieldList);
    FieldValueList fieldValueListCompute = FieldValueList.of(valueListCompute, fieldList);

    List<FieldValue> fieldValueCPU = new ArrayList<>();
    fieldValueCPU.add(FieldValue.of(PRIMITIVE, RESOURCE_ID));
    fieldValueCPU.add(FieldValue.of(PRIMITIVE, cpuCostDecimal));
    fieldValueCPU.add(FieldValue.of(PRIMITIVE, "USW2-Fargate-vCPU-Hours:perCPU"));
    FieldValueList valueList = FieldValueList.of(fieldValueCPU, fieldList);
    FieldValueList fieldValueList = FieldValueList.of(valueList, fieldList);

    Iterable<FieldValueList> fieldValueListIterator = Arrays.asList(fieldValueListCompute, fieldValueList);
    doReturn(fieldList).when(bigQueryHelperService).getFieldList(any());
    doReturn(fieldValueListIterator).when(bigQueryHelperService).getFieldValueLists(any());
    when(tableResult.getSchema()).thenReturn(Schema.of(fieldList));

    List<String> resourceIds = Collections.singletonList(RESOURCE_ID);
    Map<String, VMInstanceBillingData> resourceBillingData = new HashMap<>();
    resourceBillingData.put(RESOURCE_ID,
        VMInstanceBillingData.builder().resourceId(RESOURCE_ID).cpuCost(3.5).memoryCost(4.5).computeCost(8.0).build());
    Map<String, VMInstanceBillingData> awsEKSFargateBillingData =
        bigQueryHelperService.getEKSFargateBillingData(resourceIds, START_TIME, END_TIME, DATA_SET_ID);
    assertThat(awsEKSFargateBillingData).isEqualTo(resourceBillingData);
  }
}
