/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.anomalydetection.reader.cloud;

import io.harness.batch.processing.anomalydetection.AnomalyDetectionConstants;
import io.harness.batch.processing.anomalydetection.CloudQueryMetaData;
import io.harness.batch.processing.anomalydetection.TimeSeriesMetaData;
import io.harness.batch.processing.ccm.CCMJobConstants;
import io.harness.ccm.anomaly.entities.EntityType;
import io.harness.ccm.anomaly.entities.TimeGranularity;
import io.harness.ccm.billing.graphql.CloudBillingAggregate;
import io.harness.ccm.billing.graphql.CloudBillingFilter;
import io.harness.ccm.billing.graphql.CloudBillingGroupBy;
import io.harness.ccm.billing.graphql.CloudBillingSortCriteria;
import io.harness.ccm.billing.graphql.CloudBillingTimeFilter;
import io.harness.ccm.billing.graphql.CloudEntityGroupBy;
import io.harness.ccm.billing.graphql.CloudSortType;
import io.harness.ccm.billing.graphql.TimeTruncGroupby;
import io.harness.ccm.billing.preaggregated.PreAggregatedTableSchema;

import software.wings.graphql.datafetcher.billing.QLCCMAggregateOperation;
import software.wings.graphql.schema.type.aggregation.QLSortOrder;
import software.wings.graphql.schema.type.aggregation.QLTimeOperator;

import com.healthmarketscience.sqlbuilder.dbspec.basic.DbColumn;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.batch.core.StepExecution;

public class AnomalyDetectionGcpProjectReader extends AnomalyDetectionCloudReader {
  @Override
  public void beforeStep(StepExecution stepExecution) {
    parameters = stepExecution.getJobExecution().getJobParameters();
    accountId = parameters.getString(CCMJobConstants.ACCOUNT_ID);
    Instant endTime = CCMJobConstants.getFieldValueFromJobParams(parameters, CCMJobConstants.JOB_END_DATE);

    List<CloudBillingFilter> filterList = new ArrayList<>();
    List<CloudBillingGroupBy> groupByList = new ArrayList<>();
    List<CloudBillingAggregate> aggregationList = new ArrayList<>();
    List<CloudBillingSortCriteria> sortCriteriaList = new ArrayList<>();
    List<DbColumn> notNullColumns = new ArrayList<>();

    CloudQueryMetaData queryMetaData = CloudQueryMetaData.builder()
                                           .accountId(accountId)
                                           .aggregationList(aggregationList)
                                           .filterList(filterList)
                                           .groupByList(groupByList)
                                           .sortCriteriaList(sortCriteriaList)
                                           .notNullColumns(notNullColumns)
                                           .build();

    timeSeriesMetaData = TimeSeriesMetaData.builder()
                             .accountId(accountId)
                             .trainStart(endTime.minus(AnomalyDetectionConstants.DAYS_TO_CONSIDER, ChronoUnit.DAYS))
                             .trainEnd(endTime.minus(1, ChronoUnit.DAYS))
                             .testStart(endTime.minus(1, ChronoUnit.DAYS))
                             .testEnd(endTime)
                             .entityType(EntityType.GCP_PROJECT)
                             .cloudQueryMetaData(queryMetaData)
                             .timeGranularity(TimeGranularity.DAILY)
                             .build();

    // filters
    CloudBillingFilter startTimeFilter = new CloudBillingFilter();
    startTimeFilter.setStartTime(CloudBillingTimeFilter.builder()
                                     .operator(QLTimeOperator.AFTER)
                                     .variable(CloudBillingFilter.BILLING_GCP_STARTTIME)
                                     .value(timeSeriesMetaData.getTrainStart().toEpochMilli())
                                     .build());
    filterList.add(startTimeFilter);

    CloudBillingFilter endTimeFilter = new CloudBillingFilter();
    endTimeFilter.setStartTime(CloudBillingTimeFilter.builder()
                                   .operator(QLTimeOperator.BEFORE)
                                   .variable(CloudBillingFilter.BILLING_GCP_STARTTIME)
                                   .value(timeSeriesMetaData.getTestEnd().toEpochMilli())
                                   .build());
    filterList.add(endTimeFilter);

    // groupby
    CloudBillingGroupBy projectIdGroupBy = new CloudBillingGroupBy();
    projectIdGroupBy.setEntityGroupBy(CloudEntityGroupBy.projectId);
    groupByList.add(projectIdGroupBy);

    CloudBillingGroupBy startTime = new CloudBillingGroupBy();
    startTime.setTimeTruncGroupby(TimeTruncGroupby.builder().entity(PreAggregatedTableSchema.startTime).build());
    groupByList.add(startTime);

    // aggeration
    aggregationList.add(CloudBillingAggregate.builder()
                            .columnName(CloudBillingAggregate.BILLING_GCP_COST)
                            .operationType(QLCCMAggregateOperation.SUM)
                            .build());
    notNullColumns.add(PreAggregatedTableSchema.cost);

    // sort Critera
    sortCriteriaList.add(CloudBillingSortCriteria.builder()
                             .sortType(CloudSortType.gcpProjectId)
                             .sortOrder(QLSortOrder.ASCENDING)
                             .build());

    sortCriteriaList.add(
        CloudBillingSortCriteria.builder().sortType(CloudSortType.Time).sortOrder(QLSortOrder.ASCENDING).build());

    hashCodesList = dataService.getBatchMetaData(timeSeriesMetaData);
    hashCodeIterator = hashCodesList.listIterator();
    listAnomalyDetectionTimeSeries = new ArrayList<>();
    timeSeriesIterator = listAnomalyDetectionTimeSeries.listIterator();
  }
}
