/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.billing.timeseries.service.impl;

import io.harness.batch.processing.billing.timeseries.service.support.BillingDataTableNameProvider;
import io.harness.batch.processing.ccm.ActualIdleCostData;
import io.harness.batch.processing.ccm.BatchJobType;
import io.harness.timescaledb.DBUtils;
import io.harness.timescaledb.TimeScaleDBService;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ActualIdleBillingDataServiceImpl {
  @Autowired private TimeScaleDBService timeScaleDBService;

  static final String GET_UNALLOCATED_AND_IDLE_COST_DATA_FOR_NODES =
      "SELECT SUM(BILLINGAMOUNT) AS COST, SUM(CPUBILLINGAMOUNT) AS CPUCOST, SUM(MEMORYBILLINGAMOUNT) AS MEMORYCOST, SUM(SYSTEMCOST) AS SYSTEMCOST, SUM(CPUSYSTEMCOST) AS CPUSYSTEMCOST, SUM(MEMORYSYSTEMCOST) AS MEMORYSYSTEMCOST, SUM(IDLECOST) AS IDLECOST, SUM(CPUIDLECOST) AS CPUIDLECOST, SUM(MEMORYIDLECOST) AS MEMORYIDLECOST, ACCOUNTID, CLUSTERID, INSTANCEID FROM %s WHERE ACCOUNTID = '%s' AND CLUSTERID IS NOT NULL AND INSTANCETYPE IN ('K8S_NODE', 'ECS_CONTAINER_INSTANCE') AND STARTTIME >= '%s' AND STARTTIME < '%s' GROUP BY ACCOUNTID, CLUSTERID, INSTANCEID;";

  static final String GET_UNALLOCATED_AND_IDLE_COST_DATA_FOR_PODS =
      "SELECT SUM(BILLINGAMOUNT) AS COST, SUM(CPUBILLINGAMOUNT) AS CPUCOST, SUM(MEMORYBILLINGAMOUNT) AS MEMORYCOST, SUM(SYSTEMCOST) AS SYSTEMCOST, SUM(CPUSYSTEMCOST) AS CPUSYSTEMCOST, SUM(MEMORYSYSTEMCOST) AS MEMORYSYSTEMCOST, SUM(IDLECOST) AS IDLECOST, SUM(CPUIDLECOST) AS CPUIDLECOST, SUM(MEMORYIDLECOST) AS MEMORYIDLECOST, ACCOUNTID, CLUSTERID, PARENTINSTANCEID FROM %s WHERE ACCOUNTID = '%s' AND CLUSTERID IS NOT NULL AND INSTANCETYPE IN ('K8S_POD', 'ECS_TASK_EC2') AND STARTTIME >= '%s' AND STARTTIME < '%s' GROUP BY ACCOUNTID, CLUSTERID, PARENTINSTANCEID;";

  private static final String PARENT_INSTANCE_ID = "PARENT_INSTANCE_ID";

  // For nodes / container instance
  public List<ActualIdleCostData> getActualIdleCostDataForNodes(
      String accountId, long startDate, long endDate, BatchJobType batchJobType) {
    ResultSet resultSet = null;
    List<ActualIdleCostData> actualIdleCostDataList = new ArrayList<>();

    String query = String.format(GET_UNALLOCATED_AND_IDLE_COST_DATA_FOR_NODES,
        BillingDataTableNameProvider.getTableName(batchJobType), accountId, Instant.ofEpochMilli(startDate),
        Instant.ofEpochMilli(endDate));
    try (Connection connection = timeScaleDBService.getDBConnection();
         Statement statement = connection.createStatement()) {
      resultSet = statement.executeQuery(query);
      while (resultSet.next()) {
        actualIdleCostDataList.add(ActualIdleCostData.builder()
                                       .accountId(resultSet.getString("ACCOUNTID"))
                                       .clusterId(resultSet.getString("CLUSTERID"))
                                       .instanceId(resultSet.getString("INSTANCEID"))
                                       .parentInstanceId(PARENT_INSTANCE_ID)
                                       .cost(resultSet.getDouble("COST"))
                                       .cpuCost(resultSet.getDouble("CPUCOST"))
                                       .memoryCost(resultSet.getDouble("MEMORYCOST"))
                                       .idleCost(resultSet.getDouble("IDLECOST"))
                                       .cpuIdleCost(resultSet.getDouble("CPUIDLECOST"))
                                       .memoryIdleCost(resultSet.getDouble("MEMORYIDLECOST"))
                                       .systemCost(resultSet.getDouble("SYSTEMCOST"))
                                       .cpuSystemCost(resultSet.getDouble("CPUSYSTEMCOST"))
                                       .memorySystemCost(resultSet.getDouble("MEMORYSYSTEMCOST"))
                                       .startTime(startDate)
                                       .endTime(endDate)
                                       .build());
      }
      return actualIdleCostDataList;
    } catch (SQLException e) {
      log.error("Error while fetching Actual Idle Cost Data List for Nodes : exception", e);
    } finally {
      DBUtils.close(resultSet);
    }
    return Collections.emptyList();
  }

  // For pods/tasks
  public List<ActualIdleCostData> getActualIdleCostDataForPods(
      String accountId, long startDate, long endDate, BatchJobType batchJobType) {
    ResultSet resultSet = null;
    List<ActualIdleCostData> actualIdleCostDataList = new ArrayList<>();

    String query = String.format(GET_UNALLOCATED_AND_IDLE_COST_DATA_FOR_PODS,
        BillingDataTableNameProvider.getTableName(batchJobType), accountId, Instant.ofEpochMilli(startDate),
        Instant.ofEpochMilli(endDate));
    try (Connection connection = timeScaleDBService.getDBConnection();
         Statement statement = connection.createStatement()) {
      resultSet = statement.executeQuery(query);
      while (resultSet.next()) {
        actualIdleCostDataList.add(ActualIdleCostData.builder()
                                       .accountId(resultSet.getString("ACCOUNTID"))
                                       .clusterId(resultSet.getString("CLUSTERID"))
                                       .parentInstanceId(resultSet.getString("PARENTINSTANCEID"))
                                       .cost(resultSet.getDouble("COST"))
                                       .cpuCost(resultSet.getDouble("CPUCOST"))
                                       .memoryCost(resultSet.getDouble("MEMORYCOST"))
                                       .idleCost(resultSet.getDouble("IDLECOST"))
                                       .cpuIdleCost(resultSet.getDouble("CPUIDLECOST"))
                                       .memoryIdleCost(resultSet.getDouble("MEMORYIDLECOST"))
                                       .systemCost(resultSet.getDouble("SYSTEMCOST"))
                                       .cpuSystemCost(resultSet.getDouble("CPUSYSTEMCOST"))
                                       .memorySystemCost(resultSet.getDouble("MEMORYSYSTEMCOST"))
                                       .startTime(startDate)
                                       .endTime(endDate)
                                       .build());
      }
      return actualIdleCostDataList;
    } catch (SQLException e) {
      log.error("Error while fetching Actual Idle Cost Data List for Pods : exception", e);
    } finally {
      DBUtils.close(resultSet);
    }
    return Collections.emptyList();
  }
}
