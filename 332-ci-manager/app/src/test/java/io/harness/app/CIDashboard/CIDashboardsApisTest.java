/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.app.CIDashboard;

import static io.harness.rule.OwnerRule.JAMIE;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyObject;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
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
import io.harness.category.element.UnitTests;
import io.harness.core.ci.services.CIOverviewDashboardServiceImpl;
import io.harness.exception.InvalidRequestException;
import io.harness.licensing.usage.beans.ReferenceDTO;
import io.harness.licensing.usage.beans.UsageDataDTO;
import io.harness.ng.core.dashboard.AuthorInfo;
import io.harness.ng.core.dto.ProjectDTO;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.project.remote.ProjectClient;
import io.harness.remote.client.NGRestUtils;
import io.harness.rule.Owner;
import io.harness.timescaledb.TimeScaleDBService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mockito.stubbing.Answer;

public class CIDashboardsApisTest extends CategoryTest {
  @Mock TimeScaleDBService timeScaleDBService;
  @Mock ProjectClient projectClient;
  @InjectMocks @Spy private CIOverviewDashboardServiceImpl ciOverviewDashboardServiceImpl;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetCountAndRate() {
    // case 1
    BuildHealth buildHealthExpected1 = BuildHealth.builder().count(100).rate(0.0).build();
    BuildHealth buildHealth1 = ciOverviewDashboardServiceImpl.getCountAndRate(100, 0);
    assertThat(buildHealth1).isEqualTo(buildHealthExpected1);

    // case 2
    BuildHealth buildHealthExpected2 = BuildHealth.builder().count(100).rate(((100 - 50) / (double) 50) * 100).build();
    BuildHealth buildHealth2 = ciOverviewDashboardServiceImpl.getCountAndRate(100, 50);
    assertThat(buildHealth2).isEqualTo(buildHealthExpected2);

    // case 3
    BuildHealth buildHealthExpected3 = BuildHealth.builder().count(100).rate(0.0).build();
    BuildHealth buildHealth3 = ciOverviewDashboardServiceImpl.getCountAndRate(100, 100);
    assertThat(buildHealth3).isEqualTo(buildHealthExpected3);

    // case 4
    BuildHealth buildHealthExpected4 =
        BuildHealth.builder().count(100).rate(((100 - 200) / (double) 200) * 100).build();
    BuildHealth buildHealth4 = ciOverviewDashboardServiceImpl.getCountAndRate(100, 200);
    assertThat(buildHealth4).isEqualTo(buildHealthExpected4);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetDashBoardBuildHealthInfoWithRate() {
    long startInterval = 1619308800000L;
    long endInterval = 1619740800000L;
    long previousInterval = 1587340800000L;

    List<String> status = new ArrayList<>();
    status.add(ExecutionStatus.SUCCESS.name());
    status.add(ExecutionStatus.ABORTED.name());
    status.add(ExecutionStatus.EXPIRED.name());
    status.add(ExecutionStatus.SUCCESS.name());
    status.add(ExecutionStatus.SUCCESS.name());

    status.add(ExecutionStatus.SUCCESS.name());
    status.add(ExecutionStatus.FAILED.name());
    status.add(ExecutionStatus.SUCCESS.name());
    status.add(ExecutionStatus.SUCCESS.name());

    List<Long> time = new ArrayList<>();
    time.add(1619349021000L);
    time.add(1619349021000L);
    time.add(1619521821000L);
    time.add(1619608221000L);
    time.add(1619781021000L);

    time.add(1618917021000L);
    time.add(1619262621000L);
    time.add(1619176221000L);
    time.add(1619003421000L);

    StatusAndTime statusAndTime = StatusAndTime.builder().time(time).status(status).build();

    doReturn(statusAndTime)
        .when(ciOverviewDashboardServiceImpl)
        .queryCalculatorForStatusAndTime(anyString(), anyObject(), anyObject(), anyLong(), anyLong());

    DashboardBuildsHealthInfo resultBuildHealth = ciOverviewDashboardServiceImpl.getDashBoardBuildHealthInfoWithRate(
        "acc", "org", "pro", startInterval, endInterval, previousInterval);
    DashboardBuildsHealthInfo expectedBuildHealth =
        DashboardBuildsHealthInfo.builder()
            .builds(BuildInfo.builder()
                        .total(BuildHealth.builder().count(5).rate(((5 - 4) / (double) 4) * 100).build())
                        .success(BuildHealth.builder().count(3).rate(0.0).build())
                        .failed(BuildHealth.builder().count(2).rate(100).build())
                        .build())
            .build();

    assertThat(resultBuildHealth).isEqualTo(expectedBuildHealth);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetBuildExecutionBetweenIntervals() {
    long startInterval = 1619308800000L;
    long endInterval = 1619740800000L;

    List<String> status = new ArrayList<>();
    status.add(ExecutionStatus.SUCCESS.name());
    status.add(ExecutionStatus.ABORTED.name());
    status.add(ExecutionStatus.EXPIRED.name());
    status.add(ExecutionStatus.SUCCESS.name());
    status.add(ExecutionStatus.SUCCESS.name());
    status.add(ExecutionStatus.SUCCESS.name());
    status.add(ExecutionStatus.FAILED.name());
    status.add(ExecutionStatus.SUCCESS.name());
    status.add(ExecutionStatus.SUCCESS.name());

    List<Long> time = new ArrayList<>();
    time.add(1619349021000L);
    time.add(1619349021000L);
    time.add(1619521821000L);
    time.add(1619608221000L);
    time.add(1619781021000L);
    time.add(1619608221000L);
    time.add(1619435421000L);
    time.add(1619349021000L);
    time.add(1619349021000L);

    StatusAndTime statusAndTime = StatusAndTime.builder().time(time).status(status).build();

    doReturn(statusAndTime)
        .when(ciOverviewDashboardServiceImpl)
        .queryCalculatorForStatusAndTime(anyString(), anyObject(), anyObject(), anyLong(), anyLong());

    Mockito.mockStatic(NGRestUtils.class);
    when(NGRestUtils.getResponse(any()))
        .thenReturn(Collections.singletonList(ProjectDTO.builder().orgIdentifier("org").identifier("pro").build()));

    DashboardBuildExecutionInfo resultBuildExecution = ciOverviewDashboardServiceImpl.getBuildExecutionBetweenIntervals(
        "acc", "org", "pro", null, startInterval, endInterval);

    List<BuildExecutionInfo> buildExecutionInfoList = new ArrayList<>();
    buildExecutionInfoList.add(BuildExecutionInfo.builder()
                                   .time(1619308800000L)
                                   .builds(BuildCount.builder().total(4).success(3).aborted(1).failed(0).build())
                                   .build());
    buildExecutionInfoList.add(BuildExecutionInfo.builder()
                                   .time(1619395200000L)
                                   .builds(BuildCount.builder().total(1).success(0).failed(1).build())
                                   .build());
    buildExecutionInfoList.add(BuildExecutionInfo.builder()
                                   .time(1619481600000L)
                                   .builds(BuildCount.builder().total(1).success(0).failed(0).expired(1).build())
                                   .build());
    buildExecutionInfoList.add(BuildExecutionInfo.builder()
                                   .time(1619568000000L)
                                   .builds(BuildCount.builder().total(2).success(2).failed(0).build())
                                   .build());
    buildExecutionInfoList.add(BuildExecutionInfo.builder()
                                   .time(1619654400000L)
                                   .builds(BuildCount.builder().total(0).success(0).failed(0).build())
                                   .build());
    buildExecutionInfoList.add(BuildExecutionInfo.builder()
                                   .time(1619740800000L)
                                   .builds(BuildCount.builder().total(1).success(1).failed(0).build())
                                   .build());
    DashboardBuildExecutionInfo expectedBuildExecution = DashboardBuildExecutionInfo.builder()
                                                             .buildExecutionInfoList(buildExecutionInfoList)
                                                             .buildRate(1.5)
                                                             .buildRateChangeRate(-100.0)
                                                             .build();

    assertThat(resultBuildExecution).isEqualTo(expectedBuildExecution);

    when(NGRestUtils.getResponse(any()))
        .thenReturn(Arrays.asList(ProjectDTO.builder().orgIdentifier("org1").identifier("pro1").build(),
            ProjectDTO.builder().orgIdentifier("org2").identifier("proj2").build()));

    resultBuildExecution = ciOverviewDashboardServiceImpl.getBuildExecutionBetweenIntervals(
        "acc", null, null, null, startInterval, endInterval);
    assertThat(resultBuildExecution).isEqualTo(expectedBuildExecution);
    assertThatThrownBy(()
                           -> ciOverviewDashboardServiceImpl.getBuildExecutionBetweenIntervals(
                               "acc", "org", "proj", null, startInterval, endInterval))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetDashboardBuildFailureInfo() {
    String queryRequired =
        "select name, pipelineidentifier, moduleinfo_branch_name, moduleinfo_branch_commit_message, moduleinfo_event, moduleinfo_repository, planexecutionid, source_branch, moduleinfo_branch_commit_id, moduleinfo_author_id, author_avatar, startts, trigger_type, endts, status, id  from pipeline_execution_summary_ci where accountid='acc' and orgidentifier='org' and projectidentifier='pro' and status in ('FAILED','ABORTED','EXPIRED','IGNOREFAILED','ERRORED') ORDER BY startts DESC LIMIT 5;";

    List<BuildFailureInfo> buildFailureInfos = new ArrayList<>();
    buildFailureInfos.add(BuildFailureInfo.builder()
                              .piplineName("pip")
                              .pipelineIdentifier("pip")
                              .branch("branch")
                              .commit("commit")
                              .commitID("commitId")
                              .author(AuthorInfo.builder().name(null).url(null).build())
                              .startTs(20L)
                              .endTs(30L)
                              .status("status")
                              .triggerType("Webhook")
                              .planExecutionId("plan")
                              .build());

    doReturn(buildFailureInfos)
        .when(ciOverviewDashboardServiceImpl)
        .queryCalculatorBuildFailureInfo("acc", "org", "pro", 5);

    assertThat(buildFailureInfos)
        .isEqualTo(ciOverviewDashboardServiceImpl.getDashboardBuildFailureInfo("acc", "org", "pro", 5));
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetDashboardBuildActiveInfo() {
    String queryRequired =
        "select name, pipelineidentifier, moduleinfo_branch_name, planexecutionid, moduleinfo_branch_commit_message, moduleinfo_branch_commit_id, source_branch, moduleinfo_author_id, author_avatar, moduleinfo_event, moduleinfo_repository, startts, status, trigger_type, id   from pipeline_execution_summary_ci where accountid='acc' and orgidentifier='org' and projectidentifier='pro' and status IN ('RUNNING','ASYNCWAITING','TASKWAITING','TIMEDWAITING','PAUSED','PAUSING') ORDER BY startts DESC LIMIT 5;";

    List<BuildActiveInfo> buildActiveInfos = new ArrayList<>();
    buildActiveInfos.add(BuildActiveInfo.builder()
                             .piplineName("pip")
                             .pipelineIdentifier("pip")
                             .branch("branch")
                             .commit("commit")
                             .planExecutionId("plan")
                             .triggerType("Webhook")
                             .commitID("commitId")
                             .author(AuthorInfo.builder().name(null).url(null).build())
                             .startTs(20L)
                             .endTs(30L)
                             .status("Running")
                             .planExecutionId("plan")
                             .build());

    doReturn(buildActiveInfos)
        .when(ciOverviewDashboardServiceImpl)
        .queryCalculatorBuildActiveInfo("acc", "org", "pro", 5);

    assertThat(buildActiveInfos)
        .isEqualTo(ciOverviewDashboardServiceImpl.getDashboardBuildActiveInfo("acc", "org", "pro", 5));
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetDashboardBuildRepository() {
    long startInterval = 1619308800000L;
    long endInterval = 1619740800000L;
    long previousInterval = 1587340800000L;

    List<String> repoName = new ArrayList<>();
    List<String> status = new ArrayList<>();
    List<Long> time = new ArrayList<>();
    List<Long> endTime = new ArrayList<>();
    List<String> commitMessage = new ArrayList<>();
    List<AuthorInfo> authorInfoList = new ArrayList<>();

    repoName.add("repo1");
    repoName.add("repo2");
    repoName.add("repo2");
    repoName.add("repo3");
    repoName.add("repo1");

    repoName.add("repo2");
    repoName.add("repo3");
    repoName.add("repo3");
    repoName.add("repo4");

    status.add(ExecutionStatus.SUCCESS.name());
    status.add(ExecutionStatus.ABORTED.name());
    status.add(ExecutionStatus.EXPIRED.name());
    status.add(ExecutionStatus.SUCCESS.name());
    status.add(ExecutionStatus.SUCCESS.name());

    status.add(ExecutionStatus.SUCCESS.name());
    status.add(ExecutionStatus.FAILED.name());
    status.add(ExecutionStatus.SUCCESS.name());
    status.add(ExecutionStatus.SUCCESS.name());

    time.add(1619349021000L);
    time.add(1619349021000L);
    time.add(1619349621000L);
    time.add(1619608221000L);
    time.add(1619781021000L);

    time.add(1618917021000L);
    time.add(1619262621000L);
    time.add(1619176221000L);
    time.add(1619003421000L);

    endTime.add(-1L);
    endTime.add(-1L);
    endTime.add(-1L);
    endTime.add(-1L);
    endTime.add(-1L);

    endTime.add(-1L);
    endTime.add(-1L);
    endTime.add(-1L);
    endTime.add(-1L);

    commitMessage.add("commit101");
    commitMessage.add("commit102");
    commitMessage.add("commit103");
    commitMessage.add("commit104");
    commitMessage.add("commit105");

    commitMessage.add("commit106");
    commitMessage.add("commit107");
    commitMessage.add("commit108");
    commitMessage.add("commit109");

    authorInfoList.add(AuthorInfo.builder().name("name1").url("url1").build());
    authorInfoList.add(AuthorInfo.builder().name("name2").url("url2").build());
    authorInfoList.add(AuthorInfo.builder().name("name3").url("url3").build());
    authorInfoList.add(AuthorInfo.builder().name("name4").url("url4").build());
    authorInfoList.add(AuthorInfo.builder().name("name5").url("url5").build());

    authorInfoList.add(AuthorInfo.builder().name("name6").url("url6").build());
    authorInfoList.add(AuthorInfo.builder().name("name7").url("url7").build());
    authorInfoList.add(AuthorInfo.builder().name("name8").url("url8").build());
    authorInfoList.add(AuthorInfo.builder().name("name9").url("url9").build());

    RepositoryInformation repositoryInformation = RepositoryInformation.builder()
                                                      .repoName(repoName)
                                                      .status(status)
                                                      .commitMessage(commitMessage)
                                                      .startTime(time)
                                                      .endTime(endTime)
                                                      .authorInfoList(authorInfoList)
                                                      .build();
    doReturn(repositoryInformation)
        .when(ciOverviewDashboardServiceImpl)
        .queryRepositoryCalculator(any(), any(), any(), any(), any());

    DashboardBuildRepositoryInfo resultRepoInfo = ciOverviewDashboardServiceImpl.getDashboardBuildRepository(
        "acc", "org", "pro", startInterval, endInterval, previousInterval);

    List<RepositoryBuildInfo> repo1 = new ArrayList<>();
    repo1.add(RepositoryBuildInfo.builder()
                  .time(1619308800000L)
                  .builds(BuildRepositoryCount.builder().count(1).build())
                  .build());
    repo1.add(RepositoryBuildInfo.builder()
                  .time(1619395200000L)
                  .builds(BuildRepositoryCount.builder().count(0).build())
                  .build());
    repo1.add(RepositoryBuildInfo.builder()
                  .time(1619481600000L)
                  .builds(BuildRepositoryCount.builder().count(0).build())
                  .build());
    repo1.add(RepositoryBuildInfo.builder()
                  .time(1619568000000L)
                  .builds(BuildRepositoryCount.builder().count(0).build())
                  .build());
    repo1.add(RepositoryBuildInfo.builder()
                  .time(1619654400000L)
                  .builds(BuildRepositoryCount.builder().count(0).build())
                  .build());
    repo1.add(RepositoryBuildInfo.builder()
                  .time(1619740800000L)
                  .builds(BuildRepositoryCount.builder().count(1).build())
                  .build());

    // repo 2
    List<RepositoryBuildInfo> repo2 = new ArrayList<>();
    repo2.add(RepositoryBuildInfo.builder()
                  .time(1619308800000L)
                  .builds(BuildRepositoryCount.builder().count(2).build())
                  .build());
    repo2.add(RepositoryBuildInfo.builder()
                  .time(1619395200000L)
                  .builds(BuildRepositoryCount.builder().count(0).build())
                  .build());
    repo2.add(RepositoryBuildInfo.builder()
                  .time(1619481600000L)
                  .builds(BuildRepositoryCount.builder().count(0).build())
                  .build());
    repo2.add(RepositoryBuildInfo.builder()
                  .time(1619568000000L)
                  .builds(BuildRepositoryCount.builder().count(0).build())
                  .build());
    repo2.add(RepositoryBuildInfo.builder()
                  .time(1619654400000L)
                  .builds(BuildRepositoryCount.builder().count(0).build())
                  .build());
    repo2.add(RepositoryBuildInfo.builder()
                  .time(1619740800000L)
                  .builds(BuildRepositoryCount.builder().count(0).build())
                  .build());

    List<RepositoryBuildInfo> repo3 = new ArrayList<>();
    repo3.add(RepositoryBuildInfo.builder()
                  .time(1619308800000L)
                  .builds(BuildRepositoryCount.builder().count(0).build())
                  .build());
    repo3.add(RepositoryBuildInfo.builder()
                  .time(1619395200000L)
                  .builds(BuildRepositoryCount.builder().count(0).build())
                  .build());
    repo3.add(RepositoryBuildInfo.builder()
                  .time(1619481600000L)
                  .builds(BuildRepositoryCount.builder().count(0).build())
                  .build());
    repo3.add(RepositoryBuildInfo.builder()
                  .time(1619568000000L)
                  .builds(BuildRepositoryCount.builder().count(1).build())
                  .build());
    repo3.add(RepositoryBuildInfo.builder()
                  .time(1619654400000L)
                  .builds(BuildRepositoryCount.builder().count(0).build())
                  .build());
    repo3.add(RepositoryBuildInfo.builder()
                  .time(1619740800000L)
                  .builds(BuildRepositoryCount.builder().count(0).build())
                  .build());

    List<RepositoryInfo> repositoryInfoList = new ArrayList<>();

    repositoryInfoList.add(RepositoryInfo.builder()
                               .name("repo2")
                               .lastRepository(LastRepositoryInfo.builder()
                                                   .StartTime(1619349621000L)
                                                   .status(ExecutionStatus.EXPIRED.name())
                                                   .EndTime(null)
                                                   .commit("commit103")
                                                   .author(AuthorInfo.builder().name("name3").url("url3").build())
                                                   .build())
                               .buildCount(2)
                               .countList(repo2)
                               .percentSuccess(0)
                               .successRate(-100.0)
                               .build());

    repositoryInfoList.add(RepositoryInfo.builder()
                               .name("repo3")
                               .lastRepository(LastRepositoryInfo.builder()
                                                   .StartTime(1619608221000L)
                                                   .status(ExecutionStatus.SUCCESS.name())
                                                   .EndTime(null)
                                                   .commit("commit104")
                                                   .author(AuthorInfo.builder().name("name4").url("url4").build())
                                                   .build())
                               .buildCount(1)
                               .countList(repo3)
                               .percentSuccess(100.0)
                               .successRate(0.0)
                               .build());

    repositoryInfoList.add(RepositoryInfo.builder()
                               .name("repo1")
                               .lastRepository(LastRepositoryInfo.builder()
                                                   .StartTime(1619781021000L)
                                                   .status(ExecutionStatus.SUCCESS.name())
                                                   .EndTime(null)
                                                   .commit("commit105")
                                                   .author(AuthorInfo.builder().name("name5").url("url5").build())
                                                   .build())

                               .buildCount(2)
                               .countList(repo1)
                               .percentSuccess(100.0)
                               .successRate(0.0)
                               .build());

    DashboardBuildRepositoryInfo expectedRepoInfo =
        DashboardBuildRepositoryInfo.builder().repositoryInfo(repositoryInfoList).build();

    assertThat(resultRepoInfo).isEqualTo(expectedRepoInfo);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetActiveAndFailedBuild() {
    BuildFailureInfo failureBuild = BuildFailureInfo.builder()
                                        .piplineName("pip1")
                                        .pipelineIdentifier("pip1")
                                        .commit("commit1")
                                        .endTs(13L)
                                        .startTs(10L)
                                        .commitID("id1")
                                        .branch("branch1")
                                        .status("status")
                                        .triggerType("Webhook")
                                        .planExecutionId("plan")
                                        .build();

    BuildActiveInfo activeInfo = BuildActiveInfo.builder()
                                     .piplineName("pip2")
                                     .pipelineIdentifier("pip2")
                                     .commit("commit2")
                                     .branch("branch2")
                                     .commitID("id2")
                                     .endTs(13L)
                                     .startTs(10L)
                                     .triggerType("Webhook")
                                     .planExecutionId("plan")
                                     .status(ExecutionStatus.RUNNING.name())
                                     .build();

    assertThat(failureBuild)
        .isEqualTo(ciOverviewDashboardServiceImpl.getBuildFailureInfo(
            "pip1", "pip1", "branch1", "commit1", "id1", 10, 13, null, "status", "plan", "Webhook", null));
    assertThat(activeInfo)
        .isEqualTo(ciOverviewDashboardServiceImpl.getBuildActiveInfo("pip2", "pip2", "branch2", "commit2", "id2", null,
            10, ExecutionStatus.RUNNING.name(), "plan", 13, "Webhook", null));
  }

  @Test
  @Owner(developers = JAMIE)
  @Category(UnitTests.class)
  public void testGetCIUsage() {
    String accountId = "accountIdentifier";
    long timestamp = 1635814085000L;
    List<ReferenceDTO> referenceDTO = new ArrayList<>();
    referenceDTO.add(ReferenceDTO.builder()
                         .identifier("identifier1")
                         .projectIdentifier("projectIdentifier1")
                         .orgIdentifier("orgIdentifier1")
                         .build());
    referenceDTO.add(ReferenceDTO.builder()
                         .identifier("identifier2")
                         .projectIdentifier("projectIdentifier2")
                         .orgIdentifier("orgIdentifier2")
                         .build());
    UsageDataDTO activeCommitters =
        UsageDataDTO.builder().count(2).displayName("Last 30 Days").references(referenceDTO).build();
    doReturn(activeCommitters).when(ciOverviewDashboardServiceImpl).getActiveCommitter(accountId, timestamp);
    CIUsageResult usageResult = CIUsageResult.builder()
                                    .accountIdentifier(accountId)
                                    .timestamp(timestamp)
                                    .module("CI")
                                    .activeCommitters(activeCommitters)
                                    .build();
    assertThat(usageResult).isEqualTo(ciOverviewDashboardServiceImpl.getCIUsageResult(accountId, timestamp));
  }

  @Test
  @Owner(developers = JAMIE)
  @Category(UnitTests.class)
  public void testGetActiveCommitters() throws SQLException {
    ResultSet resultSet = mock(ResultSet.class);
    Connection connection = mock(Connection.class);
    PreparedStatement statement = mock(PreparedStatement.class);
    when(statement.executeQuery()).thenReturn(resultSet);
    when(connection.prepareStatement(any())).thenReturn(statement);
    when(timeScaleDBService.getDBConnection()).thenReturn(connection);
    final int[] count = {0};
    when(resultSet.next()).then((Answer<Boolean>) invocation -> {
      if (count[0] <= 1) {
        count[0]++;
        return true;
      }
      return false;
    });
    when(resultSet.getString("moduleinfo_author_id")).then((Answer<String>) invocation -> "authoerId" + count[0]);
    when(resultSet.getString("projectidentifier")).then((Answer<String>) invocation -> "projectId" + count[0]);
    when(resultSet.getString("orgidentifier")).then((Answer<String>) invocation -> "orgId" + count[0]);

    List<ReferenceDTO> usageReferences = new ArrayList<>();
    ReferenceDTO reference1 =
        ReferenceDTO.builder().identifier("authoerId1").projectIdentifier("projectId1").orgIdentifier("orgId1").build();
    usageReferences.add(reference1);
    ReferenceDTO reference2 =
        ReferenceDTO.builder().identifier("authoerId2").projectIdentifier("projectId2").orgIdentifier("orgId2").build();
    usageReferences.add(reference2);

    UsageDataDTO usage =
        UsageDataDTO.builder().count(2).displayName("Last 30 Days").references(usageReferences).build();
    assertThat(usage).isEqualTo(ciOverviewDashboardServiceImpl.getActiveCommitter("accountId", 0L));
  }

  @Test
  @Owner(developers = JAMIE)
  @Category(UnitTests.class)
  public void testGetActiveCommittersUniqueId() throws SQLException {
    ResultSet resultSet = mock(ResultSet.class);
    Connection connection = mock(Connection.class);
    PreparedStatement statement = mock(PreparedStatement.class);
    when(statement.executeQuery()).thenReturn(resultSet);
    when(connection.prepareStatement(any())).thenReturn(statement);
    when(timeScaleDBService.getDBConnection()).thenReturn(connection);
    final int[] count = {0};
    when(resultSet.next()).then((Answer<Boolean>) invocation -> {
      if (count[0] <= 1) {
        count[0]++;
        return true;
      }
      return false;
    });
    // ID does not change here
    when(resultSet.getString("moduleinfo_author_id")).then((Answer<String>) invocation -> "authoerId");
    when(resultSet.getString("projectidentifier")).then((Answer<String>) invocation -> "projectId" + count[0]);
    when(resultSet.getString("orgidentifier")).then((Answer<String>) invocation -> "orgId" + count[0]);

    List<ReferenceDTO> usageReferences = new ArrayList<>();
    ReferenceDTO reference1 =
        ReferenceDTO.builder().identifier("authoerId").projectIdentifier("projectId1").orgIdentifier("orgId1").build();
    usageReferences.add(reference1);
    ReferenceDTO reference2 =
        ReferenceDTO.builder().identifier("authoerId").projectIdentifier("projectId2").orgIdentifier("orgId2").build();
    usageReferences.add(reference2);

    UsageDataDTO usage =
        UsageDataDTO.builder().count(1).displayName("Last 30 Days").references(usageReferences).build();
    assertThat(usage).isEqualTo(ciOverviewDashboardServiceImpl.getActiveCommitter("accountId", 0L));
  }

  @Test
  @Owner(developers = JAMIE)
  @Category(UnitTests.class)
  public void testGetActiveCommittersCount() throws SQLException {
    final long expectedResult = 100L;
    ResultSet resultSet = mock(ResultSet.class);
    Connection connection = mock(Connection.class);
    PreparedStatement statement = mock(PreparedStatement.class);
    when(statement.executeQuery()).thenReturn(resultSet);
    when(connection.prepareStatement(any())).thenReturn(statement);
    when(timeScaleDBService.getDBConnection()).thenReturn(connection);
    final int[] count = {0};
    when(resultSet.next()).then((Answer<Boolean>) invocation -> {
      if (count[0] <= 1) {
        count[0]++;
        return true;
      }
      return false;
    });
    when(resultSet.getLong(1)).then((Answer<Long>) invocation -> expectedResult);

    assertThat(expectedResult).isEqualTo(ciOverviewDashboardServiceImpl.getActiveCommitterCount("accountId"));
  }
}
