/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.core.ci.services;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

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
import io.harness.licensing.usage.beans.ReferenceDTO;
import io.harness.licensing.usage.beans.UsageDataDTO;
import io.harness.ng.core.dashboard.AuthorInfo;
import io.harness.ng.core.dashboard.GitInfo;
import io.harness.ng.core.dashboard.ServiceDeploymentInfo;
import io.harness.pms.execution.ExecutionStatus;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class CIOverviewDashboardServiceImpl implements CIOverviewDashboardService {
  @Inject TimeScaleDBService timeScaleDBService;
  private String tableNameServiceAndInfra = "service_infra_info";
  private String tableName = "pipeline_execution_summary_ci";
  private String staticQuery = "select * from " + tableName + " where ";
  private final long HR_IN_MS = 60 * 60 * 1000;
  private final long DAY_IN_MS = 24 * HR_IN_MS;

  private List<String> failedList = Arrays.asList(ExecutionStatus.FAILED.name(), ExecutionStatus.ABORTED.name(),
      ExecutionStatus.EXPIRED.name(), ExecutionStatus.IGNOREFAILED.name(), ExecutionStatus.ERRORED.name());

  private List<String> activeStatusList = Arrays.asList(ExecutionStatus.RUNNING.name(),
      ExecutionStatus.ASYNCWAITING.name(), ExecutionStatus.TASKWAITING.name(), ExecutionStatus.TIMEDWAITING.name(),
      ExecutionStatus.PAUSED.name(), ExecutionStatus.PAUSING.name());

  private static final int MAX_RETRY_COUNT = 5;

  private String queryBuilderSelectStatusAndTime(
      String accountId, String orgId, String projectId, long startInterval, long endInterval) {
    String selectStatusQuery = "select status,startts from " + tableName + " where ";
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
      totalBuildSqlBuilder.append(String.format("startts>=%s and startts<%s;", startInterval, endInterval));
    }

    return totalBuildSqlBuilder.toString();
  }

  private String queryBuilderFailedStatusOrderBy(String accountId, String orgId, String projectId, long limit) {
    String selectStatusQuery =
        "select name, pipelineidentifier, moduleinfo_branch_name, moduleinfo_branch_commit_message, moduleinfo_event, moduleinfo_repository, planexecutionid, source_branch, moduleinfo_branch_commit_id, moduleinfo_author_id, author_avatar, startts, trigger_type, endts, status, id  from "
        + tableName + " where ";

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
    for (String failed : failedList) {
      totalBuildSqlBuilder.append(String.format("'%s',", failed));
    }

    totalBuildSqlBuilder.deleteCharAt(totalBuildSqlBuilder.length() - 1);

    totalBuildSqlBuilder.append(String.format(") ORDER BY startts DESC LIMIT %s;", limit));

    return totalBuildSqlBuilder.toString();
  }

  private String queryBuilderActiveStatusOrderBy(String accountId, String orgId, String projectId, long limit) {
    String selectStatusQuery =
        "select name, pipelineidentifier, moduleinfo_branch_name, planexecutionid, moduleinfo_branch_commit_message, moduleinfo_branch_commit_id, source_branch, moduleinfo_author_id, author_avatar, moduleinfo_event, moduleinfo_repository, startts, status, trigger_type, id   from "
        + tableName + " where ";

    StringBuilder totalBuildSqlBuilder = new StringBuilder(1024);
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

    totalBuildSqlBuilder.append("status IN (");
    for (String active : activeStatusList) {
      totalBuildSqlBuilder.append(String.format("'%s',", active));
    }

    totalBuildSqlBuilder.deleteCharAt(totalBuildSqlBuilder.length() - 1);

    totalBuildSqlBuilder.append(") ORDER BY startts DESC LIMIT " + limit + ";");

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

  private String queryBuilderSelectRepoInfo(
      String accountId, String orgId, String projectId, long previousStartInterval, long endInterval) {
    String selectStatusQuery =
        "select moduleinfo_repository, status, startts, endts, moduleinfo_branch_commit_message, moduleinfo_author_id, author_avatar  from "
        + tableName + " where ";

    StringBuilder totalBuildSqlBuilder = new StringBuilder(1024);
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

    totalBuildSqlBuilder.append(String.format("moduleinfo_repository IS NOT NULL and "));

    if (previousStartInterval > 0 && endInterval > 0) {
      totalBuildSqlBuilder.append(String.format("startts>=%s and startts<%s;", previousStartInterval, endInterval));
    }

    return totalBuildSqlBuilder.toString();
  }

  @Override
  public UsageDataDTO getActiveCommitter(String accountId, long timestamp) {
    long totalTries = 0;
    String query = "select distinct moduleinfo_author_id, projectidentifier , orgidentifier from " + tableName
        + " where accountid=? and moduleinfo_type ='CI' and moduleinfo_author_id is not null and moduleinfo_is_private=true and startts<=? and startts>=?;";

    while (totalTries <= MAX_RETRY_COUNT) {
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
        log.error(ex.getMessage());
        totalTries++;
      } finally {
        DBUtils.close(resultSet);
      }
    }
    return null;
  }

  public StatusAndTime queryCalculatorForStatusAndTime(String query) {
    long totalTries = 0;

    List<String> status = new ArrayList<>();
    List<Long> time = new ArrayList<>();
    boolean successfulOperation = false;
    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      ResultSet resultSet = null;
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(query)) {
        resultSet = statement.executeQuery();
        while (resultSet != null && resultSet.next()) {
          status.add(resultSet.getString("status"));
          if (resultSet.getString("startts") != null) {
            time.add(Long.valueOf(resultSet.getString("startts")));
          } else {
            time.add(null);
          }
        }
        successfulOperation = true;
      } catch (SQLException ex) {
        log.error(ex.getMessage());
        totalTries++;
      } finally {
        DBUtils.close(resultSet);
      }
    }
    return StatusAndTime.builder().status(status).time(time).build();
  }

  public BuildFailureInfo getBuildFailureInfo(String name, String identifier, String branch_name, String commit,
      String commit_id, long startTs, long endTs, AuthorInfo author, String status, String planExecutionId,
      String triggerType, GitInfo gitInfo, List<ServiceDeploymentInfo> serviceDeploymentInfos) {
    return BuildFailureInfo.builder()
        .piplineName(name)
        .pipelineIdentifier(identifier)
        .branch(branch_name)
        .triggerType(triggerType)
        .commit(commit)
        .planExecutionId(planExecutionId)
        .commitID(commit_id)
        .gitInfo(gitInfo)
        .serviceInfoList(serviceDeploymentInfos)
        .startTs(startTs)
        .endTs(endTs == -1L ? null : endTs)
        .author(author)
        .status(status)
        .build();
  }

  public BuildActiveInfo getBuildActiveInfo(String name, String identifier, String branch_name, String commit,
      String commit_id, AuthorInfo author, long startTs, String status, String planExecutionId, long endTs,
      String triggerType, GitInfo gitInfo, List<ServiceDeploymentInfo> serviceDeploymentInfos) {
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
        .serviceInfoList(serviceDeploymentInfos)
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

    String query = queryBuilderSelectStatusAndTime(accountId, orgId, projectId, previousStartInterval, endInterval);
    StatusAndTime statusAndTime = queryCalculatorForStatusAndTime(query);
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
    startInterval = getStartingDateEpochValue(startInterval);
    endInterval = getStartingDateEpochValue(endInterval);

    endInterval = endInterval + DAY_IN_MS;

    List<BuildExecutionInfo> buildExecutionInfoList = new ArrayList<>();

    String query = queryBuilderSelectStatusAndTime(accountId, orgId, projectId, startInterval, endInterval);
    StatusAndTime statusAndTime = queryCalculatorForStatusAndTime(query);
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

  public List<BuildFailureInfo> queryCalculatorBuildFailureInfo(String query) {
    List<BuildFailureInfo> buildFailureInfos = new ArrayList<>();
    int totalTries = 0;
    boolean successfulOperation = false;

    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      ResultSet resultSet = null;
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(query)) {
        resultSet = statement.executeQuery();
        while (resultSet != null && resultSet.next()) {
          long startTime = -1L;
          long endTime = -1L;
          if (resultSet.getString("startts") != null) {
            startTime = Long.parseLong(resultSet.getString("startts"));
          }
          if (resultSet.getString("endts") != null) {
            endTime = Long.parseLong(resultSet.getString("endts"));
          }

          List<ServiceDeploymentInfo> serviceDeploymentInfoList = new ArrayList<>();
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

          String id = resultSet.getString("id");
          String queryServiceTag = queryBuilderServiceTag(String.format("'%s'", id));
          serviceDeploymentInfoList = queryCalculatorServiceTag(queryServiceTag);
          String status = resultSet.getString("status");
          String pipelineIdentifier = resultSet.getString("pipelineidentifier");
          buildFailureInfos.add(getBuildFailureInfo(resultSet.getString("name"), pipelineIdentifier,
              resultSet.getString("moduleinfo_branch_name"), resultSet.getString("moduleinfo_branch_commit_message"),
              resultSet.getString("moduleinfo_branch_commit_id"), startTime, endTime, author, status,
              resultSet.getString("planexecutionid"), resultSet.getString("trigger_type"), gitInfo,
              serviceDeploymentInfoList));
        }
        successfulOperation = true;
      } catch (SQLException ex) {
        log.error(ex.getMessage());
        totalTries++;
      } finally {
        DBUtils.close(resultSet);
      }
    }
    return buildFailureInfos;
  }

  public List<ServiceDeploymentInfo> queryCalculatorServiceTag(String queryServiceTag) {
    List<ServiceDeploymentInfo> serviceTags = new ArrayList<>();

    int totalTries = 0;
    boolean successfulOperation = false;
    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      ResultSet resultSet = null;
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(queryServiceTag)) {
        resultSet = statement.executeQuery();
        while (resultSet != null && resultSet.next()) {
          String service_name = resultSet.getString("service_name");
          String tag = resultSet.getString("tag");
          serviceTags.add(getServiceDeployment(service_name, tag));
        }

        successfulOperation = true;
      } catch (SQLException ex) {
        log.error(ex.getMessage());
        totalTries++;
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

  public List<BuildActiveInfo> queryCalculatorBuildActiveInfo(String query) {
    List<BuildActiveInfo> buildActiveInfos = new ArrayList<>();
    int totalTries = 0;
    List<GitInfo> gitInfoList = new ArrayList<>();
    boolean successfulOperation = false;
    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      ResultSet resultSet = null;
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(query)) {
        resultSet = statement.executeQuery();
        while (resultSet != null && resultSet.next()) {
          long startTime = -1L;
          if (resultSet.getString("startts") != null) {
            startTime = Long.parseLong(resultSet.getString("startts"));
          }

          List<ServiceDeploymentInfo> serviceDeploymentInfoList = new ArrayList<>();
          // GitInfo
          GitInfo gitInfo = GitInfo.builder()
                                .targetBranch(resultSet.getString("moduleinfo_branch_name"))
                                .sourceBranch(resultSet.getString("source_branch"))
                                .repoName(resultSet.getString("moduleinfo_repository"))
                                .commit(resultSet.getString("moduleinfo_branch_commit_message"))
                                .commitID(resultSet.getString("moduleinfo_branch_commit_id"))
                                .eventType(resultSet.getString("moduleinfo_event"))
                                .build();

          String id = resultSet.getString("id");
          String queryServiceTag = queryBuilderServiceTag(String.format("'%s'", id));
          serviceDeploymentInfoList = queryCalculatorServiceTag(queryServiceTag);

          AuthorInfo author = AuthorInfo.builder()
                                  .name(resultSet.getString("moduleinfo_author_id"))
                                  .url(resultSet.getString("author_avatar"))
                                  .build();
          String pipelineIdentifier = resultSet.getString("pipelineIdentifier");
          buildActiveInfos.add(getBuildActiveInfo(resultSet.getString("name"), pipelineIdentifier,
              resultSet.getString("moduleinfo_branch_name"), resultSet.getString("moduleinfo_branch_commit_message"),
              resultSet.getString("moduleinfo_branch_commit_id"), author, startTime, resultSet.getString("status"),
              resultSet.getString("planexecutionid"), -1L, resultSet.getString("trigger_type"), gitInfo,
              serviceDeploymentInfoList));
        }
        successfulOperation = true;
      } catch (SQLException ex) {
        log.error(ex.getMessage());
        totalTries++;
      } finally {
        DBUtils.close(resultSet);
      }
    }
    return buildActiveInfos;
  }
  @Override
  public List<BuildFailureInfo> getDashboardBuildFailureInfo(
      String accountId, String orgId, String projectId, long days) {
    String query = queryBuilderFailedStatusOrderBy(accountId, orgId, projectId, days);

    return queryCalculatorBuildFailureInfo(query);
  }

  @Override
  public List<BuildActiveInfo> getDashboardBuildActiveInfo(
      String accountId, String orgId, String projectId, long days) {
    String query = queryBuilderActiveStatusOrderBy(accountId, orgId, projectId, days);

    return queryCalculatorBuildActiveInfo(query);
  }

  public RepositoryInformation queryRepositoryCalculator(String query) {
    List<String> repoName = new ArrayList<>();
    List<String> status = new ArrayList<>();
    List<Long> startTime = new ArrayList<>();
    List<Long> endTime = new ArrayList<>();
    List<String> commitMessage = new ArrayList<>();
    List<AuthorInfo> authorInfoList = new ArrayList<>();

    int totalTries = 0;
    boolean successfulOperation = false;
    while (!successfulOperation && totalTries <= MAX_RETRY_COUNT) {
      ResultSet resultSet = null;
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(query)) {
        resultSet = statement.executeQuery();
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
        successfulOperation = true;
      } catch (SQLException ex) {
        log.error(ex.getMessage());
        totalTries++;
      } finally {
        DBUtils.close(resultSet);
      }
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
    String query = queryBuilderSelectRepoInfo(accountId, orgId, projectId, previousStartInterval, endInterval);
    List<String> repoName = new ArrayList<>();
    List<String> status = new ArrayList<>();
    List<Long> startTime = new ArrayList<>();
    List<Long> endTime = new ArrayList<>();
    List<String> commitMessage = new ArrayList<>();

    HashMap<String, Integer> uniqueRepoName = new HashMap<>();

    RepositoryInformation repositoryInformation = queryRepositoryCalculator(query);
    repoName = repositoryInformation.getRepoName();
    status = repositoryInformation.getStatus();
    startTime = repositoryInformation.getStartTime();
    endTime = repositoryInformation.getEndTime();
    commitMessage = repositoryInformation.getCommitMessage();

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
}
