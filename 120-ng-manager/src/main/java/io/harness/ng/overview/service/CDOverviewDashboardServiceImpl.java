/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.overview.service;

import static io.harness.NGDateUtils.DAY_IN_MS;
import static io.harness.NGDateUtils.HOUR_IN_MS;
import static io.harness.NGDateUtils.getNumberOfDays;
import static io.harness.NGDateUtils.getStartTimeOfNextDay;
import static io.harness.NGDateUtils.getStartTimeOfPreviousInterval;
import static io.harness.NGDateUtils.getStartTimeOfTheDayAsEpoch;
import static io.harness.event.timeseries.processor.utils.DateUtils.getCurrentTime;
import static io.harness.ng.core.activityhistory.dto.TimeGroupType.DAY;
import static io.harness.ng.core.activityhistory.dto.TimeGroupType.HOUR;

import io.harness.NGDateUtils;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cd.CDDashboardServiceHelper;
import io.harness.cd.NGPipelineSummaryCDConstants;
import io.harness.cd.NGServiceConstants;
import io.harness.event.timeseries.processor.utils.DateUtils;
import io.harness.exception.UnknownEnumTypeException;
import io.harness.models.EnvBuildInstanceCount;
import io.harness.models.InstanceDetailsByBuildId;
import io.harness.models.constants.TimescaleConstants;
import io.harness.models.dashboard.InstanceCountDetailsByEnvTypeAndServiceId;
import io.harness.models.dashboard.InstanceCountDetailsByEnvTypeBase;
import io.harness.ng.core.activityhistory.dto.TimeGroupType;
import io.harness.ng.core.dashboard.AuthorInfo;
import io.harness.ng.core.dashboard.DashboardExecutionStatusInfo;
import io.harness.ng.core.dashboard.DeploymentsInfo;
import io.harness.ng.core.dashboard.ExecutionStatusInfo;
import io.harness.ng.core.dashboard.GitInfo;
import io.harness.ng.core.dashboard.ServiceDeploymentInfo;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.ng.core.mapper.TagMapper;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.ng.overview.dto.ActiveServiceInstanceSummary;
import io.harness.ng.overview.dto.BuildIdAndInstanceCount;
import io.harness.ng.overview.dto.DashboardWorkloadDeployment;
import io.harness.ng.overview.dto.Deployment;
import io.harness.ng.overview.dto.DeploymentChangeRates;
import io.harness.ng.overview.dto.DeploymentCount;
import io.harness.ng.overview.dto.DeploymentDateAndCount;
import io.harness.ng.overview.dto.DeploymentInfo;
import io.harness.ng.overview.dto.DeploymentStatusInfoList;
import io.harness.ng.overview.dto.EntityStatusDetails;
import io.harness.ng.overview.dto.EnvBuildIdAndInstanceCountInfo;
import io.harness.ng.overview.dto.EnvBuildIdAndInstanceCountInfoList;
import io.harness.ng.overview.dto.EnvIdCountPair;
import io.harness.ng.overview.dto.EnvironmentDeploymentInfo;
import io.harness.ng.overview.dto.EnvironmentInfoByServiceId;
import io.harness.ng.overview.dto.ExecutionDeployment;
import io.harness.ng.overview.dto.ExecutionDeploymentInfo;
import io.harness.ng.overview.dto.HealthDeploymentDashboard;
import io.harness.ng.overview.dto.HealthDeploymentInfo;
import io.harness.ng.overview.dto.InstancesByBuildIdList;
import io.harness.ng.overview.dto.LastWorkloadInfo;
import io.harness.ng.overview.dto.ServiceDeployment;
import io.harness.ng.overview.dto.ServiceDeploymentInfoDTO;
import io.harness.ng.overview.dto.ServiceDeploymentListInfo;
import io.harness.ng.overview.dto.ServiceDetailsDTO;
import io.harness.ng.overview.dto.ServiceDetailsDTO.ServiceDetailsDTOBuilder;
import io.harness.ng.overview.dto.ServiceDetailsInfoDTO;
import io.harness.ng.overview.dto.ServiceHeaderInfo;
import io.harness.ng.overview.dto.ServicePipelineInfo;
import io.harness.ng.overview.dto.TimeAndStatusDeployment;
import io.harness.ng.overview.dto.TimeValuePair;
import io.harness.ng.overview.dto.TimeValuePairListDTO;
import io.harness.ng.overview.dto.TotalDeploymentInfo;
import io.harness.ng.overview.dto.WorkloadCountInfo;
import io.harness.ng.overview.dto.WorkloadDateCountInfo;
import io.harness.ng.overview.dto.WorkloadDeploymentInfo;
import io.harness.ng.overview.util.GrowthTrendEvaluator;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.service.instancedashboardservice.InstanceDashboardService;
import io.harness.timescaledb.DBUtils;
import io.harness.timescaledb.TimeScaleDBService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(HarnessTeam.CDC)
@Singleton
@Slf4j
public class CDOverviewDashboardServiceImpl implements CDOverviewDashboardService {
  @Inject TimeScaleDBService timeScaleDBService;
  @Inject ServiceEntityService serviceEntityService;
  @Inject InstanceDashboardService instanceDashboardService;
  @Inject ServiceEntityService ServiceEntityServiceImpl;

  private String tableNameCD = "pipeline_execution_summary_cd";
  private String tableNameServiceAndInfra = "service_infra_info";
  public static List<String> activeStatusList = Arrays.asList(ExecutionStatus.RUNNING.name(),
      ExecutionStatus.ASYNCWAITING.name(), ExecutionStatus.TASKWAITING.name(), ExecutionStatus.TIMEDWAITING.name(),
      ExecutionStatus.PAUSED.name(), ExecutionStatus.PAUSING.name());
  private List<String> pendingStatusList = Arrays.asList(ExecutionStatus.INTERVENTIONWAITING.name(),
      ExecutionStatus.APPROVALWAITING.name(), ExecutionStatus.WAITING.name(), ExecutionStatus.RESOURCEWAITING.name());
  private static final int MAX_RETRY_COUNT = 5;
  public static final double INVALID_CHANGE_RATE = -10000;

  public String executionStatusCdTimeScaleColumns() {
    return "id,"
        + "name,"
        + "pipelineidentifier,"
        + "startts,"
        + "endTs,"
        + "status,"
        + "planexecutionid,"
        + "moduleinfo_branch_name,"
        + "source_branch,"
        + "moduleinfo_branch_commit_message,"
        + "moduleinfo_branch_commit_id,"
        + "moduleinfo_event,"
        + "moduleinfo_repository,"
        + "trigger_type,"
        + "moduleinfo_author_id,"
        + "author_avatar";
  }
  public String queryBuilderSelectStatusTime(
      String accountId, String orgId, String projectId, long startInterval, long endInterval) {
    String selectStatusQuery = "select status,startts from " + tableNameCD + " where ";
    StringBuilder totalBuildSqlBuilder = new StringBuilder();
    totalBuildSqlBuilder.append(selectStatusQuery);

    if (accountId != null) {
      totalBuildSqlBuilder.append(String.format("accountid='%s' and ", accountId));
    }

    if (orgId != null) {
      totalBuildSqlBuilder.append(String.format("orgidentifier='%s' and ", orgId));
    }

    if (projectId != null) {
      totalBuildSqlBuilder.append(String.format("projectidentifier='%s' and ", projectId));
    }

    if (startInterval > 0 && endInterval > 0) {
      totalBuildSqlBuilder.append(
          String.format("startts is not null and startts>=%s and startts<%s;", startInterval, endInterval));
    }

    return totalBuildSqlBuilder.toString();
  }

  public String queryBuilderSelectIdCdTable(
      String accountId, String orgId, String projectId, long startInterval, long endInterval) {
    String selectStatusQuery = "select id from " + tableNameCD + " where ";
    StringBuilder totalBuildSqlBuilder = new StringBuilder();
    totalBuildSqlBuilder.append(selectStatusQuery);

    if (accountId != null) {
      totalBuildSqlBuilder.append(String.format("accountid='%s' and ", accountId));
    }

    if (orgId != null) {
      totalBuildSqlBuilder.append(String.format("orgidentifier='%s' and ", orgId));
    }

    if (projectId != null) {
      totalBuildSqlBuilder.append(String.format("projectidentifier='%s' and ", projectId));
    }

    if (startInterval > 0 && endInterval > 0) {
      totalBuildSqlBuilder.append(
          String.format("startts is not null and startts>=%s and startts<%s;", startInterval, endInterval));
    }

    return totalBuildSqlBuilder.toString();
  }

  public String queryBuilderSelectIdLimitTimeCdTable(
      String accountId, String orgId, String projectId, long days, List<String> statusList) {
    String selectStatusQuery = "select id from " + tableNameCD + " where ";
    StringBuilder totalBuildSqlBuilder = new StringBuilder();
    totalBuildSqlBuilder.append(selectStatusQuery);

    if (accountId != null) {
      totalBuildSqlBuilder.append(String.format("accountid='%s' and ", accountId));
    }

    if (orgId != null) {
      totalBuildSqlBuilder.append(String.format("orgidentifier='%s' and ", orgId));
    }

    if (projectId != null) {
      totalBuildSqlBuilder.append(String.format("projectidentifier='%s' and ", projectId));
    }

    totalBuildSqlBuilder.append("status in (");
    for (String status : statusList) {
      totalBuildSqlBuilder.append(String.format("'%s',", status));
    }

    totalBuildSqlBuilder.deleteCharAt(totalBuildSqlBuilder.length() - 1);

    totalBuildSqlBuilder.append(String.format(") and startts is not null ORDER BY startts DESC LIMIT %s", days));

    return totalBuildSqlBuilder.toString();
  }

  public String queryBuilderEnvironmentType(
      String accountId, String orgId, String projectId, long startInterval, long endInterval) {
    String selectStatusQuery = "select env_type from " + tableNameServiceAndInfra + " where ";
    StringBuilder totalBuildSqlBuilder = new StringBuilder();
    totalBuildSqlBuilder.append(selectStatusQuery);

    if (startInterval > 0 && endInterval > 0) {
      String idQuery = queryBuilderSelectIdCdTable(accountId, orgId, projectId, startInterval, endInterval);
      idQuery = idQuery.replace(';', ' ');
      totalBuildSqlBuilder.append(
          String.format("pipeline_execution_summary_cd_id in (%s) and env_type is not null;", idQuery));
    }

    return totalBuildSqlBuilder.toString();
  }
  public double getRate(long current, long previous) {
    double rate = 0.0;
    if (previous != 0) {
      rate = (current - previous) / (double) previous;
    }
    rate = rate * 100.0;
    return rate;
  }

  public String queryBuilderStatus(
      String accountId, String orgId, String projectId, long days, List<String> statusList) {
    String selectStatusQuery = "select " + executionStatusCdTimeScaleColumns() + " from " + tableNameCD + " where ";
    StringBuilder totalBuildSqlBuilder = new StringBuilder();
    totalBuildSqlBuilder.append(selectStatusQuery);

    if (accountId != null) {
      totalBuildSqlBuilder.append(String.format("accountid='%s' and ", accountId));
    }

    if (orgId != null) {
      totalBuildSqlBuilder.append(String.format("orgidentifier='%s' and ", orgId));
    }

    if (projectId != null) {
      totalBuildSqlBuilder.append(String.format("projectidentifier='%s' and ", projectId));
    }

    totalBuildSqlBuilder.append("status in (");
    for (String status : statusList) {
      totalBuildSqlBuilder.append(String.format("'%s',", status));
    }

    totalBuildSqlBuilder.deleteCharAt(totalBuildSqlBuilder.length() - 1);

    totalBuildSqlBuilder.append(String.format(") and startts is not null ORDER BY startts DESC LIMIT %s;", days));

    return totalBuildSqlBuilder.toString();
  }

  public String queryBuilderServiceTag(String queryIdCdTable) {
    String selectStatusQuery =
        "select service_name,tag,pipeline_execution_summary_cd_id from " + tableNameServiceAndInfra + " where ";
    StringBuilder totalBuildSqlBuilder = new StringBuilder(20480);

    totalBuildSqlBuilder.append(String.format(
        selectStatusQuery + "pipeline_execution_summary_cd_id in (%s) and service_name is not null;", queryIdCdTable));

    return totalBuildSqlBuilder.toString();
  }

  public String queryBuilderSelectWorkload(String accountId, String orgId, String projectId, long previousStartInterval,
      long endInterval, EnvironmentType envType) {
    String selectStatusQuery =
        "select service_name,service_id,service_status as status,service_startts as startts,service_endts as endts,deployment_type, pipeline_execution_summary_cd_id from "
        + tableNameServiceAndInfra + " where ";
    StringBuilder totalBuildSqlBuilder = new StringBuilder();
    totalBuildSqlBuilder.append(selectStatusQuery);

    if (previousStartInterval > 0 && endInterval > 0) {
      String idQuery = queryBuilderSelectIdCdTable(accountId, orgId, projectId, previousStartInterval, endInterval);
      idQuery = idQuery.replace(';', ' ');

      if (envType != null) {
        totalBuildSqlBuilder.append(String.format("env_Type='%s' and ", envType.toString()));
      }

      totalBuildSqlBuilder.append(String.format(
          "pipeline_execution_summary_cd_id in (%s) and service_name is not null and service_id is not null;",
          idQuery));
    }

    return totalBuildSqlBuilder.toString();
  }

  public TimeAndStatusDeployment queryCalculatorTimeAndStatus(String query) {
    List<Long> time = new ArrayList<>();
    List<String> status = new ArrayList<>();

    int totalTries = 0;
    boolean successfulOperation = false;
    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      ResultSet resultSet = null;
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(query)) {
        resultSet = statement.executeQuery();
        while (resultSet != null && resultSet.next()) {
          status.add(resultSet.getString("status"));
          time.add(Long.valueOf(resultSet.getString("startts")));
        }
        successfulOperation = true;
      } catch (SQLException ex) {
        totalTries++;
      } finally {
        DBUtils.close(resultSet);
      }
    }

    return TimeAndStatusDeployment.builder().status(status).time(time).build();
  }

  public List<String> queryCalculatorEnvType(String queryEnvironmentType) {
    List<String> envType = new ArrayList<>();

    int totalTries = 0;
    boolean successfulOperation = false;
    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      ResultSet resultSet = null;
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(queryEnvironmentType)) {
        resultSet = statement.executeQuery();
        while (resultSet != null && resultSet.next()) {
          envType.add(resultSet.getString("env_type"));
        }
        successfulOperation = true;
      } catch (SQLException ex) {
        totalTries++;
      } finally {
        DBUtils.close(resultSet);
      }
    }
    return envType;
  }

  @Override
  public io.harness.ng.overview.dto.HealthDeploymentDashboard getHealthDeploymentDashboard(String accountId,
      String orgId, String projectId, long startInterval, long endInterval, long previousStartInterval) {
    endInterval = endInterval + getTimeUnitToGroupBy(DAY);
    String query = queryBuilderSelectStatusTime(accountId, orgId, projectId, previousStartInterval, endInterval);

    List<Long> time = new ArrayList<>();
    List<String> status = new ArrayList<>();
    List<String> envType = new ArrayList<>();

    TimeAndStatusDeployment timeAndStatusDeployment = queryCalculatorTimeAndStatus(query);
    time = timeAndStatusDeployment.getTime();
    status = timeAndStatusDeployment.getStatus();

    long total = 0;
    long currentSuccess = 0;
    long currentFailed = 0;
    long previousSuccess = 0;
    long previousFailed = 0;

    HashMap<Long, Integer> totalCountMap = new HashMap<>();
    HashMap<Long, Integer> successCountMap = new HashMap<>();
    HashMap<Long, Integer> failedCountMap = new HashMap<>();

    long startDateCopy = startInterval;
    long endDateCopy = endInterval;

    long timeUnitPerDay = getTimeUnitToGroupBy(DAY);
    while (startDateCopy < endDateCopy) {
      totalCountMap.put(startDateCopy, 0);
      successCountMap.put(startDateCopy, 0);
      failedCountMap.put(startDateCopy, 0);
      startDateCopy = startDateCopy + timeUnitPerDay;
    }

    for (int i = 0; i < time.size(); i++) {
      long currentTimeEpoch = time.get(i);
      if (currentTimeEpoch >= startInterval && currentTimeEpoch < endInterval) {
        currentTimeEpoch = getStartingDateEpochValue(currentTimeEpoch, startInterval);
        total++;
        totalCountMap.put(currentTimeEpoch, totalCountMap.get(currentTimeEpoch) + 1);
        if (status.get(i).contentEquals(ExecutionStatus.SUCCESS.name())) {
          currentSuccess++;
          successCountMap.put(currentTimeEpoch, successCountMap.get(currentTimeEpoch) + 1);
        } else if (CDDashboardServiceHelper.failedStatusList.contains(status.get(i))) {
          currentFailed++;
          failedCountMap.put(currentTimeEpoch, failedCountMap.get(currentTimeEpoch) + 1);
        }
      } else {
        if (status.get(i).contentEquals(ExecutionStatus.SUCCESS.name())) {
          previousSuccess++;
        } else if (CDDashboardServiceHelper.failedStatusList.contains(status.get(i))) {
          previousFailed++;
        }
      }
    }

    String queryEnvironmentType = queryBuilderEnvironmentType(accountId, orgId, projectId, startInterval, endInterval);
    envType = queryCalculatorEnvType(queryEnvironmentType);

    long production = Collections.frequency(envType, EnvironmentType.Production.name());
    long nonProduction = Collections.frequency(envType, EnvironmentType.PreProduction.name());

    List<DeploymentDateAndCount> totalDateAndCount = new ArrayList<>();
    List<DeploymentDateAndCount> successDateAndCount = new ArrayList<>();
    List<DeploymentDateAndCount> failedDateAndCount = new ArrayList<>();
    startDateCopy = startInterval;
    endDateCopy = endInterval;

    while (startDateCopy < endDateCopy) {
      totalDateAndCount.add(DeploymentDateAndCount.builder()
                                .time(startDateCopy)
                                .deployments(Deployment.builder().count(totalCountMap.get(startDateCopy)).build())
                                .build());
      successDateAndCount.add(DeploymentDateAndCount.builder()
                                  .time(startDateCopy)
                                  .deployments(Deployment.builder().count(successCountMap.get(startDateCopy)).build())
                                  .build());
      failedDateAndCount.add(DeploymentDateAndCount.builder()
                                 .time(startDateCopy)
                                 .deployments(Deployment.builder().count(failedCountMap.get(startDateCopy)).build())
                                 .build());
      startDateCopy = startDateCopy + timeUnitPerDay;
    }

    return HealthDeploymentDashboard.builder()
        .healthDeploymentInfo(HealthDeploymentInfo.builder()
                                  .total(TotalDeploymentInfo.builder()
                                             .count(total)
                                             .production(production)
                                             .nonProduction(nonProduction)
                                             .countList(totalDateAndCount)
                                             .build())
                                  .success(DeploymentInfo.builder()
                                               .count(currentSuccess)
                                               .rate(getRate(currentSuccess, previousSuccess))
                                               .countList(successDateAndCount)
                                               .build())
                                  .failure(DeploymentInfo.builder()
                                               .count(currentFailed)
                                               .rate(getRate(currentFailed, previousFailed))
                                               .countList(failedDateAndCount)
                                               .build())
                                  .build())
        .build();
  }

  private io.harness.ng.overview.dto.ExecutionDeployment getExecutionDeployment(
      Long time, long total, long success, long failed) {
    return io.harness.ng.overview.dto.ExecutionDeployment.builder()
        .time(time)
        .deployments(
            io.harness.ng.overview.dto.DeploymentCount.builder().total(total).success(success).failure(failed).build())
        .build();
  }

  public HashMap<String, List<ServiceDeploymentInfo>> queryCalculatorServiceTagMag(String queryServiceTag) {
    HashMap<String, List<ServiceDeploymentInfo>> serviceTagMap = new HashMap<>();

    int totalTries = 0;
    boolean successfulOperation = false;
    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      ResultSet resultSet = null;
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(queryServiceTag)) {
        resultSet = statement.executeQuery();
        while (resultSet != null && resultSet.next()) {
          String pipeline_execution_summary_cd_id = resultSet.getString("pipeline_execution_summary_cd_id");
          String service_name = resultSet.getString("service_name");
          String tag = resultSet.getString("tag");
          if (serviceTagMap.containsKey(pipeline_execution_summary_cd_id)) {
            serviceTagMap.get(pipeline_execution_summary_cd_id).add(getServiceDeployment(service_name, tag));
          } else {
            List<ServiceDeploymentInfo> serviceDeploymentInfos = new ArrayList<>();
            serviceDeploymentInfos.add(getServiceDeployment(service_name, tag));
            serviceTagMap.put(pipeline_execution_summary_cd_id, serviceDeploymentInfos);
          }
        }
        successfulOperation = true;
      } catch (SQLException ex) {
        totalTries++;
      } finally {
        DBUtils.close(resultSet);
      }
    }
    return serviceTagMap;
  }

  @Override
  public io.harness.ng.overview.dto.ExecutionDeploymentInfo getExecutionDeploymentDashboard(
      String accountId, String orgId, String projectId, long startInterval, long endInterval) {
    endInterval = endInterval + DAY_IN_MS;
    String query = queryBuilderSelectStatusTime(accountId, orgId, projectId, startInterval, endInterval);

    HashMap<Long, Integer> totalCountMap = new HashMap<>();
    HashMap<Long, Integer> successCountMap = new HashMap<>();
    HashMap<Long, Integer> failedCountMap = new HashMap<>();

    long startDateCopy = startInterval;
    long endDateCopy = endInterval;

    long timeUnitPerDay = getTimeUnitToGroupBy(DAY);
    while (startDateCopy < endDateCopy) {
      totalCountMap.put(startDateCopy, 0);
      successCountMap.put(startDateCopy, 0);
      failedCountMap.put(startDateCopy, 0);
      startDateCopy = startDateCopy + timeUnitPerDay;
    }

    TimeAndStatusDeployment timeAndStatusDeployment = queryCalculatorTimeAndStatus(query);
    List<Long> time = timeAndStatusDeployment.getTime();
    List<String> status = timeAndStatusDeployment.getStatus();

    List<ExecutionDeployment> executionDeployments = new ArrayList<>();

    for (int i = 0; i < time.size(); i++) {
      long currentTimeEpoch = time.get(i);
      currentTimeEpoch = getStartingDateEpochValue(currentTimeEpoch, startInterval);
      totalCountMap.put(currentTimeEpoch, totalCountMap.get(currentTimeEpoch) + 1);
      if (status.get(i).contentEquals(ExecutionStatus.SUCCESS.name())) {
        successCountMap.put(currentTimeEpoch, successCountMap.get(currentTimeEpoch) + 1);
      } else if (CDDashboardServiceHelper.failedStatusList.contains(status.get(i))) {
        failedCountMap.put(currentTimeEpoch, failedCountMap.get(currentTimeEpoch) + 1);
      }
    }

    startDateCopy = startInterval;
    endDateCopy = endInterval;

    while (startDateCopy < endDateCopy) {
      executionDeployments.add(getExecutionDeployment(startDateCopy, totalCountMap.get(startDateCopy),
          successCountMap.get(startDateCopy), failedCountMap.get(startDateCopy)));
      startDateCopy = startDateCopy + timeUnitPerDay;
    }
    return ExecutionDeploymentInfo.builder().executionDeploymentList(executionDeployments).build();
  }

  private Map<String, String> getLastPipeline(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, List<String> serviceIds) {
    Map<String, String> serviceIdToPipelineId = new HashMap<>();

    String query = "select distinct on(service_id) service_id, pipeline_execution_summary_cd_id, service_startts from "
        + "service_infra_info where accountid=? and orgidentifier=? and projectidentifier=? and service_id = any (?) "
        + "order by service_id, service_startts desc";

    int totalTries = 0;
    boolean successfulOperation = false;
    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      ResultSet resultSet = null;
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(query)) {
        statement.setString(1, accountIdentifier);
        statement.setString(2, orgIdentifier);
        statement.setString(3, projectIdentifier);
        statement.setArray(4, connection.createArrayOf("VARCHAR", serviceIds.toArray()));
        resultSet = statement.executeQuery();
        while (resultSet != null && resultSet.next()) {
          String service_id = resultSet.getString("service_id");
          String pipeline_execution_summary_cd_id = resultSet.getString("pipeline_execution_summary_cd_id");
          serviceIdToPipelineId.putIfAbsent(service_id, pipeline_execution_summary_cd_id);
        }
        successfulOperation = true;
      } catch (SQLException ex) {
        totalTries++;
      } finally {
        DBUtils.close(resultSet);
      }
    }

    return serviceIdToPipelineId;
  }

  private Map<String, Set<String>> getDeploymentType(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, List<String> serviceIds) {
    Map<String, Set<String>> serviceIdToDeploymentType = new HashMap<>();

    String query = "select service_id, deployment_type from service_infra_info where accountid=? and orgidentifier=? "
        + "and projectidentifier=? and service_id = any (?) group by service_id, deployment_type";

    int totalTries = 0;
    boolean successfulOperation = false;
    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      ResultSet resultSet = null;
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(query)) {
        statement.setString(1, accountIdentifier);
        statement.setString(2, orgIdentifier);
        statement.setString(3, projectIdentifier);
        statement.setArray(4, connection.createArrayOf("VARCHAR", serviceIds.toArray()));
        resultSet = statement.executeQuery();
        while (resultSet != null && resultSet.next()) {
          String service_id = resultSet.getString("service_id");
          String deployment_type = resultSet.getString("deployment_type");
          serviceIdToDeploymentType.putIfAbsent(service_id, new HashSet<>());
          serviceIdToDeploymentType.get(service_id).add(deployment_type);
        }
        successfulOperation = true;
      } catch (SQLException ex) {
        totalTries++;
      } finally {
        DBUtils.close(resultSet);
      }
    }

    return serviceIdToDeploymentType;
  }

  @Override
  public ServiceDetailsInfoDTO getServiceDetailsList(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, long startTime, long endTime) throws Exception {
    startTime = getStartTimeOfTheDayAsEpoch(startTime);
    endTime = getStartTimeOfNextDay(endTime);

    long numberOfDays = getNumberOfDays(startTime, endTime);
    if (numberOfDays < 0) {
      throw new Exception("start date should be less than or equal to end date");
    }
    long previousStartTime = getStartTimeOfPreviousInterval(startTime, numberOfDays);

    List<ServiceEntity> services =
        ServiceEntityServiceImpl.getAllNonDeletedServices(accountIdentifier, orgIdentifier, projectIdentifier);

    List<WorkloadDeploymentInfo> workloadDeploymentInfoList = getDashboardWorkloadDeployment(
        accountIdentifier, orgIdentifier, projectIdentifier, startTime, endTime, previousStartTime, null)
                                                                  .getWorkloadDeploymentInfoList();
    Map<String, WorkloadDeploymentInfo> serviceIdToWorkloadDeploymentInfo = new HashMap<>();
    workloadDeploymentInfoList.forEach(
        item -> serviceIdToWorkloadDeploymentInfo.putIfAbsent(item.getServiceId(), item));

    List<String> serviceIdentifiers = services.stream().map(ServiceEntity::getIdentifier).collect(Collectors.toList());

    Map<String, String> serviceIdToPipelineIdMap =
        getLastPipeline(accountIdentifier, orgIdentifier, projectIdentifier, serviceIdentifiers);

    List<String> pipelineExecutionIdList = serviceIdToPipelineIdMap.values().stream().collect(Collectors.toList());

    // Gets all the details for the pipeline execution id's in the list and stores it in a map.
    Map<String, ServicePipelineInfo> pipelineExecutionDetailsMap = getPipelineExecutionDetails(pipelineExecutionIdList);

    Map<String, Set<String>> serviceIdToDeploymentTypeMap =
        getDeploymentType(accountIdentifier, orgIdentifier, projectIdentifier, serviceIdentifiers);

    Map<String, InstanceCountDetailsByEnvTypeBase> serviceIdToInstanceCountDetails =
        instanceDashboardService
            .getActiveServiceInstanceCountBreakdown(
                accountIdentifier, orgIdentifier, projectIdentifier, serviceIdentifiers, getCurrentTime())
            .getInstanceCountDetailsByEnvTypeBaseMap();

    List<ServiceDetailsDTO> serviceDeploymentInfoList =
        services.stream()
            .map(service -> {
              final String serviceId = service.getIdentifier();
              final String pipelineId = serviceIdToPipelineIdMap.getOrDefault(serviceId, null);

              ServiceDetailsDTOBuilder serviceDetailsDTOBuilder = ServiceDetailsDTO.builder();
              serviceDetailsDTOBuilder.serviceName(service.getName());
              serviceDetailsDTOBuilder.description(service.getDescription());
              serviceDetailsDTOBuilder.tags(TagMapper.convertToMap(service.getTags()));
              serviceDetailsDTOBuilder.serviceIdentifier(serviceId);
              serviceDetailsDTOBuilder.deploymentTypeList(serviceIdToDeploymentTypeMap.getOrDefault(serviceId, null));
              serviceDetailsDTOBuilder.instanceCountDetails(
                  serviceIdToInstanceCountDetails.getOrDefault(serviceId, null));
              serviceDetailsDTOBuilder.lastPipelineExecuted(pipelineExecutionDetailsMap.getOrDefault(pipelineId, null));

              if (serviceIdToWorkloadDeploymentInfo.containsKey(serviceId)) {
                final WorkloadDeploymentInfo workloadDeploymentInfo = serviceIdToWorkloadDeploymentInfo.get(serviceId);
                serviceDetailsDTOBuilder.totalDeployments(workloadDeploymentInfo.getTotalDeployments());
                serviceDetailsDTOBuilder.totalDeploymentChangeRate(
                    workloadDeploymentInfo.getTotalDeploymentChangeRate());
                serviceDetailsDTOBuilder.successRate(workloadDeploymentInfo.getPercentSuccess());
                serviceDetailsDTOBuilder.successRateChangeRate(workloadDeploymentInfo.getRateSuccess());
                serviceDetailsDTOBuilder.failureRate(workloadDeploymentInfo.getFailureRate());
                serviceDetailsDTOBuilder.failureRateChangeRate(workloadDeploymentInfo.getFailureRateChangeRate());
                serviceDetailsDTOBuilder.frequency(workloadDeploymentInfo.getFrequency());
                serviceDetailsDTOBuilder.frequencyChangeRate(workloadDeploymentInfo.getFrequencyChangeRate());
              }

              return serviceDetailsDTOBuilder.build();
            })
            .collect(Collectors.toList());

    return ServiceDetailsInfoDTO.builder().serviceDeploymentDetailsList(serviceDeploymentInfoList).build();
  }

  private Map<String, ServicePipelineInfo> getPipelineExecutionDetails(List<String> pipelineExecutionIdList) {
    Map<String, ServicePipelineInfo> pipelineExecutionDetailsMap = new HashMap<>();
    int totalTries = 0;
    boolean successfulOperation = false;
    String sql = "select * from " + tableNameCD + " where id = any (?);";
    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      ResultSet resultSet = null;
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(sql)) {
        final Array array = connection.createArrayOf("VARCHAR", pipelineExecutionIdList.toArray());
        statement.setArray(1, array);
        resultSet = statement.executeQuery();
        while (resultSet != null && resultSet.next()) {
          String pipelineExecutionId = resultSet.getString(NGPipelineSummaryCDConstants.ID);
          String pipelineName = resultSet.getString(NGPipelineSummaryCDConstants.NAME);
          String pipelineId = resultSet.getString(NGPipelineSummaryCDConstants.PIPELINE_IDENTIFIER);
          String status = resultSet.getString(NGPipelineSummaryCDConstants.STATUS);
          long executionTime = Long.parseLong(resultSet.getString(NGPipelineSummaryCDConstants.START_TS));
          if (!pipelineExecutionDetailsMap.containsKey(pipelineExecutionId)) {
            pipelineExecutionDetailsMap.put(pipelineExecutionId,
                ServicePipelineInfo.builder()
                    .identifier(pipelineId)
                    .pipelineExecutionId(pipelineExecutionId)
                    .name(pipelineName)
                    .lastExecutedAt(executionTime)
                    .status(status)
                    .build());
          }
        }
        successfulOperation = true;
      } catch (SQLException ex) {
        log.error("%s after total tries = %s", ex, totalTries);
        totalTries++;
      } finally {
        DBUtils.close(resultSet);
      }
    }
    return pipelineExecutionDetailsMap;
  }

  @Override
  public ServiceDeploymentInfoDTO getServiceDeployments(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, long startTime, long endTime, String serviceIdentifier, long bucketSizeInDays) {
    String query = queryBuilderServiceDeployments(
        accountIdentifier, orgIdentifier, projectIdentifier, startTime, endTime, bucketSizeInDays, serviceIdentifier);

    /**
     * Map that stores service deployment data for a bucket time - starting time of a
     * dateCDOverviewDashboardServiceImpl.java
     */
    Map<Long, io.harness.ng.overview.dto.ServiceDeployment> resultMap = new HashMap<>();
    long startTimeCopy = startTime;

    initializeResultMap(resultMap, startTimeCopy, endTime, bucketSizeInDays);

    int totalTries = 0;
    boolean successfulOperation = false;
    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      ResultSet resultSet = null;
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(query)) {
        resultSet = statement.executeQuery();
        while (resultSet != null && resultSet.next()) {
          String status = resultSet.getString(NGServiceConstants.STATUS);
          long bucketTime = Long.parseLong(resultSet.getString(NGServiceConstants.TIME_ENTITY));
          long numberOfRecords = resultSet.getLong(NGServiceConstants.NUMBER_OF_RECORDS);
          io.harness.ng.overview.dto.ServiceDeployment serviceDeployment = resultMap.get(bucketTime);
          io.harness.ng.overview.dto.DeploymentCount deployments = serviceDeployment.getDeployments();
          deployments.setTotal(deployments.getTotal() + numberOfRecords);
          if ((ExecutionStatus.SUCCESS).toString().equals(status)) {
            deployments.setSuccess(deployments.getSuccess() + numberOfRecords);
          } else if (CDDashboardServiceHelper.failedStatusList.contains(status)) {
            deployments.setFailure(deployments.getFailure() + numberOfRecords);
          }
        }
        successfulOperation = true;
      } catch (SQLException ex) {
        log.error("%s after total tries = %s", ex, totalTries);
        totalTries++;
      } finally {
        DBUtils.close(resultSet);
      }
    }
    List<io.harness.ng.overview.dto.ServiceDeployment> serviceDeploymentList =
        resultMap.values().stream().collect(Collectors.toList());
    return ServiceDeploymentInfoDTO.builder().serviceDeploymentList(serviceDeploymentList).build();
  }

  private void initializeResultMap(Map<Long, io.harness.ng.overview.dto.ServiceDeployment> resultMap, long startTime,
      long endTime, long bucketSizeInDays) {
    long bucketSizeInMS = bucketSizeInDays * DAY_IN_MS;
    while (startTime < endTime) {
      resultMap.put(startTime,
          io.harness.ng.overview.dto.ServiceDeployment.builder()
              .time(startTime)
              .deployments(io.harness.ng.overview.dto.DeploymentCount.builder().total(0).failure(0).success(0).build())
              .rate(DeploymentChangeRates.builder()
                        .frequency(0)
                        .frequencyChangeRate(0)
                        .failureRate(0)
                        .failureRateChangeRate(0)
                        .build())
              .build());
      startTime = startTime + bucketSizeInMS;
    }
  }

  /**
   * select status, time_entity, count(*) as records from (
   *    select service_status as status, service_startts as
   *    execution_time, time_bucket_gapfill(86400000, service_startts, 1638403200000, 1654128000000) as time_entity,
   *    pipeline_execution_summary_cd_id  from
   *    service_infra_info as sii,
   *    pipeline_execution_summary_cd as pesi
   *    where pesi.accountid='ZVJHx0NyT9SciszZ0JQtFQ' and pesi.orgidentifier='PX' and
   *    pesi.projectidentifier='horizonttdmetricscollector' and service_id='horzondeploymentmetrics'
   *    and pesi.id=sii.pipeline_execution_summary_cd_id
   *    and sii. service_startts >= 1652054400000 and sii.service_startts < 1654646400000
   *    ) as service where status != ''
   *    group by status, time_entity;
   * @param accountIdentifier
   * @param orgIdentifier
   * @param projectIdentifier
   * @param startTime
   * @param endTime
   * @param bucketSizeInDays
   * @param serviceIdentifier
   * @return
   */
  public String queryBuilderServiceDeployments(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      long startTime, long endTime, long bucketSizeInDays, String serviceIdentifier) {
    long bucketSizeInMS = bucketSizeInDays * DAY_IN_MS;
    StringBuilder totalBuildSqlBuilder = new StringBuilder();
    String selectQuery =
        "select status, time_entity, COUNT(*) as numberOfRecords from (select service_status as status, service_startts as execution_time, ";
    totalBuildSqlBuilder.append(selectQuery)
        .append(String.format(
            "time_bucket_gapfill(%s, service_startts, %s, %s) as time_entity, pipeline_execution_summary_cd_id  from service_infra_info as sii, pipeline_execution_summary_cd as pesi where ",
            bucketSizeInMS, startTime, endTime));
    if (accountIdentifier != null) {
      totalBuildSqlBuilder.append(String.format("pesi.accountid='%s'", accountIdentifier));
    }

    if (orgIdentifier != null) {
      totalBuildSqlBuilder.append(String.format(" and pesi.orgidentifier='%s'", orgIdentifier));
    }

    if (projectIdentifier != null) {
      totalBuildSqlBuilder.append(String.format(" and pesi.projectidentifier='%s'", projectIdentifier));
    }

    if (serviceIdentifier != null) {
      totalBuildSqlBuilder.append(String.format(" and service_id='%s'", serviceIdentifier));
    }

    totalBuildSqlBuilder.append(String.format(
        " and pesi.id=sii.pipeline_execution_summary_cd_id and sii.service_startts>=%s and sii.service_startts<%s) as "
            + "service where status != '' group by status, time_entity;",
        startTime, endTime));

    return totalBuildSqlBuilder.toString();
  }

  private static void validateBucketSize(long numberOfDays, long bucketSizeInDays) throws Exception {
    if (numberOfDays < bucketSizeInDays) {
      throw new Exception("Bucket size should be less than the number of days in the selected time range");
    }
  }

  private void calculateRates(List<io.harness.ng.overview.dto.ServiceDeployment> serviceDeployments) {
    serviceDeployments.sort(Comparator.comparingLong(io.harness.ng.overview.dto.ServiceDeployment::getTime));

    double prevFrequency = 0, prevFailureRate = 0;
    for (int i = 0; i < serviceDeployments.size(); i++) {
      io.harness.ng.overview.dto.DeploymentCount deployments = serviceDeployments.get(i).getDeployments();
      DeploymentChangeRates rates = serviceDeployments.get(i).getRate();

      double currFrequency = deployments.getTotal();
      rates.setFrequency(currFrequency);
      rates.setFrequencyChangeRate(calculateChangeRate(prevFrequency, currFrequency));
      prevFrequency = currFrequency;

      double failureRate = deployments.getFailure() * 100;
      if (deployments.getTotal() != 0) {
        failureRate = failureRate / deployments.getTotal();
      }
      rates.setFailureRate(failureRate);
      rates.setFailureRateChangeRate(calculateChangeRate(prevFailureRate, failureRate));
      prevFailureRate = failureRate;
    }
  }

  @Override
  public io.harness.ng.overview.dto.ServiceDeploymentListInfo getServiceDeploymentsInfo(String accountIdentifier,
      String orgIdentifier, String projectIdentifier, long startTime, long endTime, String serviceIdentifier,
      long bucketSizeInDays) throws Exception {
    startTime = getStartTimeOfTheDayAsEpoch(startTime);
    endTime = getStartTimeOfNextDay(endTime);
    long numberOfDays = getNumberOfDays(startTime, endTime);
    validateBucketSize(numberOfDays, bucketSizeInDays);
    long prevStartTime = getStartTimeOfPreviousInterval(startTime, numberOfDays);

    ServiceDeploymentInfoDTO serviceDeployments = getServiceDeployments(
        accountIdentifier, orgIdentifier, projectIdentifier, startTime, endTime, serviceIdentifier, bucketSizeInDays);
    List<io.harness.ng.overview.dto.ServiceDeployment> serviceDeploymentList =
        serviceDeployments.getServiceDeploymentList();

    ServiceDeploymentInfoDTO prevServiceDeployment = getServiceDeployments(accountIdentifier, orgIdentifier,
        projectIdentifier, prevStartTime, startTime, serviceIdentifier, bucketSizeInDays);
    List<io.harness.ng.overview.dto.ServiceDeployment> prevServiceDeploymentList =
        prevServiceDeployment.getServiceDeploymentList();

    long totalDeployments = getTotalDeployments(serviceDeploymentList);
    long prevTotalDeployments = getTotalDeployments(prevServiceDeploymentList);
    double failureRate = getFailureRate(serviceDeploymentList);
    double frequency = totalDeployments / (double) numberOfDays;
    double prevFrequency = prevTotalDeployments / (double) numberOfDays;

    double totalDeploymentChangeRate = calculateChangeRate(prevTotalDeployments, totalDeployments);
    double failureRateChangeRate = getFailureRateChangeRate(serviceDeploymentList, prevServiceDeploymentList);
    double frequencyChangeRate = calculateChangeRate(prevFrequency, frequency);

    calculateRates(serviceDeploymentList);

    return ServiceDeploymentListInfo.builder()
        .startTime(startTime)
        .endTime(endTime == -1 ? null : endTime)
        .totalDeployments(totalDeployments)
        .failureRate(failureRate)
        .frequency(frequency)
        .totalDeploymentsChangeRate(totalDeploymentChangeRate)
        .failureRateChangeRate(failureRateChangeRate)
        .frequencyChangeRate(frequencyChangeRate)
        .serviceDeploymentList(serviceDeploymentList)
        .build();
  }

  /**
   * This API processes all services for given combination of identifiers and produces list of data points
   * determining the active number of services at particular timestamps, distanced by equal quantity
   * determined by the groupBy param
   * @param accountIdentifier
   * @param orgIdentifier
   * @param projectIdentifier
   * @param startTimeInMs start time of the search interval
   * @param endTimeInMs end time of the search interval
   * @param timeGroupType groupBy param to determine the discreteness of the growth trend
   * @return
   */
  @Override
  public io.harness.ng.overview.dto.TimeValuePairListDTO<Integer> getServicesGrowthTrend(String accountIdentifier,
      String orgIdentifier, String projectIdentifier, long startTimeInMs, long endTimeInMs,
      TimeGroupType timeGroupType) {
    // Fetch all services for given accId + orgId + projectId including deleted ones in ASC order of creation time
    List<ServiceEntity> serviceEntities =
        serviceEntityService.getAllServices(accountIdentifier, orgIdentifier, projectIdentifier);

    // Create List<EntityStatusDetails> out of service entity list to create growth trend out of it
    List<io.harness.ng.overview.dto.EntityStatusDetails> entities = new ArrayList<>();
    serviceEntities.forEach(serviceEntity -> {
      if (Boolean.FALSE.equals(serviceEntity.getDeleted())) {
        entities.add(new io.harness.ng.overview.dto.EntityStatusDetails(serviceEntity.getCreatedAt()));
      } else {
        entities.add(new EntityStatusDetails(
            serviceEntity.getCreatedAt(), serviceEntity.getDeleted(), serviceEntity.getDeletedAt()));
      }
    });

    return new io.harness.ng.overview.dto.TimeValuePairListDTO<>(
        GrowthTrendEvaluator.getGrowthTrend(entities, startTimeInMs, endTimeInMs, timeGroupType));
  }

  private double getFailureRateChangeRate(List<io.harness.ng.overview.dto.ServiceDeployment> executionDeploymentList,
      List<io.harness.ng.overview.dto.ServiceDeployment> prevExecutionDeploymentList) {
    double failureRate = getFailureRate(executionDeploymentList);
    double prevFailureRate = getFailureRate(prevExecutionDeploymentList);
    return calculateChangeRate(prevFailureRate, failureRate);
  }

  private double getFailureRate(List<io.harness.ng.overview.dto.ServiceDeployment> executionDeploymentList) {
    long totalDeployments = executionDeploymentList.stream()
                                .map(io.harness.ng.overview.dto.ServiceDeployment::getDeployments)
                                .mapToLong(io.harness.ng.overview.dto.DeploymentCount::getTotal)
                                .sum();
    long totalFailure = executionDeploymentList.stream()
                            .map(io.harness.ng.overview.dto.ServiceDeployment::getDeployments)
                            .mapToLong(DeploymentCount::getFailure)
                            .sum();
    double failureRate = totalFailure * 100;
    if (totalDeployments != 0) {
      failureRate = failureRate / totalDeployments;
    }
    return failureRate;
  }
  private double calculateChangeRate(double prevValue, double curValue) {
    if (prevValue == curValue) {
      return 0;
    }
    if (prevValue == 0) {
      return INVALID_CHANGE_RATE;
    }
    return ((curValue - prevValue) * 100) / prevValue;
  }

  private long getTotalDeployments(List<io.harness.ng.overview.dto.ServiceDeployment> executionDeploymentList) {
    long total = 0;
    for (ServiceDeployment item : executionDeploymentList) {
      total += item.getDeployments().getTotal();
    }
    return total;
  }

  public DeploymentStatusInfoList queryCalculatorDeploymentInfo(String queryStatus) {
    List<String> objectIdList = new ArrayList<>();
    List<String> namePipelineList = new ArrayList<>();
    List<Long> startTs = new ArrayList<>();
    List<Long> endTs = new ArrayList<>();
    List<String> planExecutionIdList = new ArrayList<>();
    List<String> identifierList = new ArrayList<>();
    List<String> deploymentStatus = new ArrayList<>();

    // CI-Info
    List<GitInfo> gitInfoList = new ArrayList<>();
    List<String> triggerTypeList = new ArrayList<>();
    List<AuthorInfo> authorInfoList = new ArrayList<>();

    int totalTries = 0;
    boolean successfulOperation = false;
    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      ResultSet resultSet = null;
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(queryStatus)) {
        resultSet = statement.executeQuery();
        while (resultSet != null && resultSet.next()) {
          objectIdList.add(resultSet.getString("id"));
          planExecutionIdList.add(resultSet.getString("planexecutionid"));
          identifierList.add(resultSet.getString("pipelineidentifier"));
          namePipelineList.add(resultSet.getString("name"));
          startTs.add(Long.valueOf(resultSet.getString("startts")));
          deploymentStatus.add(resultSet.getString("status"));
          if (resultSet.getString("endTs") != null) {
            endTs.add(Long.valueOf(resultSet.getString("endTs")));
          } else {
            endTs.add(-1L);
          }

          // GitInfo
          GitInfo gitInfo = GitInfo.builder()
                                .targetBranch(resultSet.getString("moduleinfo_branch_name"))
                                .sourceBranch(resultSet.getString("source_branch"))
                                .repoName(resultSet.getString("moduleinfo_repository"))
                                .commit(resultSet.getString("moduleinfo_branch_commit_message"))
                                .commitID(resultSet.getString("moduleinfo_branch_commit_id"))
                                .eventType(resultSet.getString("moduleinfo_event"))
                                .build();
          gitInfoList.add(gitInfo);

          // TriggerType
          triggerTypeList.add(resultSet.getString("trigger_type"));

          // AuthorInfo
          authorInfoList.add(AuthorInfo.builder()
                                 .name(resultSet.getString("moduleinfo_author_id"))
                                 .url(resultSet.getString("author_avatar"))
                                 .build());
        }
        successfulOperation = true;
      } catch (SQLException ex) {
        totalTries++;
      } finally {
        DBUtils.close(resultSet);
      }
    }
    return DeploymentStatusInfoList.builder()
        .objectIdList(objectIdList)
        .deploymentStatus(deploymentStatus)
        .endTs(endTs)
        .namePipelineList(namePipelineList)
        .startTs(startTs)
        .pipelineIdentifierList(identifierList)
        .planExecutionIdList(planExecutionIdList)
        .gitInfoList(gitInfoList)
        .triggerType(triggerTypeList)
        .author(authorInfoList)
        .build();
  }

  public List<ExecutionStatusInfo> getDeploymentStatusInfo(String queryStatus, String queryServiceNameTagId) {
    List<String> objectIdList = new ArrayList<>();
    List<String> namePipelineList = new ArrayList<>();
    List<Long> startTs = new ArrayList<>();
    List<Long> endTs = new ArrayList<>();
    List<String> deploymentStatus = new ArrayList<>();
    List<String> planExecutionIdList = new ArrayList<>();
    List<String> pipelineIdentifierList = new ArrayList<>();

    // CI-Info
    List<GitInfo> gitInfoList = new ArrayList<>();
    List<String> triggerType = new ArrayList<>();
    List<AuthorInfo> author = new ArrayList<>();

    HashMap<String, List<ServiceDeploymentInfo>> serviceTagMap = new HashMap<>();

    DeploymentStatusInfoList deploymentStatusInfoList = queryCalculatorDeploymentInfo(queryStatus);
    deploymentStatus = deploymentStatusInfoList.getDeploymentStatus();
    endTs = deploymentStatusInfoList.getEndTs();
    namePipelineList = deploymentStatusInfoList.getNamePipelineList();
    objectIdList = deploymentStatusInfoList.getObjectIdList();
    startTs = deploymentStatusInfoList.getStartTs();
    planExecutionIdList = deploymentStatusInfoList.getPlanExecutionIdList();
    pipelineIdentifierList = deploymentStatusInfoList.getPipelineIdentifierList();

    gitInfoList = deploymentStatusInfoList.getGitInfoList();
    triggerType = deploymentStatusInfoList.getTriggerType();
    author = deploymentStatusInfoList.getAuthor();

    String queryServiceTag = queryBuilderServiceTag(queryServiceNameTagId);

    serviceTagMap = queryCalculatorServiceTagMag(queryServiceTag);

    List<ExecutionStatusInfo> statusInfo = new ArrayList<>();
    for (int i = 0; i < objectIdList.size(); i++) {
      String objectId = objectIdList.get(i);
      long startTime = startTs.get(i);
      long endTime = endTs.get(i);
      String pipelineIdentifier = pipelineIdentifierList.get(i);
      String planExecutionId = planExecutionIdList.get(i);
      statusInfo.add(this.getDeploymentStatusInfoObject(namePipelineList.get(i), pipelineIdentifier, planExecutionId,
          startTime, endTime, deploymentStatus.get(i), gitInfoList.get(i), triggerType.get(i), author.get(i),
          serviceTagMap.get(objectId)));
    }
    return statusInfo;
  }
  @Override
  public DashboardExecutionStatusInfo getDeploymentActiveFailedRunningInfo(
      String accountId, String orgId, String projectId, long days) {
    // failed
    String queryFailed =
        queryBuilderStatus(accountId, orgId, projectId, days, CDDashboardServiceHelper.failedStatusList);
    String queryServiceNameTagIdFailed = queryBuilderSelectIdLimitTimeCdTable(
        accountId, orgId, projectId, days, CDDashboardServiceHelper.failedStatusList);
    List<ExecutionStatusInfo> failure = getDeploymentStatusInfo(queryFailed, queryServiceNameTagIdFailed);

    // active
    String queryActive = queryBuilderStatus(accountId, orgId, projectId, days, activeStatusList);
    String queryServiceNameTagIdActive =
        queryBuilderSelectIdLimitTimeCdTable(accountId, orgId, projectId, days, activeStatusList);
    List<ExecutionStatusInfo> active = getDeploymentStatusInfo(queryActive, queryServiceNameTagIdActive);

    // pending
    String queryPending = queryBuilderStatus(accountId, orgId, projectId, days, pendingStatusList);
    String queryServiceNameTagIdPending =
        queryBuilderSelectIdLimitTimeCdTable(accountId, orgId, projectId, days, pendingStatusList);
    List<ExecutionStatusInfo> pending = getDeploymentStatusInfo(queryPending, queryServiceNameTagIdPending);

    return DashboardExecutionStatusInfo.builder().failure(failure).active(active).pending(pending).build();
  }

  private ExecutionStatusInfo getDeploymentStatusInfoObject(String name, String identfier, String planExecutionId,
      Long startTime, Long endTime, String status, GitInfo gitInfo, String triggerType, AuthorInfo authorInfo,
      List<ServiceDeploymentInfo> serviceDeploymentInfos) {
    return ExecutionStatusInfo.builder()
        .pipelineName(name)
        .pipelineIdentifier(identfier)
        .planExecutionId(planExecutionId)
        .startTs(startTime)
        .endTs(endTime)
        .status(status)
        .gitInfo(gitInfo)
        .triggerType(triggerType)
        .author(authorInfo)
        .serviceInfoList(serviceDeploymentInfos)
        .build();
  }

  private ServiceDeploymentInfo getServiceDeployment(String service_name, String tag) {
    if (service_name != null) {
      if (tag != null) {
        return ServiceDeploymentInfo.builder().serviceName(service_name).serviceTag(tag).build();
      } else {
        return ServiceDeploymentInfo.builder().serviceName(service_name).build();
      }
    }
    return ServiceDeploymentInfo.builder().build();
  }

  private WorkloadDeploymentInfo getWorkloadDeploymentInfo(WorkloadDeploymentInfo workloadDeploymentInfo,
      long totalDeployment, long prevTotalDeployment, long success, long previousSuccess, long failure,
      long previousFailure, long numberOfDays) {
    double percentSuccess = 0.0;
    double failureRate = 0.0;
    double failureRateChangeRate = calculateChangeRate(previousFailure, failure);
    double totalDeploymentChangeRate = calculateChangeRate(prevTotalDeployment, totalDeployment);
    double frequency = totalDeployment / (double) numberOfDays;
    double prevFrequency = prevTotalDeployment / (double) numberOfDays;
    double frequencyChangeRate = calculateChangeRate(prevFrequency, frequency);
    if (totalDeployment != 0) {
      percentSuccess = success / (double) totalDeployment;
      percentSuccess = percentSuccess * 100.0;
      failureRate = failure / (double) totalDeployment;
      failureRate = failureRate * 100.0;
    }
    return WorkloadDeploymentInfo.builder()
        .serviceName(workloadDeploymentInfo.getServiceName())
        .serviceId(workloadDeploymentInfo.getServiceId())
        .lastExecuted(workloadDeploymentInfo.getLastExecuted())
        .deploymentTypeList(workloadDeploymentInfo.getDeploymentTypeList())
        .totalDeployments(totalDeployment)
        .totalDeploymentChangeRate(totalDeploymentChangeRate)
        .percentSuccess(percentSuccess)
        .rateSuccess(calculateChangeRate(previousSuccess, success))
        .failureRate(failureRate)
        .failureRateChangeRate(failureRateChangeRate)
        .frequency(frequency)
        .frequencyChangeRate(frequencyChangeRate)
        .lastPipelineExecutionId(workloadDeploymentInfo.getLastPipelineExecutionId())
        .workload(workloadDeploymentInfo.getWorkload())
        .build();
  }

  public DashboardWorkloadDeployment getWorkloadDeploymentInfoCalculation(List<String> workloadsId, List<String> status,
      List<Pair<Long, Long>> timeInterval, List<String> deploymentTypeList,
      HashMap<String, String> uniqueWorkloadNameAndId, long startDate, long endDate,
      List<String> pipelineExecutionIdList) {
    List<WorkloadDeploymentInfo> workloadDeploymentInfoList = new ArrayList<>();
    long numberOfDays = NGDateUtils.getNumberOfDays(startDate, endDate);
    for (String workloadId : uniqueWorkloadNameAndId.keySet()) {
      long totalDeployment = 0;
      long prevTotalDeployments = 0;
      long success = 0;
      long previousSuccess = 0;
      long failure = 0;
      long previousFailure = 0;
      long lastExecutedStartTs = 0L;
      long lastExecutedEndTs = 0L;
      String lastStatus = null;
      String deploymentType = null;
      String pipelineExecutionId = null;

      HashMap<Long, Integer> deploymentCountMap = new HashMap<>();

      long startDateCopy = startDate;
      long endDateCopy = endDate;

      while (startDateCopy < endDateCopy) {
        deploymentCountMap.put(startDateCopy, 0);
        startDateCopy = startDateCopy + DAY_IN_MS;
      }

      for (int i = 0; i < workloadsId.size(); i++) {
        if (workloadsId.get(i).contentEquals(workloadId)) {
          long startTime = timeInterval.get(i).getKey();
          long endTime = timeInterval.get(i).getValue();
          long currentTimeEpoch = startTime;
          if (currentTimeEpoch >= startDate && currentTimeEpoch < endDate) {
            currentTimeEpoch = getStartingDateEpochValue(currentTimeEpoch, startDate);
            totalDeployment++;
            deploymentCountMap.put(currentTimeEpoch, deploymentCountMap.get(currentTimeEpoch) + 1);
            if (status.get(i).contentEquals(ExecutionStatus.SUCCESS.name())) {
              success++;
            }
            if (CDDashboardServiceHelper.failedStatusList.contains(status.get(i))) {
              failure++;
            }
            if (lastExecutedStartTs == 0) {
              lastExecutedStartTs = startTime;
              lastExecutedEndTs = endTime;
              lastStatus = status.get(i);
              deploymentType = deploymentTypeList.get(i);
              pipelineExecutionId = pipelineExecutionIdList.get(i);
            } else {
              if (lastExecutedStartTs < startTime) {
                lastExecutedStartTs = startTime;
                lastExecutedEndTs = endTime;
                lastStatus = status.get(i);
                deploymentType = deploymentTypeList.get(i);
                pipelineExecutionId = pipelineExecutionIdList.get(i);
              }
            }
          } else {
            prevTotalDeployments++;
            if (status.get(i).contentEquals(ExecutionStatus.SUCCESS.name())) {
              previousSuccess++;
            }
            if (status.get(i).contentEquals(ExecutionStatus.FAILED.name())) {
              previousFailure++;
            }
          }
        }
      }

      if (totalDeployment > 0) {
        List<io.harness.ng.overview.dto.WorkloadDateCountInfo> dateCount = new ArrayList<>();
        startDateCopy = startDate;
        endDateCopy = endDate;
        while (startDateCopy < endDateCopy) {
          dateCount.add(WorkloadDateCountInfo.builder()
                            .date(startDateCopy)
                            .execution(WorkloadCountInfo.builder().count(deploymentCountMap.get(startDateCopy)).build())
                            .build());
          startDateCopy = startDateCopy + DAY_IN_MS;
        }
        LastWorkloadInfo lastWorkloadInfo = LastWorkloadInfo.builder()
                                                .startTime(lastExecutedStartTs)
                                                .endTime(lastExecutedEndTs == -1L ? null : lastExecutedEndTs)
                                                .status(lastStatus)
                                                .deploymentType(deploymentType)
                                                .build();
        WorkloadDeploymentInfo workloadDeploymentInfo =
            WorkloadDeploymentInfo.builder()
                .serviceName(uniqueWorkloadNameAndId.get(workloadId))
                .serviceId(workloadId)
                .totalDeployments(totalDeployment)
                .lastExecuted(lastWorkloadInfo)
                .lastPipelineExecutionId(pipelineExecutionId)
                .deploymentTypeList(deploymentTypeList.stream().collect(Collectors.toSet()))
                .workload(dateCount)
                .build();
        workloadDeploymentInfoList.add(getWorkloadDeploymentInfo(workloadDeploymentInfo, totalDeployment,
            prevTotalDeployments, success, previousSuccess, failure, previousFailure, numberOfDays));
      }
    }
    return DashboardWorkloadDeployment.builder().workloadDeploymentInfoList(workloadDeploymentInfoList).build();
  }

  @Override
  public DashboardWorkloadDeployment getDashboardWorkloadDeployment(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, long startInterval, long endInterval, long previousStartInterval,
      EnvironmentType envType) {
    String query = queryBuilderSelectWorkload(
        accountIdentifier, orgIdentifier, projectIdentifier, previousStartInterval, endInterval, envType);

    List<String> workloadsId = new ArrayList<>();
    List<String> status = new ArrayList<>();
    List<Pair<Long, Long>> timeInterval = new ArrayList<>();
    List<String> deploymentTypeList = new ArrayList<>();
    List<String> pipelineExecutionIdList = new ArrayList<>();

    HashMap<String, String> uniqueWorkloadNameAndId = new HashMap<>();

    int totalTries = 0;
    boolean successfulOperation = false;
    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      ResultSet resultSet = null;
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(query)) {
        resultSet = statement.executeQuery();
        while (resultSet != null && resultSet.next()) {
          String serviceName = resultSet.getString("service_name");
          String service_id = resultSet.getString("service_id");
          long startTime = Long.parseLong(resultSet.getString("startTs"));
          workloadsId.add(service_id);
          status.add(resultSet.getString("status"));
          String pipelineExecutionId = resultSet.getString(NGServiceConstants.PIPELINE_EXECUTION_ID);
          pipelineExecutionIdList.add(pipelineExecutionId);
          if (resultSet.getString("endTs") != null) {
            timeInterval.add(Pair.of(startTime, Long.valueOf(resultSet.getString("endTs"))));
          } else {
            timeInterval.add(Pair.of(startTime, -1L));
          }
          deploymentTypeList.add(resultSet.getString("deployment_type"));

          if (!uniqueWorkloadNameAndId.containsKey(service_id)) {
            uniqueWorkloadNameAndId.put(service_id, serviceName);
          }
        }
        successfulOperation = true;
      } catch (SQLException ex) {
        totalTries++;
      } finally {
        DBUtils.close(resultSet);
      }
    }
    return getWorkloadDeploymentInfoCalculation(workloadsId, status, timeInterval, deploymentTypeList,
        uniqueWorkloadNameAndId, startInterval, endInterval, pipelineExecutionIdList);
  }

  public long getTimeUnitToGroupBy(TimeGroupType timeGroupType) {
    if (timeGroupType == DAY) {
      return DAY_IN_MS;
    } else if (timeGroupType == HOUR) {
      return HOUR_IN_MS;
    } else {
      throw new UnknownEnumTypeException("Time Group Type", String.valueOf(timeGroupType));
    }
  }

  public long getStartingDateEpochValue(long epochValue, long startInterval) {
    return epochValue - epochValue % DAY_IN_MS;
  }

  /*
    Returns break down of instance count for various environment type for given account+org+project+serviceIds
  */
  @Override
  public InstanceCountDetailsByEnvTypeAndServiceId getActiveServiceInstanceCountBreakdown(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, List<String> serviceId) {
    return instanceDashboardService.getActiveServiceInstanceCountBreakdown(
        accountIdentifier, orgIdentifier, projectIdentifier, serviceId, getCurrentTime());
  }

  /*
    Returns a list of buildId and instance counts for various environments for given account+org+project+service
  */
  @Override
  public io.harness.ng.overview.dto.EnvBuildIdAndInstanceCountInfoList getEnvBuildInstanceCountByServiceId(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceId) {
    Map<String, List<BuildIdAndInstanceCount>> envIdToBuildMap = new HashMap<>();
    Map<String, String> envIdToEnvNameMap = new HashMap<>();

    List<EnvBuildInstanceCount> envBuildInstanceCounts = instanceDashboardService.getEnvBuildInstanceCountByServiceId(
        accountIdentifier, orgIdentifier, projectIdentifier, serviceId, getCurrentTime());

    envBuildInstanceCounts.forEach(envBuildInstanceCount -> {
      final String envId = envBuildInstanceCount.getEnvIdentifier();
      final String envName = envBuildInstanceCount.getEnvName();
      final String buildId = envBuildInstanceCount.getTag();
      final int count = envBuildInstanceCount.getCount();
      envIdToBuildMap.putIfAbsent(envId, new ArrayList<>());

      BuildIdAndInstanceCount buildIdAndInstanceCount =
          BuildIdAndInstanceCount.builder().buildId(buildId).count(count).build();
      envIdToBuildMap.get(envId).add(buildIdAndInstanceCount);

      envIdToEnvNameMap.putIfAbsent(envId, envName);
    });

    List<EnvBuildIdAndInstanceCountInfo> envBuildIdAndInstanceCountInfoList = new ArrayList<>();
    envIdToBuildMap.forEach((envId, buildIdAndInstanceCountList) -> {
      EnvBuildIdAndInstanceCountInfo envBuildIdAndInstanceCountInfo =
          EnvBuildIdAndInstanceCountInfo.builder()
              .envId(envId)
              .envName(envIdToEnvNameMap.getOrDefault(envId, ""))
              .buildIdAndInstanceCountList(buildIdAndInstanceCountList)
              .build();
      envBuildIdAndInstanceCountInfoList.add(envBuildIdAndInstanceCountInfo);
    });

    return EnvBuildIdAndInstanceCountInfoList.builder()
        .envBuildIdAndInstanceCountInfoList(envBuildIdAndInstanceCountInfoList)
        .build();
  }

  /*
    Returns list of instances for each build id for given account+org+project+service+env
  */
  @Override
  public InstancesByBuildIdList getActiveInstancesByServiceIdEnvIdAndBuildIds(String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String serviceId, String envId, List<String> buildIds) {
    List<InstanceDetailsByBuildId> instancesByBuildIdList =
        instanceDashboardService.getActiveInstancesByServiceIdEnvIdAndBuildIds(
            accountIdentifier, orgIdentifier, projectIdentifier, serviceId, envId, buildIds, getCurrentTime());
    return InstancesByBuildIdList.builder().instancesByBuildIdList(instancesByBuildIdList).build();
  }

  /*
    Returns instance count summary for given account+org+project+serviceId, includes rate of change in count since
    provided timestamp
  */
  @Override
  public io.harness.ng.overview.dto.ActiveServiceInstanceSummary getActiveServiceInstanceSummary(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceId, long timestampInMs) {
    final long currentTime = getCurrentTime();

    InstanceCountDetailsByEnvTypeBase defaultInstanceCountDetails =
        InstanceCountDetailsByEnvTypeBase.builder().envTypeVsInstanceCountMap(new HashMap<>()).build();

    InstanceCountDetailsByEnvTypeBase currentCountDetails =
        instanceDashboardService
            .getActiveServiceInstanceCountBreakdown(
                accountIdentifier, orgIdentifier, projectIdentifier, Arrays.asList(serviceId), currentTime)
            .getInstanceCountDetailsByEnvTypeBaseMap()
            .getOrDefault(serviceId, defaultInstanceCountDetails);
    InstanceCountDetailsByEnvTypeBase prevCountDetails =
        instanceDashboardService
            .getActiveServiceInstanceCountBreakdown(
                accountIdentifier, orgIdentifier, projectIdentifier, Arrays.asList(serviceId), timestampInMs)
            .getInstanceCountDetailsByEnvTypeBaseMap()
            .getOrDefault(serviceId, defaultInstanceCountDetails);

    double changeRate =
        calculateChangeRate(prevCountDetails.getTotalInstances(), currentCountDetails.getTotalInstances());

    return ActiveServiceInstanceSummary.builder().countDetails(currentCountDetails).changeRate(changeRate).build();
  }

  /*
    Returns a list of time value pairs where value represents count of instances for given account+org+project+service
    within provided time interval
  */
  @Override
  public io.harness.ng.overview.dto.TimeValuePairListDTO<Integer> getInstanceGrowthTrend(String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String serviceId, long startTimeInMs, long endTimeInMs) {
    List<TimeValuePair<Integer>> timeValuePairList = new ArrayList<>();
    Map<Long, Integer> timeValuePairMap = new HashMap<>();

    final long tunedStartTimeInMs = getStartTimeOfTheDayAsEpoch(startTimeInMs);
    final long tunedEndTimeInMs = getStartTimeOfNextDay(endTimeInMs);

    final String query =
        "select reportedat, SUM(instancecount) as count from ng_instance_stats_day where accountid = ? and orgid = ? and projectid = ? and serviceid = ? and reportedat >= ? and reportedat <= ? group by reportedat order by reportedat asc";

    int totalTries = 0;
    boolean successfulOperation = false;
    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      ResultSet resultSet = null;
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(query)) {
        statement.setString(1, accountIdentifier);
        statement.setString(2, orgIdentifier);
        statement.setString(3, projectIdentifier);
        statement.setString(4, serviceId);
        statement.setTimestamp(5, new Timestamp(tunedStartTimeInMs), DateUtils.getDefaultCalendar());
        statement.setTimestamp(6, new Timestamp(tunedEndTimeInMs), DateUtils.getDefaultCalendar());

        resultSet = statement.executeQuery();
        while (resultSet != null && resultSet.next()) {
          final long timestamp =
              resultSet.getTimestamp(TimescaleConstants.REPORTEDAT.getKey(), DateUtils.getDefaultCalendar()).getTime();
          final int count = Integer.parseInt(resultSet.getString("count"));
          timeValuePairMap.put(getStartTimeOfTheDayAsEpoch(timestamp), count);
        }
        successfulOperation = true;
      } catch (SQLException ex) {
        totalTries++;
      } finally {
        DBUtils.close(resultSet);
      }
    }

    long currTime = tunedStartTimeInMs;
    while (currTime < tunedEndTimeInMs) {
      timeValuePairList.add(new TimeValuePair<>(currTime, timeValuePairMap.getOrDefault(currTime, 0)));
      currTime = currTime + DAY.getDurationInMs();
    }

    return new io.harness.ng.overview.dto.TimeValuePairListDTO<>(timeValuePairList);
  }

  /*
    Returns a list of time value pairs where value is a pair of envid and instance count
  */
  @Override
  public io.harness.ng.overview.dto.TimeValuePairListDTO<io.harness.ng.overview.dto.EnvIdCountPair>
  getInstanceCountHistory(String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceId,
      long startTimeInMs, long endTimeInMs) {
    List<TimeValuePair<io.harness.ng.overview.dto.EnvIdCountPair>> timeValuePairList = new ArrayList<>();
    Map<String, Map<Long, Integer>> envIdToTimestampAndCountMap = new HashMap<>();

    final long tunedStartTimeInMs = getStartTimeOfTheDayAsEpoch(startTimeInMs);
    final long tunedEndTimeInMs = getStartTimeOfNextDay(endTimeInMs);

    final String query =
        "select reportedat, envid, SUM(instancecount) as count from ng_instance_stats_day where accountid = ? and orgid = ? and projectid = ? and serviceid = ? and reportedat >= ? and reportedat <= ? group by reportedat, envid order by reportedat asc";

    int totalTries = 0;
    boolean successfulOperation = false;
    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      ResultSet resultSet = null;
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(query)) {
        statement.setString(1, accountIdentifier);
        statement.setString(2, orgIdentifier);
        statement.setString(3, projectIdentifier);
        statement.setString(4, serviceId);
        statement.setTimestamp(5, new Timestamp(tunedStartTimeInMs), DateUtils.getDefaultCalendar());
        statement.setTimestamp(6, new Timestamp(tunedEndTimeInMs), DateUtils.getDefaultCalendar());

        resultSet = statement.executeQuery();
        while (resultSet != null && resultSet.next()) {
          final long timestamp =
              resultSet.getTimestamp(TimescaleConstants.REPORTEDAT.getKey(), DateUtils.getDefaultCalendar()).getTime();
          final String envId = resultSet.getString(TimescaleConstants.ENV_ID.getKey());
          final int count = Integer.parseInt(resultSet.getString("count"));

          envIdToTimestampAndCountMap.putIfAbsent(envId, new HashMap<>());
          envIdToTimestampAndCountMap.get(envId).put(getStartTimeOfTheDayAsEpoch(timestamp), count);
        }
        successfulOperation = true;
      } catch (SQLException ex) {
        totalTries++;
      } finally {
        DBUtils.close(resultSet);
      }
    }

    envIdToTimestampAndCountMap.forEach((envId, timeStampAndCountMap) -> {
      long currTime = tunedStartTimeInMs;
      while (currTime <= tunedEndTimeInMs) {
        int count = timeStampAndCountMap.getOrDefault(currTime, 0);
        io.harness.ng.overview.dto.EnvIdCountPair envIdCountPair =
            EnvIdCountPair.builder().envId(envId).count(count).build();
        timeValuePairList.add(new TimeValuePair<>(currTime, envIdCountPair));
        currTime += DAY.getDurationInMs();
      }
    });

    return new TimeValuePairListDTO<>(timeValuePairList);
  }

  public DeploymentsInfo getDeploymentsByServiceId(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String serviceId, long startTimeInMs, long endTimeInMs) {
    startTimeInMs = getStartTimeOfTheDayAsEpoch(startTimeInMs);
    endTimeInMs = getStartTimeOfNextDay(endTimeInMs);
    String query = queryBuilderDeployments(
        accountIdentifier, orgIdentifier, projectIdentifier, serviceId, startTimeInMs, endTimeInMs);
    String queryServiceNameTagId = queryToGetId(accountIdentifier, orgIdentifier, projectIdentifier, serviceId);
    List<ExecutionStatusInfo> deployments = getDeploymentStatusInfo(query, queryServiceNameTagId);
    return DeploymentsInfo.builder().deployments(deployments).build();
  }

  private String queryBuilderDeployments(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String serviceId, long startTimeInMs, long endTimeInMs) {
    return "select " + executionStatusCdTimeScaleColumns() + " from " + tableNameCD + " where id in ( "
        + queryToGetId(accountIdentifier, orgIdentifier, projectIdentifier, serviceId) + ") and "
        + String.format("startts>='%s' and startts<='%s' ", startTimeInMs, endTimeInMs) + "order by startts desc";
  }

  private String queryToGetId(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceId) {
    return "select distinct pipeline_execution_summary_cd_id from " + tableNameServiceAndInfra + " where "
        + String.format("accountid='%s' and ", accountIdentifier)
        + String.format("orgidentifier='%s' and ", orgIdentifier)
        + String.format("projectidentifier='%s' and ", projectIdentifier) + String.format("service_id='%s'", serviceId);
  }

  public io.harness.ng.overview.dto.ServiceHeaderInfo getServiceHeaderInfo(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceId) {
    Optional<ServiceEntity> service =
        ServiceEntityServiceImpl.get(accountIdentifier, orgIdentifier, projectIdentifier, serviceId, false);
    ServiceEntity serviceEntity = service.get();
    Set<String> deploymentTypes =
        getDeploymentType(accountIdentifier, orgIdentifier, projectIdentifier, Arrays.asList(serviceId))
            .getOrDefault(serviceId, new HashSet<>());
    return ServiceHeaderInfo.builder()
        .identifier(serviceId)
        .name(serviceEntity.getName())
        .description(serviceEntity.getDescription())
        .deploymentTypes(deploymentTypes)
        .createdAt(serviceEntity.getCreatedAt())
        .lastModifiedAt(serviceEntity.getLastModifiedAt())
        .build();
  }

  /*
  Returns a list of last successfully buildId deployed to environments for given account+org+project+service
*/
  @Override
  public io.harness.ng.overview.dto.EnvironmentDeploymentInfo getEnvironmentDeploymentDetailsByServiceId(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceId) {
    String query =
        queryBuilderDeploymentsWithArtifactsDetails(accountIdentifier, orgIdentifier, projectIdentifier, serviceId);
    List<EnvironmentInfoByServiceId> environmentInfoByServiceIds = getEnvironmentWithArtifactDetails(query);
    return EnvironmentDeploymentInfo.builder().environmentInfoByServiceId(environmentInfoByServiceIds).build();
  }

  public String queryBuilderDeploymentsWithArtifactsDetails(
      String accountId, String orgId, String projectId, String serviceId) {
    return "SELECT DISTINCT ON (env_id) env_name, env_id, artifact_image, tag, service_startts, "
        + "service_endts, service_name, service_id from " + tableNameServiceAndInfra + " where "
        + String.format("accountid='%s' and ", accountId) + String.format("orgidentifier='%s' and ", orgId)
        + String.format("projectidentifier='%s' and ", projectId) + String.format("service_id='%s'", serviceId)
        + " and service_status = 'SUCCESS' AND tag is not null order by env_id , service_endts DESC;";
  }

  public List<EnvironmentInfoByServiceId> getEnvironmentWithArtifactDetails(String queryStatus) {
    int totalTries = 0;
    List<EnvironmentInfoByServiceId> environmentInfoList = new ArrayList<>();
    boolean successfulOperation = false;
    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      ResultSet resultSet = null;
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(queryStatus)) {
        resultSet = statement.executeQuery();
        while (resultSet != null && resultSet.next()) {
          environmentInfoList.add(EnvironmentInfoByServiceId.builder()
                                      .environmentId(resultSet.getString("env_id"))
                                      .environmentName(resultSet.getString("env_name"))
                                      .artifactImage(resultSet.getString("artifact_image"))
                                      .tag(resultSet.getString("tag"))
                                      .serviceId(resultSet.getString("service_id"))
                                      .serviceName(resultSet.getString("service_name"))
                                      .service_startTs(resultSet.getLong("service_startts"))
                                      .service_endTs(resultSet.getLong("service_endts"))
                                      .build());
        }
        successfulOperation = true;
      } catch (SQLException ex) {
        totalTries++;
      } finally {
        DBUtils.close(resultSet);
      }
    }
    return environmentInfoList;
  }
}
