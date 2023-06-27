/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.gitops.steps;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.agent.sdk.HarnessAlwaysRun;
import io.harness.category.element.UnitTests;
import io.harness.cdng.envGroup.beans.EnvironmentGroupEntity;
import io.harness.cdng.envGroup.services.EnvironmentGroupService;
import io.harness.cdng.gitops.service.ClusterService;
import io.harness.exception.InvalidRequestException;
import io.harness.gitops.models.Cluster;
import io.harness.gitops.models.ClusterQuery;
import io.harness.gitops.remote.GitopsResourceClient;
import io.harness.logstreaming.ILogStreamingStepClient;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.expression.EngineExpressionService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;
import retrofit2.Response;

@RunWith(JUnitParamsRunner.class)
public class GitopsClustersStepTest extends CategoryTest {
  public static final int EXPECTED_PAGE_SIZE = 100000;
  @Mock private ExecutionSweepingOutputService sweepingOutputService;
  @Mock private EnvironmentGroupService environmentGroupService;
  @Mock private GitopsResourceClient gitopsResourceClient;
  @Mock private ClusterService clusterService;
  @Mock private ILogStreamingStepClient logStreamingStepClient;
  @Mock private LogStreamingStepClientFactory logStreamingClientFactory;
  @Mock private EngineExpressionService engineExpressionService;

  @InjectMocks private GitopsClustersStep step;

  /*
  envgroup -> envGroupId
  environments -> env1, env2, env3
  env1 -> clusters: c1, c2, account.x1, org.x2
  env2 -> clusters: c3, c4, c5 (c5 does not exist on gitops)
  env3 -> no gitops clusters
   */
  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    doReturn(Optional.of(EnvironmentGroupEntity.builder().envIdentifiers(asList("env1Id", "env2Id", "env3")).build()))
        .when(environmentGroupService)
        .get("accountId", "orgId", "projId", "envGroupId", false);

    doReturn(logStreamingStepClient).when(logStreamingClientFactory).getLogStreamingStepClient(any());

    mockGitopsResourceClient();
    mockClusterService();
  }

  private void mockClusterService() {
    doReturn(asList(io.harness.cdng.gitops.entity.Cluster.builder().clusterRef("c1").envRef("env1Id").build(),
                 io.harness.cdng.gitops.entity.Cluster.builder().clusterRef("c2").envRef("env1Id").build(),
                 io.harness.cdng.gitops.entity.Cluster.builder().clusterRef("account.x1").envRef("env1Id").build(),
                 io.harness.cdng.gitops.entity.Cluster.builder().clusterRef("org.x2").envRef("env1Id").build()))
        .when(clusterService)
        .listAcrossEnv(0, EXPECTED_PAGE_SIZE, "accountId", "orgId", "projId", ImmutableSet.of("env1Id"));

    doReturn(asList(io.harness.cdng.gitops.entity.Cluster.builder().clusterRef("c1").envRef("env1Id").build(),
                 io.harness.cdng.gitops.entity.Cluster.builder().clusterRef("c2").envRef("env1Id").build(),
                 io.harness.cdng.gitops.entity.Cluster.builder().clusterRef("c3").envRef("env2Id").build(),
                 io.harness.cdng.gitops.entity.Cluster.builder().clusterRef("c4").envRef("env2Id").build(),
                 io.harness.cdng.gitops.entity.Cluster.builder().clusterRef("c5").envRef("env2Id").build(),
                 io.harness.cdng.gitops.entity.Cluster.builder().clusterRef("account.x1").envRef("env1Id").build(),
                 io.harness.cdng.gitops.entity.Cluster.builder().clusterRef("org.x2").envRef("env1Id").build()))
        .when(clusterService)
        .listAcrossEnv(
            0, EXPECTED_PAGE_SIZE, "accountId", "orgId", "projId", ImmutableSet.of("env1Id", "env2Id", "env3"));

    doReturn(asList(io.harness.cdng.gitops.entity.Cluster.builder().clusterRef("c3").envRef("env2Id").build(),
                 io.harness.cdng.gitops.entity.Cluster.builder().clusterRef("c4").envRef("env2Id").build(),
                 io.harness.cdng.gitops.entity.Cluster.builder().clusterRef("c5").envRef("env2Id").build()))
        .when(clusterService)
        .listAcrossEnv(0, EXPECTED_PAGE_SIZE, "accountId", "orgId", "projId", ImmutableSet.of("env2Id"));
  }

  private void mockGitopsResourceClient() throws IOException {
    // mock cluster list call from gitops service
    Call<ResponseDTO<List<Cluster>>> rmock1 = mock(Call.class);
    doReturn(rmock1)
        .when(gitopsResourceClient)
        .listClusters(ClusterQuery.builder()
                          .accountId("accountId")
                          .orgIdentifier("orgId")
                          .projectIdentifier("projId")
                          .pageIndex(0)
                          .pageSize(2)
                          .filter(ImmutableMap.of("identifier", ImmutableMap.of("$in", ImmutableSet.of("c1", "c2"))))
                          .build());
    doReturn(
        Response.success(
            PageResponse.builder().content(asList(new Cluster("c1", "c1-name"), new Cluster("c2", "c2-name"))).build()))
        .when(rmock1)
        .execute();

    // mock cluster list call from gitops service
    Call<ResponseDTO<List<Cluster>>> rmock2 = mock(Call.class);
    doReturn(rmock2)
        .when(gitopsResourceClient)
        .listClusters(
            ClusterQuery.builder()
                .accountId("accountId")
                .orgIdentifier("orgId")
                .projectIdentifier("projId")
                .pageIndex(0)
                .pageSize(3)
                .filter(ImmutableMap.of("identifier", ImmutableMap.of("$in", ImmutableSet.of("c3", "c4", "c5"))))
                .build());
    doReturn(
        Response.success(
            PageResponse.builder().content(asList(new Cluster("c3", "c3-name"), new Cluster("c4", "c4-name"))).build()))
        .when(rmock2)
        .execute();

    Call<ResponseDTO<List<Cluster>>> rmock3 = mock(Call.class);
    doReturn(rmock3)
        .when(gitopsResourceClient)
        .listClusters(ClusterQuery.builder()
                          .accountId("accountId")
                          .orgIdentifier("orgId")
                          .projectIdentifier("projId")
                          .pageIndex(0)
                          .pageSize(5)
                          .filter(ImmutableMap.of(
                              "identifier", ImmutableMap.of("$in", ImmutableSet.of("c3", "c4", "c5", "c1", "c2"))))
                          .build());
    doReturn(Response.success(PageResponse.builder()
                                  .content(asList(new Cluster("c1", "c1-name"), new Cluster("c2", "c2-name"),
                                      new Cluster("c3", "c3-name"), new Cluster("c4", "c4-name")))
                                  .build()))
        .when(rmock3)
        .execute();

    Call<ResponseDTO<List<Cluster>>> rmock4 = mock(Call.class);
    doReturn(rmock4)
        .when(gitopsResourceClient)
        .listClusters(ClusterQuery.builder()
                          .accountId("accountId")
                          .orgIdentifier("orgId")
                          .projectIdentifier("projId")
                          .pageIndex(0)
                          .pageSize(1)
                          .filter(ImmutableMap.of("identifier", ImmutableMap.of("$in", ImmutableSet.of("c4"))))
                          .build());
    doReturn(Response.success(PageResponse.builder().content(asList(new Cluster("c4", "c4-name"))).build()))
        .when(rmock4)
        .execute();

    Call<ResponseDTO<List<Cluster>>> rmock5 = mock(Call.class);
    doReturn(rmock5)
        .when(gitopsResourceClient)
        .listClusters(ClusterQuery.builder()
                          .accountId("accountId")
                          .orgIdentifier("")
                          .projectIdentifier("")
                          .pageIndex(0)
                          .pageSize(1)
                          .filter(ImmutableMap.of("identifier", ImmutableMap.of("$in", ImmutableSet.of("x1"))))
                          .build());
    doReturn(Response.success(PageResponse.builder().content(asList(new Cluster("x1", "x1-name"))).build()))
        .when(rmock5)
        .execute();

    Call<ResponseDTO<List<Cluster>>> rmock6 = mock(Call.class);
    doReturn(rmock6)
        .when(gitopsResourceClient)
        .listClusters(ClusterQuery.builder()
                          .accountId("accountId")
                          .orgIdentifier("orgId")
                          .projectIdentifier("")
                          .pageIndex(0)
                          .pageSize(1)
                          .filter(ImmutableMap.of("identifier", ImmutableMap.of("$in", ImmutableSet.of("x2"))))
                          .build());
    doReturn(Response.success(PageResponse.builder().content(asList(new Cluster("x2", "x2-name"))).build()))
        .when(rmock6)
        .execute();
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  @Parameters(method = "getData")
  public void testExecuteSyncAfterRbac(ClusterStepParameters input, GitopsClustersOutcome expectedOutcome) {
    step.executeSyncAfterRbac(buildAmbiance(), input, StepInputPackage.builder().build(), null);

    verify(sweepingOutputService, atLeast(2)).resolveOptional(any(), any());
    verify(sweepingOutputService).consume(any(), eq("gitops"), eq(expectedOutcome), eq("STAGE"));
    reset(sweepingOutputService);
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  @Parameters(method = "getDataForExceptions")
  public void testExecuteSyncAfterRbacShouldThrow(ClusterStepParameters input) {
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> step.executeSyncAfterRbac(buildAmbiance(), input, StepInputPackage.builder().build(), null));
  }

  // Test cases
  private Object[][] getData() {
    final Object[] set1 =
        new Object[] {ClusterStepParameters.builder()
                          .envGroupRef("envGroupId")
                          .deployToAllEnvs(true)
                          .envClusterRefs(asList(EnvClusterRefs.builder()
                                                     .envRef("env1Id")
                                                     .deployToAll(false)
                                                     .clusterRefs(Set.of("c1", "c2"))
                                                     .envType(EnvironmentType.PreProduction.toString())
                                                     .build()))
                          .build(),
            new GitopsClustersOutcome(new ArrayList<>())
                .appendCluster(new Metadata("envGroupId", null), new Metadata("env1Id", null),
                    EnvironmentType.PreProduction.toString(), new Metadata("c1", "c1-name"), Map.of(), null)
                .appendCluster(new Metadata("envGroupId", null), new Metadata("env1Id", null),
                    EnvironmentType.PreProduction.toString(), new Metadata("c2", "c2-name"), Map.of(), null)};

    final Object[] set2 = new Object[] {ClusterStepParameters.builder()
                                            .envClusterRefs(asList(EnvClusterRefs.builder()
                                                                       .envRef("env1Id")
                                                                       .deployToAll(true)
                                                                       .envType(EnvironmentType.Production.toString())
                                                                       .build()))
                                            .build(),
        new GitopsClustersOutcome(new ArrayList<>())
            .appendCluster(new Metadata("env1Id", null), new Metadata("account.x1", "x1-name"),
                EnvironmentType.Production.toString())
            .appendCluster(
                new Metadata("env1Id", null), new Metadata("org.x2", "x2-name"), EnvironmentType.Production.toString())
            .appendCluster(
                new Metadata("env1Id", null), new Metadata("c1", "c1-name"), EnvironmentType.Production.toString())
            .appendCluster(
                new Metadata("env1Id", null), new Metadata("c2", "c2-name"), EnvironmentType.Production.toString())};

    final Object[] set3 = new Object[] {
        ClusterStepParameters.builder()
            .envClusterRefs(asList(EnvClusterRefs.builder()
                                       .envRef("env2Id")
                                       .deployToAll(true)
                                       .envType(EnvironmentType.Production.toString())
                                       .build()))
            .build(),
        new GitopsClustersOutcome(new ArrayList<>())
            .appendCluster(
                new Metadata("env2Id", null), new Metadata("c3", "c3-name"), EnvironmentType.Production.toString())
            .appendCluster(
                new Metadata("env2Id", null), new Metadata("c4", "c4-name"), EnvironmentType.Production.toString()),
    };

    final Object[] set4 = new Object[] {
        ClusterStepParameters.builder()
            .envClusterRefs(asList(EnvClusterRefs.builder()
                                       .envRef("env2Id")
                                       .deployToAll(false)
                                       .clusterRefs(Set.of("c4"))
                                       .envType(EnvironmentType.Production.toString())
                                       .build()))
            .deployToAllEnvs(false)
            .build(),
        new GitopsClustersOutcome(new ArrayList<>())
            .appendCluster(
                new Metadata("env2Id", null), new Metadata("c4", "c4-name"), EnvironmentType.Production.toString()),
    };

    return new Object[][] {set1, set2, set3, set4};
  }

  // Test cases
  private Object[][] getDataForExceptions() {
    final Object[] set1 = new Object[] {ClusterStepParameters.builder().build()};
    return new Object[][] {set1};
  }

  private Ambiance buildAmbiance() {
    return Ambiance.newBuilder()
        .setPlanExecutionId("PLAN_EXECUTION_ID")
        .setPlanId("PLAN_ID")
        .putAllSetupAbstractions(ImmutableMap.of(
            "accountId", "accountId", "orgIdentifier", "orgId", "projectIdentifier", "projId", "appId", "APP_ID"))
        .build();
  }

  @Test
  @Owner(developers = OwnerRule.ROHITKARELIA)
  @Category(UnitTests.class)
  @HarnessAlwaysRun
  public void testToGitOpsOutcomeForServiceOverrides() {
    Map<String, List<GitopsClustersStep.IndividualClusterInternal>> validatedClusters = new HashMap<>();
    GitopsClustersStep.IndividualClusterInternal c1IndividualCluster =
        GitopsClustersStep.IndividualClusterInternal.builder()
            .clusterName("c1-Ref")
            .clusterRef("c1Ref")
            .envRef("env1")
            .envVariables(Map.of("k1", "v1"))
            .build();
    validatedClusters.put("c1", Arrays.asList(c1IndividualCluster));
    Map<String, Object> svcVariables = Map.of("k1", "sv1");
    Map<String, Map<String, Object>> envSvcOverrideVars = Map.of("env1", Map.of("k1", "svcEnvOveride1"));

    GitopsClustersOutcome outcome = step.toOutcome(validatedClusters, svcVariables, envSvcOverrideVars);
    assertThat(outcome).isNotNull();
    String k1 = outcome.getClustersData().get(0).variables.get("k1").toString();
    assertThat(k1).isEqualTo("svcEnvOveride1");
  }

  @Test
  @Owner(developers = OwnerRule.ROHITKARELIA)
  @Category(UnitTests.class)
  @HarnessAlwaysRun
  public void testToGitOpsOutcomeForEnvrionmentOveride() {
    Map<String, List<GitopsClustersStep.IndividualClusterInternal>> validatedClusters = new HashMap<>();
    GitopsClustersStep.IndividualClusterInternal c1IndividualCluster =
        GitopsClustersStep.IndividualClusterInternal.builder()
            .clusterName("c1-Ref")
            .clusterRef("c1Ref")
            .envRef("env1")
            .envVariables(Map.of("k1", "v1"))
            .build();
    validatedClusters.put("c1", Arrays.asList(c1IndividualCluster));
    Map<String, Object> svcVariables = Map.of("k1", "sv1");

    GitopsClustersOutcome outcome = step.toOutcome(validatedClusters, svcVariables, Map.of());
    assertThat(outcome).isNotNull();
    String k1 = outcome.getClustersData().get(0).variables.get("k1").toString();
    assertThat(k1).isEqualTo("v1");
  }

  @Test
  @Owner(developers = OwnerRule.MEENA)
  @Category(UnitTests.class)
  @HarnessAlwaysRun
  public void testMultipleEnvsToOneCluster() {
    Map<String, List<GitopsClustersStep.IndividualClusterInternal>> validatedClusters = new HashMap<>();
    GitopsClustersStep.IndividualClusterInternal env1 = GitopsClustersStep.IndividualClusterInternal.builder()
                                                            .clusterName("c1-Ref")
                                                            .clusterRef("c1Ref")
                                                            .envRef("env1")
                                                            .build();
    GitopsClustersStep.IndividualClusterInternal env2 = GitopsClustersStep.IndividualClusterInternal.builder()
                                                            .clusterName("c1-Ref")
                                                            .clusterRef("c1Ref")
                                                            .envRef("env2")
                                                            .build();
    GitopsClustersStep.IndividualClusterInternal env3 = GitopsClustersStep.IndividualClusterInternal.builder()
                                                            .clusterName("c1-Ref")
                                                            .clusterRef("c1Ref")
                                                            .envRef("env3")
                                                            .build();
    validatedClusters.put("c1", Arrays.asList(env1, env2, env3));

    GitopsClustersOutcome outcome = step.toOutcome(validatedClusters, new HashMap<>(), new HashMap<>());
    assertThat(outcome).isNotNull();
    assertThat(
        outcome.getClustersData().stream().map(GitopsClustersOutcome.ClusterData::getEnvId).collect(Collectors.toSet()))
        .containsExactlyInAnyOrder("env1", "env2", "env3");
  }
}
