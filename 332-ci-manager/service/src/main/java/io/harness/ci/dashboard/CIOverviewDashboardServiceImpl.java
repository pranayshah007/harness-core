/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.core.ci.services;

import static io.harness.beans.execution.ExecutionSource.Type.WEBHOOK;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;

import io.harness.app.beans.entities.BuildActiveInfo;
import io.harness.app.beans.entities.BuildCount;
import io.harness.app.beans.entities.BuildExecutionInfo;
import io.harness.app.beans.entities.BuildFailureInfo;
import io.harness.app.beans.entities.BuildHealth;
import io.harness.app.beans.entities.BuildInfo;
import io.harness.app.beans.entities.BuildRepositoryCount;
import io.harness.app.beans.entities.CIUsageResult;
import io.harness.app.beans.entities.DashboardBuildExecutionInfo;
import io.harness.app.beans.entities.DashboardBuildRepositoryInfo;
import io.harness.app.beans.entities.DashboardBuildsHealthInfo;
import io.harness.app.beans.entities.LastRepositoryInfo;
import io.harness.app.beans.entities.RepositoryBuildInfo;
import io.harness.app.beans.entities.RepositoryInfo;
import io.harness.app.beans.entities.RepositoryInformation;
import io.harness.app.beans.entities.StatusAndTime;
import io.harness.exception.InvalidRequestException;
import io.harness.licensing.usage.beans.ReferenceDTO;
import io.harness.licensing.usage.beans.UsageDataDTO;
import io.harness.ng.core.OrgProjectIdentifier;
import io.harness.ng.core.dashboard.AuthorInfo;
import io.harness.ng.core.dashboard.GitInfo;
import io.harness.ng.core.dashboard.ServiceDeploymentInfo;
import io.harness.ng.core.dto.ProjectDTO;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.project.remote.ProjectClient;
import io.harness.remote.client.NGRestUtils;
import io.harness.timescaledb.DBUtils;
import io.harness.timescaledb.TimeScaleDBService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class CIOverviewDashboardServiceImpl implements CIOverviewDashboardService {
  @Inject TimeScaleDBService timeScaleDBService;

  @Inject private ProjectClient projectClient;
  private static final String tableNameServiceAndInfra = "service_infra_info";
  private static final String tableName = "pipeline_execution_summary_ci";
  private static final long HR_IN_MS = 60 * 60 * 1000;
  private static final long DAY_IN_MS = 24 * HR_IN_MS;

  private final List<String> failedList = Arrays.asList(ExecutionStatus.FAILED.name(), ExecutionStatus.ABORTED.name(),
      ExecutionStatus.EXPIRED.name(), ExecutionStatus.IGNOREFAILED.name(), ExecutionStatus.ERRORED.name());

  private final List<String> activeStatusList = Arrays.asList(ExecutionStatus.RUNNING.name(),
      ExecutionStatus.ASYNCWAITING.name(), ExecutionStatus.TASKWAITING.name(), ExecutionStatus.TIMEDWAITING.name(),
      ExecutionStatus.PAUSED.name(), ExecutionStatus.PAUSING.name());

  private static final int MAX_RETRY_COUNT = 1;

  // This ID comes, and should only come from internal source only. No need to use prepare statement here
  public String queryBuilderServiceTag(String queryIdCdTable) {
    return String.format("select service_name,tag,pipeline_execution_summary_cd_id from " + tableNameServiceAndInfra
            + " where "
            + "pipeline_execution_summary_cd_id=%s and service_name is not null;",
        queryIdCdTable);
  }

  public long getActiveCommitterCount(String accountId) {
    long timestamp = System.currentTimeMillis();
    long totalTries = 0;
    String query = "select count(distinct moduleinfo_author_id) from " + tableName
        + " where accountid=? and moduleinfo_type ='CI' and moduleinfo_author_id is not null and moduleinfo_is_private=true and trigger_type='"
        + WEBHOOK + "' and startts<=? and startts>=?;";

    while (totalTries <= MAX_RETRY_COUNT) {
      totalTries++;
      ResultSet resultSet = null;
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(query)) {
        statement.setString(1, accountId);
        statement.setLong(2, timestamp);
        statement.setLong(3, timestamp - 30 * DAY_IN_MS);
        resultSet = statement.executeQuery();
        return resultSet.next() ? resultSet.getLong(1) : 0L;
      } catch (SQLException ex) {
        log.error("Caught SQL Exception:" + ex.getMessage());
      } finally {
        DBUtils.close(resultSet);
      }
    }
    return -1L;
  }

  @Override
  public UsageDataDTO getActiveCommitter(String accountId, long timestamp) {
    long totalTries = 0;
    String query = "select distinct moduleinfo_author_id, projectidentifier , orgidentifier from " + tableName
        + " where accountid=? and moduleinfo_type ='CI' and moduleinfo_author_id is not null and moduleinfo_is_private=true and trigger_type='"
        + WEBHOOK + "' and startts<=? and startts>=?;";

    while (totalTries <= MAX_RETRY_COUNT) {
      totalTries++;
      ResultSet resultSet = null;
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(query)) {
        statement.setString(1, accountId);
        statement.setLong(2, timestamp);
        statement.setLong(3, timestamp - 30 * DAY_IN_MS);
        resultSet = statement.executeQuery();
        List<ReferenceDTO> usageReferences = new ArrayList<>();
        Set<String> uniqueIds = new HashSet<>();
        while (resultSet != null && resultSet.next()) {
          String id = resultSet.getString("moduleinfo_author_id");
          if (isEmpty(id)) {
            continue;
          }
          ReferenceDTO reference = ReferenceDTO.builder()
                                       .identifier(id)
                                       .projectIdentifier(resultSet.getString("projectidentifier"))
                                       .orgIdentifier(resultSet.getString("orgidentifier"))
                                       .build();
          uniqueIds.add(id);
          usageReferences.add(reference);
        }
        return UsageDataDTO.builder()
            .count(uniqueIds.size())
            .displayName("Last 30 Days")
            .references(usageReferences)
            .build();
      } catch (SQLException ex) {
        log.error("Caught SQL Exception:" + ex.getMessage());
      } finally {
        DBUtils.close(resultSet);
      }
    }
    return null;
  }

  public StatusAndTime queryCalculatorForStatusAndTime(
      String accountId, List<OrgProjectIdentifier> orgProjectIdentifiers, long startInterval, long endInterval) {
    if (isEmpty(accountId)) {
      throw new InvalidRequestException("Account ID cannot be empty");
    }

    if (isEmpty(orgProjectIdentifiers)) {
      throw new InvalidRequestException("No projects are accessible by current user");
    }

    if (startInterval <= 0 && endInterval <= 0) {
      throw new InvalidRequestException("Timestamp must be a positive long");
    }

    String selectStatusQuery = "select status, startts from " + tableName + " where accountid=?";
    String orgIds = orgProjectIdentifiers.stream()
                        .map(OrgProjectIdentifier::getOrgIdentifier)
                        .distinct()
                        .map(o -> String.format("'%s'", o))
                        .collect(Collectors.joining(","));
    String projectIds = orgProjectIdentifiers.stream()
                            .map(OrgProjectIdentifier::getProjectIdentifier)
                            .distinct()
                            .map(p -> String.format("'%s'", p))
                            .collect(Collectors.joining(","));

    selectStatusQuery += " and orgidentifier IN (" + orgIds + ") and projectidentifier IN (" + projectIds
        + ") and startts>=? and startts<?;";

    long totalTries = 0;
    while (totalTries <= MAX_RETRY_COUNT) {
      totalTries++;
      ResultSet resultSet = null;
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(selectStatusQuery)) {
        statement.setString(1, accountId);
        statement.setLong(2, startInterval);
        statement.setLong(3, endInterval);
        resultSet = statement.executeQuery();
        return parseResultToStatusAndTime(resultSet);
      } catch (SQLException ex) {
        log.error("Caught SQL Exception:" + ex.getMessage());
      } finally {
        DBUtils.close(resultSet);
      }
    }
    return StatusAndTime.builder().status(Collections.emptyList()).time(Collections.emptyList()).build();
  }

  private StatusAndTime parseResultToStatusAndTime(ResultSet resultSet) throws SQLException {
    List<String> status = new ArrayList<>();
    List<Long> time = new ArrayList<>();
    while (resultSet != null && resultSet.next()) {
      status.add(resultSet.getString("status"));
      if (resultSet.getString("startts") != null) {
        time.add(Long.valueOf(resultSet.getString("startts")));
      } else {
        time.add(null);
      }
    }
    return StatusAndTime.builder().status(status).time(time).build();
  }

  public BuildFailureInfo getBuildFailureInfo(String name, String identifier, String branch_name, String commit,
      String commit_id, long startTs, long endTs, AuthorInfo author, String status, String planExecutionId,
      String triggerType, GitInfo gitInfo) {
    return BuildFailureInfo.builder()
        .piplineName(name)
        .pipelineIdentifier(identifier)
        .branch(branch_name)
        .triggerType(triggerType)
        .commit(commit)
        .planExecutionId(planExecutionId)
        .commitID(commit_id)
        .gitInfo(gitInfo)
        .startTs(startTs)
        .endTs(endTs == -1L ? null : endTs)
        .author(author)
        .status(status)
        .build();
  }

  public BuildActiveInfo getBuildActiveInfo(String name, String identifier, String branch_name, String commit,
      String commit_id, AuthorInfo author, long startTs, String status, String planExecutionId, long endTs,
      String triggerType, GitInfo gitInfo) {
    return BuildActiveInfo.builder()
        .piplineName(name)
        .pipelineIdentifier(identifier)
        .branch(branch_name)
        .commit(commit)
        .commitID(commit_id)
        .planExecutionId(planExecutionId)
        .triggerType(triggerType)
        .gitInfo(gitInfo)
        .author(author)
        .startTs(startTs)
        .status(status)
        .endTs(endTs == -1 ? null : endTs)
        .build();
  }

  @Override
  public BuildHealth getCountAndRate(long currentCount, long previousCount) {
    double rate = 0.0;
    if (previousCount != 0) {
      rate = (currentCount - previousCount) / (double) previousCount;
    }
    rate = rate * 100;
    return BuildHealth.builder().count(currentCount).rate(rate).build();
  }

  @Override
  public DashboardBuildsHealthInfo getDashBoardBuildHealthInfoWithRate(String accountId, String orgId, String projectId,
      long startInterval, long endInterval, long previousStartInterval) {
    startInterval = getStartingDateEpochValue(startInterval);
    endInterval = getStartingDateEpochValue(endInterval);
    previousStartInterval = getStartingDateEpochValue(previousStartInterval);

    endInterval = endInterval + DAY_IN_MS;
    OrgProjectIdentifier orgProjectIdentifier =
        OrgProjectIdentifier.builder().orgIdentifier(orgId).projectIdentifier(projectId).build();

    StatusAndTime statusAndTime = queryCalculatorForStatusAndTime(
        accountId, Collections.singletonList(orgProjectIdentifier), previousStartInterval, endInterval);
    List<String> status = statusAndTime.getStatus();
    List<Long> time = statusAndTime.getTime();

    long currentTotal = 0, currentSuccess = 0, currentFailed = 0;
    long previousTotal = 0, previousSuccess = 0, previousFailed = 0;
    for (int i = 0; i < time.size(); i++) {
      long currentEpochValue = time.get(i);
      if (currentEpochValue >= startInterval && currentEpochValue < endInterval) {
        currentTotal++;
        if (status.get(i).contentEquals(ExecutionStatus.SUCCESS.name())) {
          currentSuccess++;
        } else if (failedList.contains(status.get(i))) {
          currentFailed++;
        }
      }

      // previous interval record
      if (currentEpochValue >= previousStartInterval && currentEpochValue < startInterval) {
        previousTotal++;
        if (status.get(i).contentEquals(ExecutionStatus.SUCCESS.name())) {
          previousSuccess++;
        } else if (failedList.contains(status.get(i))) {
          previousFailed++;
        }
      }
    }

    return DashboardBuildsHealthInfo.builder()
        .builds(BuildInfo.builder()
                    .total(getCountAndRate(currentTotal, previousTotal))
                    .success(getCountAndRate(currentSuccess, previousSuccess))
                    .failed(getCountAndRate(currentFailed, previousFailed))
                    .build())
        .build();
  }

  @Override
  public DashboardBuildExecutionInfo getBuildExecutionBetweenIntervals(
      String accountId, String orgId, String projectId, long startInterval, long endInterval) {
    List<ProjectDTO> accessibleProjectList = NGRestUtils.getResponse(projectClient.getProjectList(accountId, null));
    List<OrgProjectIdentifier> orgProjectIdentifierList = getOrgProjectIdentifier(accessibleProjectList);
    List<OrgProjectIdentifier> orgProjectIdentifiers =
        getOrgProjectIdentifierList(orgProjectIdentifierList, orgId, projectId);

    startInterval = getStartingDateEpochValue(startInterval);
    endInterval = getStartingDateEpochValue(endInterval);

    endInterval = endInterval + DAY_IN_MS;

    List<BuildExecutionInfo> buildExecutionInfoList = new ArrayList<>();

    StatusAndTime statusAndTime =
        queryCalculatorForStatusAndTime(accountId, orgProjectIdentifiers, startInterval, endInterval);
    List<String> status = statusAndTime.getStatus();
    List<Long> time = statusAndTime.getTime();

    long startDateCopy = startInterval;
    long endDateCopy = endInterval;
    while (startDateCopy < endDateCopy) {
      long total = 0, success = 0, failed = 0, expired = 0, aborted = 0;
      for (int i = 0; i < time.size(); i++) {
        if (startDateCopy == getStartingDateEpochValue(time.get(i))) {
          total++;
          if (status.get(i).contentEquals(ExecutionStatus.SUCCESS.name())) {
            success++;
          } else if (status.get(i).contentEquals(ExecutionStatus.EXPIRED.name())) {
            expired++;
          } else if (status.get(i).contentEquals(ExecutionStatus.ABORTED.name())) {
            aborted++;
          } else if (failedList.contains(status.get(i))) {
            failed++;
          }
        }
      }
      BuildCount buildCount =
          BuildCount.builder().total(total).success(success).expired(expired).aborted(aborted).failed(failed).build();
      buildExecutionInfoList.add(BuildExecutionInfo.builder().time(startDateCopy).builds(buildCount).build());
      startDateCopy = startDateCopy + DAY_IN_MS;
    }

    return DashboardBuildExecutionInfo.builder().buildExecutionInfoList(buildExecutionInfoList).build();
  }

  public List<BuildFailureInfo> queryCalculatorBuildFailureInfo(
      String accountId, String orgId, String projectId, long limit) {
    if (accountId == null || orgId == null || projectId == null) {
      throw new InvalidRequestException("Account ID, OrgID, ProjectID cannot be empty");
    }
    List<BuildFailureInfo> buildFailureInfos = new ArrayList<>();
    String selectStatusQuery =
        "select name, pipelineidentifier, moduleinfo_branch_name, moduleinfo_branch_commit_message, moduleinfo_event, moduleinfo_repository, planexecutionid, source_branch, moduleinfo_branch_commit_id, moduleinfo_author_id, author_avatar, startts, trigger_type, endts, status, id  from "
        + tableName + " where accountid=? and orgidentifier=? and projectidentifier=? and status in (";

    StringBuilder totalBuildSqlBuilder = new StringBuilder(2048);
    totalBuildSqlBuilder.append(selectStatusQuery);
    for (String failed : failedList) {
      totalBuildSqlBuilder.append(String.format("'%s',", failed));
    }

    totalBuildSqlBuilder.deleteCharAt(totalBuildSqlBuilder.length() - 1);

    totalBuildSqlBuilder.append(") ORDER BY startts DESC LIMIT ?;");

    int totalTries = 0;
    List<String> serviceIds = new ArrayList<>();
    while (totalTries <= MAX_RETRY_COUNT) {
      totalTries++;
      ResultSet resultSet = null;
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(totalBuildSqlBuilder.toString())) {
        setPrepareStatement(accountId, orgId, projectId, statement);
        statement.setLong(4, limit);
        resultSet = statement.executeQuery();
        while (resultSet != null && resultSet.next()) {
          parseResultToBuildFailureInfo(resultSet, buildFailureInfos, serviceIds);
        }
        break;
      } catch (SQLException ex) {
        buildFailureInfos.clear();
        serviceIds.clear();
        log.error("Caught SQL Exception:" + ex.getMessage());
      } finally {
        DBUtils.close(resultSet);
      }
    }

    fillBuildFailureInfos(serviceIds, buildFailureInfos);

    return buildFailureInfos;
  }

  private void fillBuildFailureInfos(List<String> serviceIds, List<BuildFailureInfo> buildFailureInfos) {
    for (int i = 0; i < serviceIds.size(); i++) {
      List<ServiceDeploymentInfo> serviceDeploymentInfoList;
      if (serviceIds.get(i) != null) {
        String queryServiceTag = queryBuilderServiceTag(String.format("'%s'", serviceIds.get(i)));
        serviceDeploymentInfoList = queryCalculatorServiceTag(queryServiceTag);
      } else {
        serviceDeploymentInfoList = Collections.emptyList();
      }
      buildFailureInfos.get(i).setServiceInfoList(serviceDeploymentInfoList);
    }
  }

  private void parseResultToBuildFailureInfo(
      ResultSet resultSet, List<BuildFailureInfo> buildFailureInfo, List<String> serviceIds) throws SQLException {
    long startTime = -1L;
    long endTime = -1L;
    if (resultSet.getString("startts") != null) {
      startTime = Long.parseLong(resultSet.getString("startts"));
    }
    if (resultSet.getString("endts") != null) {
      endTime = Long.parseLong(resultSet.getString("endts"));
    }

    GitInfo gitInfo = GitInfo.builder()
                          .targetBranch(resultSet.getString("moduleinfo_branch_name"))
                          .sourceBranch(resultSet.getString("source_branch"))
                          .repoName(resultSet.getString("moduleinfo_repository"))
                          .commit(resultSet.getString("moduleinfo_branch_commit_message"))
                          .commitID(resultSet.getString("moduleinfo_branch_commit_id"))
                          .eventType(resultSet.getString("moduleinfo_event"))
                          .build();

    AuthorInfo author = AuthorInfo.builder()
                            .name(resultSet.getString("moduleinfo_author_id"))
                            .url(resultSet.getString("author_avatar"))
                            .build();

    String status = resultSet.getString("status");
    String pipelineIdentifier = resultSet.getString("pipelineidentifier");
    buildFailureInfo.add(getBuildFailureInfo(resultSet.getString("name"), pipelineIdentifier,
        resultSet.getString("moduleinfo_branch_name"), resultSet.getString("moduleinfo_branch_commit_message"),
        resultSet.getString("moduleinfo_branch_commit_id"), startTime, endTime, author, status,
        resultSet.getString("planexecutionid"), resultSet.getString("trigger_type"), gitInfo));
    serviceIds.add(resultSet.getString("id"));
  }

  public List<ServiceDeploymentInfo> queryCalculatorServiceTag(String queryServiceTag) {
    List<ServiceDeploymentInfo> serviceTags = new ArrayList<>();

    int totalTries = 0;
    while (totalTries <= MAX_RETRY_COUNT) {
      totalTries++;
      ResultSet resultSet = null;
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(queryServiceTag)) {
        resultSet = statement.executeQuery();
        while (resultSet != null && resultSet.next()) {
          String service_name = resultSet.getString("service_name");
          String tag = resultSet.getString("tag");
          serviceTags.add(getServiceDeployment(service_name, tag));
        }
        return serviceTags;
      } catch (SQLException ex) {
        log.error("Caught SQL Exception:" + ex.getMessage());
      } finally {
        DBUtils.close(resultSet);
      }
    }
    return serviceTags;
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

  public List<BuildActiveInfo> queryCalculatorBuildActiveInfo(
      String accountId, String orgId, String projectId, long limit) {
    if (accountId == null || orgId == null || projectId == null) {
      throw new InvalidRequestException("Account ID, OrgID, ProjectID cannot be empty");
    }
    String selectStatusQuery =
        "select name, pipelineidentifier, moduleinfo_branch_name, planexecutionid, moduleinfo_branch_commit_message, moduleinfo_branch_commit_id, source_branch, moduleinfo_author_id, author_avatar, moduleinfo_event, moduleinfo_repository, startts, status, trigger_type, id   from "
        + tableName + " where accountid=? and orgidentifier=? and projectidentifier=? and ";

    StringBuilder totalBuildSqlBuilder = new StringBuilder(2048);
    totalBuildSqlBuilder.append(selectStatusQuery).append("status IN (");

    for (String active : activeStatusList) {
      totalBuildSqlBuilder.append(String.format("'%s',", active));
    }

    totalBuildSqlBuilder.deleteCharAt(totalBuildSqlBuilder.length() - 1);

    totalBuildSqlBuilder.append(") ORDER BY startts DESC LIMIT ?;");
    List<BuildActiveInfo> buildActiveInfos = new ArrayList<>();
    List<String> serviceIds = new ArrayList<>();
    int totalTries = 0;
    while (totalTries <= MAX_RETRY_COUNT) {
      totalTries++;
      ResultSet resultSet = null;
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(totalBuildSqlBuilder.toString())) {
        setPrepareStatement(accountId, orgId, projectId, statement);
        statement.setLong(4, limit);
        resultSet = statement.executeQuery();
        while (resultSet != null && resultSet.next()) {
          convertResultToBuildActiveInfo(resultSet, buildActiveInfos, serviceIds);
        }
        return buildActiveInfos;
      } catch (SQLException ex) {
        buildActiveInfos.clear();
        serviceIds.clear();
        log.error("Caught SQL Exception:" + ex.getMessage());
      } finally {
        DBUtils.close(resultSet);
      }
    }
    fillBuildActiveInfos(serviceIds, buildActiveInfos);
    return buildActiveInfos;
  }

  private void fillBuildActiveInfos(List<String> serviceIds, List<BuildActiveInfo> buildActiveInfos) {
    for (int i = 0; i < serviceIds.size(); i++) {
      List<ServiceDeploymentInfo> serviceDeploymentInfoList;
      if (serviceIds.get(i) != null) {
        String queryServiceTag = queryBuilderServiceTag(String.format("'%s'", serviceIds.get(i)));
        serviceDeploymentInfoList = queryCalculatorServiceTag(queryServiceTag);
      } else {
        serviceDeploymentInfoList = Collections.emptyList();
      }
      buildActiveInfos.get(i).setServiceInfoList(serviceDeploymentInfoList);
    }
  }

  private void convertResultToBuildActiveInfo(
      ResultSet resultSet, List<BuildActiveInfo> buildActiveInfos, List<String> serviceIds) throws SQLException {
    long startTime = -1L;
    if (resultSet.getString("startts") != null) {
      startTime = Long.parseLong(resultSet.getString("startts"));
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

    serviceIds.add(resultSet.getString("id"));

    AuthorInfo author = AuthorInfo.builder()
                            .name(resultSet.getString("moduleinfo_author_id"))
                            .url(resultSet.getString("author_avatar"))
                            .build();
    String pipelineIdentifier = resultSet.getString("pipelineIdentifier");
    buildActiveInfos.add(getBuildActiveInfo(resultSet.getString("name"), pipelineIdentifier,
        resultSet.getString("moduleinfo_branch_name"), resultSet.getString("moduleinfo_branch_commit_message"),
        resultSet.getString("moduleinfo_branch_commit_id"), author, startTime, resultSet.getString("status"),
        resultSet.getString("planexecutionid"), -1L, resultSet.getString("trigger_type"), gitInfo));
  }

  @Override
  public List<BuildFailureInfo> getDashboardBuildFailureInfo(
      String accountId, String orgId, String projectId, long days) {
    return queryCalculatorBuildFailureInfo(accountId, orgId, projectId, days);
  }

  @Override
  public List<BuildActiveInfo> getDashboardBuildActiveInfo(
      String accountId, String orgId, String projectId, long days) {
    return queryCalculatorBuildActiveInfo(accountId, orgId, projectId, days);
  }

  public RepositoryInformation queryRepositoryCalculator(
      String accountId, String orgId, String projectId, Long previousStartInterval, Long endInterval) {
    if (accountId == null || orgId == null || projectId == null) {
      throw new InvalidRequestException("Account ID, OrgID, ProjectID cannot be empty");
    }

    if (previousStartInterval <= 0 && endInterval <= 0) {
      throw new InvalidRequestException("Timestamp must be a positive long");
    }

    String selectStatusQuery =
        "select moduleinfo_repository, status, startts, endts, moduleinfo_branch_commit_message, moduleinfo_author_id, author_avatar  from "
        + tableName
        + " where accountid=? and orgidentifier=? and projectidentifier=? and moduleinfo_repository IS NOT NULL and startts>=? and startts<?;";

    int totalTries = 0;
    while (totalTries <= MAX_RETRY_COUNT) {
      totalTries++;
      ResultSet resultSet = null;
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(selectStatusQuery)) {
        setPrepareStatement(accountId, orgId, projectId, statement);
        statement.setLong(4, previousStartInterval);
        statement.setLong(5, endInterval);

        resultSet = statement.executeQuery();
        return parseResultToRepoInfo(resultSet);
      } catch (SQLException ex) {
        log.error("Caught SQL Exception:" + ex.getMessage());
      } finally {
        DBUtils.close(resultSet);
      }
    }

    return RepositoryInformation.builder()
        .repoName(Collections.emptyList())
        .status(Collections.emptyList())
        .startTime(Collections.emptyList())
        .endTime(Collections.emptyList())
        .commitMessage(Collections.emptyList())
        .authorInfoList(Collections.emptyList())
        .build();
  }

  private RepositoryInformation parseResultToRepoInfo(ResultSet resultSet) throws SQLException {
    List<String> repoName = new ArrayList<>();
    List<String> status = new ArrayList<>();
    List<Long> startTime = new ArrayList<>();
    List<Long> endTime = new ArrayList<>();
    List<String> commitMessage = new ArrayList<>();
    List<AuthorInfo> authorInfoList = new ArrayList<>();
    while (resultSet != null && resultSet.next()) {
      repoName.add(resultSet.getString("moduleinfo_repository"));
      status.add(resultSet.getString("status"));
      startTime.add(Long.valueOf(resultSet.getString("startts")));
      if (resultSet.getString("endts") != null) {
        endTime.add(Long.valueOf(resultSet.getString("endts")));
      } else {
        endTime.add(-1L);
      }
      AuthorInfo author = AuthorInfo.builder()
                              .name(resultSet.getString("moduleinfo_author_id"))
                              .url(resultSet.getString("author_avatar"))
                              .build();
      authorInfoList.add(author);
      commitMessage.add(resultSet.getString("moduleinfo_branch_commit_message"));
    }
    return RepositoryInformation.builder()
        .repoName(repoName)
        .status(status)
        .startTime(startTime)
        .endTime(endTime)
        .commitMessage(commitMessage)
        .authorInfoList(authorInfoList)
        .build();
  }

  @Override
  public DashboardBuildRepositoryInfo getDashboardBuildRepository(String accountId, String orgId, String projectId,
      long startInterval, long endInterval, long previousStartInterval) {
    startInterval = getStartingDateEpochValue(startInterval);
    endInterval = getStartingDateEpochValue(endInterval);
    previousStartInterval = getStartingDateEpochValue(previousStartInterval);

    endInterval = endInterval + DAY_IN_MS;

    HashMap<String, Integer> uniqueRepoName = new HashMap<>();

    RepositoryInformation repositoryInformation =
        queryRepositoryCalculator(accountId, orgId, projectId, previousStartInterval, endInterval);
    List<String> repoName = repositoryInformation.getRepoName();
    List<String> status = repositoryInformation.getStatus();
    List<Long> startTime = repositoryInformation.getStartTime();
    List<Long> endTime = repositoryInformation.getEndTime();
    List<String> commitMessage = repositoryInformation.getCommitMessage();

    List<AuthorInfo> authorInfo = repositoryInformation.getAuthorInfoList();

    for (String repository_name : repoName) {
      if (repository_name != null && !uniqueRepoName.containsKey(repository_name)) {
        uniqueRepoName.put(repository_name, 1);
      }
    }
    List<RepositoryInfo> repositoryInfoList = new ArrayList<>();
    for (String repositoryName : uniqueRepoName.keySet()) {
      long totalBuild = 0;
      long success = 0;
      long previousSuccess = 0;
      String lastCommit = null;
      long lastCommitTime = -1L;
      long lastCommitEndTime = -1L;
      AuthorInfo author = null;
      String lastStatus = null;

      HashMap<Long, Integer> buildCountMap = new HashMap<>();
      long startDateCopy = startInterval;
      long endDateCopy = endInterval;

      while (startDateCopy < endDateCopy) {
        buildCountMap.put(startDateCopy, 0);
        startDateCopy = startDateCopy + DAY_IN_MS;
      }

      for (int i = 0; i < repoName.size(); i++) {
        if (repoName.get(i).contentEquals(repositoryName)) {
          Long variableEpochValue = getStartingDateEpochValue(startTime.get(i));
          if (variableEpochValue >= startInterval && variableEpochValue < endInterval) {
            totalBuild++;

            buildCountMap.put(variableEpochValue, buildCountMap.get(variableEpochValue) + 1);

            if (status.get(i).contentEquals(ExecutionStatus.SUCCESS.name())) {
              success++;
            }

            if (lastCommitTime == -1) {
              lastCommit = commitMessage.get(i);
              lastCommitTime = startTime.get(i);
              lastStatus = status.get(i);
              lastCommitEndTime = endTime.get(i);
              author = authorInfo.get(i);

            } else {
              if (lastCommitTime < startTime.get(i)) {
                lastCommitTime = startTime.get(i);
                lastCommit = commitMessage.get(i);
                lastStatus = status.get(i);
                lastCommitEndTime = endTime.get(i);
                author = authorInfo.get(i);
              }
            }
          } else if (status.get(i).contentEquals(ExecutionStatus.SUCCESS.name())) {
            previousSuccess++;
          }
        }
      }

      List<RepositoryBuildInfo> buildCount = new ArrayList<>();
      startDateCopy = startInterval;
      endDateCopy = endInterval;

      while (startDateCopy < endDateCopy) {
        buildCount.add(RepositoryBuildInfo.builder()
                           .time(startDateCopy)
                           .builds(BuildRepositoryCount.builder().count(buildCountMap.get(startDateCopy)).build())
                           .build());
        startDateCopy = startDateCopy + DAY_IN_MS;
      }

      if (totalBuild > 0) {
        LastRepositoryInfo lastRepositoryInfo = LastRepositoryInfo.builder()
                                                    .StartTime(lastCommitTime)
                                                    .EndTime(lastCommitEndTime == -1L ? null : lastCommitEndTime)
                                                    .status(lastStatus)
                                                    .author(author)
                                                    .commit(lastCommit)
                                                    .build();
        repositoryInfoList.add(
            getRepositoryInfo(repositoryName, totalBuild, success, previousSuccess, lastRepositoryInfo, buildCount));
      }
    }

    return DashboardBuildRepositoryInfo.builder().repositoryInfo(repositoryInfoList).build();
  }

  @Override
  public CIUsageResult getCIUsageResult(String accountId, long timestamp) {
    return CIUsageResult.builder()
        .accountIdentifier(accountId)
        .timestamp(timestamp)
        .module("CI")
        .activeCommitters(getActiveCommitter(accountId, timestamp))
        .build();
  }

  private RepositoryInfo getRepositoryInfo(String repoName, long totalBuild, long success, long previousSuccess,
      LastRepositoryInfo lastRepositoryInfo, List<RepositoryBuildInfo> buildCount) {
    // percentOfSuccess
    double percentOfSuccess = 0.0;
    if (totalBuild != 0) {
      percentOfSuccess = success / (double) totalBuild;
      percentOfSuccess = percentOfSuccess * 100.0;
    }

    // successRate
    double successRate = 0.0;
    if (previousSuccess != 0) {
      successRate = (success - previousSuccess) / (double) previousSuccess;
      successRate = successRate * 100;
    }

    return RepositoryInfo.builder()
        .name(repoName)
        .buildCount(totalBuild)
        .successRate(successRate)
        .percentSuccess(percentOfSuccess)
        .lastRepository(lastRepositoryInfo)
        .countList(buildCount)
        .build();
  }

  public long getStartingDateEpochValue(long epochValue) {
    return epochValue - epochValue % DAY_IN_MS;
  }

  private void setPrepareStatement(
      String accountId, String orgId, String projectId, PreparedStatement preparedStatement) throws SQLException {
    preparedStatement.setString(1, accountId);
    preparedStatement.setString(2, orgId);
    preparedStatement.setString(3, projectId);
  }

  private List<OrgProjectIdentifier> getOrgProjectIdentifier(List<ProjectDTO> listOfAccessibleProject) {
    return emptyIfNull(listOfAccessibleProject)
        .stream()
        .map(projectDTO
            -> OrgProjectIdentifier.builder()
                   .orgIdentifier(projectDTO.getOrgIdentifier())
                   .projectIdentifier(projectDTO.getIdentifier())
                   .build())
        .collect(Collectors.toList());
  }

  private List<OrgProjectIdentifier> getOrgProjectIdentifierList(
      List<OrgProjectIdentifier> orgProjectIdentifierList, String orgIdentifier, String projectIdentifier) {
    if (isNotEmpty(orgIdentifier) && isNotEmpty(projectIdentifier)) {
      OrgProjectIdentifier orgProjectIdentifier =
          OrgProjectIdentifier.builder().orgIdentifier(orgIdentifier).projectIdentifier(projectIdentifier).build();
      if (orgProjectIdentifierList.contains(orgProjectIdentifier)) {
        return Collections.singletonList(orgProjectIdentifier);
      } else {
        throw new InvalidRequestException(
            format("Project with identifier %s in organization with identifier %s in not accessible to the user",
                projectIdentifier, orgIdentifier));
      }
    }
    return orgProjectIdentifierList;
  }
}
