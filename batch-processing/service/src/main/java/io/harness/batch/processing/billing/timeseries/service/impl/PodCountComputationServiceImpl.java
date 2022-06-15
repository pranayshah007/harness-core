/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.billing.timeseries.service.impl;

import io.harness.batch.processing.billing.timeseries.data.InstanceLifecycleInfo;
import io.harness.batch.processing.billing.timeseries.data.NodePodId;
import io.harness.batch.processing.billing.timeseries.data.PodActivityInfo;
import io.harness.batch.processing.billing.timeseries.data.PodCountData;
import io.harness.batch.processing.billing.timeseries.data.UsageTimeInfo;
import io.harness.batch.processing.service.intfc.InstanceDataService;
import io.harness.batch.processing.shard.AccountShardService;
import io.harness.ccm.commons.utils.TimeUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.timescaledb.DBUtils;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.service.impl.instance.CloudToHarnessMappingServiceImpl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class PodCountComputationServiceImpl {
  @Autowired private TimeScaleDBService timeScaleDBService;
  @Autowired private CloudToHarnessMappingServiceImpl cloudToHarnessMappingService;
  @Autowired private AccountShardService accountShardService;
  @Autowired private InstanceDataService instanceDataService;
  @Autowired private TimeUtils utils;

  private static final String ACTIVE_POD_COUNT_PURGE_QUERY =
      "SELECT drop_chunks('active_pod_count', interval '90 days')";

  static final String GET_NODE_QUERY =
      "SELECT INSTANCEID, CLUSTERID FROM billing_data where INSTANCETYPE = 'K8S_NODE' AND ACCOUNTID = '%s' AND STARTTIME >= '%s' AND STARTTIME < '%s' GROUP BY INSTANCEID, CLUSTERID";

  static final String GET_PODS_QUERY =
      "SELECT DISTINCT(INSTANCEID) AS PODID FROM billing_data where INSTANCETYPE = 'K8S_POD' AND ACCOUNTID = '%s' AND CLUSTERID = '%s' AND PARENTINSTANCEID = '%s'  AND STARTTIME >= '%s' AND STARTTIME < '%s';";
  private static final int MAX_RETRY = 3;
  private static final int BATCH_SIZE = 500;
  private static final long ONE_DAY_MILLIS = 86400000;
  private static final long FIVE_MINUTES_IN_MILLIS = 300000;
  static final String INSERT_STATEMENT =
      "INSERT INTO ACTIVE_POD_COUNT (STARTTIME, ENDTIME, ACCOUNTID, CLUSTERID, INSTANCEID, PODCOUNT) VALUES (?,?,?,?,?,?) ON CONFLICT DO NOTHING";

  public boolean computePodCountForNodes(String accountId, long startTime, long endTime, NodePodId nodePodId) {
    List<InstanceLifecycleInfo> instanceLifecycleInfoList =
        instanceDataService.fetchInstanceDataForGivenInstances(nodePodId.getPodId());
    Map<String, PodActivityInfo> podIdToActivityInfo = getPodActivityInfo(instanceLifecycleInfoList, startTime);
    return storePodCountForNode(accountId, startTime, endTime, nodePodId.getNodeId(), nodePodId.getClusterId(),
        nodePodId.getPodId(), podIdToActivityInfo);
  }

  private boolean storePodCountForNode(String accountId, long startTime, long endTime, String nodeId, String clusterId,
      Set<String> podIds, Map<String, PodActivityInfo> podIdToActivityInfo) {
    List<UsageTimeInfo> times = new ArrayList<>();
    podIds.forEach(podId -> {
      PodActivityInfo podTimings = podIdToActivityInfo.get(podId);
      if (podTimings != null) {
        times.add(UsageTimeInfo.builder().time(podTimings.getUsageStartTime()).type("START").build());
        times.add(UsageTimeInfo.builder().time(podTimings.getUsageStopTime()).type("STOP").build());
      }
    });
    times.sort(Comparator.comparing(UsageTimeInfo::getTime));
    long count = 0;
    int index = 0;
    long jobStartTime = startTime;
    Map<Long, Long> podCount = getDefaultCountMap(startTime);
    while (startTime < endTime) {
      int offset = 0;
      while (index < times.size() && times.get(index).getTime() <= startTime + FIVE_MINUTES_IN_MILLIS - 1) {
        if (times.get(index).getType().equals("START")) {
          count++;
        } else {
          offset++;
        }
        index++;
      }
      podCount.put(startTime, count);
      count -= offset;
      startTime += FIVE_MINUTES_IN_MILLIS;
    }
    return insertDataInTable(clusterId, accountId, nodeId, jobStartTime, podCount);
  }

  public boolean purgeActivePodCount() {
    log.info("Purging old {} data !!", ACTIVE_POD_COUNT_PURGE_QUERY);
    return executeQuery(ACTIVE_POD_COUNT_PURGE_QUERY);
  }

  private boolean executeQuery(String query) {
    boolean result = false;
    if (timeScaleDBService.isValid()) {
      int retryCount = 0;
      while (retryCount < 2 && !result) {
        try (Connection connection = timeScaleDBService.getDBConnection();
             Statement statement = connection.createStatement()) {
          statement.execute(query);
          result = true;
        } catch (SQLException e) {
          log.error("Failed to execute query=[{}]", query, e);
          retryCount++;
        }
      }
    }
    return result;
  }

  private boolean insertDataInTable(
      String clusterId, String accountId, String nodeId, long startTime, Map<Long, Long> podCount) {
    boolean successfulInsert = false;
    if (timeScaleDBService.isValid()) {
      int retryCount = 0;
      while (!successfulInsert && retryCount < MAX_RETRY) {
        try (Connection dbConnection = timeScaleDBService.getDBConnection();
             PreparedStatement statement = dbConnection.prepareStatement(INSERT_STATEMENT)) {
          int index = 0;
          int totalEntries = podCount.size();
          long time = startTime;
          while (index < totalEntries) {
            updateInsertStatement(statement,
                PodCountData.builder()
                    .accountId(accountId)
                    .clusterId(clusterId)
                    .nodeId(nodeId)
                    .startTime(time)
                    .endTime(time + FIVE_MINUTES_IN_MILLIS - 1)
                    .count(podCount.get(time))
                    .build());
            statement.addBatch();
            index++;
            time += FIVE_MINUTES_IN_MILLIS;

            if (index % BATCH_SIZE == 0 || index == totalEntries) {
              statement.executeBatch();
            }
          }
          successfulInsert = true;
        } catch (SQLException e) {
          log.error("Failed to save podCount data,retryCount=[{}], Exception: ", retryCount, e);
          retryCount++;
        }
      }
    } else {
      log.error("TimescaleDbService is invalid");
    }
    return successfulInsert;
  }

  public List<NodePodId> getNodes(String accountId, Instant startTime, Instant endTime) {
    List<NodePodId> nodePodIdList = new ArrayList<>();
    if (timeScaleDBService.isValid()) {
      ResultSet resultSet = null;
      boolean successful = false;
      int retryCount = 0;
      String query = String.format(GET_NODE_QUERY, accountId, startTime, endTime);
      while (!successful && retryCount < MAX_RETRY) {
        try (Connection connection = timeScaleDBService.getDBConnection();
             Statement statement = connection.createStatement()) {
          resultSet = statement.executeQuery(query);
          successful = true;
          while (resultSet.next()) {
            String nodeId = resultSet.getString("INSTANCEID");
            String clusterId = resultSet.getString("CLUSTERID");
            nodePodIdList.add(NodePodId.builder().clusterId(clusterId).nodeId(nodeId).build());
          }
        } catch (SQLException e) {
          retryCount++;
          if (retryCount >= MAX_RETRY) {
            log.error("Failed to execute query in getNodes, max retry count reached, query=[{}],accountId=[{}]", query,
                accountId, e);
          } else {
            log.warn("Failed to execute query in getNodes, query=[{}],accountId=[{}], retryCount=[{}]", query,
                accountId, retryCount);
          }
        } finally {
          DBUtils.close(resultSet);
        }
      }
    } else {
      throw new InvalidRequestException("Cannot process request in getNodes");
    }
    return nodePodIdList;
  }

  public NodePodId getPods(String accountId, String clusterId, String nodeId, Instant startTime, Instant endTime) {
    Set<String> pods = new HashSet<>();
    if (timeScaleDBService.isValid()) {
      ResultSet resultSet = null;
      boolean successful = false;
      int retryCount = 0;
      String query = String.format(GET_PODS_QUERY, accountId, clusterId, nodeId, startTime, endTime);
      while (!successful && retryCount < MAX_RETRY) {
        try (Connection connection = timeScaleDBService.getDBConnection();
             Statement statement = connection.createStatement()) {
          resultSet = statement.executeQuery(query);
          successful = true;
          while (resultSet.next()) {
            pods.add(resultSet.getString("PODID"));
          }
        } catch (SQLException e) {
          retryCount++;
          if (retryCount >= MAX_RETRY) {
            log.error("Failed to execute query in getPods, max retry count reached, query=[{}],accountId=[{}]", query,
                accountId, e);
          } else {
            log.warn("Failed to execute query in getPods, query=[{}],accountId=[{}], retryCount=[{}]", query, accountId,
                retryCount);
          }
        } finally {
          DBUtils.close(resultSet);
        }
      }
    } else {
      throw new InvalidRequestException("Cannot process request in getPods");
    }
    return NodePodId.builder().clusterId(clusterId).nodeId(nodeId).podId(pods).build();
  }

  private void updateInsertStatement(PreparedStatement statement, PodCountData data) throws SQLException {
    statement.setTimestamp(1, new Timestamp(data.getStartTime()), utils.getDefaultCalendar());
    statement.setTimestamp(2, new Timestamp(data.getEndTime()), utils.getDefaultCalendar());
    statement.setString(3, data.getAccountId());
    statement.setString(4, data.getClusterId());
    statement.setString(5, data.getNodeId());
    statement.setLong(6, data.getCount());
  }

  private Map<String, PodActivityInfo> getPodActivityInfo(List<InstanceLifecycleInfo> instanceData, long startTime) {
    Map<String, PodActivityInfo> podIdToActivityInfo = new HashMap<>();
    instanceData.forEach(entry -> {
      long usageStartTime = startTime + ONE_DAY_MILLIS;
      long usageStopTime = startTime + ONE_DAY_MILLIS;
      String podId = entry.getInstanceId();
      if (entry.getUsageStartTime() != null) {
        usageStartTime = entry.getUsageStartTime().toEpochMilli();
      }
      if (entry.getUsageStopTime() != null) {
        usageStopTime = entry.getUsageStopTime().toEpochMilli();
      }
      podIdToActivityInfo.put(podId,
          PodActivityInfo.builder().podId(podId).usageStartTime(usageStartTime).usageStopTime(usageStopTime).build());
    });
    return podIdToActivityInfo;
  }

  private Map<Long, Long> getDefaultCountMap(long startTime) {
    long endTime = startTime + ONE_DAY_MILLIS;
    Map<Long, Long> podCount = new HashMap<>();
    while (startTime < endTime) {
      podCount.put(startTime, 0L);
      startTime += FIVE_MINUTES_IN_MILLIS;
    }
    return podCount;
  }
}
