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
import static io.harness.NGDateUtils.getStartTimeOfPreviousInterval;
import static io.harness.NGDateUtils.getStartTimeOfTheDayAsEpoch;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.event.timeseries.processor.utils.DateUtils.getCurrentTime;
import static io.harness.ng.core.activityhistory.dto.TimeGroupType.DAY;
import static io.harness.ng.core.activityhistory.dto.TimeGroupType.HOUR;
import static io.harness.ng.core.template.TemplateListType.STABLE_TEMPLATE_TYPE;

import static java.lang.String.format;
import static java.util.Objects.isNull;

import io.harness.NGDateUtils;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cd.CDDashboardServiceHelper;
import io.harness.cd.NGPipelineSummaryCDConstants;
import io.harness.cd.NGServiceConstants;
import io.harness.cdng.envGroup.beans.EnvironmentGroupEntity;
import io.harness.cdng.envGroup.services.EnvironmentGroupServiceImpl;
import io.harness.cdng.service.beans.CustomSequenceDTO;
import io.harness.data.structure.EmptyPredicate;
import io.harness.encryption.Scope;
import io.harness.event.timeseries.processor.utils.DateUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnknownEnumTypeException;
import io.harness.models.ActiveServiceInstanceInfoV2;
import io.harness.models.ActiveServiceInstanceInfoWithEnvType;
import io.harness.models.ArtifactDeploymentDetailModel;
import io.harness.models.EnvBuildInstanceCount;
import io.harness.models.EnvironmentInstanceCountModel;
import io.harness.models.InstanceDetailGroupedByPipelineExecutionList;
import io.harness.models.InstanceDetailsByBuildId;
import io.harness.models.constants.TimescaleConstants;
import io.harness.models.dashboard.InstanceCountDetailsByEnvTypeAndServiceId;
import io.harness.models.dashboard.InstanceCountDetailsByEnvTypeBase;
import io.harness.ng.core.activityhistory.dto.TimeGroupType;
import io.harness.ng.core.customDeployment.helper.CustomDeploymentYamlHelper;
import io.harness.ng.core.dashboard.AuthorInfo;
import io.harness.ng.core.dashboard.DashboardExecutionStatusInfo;
import io.harness.ng.core.dashboard.DeploymentsInfo;
import io.harness.ng.core.dashboard.EnvironmentDeploymentsInfo;
import io.harness.ng.core.dashboard.ExecutionStatusInfo;
import io.harness.ng.core.dashboard.GitInfo;
import io.harness.ng.core.dashboard.InfrastructureInfo;
import io.harness.ng.core.dashboard.ServiceDeploymentInfo;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.beans.EnvironmentFilterPropertiesDTO;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.ng.core.environment.services.impl.EnvironmentServiceImpl;
import io.harness.ng.core.mapper.TagMapper;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.entity.ServiceSequence;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.ng.core.service.services.ServiceSequenceService;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.ng.core.template.TemplateMetadataSummaryResponseDTO;
import io.harness.ng.core.template.TemplateResponseDTO;
import io.harness.ng.overview.dto.ActiveServiceDeploymentsInfo;
import io.harness.ng.overview.dto.ActiveServiceInstanceSummary;
import io.harness.ng.overview.dto.ActiveServiceInstanceSummaryV2;
import io.harness.ng.overview.dto.ArtifactDeploymentDetail;
import io.harness.ng.overview.dto.ArtifactInstanceDetails;
import io.harness.ng.overview.dto.BuildIdAndInstanceCount;
import io.harness.ng.overview.dto.ChangeRate;
import io.harness.ng.overview.dto.DashboardWorkloadDeployment;
import io.harness.ng.overview.dto.DashboardWorkloadDeploymentV2;
import io.harness.ng.overview.dto.Deployment;
import io.harness.ng.overview.dto.DeploymentChangeRates;
import io.harness.ng.overview.dto.DeploymentChangeRatesV2;
import io.harness.ng.overview.dto.DeploymentCount;
import io.harness.ng.overview.dto.DeploymentDateAndCount;
import io.harness.ng.overview.dto.DeploymentInfo;
import io.harness.ng.overview.dto.DeploymentInfoV2;
import io.harness.ng.overview.dto.DeploymentStatusInfoList;
import io.harness.ng.overview.dto.EntityStatusDetails;
import io.harness.ng.overview.dto.EnvBuildIdAndInstanceCountInfo;
import io.harness.ng.overview.dto.EnvBuildIdAndInstanceCountInfoList;
import io.harness.ng.overview.dto.EnvIdCountPair;
import io.harness.ng.overview.dto.EnvironmentDeploymentInfo;
import io.harness.ng.overview.dto.EnvironmentGroupInstanceDetails;
import io.harness.ng.overview.dto.EnvironmentInfoByServiceId;
import io.harness.ng.overview.dto.ExecutionDeployment;
import io.harness.ng.overview.dto.ExecutionDeploymentInfo;
import io.harness.ng.overview.dto.HealthDeploymentDashboard;
import io.harness.ng.overview.dto.HealthDeploymentDashboardV2;
import io.harness.ng.overview.dto.HealthDeploymentDetails;
import io.harness.ng.overview.dto.HealthDeploymentInfo;
import io.harness.ng.overview.dto.HealthDeploymentInfoV2;
import io.harness.ng.overview.dto.IconDTO;
import io.harness.ng.overview.dto.InstanceGroupedByArtifactList;
import io.harness.ng.overview.dto.InstanceGroupedByEnvironmentList;
import io.harness.ng.overview.dto.InstanceGroupedByServiceList;
import io.harness.ng.overview.dto.InstanceGroupedOnArtifactList;
import io.harness.ng.overview.dto.InstancesByBuildIdList;
import io.harness.ng.overview.dto.LastWorkloadInfo;
import io.harness.ng.overview.dto.OpenTaskDetails;
import io.harness.ng.overview.dto.PipelineExecutionCountInfo;
import io.harness.ng.overview.dto.ServiceArtifactExecutionDetail;
import io.harness.ng.overview.dto.ServiceDeployment;
import io.harness.ng.overview.dto.ServiceDeploymentInfoDTO;
import io.harness.ng.overview.dto.ServiceDeploymentInfoDTOV2;
import io.harness.ng.overview.dto.ServiceDeploymentListInfo;
import io.harness.ng.overview.dto.ServiceDeploymentListInfoV2;
import io.harness.ng.overview.dto.ServiceDeploymentV2;
import io.harness.ng.overview.dto.ServiceDetailsDTO;
import io.harness.ng.overview.dto.ServiceDetailsDTO.ServiceDetailsDTOBuilder;
import io.harness.ng.overview.dto.ServiceDetailsDTOV2;
import io.harness.ng.overview.dto.ServiceDetailsDTOV2.ServiceDetailsDTOV2Builder;
import io.harness.ng.overview.dto.ServiceDetailsInfoDTO;
import io.harness.ng.overview.dto.ServiceDetailsInfoDTOV2;
import io.harness.ng.overview.dto.ServiceHeaderInfo;
import io.harness.ng.overview.dto.ServicePipelineInfo;
import io.harness.ng.overview.dto.ServicePipelineWithRevertInfo;
import io.harness.ng.overview.dto.TimeAndStatusDeployment;
import io.harness.ng.overview.dto.TimeValuePair;
import io.harness.ng.overview.dto.TimeValuePairListDTO;
import io.harness.ng.overview.dto.TotalDeploymentInfo;
import io.harness.ng.overview.dto.TotalDeploymentInfoV2;
import io.harness.ng.overview.dto.WorkloadCountInfo;
import io.harness.ng.overview.dto.WorkloadDateCountInfo;
import io.harness.ng.overview.dto.WorkloadDeploymentDetails;
import io.harness.ng.overview.dto.WorkloadDeploymentInfo;
import io.harness.ng.overview.dto.WorkloadDeploymentInfoV2;
import io.harness.ng.overview.dto.WorkloadInfo;
import io.harness.ng.overview.util.GrowthTrendEvaluator;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.merger.YamlConfig;
import io.harness.remote.client.NGRestUtils;
import io.harness.service.instancedashboardservice.InstanceDashboardService;
import io.harness.template.remote.TemplateResourceClient;
import io.harness.template.resources.beans.TemplateFilterPropertiesDTO;
import io.harness.timescaledb.DBUtils;
import io.harness.timescaledb.TimeScaleDBService;
import io.harness.utils.IdentifierRefHelper;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(HarnessTeam.CDC)
@Singleton
@Slf4j
public class CDOverviewDashboardServiceImpl implements CDOverviewDashboardService {
  @Inject TimeScaleDBService timeScaleDBService;
  @Inject ServiceEntityService serviceEntityService;
  @Inject InstanceDashboardService instanceDashboardService;
  @Inject ServiceEntityService serviceEntityServiceImpl;
  @Inject EnvironmentServiceImpl environmentService;
  @Inject EnvironmentGroupServiceImpl environmentGroupService;
  @Inject ServiceSequenceService serviceSequenceService;
  @Inject TemplateResourceClient templateResourceClient;
  @Inject CustomDeploymentYamlHelper customDeploymentYamlHelper;

  private String tableNameCD = "pipeline_execution_summary_cd";
  private String EMPTY_ARTIFACT = "";
  private String CUSTOM_DEPLOYMENT = "CustomDeployment";
  private String tableNameServiceAndInfra = "service_infra_info";
  private static final String PIPELINE_EXECUTION_SUMMARY_CD_ID = "pipeline_execution_summary_cd_id";
  private static final String EXECUTION_FAILURE_DETAILS = "execution_failure_details";
  public static List<String> activeStatusList = Arrays.asList(ExecutionStatus.RUNNING.name(),
      ExecutionStatus.ASYNCWAITING.name(), ExecutionStatus.TASKWAITING.name(), ExecutionStatus.TIMEDWAITING.name(),
      ExecutionStatus.PAUSED.name(), ExecutionStatus.PAUSING.name());
  private List<String> pendingStatusList = Arrays.asList(ExecutionStatus.INTERVENTIONWAITING.name(),
      ExecutionStatus.APPROVALWAITING.name(), ExecutionStatus.WAITING.name(), ExecutionStatus.RESOURCEWAITING.name());
  private static final int MAX_RETRY_COUNT = 5;
  public static final double INVALID_CHANGE_RATE = -10000;
  private static final String SERVICE_NAME = "service_name";
  private static final String SERVICE_ID = "service_id";
  private static final String ARTIFACT_IMAGE = "artifact_image";
  private static final String TAG = "tag";
  private static final String ARTIFACT_DISPLAY_NAME = "artifact_display_name";
  private static final String ACCOUNT_ID = "accountid";
  private static final String ORG_ID = "orgidentifier";
  private static final String PROJECT_ID = "projectidentifier";
  private static final String SERVICE_STARTTS = "service_startts";
  private static final String ACCOUNT_IDENTIFIER = "account.";
  private static final String ORG_IDENTIFIER = "org.";

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

  public String queryBuilderSelectIdLimitTimeCdTableNew(String accountId, String orgId, String projectId, long days,
      List<String> statusList, long startInterval, long endInterval) {
    String queryPrepared = queryBuilderSelectIdLimitTimeCdTablePrepared(
        accountId, orgId, projectId, days, statusList, startInterval, endInterval);
    return (queryPrepared != null) ? queryPrepared
                                   : queryBuilderSelectIdLimitTimeCdTable(
                                       accountId, orgId, projectId, days, statusList, startInterval, endInterval);
  }

  public String queryBuilderSelectIdLimitTimeCdTablePrepared(String accountId, String orgId, String projectId,
      long days, List<String> statusList, long startInterval, long endInterval) {
    String selectStatusQuery = "select id from " + tableNameCD + " where ";
    StringBuilder preparedSqlBuilder = new StringBuilder(200);
    preparedSqlBuilder.append(selectStatusQuery);

    if (accountId != null) {
      preparedSqlBuilder.append("accountid=? and ");
    }

    if (orgId != null) {
      preparedSqlBuilder.append("orgidentifier=? and ");
    }

    if (projectId != null) {
      preparedSqlBuilder.append("projectidentifier=? and ");
    }

    preparedSqlBuilder.append("status in (");
    for (String status : statusList) {
      preparedSqlBuilder.append(String.format("'%s',", status));
    }

    preparedSqlBuilder.deleteCharAt(preparedSqlBuilder.length() - 1);

    if (startInterval > 0 && endInterval > 0) {
      preparedSqlBuilder.append(String.format(") and startts>=%s and startts<%s", startInterval, endInterval));
    } else {
      preparedSqlBuilder.append(String.format(")"));
    }

    preparedSqlBuilder.append(String.format(" and startts is not null ORDER BY startts DESC LIMIT %s", days));

    int totalTries = 0;
    int parameterIndex = 1;
    boolean success = false;
    String value = null;
    while (!success && totalTries <= MAX_RETRY_COUNT) {
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(preparedSqlBuilder.toString())) {
        if (accountId != null) {
          statement.setString(parameterIndex++, accountId);
        }
        if (orgId != null) {
          statement.setString(parameterIndex++, orgId);
        }
        if (projectId != null) {
          statement.setString(parameterIndex, projectId);
        }
        success = true;
        value = statement.toString();
      } catch (SQLException e) {
        totalTries++;
      }
    }
    return value;
  }

  public String queryBuilderSelectIdLimitTimeCdTable(String accountId, String orgId, String projectId, long days,
      List<String> statusList, long startInterval, long endInterval) {
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

    if (startInterval > 0 && endInterval > 0) {
      totalBuildSqlBuilder.append(String.format(") and startts>=%s and startts<%s", startInterval, endInterval));
    } else {
      totalBuildSqlBuilder.append(String.format(")"));
    }

    totalBuildSqlBuilder.append(String.format(" and startts is not null ORDER BY startts DESC LIMIT %s", days));

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

  public String queryBuilderStatusNew(String accountId, String orgId, String projectId, long days,
      List<String> statusList, long startInterval, long endInterval) {
    String queryPrepared =
        queryBuilderStatusPrepared(accountId, orgId, projectId, days, statusList, startInterval, endInterval);
    return (queryPrepared != null)
        ? queryPrepared
        : queryBuilderStatus(accountId, orgId, projectId, days, statusList, startInterval, endInterval);
  }

  public String queryBuilderStatusPrepared(String accountId, String orgId, String projectId, long days,
      List<String> statusList, long startInterval, long endInterval) {
    String selectStatusQuery = "select " + executionStatusCdTimeScaleColumns() + " from " + tableNameCD + " where ";
    StringBuilder preparedSqlBuilder = new StringBuilder(200);
    preparedSqlBuilder.append(selectStatusQuery);

    if (accountId != null) {
      preparedSqlBuilder.append("accountid=? and ");
    }

    if (orgId != null) {
      preparedSqlBuilder.append("orgidentifier=? and ");
    }

    if (projectId != null) {
      preparedSqlBuilder.append("projectidentifier=? and ");
    }

    preparedSqlBuilder.append("status in (");
    for (String status : statusList) {
      preparedSqlBuilder.append(String.format("'%s',", status));
    }

    preparedSqlBuilder.deleteCharAt(preparedSqlBuilder.length() - 1);

    if (startInterval > 0 && endInterval > 0) {
      preparedSqlBuilder.append(String.format(") and startts>=%s and startts<%s", startInterval, endInterval));
    } else {
      preparedSqlBuilder.append(String.format(")"));
    }

    preparedSqlBuilder.append(String.format(" and startts is not null ORDER BY startts DESC LIMIT %s;", days));

    int totalTries = 0;
    int parameterIndex = 1;
    boolean success = false;
    String value = null;
    while (!success && totalTries <= MAX_RETRY_COUNT) {
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(preparedSqlBuilder.toString())) {
        if (accountId != null) {
          statement.setString(parameterIndex++, accountId);
        }
        if (orgId != null) {
          statement.setString(parameterIndex++, orgId);
        }
        if (projectId != null) {
          statement.setString(parameterIndex, projectId);
        }
        success = true;
        value = statement.toString();
      } catch (SQLException e) {
        totalTries++;
      }
    }
    return value;
  }

  public String queryBuilderStatus(String accountId, String orgId, String projectId, long days, List<String> statusList,
      long startInterval, long endInterval) {
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

    if (startInterval > 0 && endInterval > 0) {
      totalBuildSqlBuilder.append(String.format(") and startts>=%s and startts<%s", startInterval, endInterval));
    } else {
      totalBuildSqlBuilder.append(String.format(")"));
    }

    totalBuildSqlBuilder.append(String.format(" and startts is not null ORDER BY startts DESC LIMIT %s;", days));

    return totalBuildSqlBuilder.toString();
  }

  public String queryBuilderServiceTag(String queryIdCdTable) {
    return queryBuilderServiceTag(queryIdCdTable, null);
  }

  public String queryBuilderServiceTag(String queryIdCdTable, String serviceId) {
    String selectStatusQuery =
        "select service_name,service_id,tag,env_id,env_name,env_type,artifact_image,pipeline_execution_summary_cd_id, infrastructureidentifier, infrastructureName from "
        + tableNameServiceAndInfra + " where ";
    StringBuilder totalBuildSqlBuilder = new StringBuilder(20480);

    totalBuildSqlBuilder.append(String.format(
        selectStatusQuery + "pipeline_execution_summary_cd_id in (%s) and service_name is not null", queryIdCdTable));

    if (serviceId != null) {
      totalBuildSqlBuilder.append(String.format(" and service_id='%s'", serviceId));
    }
    totalBuildSqlBuilder.append(';');
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
    HealthDeploymentDetails healthDeploymentDetails =
        healthDeploymentDashboardHelper(accountId, orgId, projectId, startInterval, endInterval, previousStartInterval);

    return HealthDeploymentDashboard.builder()
        .healthDeploymentInfo(HealthDeploymentInfo.builder()
                                  .total(TotalDeploymentInfo.builder()
                                             .count(healthDeploymentDetails.getTotal())
                                             .production(healthDeploymentDetails.getProduction())
                                             .rate(getRate(healthDeploymentDetails.getTotal(),
                                                 healthDeploymentDetails.getPreviousDeployment()))
                                             .nonProduction(healthDeploymentDetails.getNonProduction())
                                             .countList(healthDeploymentDetails.getTotalDateAndCount())
                                             .build())
                                  .success(DeploymentInfo.builder()
                                               .count(healthDeploymentDetails.getCurrentSuccess())
                                               .rate(getRate(healthDeploymentDetails.getCurrentSuccess(),
                                                   healthDeploymentDetails.getPreviousSuccess()))
                                               .countList(healthDeploymentDetails.getSuccessDateAndCount())
                                               .build())
                                  .failure(DeploymentInfo.builder()
                                               .count(healthDeploymentDetails.getCurrentFailed())
                                               .rate(getRate(healthDeploymentDetails.getCurrentFailed(),
                                                   healthDeploymentDetails.getPreviousFailed()))
                                               .countList(healthDeploymentDetails.getFailedDateAndCount())
                                               .build())
                                  .active(DeploymentInfo.builder()
                                              .count(healthDeploymentDetails.getCurrentActive())
                                              .rate(getRate(healthDeploymentDetails.getCurrentActive(),
                                                  healthDeploymentDetails.getPreviousActive()))
                                              .countList(healthDeploymentDetails.getActiveDateAndCount())
                                              .build())
                                  .build())
        .build();
  }

  @Override
  public HealthDeploymentDashboardV2 getHealthDeploymentDashboardV2(String accountId, String orgId, String projectId,
      long startInterval, long endInterval, long previousStartInterval) {
    HealthDeploymentDetails healthDeploymentDetails =
        healthDeploymentDashboardHelper(accountId, orgId, projectId, startInterval, endInterval, previousStartInterval);

    return HealthDeploymentDashboardV2.builder()
        .healthDeploymentInfo(
            HealthDeploymentInfoV2.builder()
                .total(TotalDeploymentInfoV2.builder()
                           .count(healthDeploymentDetails.getTotal())
                           .production(healthDeploymentDetails.getProduction())
                           .rate(calculateChangeRateV2(
                               healthDeploymentDetails.getPreviousDeployment(), healthDeploymentDetails.getTotal()))
                           .nonProduction(healthDeploymentDetails.getNonProduction())
                           .countList(healthDeploymentDetails.getTotalDateAndCount())
                           .build())
                .success(DeploymentInfoV2.builder()
                             .count(healthDeploymentDetails.getCurrentSuccess())
                             .rate(calculateChangeRateV2(healthDeploymentDetails.getPreviousSuccess(),
                                 healthDeploymentDetails.getCurrentSuccess()))
                             .countList(healthDeploymentDetails.getSuccessDateAndCount())
                             .build())
                .failure(DeploymentInfoV2.builder()
                             .count(healthDeploymentDetails.getCurrentFailed())
                             .rate(calculateChangeRateV2(healthDeploymentDetails.getPreviousFailed(),
                                 healthDeploymentDetails.getCurrentFailed()))
                             .countList(healthDeploymentDetails.getFailedDateAndCount())
                             .build())
                .active(DeploymentInfoV2.builder()
                            .count(healthDeploymentDetails.getCurrentActive())
                            .rate(calculateChangeRateV2(healthDeploymentDetails.getPreviousActive(),
                                healthDeploymentDetails.getCurrentActive()))
                            .countList(healthDeploymentDetails.getActiveDateAndCount())
                            .build())
                .build())
        .build();
  }

  public HealthDeploymentDetails healthDeploymentDashboardHelper(String accountId, String orgId, String projectId,
      long startInterval, long endInterval, long previousStartInterval) {
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
    long currentActive = 0;
    long previousSuccess = 0;
    long previousFailed = 0;
    long previousDeployment = 0;
    long previousActive = 0;

    HashMap<Long, Integer> totalCountMap = new HashMap<>();
    HashMap<Long, Integer> successCountMap = new HashMap<>();
    HashMap<Long, Integer> failedCountMap = new HashMap<>();
    HashMap<Long, Integer> activeCountMap = new HashMap<>();

    long startDateCopy = startInterval;
    long endDateCopy = endInterval;

    long timeUnitPerDay = getTimeUnitToGroupBy(DAY);
    while (startDateCopy < endDateCopy) {
      totalCountMap.put(startDateCopy, 0);
      successCountMap.put(startDateCopy, 0);
      failedCountMap.put(startDateCopy, 0);
      activeCountMap.put(startDateCopy, 0);
      startDateCopy = startDateCopy + timeUnitPerDay;
    }

    for (int i = 0; i < time.size(); i++) {
      long currentTimeEpoch = time.get(i);
      if (currentTimeEpoch >= startInterval && currentTimeEpoch < endInterval) {
        currentTimeEpoch = getStartingDateEpochValue(currentTimeEpoch, startInterval);
        total++;
        totalCountMap.put(currentTimeEpoch, totalCountMap.get(currentTimeEpoch) + 1);
        if (CDDashboardServiceHelper.successStatusList.contains(status.get(i))) {
          currentSuccess++;
          successCountMap.put(currentTimeEpoch, successCountMap.get(currentTimeEpoch) + 1);
        } else if (activeStatusList.contains(status.get(i)) || pendingStatusList.contains(status.get(i))) {
          currentActive++;
          activeCountMap.put(currentTimeEpoch, activeCountMap.get(currentTimeEpoch) + 1);
        } else {
          currentFailed++;
          failedCountMap.put(currentTimeEpoch, failedCountMap.get(currentTimeEpoch) + 1);
        }
      } else {
        previousDeployment++;
        if (CDDashboardServiceHelper.successStatusList.contains(status.get(i))) {
          previousSuccess++;
        } else if (activeStatusList.contains(status.get(i)) || pendingStatusList.contains(status.get(i))) {
          previousActive++;
        } else {
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
    List<DeploymentDateAndCount> activeDateAndCount = new ArrayList<>();

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
      activeDateAndCount.add(DeploymentDateAndCount.builder()
                                 .time(startDateCopy)
                                 .deployments(Deployment.builder().count(activeCountMap.get(startDateCopy)).build())
                                 .build());
      startDateCopy = startDateCopy + timeUnitPerDay;
    }

    return HealthDeploymentDetails.builder()
        .total(total)
        .currentActive(currentActive)
        .currentFailed(currentFailed)
        .currentSuccess(currentSuccess)
        .previousDeployment(previousDeployment)
        .previousActive(previousActive)
        .previousFailed(previousFailed)
        .previousSuccess(previousSuccess)
        .production(production)
        .nonProduction(nonProduction)
        .totalDateAndCount(totalDateAndCount)
        .activeDateAndCount(activeDateAndCount)
        .failedDateAndCount(failedDateAndCount)
        .successDateAndCount(successDateAndCount)
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

  public Pair<HashMap<String, List<ServiceDeploymentInfo>>, HashMap<String, List<EnvironmentDeploymentsInfo>>>
  queryCalculatorServiceTagMag(String queryServiceTag) {
    HashMap<String, List<ServiceDeploymentInfo>> serviceTagMap = new HashMap<>();
    HashMap<String, EnvironmentDeploymentsInfo> envIdToNameAndTypeMap = new HashMap<>();
    HashMap<String, HashMap<String, List<InfrastructureInfo>>> pipelineEnvInfraMap = new HashMap<>();

    int totalTries = 0;
    boolean successfulOperation = false;
    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      ResultSet resultSet = null;
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(queryServiceTag)) {
        resultSet = statement.executeQuery();
        while (resultSet != null && resultSet.next()) {
          String pipeline_execution_summary_cd_id = resultSet.getString(PIPELINE_EXECUTION_SUMMARY_CD_ID);
          String service_name = resultSet.getString(SERVICE_NAME);
          String service_id = resultSet.getString(SERVICE_ID);
          String tag = resultSet.getString("tag");
          String envId = resultSet.getString("env_id");
          String envName = resultSet.getString("env_name");
          String envType = resultSet.getString("env_type");
          String image = resultSet.getString("artifact_image");
          String infrastructureIdentifier = resultSet.getString("infrastructureidentifier");
          String infrastructureName = resultSet.getString("infrastructureName");
          if (serviceTagMap.containsKey(pipeline_execution_summary_cd_id)) {
            serviceTagMap.get(pipeline_execution_summary_cd_id)
                .add(getServiceDeployment(service_name, tag, image, service_id));
          } else {
            List<ServiceDeploymentInfo> serviceDeploymentInfos = new ArrayList<>();
            serviceDeploymentInfos.add(getServiceDeployment(service_name, tag, image, service_id));
            serviceTagMap.put(pipeline_execution_summary_cd_id, serviceDeploymentInfos);
          }
          envIdToNameAndTypeMap.putIfAbsent(
              envId, EnvironmentDeploymentsInfo.builder().envType(envType).envName(envName).build());

          pipelineEnvInfraMap.putIfAbsent(pipeline_execution_summary_cd_id, new HashMap<>());
          pipelineEnvInfraMap.get(pipeline_execution_summary_cd_id).putIfAbsent(envId, new ArrayList<>());
          pipelineEnvInfraMap.get(pipeline_execution_summary_cd_id)
              .get(envId)
              .add(InfrastructureInfo.builder()
                       .infrastructureName(infrastructureName)
                       .infrastructureIdentifier(infrastructureIdentifier)
                       .build());
        }
        successfulOperation = true;
      } catch (SQLException ex) {
        totalTries++;
      } finally {
        DBUtils.close(resultSet);
      }
    }
    return new MutablePair<>(serviceTagMap,
        getPipelineExecutionIdToEnvironmentDeploymentsInfoMap(envIdToNameAndTypeMap, pipelineEnvInfraMap));
  }

  private HashMap<String, List<EnvironmentDeploymentsInfo>> getPipelineExecutionIdToEnvironmentDeploymentsInfoMap(
      HashMap<String, EnvironmentDeploymentsInfo> envIdToNameAndTypeMap,
      HashMap<String, HashMap<String, List<InfrastructureInfo>>> pipelineEnvInfraMap) {
    HashMap<String, List<EnvironmentDeploymentsInfo>> pipelineExecutionIdToEnvironmentDeploymentsInfoMap =
        new HashMap<>();
    for (Map.Entry<String, HashMap<String, List<InfrastructureInfo>>> entry : pipelineEnvInfraMap.entrySet()) {
      String pipelineExecutionId = entry.getKey();
      HashMap<String, List<InfrastructureInfo>> envInfraMap = entry.getValue();
      List<EnvironmentDeploymentsInfo> environmentDeploymentsInfoList = new ArrayList<>();
      for (Map.Entry<String, List<InfrastructureInfo>> entry1 : envInfraMap.entrySet()) {
        String envId = entry1.getKey();
        List<InfrastructureInfo> infrastructureDetails = entry1.getValue();
        environmentDeploymentsInfoList.add(EnvironmentDeploymentsInfo.builder()
                                               .envId(envId)
                                               .envName(envIdToNameAndTypeMap.get(envId).getEnvName())
                                               .envType(envIdToNameAndTypeMap.get(envId).getEnvType())
                                               .infrastructureDetails(infrastructureDetails)
                                               .build());
      }
      pipelineExecutionIdToEnvironmentDeploymentsInfoMap.putIfAbsent(
          pipelineExecutionId, environmentDeploymentsInfoList);
    }
    return pipelineExecutionIdToEnvironmentDeploymentsInfoMap;
  }

  @Override
  public io.harness.ng.overview.dto.ExecutionDeploymentInfo getExecutionDeploymentDashboard(
      String accountId, String orgId, String projectId, long startInterval, long endInterval) {
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
      if (CDDashboardServiceHelper.successStatusList.contains(status.get(i))) {
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

  @Override
  public Map<String, String> getLastPipeline(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, List<String> serviceIds) {
    Map<String, String> serviceIdToPipelineId = new HashMap<>();
    List<String> serviceRefs = serviceIds.stream()
                                   .map(serviceId
                                       -> IdentifierRefHelper.getRefFromIdentifierOrRef(
                                           accountIdentifier, orgIdentifier, projectIdentifier, serviceId))
                                   .collect(Collectors.toList());

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
        statement.setArray(4, connection.createArrayOf("VARCHAR", serviceRefs.toArray()));
        resultSet = statement.executeQuery();
        while (resultSet != null && resultSet.next()) {
          String service_id = resultSet.getString(SERVICE_ID);
          String pipeline_execution_summary_cd_id = resultSet.getString(PIPELINE_EXECUTION_SUMMARY_CD_ID);
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

  @Override
  public Map<String, String> getLastPipeline(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      Set<String> serviceIds, Set<String> envIds) {
    Map<String, String> serviceIdToPipelineId = new HashMap<>();
    List<String> serviceRefs = serviceIds.stream()
                                   .map(serviceId
                                       -> IdentifierRefHelper.getRefFromIdentifierOrRef(
                                           accountIdentifier, orgIdentifier, projectIdentifier, serviceId))
                                   .collect(Collectors.toList());

    List<String> envRefs = envIds.stream()
                               .map(envId
                                   -> IdentifierRefHelper.getRefFromIdentifierOrRef(
                                       accountIdentifier, orgIdentifier, projectIdentifier, envId))
                               .collect(Collectors.toList());

    String query =
        "select distinct on(env_id, service_id) service_id, env_id, pipeline_execution_summary_cd_id, service_startts from "
        + "service_infra_info where accountid=? and orgidentifier=? and projectidentifier=? and service_id = any (?) and env_id = any (?) "
        + "group by service_id, env_id, pipeline_execution_summary_cd_id, service_startts "
        + "order by env_id, service_id, service_startts desc";

    int totalTries = 0;
    boolean successfulOperation = false;
    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      ResultSet resultSet = null;
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(query)) {
        statement.setString(1, accountIdentifier);
        statement.setString(2, orgIdentifier);
        statement.setString(3, projectIdentifier);
        statement.setArray(4, connection.createArrayOf("VARCHAR", serviceRefs.toArray()));
        statement.setArray(5, connection.createArrayOf("VARCHAR", envRefs.toArray()));

        resultSet = statement.executeQuery();
        while (resultSet != null && resultSet.next()) {
          String service_id = resultSet.getString(SERVICE_ID);
          String env_id = resultSet.getString("env_id");
          String service_env_id = service_id + '-' + env_id;
          String pipeline_execution_summary_cd_id = resultSet.getString(PIPELINE_EXECUTION_SUMMARY_CD_ID);
          serviceIdToPipelineId.putIfAbsent(service_env_id, pipeline_execution_summary_cd_id);
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

    String query =
        "select service_id, deployment_type, gitOpsEnabled from service_infra_info where accountid=? and orgidentifier=? "
        + "and projectidentifier=? and service_id = any (?) group by service_id, deployment_type, gitOpsEnabled";

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
          String service_id = resultSet.getString(SERVICE_ID);
          String deployment_type = resultSet.getString("deployment_type");
          boolean gitOpsEnabled = resultSet.getBoolean("gitOpsEnabled");
          serviceIdToDeploymentType.putIfAbsent(service_id, new HashSet<>());
          if (gitOpsEnabled) {
            serviceIdToDeploymentType.get(service_id).add("KubernetesGitOps");
          } else {
            serviceIdToDeploymentType.get(service_id).add(deployment_type);
          }
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
      String projectIdentifier, long startTime, long endTime, List<String> sort) throws Exception {
    long numberOfDays = getNumberOfDays(startTime, endTime);
    if (numberOfDays < 0) {
      throw new Exception("start date should be less than or equal to end date");
    }
    long previousStartTime = getStartTimeOfPreviousInterval(startTime, numberOfDays);

    List<ServiceEntity> services =
        serviceEntityServiceImpl.getAllNonDeletedServices(accountIdentifier, orgIdentifier, projectIdentifier, sort);

    List<WorkloadDeploymentInfo> workloadDeploymentInfoList = getDashboardWorkloadDeployment(
        accountIdentifier, orgIdentifier, projectIdentifier, startTime, endTime, previousStartTime, null)
                                                                  .getWorkloadDeploymentInfoList();
    Map<String, WorkloadDeploymentInfo> serviceIdToWorkloadDeploymentInfo = new HashMap<>();
    workloadDeploymentInfoList.forEach(
        item -> serviceIdToWorkloadDeploymentInfo.putIfAbsent(item.getServiceId(), item));

    List<String> serviceIdentifiers = services.stream().map(ServiceEntity::getIdentifier).collect(Collectors.toList());
    List<String> serviceRefs = serviceIdentifiers.stream()
                                   .map(serviceId
                                       -> IdentifierRefHelper.getRefFromIdentifierOrRef(
                                           accountIdentifier, orgIdentifier, projectIdentifier, serviceId))
                                   .collect(Collectors.toList());
    Map<String, String> serviceIdToPipelineIdMap =
        getLastPipeline(accountIdentifier, orgIdentifier, projectIdentifier, serviceRefs);

    List<String> pipelineExecutionIdList = serviceIdToPipelineIdMap.values().stream().collect(Collectors.toList());

    // Gets all the details for the pipeline execution id's in the list and stores it in a map.
    Map<String, ServicePipelineInfo> pipelineExecutionDetailsMap = getPipelineExecutionDetails(pipelineExecutionIdList);

    Map<String, Set<String>> serviceIdToDeploymentTypeMap =
        getDeploymentType(accountIdentifier, orgIdentifier, projectIdentifier, serviceRefs);

    Map<String, InstanceCountDetailsByEnvTypeBase> serviceIdToInstanceCountDetails =
        instanceDashboardService
            .getActiveServiceInstanceCountBreakdown(
                accountIdentifier, orgIdentifier, projectIdentifier, serviceRefs, getCurrentTime())
            .getInstanceCountDetailsByEnvTypeBaseMap();

    List<ServiceDetailsDTO> serviceDeploymentInfoList =
        services.stream()
            .map(service -> {
              final String serviceId = service.getIdentifier();
              final String serviceRef = IdentifierRefHelper.getRefFromIdentifierOrRef(
                  accountIdentifier, orgIdentifier, projectIdentifier, serviceId);
              final String pipelineId = serviceIdToPipelineIdMap.getOrDefault(serviceRef, null);

              ServiceDetailsDTOBuilder serviceDetailsDTOBuilder = ServiceDetailsDTO.builder();
              serviceDetailsDTOBuilder.serviceName(service.getName());
              serviceDetailsDTOBuilder.description(service.getDescription());
              serviceDetailsDTOBuilder.tags(TagMapper.convertToMap(service.getTags()));
              serviceDetailsDTOBuilder.serviceIdentifier(serviceId);
              serviceDetailsDTOBuilder.deploymentTypeList(serviceIdToDeploymentTypeMap.getOrDefault(serviceRef, null));
              serviceDetailsDTOBuilder.instanceCountDetails(
                  serviceIdToInstanceCountDetails.getOrDefault(serviceRef, null));

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

  public Map<String, Set<IconDTO>> getDeploymentIconMap(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, List<ServiceEntity> services, Map<String, Set<String>> serviceIdToDeploymentTypeMap) {
    Map<String, Set<IconDTO>> serviceIdToDeploymentIconMap = new HashMap<>();
    Map<String, String> serviceRefToTemplateRef = new HashMap<>();
    Map<Scope, List<String>> templateScopeToIds = new HashMap<>();
    Map<String, String> templateRefToIcon = new HashMap<>();

    try {
      services.forEach(serviceEntity
          -> getServiceToTemplateRef(serviceIdToDeploymentTypeMap.get(serviceEntity.getIdentifier()),
              serviceEntity.getYaml(), serviceEntity.getIdentifier(), templateScopeToIds, serviceRefToTemplateRef));

      getTemplateRefToIcon(accountIdentifier, orgIdentifier, projectIdentifier, templateRefToIcon, templateScopeToIds);

      services.forEach(serviceEntity
          -> setServiceToIconList(templateRefToIcon, serviceRefToTemplateRef, serviceEntity.getIdentifier(),
              serviceIdToDeploymentTypeMap.get(serviceEntity.getIdentifier()), serviceIdToDeploymentIconMap));

    } catch (Exception e) {
      log.error("Not able to fetch icons for services ", e);
    }

    return serviceIdToDeploymentIconMap;
  }

  private void setServiceToIconList(Map<String, String> templateRefToIcon, Map<String, String> serviceRefToTemplateRef,
      String serviceId, Set<String> deploymentType, Map<String, Set<IconDTO>> serviceIdToDeploymentIconMap) {
    if (isNull(deploymentType)) {
      return;
    }
    String templateRef = serviceRefToTemplateRef.get(serviceId);
    String icon = "";
    if (!isEmpty(templateRef) && !isEmpty(templateRefToIcon.get(IdentifierRefHelper.getIdentifier(templateRef)))) {
      icon = templateRefToIcon.get(IdentifierRefHelper.getIdentifier(templateRef));
    }
    Set<IconDTO> iconDTOSet = new HashSet<>();
    String finalIcon = icon;
    deploymentType.forEach(deployment -> setIconToIconSet(iconDTOSet, deployment, finalIcon));
    serviceIdToDeploymentIconMap.put(serviceId, iconDTOSet);
  }
  private void setIconToIconSet(Set<IconDTO> iconDTOSet, String deployment, String icon) {
    if (CUSTOM_DEPLOYMENT.equals(deployment)) {
      iconDTOSet.add(IconDTO.builder().deploymentType(deployment).icon(icon).build());
    } else {
      iconDTOSet.add(IconDTO.builder().deploymentType(deployment).icon("").build());
    }
  }

  private void getServiceToTemplateRef(Set<String> deploymentType, String yaml, String serviceIdentifier,
      Map<Scope, List<String>> templateScopeToIds, Map<String, String> serviceRefToTemplateRef) {
    if (isEmpty(deploymentType)) {
      return;
    }
    if (deploymentType.contains(CUSTOM_DEPLOYMENT)) {
      String templateRef;
      YamlConfig yamlConfig = new YamlConfig(yaml);
      JsonNode serviceYaml = yamlConfig.getYamlMap().get("service");
      if (!isNull(serviceYaml)) {
        JsonNode serviceDefinition = serviceYaml.get("serviceDefinition");
        if (!isNull(serviceDefinition)) {
          JsonNode spec = serviceDefinition.get("spec");
          if (!isNull(spec)) {
            JsonNode customDeploymentRef = spec.get("customDeploymentRef");
            if (!isNull(customDeploymentRef)) {
              JsonNode template = customDeploymentRef.get("templateRef");
              if (!isNull(template)) {
                templateRef = template.asText();
                addTemplateByScope(templateRef, templateScopeToIds);
                serviceRefToTemplateRef.put(serviceIdentifier, templateRef);
              }
            }
          }
        }
      }
    }
  }

  private void addTemplateByScope(String templateRef, Map<Scope, List<String>> templateScopeToIds) {
    if (templateRef.contains(ACCOUNT_IDENTIFIER)) {
      if (!templateScopeToIds.containsKey(Scope.ACCOUNT)) {
        templateScopeToIds.put(Scope.ACCOUNT, new ArrayList<>());
      }
      templateScopeToIds.get(Scope.ACCOUNT).add(templateRef.replace(ACCOUNT_IDENTIFIER, ""));
    } else if (templateRef.contains(ORG_IDENTIFIER)) {
      if (!templateScopeToIds.containsKey(Scope.ORG)) {
        templateScopeToIds.put(Scope.ORG, new ArrayList<>());
      }
      templateScopeToIds.get(Scope.ORG).add(templateRef.replace(ORG_IDENTIFIER, ""));
    } else {
      if (!templateScopeToIds.containsKey(Scope.PROJECT)) {
        templateScopeToIds.put(Scope.PROJECT, new ArrayList<>());
      }
      templateScopeToIds.get(Scope.PROJECT).add(templateRef);
    }
  }

  private void getTemplateRefToIcon(String accountId, String orgId, String projectId,
      Map<String, String> templateRefToIcon, Map<Scope, List<String>> templateScopeToIds) {
    for (Map.Entry<Scope, List<String>> templateIds : templateScopeToIds.entrySet()) {
      if (!isEmpty(templateIds.getValue())) {
        TemplateFilterPropertiesDTO templateFilterPropertiesDTO =
            TemplateFilterPropertiesDTO.builder()
                .templateEntityTypes(Collections.singletonList(TemplateEntityType.CUSTOM_DEPLOYMENT_TEMPLATE))
                .templateIdentifiers(templateIds.getValue())
                .build();
        List<TemplateMetadataSummaryResponseDTO> templates;
        switch (templateIds.getKey()) {
          case ACCOUNT:
            templates = NGRestUtils
                            .getResponse(templateResourceClient.listTemplateMetadata(accountId, null, null,
                                STABLE_TEMPLATE_TYPE, 0, templateIds.getValue().size(), templateFilterPropertiesDTO))
                            .getContent();
            break;
          case ORG:
            templates = NGRestUtils
                            .getResponse(templateResourceClient.listTemplateMetadata(accountId, orgId, null,
                                STABLE_TEMPLATE_TYPE, 0, templateIds.getValue().size(), templateFilterPropertiesDTO))
                            .getContent();
            break;
          default:
            templates = NGRestUtils
                            .getResponse(templateResourceClient.listTemplateMetadata(accountId, orgId, projectId,
                                STABLE_TEMPLATE_TYPE, 0, templateIds.getValue().size(), templateFilterPropertiesDTO))
                            .getContent();
        }

        templates.forEach(template -> templateRefToIcon.put(template.getIdentifier(), template.getIcon()));
      }
    }
  }
  @Override
  public ServiceDetailsInfoDTOV2 getServiceDetailsListV2(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, long startTime, long endTime, List<String> sort) throws Exception {
    long numberOfDays = getNumberOfDays(startTime, endTime);
    if (numberOfDays < 0) {
      throw new Exception("start date should be less than or equal to end date");
    }
    long previousStartTime = getStartTimeOfPreviousInterval(startTime, numberOfDays);

    List<ServiceEntity> services =
        serviceEntityServiceImpl.getAllNonDeletedServices(accountIdentifier, orgIdentifier, projectIdentifier, sort);

    List<WorkloadDeploymentInfoV2> workloadDeploymentInfoList = getDashboardWorkloadDeploymentV2(
        accountIdentifier, orgIdentifier, projectIdentifier, startTime, endTime, previousStartTime, null)
                                                                    .getWorkloadDeploymentInfoList();
    Map<String, WorkloadDeploymentInfoV2> serviceIdToWorkloadDeploymentInfo = new HashMap<>();
    workloadDeploymentInfoList.forEach(
        item -> serviceIdToWorkloadDeploymentInfo.putIfAbsent(item.getServiceId(), item));

    List<String> serviceIdentifiers = services.stream().map(ServiceEntity::getIdentifier).collect(Collectors.toList());
    List<String> serviceRefs = serviceIdentifiers.stream()
                                   .map(serviceId
                                       -> IdentifierRefHelper.getRefFromIdentifierOrRef(
                                           accountIdentifier, orgIdentifier, projectIdentifier, serviceId))
                                   .collect(Collectors.toList());
    Map<String, String> serviceIdToPipelineIdMap =
        getLastPipeline(accountIdentifier, orgIdentifier, projectIdentifier, serviceRefs);

    List<String> pipelineExecutionIdList = serviceIdToPipelineIdMap.values().stream().collect(Collectors.toList());

    // Gets all the details for the pipeline execution id's in the list and stores it in a map.
    Map<String, ServicePipelineInfo> pipelineExecutionDetailsMap = getPipelineExecutionDetails(pipelineExecutionIdList);

    Map<String, Set<String>> serviceIdToDeploymentTypeMap =
        getDeploymentType(accountIdentifier, orgIdentifier, projectIdentifier, serviceRefs);

    Map<String, Set<IconDTO>> serviceIdToDeploymentIconMap = getDeploymentIconMap(
        accountIdentifier, orgIdentifier, projectIdentifier, services, serviceIdToDeploymentTypeMap);

    Map<String, InstanceCountDetailsByEnvTypeBase> serviceIdToInstanceCountDetails =
        instanceDashboardService
            .getActiveServiceInstanceCountBreakdown(
                accountIdentifier, orgIdentifier, projectIdentifier, serviceRefs, getCurrentTime())
            .getInstanceCountDetailsByEnvTypeBaseMap();

    List<ServiceDetailsDTOV2> serviceDeploymentInfoList =
        services.stream()
            .map(service -> {
              final String serviceId = service.getIdentifier();
              final String serviceRef = IdentifierRefHelper.getRefFromIdentifierOrRef(
                  accountIdentifier, orgIdentifier, projectIdentifier, serviceId);

              final String pipelineId = serviceIdToPipelineIdMap.getOrDefault(serviceRef, null);

              ServiceDetailsDTOV2Builder serviceDetailsDTOBuilder = ServiceDetailsDTOV2.builder();
              serviceDetailsDTOBuilder.serviceName(service.getName());
              serviceDetailsDTOBuilder.description(service.getDescription());
              serviceDetailsDTOBuilder.tags(TagMapper.convertToMap(service.getTags()));
              serviceDetailsDTOBuilder.serviceIdentifier(serviceId);
              serviceDetailsDTOBuilder.deploymentIconList(serviceIdToDeploymentIconMap.getOrDefault(serviceId, null));
              serviceDetailsDTOBuilder.deploymentTypeList(serviceIdToDeploymentTypeMap.getOrDefault(serviceId, null));
              serviceDetailsDTOBuilder.instanceCountDetails(
                  serviceIdToInstanceCountDetails.getOrDefault(serviceRef, null));

              serviceDetailsDTOBuilder.lastPipelineExecuted(pipelineExecutionDetailsMap.getOrDefault(pipelineId, null));

              if (serviceIdToWorkloadDeploymentInfo.containsKey(serviceRef)) {
                final WorkloadDeploymentInfoV2 workloadDeploymentInfo =
                    serviceIdToWorkloadDeploymentInfo.get(serviceRef);
                serviceDetailsDTOBuilder.totalDeployments(workloadDeploymentInfo.getTotalDeployments());
                serviceDetailsDTOBuilder.totalDeploymentChangeRate(
                    workloadDeploymentInfo.getTotalDeploymentChangeRate());
                serviceDetailsDTOBuilder.successRate(workloadDeploymentInfo.getPercentSuccess());
                serviceDetailsDTOBuilder.successRateChangeRate(workloadDeploymentInfo.getRateSuccess());
                serviceDetailsDTOBuilder.failureRate(workloadDeploymentInfo.getFailureRate());
                serviceDetailsDTOBuilder.failureRateChangeRate(workloadDeploymentInfo.getFailureRateChangeRate());
                serviceDetailsDTOBuilder.frequency(workloadDeploymentInfo.getFrequency());
                serviceDetailsDTOBuilder.frequencyChangeRate(workloadDeploymentInfo.getFrequencyChangeRate());
              } else {
                ChangeRate changeRate = calculateChangeRateV2(0, 0);
                serviceDetailsDTOBuilder.totalDeploymentChangeRate(changeRate);
                serviceDetailsDTOBuilder.successRateChangeRate(changeRate);
                serviceDetailsDTOBuilder.failureRateChangeRate(changeRate);
                serviceDetailsDTOBuilder.frequencyChangeRate(changeRate);
              }

              return serviceDetailsDTOBuilder.build();
            })
            .collect(Collectors.toList());

    return ServiceDetailsInfoDTOV2.builder().serviceDeploymentDetailsList(serviceDeploymentInfoList).build();
  }

  @Override
  public Map<String, ServicePipelineInfo> getPipelineExecutionDetails(List<String> pipelineExecutionIdList) {
    return getPipelineExecutionDetails(pipelineExecutionIdList, null);
  }

  public Map<String, ServicePipelineInfo> getPipelineExecutionDetails(
      List<String> pipelineExecutionIdList, List<String> statusList) {
    Map<String, ServicePipelineInfo> pipelineExecutionDetailsMap = new HashMap<>();
    int totalTries = 0;
    boolean successfulOperation = false;
    String sql;
    if (EmptyPredicate.isNotEmpty(statusList)) {
      sql = "select * from " + tableNameCD + " where id = any (?) and status = any (?);";
    } else {
      sql = "select * from " + tableNameCD + " where id = any (?);";
    }

    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      ResultSet resultSet = null;
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(sql)) {
        final Array array = connection.createArrayOf("VARCHAR", pipelineExecutionIdList.toArray());
        statement.setArray(1, array);
        if (EmptyPredicate.isNotEmpty(statusList)) {
          final Array statusArray = connection.createArrayOf("VARCHAR", statusList.toArray());
          statement.setArray(2, statusArray);
        }
        resultSet = statement.executeQuery();
        while (resultSet != null && resultSet.next()) {
          String pipelineExecutionId = resultSet.getString(NGPipelineSummaryCDConstants.ID);
          String pipelineName = resultSet.getString(NGPipelineSummaryCDConstants.NAME);
          String pipelineId = resultSet.getString(NGPipelineSummaryCDConstants.PIPELINE_IDENTIFIER);
          String status = resultSet.getString(NGPipelineSummaryCDConstants.STATUS);
          String planExecutionId = resultSet.getString(NGPipelineSummaryCDConstants.PLAN_EXECUTION_ID);
          String deployedByName = resultSet.getString(NGPipelineSummaryCDConstants.AUTHOR_NAME);
          String deployedById = resultSet.getString(NGPipelineSummaryCDConstants.AUTHOR_ID);

          long executionTime = Long.parseLong(resultSet.getString(NGPipelineSummaryCDConstants.START_TS));
          if (!pipelineExecutionDetailsMap.containsKey(pipelineExecutionId)) {
            pipelineExecutionDetailsMap.put(pipelineExecutionId,
                ServicePipelineInfo.builder()
                    .identifier(pipelineId)
                    .pipelineExecutionId(pipelineExecutionId)
                    .name(pipelineName)
                    .lastExecutedAt(executionTime)
                    .status(status)
                    .planExecutionId(planExecutionId)
                    .deployedByName(deployedByName)
                    .deployedById(deployedById)
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
  public Map<String, ServicePipelineWithRevertInfo> getPipelineExecutionDetailsWithRevertInfo(
      List<String> planExecutionIdList) {
    return getPipelineExecutionDetailsWithRevertInfo(planExecutionIdList, null);
  }

  public Map<String, ServicePipelineWithRevertInfo> getPipelineExecutionDetailsWithRevertInfo(
      List<String> planExecutionIdList, List<String> statusList) {
    Map<String, ServicePipelineWithRevertInfo> pipelineExecutionDetailsMap = new HashMap<>();
    int totalTries = 0;
    boolean successfulOperation = false;
    String sql;
    if (EmptyPredicate.isNotEmpty(statusList)) {
      sql = "select * from " + tableNameCD + " where planexecutionid = any (?) and status = any (?);";
    } else {
      sql = "select * from " + tableNameCD + " where planexecutionid = any (?);";
    }

    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      ResultSet resultSet = null;
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(sql)) {
        final Array array = connection.createArrayOf("VARCHAR", planExecutionIdList.toArray());
        statement.setArray(1, array);
        if (EmptyPredicate.isNotEmpty(statusList)) {
          final Array statusArray = connection.createArrayOf("VARCHAR", statusList.toArray());
          statement.setArray(2, statusArray);
        }
        resultSet = statement.executeQuery();
        while (resultSet != null && resultSet.next()) {
          String pipelineExecutionId = resultSet.getString(NGPipelineSummaryCDConstants.ID);
          String pipelineName = resultSet.getString(NGPipelineSummaryCDConstants.NAME);
          String pipelineId = resultSet.getString(NGPipelineSummaryCDConstants.PIPELINE_IDENTIFIER);
          String status = resultSet.getString(NGPipelineSummaryCDConstants.STATUS);
          String planExecutionId = resultSet.getString(NGPipelineSummaryCDConstants.PLAN_EXECUTION_ID);
          boolean isRevertExecution = resultSet.getBoolean(NGPipelineSummaryCDConstants.REVERT_EXECUTION);
          String deployedByName = resultSet.getString(NGPipelineSummaryCDConstants.AUTHOR_NAME);
          String deployedById = resultSet.getString(NGPipelineSummaryCDConstants.AUTHOR_ID);

          long executionTime = Long.parseLong(resultSet.getString(NGPipelineSummaryCDConstants.START_TS));
          if (!pipelineExecutionDetailsMap.containsKey(planExecutionId)) {
            pipelineExecutionDetailsMap.put(planExecutionId,
                ServicePipelineWithRevertInfo.builder()
                    .identifier(pipelineId)
                    .pipelineExecutionId(pipelineExecutionId)
                    .name(pipelineName)
                    .lastExecutedAt(executionTime)
                    .status(status)
                    .planExecutionId(planExecutionId)
                    .deployedByName(deployedByName)
                    .deployedById(deployedById)
                    .isRevertExecution(isRevertExecution)
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

  public Map<String, String> getPipelineExecutionStatusMap(List<String> pipelineExecutionIdList, String query) {
    Map<String, String> executionStatusMap = new HashMap<>();
    int totalTries = 0;
    boolean successfulOperation = false;

    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      ResultSet resultSet = null;
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(query)) {
        final Array array = connection.createArrayOf("VARCHAR", pipelineExecutionIdList.toArray());
        statement.setArray(1, array);
        resultSet = statement.executeQuery();
        while (resultSet != null && resultSet.next()) {
          String pipelineExecutionId = resultSet.getString(NGPipelineSummaryCDConstants.ID);
          String status = resultSet.getString(NGPipelineSummaryCDConstants.STATUS);
          executionStatusMap.put(pipelineExecutionId, status);
        }
        successfulOperation = true;
      } catch (SQLException ex) {
        log.error("{} after total tries = {}", ex, totalTries);
        totalTries++;
      } finally {
        DBUtils.close(resultSet);
      }
    }
    return executionStatusMap;
  }

  public List<String> getPipelineExecutionIdFromServiceInfraInfo(String query) {
    Set<String> ids = new HashSet<>();
    int totalTries = 0;
    boolean successfulOperation = false;
    ResultSet resultSet = null;
    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(query)) {
        resultSet = statement.executeQuery();
        while (resultSet != null && resultSet.next()) {
          String id = resultSet.getString(PIPELINE_EXECUTION_SUMMARY_CD_ID);
          if (EmptyPredicate.isNotEmpty(id)) {
            ids.add(id);
          }
        }
        successfulOperation = true;
      } catch (SQLException ex) {
        log.error("{} after total tries = {}", ex, totalTries);
        totalTries++;
      } finally {
        DBUtils.close(resultSet);
      }
    }
    return ids.stream().collect(Collectors.toList());
  }

  public Map<String, String> getPipelineExecutionIdAndFailureDetailsFromServiceInfraInfo(String query) {
    Map<String, String> idToFailureInfoMap = new HashMap<>();
    int totalTries = 0;
    boolean successfulOperation = false;
    ResultSet resultSet = null;
    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(query)) {
        resultSet = statement.executeQuery();
        while (resultSet != null && resultSet.next()) {
          String id = resultSet.getString(PIPELINE_EXECUTION_SUMMARY_CD_ID);
          String executionFailureDetails = resultSet.getString(EXECUTION_FAILURE_DETAILS);
          idToFailureInfoMap.put(id, executionFailureDetails);
        }
        successfulOperation = true;
      } catch (SQLException ex) {
        log.error("{} after total tries = {}", ex, totalTries);
        totalTries++;
      } finally {
        DBUtils.close(resultSet);
      }
    }
    return idToFailureInfoMap;
  }

  public Map<String, Pair<String, AuthorInfo>> getPipelineExecutionIdToTriggerTypeAndAuthorInfoMapping(
      List<String> pipelineExecutionIdList) {
    Map<String, Pair<String, AuthorInfo>> triggerAndAuthorInfoMap = new HashMap<>();

    int totalTries = 0;
    boolean successfulOperation = false;
    String sql =
        "select id, moduleinfo_author_id, author_avatar, trigger_type from " + tableNameCD + " where id = any (?);";
    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      ResultSet resultSet = null;
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(sql)) {
        final Array array = connection.createArrayOf("VARCHAR", pipelineExecutionIdList.toArray());
        statement.setArray(1, array);
        resultSet = statement.executeQuery();
        while (resultSet != null && resultSet.next()) {
          String pipelineExecutionId = resultSet.getString(NGPipelineSummaryCDConstants.ID);
          String authorId = resultSet.getString(NGPipelineSummaryCDConstants.AUTHOR_ID);
          String authorAvatar = resultSet.getString(NGPipelineSummaryCDConstants.AUTHOR_AVATAR);
          String triggerType = resultSet.getString(NGPipelineSummaryCDConstants.TRIGGER_TYPE);
          if (!triggerAndAuthorInfoMap.containsKey(pipelineExecutionId)) {
            triggerAndAuthorInfoMap.put(pipelineExecutionId,
                new MutablePair<>(triggerType, AuthorInfo.builder().name(authorId).url(authorAvatar).build()));
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
    return triggerAndAuthorInfoMap;
  }

  public PipelineExecutionCountInfo getPipelineExecutionCountInfo(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String serviceId, Long startInterval, Long endInterval, String artifactPath,
      String artifactVersion, String artifact, String status) {
    endInterval = DashboardServiceHelper.checkForDefaultEndInterval(endInterval);
    startInterval = DashboardServiceHelper.checkForDefaultStartInterval(startInterval, endInterval);
    if (!DashboardServiceHelper.validateDuration(startInterval, endInterval)) {
      throw new InvalidRequestException("startTime and endTime interval should be less than 6 months");
    }
    String queryArtifactDetails =
        DashboardServiceHelper.queryToFetchExecutionIdAndArtifactDetails(accountIdentifier, orgIdentifier,
            projectIdentifier, serviceId, startInterval, endInterval, artifactPath, artifactVersion, artifact);
    List<ServiceArtifactExecutionDetail> serviceArtifactExecutionDetailList =
        getExecutionIdAndArtifactDetails(queryArtifactDetails);
    List<String> ids = new ArrayList<>(serviceArtifactExecutionDetailList.stream()
                                           .map(ServiceArtifactExecutionDetail::getPipelineExecutionSummaryCDId)
                                           .collect(Collectors.toSet()));
    String queryExecutionStatus = DashboardServiceHelper.queryToFetchStatusOfExecution(
        accountIdentifier, orgIdentifier, projectIdentifier, status);
    Map<String, String> executionStatusMap = getPipelineExecutionStatusMap(ids, queryExecutionStatus);
    return DashboardServiceHelper.getPipelineExecutionCountInfoHelper(
        serviceArtifactExecutionDetailList, executionStatusMap);
  }
  @Override
  public ServiceSequence getCustomSequence(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceId) {
    Optional<ServiceSequence> serviceSequenceOptional =
        serviceSequenceService.get(accountIdentifier, orgIdentifier, projectIdentifier, serviceId);
    ServiceSequence serviceSequence;
    if (serviceSequenceOptional.isPresent()) {
      serviceSequence = serviceSequenceOptional.get();
    } else {
      throw new InvalidRequestException("No default sequence of Env and Env Group exist for this service");
    }
    if (isNull(serviceSequence.getCustomSequence())) {
      return serviceSequence;
    } else {
      List<CustomSequenceDTO.EnvAndEnvGroupCard> customSequence = filterCustomSequence(serviceSequence);
      serviceSequence.setCustomSequence(CustomSequenceDTO.builder().EnvAndEnvGroupCardList(customSequence).build());
      return serviceSequence;
    }
  }

  private List<CustomSequenceDTO.EnvAndEnvGroupCard> filterCustomSequence(ServiceSequence serviceSequence) {
    List<CustomSequenceDTO.EnvAndEnvGroupCard> customSequence =
        serviceSequence.getCustomSequence().getEnvAndEnvGroupCardList();
    List<CustomSequenceDTO.EnvAndEnvGroupCard> defaultSequence =
        serviceSequence.getDefaultSequence().getEnvAndEnvGroupCardList();
    HashMap<String, CustomSequenceDTO.EnvAndEnvGroupCard> envGrpCardsMap = new HashMap<>();
    customSequence.forEach(envGroupCard
        -> envGrpCardsMap.put(
            envGroupCard.getName() + envGroupCard.getIdentifier() + envGroupCard.isEnvGroup(), envGroupCard));

    List<CustomSequenceDTO.EnvAndEnvGroupCard> appendSequence = new ArrayList<>();
    defaultSequence.forEach(envGroupCard -> addIfPresentInDefault(appendSequence, envGroupCard, envGrpCardsMap));

    for (String envKey : envGrpCardsMap.keySet()) {
      CustomSequenceDTO.EnvAndEnvGroupCard customCard = envGrpCardsMap.get(envKey);

      boolean isNew = customCard.isNew();
      customCard.setNew(false);

      if (!defaultSequence.contains(customCard)) {
        customSequence.remove(customCard);
      }
      customCard.setNew(isNew);
    }

    customSequence.addAll(0, appendSequence);
    return customSequence;
  }

  private void addIfPresentInDefault(List<CustomSequenceDTO.EnvAndEnvGroupCard> appendSequence,
      CustomSequenceDTO.EnvAndEnvGroupCard envGroupCard,
      HashMap<String, CustomSequenceDTO.EnvAndEnvGroupCard> envGrpCardsMap) {
    if (isNull(envGrpCardsMap.get(envGroupCard.getName() + envGroupCard.getIdentifier() + envGroupCard.isEnvGroup()))) {
      envGroupCard.setNew(true);
      appendSequence.add(envGroupCard);
    }
  }

  @Override
  public ServiceSequence saveCustomSequence(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String serviceId, CustomSequenceDTO customSequenceDTO) {
    ServiceSequence serviceSequence = ServiceSequence.builder()
                                          .accountId(accountIdentifier)
                                          .orgIdentifier(orgIdentifier)
                                          .projectIdentifier(projectIdentifier)
                                          .customSequence(customSequenceDTO)
                                          .serviceIdentifier(serviceId)
                                          .build();
    return serviceSequenceService.upsertCustomSequence(serviceSequence);
  }

  public List<ServiceArtifactExecutionDetail> getExecutionIdAndArtifactDetails(String query) {
    List<ServiceArtifactExecutionDetail> serviceArtifactExecutionDetailList = new ArrayList<>();
    int totalTries = 0;
    boolean successfulOperation = false;
    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      ResultSet resultSet = null;
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(query)) {
        resultSet = statement.executeQuery();
        while (resultSet != null && resultSet.next()) {
          serviceArtifactExecutionDetailList.add(
              ServiceArtifactExecutionDetail.builder()
                  .artifactPath(resultSet.getString(ARTIFACT_IMAGE))
                  .artifactTag(resultSet.getString(TAG))
                  .artifactDisplayName(resultSet.getString(ARTIFACT_DISPLAY_NAME))
                  .pipelineExecutionSummaryCDId(resultSet.getString(PIPELINE_EXECUTION_SUMMARY_CD_ID))
                  .accountId(resultSet.getString(ACCOUNT_ID))
                  .orgId(resultSet.getString(ORG_ID))
                  .projectId(resultSet.getString(PROJECT_ID))
                  .serviceRef(resultSet.getString(SERVICE_ID))
                  .serviceName(resultSet.getString(SERVICE_NAME))
                  .serviceStartTime(resultSet.getLong(SERVICE_STARTTS))
                  .build());
        }
        successfulOperation = true;
      } catch (SQLException ex) {
        log.error("{} after total tries = {}", ex, totalTries);
        totalTries++;
      } finally {
        DBUtils.close(resultSet);
      }
    }
    return serviceArtifactExecutionDetailList;
  }

  @Override
  public ServiceDeploymentInfoDTO getServiceDeployments(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, long startTime, long endTime, String serviceIdentifier, long bucketSizeInDays) {
    String serviceRef = IdentifierRefHelper.getRefFromIdentifierOrRef(
        accountIdentifier, orgIdentifier, projectIdentifier, serviceIdentifier);
    String query = queryBuilderServiceDeployments(
        accountIdentifier, orgIdentifier, projectIdentifier, startTime, endTime, bucketSizeInDays, serviceRef);

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
          if (CDDashboardServiceHelper.successStatusList.contains(status)) {
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

  @Override
  public ServiceDeploymentInfoDTOV2 getServiceDeploymentsV2(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, long startTime, long endTime, String serviceIdentifier, long bucketSizeInDays) {
    String serviceRef = IdentifierRefHelper.getRefFromIdentifierOrRef(
        accountIdentifier, orgIdentifier, projectIdentifier, serviceIdentifier);
    String query = queryBuilderServiceDeployments(
        accountIdentifier, orgIdentifier, projectIdentifier, startTime, endTime, bucketSizeInDays, serviceRef);

    /**
     * Map that stores service deployment data for a bucket time - starting time of a
     * dateCDOverviewDashboardServiceImpl.java
     */
    Map<Long, ServiceDeploymentV2> resultMap = new HashMap<>();
    long startTimeCopy = startTime;

    initializeResultMapV2(resultMap, startTimeCopy, endTime, bucketSizeInDays);

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
          ServiceDeploymentV2 serviceDeployment = resultMap.get(bucketTime);
          DeploymentCount deployments = serviceDeployment.getDeployments();
          deployments.setTotal(deployments.getTotal() + numberOfRecords);
          if (CDDashboardServiceHelper.successStatusList.contains(status)) {
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
    List<ServiceDeploymentV2> serviceDeploymentList = resultMap.values().stream().collect(Collectors.toList());
    return ServiceDeploymentInfoDTOV2.builder().serviceDeploymentList(serviceDeploymentList).build();
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
  private void initializeResultMapV2(
      Map<Long, ServiceDeploymentV2> resultMap, long startTime, long endTime, long bucketSizeInDays) {
    long bucketSizeInMS = bucketSizeInDays * DAY_IN_MS;
    while (startTime < endTime) {
      resultMap.put(startTime,
          ServiceDeploymentV2.builder()
              .time(startTime)
              .deployments(io.harness.ng.overview.dto.DeploymentCount.builder().total(0).failure(0).success(0).build())
              .rate(DeploymentChangeRatesV2.builder()
                        .frequency(0)
                        .frequencyChangeRate(new ChangeRate(Double.valueOf(0)))
                        .failureRate(0)
                        .failureRateChangeRate(new ChangeRate(Double.valueOf(0)))
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
            "time_bucket(%s, service_startts) as time_entity, pipeline_execution_summary_cd_id  from service_infra_info as sii, pipeline_execution_summary_cd as pesi where sii.service_id is not null and ",
            bucketSizeInMS));
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
      totalBuildSqlBuilder.append(String.format(" and sii.service_id='%s'", serviceIdentifier));
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

  private void calculateRates(List<ServiceDeployment> serviceDeployments) {
    serviceDeployments.sort(Comparator.comparingLong(ServiceDeployment::getTime));

    double prevFrequency = 0, prevFailureRate = 0;
    for (int i = 0; i < serviceDeployments.size(); i++) {
      DeploymentCount deployments = serviceDeployments.get(i).getDeployments();
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

  private void calculateRatesV2(List<ServiceDeploymentV2> serviceDeployments) {
    serviceDeployments.sort(Comparator.comparingLong(ServiceDeploymentV2::getTime));

    double prevFrequency = 0, prevFailureRate = 0;
    for (int i = 0; i < serviceDeployments.size(); i++) {
      DeploymentCount deployments = serviceDeployments.get(i).getDeployments();
      DeploymentChangeRatesV2 rates = serviceDeployments.get(i).getRate();

      double currFrequency = deployments.getTotal();
      rates.setFrequency(currFrequency);
      rates.setFrequencyChangeRate(calculateChangeRateV2(prevFrequency, currFrequency));
      prevFrequency = currFrequency;

      double failureRate = deployments.getFailure() * 100;
      if (deployments.getTotal() != 0) {
        failureRate = failureRate / deployments.getTotal();
      }
      rates.setFailureRate(failureRate);
      rates.setFailureRateChangeRate(calculateChangeRateV2(prevFailureRate, failureRate));
      prevFailureRate = failureRate;
    }
  }

  @Override
  public io.harness.ng.overview.dto.ServiceDeploymentListInfo getServiceDeploymentsInfo(String accountIdentifier,
      String orgIdentifier, String projectIdentifier, long startTime, long endTime, String serviceIdentifier,
      long bucketSizeInDays) throws Exception {
    String serviceRef = IdentifierRefHelper.getRefFromIdentifierOrRef(
        accountIdentifier, orgIdentifier, projectIdentifier, serviceIdentifier);
    long numberOfDays = getNumberOfDays(startTime, endTime);
    validateBucketSize(numberOfDays, bucketSizeInDays);
    long prevStartTime = getStartTimeOfPreviousInterval(startTime, numberOfDays);

    ServiceDeploymentInfoDTO serviceDeployments = getServiceDeployments(
        accountIdentifier, orgIdentifier, projectIdentifier, startTime, endTime, serviceRef, bucketSizeInDays);
    List<io.harness.ng.overview.dto.ServiceDeployment> serviceDeploymentList =
        serviceDeployments.getServiceDeploymentList();

    ServiceDeploymentInfoDTO prevServiceDeployment = getServiceDeployments(
        accountIdentifier, orgIdentifier, projectIdentifier, prevStartTime, startTime, serviceRef, bucketSizeInDays);
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

  @Override
  public ServiceDeploymentListInfoV2 getServiceDeploymentsInfoV2(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, long startTime, long endTime, String serviceIdentifier, long bucketSizeInDays)
      throws Exception {
    String serviceRef = IdentifierRefHelper.getRefFromIdentifierOrRef(
        accountIdentifier, orgIdentifier, projectIdentifier, serviceIdentifier);
    long numberOfDays = getNumberOfDays(startTime, endTime);
    validateBucketSize(numberOfDays, bucketSizeInDays);
    long prevStartTime = getStartTimeOfPreviousInterval(startTime, numberOfDays);

    ServiceDeploymentInfoDTOV2 serviceDeployments = getServiceDeploymentsV2(
        accountIdentifier, orgIdentifier, projectIdentifier, startTime, endTime, serviceRef, bucketSizeInDays);
    List<ServiceDeploymentV2> serviceDeploymentList = serviceDeployments.getServiceDeploymentList();

    ServiceDeploymentInfoDTOV2 prevServiceDeployment = getServiceDeploymentsV2(
        accountIdentifier, orgIdentifier, projectIdentifier, prevStartTime, startTime, serviceRef, bucketSizeInDays);
    List<ServiceDeploymentV2> prevServiceDeploymentList = prevServiceDeployment.getServiceDeploymentList();

    long totalDeployments = getTotalDeploymentsV2(serviceDeploymentList);
    long prevTotalDeployments = getTotalDeploymentsV2(prevServiceDeploymentList);
    double failureRate = getFailureRateV2(serviceDeploymentList);
    double frequency = totalDeployments / (double) numberOfDays;
    double prevFrequency = prevTotalDeployments / (double) numberOfDays;

    ChangeRate totalDeploymentChangeRate = calculateChangeRateV2(prevTotalDeployments, totalDeployments);
    ChangeRate failureRateChangeRate = getFailureRateChangeRateV2(serviceDeploymentList, prevServiceDeploymentList);
    ChangeRate frequencyChangeRate = calculateChangeRateV2(prevFrequency, frequency);

    calculateRatesV2(serviceDeploymentList);

    return ServiceDeploymentListInfoV2.builder()
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
  private ChangeRate getFailureRateChangeRateV2(
      List<ServiceDeploymentV2> executionDeploymentList, List<ServiceDeploymentV2> prevExecutionDeploymentList) {
    double failureRate = getFailureRateV2(executionDeploymentList);
    double prevFailureRate = getFailureRateV2(prevExecutionDeploymentList);
    return calculateChangeRateV2(prevFailureRate, failureRate);
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
  private double getFailureRateV2(List<ServiceDeploymentV2> executionDeploymentList) {
    long totalDeployments = executionDeploymentList.stream()
                                .map(ServiceDeploymentV2::getDeployments)
                                .mapToLong(DeploymentCount::getTotal)
                                .sum();
    long totalFailure = executionDeploymentList.stream()
                            .map(ServiceDeploymentV2::getDeployments)
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
  private ChangeRate calculateChangeRateV2(double prevValue, double curValue) {
    if (prevValue == curValue) {
      return new ChangeRate(Double.valueOf(0));
    }
    if (prevValue == 0) {
      return new ChangeRate(null);
    }
    return new ChangeRate(((curValue - prevValue) * 100) / prevValue);
  }

  private long getTotalDeployments(List<io.harness.ng.overview.dto.ServiceDeployment> executionDeploymentList) {
    long total = 0;
    for (ServiceDeployment item : executionDeploymentList) {
      total += item.getDeployments().getTotal();
    }
    return total;
  }
  private long getTotalDeploymentsV2(List<ServiceDeploymentV2> executionDeploymentList) {
    long total = 0;
    for (ServiceDeploymentV2 item : executionDeploymentList) {
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

  public List<ExecutionStatusInfo> getDeploymentStatusInfo(String queryStatus, String queryServiceTag) {
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
    HashMap<String, List<EnvironmentDeploymentsInfo>> pipelineToEnvMap = new HashMap<>();

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

    Pair<HashMap<String, List<ServiceDeploymentInfo>>, HashMap<String, List<EnvironmentDeploymentsInfo>>>
        deploymentInfo = queryCalculatorServiceTagMag(queryServiceTag);
    serviceTagMap = deploymentInfo.getKey();
    pipelineToEnvMap = deploymentInfo.getValue();

    List<ExecutionStatusInfo> statusInfo = new ArrayList<>();
    for (int i = 0; i < objectIdList.size(); i++) {
      String objectId = objectIdList.get(i);
      long startTime = startTs.get(i);
      long endTime = endTs.get(i);
      String pipelineIdentifier = pipelineIdentifierList.get(i);
      String planExecutionId = planExecutionIdList.get(i);
      statusInfo.add(this.getDeploymentStatusInfoObject(namePipelineList.get(i), pipelineIdentifier, planExecutionId,
          startTime, endTime, deploymentStatus.get(i), gitInfoList.get(i), triggerType.get(i), author.get(i),
          serviceTagMap.get(objectId), pipelineToEnvMap.get(objectId)));
    }
    return statusInfo;
  }
  @Override
  public DashboardExecutionStatusInfo getDeploymentActiveFailedRunningInfo(
      String accountId, String orgId, String projectId, long days, long startInterval, long endInterval) {
    // failed
    String queryFailed = queryBuilderStatusNew(
        accountId, orgId, projectId, days, CDDashboardServiceHelper.failedStatusList, startInterval, endInterval);
    String queryServiceNameTagIdFailed = queryBuilderSelectIdLimitTimeCdTableNew(
        accountId, orgId, projectId, days, CDDashboardServiceHelper.failedStatusList, startInterval, endInterval);
    queryServiceNameTagIdFailed = queryBuilderServiceTag(queryServiceNameTagIdFailed);
    List<ExecutionStatusInfo> failure = getDeploymentStatusInfo(queryFailed, queryServiceNameTagIdFailed);

    // active
    String queryActive =
        queryBuilderStatusNew(accountId, orgId, projectId, days, activeStatusList, startInterval, endInterval);
    String queryServiceNameTagIdActive = queryBuilderSelectIdLimitTimeCdTableNew(
        accountId, orgId, projectId, days, activeStatusList, startInterval, endInterval);
    queryServiceNameTagIdActive = queryBuilderServiceTag(queryServiceNameTagIdActive);
    List<ExecutionStatusInfo> active = getDeploymentStatusInfo(queryActive, queryServiceNameTagIdActive);

    // pending
    String queryPending =
        queryBuilderStatusNew(accountId, orgId, projectId, days, pendingStatusList, startInterval, endInterval);
    String queryServiceNameTagIdPending = queryBuilderSelectIdLimitTimeCdTableNew(
        accountId, orgId, projectId, days, pendingStatusList, startInterval, endInterval);
    queryServiceNameTagIdPending = queryBuilderServiceTag(queryServiceNameTagIdPending);
    List<ExecutionStatusInfo> pending = getDeploymentStatusInfo(queryPending, queryServiceNameTagIdPending);

    return DashboardExecutionStatusInfo.builder().failure(failure).active(active).pending(pending).build();
  }

  private ExecutionStatusInfo getDeploymentStatusInfoObject(String name, String identfier, String planExecutionId,
      Long startTime, Long endTime, String status, GitInfo gitInfo, String triggerType, AuthorInfo authorInfo,
      List<ServiceDeploymentInfo> serviceDeploymentInfos,
      List<EnvironmentDeploymentsInfo> environmentDeploymentsInfos) {
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
        .environmentInfoList(environmentDeploymentsInfos)
        .build();
  }

  private ServiceDeploymentInfo getServiceDeployment(String service_name, String tag, String image, String serviceId) {
    if (service_name != null) {
      if (image != null) {
        return ServiceDeploymentInfo.builder()
            .serviceName(service_name)
            .serviceId(serviceId)
            .serviceTag(tag)
            .image(image)
            .build();
      }
      return ServiceDeploymentInfo.builder().serviceName(service_name).serviceId(serviceId).build();
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

  private WorkloadDeploymentInfoV2 getWorkloadDeploymentInfoV2(WorkloadDeploymentInfoV2 workloadDeploymentInfo,
      long totalDeployment, long prevTotalDeployment, long success, long previousSuccess, long failure,
      long previousFailure, long numberOfDays) {
    double percentSuccess = 0.0;
    double failureRate = 0.0;
    ChangeRate failureRateChangeRate = calculateChangeRateV2(previousFailure, failure);
    ChangeRate totalDeploymentChangeRate = calculateChangeRateV2(prevTotalDeployment, totalDeployment);
    double frequency = totalDeployment / (double) numberOfDays;
    double prevFrequency = prevTotalDeployment / (double) numberOfDays;
    ChangeRate frequencyChangeRate = calculateChangeRateV2(prevFrequency, frequency);
    ChangeRate rateSuccess = calculateChangeRateV2(previousSuccess, success);
    if (totalDeployment != 0) {
      percentSuccess = success / (double) totalDeployment;
      percentSuccess = percentSuccess * 100.0;
      failureRate = failure / (double) totalDeployment;
      failureRate = failureRate * 100.0;
    }
    return WorkloadDeploymentInfoV2.builder()
        .serviceName(workloadDeploymentInfo.getServiceName())
        .serviceId(workloadDeploymentInfo.getServiceId())
        .lastExecuted(workloadDeploymentInfo.getLastExecuted())
        .deploymentTypeList(workloadDeploymentInfo.getDeploymentTypeList())
        .totalDeployments(totalDeployment)
        .totalDeploymentChangeRate(totalDeploymentChangeRate)
        .percentSuccess(percentSuccess)
        .rateSuccess(rateSuccess)
        .failureRate(failureRate)
        .failureRateChangeRate(failureRateChangeRate)
        .frequency(frequency)
        .frequencyChangeRate(frequencyChangeRate)
        .lastPipelineExecutionId(workloadDeploymentInfo.getLastPipelineExecutionId())
        .workload(workloadDeploymentInfo.getWorkload())
        .build();
  }

  public DashboardWorkloadDeployment getWorkloadDeploymentInfoCalculation(List<String> workloadsId, List<String> status,
      List<Pair<Long, Long>> timeInterval, List<String> deploymentTypeList, Map<String, String> uniqueWorkloadNameAndId,
      long startDate, long endDate, List<String> pipelineExecutionIdList) {
    Map<String, Pair<String, AuthorInfo>> pipelineExecutionIdToTriggerAndAuthorInfoMap =
        getPipelineExecutionIdToTriggerTypeAndAuthorInfoMapping(pipelineExecutionIdList);
    long numberOfDays = NGDateUtils.getNumberOfDays(startDate, endDate);

    List<WorkloadDeploymentInfo> workloadDeploymentInfoList = new ArrayList<>();

    List<WorkloadDeploymentDetails> workloadDeploymentDetailsList = workloadDeploymentInfoCalculationHelper(workloadsId,
        status, timeInterval, deploymentTypeList, uniqueWorkloadNameAndId, startDate, endDate, pipelineExecutionIdList);

    for (WorkloadDeploymentDetails workloadDeploymentDetails : workloadDeploymentDetailsList) {
      LastWorkloadInfo lastWorkloadInfo =
          LastWorkloadInfo.builder()
              .startTime(workloadDeploymentDetails.getLastExecutedStartTs())
              .endTime(workloadDeploymentDetails.getLastExecutedEndTs() == -1L
                      ? null
                      : workloadDeploymentDetails.getLastExecutedEndTs())
              .status(workloadDeploymentDetails.getLastStatus())
              .triggerType(
                  pipelineExecutionIdToTriggerAndAuthorInfoMap.get(workloadDeploymentDetails.getPipelineExecutionId())
                          == null
                      ? null
                      : pipelineExecutionIdToTriggerAndAuthorInfoMap
                            .get(workloadDeploymentDetails.getPipelineExecutionId())
                            .getKey())
              .authorInfo(
                  pipelineExecutionIdToTriggerAndAuthorInfoMap.get(workloadDeploymentDetails.getPipelineExecutionId())
                          == null
                      ? null
                      : pipelineExecutionIdToTriggerAndAuthorInfoMap
                            .get(workloadDeploymentDetails.getPipelineExecutionId())
                            .getValue())
              .deploymentType(workloadDeploymentDetails.getDeploymentType())
              .build();
      WorkloadDeploymentInfo workloadDeploymentInfo =
          WorkloadDeploymentInfo.builder()
              .serviceName(uniqueWorkloadNameAndId.get(workloadDeploymentDetails.getWorkloadId()))
              .serviceId(workloadDeploymentDetails.getWorkloadId())
              .totalDeployments(workloadDeploymentDetails.getTotalDeployment())
              .lastExecuted(lastWorkloadInfo)
              .lastPipelineExecutionId(workloadDeploymentDetails.getPipelineExecutionId())
              .deploymentTypeList(deploymentTypeList.stream().collect(Collectors.toSet()))
              .workload(workloadDeploymentDetails.getDateCount())
              .build();
      workloadDeploymentInfoList.add(getWorkloadDeploymentInfo(workloadDeploymentInfo,
          workloadDeploymentDetails.getTotalDeployment(), workloadDeploymentDetails.getPrevTotalDeployments(),
          workloadDeploymentDetails.getSuccess(), workloadDeploymentDetails.getPreviousSuccess(),
          workloadDeploymentDetails.getFailure(), workloadDeploymentDetails.getPreviousFailure(), numberOfDays));
    }

    return DashboardWorkloadDeployment.builder().workloadDeploymentInfoList(workloadDeploymentInfoList).build();
  }

  public DashboardWorkloadDeploymentV2 getWorkloadDeploymentInfoCalculationV2(List<String> workloadsId,
      List<String> status, List<Pair<Long, Long>> timeInterval, List<String> deploymentTypeList,
      Map<String, String> uniqueWorkloadNameAndId, long startDate, long endDate, List<String> pipelineExecutionIdList) {
    Map<String, Pair<String, AuthorInfo>> pipelineExecutionIdToTriggerAndAuthorInfoMap =
        getPipelineExecutionIdToTriggerTypeAndAuthorInfoMapping(pipelineExecutionIdList);
    long numberOfDays = NGDateUtils.getNumberOfDays(startDate, endDate);

    List<WorkloadDeploymentInfoV2> workloadDeploymentInfoList = new ArrayList<>();

    List<WorkloadDeploymentDetails> workloadDeploymentDetailsList = workloadDeploymentInfoCalculationHelper(workloadsId,
        status, timeInterval, deploymentTypeList, uniqueWorkloadNameAndId, startDate, endDate, pipelineExecutionIdList);

    for (WorkloadDeploymentDetails workloadDeploymentDetails : workloadDeploymentDetailsList) {
      LastWorkloadInfo lastWorkloadInfo =
          LastWorkloadInfo.builder()
              .startTime(workloadDeploymentDetails.getLastExecutedStartTs())
              .endTime(workloadDeploymentDetails.getLastExecutedEndTs() == -1L
                      ? null
                      : workloadDeploymentDetails.getLastExecutedEndTs())
              .status(workloadDeploymentDetails.getLastStatus())
              .triggerType(
                  pipelineExecutionIdToTriggerAndAuthorInfoMap.get(workloadDeploymentDetails.getPipelineExecutionId())
                          == null
                      ? null
                      : pipelineExecutionIdToTriggerAndAuthorInfoMap
                            .get(workloadDeploymentDetails.getPipelineExecutionId())
                            .getKey())
              .authorInfo(
                  pipelineExecutionIdToTriggerAndAuthorInfoMap.get(workloadDeploymentDetails.getPipelineExecutionId())
                          == null
                      ? null
                      : pipelineExecutionIdToTriggerAndAuthorInfoMap
                            .get(workloadDeploymentDetails.getPipelineExecutionId())
                            .getValue())
              .deploymentType(workloadDeploymentDetails.getDeploymentType())
              .build();
      WorkloadDeploymentInfoV2 workloadDeploymentInfo =
          WorkloadDeploymentInfoV2.builder()
              .serviceName(uniqueWorkloadNameAndId.get(workloadDeploymentDetails.getWorkloadId()))
              .serviceId(workloadDeploymentDetails.getWorkloadId())
              .totalDeployments(workloadDeploymentDetails.getTotalDeployment())
              .lastExecuted(lastWorkloadInfo)
              .lastPipelineExecutionId(workloadDeploymentDetails.getPipelineExecutionId())
              .deploymentTypeList(deploymentTypeList.stream().collect(Collectors.toSet()))
              .workload(workloadDeploymentDetails.getDateCount())
              .build();
      workloadDeploymentInfoList.add(getWorkloadDeploymentInfoV2(workloadDeploymentInfo,
          workloadDeploymentDetails.getTotalDeployment(), workloadDeploymentDetails.getPrevTotalDeployments(),
          workloadDeploymentDetails.getSuccess(), workloadDeploymentDetails.getPreviousSuccess(),
          workloadDeploymentDetails.getFailure(), workloadDeploymentDetails.getPreviousFailure(), numberOfDays));
    }

    return DashboardWorkloadDeploymentV2.builder().workloadDeploymentInfoList(workloadDeploymentInfoList).build();
  }

  public List<WorkloadDeploymentDetails> workloadDeploymentInfoCalculationHelper(List<String> workloadsId,
      List<String> status, List<Pair<Long, Long>> timeInterval, List<String> deploymentTypeList,
      Map<String, String> uniqueWorkloadNameAndId, long startDate, long endDate, List<String> pipelineExecutionIdList) {
    List<WorkloadDeploymentDetails> workloadDeploymentDetailsList = new ArrayList<>();
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
            if (CDDashboardServiceHelper.successStatusList.contains(status.get(i))) {
              success++;
            }
            if (CDDashboardServiceHelper.failedStatusList.contains(status.get(i))) {
              failure++;
            }
            if (lastExecutedStartTs == 0 || lastExecutedStartTs < startTime) {
              lastExecutedStartTs = startTime;
              lastExecutedEndTs = endTime;
              lastStatus = status.get(i);
              deploymentType = deploymentTypeList.get(i);
              pipelineExecutionId = pipelineExecutionIdList.get(i);
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
        workloadDeploymentDetailsList.add(WorkloadDeploymentDetails.builder()
                                              .deploymentType(deploymentType)
                                              .workloadId(workloadId)
                                              .totalDeployment(totalDeployment)
                                              .prevTotalDeployments(prevTotalDeployments)
                                              .dateCount(dateCount)
                                              .failure(failure)
                                              .lastExecutedEndTs(lastExecutedEndTs)
                                              .lastExecutedStartTs(lastExecutedStartTs)
                                              .lastStatus(lastStatus)
                                              .pipelineExecutionId(pipelineExecutionId)
                                              .success(success)
                                              .previousFailure(previousFailure)
                                              .previousSuccess(previousSuccess)
                                              .build());
      }
    }
    return workloadDeploymentDetailsList;
  }

  @Override
  public DashboardWorkloadDeployment getDashboardWorkloadDeployment(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, long startInterval, long endInterval, long previousStartInterval,
      EnvironmentType envType) {
    WorkloadInfo workloadInfo = getWorkloadInfo(
        accountIdentifier, orgIdentifier, projectIdentifier, endInterval, previousStartInterval, envType);

    return getWorkloadDeploymentInfoCalculation(workloadInfo.getWorkloadsId(), workloadInfo.getStatus(),
        workloadInfo.getTimeInterval(), workloadInfo.getDeploymentTypeList(), workloadInfo.getUniqueWorkloadNameAndId(),
        startInterval, endInterval, workloadInfo.getPipelineExecutionIdList());
  }

  @Override
  public DashboardWorkloadDeploymentV2 getDashboardWorkloadDeploymentV2(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, long startInterval, long endInterval, long previousStartInterval,
      EnvironmentType envType) {
    WorkloadInfo workloadInfo = getWorkloadInfo(
        accountIdentifier, orgIdentifier, projectIdentifier, endInterval, previousStartInterval, envType);

    return getWorkloadDeploymentInfoCalculationV2(workloadInfo.getWorkloadsId(), workloadInfo.getStatus(),
        workloadInfo.getTimeInterval(), workloadInfo.getDeploymentTypeList(), workloadInfo.getUniqueWorkloadNameAndId(),
        startInterval, endInterval, workloadInfo.getPipelineExecutionIdList());
  }

  private WorkloadInfo getWorkloadInfo(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      long endInterval, long previousStartInterval, EnvironmentType envType) {
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
          String serviceName = resultSet.getString(SERVICE_NAME);
          String service_id = resultSet.getString(SERVICE_ID);
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
    return WorkloadInfo.builder()
        .workloadsId(workloadsId)
        .uniqueWorkloadNameAndId(uniqueWorkloadNameAndId)
        .timeInterval(timeInterval)
        .deploymentTypeList(deploymentTypeList)
        .status(status)
        .pipelineExecutionIdList(pipelineExecutionIdList)
        .build();
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
    return epochValue - (epochValue - startInterval) % DAY_IN_MS;
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
  public EnvBuildIdAndInstanceCountInfoList getEnvBuildInstanceCountByServiceId(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceId) {
    Map<String, List<BuildIdAndInstanceCount>> envIdToBuildMap = new HashMap<>();
    Map<String, String> envIdToEnvNameMap = new HashMap<>();

    String serviceRef =
        IdentifierRefHelper.getRefFromIdentifierOrRef(accountIdentifier, orgIdentifier, projectIdentifier, serviceId);

    List<EnvBuildInstanceCount> envBuildInstanceCounts = instanceDashboardService.getEnvBuildInstanceCountByServiceId(
        accountIdentifier, orgIdentifier, projectIdentifier, serviceRef, getCurrentTime());

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

  @Override
  public InstanceGroupedByEnvironmentList getInstanceGroupedByEnvironmentList(String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String serviceId, String environmentId, String envGrpId) {
    boolean isGitOps = isGitopsEnabled(accountIdentifier, orgIdentifier, projectIdentifier, serviceId);
    List<ActiveServiceInstanceInfoWithEnvType> activeServiceInstanceInfoList =
        instanceDashboardService.getActiveServiceInstanceInfoWithEnvType(
            accountIdentifier, orgIdentifier, projectIdentifier, environmentId, serviceId, null, isGitOps, false);

    updateNullArtifact(activeServiceInstanceInfoList);

    DashboardServiceHelper.sortActiveServiceInstanceInfoWithEnvTypeList(activeServiceInstanceInfoList);

    List<String> envIds = new ArrayList<>();
    activeServiceInstanceInfoList.forEach(
        activeServiceInstanceInfo -> envIds.add(activeServiceInstanceInfo.getEnvIdentifier()));

    Criteria criteria = environmentGroupService.formCriteria(
        accountIdentifier, orgIdentifier, projectIdentifier, false, "", "", null, false);
    Page<EnvironmentGroupEntity> environmentGroupEntitiesPage =
        environmentGroupService.list(criteria, Pageable.unpaged(), projectIdentifier, orgIdentifier, accountIdentifier);

    List<Environment> environments = environmentService.fetchesNonDeletedEnvironmentFromListOfRefs(
        accountIdentifier, orgIdentifier, projectIdentifier, new ArrayList<>(envIds));

    activeServiceInstanceInfoList = filterNonDeletedEnvs(activeServiceInstanceInfoList, environments);

    return DashboardServiceHelper.getInstanceGroupedByEnvironmentListHelper(
        envGrpId, activeServiceInstanceInfoList, isGitOps, environmentGroupEntitiesPage);
  }

  private void updateNullArtifact(List<ActiveServiceInstanceInfoWithEnvType> activeServiceInstanceInfoList) {
    for (ActiveServiceInstanceInfoWithEnvType activeServiceInstanceInfoWithEnvType : activeServiceInstanceInfoList) {
      if (isNull(activeServiceInstanceInfoWithEnvType.getDisplayName())) {
        activeServiceInstanceInfoWithEnvType.setDisplayName(EMPTY_ARTIFACT);
      }
    }
  }

  private String convertIdToRef(String accountId, String orgId, String projectId, String id) {
    return IdentifierRefHelper.getIdentifierRefWithScope(accountId, orgId, projectId, id).buildScopedIdentifier();
  }

  private List<ActiveServiceInstanceInfoWithEnvType> filterNonDeletedEnvs(
      List<ActiveServiceInstanceInfoWithEnvType> activeServiceInstanceInfoList, List<Environment> environments) {
    List<String> envIds = new ArrayList<>();
    HashMap<String, Environment> envRefEnvMap = new HashMap<>();
    environments.forEach(environment -> {
      String envRef = convertIdToRef(environment.getAccountId(), environment.getOrgIdentifier(),
          environment.getProjectIdentifier(), environment.getIdentifier());
      envRefEnvMap.put(envRef, environment);
      envIds.add(envRef);
    });
    List<ActiveServiceInstanceInfoWithEnvType> updatedActiveServiceInstanceInfoList = new ArrayList<>();

    for (ActiveServiceInstanceInfoWithEnvType activeServiceInstanceInfoWithEnvType : activeServiceInstanceInfoList) {
      if (envIds.contains(activeServiceInstanceInfoWithEnvType.getEnvIdentifier())) {
        activeServiceInstanceInfoWithEnvType.setEnvName(
            envRefEnvMap.get(activeServiceInstanceInfoWithEnvType.getEnvIdentifier()).getName());
        activeServiceInstanceInfoWithEnvType.setEnvType(
            envRefEnvMap.get(activeServiceInstanceInfoWithEnvType.getEnvIdentifier()).getType());
        updatedActiveServiceInstanceInfoList.add(activeServiceInstanceInfoWithEnvType);
      }
    }
    return updatedActiveServiceInstanceInfoList;
  }

  @Override
  public InstanceGroupedOnArtifactList getInstanceGroupedOnArtifactList(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String serviceId, String environmentId, String envGrpId, String displayName,
      boolean filterOnArtifact) {
    boolean isGitOps = isGitopsEnabled(accountIdentifier, orgIdentifier, projectIdentifier, serviceId);

    List<ActiveServiceInstanceInfoWithEnvType> activeServiceInstanceInfoList = new ArrayList<>();
    if (filterOnArtifact && isEmpty(displayName)) {
      activeServiceInstanceInfoList.addAll(
          instanceDashboardService.getActiveServiceInstanceInfoWithEnvType(accountIdentifier, orgIdentifier,
              projectIdentifier, environmentId, serviceId, EMPTY_ARTIFACT, isGitOps, filterOnArtifact));

      activeServiceInstanceInfoList.addAll(
          instanceDashboardService.getActiveServiceInstanceInfoWithEnvType(accountIdentifier, orgIdentifier,
              projectIdentifier, environmentId, serviceId, null, isGitOps, filterOnArtifact));

    } else {
      activeServiceInstanceInfoList =
          instanceDashboardService.getActiveServiceInstanceInfoWithEnvType(accountIdentifier, orgIdentifier,
              projectIdentifier, environmentId, serviceId, displayName, isGitOps, filterOnArtifact);
    }

    updateNullArtifact(activeServiceInstanceInfoList);

    DashboardServiceHelper.sortActiveServiceInstanceInfoWithEnvTypeList(activeServiceInstanceInfoList);

    List<String> envIds = new ArrayList<>();
    activeServiceInstanceInfoList.forEach(
        activeServiceInstanceInfo -> envIds.add(activeServiceInstanceInfo.getEnvIdentifier()));

    Criteria criteria = environmentGroupService.formCriteria(
        accountIdentifier, orgIdentifier, projectIdentifier, false, "", "", null, false);
    Page<EnvironmentGroupEntity> environmentGroupEntitiesPage =
        environmentGroupService.list(criteria, Pageable.unpaged(), projectIdentifier, orgIdentifier, accountIdentifier);

    List<Environment> environments = environmentService.fetchesNonDeletedEnvironmentFromListOfRefs(
        accountIdentifier, orgIdentifier, projectIdentifier, new ArrayList<>(envIds));

    activeServiceInstanceInfoList = filterNonDeletedEnvs(activeServiceInstanceInfoList, environments);

    return DashboardServiceHelper.getInstanceGroupedByArtifactListHelper(
        activeServiceInstanceInfoList, isGitOps, environmentGroupEntitiesPage, envGrpId);
  }

  @Override
  public InstanceGroupedByServiceList.InstanceGroupedByService getInstanceGroupedByArtifactList(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceId) {
    List<ActiveServiceInstanceInfoV2> activeServiceInstanceInfoList;
    String serviceRef =
        IdentifierRefHelper.getRefFromIdentifierOrRef(accountIdentifier, orgIdentifier, projectIdentifier, serviceId);
    if (!Boolean.TRUE.equals(isGitopsEnabled(accountIdentifier, orgIdentifier, projectIdentifier, serviceId))) {
      activeServiceInstanceInfoList = instanceDashboardService.getActiveServiceInstanceInfo(
          accountIdentifier, orgIdentifier, projectIdentifier, null, serviceRef, null, false);
    } else {
      activeServiceInstanceInfoList = instanceDashboardService.getActiveServiceInstanceInfo(
          accountIdentifier, orgIdentifier, projectIdentifier, null, serviceRef, null, true);
    }

    InstanceGroupedByServiceList instanceGroupedByServiceList =
        getInstanceGroupedByServiceListHelper(activeServiceInstanceInfoList);
    return getInstanceGroupedByService(instanceGroupedByServiceList);
  }

  private InstanceGroupedByServiceList.InstanceGroupedByService getInstanceGroupedByService(
      InstanceGroupedByServiceList instanceGroupedByServiceList) {
    if (EmptyPredicate.isNotEmpty(instanceGroupedByServiceList.getInstanceGroupedByServiceList())) {
      return instanceGroupedByServiceList.getInstanceGroupedByServiceList().get(0);
    } else {
      return InstanceGroupedByServiceList.InstanceGroupedByService.builder()
          .instanceGroupedByArtifactList(new ArrayList<>())
          .build();
    }
  }

  @Override
  public InstanceGroupedByServiceList getInstanceGroupedByServiceList(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String envIdentifier, String serviceIdentifier, String buildIdentifier) {
    String serviceRef = IdentifierRefHelper.getRefFromIdentifierOrRef(
        accountIdentifier, orgIdentifier, projectIdentifier, serviceIdentifier);
    List<ActiveServiceInstanceInfoV2> activeServiceInstanceInfoList =
        instanceDashboardService.getActiveServiceInstanceInfo(
            accountIdentifier, orgIdentifier, projectIdentifier, envIdentifier, serviceRef, buildIdentifier, false);
    List<ActiveServiceInstanceInfoV2> activeServiceInstanceGitOpsInfoList =
        instanceDashboardService.getActiveServiceInstanceInfo(
            accountIdentifier, orgIdentifier, projectIdentifier, envIdentifier, serviceRef, buildIdentifier, true);
    activeServiceInstanceInfoList.addAll(activeServiceInstanceGitOpsInfoList);

    return getInstanceGroupedByServiceListHelper(activeServiceInstanceInfoList);
  }

  public InstanceGroupedByServiceList getInstanceGroupedByServiceListHelper(
      List<ActiveServiceInstanceInfoV2> activeServiceInstanceInfoList) {
    Map<String,
        Map<String,
            Map<String,
                Pair<Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>,
                    Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>>>>>
        serviceBuildEnvInfraMap = new HashMap<>();

    Map<String, String> serviceIdToServiceNameMap = new HashMap<>();
    Map<String, String> envIdToEnvNameMap = new HashMap<>();
    Map<String, String> infraIdToInfraNameMap = new HashMap<>();
    Map<String, String> clusterIdToAgentIdMap = new HashMap<>();
    Map<String, String> serviceIdToLatestBuildMap = new HashMap<>();
    Map<String, Long> serviceIdToLastDeployed = new HashMap<>();
    activeServiceInstanceInfoList.forEach(activeServiceInstanceInfo -> {
      final String serviceId = activeServiceInstanceInfo.getServiceIdentifier();
      final String buildId = activeServiceInstanceInfo.getTag();
      final String envId = activeServiceInstanceInfo.getEnvIdentifier();
      final Long lastDeployedAt = activeServiceInstanceInfo.getLastDeployedAt();

      if (serviceId == null || envId == null || lastDeployedAt == null) {
        return;
      }

      final String serviceName = activeServiceInstanceInfo.getServiceName();
      final String infraIdentifier = activeServiceInstanceInfo.getInfraIdentifier();
      final String infraName = activeServiceInstanceInfo.getInfraName();
      final String clusterIdentifier = activeServiceInstanceInfo.getClusterIdentifier();
      final String agentIdentifier = activeServiceInstanceInfo.getAgentIdentifier();
      final String lastPipelineExecutionId = activeServiceInstanceInfo.getLastPipelineExecutionId();
      final String lastPipelineExecutionName = activeServiceInstanceInfo.getLastPipelineExecutionName();
      final String envName = activeServiceInstanceInfo.getEnvName();
      final String artifactPath =
          DashboardServiceHelper.getArtifactPathFromDisplayName(activeServiceInstanceInfo.getDisplayName());
      final Integer count = activeServiceInstanceInfo.getCount();
      final String displayName = DashboardServiceHelper.getDisplayNameFromArtifact(artifactPath, buildId);

      if ((!serviceIdToLastDeployed.containsKey(serviceId))
          || (lastDeployedAt > serviceIdToLastDeployed.get(serviceId))) {
        serviceIdToLatestBuildMap.put(serviceId, displayName);
        serviceIdToLastDeployed.put(serviceId, lastDeployedAt);
      }

      serviceBuildEnvInfraMap.putIfAbsent(serviceId, new HashMap<>());
      serviceBuildEnvInfraMap.get(serviceId).putIfAbsent(displayName, new HashMap<>());
      serviceBuildEnvInfraMap.get(serviceId)
          .get(displayName)
          .putIfAbsent(envId, new MutablePair<>(new HashMap<>(), new HashMap<>()));

      if (clusterIdentifier != null) {
        Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>> map =
            serviceBuildEnvInfraMap.get(serviceId).get(displayName).get(envId).getValue();
        map.putIfAbsent(clusterIdentifier, new ArrayList<>());
        map.get(clusterIdentifier)
            .add(new InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution(
                count, lastPipelineExecutionId, lastPipelineExecutionName, lastDeployedAt));
        clusterIdToAgentIdMap.putIfAbsent(clusterIdentifier, agentIdentifier);
      } else {
        Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>> map =
            serviceBuildEnvInfraMap.get(serviceId).get(displayName).get(envId).getKey();
        map.putIfAbsent(infraIdentifier, new ArrayList<>());
        map.get(infraIdentifier)
            .add(new InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution(
                count, lastPipelineExecutionId, lastPipelineExecutionName, lastDeployedAt));
        infraIdToInfraNameMap.putIfAbsent(infraIdentifier, infraName);
      }

      serviceIdToServiceNameMap.putIfAbsent(serviceId, serviceName);
      envIdToEnvNameMap.putIfAbsent(envId, envName);
    });
    List<InstanceGroupedByServiceList.InstanceGroupedByService> instanceGroupedByServiceList =
        groupedByServices(serviceBuildEnvInfraMap, envIdToEnvNameMap, infraIdToInfraNameMap, serviceIdToServiceNameMap,
            clusterIdToAgentIdMap, serviceIdToLatestBuildMap);

    return InstanceGroupedByServiceList.builder().instanceGroupedByServiceList(instanceGroupedByServiceList).build();
  }

  public List<InstanceGroupedByServiceList.InstanceGroupedByService> groupedByServices(
      Map<String,
          Map<String,
              Map<String,
                  Pair<Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>,
                      Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>>>>>
          serviceBuildEnvInfraMap,
      Map<String, String> envIdToEnvNameMap, Map<String, String> infraIdToInfraNameMap,
      Map<String, String> serviceIdToServiceNameMap, Map<String, String> clusterIdAgentIdMap,
      Map<String, String> serviceIdToLatestBuildMap) {
    List<InstanceGroupedByServiceList.InstanceGroupedByService> instanceGroupedByServiceList = new ArrayList<>();

    for (Map.Entry<String,
             Map<String,
                 Map<String,
                     Pair<Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>,
                         Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>>>>> entry3 :
        serviceBuildEnvInfraMap.entrySet()) {
      String serviceId = entry3.getKey();
      String serviceName = serviceIdToServiceNameMap.get(serviceId);

      List<InstanceGroupedByServiceList.InstanceGroupedByArtifactV2> instanceGroupedByArtifactList =
          groupByArtifact(entry3.getValue(), serviceIdToLatestBuildMap, serviceId, infraIdToInfraNameMap,
              envIdToEnvNameMap, clusterIdAgentIdMap);

      instanceGroupedByServiceList.add(InstanceGroupedByServiceList.InstanceGroupedByService.builder()
                                           .serviceId(serviceId)
                                           .serviceName(serviceName)
                                           .lastDeployedAt(instanceGroupedByArtifactList.get(0).getLastDeployedAt())
                                           .instanceGroupedByArtifactList(instanceGroupedByArtifactList)
                                           .build());
    }

    // sort based on last deployed time generated by taking maximum or latest time from all executions that are grouped

    Collections.sort(
        instanceGroupedByServiceList, new Comparator<InstanceGroupedByServiceList.InstanceGroupedByService>() {
          public int compare(InstanceGroupedByServiceList.InstanceGroupedByService o1,
              InstanceGroupedByServiceList.InstanceGroupedByService o2) {
            return -(o1.getLastDeployedAt().compareTo(o2.getLastDeployedAt()));
          }
        });

    return instanceGroupedByServiceList;
  }

  public List<InstanceGroupedByServiceList.InstanceGroupedByArtifactV2> groupByArtifact(
      Map<String,
          Map<String,
              Pair<Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>,
                  Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>>>>
          artifactToEnvMap,
      Map<String, String> serviceIdToLatestBuildMap, String serviceId, Map<String, String> infraIdToInfraNameMap,
      Map<String, String> envIdToEnvNameMap, Map<String, String> clusterIdAgentIdMap) {
    List<InstanceGroupedByServiceList.InstanceGroupedByArtifactV2> instanceGroupedByArtifactList = new ArrayList<>();
    for (Map.Entry<String,
             Map<String,
                 Pair<Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>,
                     Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>>>> entry :
        artifactToEnvMap.entrySet()) {
      String displayName = entry.getKey();
      String artifactPath = DashboardServiceHelper.getArtifactPathFromDisplayName(displayName);
      String buildId = DashboardServiceHelper.getTagFromDisplayName(displayName);

      List<InstanceGroupedByServiceList.InstanceGroupedByEnvironmentV2> instanceGroupedByEnvironmentList =
          groupByEnvironment(entry.getValue(), infraIdToInfraNameMap, envIdToEnvNameMap, clusterIdAgentIdMap);

      instanceGroupedByArtifactList.add(
          InstanceGroupedByServiceList.InstanceGroupedByArtifactV2.builder()
              .artifactVersion(buildId)
              .artifactPath(artifactPath)
              .lastDeployedAt(instanceGroupedByEnvironmentList.get(0).getLastDeployedAt())
              .latest(checkEquality(serviceIdToLatestBuildMap.get(serviceId), displayName))
              .instanceGroupedByEnvironmentList(instanceGroupedByEnvironmentList)
              .build());
    }

    // sort based on last deployed time generated by taking maximum or latest time from all executions that are
    // grouped

    Collections.sort(
        instanceGroupedByArtifactList, new Comparator<InstanceGroupedByServiceList.InstanceGroupedByArtifactV2>() {
          public int compare(InstanceGroupedByServiceList.InstanceGroupedByArtifactV2 o1,
              InstanceGroupedByServiceList.InstanceGroupedByArtifactV2 o2) {
            return -(o1.getLastDeployedAt().compareTo(o2.getLastDeployedAt()));
          }
        });

    return instanceGroupedByArtifactList;
  }

  private boolean checkEquality(String a, String b) {
    if (a == null && b == null) {
      return true;
    } else if (a == null) {
      return false;
    } else if (b == null) {
      return false;
    }
    return a.equals(b);
  }

  public List<InstanceGroupedByServiceList.InstanceGroupedByEnvironmentV2> groupByEnvironment(
      Map<String,
          Pair<Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>,
              Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>>> envToInfraClusterMap,
      Map<String, String> infraIdToInfraNameMap, Map<String, String> envIdToEnvNameMap,
      Map<String, String> clusterIdAgentIdMap) {
    List<InstanceGroupedByServiceList.InstanceGroupedByEnvironmentV2> instanceGroupedByEnvironmentList =
        new ArrayList<>();

    for (Map.Entry<String,
             Pair<Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>,
                 Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>>>> entry1 :
        envToInfraClusterMap.entrySet()) {
      String envId = entry1.getKey();
      String envName = envIdToEnvNameMap.get(envId);

      List<InstanceGroupedByServiceList.InstanceGroupedByInfrastructureV2> instanceGroupedByInfrastructureList =
          groupedByInfrastructure(entry1.getValue().getKey(), infraIdToInfraNameMap, false);
      List<InstanceGroupedByServiceList.InstanceGroupedByInfrastructureV2> instanceGroupedByClusterList =
          groupedByInfrastructure(entry1.getValue().getValue(), clusterIdAgentIdMap, true);

      // fetch last deployed time by taking maximum or latest time from all executions that are grouped

      Long lastDeployedAt = 0l;

      if (EmptyPredicate.isNotEmpty(instanceGroupedByInfrastructureList)) {
        lastDeployedAt = Math.max(instanceGroupedByInfrastructureList.get(0).getLastDeployedAt(), lastDeployedAt);
      }

      if (EmptyPredicate.isNotEmpty(instanceGroupedByClusterList)) {
        lastDeployedAt = Math.max(instanceGroupedByClusterList.get(0).getLastDeployedAt(), lastDeployedAt);
      }

      instanceGroupedByEnvironmentList.add(InstanceGroupedByServiceList.InstanceGroupedByEnvironmentV2.builder()
                                               .envId(envId)
                                               .envName(envName)
                                               .lastDeployedAt(lastDeployedAt)
                                               .instanceGroupedByClusterList(instanceGroupedByClusterList)
                                               .instanceGroupedByInfraList(instanceGroupedByInfrastructureList)
                                               .build());
    }

    // sort based on last deployed time generated by taking maximum or latest time from all executions that are
    // grouped

    Collections.sort(instanceGroupedByEnvironmentList,
        new Comparator<InstanceGroupedByServiceList.InstanceGroupedByEnvironmentV2>() {
          public int compare(InstanceGroupedByServiceList.InstanceGroupedByEnvironmentV2 o1,
              InstanceGroupedByServiceList.InstanceGroupedByEnvironmentV2 o2) {
            return -(o1.getLastDeployedAt().compareTo(o2.getLastDeployedAt()));
          }
        });

    return instanceGroupedByEnvironmentList;
  }

  public List<InstanceGroupedByServiceList.InstanceGroupedByInfrastructureV2> groupedByInfrastructure(
      Map<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>> infraToPipelineExecutionMap,
      Map<String, String> infraIdToInfraNameMap, boolean isGitOps) {
    List<InstanceGroupedByServiceList.InstanceGroupedByInfrastructureV2> instanceGroupedByInfrastructureList =
        new ArrayList<>();

    for (Map.Entry<String, List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>> entry2 :
        infraToPipelineExecutionMap.entrySet()) {
      String infraId = entry2.getKey();
      String infraName = infraIdToInfraNameMap.get(infraId);

      List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution> pipelineExecutions = entry2.getValue();

      pipelineExecutions = groupByPipelineExecution(pipelineExecutions);

      // sort based on last deployed time generated by taking maximum or latest time from all executions that are
      // grouped

      Collections.sort(
          pipelineExecutions, new Comparator<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution>() {
            public int compare(InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution o1,
                InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution o2) {
              return -(o1.getLastDeployedAt().compareTo(o2.getLastDeployedAt()));
            }
          });

      if (!isGitOps) {
        instanceGroupedByInfrastructureList.add(InstanceGroupedByServiceList.InstanceGroupedByInfrastructureV2.builder()
                                                    .infraName(infraName)
                                                    .infraIdentifier(infraId)
                                                    .lastDeployedAt(pipelineExecutions.get(0).getLastDeployedAt())
                                                    .instanceGroupedByPipelineExecutionList(pipelineExecutions)
                                                    .build());
      } else {
        instanceGroupedByInfrastructureList.add(InstanceGroupedByServiceList.InstanceGroupedByInfrastructureV2.builder()
                                                    .agentIdentifier(infraName)
                                                    .clusterIdentifier(infraId)
                                                    .lastDeployedAt(pipelineExecutions.get(0).getLastDeployedAt())
                                                    .instanceGroupedByPipelineExecutionList(pipelineExecutions)
                                                    .build());
      }
    }

    // sort based on last deployed time generated by taking maximum or latest time from all executions that are
    // grouped

    Collections.sort(instanceGroupedByInfrastructureList,
        new Comparator<InstanceGroupedByServiceList.InstanceGroupedByInfrastructureV2>() {
          public int compare(InstanceGroupedByServiceList.InstanceGroupedByInfrastructureV2 o1,
              InstanceGroupedByServiceList.InstanceGroupedByInfrastructureV2 o2) {
            return -(o1.getLastDeployedAt().compareTo(o2.getLastDeployedAt()));
          }
        });

    return instanceGroupedByInfrastructureList;
  }

  public List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution> groupByPipelineExecution(
      List<InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution> pipelineExecutions) {
    Map<String, InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution> instanceGroupedByPipelineExecutionMap =
        new HashMap<>();

    for (InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution pipelineExecution : pipelineExecutions) {
      if (instanceGroupedByPipelineExecutionMap.containsKey(pipelineExecution.getLastPipelineExecutionId())) {
        InstanceGroupedByServiceList.InstanceGroupedByPipelineExecution instanceGroupedByPipelineExecution =
            instanceGroupedByPipelineExecutionMap.get(pipelineExecution.getLastPipelineExecutionId());
        instanceGroupedByPipelineExecution.setCount(
            instanceGroupedByPipelineExecution.getCount() + pipelineExecution.getCount());
        if (pipelineExecution.getLastDeployedAt() > instanceGroupedByPipelineExecution.getLastDeployedAt()) {
          instanceGroupedByPipelineExecution.setLastDeployedAt(pipelineExecution.getLastDeployedAt());
        }
      } else {
        instanceGroupedByPipelineExecutionMap.put(pipelineExecution.getLastPipelineExecutionId(), pipelineExecution);
      }
    }
    return new ArrayList<>(instanceGroupedByPipelineExecutionMap.values());
  }

  @Override
  public EnvironmentGroupInstanceDetails getEnvironmentInstanceDetails(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String serviceIdentifier,
      EnvironmentFilterPropertiesDTO environmentFilterPropertiesDTO) {
    Boolean isGitOps = isGitopsEnabled(accountIdentifier, orgIdentifier, projectIdentifier, serviceIdentifier);
    List<EnvironmentInstanceCountModel> environmentInstanceCounts =
        instanceDashboardService.getInstanceCountForEnvironmentFilteredByService(
            accountIdentifier, orgIdentifier, projectIdentifier, serviceIdentifier, isGitOps);

    Set<String> envIds = new HashSet<>();
    Map<String, Integer> envToCountMap = new HashMap<>();

    DashboardServiceHelper.constructEnvironmentCountMap(environmentInstanceCounts, envToCountMap, envIds);

    List<EnvironmentGroupEntity> environmentGroupEntities =
        fetchEnvGrpList(accountIdentifier, orgIdentifier, projectIdentifier, envIds);

    List<Environment> environments = fetchEnvList(accountIdentifier, orgIdentifier, projectIdentifier, envIds);

    Map<String, String> envIdToEnvNameMap = new HashMap<>();
    Map<String, EnvironmentType> envIdToEnvTypeMap = new HashMap<>();
    DashboardServiceHelper.constructEnvironmentNameAndTypeMap(environments, envIdToEnvNameMap, envIdToEnvTypeMap);

    List<ArtifactDeploymentDetailModel> artifactDeploymentDetails = instanceDashboardService.getLastDeployedInstance(
        accountIdentifier, orgIdentifier, projectIdentifier, serviceIdentifier, true, isGitOps);
    Map<String, ArtifactDeploymentDetail> artifactDeploymentDetailsMap =
        DashboardServiceHelper.constructEnvironmentToArtifactDeploymentMap(
            artifactDeploymentDetails, envIdToEnvNameMap);
    Map<String, ServicePipelineWithRevertInfo> pipelineExecutionDetailsMap = getPipelineExecutionDetailsWithRevertInfo(
        artifactDeploymentDetailsMap.values()
            .stream()
            .filter(artifactDeploymentDetail
                -> EmptyPredicate.isNotEmpty(artifactDeploymentDetail.getLastPipelineExecutionId()))
            .map(artifactDeploymentDetail -> artifactDeploymentDetail.getLastPipelineExecutionId())
            .collect(Collectors.toList()));
    List<String> pipelineExecutionIdsWhereRollbackOccurred = getPipelineExecutionsWhereRollbackOccurred(
        pipelineExecutionDetailsMap.values()
            .stream()
            .filter(servicePipelineWithRevertInfo
                -> EmptyPredicate.isNotEmpty(servicePipelineWithRevertInfo.getPipelineExecutionId()))
            .map(servicePipelineWithRevertInfo -> servicePipelineWithRevertInfo.getPipelineExecutionId())
            .collect(Collectors.toList()));
    return DashboardServiceHelper.getEnvironmentInstanceDetailsFromMap(artifactDeploymentDetailsMap, envToCountMap,
        envIdToEnvNameMap, envIdToEnvTypeMap, environmentGroupEntities, environmentFilterPropertiesDTO,
        pipelineExecutionDetailsMap, pipelineExecutionIdsWhereRollbackOccurred);

    /* saveDefaultSequenceInDB(
         environmentGroupInstanceDetails, accountIdentifier, orgIdentifier, projectIdentifier, serviceIdentifier);

     environmentGroupInstanceDetails.setEnvironmentGroupInstanceDetails(
         getServiceSequence(accountIdentifier, orgIdentifier, projectIdentifier, serviceIdentifier,
             environmentGroupInstanceDetails.getEnvironmentGroupInstanceDetails()));*/
  }

  private List<EnvironmentGroupInstanceDetails.EnvironmentGroupInstanceDetail> getServiceSequence(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceIdentifier,
      List<EnvironmentGroupInstanceDetails.EnvironmentGroupInstanceDetail> environmentGroupInstanceDetailList) {
    Optional<ServiceSequence> serviceSequenceOptional =
        serviceSequenceService.get(accountIdentifier, orgIdentifier, projectIdentifier, serviceIdentifier);
    ServiceSequence serviceSequence;
    if (serviceSequenceOptional.isPresent()) {
      serviceSequence = serviceSequenceOptional.get();
    } else {
      throw new InvalidRequestException(format("Failed to get service sequence for service id: ", serviceIdentifier));
    }

    CustomSequenceDTO sequenceDTO;
    if (isNull(serviceSequence.getCustomSequence())) {
      return environmentGroupInstanceDetailList;

    } else {
      sequenceDTO = serviceSequence.getCustomSequence();

      List<CustomSequenceDTO.EnvAndEnvGroupCard> envAndEnvGroupCards = sequenceDTO.getEnvAndEnvGroupCardList();

      List<EnvironmentGroupInstanceDetails.EnvironmentGroupInstanceDetail> newEnvironmentGroupInstanceDetailList =
          new ArrayList<>();

      List<EnvironmentGroupInstanceDetails.EnvironmentGroupInstanceDetail> appendListForEnvGrpNotPresentInSequence =
          new ArrayList<>();

      HashMap<String, EnvironmentGroupInstanceDetails.EnvironmentGroupInstanceDetail> envGrpMapForListFromDB =
          new HashMap<>();

      HashMap<String, CustomSequenceDTO.EnvAndEnvGroupCard> envGrpMapForCustomSequence = new HashMap<>();

      envAndEnvGroupCards.forEach(envGroupDetail
          -> envGrpMapForCustomSequence.put(
              envGroupDetail.getName() + envGroupDetail.getIdentifier() + envGroupDetail.isEnvGroup(), envGroupDetail));

      environmentGroupInstanceDetailList.forEach(envGroupDetail
          -> envGrpMapForListFromDB.put(
              envGroupDetail.getName() + envGroupDetail.getId() + envGroupDetail.getIsEnvGroup().toString(),
              envGroupDetail));

      for (String key : envGrpMapForListFromDB.keySet()) {
        if (isNull(envGrpMapForCustomSequence.get(key))) {
          appendListForEnvGrpNotPresentInSequence.add(envGrpMapForListFromDB.get(key));
        }
      }
      envAndEnvGroupCards.forEach(envGroup
          -> filterInstanceDetailsList(newEnvironmentGroupInstanceDetailList, envGrpMapForListFromDB, envGroup));

      newEnvironmentGroupInstanceDetailList.addAll(0, appendListForEnvGrpNotPresentInSequence);

      saveCustomSequenceInDB(newEnvironmentGroupInstanceDetailList, envGrpMapForCustomSequence, accountIdentifier,
          orgIdentifier, projectIdentifier, serviceIdentifier);

      return newEnvironmentGroupInstanceDetailList;
    }
  }

  private void saveCustomSequenceInDB(
      List<EnvironmentGroupInstanceDetails.EnvironmentGroupInstanceDetail> newEnvironmentGroupInstanceDetailList,
      HashMap<String, CustomSequenceDTO.EnvAndEnvGroupCard> envAndEnvGroupCards, String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String serviceIdentifier) {
    List<CustomSequenceDTO.EnvAndEnvGroupCard> sequenceToStoreInDB = new ArrayList<>();
    for (EnvironmentGroupInstanceDetails.EnvironmentGroupInstanceDetail envGrpDetail :
        newEnvironmentGroupInstanceDetailList) {
      if (isNull(envAndEnvGroupCards.get(
              envGrpDetail.getName() + envGrpDetail.getId() + envGrpDetail.getIsEnvGroup().toString()))) {
        sequenceToStoreInDB.add(createEnvAndEnvGroupCard(envGrpDetail, true));
      } else {
        sequenceToStoreInDB.add(createEnvAndEnvGroupCard(envGrpDetail, false));
      }
    }

    CustomSequenceDTO customSequenceDTO =
        CustomSequenceDTO.builder().EnvAndEnvGroupCardList(sequenceToStoreInDB).build();
    ServiceSequence serviceSequence = ServiceSequence.builder()
                                          .customSequence(customSequenceDTO)
                                          .accountId(accountIdentifier)
                                          .projectIdentifier(projectIdentifier)
                                          .orgIdentifier(orgIdentifier)
                                          .serviceIdentifier(serviceIdentifier)
                                          .build();
    serviceSequenceService.upsertCustomSequence(serviceSequence);
  }

  private void filterInstanceDetailsList(
      List<EnvironmentGroupInstanceDetails.EnvironmentGroupInstanceDetail> newEnvironmentGroupInstanceDetailList,
      HashMap<String, EnvironmentGroupInstanceDetails.EnvironmentGroupInstanceDetail> mapForEnvGroup,
      CustomSequenceDTO.EnvAndEnvGroupCard envGroup) {
    if (!isNull(mapForEnvGroup.get(envGroup.getName() + envGroup.getIdentifier() + envGroup.isEnvGroup()))) {
      newEnvironmentGroupInstanceDetailList.add(
          mapForEnvGroup.get(envGroup.getName() + envGroup.getIdentifier() + envGroup.isEnvGroup()));
    }
  }

  private void saveDefaultSequenceInDB(EnvironmentGroupInstanceDetails environmentGroupInstanceDetails,
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceIdentifier) {
    List<EnvironmentGroupInstanceDetails.EnvironmentGroupInstanceDetail> environmentGroupInstanceDetailList =
        environmentGroupInstanceDetails.getEnvironmentGroupInstanceDetails();
    List<CustomSequenceDTO.EnvAndEnvGroupCard> envAndEnvGroupCards = new ArrayList<>();

    environmentGroupInstanceDetailList.forEach(
        envGrpDetail -> envAndEnvGroupCards.add(createEnvAndEnvGroupCard(envGrpDetail, false)));
    CustomSequenceDTO defaultSequenceDTO =
        CustomSequenceDTO.builder().EnvAndEnvGroupCardList(envAndEnvGroupCards).build();
    ServiceSequence serviceSequence = ServiceSequence.builder()
                                          .defaultSequence(defaultSequenceDTO)
                                          .accountId(accountIdentifier)
                                          .projectIdentifier(projectIdentifier)
                                          .orgIdentifier(orgIdentifier)
                                          .serviceIdentifier(serviceIdentifier)
                                          .build();
    serviceSequenceService.upsertDefaultSequence(serviceSequence);
  }

  private CustomSequenceDTO.EnvAndEnvGroupCard createEnvAndEnvGroupCard(
      EnvironmentGroupInstanceDetails.EnvironmentGroupInstanceDetail envGrpDetail, boolean isNew) {
    return CustomSequenceDTO.EnvAndEnvGroupCard.builder()
        .isEnvGroup(envGrpDetail.getIsEnvGroup())
        .identifier(envGrpDetail.getId())
        .environmentTypes(envGrpDetail.getEnvironmentTypes())
        .isNew(isNew)
        .name(envGrpDetail.getName())
        .build();
  }

  @Override
  public ArtifactInstanceDetails getArtifactInstanceDetails(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceIdentifier) {
    Boolean isGitOps = isGitopsEnabled(accountIdentifier, orgIdentifier, projectIdentifier, serviceIdentifier);

    List<ArtifactDeploymentDetailModel> artifactDeploymentDetails = instanceDashboardService.getLastDeployedInstance(
        accountIdentifier, orgIdentifier, projectIdentifier, serviceIdentifier, false, isGitOps);

    Set<String> envIds = new HashSet<>();

    Map<String, Map<String, ArtifactDeploymentDetail>> artifactDeploymentDetailsMap =
        DashboardServiceHelper.constructArtifactToLastDeploymentMap(artifactDeploymentDetails, envIds);

    List<EnvironmentGroupEntity> environmentGroupEntities =
        fetchEnvGrpList(accountIdentifier, orgIdentifier, projectIdentifier, envIds);

    List<Environment> environments = fetchEnvList(accountIdentifier, orgIdentifier, projectIdentifier, envIds);

    Map<String, String> envIdToEnvNameMap = new HashMap<>();
    Map<String, EnvironmentType> envIdToEnvTypeMap = new HashMap<>();

    DashboardServiceHelper.constructEnvironmentNameAndTypeMap(environments, envIdToEnvNameMap, envIdToEnvTypeMap);
    Map<String, List<ArtifactDeploymentDetail>> envToArtifactMap =
        DashboardServiceHelper.constructEnvironmentToArtifactDeploymentListMap(
            artifactDeploymentDetails, envIdToEnvNameMap);

    return DashboardServiceHelper.getArtifactInstanceDetailsFromMap(
        artifactDeploymentDetailsMap, envIdToEnvNameMap, envIdToEnvTypeMap, environmentGroupEntities, envToArtifactMap);
  }

  private List<EnvironmentGroupEntity> fetchEnvGrpList(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, Set<String> envIds) {
    Criteria criteria = environmentGroupService.formCriteria(
        accountIdentifier, orgIdentifier, projectIdentifier, false, "", "", null, false);
    Page<EnvironmentGroupEntity> environmentGroupEntitiesPage =
        environmentGroupService.list(criteria, Pageable.unpaged(), projectIdentifier, orgIdentifier, accountIdentifier);

    List<EnvironmentGroupEntity> environmentGroupEntities = null;

    if (environmentGroupEntitiesPage != null) {
      environmentGroupEntities = environmentGroupEntitiesPage.getContent();
      for (EnvironmentGroupEntity environmentGroupEntity : environmentGroupEntities) {
        if (EmptyPredicate.isNotEmpty(environmentGroupEntity.getEnvIdentifiers())) {
          envIds.addAll(environmentGroupEntity.getEnvIdentifiers()
                            .stream()
                            .map(envId
                                -> convertIdToRef(environmentGroupEntity.getAccountId(),
                                    environmentGroupEntity.getOrgIdentifier(),
                                    environmentGroupEntity.getProjectIdentifier(), envId))
                            .collect(Collectors.toList()));
        }
      }
    }
    return environmentGroupEntities;
  }

  private List<Environment> fetchEnvList(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, Set<String> envIds) {
    return environmentService.fetchesNonDeletedEnvironmentFromListOfRefs(
        accountIdentifier, orgIdentifier, projectIdentifier, new ArrayList<>(envIds));
  }

  @Override
  public OpenTaskDetails getOpenTasks(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String serviceIdentifier, long startInterval) {
    final List<String> STATUS_LIST =
        Arrays
            .asList(ExecutionStatus.ABORTED, ExecutionStatus.ABORTEDBYFREEZE, ExecutionStatus.FAILED,
                ExecutionStatus.EXPIRED, ExecutionStatus.APPROVALWAITING)
            .stream()
            .map(ExecutionStatus::name)
            .collect(Collectors.toList());
    String query = DashboardServiceHelper.buildOpenTaskQuery(
        accountIdentifier, orgIdentifier, projectIdentifier, serviceIdentifier, startInterval);
    Map<String, String> pipelineExecutionIdToFailureInfoMap =
        getPipelineExecutionIdAndFailureDetailsFromServiceInfraInfo(query);
    Map<String, ServicePipelineInfo> servicePipelineInfoMap =
        getPipelineExecutionDetails(new ArrayList<>(pipelineExecutionIdToFailureInfoMap.keySet()), STATUS_LIST);
    List<ServicePipelineWithRevertInfo> servicePipelineInfoList = new ArrayList<>();
    if (isNotEmpty(servicePipelineInfoMap.values())) {
      servicePipelineInfoList.addAll(servicePipelineInfoMap.values()
                                         .stream()
                                         .map(servicePipelineInfo
                                             -> ServicePipelineWithRevertInfo.builder()
                                                    .name(servicePipelineInfo.getName())
                                                    .deployedById(servicePipelineInfo.getDeployedById())
                                                    .deployedByName(servicePipelineInfo.getDeployedByName())
                                                    .identifier(servicePipelineInfo.getIdentifier())
                                                    .pipelineExecutionId(servicePipelineInfo.getPipelineExecutionId())
                                                    .planExecutionId(servicePipelineInfo.getPlanExecutionId())
                                                    .lastExecutedAt(servicePipelineInfo.getLastExecutedAt())
                                                    .status(servicePipelineInfo.getStatus())
                                                    .failureDetail(pipelineExecutionIdToFailureInfoMap.getOrDefault(
                                                        servicePipelineInfo.getPipelineExecutionId(), ""))
                                                    .build())
                                         .collect(Collectors.toList()));
    }
    DashboardServiceHelper.sortServicePipelineInfoList(servicePipelineInfoList);
    return OpenTaskDetails.builder().pipelineDeploymentDetails(servicePipelineInfoList).build();
  }

  @Override
  public List<String> getPipelineExecutionsWhereRollbackOccurred(List<String> pipelineExecutionIdList) {
    String query = DashboardServiceHelper.buildRollbackDurationQuery(pipelineExecutionIdList);
    return getPipelineExecutionIdFromServiceInfraInfo(query);
  }

  private List<InstanceGroupedByArtifactList.InstanceGroupedByArtifact> groupedByArtifacts(
      Map<String, Map<String, List<InstanceGroupedByArtifactList.InstanceGroupedByInfrastructure>>> buildEnvInfraMap,
      Map<String, String> envIdToEnvNameMap, Map<String, String> buildIdToArtifactPathMap) {
    List<InstanceGroupedByArtifactList.InstanceGroupedByArtifact> instanceGroupedByArtifactList = new ArrayList<>();

    for (Map.Entry<String, Map<String, List<InstanceGroupedByArtifactList.InstanceGroupedByInfrastructure>>> entry :
        buildEnvInfraMap.entrySet()) {
      String buildId = entry.getKey();
      String artifactPath = buildIdToArtifactPathMap.get(buildId);
      Map<String, List<InstanceGroupedByArtifactList.InstanceGroupedByInfrastructure>> envInfraMap = entry.getValue();
      List<InstanceGroupedByArtifactList.InstanceGroupedByEnvironment> instanceGroupedByEnvironmentList =
          new ArrayList<>();

      for (Map.Entry<String, List<InstanceGroupedByArtifactList.InstanceGroupedByInfrastructure>> entry1 :
          envInfraMap.entrySet()) {
        String envId = entry1.getKey();
        String envName = envIdToEnvNameMap.get(envId);

        List<InstanceGroupedByArtifactList.InstanceGroupedByInfrastructure> instanceList = entry1.getValue();
        List<InstanceGroupedByArtifactList.InstanceGroupedByInfrastructure> instanceGroupedByInfrastructureList =
            instanceList.stream().filter(e -> e.getInfraIdentifier() != null).collect(Collectors.toList());
        List<InstanceGroupedByArtifactList.InstanceGroupedByInfrastructure> instanceGroupedByClusterList =
            instanceList.stream().filter(e -> e.getClusterIdentifier() != null).collect(Collectors.toList());

        instanceGroupedByEnvironmentList.add(InstanceGroupedByArtifactList.InstanceGroupedByEnvironment.builder()
                                                 .envId(envId)
                                                 .envName(envName)
                                                 .instanceGroupedByClusterList(instanceGroupedByClusterList)
                                                 .instanceGroupedByInfraList(instanceGroupedByInfrastructureList)
                                                 .build());
      }
      instanceGroupedByArtifactList.add(InstanceGroupedByArtifactList.InstanceGroupedByArtifact.builder()
                                            .artifactVersion(buildId)
                                            .artifactPath(artifactPath)
                                            .instanceGroupedByEnvironmentList(instanceGroupedByEnvironmentList)
                                            .build());
    }

    return instanceGroupedByArtifactList;
  }

  /*
    Returns list of instances for each build id for given account+org+project+service+env
  */
  @Override
  public InstancesByBuildIdList getActiveInstancesByServiceIdEnvIdAndBuildIds(String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String serviceId, String envId, List<String> buildIds,
      String infraId, String clusterId, String pipelineExecutionId) {
    String serviceRef =
        IdentifierRefHelper.getRefFromIdentifierOrRef(accountIdentifier, orgIdentifier, projectIdentifier, serviceId);
    String envRef =
        IdentifierRefHelper.getRefFromIdentifierOrRef(accountIdentifier, orgIdentifier, projectIdentifier, envId);

    List<InstanceDetailsByBuildId> instancesByBuildIdList =
        instanceDashboardService.getActiveInstancesByServiceIdEnvIdAndBuildIds(accountIdentifier, orgIdentifier,
            projectIdentifier, serviceRef, envRef, buildIds, getCurrentTime(), infraId, clusterId, pipelineExecutionId,
            isGitopsEnabled(accountIdentifier, orgIdentifier, projectIdentifier, serviceId));

    return InstancesByBuildIdList.builder().instancesByBuildIdList(instancesByBuildIdList).build();
  }

  @Override
  public InstanceDetailsByBuildId getActiveInstanceDetails(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String serviceIdentifier, String envIdentifier, String infraIdentifier,
      String clusterIdentifier, String pipelineExecutionId, String buildId) {
    return instanceDashboardService.getActiveInstanceDetails(accountIdentifier, orgIdentifier, projectIdentifier,
        serviceIdentifier, envIdentifier, infraIdentifier, clusterIdentifier, pipelineExecutionId, buildId,
        isGitopsEnabled(accountIdentifier, orgIdentifier, projectIdentifier, serviceIdentifier));
  }

  @Override
  public InstanceDetailGroupedByPipelineExecutionList getInstanceDetailGroupedByPipelineExecution(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceIdentifier,
      String envIdentifier, EnvironmentType environmentType, String infraIdentifier, String clusterIdentifier,
      String displayName) {
    boolean isGitOps = isGitopsEnabled(accountIdentifier, orgIdentifier, projectIdentifier, serviceIdentifier);

    List<InstanceDetailGroupedByPipelineExecutionList.InstanceDetailGroupedByPipelineExecution>
        instanceDetailGroupedByPipelineExecutionList = new ArrayList<>();

    if (isEmpty(displayName)) {
      instanceDetailGroupedByPipelineExecutionList.addAll(
          instanceDashboardService.getActiveInstanceDetailGroupedByPipelineExecution(accountIdentifier, orgIdentifier,
              projectIdentifier, serviceIdentifier, envIdentifier, environmentType, infraIdentifier, clusterIdentifier,
              EMPTY_ARTIFACT, isGitOps));

      instanceDetailGroupedByPipelineExecutionList.addAll(
          instanceDashboardService.getActiveInstanceDetailGroupedByPipelineExecution(accountIdentifier, orgIdentifier,
              projectIdentifier, serviceIdentifier, envIdentifier, environmentType, infraIdentifier, clusterIdentifier,
              null, isGitOps));
    } else {
      instanceDetailGroupedByPipelineExecutionList.addAll(
          instanceDashboardService.getActiveInstanceDetailGroupedByPipelineExecution(accountIdentifier, orgIdentifier,
              projectIdentifier, serviceIdentifier, envIdentifier, environmentType, infraIdentifier, clusterIdentifier,
              displayName, isGitOps));
    }
    // sort based on last deployed time

    Collections.sort(instanceDetailGroupedByPipelineExecutionList,
        new Comparator<InstanceDetailGroupedByPipelineExecutionList.InstanceDetailGroupedByPipelineExecution>() {
          public int compare(InstanceDetailGroupedByPipelineExecutionList.InstanceDetailGroupedByPipelineExecution o1,
              InstanceDetailGroupedByPipelineExecutionList.InstanceDetailGroupedByPipelineExecution o2) {
            return (int) (o2.getLastDeployedAt() - o1.getLastDeployedAt());
          }
        });

    return InstanceDetailGroupedByPipelineExecutionList.builder()
        .instanceDetailGroupedByPipelineExecutionList(instanceDetailGroupedByPipelineExecutionList)
        .build();
  }

  /*
    Returns instance count summary for given account+org+project+serviceId, includes rate of change in count since
    provided timestamp
  */
  @Override
  public io.harness.ng.overview.dto.ActiveServiceInstanceSummary getActiveServiceInstanceSummary(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceId, long timestampInMs) {
    // build service ref from id
    String serviceRef =
        IdentifierRefHelper.getRefFromIdentifierOrRef(accountIdentifier, orgIdentifier, projectIdentifier, serviceId);
    Pair<InstanceCountDetailsByEnvTypeBase, InstanceCountDetailsByEnvTypeBase> countDetailsByEnvTypeBasePair =
        getActiveServiceInstanceSummaryHelper(
            accountIdentifier, orgIdentifier, projectIdentifier, serviceRef, timestampInMs);

    InstanceCountDetailsByEnvTypeBase currentCountDetails = countDetailsByEnvTypeBasePair.getValue();
    InstanceCountDetailsByEnvTypeBase prevCountDetails = countDetailsByEnvTypeBasePair.getKey();

    double changeRate =
        calculateChangeRate(prevCountDetails.getTotalInstances(), currentCountDetails.getTotalInstances());

    return ActiveServiceInstanceSummary.builder().countDetails(currentCountDetails).changeRate(changeRate).build();
  }

  @Override
  public ActiveServiceInstanceSummaryV2 getActiveServiceInstanceSummaryV2(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceId, long timestampInMs) {
    String serviceRef =
        IdentifierRefHelper.getRefFromIdentifierOrRef(accountIdentifier, orgIdentifier, projectIdentifier, serviceId);

    Pair<InstanceCountDetailsByEnvTypeBase, InstanceCountDetailsByEnvTypeBase> countDetailsByEnvTypeBasePair =
        getActiveServiceInstanceSummaryHelper(
            accountIdentifier, orgIdentifier, projectIdentifier, serviceRef, timestampInMs);

    InstanceCountDetailsByEnvTypeBase currentCountDetails = countDetailsByEnvTypeBasePair.getValue();
    InstanceCountDetailsByEnvTypeBase prevCountDetails = countDetailsByEnvTypeBasePair.getKey();

    ChangeRate changeRate =
        calculateChangeRateV2(prevCountDetails.getTotalInstances(), currentCountDetails.getTotalInstances());

    return ActiveServiceInstanceSummaryV2.builder().countDetails(currentCountDetails).changeRate(changeRate).build();
  }

  public Pair<InstanceCountDetailsByEnvTypeBase, InstanceCountDetailsByEnvTypeBase>
  getActiveServiceInstanceSummaryHelper(
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

    return MutablePair.of(prevCountDetails, currentCountDetails);
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

    final long tunedStartTimeInMs = startTimeInMs;
    final long tunedEndTimeInMs = endTimeInMs;

    String serviceRef =
        IdentifierRefHelper.getRefFromIdentifierOrRef(accountIdentifier, orgIdentifier, projectIdentifier, serviceId);

    final String query =
        "select reportedat, SUM(instancecount) as count from ng_instance_stats_day where accountid = ? and orgid = ? and projectid = ? and serviceid = ? and reportedat >= ? and reportedat <= ? group by reportedat order by reportedat asc";

    int totalTries = 0;
    boolean successfulOperation = false;
    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      ResultSet resultSet = null;
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(query)) {
        statement.setString(1, accountIdentifier);
        // org/project can be optional
        statement.setString(2, orgIdentifier);
        statement.setString(3, projectIdentifier);
        statement.setString(4, serviceRef);
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

    final long tunedStartTimeInMs = startTimeInMs;
    final long tunedEndTimeInMs = endTimeInMs;

    String serviceRef =
        IdentifierRefHelper.getRefFromIdentifierOrRef(accountIdentifier, orgIdentifier, projectIdentifier, serviceId);
    final String query =
        "select reportedat, envid, SUM(instancecount) as count from ng_instance_stats_day where accountid = ? and orgid = ? and projectid = ? and serviceid = ? and reportedat >= ? and reportedat <= ? group by reportedat, envid order by reportedat asc";

    int totalTries = 0;
    boolean successfulOperation = false;
    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      ResultSet resultSet = null;
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(query)) {
        statement.setString(1, accountIdentifier);
        // org/project can be absent in org/acc level dashboards
        statement.setString(2, orgIdentifier);
        statement.setString(3, projectIdentifier);
        statement.setString(4, serviceRef);
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
    String serviceRef =
        IdentifierRefHelper.getRefFromIdentifierOrRef(accountIdentifier, orgIdentifier, projectIdentifier, serviceId);
    String query = queryBuilderDeployments(
        accountIdentifier, orgIdentifier, projectIdentifier, serviceRef, startTimeInMs, endTimeInMs);
    String queryServiceNameTagId = queryBuilderServiceTag(
        queryToGetId(accountIdentifier, orgIdentifier, projectIdentifier, serviceRef), serviceRef);
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
        serviceEntityServiceImpl.get(accountIdentifier, orgIdentifier, projectIdentifier, serviceId, false);
    ServiceEntity serviceEntity = service.get();

    String serviceRef =
        IdentifierRefHelper.getRefFromIdentifierOrRef(accountIdentifier, orgIdentifier, projectIdentifier, serviceId);
    Set<String> deploymentTypes =
        getDeploymentType(accountIdentifier, orgIdentifier, projectIdentifier, Arrays.asList(serviceRef))
            .getOrDefault(serviceRef, new HashSet<>());

    Set<IconDTO> iconDTOSet = new HashSet<>();
    Map<String, String> serviceRefToTemplateRef = new HashMap<>();
    getServiceToTemplateRef(
        deploymentTypes, serviceEntity.getYaml(), serviceRef, new HashMap<>(), serviceRefToTemplateRef);
    if (!isEmpty(serviceRefToTemplateRef.get(serviceId))) {
      updateIconDTOList(accountIdentifier, orgIdentifier, projectIdentifier, serviceRefToTemplateRef.get(serviceId),
          deploymentTypes, iconDTOSet);
    }

    return ServiceHeaderInfo.builder()
        .identifier(serviceId)
        .name(serviceEntity.getName())
        .deploymentIconList(iconDTOSet)
        .description(serviceEntity.getDescription())
        .deploymentTypes(deploymentTypes)
        .createdAt(serviceEntity.getCreatedAt())
        .lastModifiedAt(serviceEntity.getLastModifiedAt())
        .build();
  }

  public void updateIconDTOList(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String templateRef, Set<String> deploymentTypes, Set<IconDTO> iconDTOSet) {
    String icon = getIcon(accountIdentifier, projectIdentifier, orgIdentifier, templateRef);
    deploymentTypes.forEach(
        deploymentType -> iconDTOSet.add(setIcon(IconDTO.builder().deploymentType(deploymentType).build(), icon)));
  }

  private IconDTO setIcon(IconDTO iconDTO, String icon) {
    if (CUSTOM_DEPLOYMENT.equals(iconDTO.getDeploymentType())) {
      iconDTO.setIcon(icon);
    }
    return iconDTO;
  }

  private String getIcon(String accountIdentifier, String projectIdentifier, String orgIdentifier, String templateRef) {
    try {
      TemplateResponseDTO responseDTO = customDeploymentYamlHelper.getScopedTemplateResponseDTO(
          accountIdentifier, orgIdentifier, projectIdentifier, templateRef, null);
      if (!isNull(responseDTO)) {
        return responseDTO.getIcon();
      } else {
        return "";
      }
    } catch (Exception e) {
      log.error("could not fetch icon for template with template ref : {}", templateRef);
      return "";
    }
  }

  /*
  Returns a list of last successfully buildId deployed to environments for given account+org+project+service
*/
  @Override
  public io.harness.ng.overview.dto.EnvironmentDeploymentInfo getEnvironmentDeploymentDetailsByServiceId(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceId) {
    String serviceRef =
        IdentifierRefHelper.getRefFromIdentifierOrRef(accountIdentifier, orgIdentifier, projectIdentifier, serviceId);
    String query =
        queryBuilderDeploymentsWithArtifactsDetails(accountIdentifier, orgIdentifier, projectIdentifier, serviceRef);
    List<EnvironmentInfoByServiceId> environmentInfoByServiceIds = getEnvironmentWithArtifactDetails(query);
    return EnvironmentDeploymentInfo.builder().environmentInfoByServiceId(environmentInfoByServiceIds).build();
  }

  @Override
  public InstanceGroupedByServiceList.InstanceGroupedByService getActiveServiceDeploymentsList(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceIdentifier) {
    String serviceRef = IdentifierRefHelper.getRefFromIdentifierOrRef(
        accountIdentifier, orgIdentifier, projectIdentifier, serviceIdentifier);
    InstanceGroupedByServiceList instanceGroupedByServiceList = getActiveServiceDeploymentsListHelper(
        accountIdentifier, orgIdentifier, projectIdentifier, serviceRef, null, null);
    return getInstanceGroupedByService(instanceGroupedByServiceList);
  }

  public InstanceGroupedByServiceList getActiveServiceDeploymentsListHelper(String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String serviceIdentifier, String buildIdentifier,
      String envIdentifier) {
    List<ActiveServiceInstanceInfoV2> activeServiceInstanceInfoList = new ArrayList<>();
    Set<String> envIdsWithInfra = new HashSet<>();

    String query = queryActiveServiceDeploymentsInfo(
        accountIdentifier, orgIdentifier, projectIdentifier, serviceIdentifier, buildIdentifier, envIdentifier);
    List<ActiveServiceDeploymentsInfo> deploymentsInfo = getActiveServiceDeploymentsInfo(query);

    List<String> pipelineExecutionIdList = new ArrayList<>();

    deploymentsInfo.forEach(deploymentInfo -> {
      if (deploymentInfo.getPipelineExecutionId() != null) {
        pipelineExecutionIdList.add(deploymentInfo.getPipelineExecutionId());
      }
      if (deploymentInfo.getInfrastructureIdentifier() != null) {
        envIdsWithInfra.add(deploymentInfo.getEnvId());
      }
    });

    Map<String, ServicePipelineInfo> pipelineExecutionDetailsMap = getPipelineExecutionDetails(pipelineExecutionIdList);

    deploymentsInfo.forEach(deploymentInfo -> {
      final String infrastructureIdentifier = deploymentInfo.getInfrastructureIdentifier();
      final String envId = deploymentInfo.getEnvId();

      if (envId == null || (infrastructureIdentifier == null && envIdsWithInfra.contains(envId))) {
        return;
      }

      final String artifact = deploymentInfo.getTag();
      final String envName = deploymentInfo.getEnvName();
      final String pipelineExecutionId = deploymentInfo.getPipelineExecutionId();
      final String infrastructureName = deploymentInfo.getInfrastructureName();
      final String artifactPath = deploymentInfo.getArtifactPath();
      final String serviceId = deploymentInfo.getServiceId();
      final String serviceName = deploymentInfo.getServiceName();
      final String displayName = DashboardServiceHelper.getDisplayNameFromArtifact(artifactPath, artifact);

      String lastPipelineExecutionId = null;
      String lastPipelineExecutionName = null;
      Long lastDeployedAt = null;
      if (pipelineExecutionId != null) {
        ServicePipelineInfo servicePipelineInfo = pipelineExecutionDetailsMap.get(pipelineExecutionId);
        if (servicePipelineInfo != null) {
          lastPipelineExecutionId = servicePipelineInfo.getPlanExecutionId();
          lastPipelineExecutionName = servicePipelineInfo.getIdentifier();
          lastDeployedAt = servicePipelineInfo.getLastExecutedAt();
        }
      }
      if (lastPipelineExecutionId == null || lastDeployedAt == null) {
        return;
      }
      activeServiceInstanceInfoList.add(new ActiveServiceInstanceInfoV2(serviceId, serviceName, envId, envName,
          infrastructureIdentifier, infrastructureName, null, null, lastPipelineExecutionId, lastPipelineExecutionName,
          lastDeployedAt, artifact, displayName, null));
    });

    return getInstanceGroupedByServiceListHelper(activeServiceInstanceInfoList);
  }

  public String queryBuilderDeploymentsWithArtifactsDetails(
      String accountId, String orgId, String projectId, String serviceId) {
    return "SELECT DISTINCT ON (env_id) env_name, env_id, artifact_image, tag, service_startts, "
        + "service_endts, service_name, service_id from " + tableNameServiceAndInfra + " where "
        + String.format("accountid='%s' and ", accountId) + String.format("orgidentifier='%s' and ", orgId)
        + String.format("projectidentifier='%s' and ", projectId) + String.format("service_id='%s'", serviceId)
        + " and service_status = 'SUCCESS' AND tag is not null order by env_id , service_endts DESC;";
  }

  public String queryActiveServiceDeploymentsInfo(
      String accountId, String orgId, String projectId, String serviceId, String buildId, String envId) {
    String query = String.format(
        "select distinct on (env_id,infrastructureIdentifier) tag, env_id, env_name, service_id, service_name, infrastructureIdentifier, infrastructureName, artifact_image, pipeline_execution_summary_cd_id from %s where accountid='%s' and orgidentifier='%s' and projectidentifier='%s' and service_status = 'SUCCESS' AND tag is not null AND service_id is not null",
        tableNameServiceAndInfra, accountId, orgId, projectId);

    if (serviceId != null) {
      query = query + String.format(" and service_id='%s'", serviceId);
    }
    if (buildId != null) {
      query = query + String.format(" and tag='%s'", buildId);
    }
    if (envId != null) {
      query = query + String.format(" and env_id='%s'", envId);
    }

    return query + " order by env_id , infrastructureIdentifier, service_endts DESC;";
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
                                      .serviceId(resultSet.getString(SERVICE_ID))
                                      .serviceName(resultSet.getString(SERVICE_NAME))
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

  public List<ActiveServiceDeploymentsInfo> getActiveServiceDeploymentsInfo(String queryStatus) {
    List<ActiveServiceDeploymentsInfo> activeServiceDeploymentsInfoList = new ArrayList<>();

    int totalTries = 0;
    boolean successfulOperation = false;
    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      ResultSet resultSet = null;
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(queryStatus)) {
        resultSet = statement.executeQuery();
        while (resultSet != null && resultSet.next()) {
          ActiveServiceDeploymentsInfo activeServiceDeploymentsInfo =
              ActiveServiceDeploymentsInfo.builder()
                  .envId(resultSet.getString("env_id"))
                  .envName(resultSet.getString("env_name"))
                  .tag(resultSet.getString("tag"))
                  .pipelineExecutionId(resultSet.getString(PIPELINE_EXECUTION_SUMMARY_CD_ID))
                  .infrastructureIdentifier(resultSet.getString("infrastructureIdentifier"))
                  .infrastructureName(resultSet.getString("infrastructureName"))
                  .artifactPath(resultSet.getString("artifact_image"))
                  .serviceId(resultSet.getString(SERVICE_ID))
                  .serviceName(resultSet.getString(SERVICE_NAME))
                  .build();
          activeServiceDeploymentsInfoList.add(activeServiceDeploymentsInfo);
        }
        successfulOperation = true;
      } catch (SQLException ex) {
        totalTries++;
      } finally {
        DBUtils.close(resultSet);
      }
    }
    return activeServiceDeploymentsInfoList;
  }

  private Boolean isGitopsEnabled(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceId) {
    Optional<ServiceEntity> serviceEntity =
        serviceEntityServiceImpl.getService(accountIdentifier, orgIdentifier, projectIdentifier, serviceId);
    if (serviceEntity.isPresent()) {
      ServiceEntity service = serviceEntity.get();
      return service.getGitOpsEnabled() != null ? service.getGitOpsEnabled() : Boolean.FALSE;
    }
    return Boolean.FALSE;
  }
}
