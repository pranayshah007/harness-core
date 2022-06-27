/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.service.impl;

import com.google.cloud.bigquery.*;
import com.google.inject.Inject;
import io.harness.ccm.bigQuery.BigQueryService;
import io.harness.ccm.commons.entities.*;
import io.harness.ccm.service.intf.BigQueryOrchestratorService;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

import static io.harness.ccm.commons.entities.BQOrchestratorCommitmentDuration.MONTHLY;

@Slf4j
public class BigQueryOrchestratorServiceImpl implements BigQueryOrchestratorService {
    @Inject
    BigQueryService bigQueryService;

    private static final String BQ_TOTAL_COST =
            "SELECT\n" +
                    "    COALESCE(SUM(( 5.0*bigquery_data_access_job_statistics.totalBilledBytes/1000000000000  ) ), 0) AS bigquery_data_access_job_statistics_total_query_cost\n" +
                    "FROM `ccm-play.BQPOC.cloudaudit_googleapis_com_data_access` AS bigquery_data_access\n" +
                    "LEFT JOIN UNNEST([bigquery_data_access.protopayload_auditlog]) as bigquery_data_access_payload\n" +
                    "LEFT JOIN UNNEST([bigquery_data_access_payload.servicedata_v1_bigquery]) AS bigquery_data_access_servicedata\n" +
                    "LEFT JOIN UNNEST([bigquery_data_access_servicedata.jobCompletedEvent]) AS bigquery_data_access_job_completed_event\n" +
                    "LEFT JOIN UNNEST([bigquery_data_access_job_completed_event.job]) AS bigquery_data_access_job\n" +
                    "LEFT JOIN UNNEST([bigquery_data_access_job.jobStatistics]) AS bigquery_data_access_job_statistics\n" +
                    "WHERE (bigquery_data_access_payload.methodName ) = 'jobservice.jobcompleted' AND (bigquery_data_access_payload.serviceName ) = 'bigquery.googleapis.com' AND (bigquery_data_access_job_completed_event.eventName ) LIKE 'query_job_completed' AND ((( bigquery_data_access_job_statistics.startTime  ) >= ((TIMESTAMP_ADD(TIMESTAMP_TRUNC(CURRENT_TIMESTAMP(), DAY, 'UTC'), INTERVAL -29 DAY))) AND ( bigquery_data_access_job_statistics.startTime  ) < ((TIMESTAMP_ADD(TIMESTAMP_ADD(TIMESTAMP_TRUNC(CURRENT_TIMESTAMP(), DAY, 'UTC'), INTERVAL -29 DAY), INTERVAL 30 DAY)))))\n" +
                    "LIMIT 500";

    private static final String BQ_TOTAL_BYTES_SCANNED = "SELECT\n" +
            "    COALESCE(SUM(( 1.0*bigquery_data_access_job_statistics.totalBilledBytes/1000000000000  ) ), 0) AS bigquery_data_access_job_statistics_total_billed_terabytes\n" +
            "FROM `ccm-play.BQPOC.cloudaudit_googleapis_com_data_access` AS bigquery_data_access\n" +
            "LEFT JOIN UNNEST([bigquery_data_access.protopayload_auditlog]) as bigquery_data_access_payload\n" +
            "LEFT JOIN UNNEST([bigquery_data_access_payload.servicedata_v1_bigquery]) AS bigquery_data_access_servicedata\n" +
            "LEFT JOIN UNNEST([bigquery_data_access_servicedata.jobCompletedEvent]) AS bigquery_data_access_job_completed_event\n" +
            "LEFT JOIN UNNEST([bigquery_data_access_job_completed_event.job]) AS bigquery_data_access_job\n" +
            "LEFT JOIN UNNEST([bigquery_data_access_job.jobStatistics]) AS bigquery_data_access_job_statistics\n" +
            "WHERE (bigquery_data_access_payload.methodName ) = 'jobservice.jobcompleted' AND (bigquery_data_access_payload.serviceName ) = 'bigquery.googleapis.com' AND (bigquery_data_access_job_completed_event.eventName ) LIKE 'query_job_completed' AND ((( bigquery_data_access_job_statistics.startTime  ) >= ((TIMESTAMP_ADD(TIMESTAMP_TRUNC(CURRENT_TIMESTAMP(), DAY, 'UTC'), INTERVAL -29 DAY))) AND ( bigquery_data_access_job_statistics.startTime  ) < ((TIMESTAMP_ADD(TIMESTAMP_ADD(TIMESTAMP_TRUNC(CURRENT_TIMESTAMP(), DAY, 'UTC'), INTERVAL -29 DAY), INTERVAL 30 DAY)))))";


    private static final String BQ_SUCCESS_QUERIES = "SELECT\n" +
            "    COUNT(*) AS bigquery_data_access_number_of_queries\n" +
            "FROM `ccm-play.BQPOC.cloudaudit_googleapis_com_data_access` AS bigquery_data_access\n" +
            "LEFT JOIN UNNEST([bigquery_data_access.protopayload_auditlog]) as bigquery_data_access_payload\n" +
            "LEFT JOIN UNNEST([bigquery_data_access_payload.servicedata_v1_bigquery]) AS bigquery_data_access_servicedata\n" +
            "LEFT JOIN UNNEST([bigquery_data_access_servicedata.jobCompletedEvent]) AS bigquery_data_access_job_completed_event\n" +
            "LEFT JOIN UNNEST([bigquery_data_access_job_completed_event.job]) AS bigquery_data_access_job\n" +
            "LEFT JOIN UNNEST([bigquery_data_access_job.jobStatistics]) AS bigquery_data_access_job_statistics\n" +
            "LEFT JOIN UNNEST([bigquery_data_access_job.jobStatus]) AS bigquery_data_access_job_status\n" +
            "WHERE (bigquery_data_access_payload.methodName ) = 'jobservice.jobcompleted' AND (bigquery_data_access_payload.serviceName ) = 'bigquery.googleapis.com' AND (bigquery_data_access_job_completed_event.eventName ) LIKE 'query_job_completed' AND ((( bigquery_data_access_job_statistics.startTime  ) >= ((TIMESTAMP_ADD(TIMESTAMP_TRUNC(CURRENT_TIMESTAMP(), DAY, 'UTC'), INTERVAL -29 DAY))) AND ( bigquery_data_access_job_statistics.startTime  ) < ((TIMESTAMP_ADD(TIMESTAMP_ADD(TIMESTAMP_TRUNC(CURRENT_TIMESTAMP(), DAY, 'UTC'), INTERVAL -29 DAY), INTERVAL 30 DAY))))) AND (NOT (bigquery_data_access_job_status.error IS NOT NULL ) OR (bigquery_data_access_job_status.error IS NOT NULL ) IS NULL)";


    private static final String BQ_FAILED_QUERIES = "SELECT\n" +
            "    COUNT(*) AS bigquery_data_access_number_of_queries\n" +
            "FROM `ccm-play.BQPOC.cloudaudit_googleapis_com_data_access` AS bigquery_data_access\n" +
            "LEFT JOIN UNNEST([bigquery_data_access.protopayload_auditlog]) as bigquery_data_access_payload\n" +
            "LEFT JOIN UNNEST([bigquery_data_access_payload.servicedata_v1_bigquery]) AS bigquery_data_access_servicedata\n" +
            "LEFT JOIN UNNEST([bigquery_data_access_servicedata.jobCompletedEvent]) AS bigquery_data_access_job_completed_event\n" +
            "LEFT JOIN UNNEST([bigquery_data_access_job_completed_event.job]) AS bigquery_data_access_job\n" +
            "LEFT JOIN UNNEST([bigquery_data_access_job.jobStatistics]) AS bigquery_data_access_job_statistics\n" +
            "LEFT JOIN UNNEST([bigquery_data_access_job.jobStatus]) AS bigquery_data_access_job_status\n" +
            "WHERE (bigquery_data_access_payload.methodName ) = 'jobservice.jobcompleted' AND (bigquery_data_access_payload.serviceName ) = 'bigquery.googleapis.com' AND (bigquery_data_access_job_completed_event.eventName ) LIKE 'query_job_completed' AND ((( bigquery_data_access_job_statistics.startTime  ) >= ((TIMESTAMP_ADD(TIMESTAMP_TRUNC(CURRENT_TIMESTAMP(), DAY, 'UTC'), INTERVAL -29 DAY))) AND ( bigquery_data_access_job_statistics.startTime  ) < ((TIMESTAMP_ADD(TIMESTAMP_ADD(TIMESTAMP_TRUNC(CURRENT_TIMESTAMP(), DAY, 'UTC'), INTERVAL -29 DAY), INTERVAL 30 DAY))))) AND (bigquery_data_access_job_status.error IS NOT NULL )\n";

    private static final String BQ_TIME_SERIES_VISBILITY = "SELECT\n" +
            "    (TIMESTAMP_TRUNC( bigquery_data_access_job_statistics.startTime, DAY)) AS bigquery_data_access_job_statistics_start_date,\n" +
            "    COUNT(*) AS bigquery_data_access_number_of_queries,\n" +
            "    COALESCE(SUM(( 5.0*bigquery_data_access_job_statistics.totalBilledBytes/1000000000000  ) ), 0) AS bigquery_data_access_job_statistics_total_query_cost\n" +
            "FROM `ccm-play.BQPOC.cloudaudit_googleapis_com_data_access` AS bigquery_data_access\n" +
            "LEFT JOIN UNNEST([bigquery_data_access.protopayload_auditlog]) as bigquery_data_access_payload\n" +
            "LEFT JOIN UNNEST([bigquery_data_access_payload.servicedata_v1_bigquery]) AS bigquery_data_access_servicedata\n" +
            "LEFT JOIN UNNEST([bigquery_data_access_servicedata.jobCompletedEvent]) AS bigquery_data_access_job_completed_event\n" +
            "LEFT JOIN UNNEST([bigquery_data_access_job_completed_event.job]) AS bigquery_data_access_job\n" +
            "LEFT JOIN UNNEST([bigquery_data_access_job.jobStatistics]) AS bigquery_data_access_job_statistics\n" +
            "WHERE (bigquery_data_access_payload.methodName ) = 'jobservice.jobcompleted' AND (bigquery_data_access_payload.serviceName ) = 'bigquery.googleapis.com' AND (bigquery_data_access_job_completed_event.eventName ) LIKE 'query_job_completed' AND ((( bigquery_data_access_job_statistics.startTime  ) >= ((TIMESTAMP_ADD(TIMESTAMP_TRUNC(CURRENT_TIMESTAMP(), DAY, 'UTC'), INTERVAL -29 DAY))) AND ( bigquery_data_access_job_statistics.startTime  ) < ((TIMESTAMP_ADD(TIMESTAMP_ADD(TIMESTAMP_TRUNC(CURRENT_TIMESTAMP(), DAY, 'UTC'), INTERVAL -29 DAY), INTERVAL 30 DAY)))))\n" +
            "GROUP BY\n" +
            "    1\n" +
            "ORDER BY\n" +
            "    1 DESC\n";

    private static final String BQ_EXPENSIVE_QUERIES = "SELECT\n" +
            "    bigquery_data_access_authentication_info.principalEmail  AS bigquery_data_access_authentication_info_user_id,\n" +
            "    bigquery_data_access_resource_labels.project_id  AS bigquery_data_access_resource_labels_project_id,\n" +
            "    bigquery_data_access_query.query  AS bigquery_data_access_query_query,\n" +
            "    5.0*bigquery_data_access_job_statistics.totalBilledBytes/1000000000000  AS bigquery_data_access_job_statistics_query_cost,\n" +
            "    1.0*TIMESTAMP_DIFF(bigquery_data_access_job_statistics.endTime, bigquery_data_access_job_statistics.startTime, MILLISECOND)/1000  AS bigquery_data_access_job_statistics_query_runtime\n" +
            "FROM `ccm-play.BQPOC.cloudaudit_googleapis_com_data_access`  AS bigquery_data_access\n" +
            "LEFT JOIN UNNEST([bigquery_data_access.protopayload_auditlog]) as bigquery_data_access_payload\n" +
            "LEFT JOIN UNNEST([bigquery_data_access.resource]) AS bigquery_data_access_resource\n" +
            "LEFT JOIN UNNEST([bigquery_data_access_resource.labels]) as bigquery_data_access_resource_labels\n" +
            "LEFT JOIN UNNEST([bigquery_data_access_payload.servicedata_v1_bigquery]) AS bigquery_data_access_servicedata\n" +
            "LEFT JOIN UNNEST([bigquery_data_access_servicedata.jobCompletedEvent]) AS bigquery_data_access_job_completed_event\n" +
            "LEFT JOIN UNNEST([bigquery_data_access_job_completed_event.job]) AS bigquery_data_access_job\n" +
            "LEFT JOIN UNNEST([bigquery_data_access_job.jobStatistics]) AS bigquery_data_access_job_statistics\n" +
            "LEFT JOIN UNNEST([bigquery_data_access_job.jobConfiguration]) AS bigquery_data_access_job_configuration\n" +
            "LEFT JOIN UNNEST([bigquery_data_access_job_configuration.query]) AS bigquery_data_access_query\n" +
            "LEFT JOIN UNNEST([bigquery_data_access_payload.authenticationInfo]) AS bigquery_data_access_authentication_info\n" +
            "WHERE (bigquery_data_access_payload.methodName ) = 'jobservice.jobcompleted' AND (bigquery_data_access_payload.serviceName ) = 'bigquery.googleapis.com' AND (bigquery_data_access_job_completed_event.eventName ) LIKE 'query_job_completed' AND (1.0*bigquery_data_access_job_statistics.totalBilledBytes/1000000000 ) > 30\n" +
            "GROUP BY\n" +
            "    1,\n" +
            "    2,\n" +
            "    3,\n" +
            "    4,\n" +
            "    5\n" +
            "ORDER BY\n" +
            "    4 DESC\n" +
            "LIMIT 10";

    private static final String BQ_SLOTS = "SELECT startTime, allocatedSlots, reservedSlots, netReservedSlots, gcpProjectId\n" +
            "FROM `ccm-play.BigQueryHackathon.bqslotusage` WHERE (( startTime >= ((TIMESTAMP_ADD(TIMESTAMP_TRUNC(CURRENT_TIMESTAMP(), DAY, 'UTC'), INTERVAL -29 DAY))) AND startTime < ((TIMESTAMP_ADD(TIMESTAMP_ADD(TIMESTAMP_TRUNC(CURRENT_TIMESTAMP(), DAY, 'UTC'), INTERVAL -29 DAY), INTERVAL 30 DAY))))) ";

    @Override
    public Double getTotalCost() {
        BigQuery bigquery = bigQueryService.get();
        String query = String.format(BQ_TOTAL_COST);
        QueryJobConfiguration queryConfig =
                QueryJobConfiguration.newBuilder(query)
                        .build();

        // Get the results.
        TableResult result;
        try {
            result = bigquery.query(queryConfig);
        } catch (InterruptedException e) {
            log.error("Failed to check for data. {}", e);
            Thread.currentThread().interrupt();
            return 0D;
        }

        for (FieldValueList row : result.iterateAll()) {
            return row.get("bigquery_data_access_job_statistics_total_query_cost").getDoubleValue();
        }
        return 0D;
    }

    @Override
    public Double getBytesScanned() {
        BigQuery bigquery = bigQueryService.get();
        String query = String.format(BQ_TOTAL_BYTES_SCANNED);
        QueryJobConfiguration queryConfig =
                QueryJobConfiguration.newBuilder(query)
                        .build();

        // Get the results.
        TableResult result;
        try {
            result = bigquery.query(queryConfig);
        } catch (InterruptedException e) {
            log.error("Failed to check for data. {}", e);
            Thread.currentThread().interrupt();
            return 0D;
        }

        for (FieldValueList row : result.iterateAll()) {
            return row.get("bigquery_data_access_job_statistics_total_billed_terabytes").getDoubleValue();
        }
        return 0D;
    }

    @Override
    public Double getSuccessfulQueries() {
        BigQuery bigquery = bigQueryService.get();
        String query = String.format(BQ_SUCCESS_QUERIES);
        QueryJobConfiguration queryConfig =
                QueryJobConfiguration.newBuilder(query)
                        .build();

        // Get the results.
        TableResult result;
        try {
            result = bigquery.query(queryConfig);
        } catch (InterruptedException e) {
            log.error("Failed to check for data. {}", e);
            Thread.currentThread().interrupt();
            return 0D;
        }

        for (FieldValueList row : result.iterateAll()) {
            return row.get("bigquery_data_access_number_of_queries").getDoubleValue();
        }
        return 0D;
    }

    @Override
    public Double getFailedQueries() {
        BigQuery bigquery = bigQueryService.get();
        String query = String.format(BQ_FAILED_QUERIES);
        QueryJobConfiguration queryConfig =
                QueryJobConfiguration.newBuilder(query)
                        .build();

        // Get the results.
        TableResult result;
        try {
            result = bigquery.query(queryConfig);
        } catch (InterruptedException e) {
            log.error("Failed to check for data. {}", e);
            Thread.currentThread().interrupt();
            return 0D;
        }

        for (FieldValueList row : result.iterateAll()) {
            return row.get("bigquery_data_access_number_of_queries").getDoubleValue();
        }
        return 0D;
    }

    @Override
    public List<BQOrchestratorVisibilityDataPoint> getVisibilityTimeSeries() {
        BigQuery bigquery = bigQueryService.get();
        String query = String.format(BQ_TIME_SERIES_VISBILITY);
        QueryJobConfiguration queryConfig =
                QueryJobConfiguration.newBuilder(query)
                        .build();
        List<BQOrchestratorVisibilityDataPoint> visibilityDataPointList = new ArrayList<>();


        // Get the results.
        TableResult result;
        try {
            result = bigquery.query(queryConfig);
        } catch (InterruptedException e) {
            log.error("Failed to check for data. {}", e);
            Thread.currentThread().interrupt();
            return visibilityDataPointList;
        }
        for (FieldValueList row : result.iterateAll()) {
            log.info("Row: {}", row);
            log.info("Iterating for Row: {}", visibilityDataPointList.size());
            BQOrchestratorVisibilityDataPoint.BQOrchestratorVisibilityDataPointBuilder builder = BQOrchestratorVisibilityDataPoint.builder();
            builder.timestamp(row.get("bigquery_data_access_job_statistics_start_date").getTimestampValue());
            builder.cost(row.get("bigquery_data_access_job_statistics_total_query_cost").getDoubleValue());
            builder.queries(row.get("bigquery_data_access_number_of_queries").getDoubleValue());
            visibilityDataPointList.add(builder.build());
        }
        log.info("End of Iteration");
        return visibilityDataPointList;
    }

    @Override
    public List<BQOrchestratorExpensiveQueryPoint> getExpensiveQueries() {
        BigQuery bigquery = bigQueryService.get();
        String query = String.format(BQ_EXPENSIVE_QUERIES);
        QueryJobConfiguration queryConfig =
                QueryJobConfiguration.newBuilder(query)
                        .build();
        List<BQOrchestratorExpensiveQueryPoint> bqOrchestratorExpensiveQueryPointList = new ArrayList<>();


        // Get the results.
        TableResult result;
        try {
            result = bigquery.query(queryConfig);
        } catch (InterruptedException e) {
            log.error("Failed to check for data. {}", e);
            Thread.currentThread().interrupt();
            return bqOrchestratorExpensiveQueryPointList;
        }

        for (FieldValueList row : result.iterateAll()) {
            BQOrchestratorExpensiveQueryPoint.BQOrchestratorExpensiveQueryPointBuilder builder = BQOrchestratorExpensiveQueryPoint.builder();
            builder.userID(row.get("bigquery_data_access_authentication_info_user_id").getStringValue());
            builder.query(row.get("bigquery_data_access_query_query").getStringValue());
            builder.cost(row.get("bigquery_data_access_job_statistics_query_cost").getDoubleValue());
            builder.runtime(row.get("bigquery_data_access_job_statistics_query_runtime").getDoubleValue());
            bqOrchestratorExpensiveQueryPointList.add(builder.build());
        }
        return bqOrchestratorExpensiveQueryPointList;
    }

    @Override
    public List<BQOrchestratorSlotsDataPoint> getSlotData() {
        BigQuery bigquery = bigQueryService.get();
        String query = String.format(BQ_SLOTS);
        QueryJobConfiguration queryConfig =
                QueryJobConfiguration.newBuilder(query)
                        .build();
        List<BQOrchestratorSlotsDataPoint> bqOrchestratorSlotsDataPointList = new ArrayList<>();


        // Get the results.
        TableResult result;
        try {
            result = bigquery.query(queryConfig);
        } catch (InterruptedException e) {
            log.error("Failed to check for data. {}", e);
            Thread.currentThread().interrupt();
            return bqOrchestratorSlotsDataPointList;
        }

        for (FieldValueList row : result.iterateAll()) {
            BQOrchestratorSlotsDataPoint.BQOrchestratorSlotsDataPointBuilder builder = BQOrchestratorSlotsDataPoint.builder();
            builder.startTime(row.get("startTime").getTimestampValue());
            builder.allocatedSlots(row.get("allocatedSlots").getDoubleValue());
            builder.reservedSlots(row.get("reservedSlots").getDoubleValue());
            builder.gcpProjectId(row.get("gcpProjectId").getStringValue());
            bqOrchestratorSlotsDataPointList.add(builder.build());
        }
        return bqOrchestratorSlotsDataPointList;
    }

    @Override
    public BQOrchestratorSlotUsageStats getSlotUsageStats(BQOrchestratorOptimizationType optimizationType, BQOrchestratorCommitmentDuration commitmentDuration, Double slotCount) {
        List<BQOrchestratorSlotsDataPoint> slotData = getSlotData();
        double recommendedSlotCount = getRecommendedSlotUsage(optimizationType, slotData);
        double customSlotCount = slotCount != null ? slotCount : recommendedSlotCount;
        double numberOfDataPoints = slotData.size();
        double aggregatedSlotFraction = 0.0;
        double averageSlotUsage = 0.0;
        double coveragePercentage = 0.0;
        for (BQOrchestratorSlotsDataPoint dataPoint : slotData) {
            averageSlotUsage += dataPoint.getAllocatedSlots();
            aggregatedSlotFraction += Math.min((customSlotCount/dataPoint.getAllocatedSlots()) , 1.0);
        }
        coveragePercentage = (aggregatedSlotFraction/numberOfDataPoints) * 100;
        averageSlotUsage /= numberOfDataPoints;

        return BQOrchestratorSlotUsageStats.builder()
                .potentialSavings(getPotentialSavings(customSlotCount,averageSlotUsage, commitmentDuration))
                .recommendedSlots(recommendedSlotCount)
                .coveragePercentage(coveragePercentage)
                .averageOnDemandSlots(averageSlotUsage)
                .build();
    }

    private Double getRecommendedSlotUsage(BQOrchestratorOptimizationType optimizationType, List<BQOrchestratorSlotsDataPoint> slotData){
        double averageSlotUsage = 0.0;
        double minSlotUsage = Integer.MAX_VALUE;
        double numberOfDataPoints = slotData.size();

        for (BQOrchestratorSlotsDataPoint dataPoint : slotData) {
            averageSlotUsage += dataPoint.getAllocatedSlots();
            minSlotUsage = Math.min(minSlotUsage, dataPoint.getAllocatedSlots());
        }

        averageSlotUsage = averageSlotUsage/numberOfDataPoints;
        switch (optimizationType){
            case PERFORMANCE_OPTIMIZED:
                return 100*(Math.floor(Math.abs(averageSlotUsage/100)));
            case COST_OPTIMIZED:
            default:
                return 100*(Math.floor(Math.abs(minSlotUsage/100)));
        }
    }

    private Double getPotentialSavings(Double customSlotCount, Double averageSlotCount, BQOrchestratorCommitmentDuration commitmentDuration){
        double baselinePotentialSavings = getTotalCost() * 0.4;
        double savingsFactor = commitmentDuration == MONTHLY ? 10.0 : 13.0;
        return baselinePotentialSavings + (averageSlotCount - customSlotCount)* savingsFactor;
    }
}
