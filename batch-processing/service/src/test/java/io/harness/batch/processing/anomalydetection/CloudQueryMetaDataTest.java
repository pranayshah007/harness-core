/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.anomalydetection;

import static io.harness.rule.OwnerRule.SANDESH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ccm.billing.graphql.CloudBillingAggregate;
import io.harness.ccm.billing.graphql.CloudBillingFilter;
import io.harness.ccm.billing.graphql.CloudBillingGroupBy;
import io.harness.ccm.billing.graphql.CloudBillingSortCriteria;
import io.harness.ccm.billing.graphql.CloudBillingTimeFilter;
import io.harness.ccm.billing.graphql.CloudEntityGroupBy;
import io.harness.ccm.billing.graphql.CloudSortType;
import io.harness.ccm.billing.graphql.TimeTruncGroupby;
import io.harness.ccm.billing.preaggregated.PreAggregatedTableSchema;
import io.harness.rule.Owner;

import software.wings.graphql.datafetcher.billing.QLCCMAggregateOperation;
import software.wings.graphql.schema.type.aggregation.QLSortOrder;
import software.wings.graphql.schema.type.aggregation.QLTimeOperator;

import com.healthmarketscience.sqlbuilder.dbspec.basic.DbColumn;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class CloudQueryMetaDataTest extends CategoryTest {
  static String awsAccountQuery =
      "SELECT MD5(CONCAT(t0.awsUsageaccountid)) AS hashcode,SUM(t0.awsBlendedCost) AS sum_blendedCost,t0.awsUsageaccountid,t0.startTime FROM `<Project>.<DataSet>.<TableName>` t0 WHERE ((t0.awsBlendedCost IS NOT NULL) AND (t0.startTime >= '1969-12-18T00:00:00Z') AND (t0.startTime <= '1970-01-01T00:00:00Z') AND (t0.awsUsageaccountid IS NOT NULL) AND (t0.startTime IS NOT NULL) AND (TO_BASE64(MD5(CONCAT(t0.awsUsageaccountid))) IN () )) GROUP BY t0.awsUsageaccountid,t0.startTime ORDER BY awsUsageaccountid ASC,startTime ASC";
  static String awsUsageTypeQuery =
      "SELECT MD5(CONCAT(t0.awsUsageaccountid,t0.awsServicecode,t0.awsUsagetype)) AS hashcode,SUM(t0.awsBlendedCost) AS sum_blendedCost,t0.awsUsageaccountid,t0.awsServicecode,t0.awsUsagetype,t0.startTime FROM `<Project>.<DataSet>.<TableName>` t0 WHERE ((t0.awsBlendedCost IS NOT NULL) AND (t0.startTime >= '1969-12-18T00:00:00Z') AND (t0.startTime <= '1970-01-01T00:00:00Z') AND (t0.awsUsageaccountid IS NOT NULL) AND (t0.awsServicecode IS NOT NULL) AND (t0.awsUsagetype IS NOT NULL) AND (t0.startTime IS NOT NULL) AND (TO_BASE64(MD5(CONCAT(t0.awsUsageaccountid,t0.awsServicecode,t0.awsUsagetype))) IN () )) GROUP BY t0.awsUsageaccountid,t0.awsServicecode,t0.awsUsagetype,t0.startTime ORDER BY awsUsageaccountid ASC,awsServicecode ASC,awsUsagetype ASC,startTime ASC";
  List<CloudBillingFilter> filterList = new ArrayList<>();
  List<CloudBillingGroupBy> groupByList = new ArrayList<>();
  List<CloudBillingAggregate> aggregationList = new ArrayList<>();
  List<CloudBillingSortCriteria> sortCriteriaList = new ArrayList<>();
  List<DbColumn> notNullColumns = new ArrayList<>();

  String accountId;
  Instant endTime;
  Instant startTime;
  CloudQueryMetaData cloudQueryMetaData;

  @Before
  public void setup() {
    accountId = "ACCOUNT_ID";
    endTime = Instant.ofEpochMilli(0);
    startTime = endTime.minus(14, ChronoUnit.DAYS);
    cloudQueryMetaData = CloudQueryMetaData.builder()
                             .accountId(accountId)
                             .aggregationList(aggregationList)
                             .filterList(filterList)
                             .groupByList(groupByList)
                             .sortCriteriaList(sortCriteriaList)
                             .notNullColumns(notNullColumns)
                             .build();
    // filters
    CloudBillingFilter startTimeFilter = new CloudBillingFilter();
    startTimeFilter.setStartTime(CloudBillingTimeFilter.builder()
                                     .operator(QLTimeOperator.AFTER)
                                     .variable(CloudBillingFilter.BILLING_AWS_STARTTIME)
                                     .value(startTime.toEpochMilli())
                                     .build());
    filterList.add(startTimeFilter);

    CloudBillingFilter endTimeFilter = new CloudBillingFilter();
    endTimeFilter.setStartTime(CloudBillingTimeFilter.builder()
                                   .operator(QLTimeOperator.BEFORE)
                                   .variable(CloudBillingFilter.BILLING_AWS_STARTTIME)
                                   .value(endTime.toEpochMilli())
                                   .build());
    filterList.add(endTimeFilter);

    // aggeration
    aggregationList.add(CloudBillingAggregate.builder()
                            .columnName(CloudBillingAggregate.AWS_BLENDED_COST)
                            .operationType(QLCCMAggregateOperation.SUM)
                            .build());
    notNullColumns.add(PreAggregatedTableSchema.awsBlendedCost);
  }

  @Test
  @Owner(developers = SANDESH)
  @Category(UnitTests.class)
  public void shouldFormAwsAccountQuery() {
    // groupby
    CloudBillingGroupBy projectIdGroupBy = new CloudBillingGroupBy();
    projectIdGroupBy.setEntityGroupBy(CloudEntityGroupBy.awsLinkedAccount);
    groupByList.add(projectIdGroupBy);

    CloudBillingGroupBy startTime = new CloudBillingGroupBy();
    startTime.setTimeTruncGroupby(TimeTruncGroupby.builder().entity(PreAggregatedTableSchema.startTime).build());
    groupByList.add(startTime);

    // sort Critera
    sortCriteriaList.add(CloudBillingSortCriteria.builder()
                             .sortType(CloudSortType.awsLinkedAccount)
                             .sortOrder(QLSortOrder.ASCENDING)
                             .build());

    sortCriteriaList.add(
        CloudBillingSortCriteria.builder().sortType(CloudSortType.Time).sortOrder(QLSortOrder.ASCENDING).build());
    assertThat(cloudQueryMetaData.getQuery()).isEqualTo(awsAccountQuery);
  }

  @Test
  @Owner(developers = SANDESH)
  @Category(UnitTests.class)
  public void shouldFormAwsUsageQuery() {
    // groupby
    CloudBillingGroupBy awsLinkedAccountGroupBy = new CloudBillingGroupBy();
    awsLinkedAccountGroupBy.setEntityGroupBy(CloudEntityGroupBy.awsLinkedAccount);
    groupByList.add(awsLinkedAccountGroupBy);

    CloudBillingGroupBy awsServiceGroupBy = new CloudBillingGroupBy();
    awsServiceGroupBy.setEntityGroupBy(CloudEntityGroupBy.awsService);
    groupByList.add(awsServiceGroupBy);

    CloudBillingGroupBy awsUsageTypeGroupBy = new CloudBillingGroupBy();
    awsUsageTypeGroupBy.setEntityGroupBy(CloudEntityGroupBy.awsUsageType);
    groupByList.add(awsUsageTypeGroupBy);

    CloudBillingGroupBy startTime = new CloudBillingGroupBy();
    startTime.setTimeTruncGroupby(TimeTruncGroupby.builder().entity(PreAggregatedTableSchema.startTime).build());
    groupByList.add(startTime);

    // sort Critera
    sortCriteriaList.add(CloudBillingSortCriteria.builder()
                             .sortType(CloudSortType.awsLinkedAccount)
                             .sortOrder(QLSortOrder.ASCENDING)
                             .build());
    sortCriteriaList.add(
        CloudBillingSortCriteria.builder().sortType(CloudSortType.awsService).sortOrder(QLSortOrder.ASCENDING).build());

    sortCriteriaList.add(CloudBillingSortCriteria.builder()
                             .sortType(CloudSortType.awsUsageType)
                             .sortOrder(QLSortOrder.ASCENDING)
                             .build());

    sortCriteriaList.add(
        CloudBillingSortCriteria.builder().sortType(CloudSortType.Time).sortOrder(QLSortOrder.ASCENDING).build());

    assertThat(cloudQueryMetaData.getQuery()).isEqualTo(awsUsageTypeQuery);
  }
}
