/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.billing.timeseries.service.impl;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.batch.processing.billing.service.UtilizationData;
import io.harness.batch.processing.billing.timeseries.data.InstanceUtilizationData;
import io.harness.batch.processing.cloudevents.aws.ecs.service.util.ClusterIdAndServiceArn;
import io.harness.batch.processing.cloudevents.aws.ecs.service.util.ECSUtilizationData;
import io.harness.ccm.commons.beans.InstanceType;
import io.harness.ccm.commons.constants.InstanceMetaDataConstants;
import io.harness.ccm.commons.entities.batch.InstanceData;
import io.harness.ccm.commons.utils.TimeUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.timescaledb.DBUtils;
import io.harness.timescaledb.TimeScaleDBService;

import com.google.inject.Singleton;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Singleton
@Slf4j
public class UtilizationDataServiceImpl {
  @Autowired private TimeScaleDBService timeScaleDBService;
  @Autowired private TimeUtils utils;

  private static final int MAX_RETRY_COUNT = 2;
  private static final int SELECT_MAX_RETRY_COUNT = 5;
  private static final int BATCH_SIZE = 500;

  static final String INSERT_STATEMENT =
      "INSERT INTO UTILIZATION_DATA (STARTTIME, ENDTIME, ACCOUNTID, MAXCPU, MAXMEMORY, AVGCPU, AVGMEMORY, INSTANCEID, INSTANCETYPE, CLUSTERID, SETTINGID, MAXCPUVALUE, MAXMEMORYVALUE, AVGCPUVALUE, AVGMEMORYVALUE, AVGSTORAGECAPACITYVALUE, AVGSTORAGEUSAGEVALUE, AVGSTORAGEREQUESTVALUE, MAXSTORAGEUSAGEVALUE, MAXSTORAGEREQUESTVALUE) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT DO NOTHING";
  private static final String UTILIZATION_DATA_QUERY =
      "SELECT MAX(MAXCPU) as MAXCPUUTILIZATION, MAX(MAXMEMORY) as MAXMEMORYUTILIZATION, AVG(AVGCPU) as AVGCPUUTILIZATION, AVG(AVGMEMORY) as AVGMEMORYUTILIZATION, MAX(MAXCPUVALUE) as MAXCPUVALUE, MAX(MAXMEMORYVALUE) as MAXMEMORYVALUE, AVG(AVGCPUVALUE) as AVGCPUVALUE, AVG(AVGMEMORYVALUE) as AVGMEMORYVALUE, AVG(AVGSTORAGECAPACITYVALUE) as AVGSTORAGECAPACITYVALUE ,AVG(AVGSTORAGEUSAGEVALUE) as AVGSTORAGEUSAGEVALUE, AVG(AVGSTORAGEREQUESTVALUE) as AVGSTORAGEREQUESTVALUE ,MAX(MAXSTORAGEUSAGEVALUE) as MAXSTORAGEUSAGEVALUE, MAX(MAXSTORAGEREQUESTVALUE) as MAXSTORAGEREQUESTVALUE, INSTANCEID FROM UTILIZATION_DATA WHERE ACCOUNTID = '%s' AND SETTINGID = '%s' AND CLUSTERID = '%s' AND INSTANCEID IN ('%s') AND STARTTIME >= '%s' AND STARTTIME < '%s' GROUP BY INSTANCEID;";
  private static final String UTILIZATION_DATA_QUERY_BY_CLUSTER_IDS =
      "SELECT INSTANCEID AS SERVICEID, CLUSTERID, MAX(MAXCPU) as MAXCPUUTILIZATION, MAX(MAXMEMORY) as MAXMEMORYUTILIZATION, STARTTIME, ENDTIME FROM UTILIZATION_DATA WHERE ACCOUNTID = '%s' AND CLUSTERID IN ('%s') AND STARTTIME >= '%s' AND STARTTIME < '%s' GROUP BY CLUSTERID, INSTANCEID, STARTTIME, ENDTIME ORDER BY STARTTIME ASC;";
  private static final String UTILIZATION_DATA_PURGE_QUERY =
      "SELECT drop_chunks('utilization_data', interval '90 days')";

  public boolean create(List<InstanceUtilizationData> instanceUtilizationDataList) {
    boolean successfulInsert = false;
    if (timeScaleDBService.isValid() && isNotEmpty(instanceUtilizationDataList)) {
      log.info("Util data size {}", instanceUtilizationDataList.size());
      int retryCount = 0;
      while (!successfulInsert && retryCount < MAX_RETRY_COUNT) {
        try (Connection dbConnection = timeScaleDBService.getDBConnection();
             PreparedStatement statement = dbConnection.prepareStatement(INSERT_STATEMENT)) {
          int index = 0;
          for (InstanceUtilizationData instanceUtilizationData : instanceUtilizationDataList) {
            updateInsertStatement(statement, instanceUtilizationData);
            statement.addBatch();
            index++;

            if (index % BATCH_SIZE == 0 || index == instanceUtilizationDataList.size()) {
              statement.executeBatch();
            }
          }
          successfulInsert = true;
        } catch (SQLException e) {
          log.error("Failed to save instance Utilization data,[{}],retryCount=[{}], Exception: ",
              instanceUtilizationDataList, retryCount, e);
          retryCount++;
        }
      }
    } else {
      log.info("Not processing instance Utilization data:[{}]", instanceUtilizationDataList);
    }
    return successfulInsert;
  }

  private void updateInsertStatement(PreparedStatement statement, InstanceUtilizationData instanceUtilizationData)
      throws SQLException {
    statement.setTimestamp(1, new Timestamp(instanceUtilizationData.getStartTimestamp()), utils.getDefaultCalendar());
    statement.setTimestamp(2, new Timestamp(instanceUtilizationData.getEndTimestamp()), utils.getDefaultCalendar());
    statement.setString(3, instanceUtilizationData.getAccountId());
    statement.setDouble(4, instanceUtilizationData.getCpuUtilizationMax());
    statement.setDouble(5, instanceUtilizationData.getMemoryUtilizationMax());
    statement.setDouble(6, instanceUtilizationData.getCpuUtilizationAvg());
    statement.setDouble(7, instanceUtilizationData.getMemoryUtilizationAvg());
    statement.setString(8, instanceUtilizationData.getInstanceId());
    statement.setString(9, instanceUtilizationData.getInstanceType());
    statement.setString(10, instanceUtilizationData.getClusterId());
    statement.setString(11, instanceUtilizationData.getSettingId());
    statement.setDouble(12, instanceUtilizationData.getCpuUtilizationMaxValue());
    statement.setDouble(13, instanceUtilizationData.getMemoryUtilizationMaxValue());
    statement.setDouble(14, instanceUtilizationData.getCpuUtilizationAvgValue());
    statement.setDouble(15, instanceUtilizationData.getMemoryUtilizationAvgValue());
    statement.setDouble(16, instanceUtilizationData.getStorageCapacityAvgValue());
    statement.setDouble(17, instanceUtilizationData.getStorageUsageAvgValue());
    statement.setDouble(18, instanceUtilizationData.getStorageRequestAvgValue());
    statement.setDouble(19, instanceUtilizationData.getStorageUsageMaxValue());
    statement.setDouble(20, instanceUtilizationData.getStorageRequestMaxValue());
  }

  public Map<String, UtilizationData> getUtilizationDataForInstances(List<? extends InstanceData> instanceDataList,
      String startTime, String endTime, String accountId, String settingId, String clusterId) {
    try {
      if (timeScaleDBService.isValid()) {
        Map<String, List<String>> serviceArnToInstanceIds = getServiceArnToInstanceIdMapping(instanceDataList);
        String query = String.format(UTILIZATION_DATA_QUERY, accountId, settingId, clusterId,
            String.join("','", serviceArnToInstanceIds.keySet()), startTime, endTime);

        return getUtilizationDataFromTimescaleDB(query, serviceArnToInstanceIds);
      } else {
        throw new InvalidRequestException("Cannot process request in InstanceBillingDataTasklet");
      }
    } catch (Exception e) {
      throw new InvalidRequestException("Error while fetching utilization data {}", e);
    }
  }

  public Map<ClusterIdAndServiceArn, List<ECSUtilizationData>> getUtilizationDataForECSClusters(
      String accountId, List<String> clusterIds, String startTime, String endTime) {
    try {
      if (timeScaleDBService.isValid()) {
        String query = String.format(
            UTILIZATION_DATA_QUERY_BY_CLUSTER_IDS, accountId, String.join("','", clusterIds), startTime, endTime);
        ResultSet resultSet = null;
        Map<ClusterIdAndServiceArn, List<ECSUtilizationData>> utilizationDataMap = new HashMap<>();
        int retryCount = 0;
        while (retryCount < SELECT_MAX_RETRY_COUNT) {
          retryCount++;
          try (Connection connection = timeScaleDBService.getDBConnection();
               Statement statement = connection.createStatement()) {
            resultSet = statement.executeQuery(query);
            while (resultSet.next()) {
              String clusterId = resultSet.getString("CLUSTERID");
              String serviceId = resultSet.getString("SERVICEID");
              double maxCpuUtilization = resultSet.getDouble("MAXCPUUTILIZATION");
              double maxMemoryUtilization = resultSet.getDouble("MAXMEMORYUTILIZATION");
              Instant utilStartTime = resultSet.getTimestamp("STARTTIME").toInstant();
              Instant utilEndTime = resultSet.getTimestamp("ENDTIME").toInstant();
              ClusterIdAndServiceArn clusterIdAndServiceArn = new ClusterIdAndServiceArn(clusterId, serviceId);
              if (!utilizationDataMap.containsKey(clusterIdAndServiceArn)) {
                utilizationDataMap.put(clusterIdAndServiceArn, new ArrayList<>());
              }
              utilizationDataMap.get(clusterIdAndServiceArn)
                  .add(ECSUtilizationData.builder()
                           .maxCpuUtilization(maxCpuUtilization)
                           .maxMemoryUtilization(maxMemoryUtilization)
                           .startTime(utilStartTime)
                           .endTime(utilEndTime)
                           .build());
            }
            return utilizationDataMap;
          } catch (SQLException e) {
            log.error("Error while fetching utilization data : exception", e);
          } finally {
            DBUtils.close(resultSet);
          }
        }
        return null;
      } else {
        throw new InvalidRequestException("Cannot process request in ECS Recommendation");
      }
    } catch (Exception e) {
      throw new InvalidRequestException("Error while fetching utilization data {}", e);
    }
  }

  private Map<String, UtilizationData> getUtilizationDataFromTimescaleDB(
      String query, Map<String, List<String>> serviceArnToInstanceIds) {
    ResultSet resultSet = null;
    Map<String, UtilizationData> utilizationDataForInstances = new HashMap<>();
    populateDefaultUtilizationData(utilizationDataForInstances, serviceArnToInstanceIds);
    int retryCount = 0;
    log.debug("Utilization data query : {}", query);
    while (retryCount < SELECT_MAX_RETRY_COUNT) {
      retryCount++;
      try (Connection connection = timeScaleDBService.getDBConnection();
           Statement statement = connection.createStatement()) {
        resultSet = statement.executeQuery(query);
        while (resultSet.next()) {
          String instanceId = resultSet.getString("INSTANCEID");
          double maxCpuUtilization = resultSet.getDouble("MAXCPUUTILIZATION");
          double maxMemoryUtilization = resultSet.getDouble("MAXMEMORYUTILIZATION");
          double avgCpuUtilization = resultSet.getDouble("AVGCPUUTILIZATION");
          double avgMemoryUtilization = resultSet.getDouble("AVGMEMORYUTILIZATION");
          double maxCpuValue = resultSet.getDouble("MAXCPUVALUE");
          double maxMemoryValue = resultSet.getDouble("MAXMEMORYVALUE");
          double avgCpuValue = resultSet.getDouble("AVGCPUVALUE");
          double avgMemoryValue = resultSet.getDouble("AVGMEMORYVALUE");

          double avgStorageCapacityValue = resultSet.getDouble("AVGSTORAGECAPACITYVALUE");
          double avgStorageUsageValue = resultSet.getDouble("AVGSTORAGEUSAGEVALUE");
          double avgStorageRequestValue = resultSet.getDouble("AVGSTORAGEREQUESTVALUE");

          double maxStorageUsageValue = resultSet.getDouble("MAXSTORAGEUSAGEVALUE");
          double maxStorageRequestValue = resultSet.getDouble("MAXSTORAGEREQUESTVALUE");

          if (serviceArnToInstanceIds.get(instanceId) != null) {
            serviceArnToInstanceIds.get(instanceId)
                .forEach(instance
                    -> utilizationDataForInstances.put(instance,
                        UtilizationData.builder()
                            .maxCpuUtilization(maxCpuUtilization)
                            .maxMemoryUtilization(maxMemoryUtilization)
                            .avgCpuUtilization(avgCpuUtilization)
                            .avgMemoryUtilization(avgMemoryUtilization)
                            .maxCpuUtilizationValue(maxCpuValue)
                            .avgCpuUtilizationValue(avgCpuValue)
                            .maxMemoryUtilizationValue(maxMemoryValue)
                            .avgMemoryUtilizationValue(avgMemoryValue)
                            .avgStorageCapacityValue(avgStorageCapacityValue)
                            .avgStorageRequestValue(avgStorageRequestValue)
                            .avgStorageUsageValue(avgStorageUsageValue)
                            .maxStorageRequestValue(maxStorageRequestValue)
                            .maxStorageUsageValue(maxStorageUsageValue)
                            .build()));
          }
        }
        return utilizationDataForInstances;
      } catch (SQLException e) {
        log.error("Error while fetching utilization data : exception", e);
      } finally {
        DBUtils.close(resultSet);
      }
    }
    return null;
  }

  private Map<String, List<String>> getServiceArnToInstanceIdMapping(List<? extends InstanceData> instanceDataList) {
    Map<String, List<String>> instanceIds = new HashMap<>();
    instanceDataList.forEach(instanceData -> {
      String instanceId = instanceData.getInstanceId();
      String utilInstanceId = instanceId;
      if (instanceData.getInstanceType() == InstanceType.ECS_TASK_EC2
          || instanceData.getInstanceType() == InstanceType.ECS_TASK_FARGATE) {
        utilInstanceId = getValueForKeyFromInstanceMetaData(InstanceMetaDataConstants.ECS_SERVICE_ARN, instanceData);
      } else if (instanceData.getInstanceType() == InstanceType.ECS_CONTAINER_INSTANCE) {
        utilInstanceId = getValueForKeyFromInstanceMetaData(InstanceMetaDataConstants.CLUSTER_ARN, instanceData);
      } else if (instanceData.getInstanceType() == InstanceType.K8S_PV) {
        utilInstanceId = String.format("%s/%s",
            getValueForKeyFromInstanceMetaData(InstanceMetaDataConstants.CLAIM_NAMESPACE, instanceData),
            getValueForKeyFromInstanceMetaData(InstanceMetaDataConstants.CLAIM_NAME, instanceData));
      }
      instanceIds.computeIfAbsent(utilInstanceId, k -> new ArrayList<>()).add(instanceId);
    });
    return instanceIds;
  }

  public boolean purgeUtilisationData() {
    log.info("Purging old {} data !!", UTILIZATION_DATA_PURGE_QUERY);
    return executeQuery(UTILIZATION_DATA_PURGE_QUERY);
  }

  private boolean executeQuery(String query) {
    boolean result = false;
    if (timeScaleDBService.isValid()) {
      int retryCount = 0;
      while (retryCount < MAX_RETRY_COUNT && !result) {
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

  private String getValueForKeyFromInstanceMetaData(String metaDataKey, InstanceData instanceData) {
    if (null != instanceData.getMetaData() && instanceData.getMetaData().containsKey(metaDataKey)) {
      return instanceData.getMetaData().get(metaDataKey);
    }
    return null;
  }

  private void populateDefaultUtilizationData(
      Map<String, UtilizationData> utilizationDataForInstances, Map<String, List<String>> serviceArnToInstanceIds) {
    if (serviceArnToInstanceIds != null) {
      for (Entry<String, List<String>> entry : serviceArnToInstanceIds.entrySet()) {
        if (entry.getValue() != null) {
          entry.getValue().forEach(instance
              -> utilizationDataForInstances.put(instance,
                  UtilizationData.builder()
                      .maxCpuUtilization(1)
                      .maxMemoryUtilization(1)
                      .avgCpuUtilization(1)
                      .avgMemoryUtilization(1)
                      .build()));
        }
      }
    }
  }
}
