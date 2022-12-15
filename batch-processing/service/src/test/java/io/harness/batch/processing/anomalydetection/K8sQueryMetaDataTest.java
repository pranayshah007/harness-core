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
import io.harness.ccm.anomaly.entities.EntityType;
import io.harness.ccm.anomaly.entities.TimeGranularity;
import io.harness.rule.Owner;

import software.wings.graphql.datafetcher.billing.BillingDataTableSchema;
import software.wings.graphql.datafetcher.billing.QLCCMAggregateOperation;
import software.wings.graphql.datafetcher.billing.QLCCMAggregationFunction;
import software.wings.graphql.schema.type.aggregation.QLIdFilter;
import software.wings.graphql.schema.type.aggregation.QLIdOperator;
import software.wings.graphql.schema.type.aggregation.QLSortOrder;
import software.wings.graphql.schema.type.aggregation.QLTimeFilter;
import software.wings.graphql.schema.type.aggregation.QLTimeOperator;
import software.wings.graphql.schema.type.aggregation.billing.QLBillingDataFilter;
import software.wings.graphql.schema.type.aggregation.billing.QLBillingSortCriteria;
import software.wings.graphql.schema.type.aggregation.billing.QLBillingSortType;
import software.wings.graphql.schema.type.aggregation.billing.QLCCMEntityGroupBy;

import com.healthmarketscience.sqlbuilder.dbspec.basic.DbColumn;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class K8sQueryMetaDataTest extends CategoryTest {
  final String clusterUrl =
      "SELECT MD5(CONCAT(t0.clusterid)) AS hashcode,SUM(t0.billingamount) AS cost,SUM(t0.billingamount) AS COST,t0.clusterid,t0.starttime,t0.clustername FROM billing_data t0 WHERE ((t0.accountid = 'ACCOUNT_ID') AND (t0.clusterid IS NOT NULL) AND (t0.starttime IS NOT NULL) AND (t0.clustername IS NOT NULL) AND (t0.starttime >= '1969-12-17T00:00:00Z') AND (t0.starttime <= '1970-01-01T00:00:00Z') AND (t0.instancetype IN ('K8S_NODE') )) GROUP BY t0.clusterid,t0.starttime,t0.clustername ORDER BY CLUSTERID ASC,STARTTIME ASC";
  final String namespaceUrl =
      "SELECT MD5(CONCAT(t0.clusterid,t0.namespace)) AS hashcode,SUM(t0.billingamount) AS cost,SUM(t0.billingamount) AS COST,t0.clusterid,t0.clustername,t0.namespace,t0.starttime FROM billing_data t0 WHERE ((t0.accountid = 'ACCOUNT_ID') AND (t0.clusterid IS NOT NULL) AND (t0.clustername IS NOT NULL) AND (t0.namespace IS NOT NULL) AND (t0.starttime IS NOT NULL) AND (t0.starttime >= '1969-12-17T00:00:00Z') AND (t0.starttime <= '1970-01-01T00:00:00Z') AND (t0.instancetype IN ('K8S_POD') )) GROUP BY t0.clusterid,t0.clustername,t0.namespace,t0.starttime ORDER BY CLUSTERID ASC,NAMESPACE ASC,STARTTIME ASC";
  K8sQueryMetaData k8sQueryMetaData;
  List<DbColumn> selectColumns;
  List<QLCCMAggregationFunction> aggregationList;
  List<QLBillingDataFilter> filtersList;
  List<QLCCMEntityGroupBy> groupByList;
  List<QLBillingSortCriteria> sortCriteria;

  String accountId;
  Instant endTime;
  BillingDataTableSchema tableSchema;

  @Before
  public void setup() {
    accountId = "ACCOUNT_ID";
    endTime = Instant.ofEpochMilli(0);
    tableSchema = new BillingDataTableSchema();

    selectColumns = new ArrayList<>();
    aggregationList = new ArrayList<>();
    filtersList = new ArrayList<>();
    groupByList = new ArrayList<>();
    sortCriteria = new ArrayList<>();

    k8sQueryMetaData = K8sQueryMetaData.builder()
                           .accountId(accountId)
                           .filtersList(filtersList)
                           .aggregationList(aggregationList)
                           .sortCriteria(sortCriteria)
                           .groupByList(groupByList)
                           .selectColumns(selectColumns)
                           .build();
  }

  @Test
  @Owner(developers = SANDESH)
  @Category(UnitTests.class)
  public void shouldFormK8sClusterQuery() {
    TimeSeriesMetaData timeSeriesMetaData = TimeSeriesMetaData.builder()
                                                .accountId(accountId)
                                                .trainStart(endTime.minus(15, ChronoUnit.DAYS))
                                                .trainEnd(endTime.minus(1, ChronoUnit.DAYS))
                                                .testStart(endTime.minus(1, ChronoUnit.DAYS))
                                                .testEnd(endTime)
                                                .timeGranularity(TimeGranularity.DAILY)
                                                .entityType(EntityType.CLUSTER)
                                                .k8sQueryMetaData(k8sQueryMetaData)
                                                .build();

    // cost aggreation
    aggregationList.add(QLCCMAggregationFunction.builder()
                            .operationType(QLCCMAggregateOperation.SUM)
                            .columnName(tableSchema.getBillingAmount().getColumnNameSQL())
                            .build());

    // filters
    // Start Time
    filtersList.add(QLBillingDataFilter.builder()
                        .startTime(QLTimeFilter.builder()
                                       .operator(QLTimeOperator.AFTER)
                                       .value(timeSeriesMetaData.getTrainStart().toEpochMilli())
                                       .build())
                        .build());

    // End Time
    filtersList.add(QLBillingDataFilter.builder()
                        .endTime(QLTimeFilter.builder()
                                     .operator(QLTimeOperator.BEFORE)
                                     .value(timeSeriesMetaData.getTestEnd().toEpochMilli())
                                     .build())
                        .build());
    List<String> instanceType = new ArrayList<>();
    instanceType.add("K8S_NODE");
    filtersList.add(
        QLBillingDataFilter.builder()
            .instanceType(
                QLIdFilter.builder().operator(QLIdOperator.EQUALS).values(instanceType.toArray(new String[0])).build())
            .build());

    // groupby
    groupByList.add(QLCCMEntityGroupBy.Cluster);
    groupByList.add(QLCCMEntityGroupBy.StartTime);
    groupByList.add(QLCCMEntityGroupBy.ClusterName);

    // sort criteria
    sortCriteria.add(
        QLBillingSortCriteria.builder().sortType(QLBillingSortType.Cluster).sortOrder(QLSortOrder.ASCENDING).build());
    sortCriteria.add(
        QLBillingSortCriteria.builder().sortType(QLBillingSortType.Time).sortOrder(QLSortOrder.ASCENDING).build());

    assertThat(k8sQueryMetaData.getQuery()).isEqualTo(clusterUrl);
  }

  @Test
  @Owner(developers = SANDESH)
  @Category(UnitTests.class)
  public void shouldFormK8sNamespaceQuery() {
    TimeSeriesMetaData timeSeriesMetaData = TimeSeriesMetaData.builder()
                                                .accountId(accountId)
                                                .trainStart(endTime.minus(15, ChronoUnit.DAYS))
                                                .trainEnd(endTime.minus(1, ChronoUnit.DAYS))
                                                .testStart(endTime.minus(1, ChronoUnit.DAYS))
                                                .testEnd(endTime)
                                                .timeGranularity(TimeGranularity.DAILY)
                                                .entityType(EntityType.NAMESPACE)
                                                .k8sQueryMetaData(k8sQueryMetaData)
                                                .build();
    // cost aggreation
    aggregationList.add(QLCCMAggregationFunction.builder()
                            .operationType(QLCCMAggregateOperation.SUM)
                            .columnName(tableSchema.getBillingAmount().getColumnNameSQL())
                            .build());

    // filters
    // Start Time
    filtersList.add(QLBillingDataFilter.builder()
                        .startTime(QLTimeFilter.builder()
                                       .operator(QLTimeOperator.AFTER)
                                       .value(timeSeriesMetaData.getTrainStart().toEpochMilli())
                                       .build())
                        .build());

    // End Time
    filtersList.add(QLBillingDataFilter.builder()
                        .endTime(QLTimeFilter.builder()
                                     .operator(QLTimeOperator.BEFORE)
                                     .value(timeSeriesMetaData.getTestEnd().toEpochMilli())
                                     .build())
                        .build());

    List<String> instanceType = new ArrayList<>();
    instanceType.add("K8S_POD");
    filtersList.add(
        QLBillingDataFilter.builder()
            .instanceType(
                QLIdFilter.builder().operator(QLIdOperator.EQUALS).values(instanceType.toArray(new String[0])).build())
            .build());

    // groupby
    groupByList.add(QLCCMEntityGroupBy.Cluster);
    groupByList.add(QLCCMEntityGroupBy.ClusterName);
    groupByList.add(QLCCMEntityGroupBy.Namespace);
    groupByList.add(QLCCMEntityGroupBy.StartTime);

    // sort criteria
    sortCriteria.add(
        QLBillingSortCriteria.builder().sortType(QLBillingSortType.Cluster).sortOrder(QLSortOrder.ASCENDING).build());
    sortCriteria.add(
        QLBillingSortCriteria.builder().sortType(QLBillingSortType.Namespace).sortOrder(QLSortOrder.ASCENDING).build());
    sortCriteria.add(
        QLBillingSortCriteria.builder().sortType(QLBillingSortType.Time).sortOrder(QLSortOrder.ASCENDING).build());

    assertThat(k8sQueryMetaData.getQuery()).isEqualTo(namespaceUrl);
  }
}
