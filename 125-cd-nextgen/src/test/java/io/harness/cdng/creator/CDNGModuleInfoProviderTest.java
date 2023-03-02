/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator;

import static io.harness.data.structure.UUIDGenerator.generateUuid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.cdng.artifact.GcrArtifactSummary;
import io.harness.cdng.artifact.outcome.ArtifactsOutcome;
import io.harness.cdng.artifact.outcome.DockerArtifactOutcome;
import io.harness.cdng.artifact.outcome.GcrArtifactOutcome;
import io.harness.cdng.freeze.FreezeOutcome;
import io.harness.cdng.gitops.steps.GitopsClustersOutcome;
import io.harness.cdng.gitops.steps.Metadata;
import io.harness.cdng.infra.beans.K8sDirectInfrastructureOutcome;
import io.harness.cdng.pipeline.executions.beans.CDPipelineModuleInfo;
import io.harness.cdng.pipeline.executions.beans.CDStageModuleInfo;
import io.harness.cdng.pipeline.executions.beans.GitOpsExecutionSummary;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.cdng.service.steps.ServiceStepOutcome;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.freeze.service.FreezeEvaluateService;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.refobjects.RefObject;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.data.OptionalOutcome;
import io.harness.pms.sdk.core.events.OrchestrationEvent;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.steps.environment.EnvironmentOutcome;
import io.harness.utils.NGFeatureFlagHelperService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class CDNGModuleInfoProviderTest extends CategoryTest {
  private final String ACCOUNT_ID = "accountId";
  private final String APP_ID = "appId";
  @Mock OutcomeService outcomeService;
  @Mock private NGFeatureFlagHelperService ngFeatureFlagHelperService;
  @Mock private FreezeEvaluateService freezeEvaluateService;
  @InjectMocks CDNGModuleInfoProvider provider;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.openMocks(this);
    doReturn(false).when(ngFeatureFlagHelperService).isEnabled(anyString(), any());
    doReturn(OptionalOutcome.builder().found(true).build())
        .when(outcomeService)
        .resolveOptional(any(Ambiance.class), any(RefObject.class));
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testGetPipelineLevelModuleInfo_Service() {
    Ambiance ambiance = buildAmbiance(StepType.newBuilder()
                                          .setType(ExecutionNodeType.SERVICE_SECTION.getName())
                                          .setStepCategory(StepCategory.STEP)
                                          .build());

    doReturn(OptionalOutcome.builder()
                 .found(true)
                 .outcome(ServiceStepOutcome.builder()
                              .identifier("s1")
                              .type(ServiceDefinitionType.KUBERNETES.getYamlName())
                              .build())
                 .build())
        .when(outcomeService)
        .resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject("service"));
    doReturn(OptionalOutcome.builder()
                 .found(true)
                 .outcome(ArtifactsOutcome.builder()
                              .primary(DockerArtifactOutcome.builder().imagePath("imagePath").tag("tag").build())
                              .build())
                 .build())
        .when(outcomeService)
        .resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject("artifacts"));

    OrchestrationEvent event = OrchestrationEvent.builder().ambiance(ambiance).status(Status.SUCCEEDED).build();
    CDPipelineModuleInfo pipelineLevelModuleInfo = (CDPipelineModuleInfo) provider.getPipelineLevelModuleInfo(event);

    assertThat(pipelineLevelModuleInfo.getServiceIdentifiers()).containsExactlyInAnyOrder("s1");
    assertThat(pipelineLevelModuleInfo.getArtifactDisplayNames()).containsExactlyInAnyOrder("imagePath:tag");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testGetPipelineLevelModuleInfo_Env() {
    Ambiance ambiance = buildAmbiance(StepType.newBuilder()
                                          .setType(ExecutionNodeType.INFRASTRUCTURE.getName())
                                          .setStepCategory(StepCategory.STEP)
                                          .build());

    doReturn(
        OptionalOutcome.builder()
            .found(true)
            .outcome(K8sDirectInfrastructureOutcome.builder()
                         .environment(
                             EnvironmentOutcome.builder().identifier("env1").type(EnvironmentType.Production).build())
                         .build())
            .build())
        .when(outcomeService)
        .resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject("stage.spec.infrastructure.output"));

    OrchestrationEvent event = OrchestrationEvent.builder().ambiance(ambiance).status(Status.SUCCEEDED).build();
    CDPipelineModuleInfo pipelineLevelModuleInfo = (CDPipelineModuleInfo) provider.getPipelineLevelModuleInfo(event);

    assertThat(pipelineLevelModuleInfo.getEnvIdentifiers()).containsExactlyInAnyOrder("env1");
    assertThat(pipelineLevelModuleInfo.getEnvironmentTypes()).containsExactlyInAnyOrder(EnvironmentType.Production);
    assertThat(pipelineLevelModuleInfo.getInfrastructureTypes()).containsExactlyInAnyOrder("KubernetesDirect");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testGetPipelineLevelModuleInfo_Gitops_0() {
    Ambiance ambiance = buildAmbiance(StepType.newBuilder()
                                          .setType(ExecutionNodeType.GITOPS_CLUSTERS.getName())
                                          .setStepCategory(StepCategory.STEP)
                                          .build());

    doReturn(OptionalOutcome.builder().found(false).build())
        .when(outcomeService)
        .resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject("gitops"));

    OrchestrationEvent event = OrchestrationEvent.builder().ambiance(ambiance).status(Status.SUCCEEDED).build();
    assertThat(provider.getPipelineLevelModuleInfo(event)).isNotNull();

    doReturn(null).when(outcomeService).resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject("gitops"));
    assertThat(provider.getPipelineLevelModuleInfo(event)).isNotNull();
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testGetPipelineLevelModuleInfo_Gitops_1() {
    Ambiance ambiance = buildAmbiance(StepType.newBuilder()
                                          .setType(ExecutionNodeType.GITOPS_CLUSTERS.getName())
                                          .setStepCategory(StepCategory.STEP)
                                          .build());

    doReturn(OptionalOutcome.builder()
                 .found(true)
                 .outcome(new GitopsClustersOutcome(new ArrayList<>())
                              .appendCluster(new Metadata("env1", "env1"), new Metadata("c1", "c1"), "Production"))
                 .build())
        .when(outcomeService)
        .resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject("gitops"));

    OrchestrationEvent event = OrchestrationEvent.builder().ambiance(ambiance).status(Status.SUCCEEDED).build();
    CDPipelineModuleInfo pipelineLevelModuleInfo = (CDPipelineModuleInfo) provider.getPipelineLevelModuleInfo(event);

    assertThat(pipelineLevelModuleInfo.getEnvIdentifiers()).containsExactlyInAnyOrder("env1");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testGetPipelineLevelModuleInfo_Gitops_2() {
    Ambiance ambiance = buildAmbiance(StepType.newBuilder()
                                          .setType(ExecutionNodeType.GITOPS_CLUSTERS.getName())
                                          .setStepCategory(StepCategory.STEP)
                                          .build());
    doReturn(OptionalOutcome.builder()
                 .found(true)
                 .outcome(new GitopsClustersOutcome(new ArrayList<>())
                              .appendCluster(new Metadata("env1", "env1"), new Metadata("c1", "c1"), "Production")
                              .appendCluster(new Metadata("env2", "env2"), new Metadata("c2", "c2"), "Production"))
                 .build())
        .when(outcomeService)
        .resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject("gitops"));

    OrchestrationEvent event = OrchestrationEvent.builder().ambiance(ambiance).status(Status.SUCCEEDED).build();
    CDPipelineModuleInfo pipelineLevelModuleInfo = (CDPipelineModuleInfo) provider.getPipelineLevelModuleInfo(event);

    assertThat(pipelineLevelModuleInfo.getEnvIdentifiers()).containsExactlyInAnyOrder("env1", "env2");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testGetPipelineLevelModuleInfo_GitopsEnvGroup() {
    Ambiance ambiance = buildAmbiance(StepType.newBuilder()
                                          .setType(ExecutionNodeType.GITOPS_CLUSTERS.getName())
                                          .setStepCategory(StepCategory.STEP)
                                          .build());

    doReturn(OptionalOutcome.builder()
                 .found(true)
                 .outcome(new GitopsClustersOutcome(new ArrayList<>())
                              .appendCluster(new Metadata("envgroup1", "envgroup1"), new Metadata("env1", "env1"),
                                  EnvironmentType.PreProduction.toString(), new Metadata("c1", "c1"), null))
                 .build())
        .when(outcomeService)
        .resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject("gitops"));

    OrchestrationEvent event = OrchestrationEvent.builder().ambiance(ambiance).status(Status.SUCCEEDED).build();
    CDPipelineModuleInfo pipelineLevelModuleInfo = (CDPipelineModuleInfo) provider.getPipelineLevelModuleInfo(event);

    assertThat(pipelineLevelModuleInfo.getEnvIdentifiers()).containsExactlyInAnyOrder("env1");
    assertThat(pipelineLevelModuleInfo.getEnvGroupIdentifiers()).containsExactlyInAnyOrder("envgroup1");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testGetStageLevelModuleInfo_Svc() {
    Ambiance ambiance = buildAmbiance(StepType.newBuilder()
                                          .setType(ExecutionNodeType.SERVICE_SECTION.getName())
                                          .setStepCategory(StepCategory.STEP)
                                          .build());

    doReturn(OptionalOutcome.builder()
                 .found(true)
                 .outcome(ServiceStepOutcome.builder()
                              .identifier("s1")
                              .type(ServiceDefinitionType.KUBERNETES.getYamlName())
                              .build())
                 .build())
        .when(outcomeService)
        .resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject("service"));

    doReturn(OptionalOutcome.builder()
                 .found(true)
                 .outcome(ArtifactsOutcome.builder().primary(GcrArtifactOutcome.builder().build()).build())
                 .build())
        .when(outcomeService)
        .resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject("artifacts"));

    OrchestrationEvent event = OrchestrationEvent.builder().ambiance(ambiance).status(Status.SUCCEEDED).build();
    CDStageModuleInfo stageLevelModuleInfo = (CDStageModuleInfo) provider.getStageLevelModuleInfo(event);

    assertThat(stageLevelModuleInfo.getServiceInfo().getIdentifier()).isEqualTo("s1");
    assertThat(stageLevelModuleInfo.getServiceInfo().getArtifacts().getPrimary())
        .isInstanceOf(GcrArtifactSummary.class);
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testGetStageLevelModuleInfo_Env() {
    Ambiance ambiance = buildAmbiance(StepType.newBuilder()
                                          .setType(ExecutionNodeType.INFRASTRUCTURE.getName())
                                          .setStepCategory(StepCategory.STEP)
                                          .build());

    doReturn(
        OptionalOutcome.builder()
            .found(true)
            .outcome(K8sDirectInfrastructureOutcome.builder()
                         .environment(
                             EnvironmentOutcome.builder().identifier("env1").type(EnvironmentType.Production).build())
                         .build())
            .build())
        .when(outcomeService)
        .resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject("output"));

    OrchestrationEvent event = OrchestrationEvent.builder().ambiance(ambiance).status(Status.SUCCEEDED).build();
    CDStageModuleInfo stageLevelModuleInfo = (CDStageModuleInfo) provider.getStageLevelModuleInfo(event);

    assertThat(stageLevelModuleInfo.getInfraExecutionSummary().getIdentifier()).isEqualTo("env1");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testGetStageLevelModuleInfo_Gitops_0() {
    Ambiance ambiance = buildAmbiance(StepType.newBuilder()
                                          .setType(ExecutionNodeType.GITOPS_CLUSTERS.getName())
                                          .setStepCategory(StepCategory.STEP)
                                          .build());

    doReturn(OptionalOutcome.builder()
                 .found(true)
                 .outcome(new GitopsClustersOutcome(new ArrayList<>())
                              .appendCluster(new Metadata("env1", "env1name"), new Metadata("c1", "c1"), "Production"))
                 .build())
        .when(outcomeService)
        .resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject("gitops"));

    OrchestrationEvent event = OrchestrationEvent.builder().ambiance(ambiance).status(Status.SUCCEEDED).build();
    CDStageModuleInfo stageLevelModuleInfo = (CDStageModuleInfo) provider.getStageLevelModuleInfo(event);

    assertThat(stageLevelModuleInfo.getGitopsExecutionSummary().getEnvironments())
        .hasSize(1)
        .contains(GitOpsExecutionSummary.Environment.builder()
                      .identifier("env1")
                      .name("env1name")
                      .type("Production")
                      .build());

    assertThat(stageLevelModuleInfo.getInfraExecutionSummary().getIdentifier()).isEqualTo("env1");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testGetStageLevelModuleInfo_Gitops_1() {
    Ambiance ambiance = buildAmbiance(StepType.newBuilder()
                                          .setType(ExecutionNodeType.GITOPS_CLUSTERS.getName())
                                          .setStepCategory(StepCategory.STEP)
                                          .build());

    doReturn(OptionalOutcome.builder()
                 .found(true)
                 .outcome(new GitopsClustersOutcome(new ArrayList<>())
                              .appendCluster(new Metadata("env1", "env1"), new Metadata("c1", "c1"), "Production")
                              .appendCluster(new Metadata("env2", "env2"), new Metadata("c2", "c2"), "Production"))
                 .build())
        .when(outcomeService)
        .resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject("gitops"));

    OrchestrationEvent event = OrchestrationEvent.builder().ambiance(ambiance).status(Status.SUCCEEDED).build();
    CDStageModuleInfo stageLevelModuleInfo = (CDStageModuleInfo) provider.getStageLevelModuleInfo(event);

    assertThat(stageLevelModuleInfo.getInfraExecutionSummary()).isNull();
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testGetStageLevelModuleInfo_Gitops_2() {
    Ambiance ambiance = buildAmbiance(StepType.newBuilder()
                                          .setType(ExecutionNodeType.GITOPS_CLUSTERS.getName())
                                          .setStepCategory(StepCategory.STEP)
                                          .build());

    doReturn(OptionalOutcome.builder()
                 .found(true)
                 .outcome(new GitopsClustersOutcome(new ArrayList<>())
                              .appendCluster(new Metadata("env1", "env1name"), new Metadata("c1", "c1"), "Production")
                              .appendCluster(new Metadata("env2", "env2name"), new Metadata("c2", "c2"), "Production")
                              .appendCluster(new Metadata("eg1", "eg1name"), new Metadata("env3", "env3name"),
                                  EnvironmentType.PreProduction.toString(), new Metadata("c3", "c3"), null))
                 .build())
        .when(outcomeService)
        .resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject("gitops"));

    OrchestrationEvent event = OrchestrationEvent.builder().ambiance(ambiance).status(Status.SUCCEEDED).build();
    CDStageModuleInfo stageLevelModuleInfo = (CDStageModuleInfo) provider.getStageLevelModuleInfo(event);

    assertThat(stageLevelModuleInfo.getGitopsExecutionSummary().getEnvironments())
        .hasSize(3)
        .containsExactlyInAnyOrder(
            GitOpsExecutionSummary.Environment.builder().identifier("env1").name("env1name").type("Production").build(),
            GitOpsExecutionSummary.Environment.builder().identifier("env2").name("env2name").type("Production").build(),
            GitOpsExecutionSummary.Environment.builder()
                .identifier("env3")
                .name("env3name")
                .envGroupName("eg1name")
                .envGroupIdentifier("eg1")
                .type("PreProduction")
                .build());
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testGetStageLevelModuleInfo_Gitops_3() {
    Ambiance ambiance = buildAmbiance(StepType.newBuilder()
                                          .setType(ExecutionNodeType.GITOPS_CLUSTERS.getName())
                                          .setStepCategory(StepCategory.STEP)
                                          .build());

    doReturn(OptionalOutcome.builder()
                 .found(true)
                 .outcome(new GitopsClustersOutcome(new ArrayList<>())
                              .appendCluster(new Metadata("eg1", "eg1name"), new Metadata("env1", "env1name"),
                                  EnvironmentType.PreProduction.toString(), new Metadata("c1", "c1"), null)
                              .appendCluster(new Metadata("eg1", "eg1name"), new Metadata("env2", "env2name"),
                                  EnvironmentType.PreProduction.toString(), new Metadata("c2", "c2"), null))
                 .build())
        .when(outcomeService)
        .resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject("gitops"));

    OrchestrationEvent event = OrchestrationEvent.builder().ambiance(ambiance).status(Status.SUCCEEDED).build();
    CDStageModuleInfo stageLevelModuleInfo = (CDStageModuleInfo) provider.getStageLevelModuleInfo(event);

    assertThat(stageLevelModuleInfo.getGitopsExecutionSummary().getEnvironments())
        .hasSize(2)
        .containsExactlyInAnyOrder(GitOpsExecutionSummary.Environment.builder()
                                       .identifier("env1")
                                       .name("env1name")
                                       .type("PreProduction")
                                       .envGroupName("eg1name")
                                       .envGroupIdentifier("eg1")
                                       .build(),
            GitOpsExecutionSummary.Environment.builder()
                .identifier("env2")
                .name("env2name")
                .type("PreProduction")
                .envGroupName("eg1name")
                .envGroupIdentifier("eg1")
                .build());
  }

  @Test
  @Owner(developers = OwnerRule.PRATYUSH)
  @Category(UnitTests.class)
  public void testGetStageLevelModuleInfo_RollbackDuration() {
    Ambiance ambiance = buildAmbiance(
        StepType.newBuilder().setType("ROLLBACK_OPTIONAL_CHILD_CHAIN").setStepCategory(StepCategory.STEP).build());

    OrchestrationEvent event = OrchestrationEvent.builder().ambiance(ambiance).status(Status.SUCCEEDED).build();
    CDStageModuleInfo stageLevelModuleInfo = (CDStageModuleInfo) provider.getStageLevelModuleInfo(event);

    assertThat(stageLevelModuleInfo.getRollbackDuration()).isPositive();
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testShouldRun_0() {
    Ambiance ambiance = buildAmbiance(StepType.newBuilder()
                                          .setType(ExecutionNodeType.GITOPS_CLUSTERS.getName())
                                          .setStepCategory(StepCategory.STEP)
                                          .build());
    assertThat(provider.shouldRun(OrchestrationEvent.builder().ambiance(ambiance).status(Status.SUCCEEDED).build()))
        .isTrue();

    assertThat(provider.shouldRun(OrchestrationEvent.builder().ambiance(ambiance).status(Status.FAILED).build()))
        .isTrue();

    assertThat(provider.shouldRun(OrchestrationEvent.builder().ambiance(ambiance).status(Status.RUNNING).build()))
        .isFalse();
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testShouldRun_1() {
    Ambiance ambiance = buildAmbiance(StepType.newBuilder()
                                          .setType(ExecutionNodeType.SERVICE_SECTION.getName())
                                          .setStepCategory(StepCategory.STEP)
                                          .build());
    assertThat(provider.shouldRun(OrchestrationEvent.builder().ambiance(ambiance).status(Status.SUCCEEDED).build()))
        .isTrue();

    assertThat(provider.shouldRun(OrchestrationEvent.builder().ambiance(ambiance).status(Status.FAILED).build()))
        .isTrue();

    assertThat(provider.shouldRun(OrchestrationEvent.builder().ambiance(ambiance).status(Status.RUNNING).build()))
        .isFalse();
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testShouldRun_2() {
    Ambiance ambiance = buildAmbiance(StepType.newBuilder()
                                          .setType(ExecutionNodeType.INFRASTRUCTURE.getName())
                                          .setStepCategory(StepCategory.STEP)
                                          .build());

    assertThat(provider.shouldRun(OrchestrationEvent.builder().ambiance(ambiance).status(Status.SUCCEEDED).build()))
        .isTrue();

    assertThat(provider.shouldRun(OrchestrationEvent.builder().ambiance(ambiance).status(Status.FAILED).build()))
        .isTrue();

    assertThat(provider.shouldRun(OrchestrationEvent.builder().ambiance(ambiance).status(Status.RUNNING).build()))
        .isFalse();
  }

  @Test
  @Owner(developers = OwnerRule.PRATYUSH)
  @Category(UnitTests.class)
  public void testShouldRun_4() {
    Ambiance ambiance = buildAmbiance(
        StepType.newBuilder().setType("ROLLBACK_OPTIONAL_CHILD_CHAIN").setStepCategory(StepCategory.STEP).build());
    assertThat(provider.shouldRun(OrchestrationEvent.builder().ambiance(ambiance).status(Status.SUCCEEDED).build()))
        .isTrue();

    assertThat(provider.shouldRun(OrchestrationEvent.builder().ambiance(ambiance).status(Status.FAILED).build()))
        .isTrue();

    assertThat(provider.shouldRun(OrchestrationEvent.builder().ambiance(ambiance).status(Status.RUNNING).build()))
        .isFalse();
  }

  @Test
  @Owner(developers = OwnerRule.VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldPopulateGitOpsClustersInStageModuleInfo() {
    Ambiance ambiance = buildAmbiance(StepType.newBuilder()
                                          .setType(ExecutionNodeType.GITOPS_CLUSTERS.getName())
                                          .setStepCategory(StepCategory.STEP)
                                          .build());

    doReturn(
        OptionalOutcome.builder()
            .found(true)
            .outcome(new GitopsClustersOutcome(new ArrayList<>())
                         .appendCluster(new Metadata("eg1", "eg1name"), new Metadata("env1", "env1name"),
                             EnvironmentType.PreProduction.toString(), new Metadata("c1", "c1name"), "agent1")
                         .appendCluster(new Metadata("eg1", "eg1name"), new Metadata("env1", "env1name"),
                             EnvironmentType.PreProduction.toString(), new Metadata("account.c2", "c2name"), "agent2")
                         .appendCluster(new Metadata("eg1", "eg1name"), new Metadata("env2", "env2name"),
                             EnvironmentType.PreProduction.toString(), new Metadata("c3", "c3name"), null))
            .build())
        .when(outcomeService)
        .resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject("gitops"));

    OrchestrationEvent event = OrchestrationEvent.builder().ambiance(ambiance).status(Status.SUCCEEDED).build();
    CDStageModuleInfo stageLevelModuleInfo = (CDStageModuleInfo) provider.getStageLevelModuleInfo(event);

    List<GitOpsExecutionSummary.Cluster> clusters = stageLevelModuleInfo.getGitopsExecutionSummary().getClusters();
    assertThat(clusters).hasSize(3);
    assertThat(clusters.get(0))
        .isEqualTo(GitOpsExecutionSummary.Cluster.builder()
                       .clusterId("c1")
                       .clusterName("c1name")
                       .envName("env1name")
                       .envId("env1")
                       .envGroupName("eg1name")
                       .envGroupId("eg1")
                       .agentId("agent1")
                       .scope(ScopeLevel.PROJECT.name())
                       .build());
    assertThat(clusters.get(1))
        .isEqualTo(GitOpsExecutionSummary.Cluster.builder()
                       .clusterId("c2")
                       .clusterName("c2name")
                       .envName("env1name")
                       .envId("env1")
                       .envGroupName("eg1name")
                       .envGroupId("eg1")
                       .agentId("agent2")
                       .scope(ScopeLevel.ACCOUNT.name())
                       .build());
    assertThat(clusters.get(2))
        .isEqualTo(GitOpsExecutionSummary.Cluster.builder()
                       .clusterId("c3")
                       .clusterName("c3name")
                       .envName("env2name")
                       .envId("env2")
                       .envGroupName("eg1name")
                       .envGroupId("eg1")
                       .scope(ScopeLevel.PROJECT.name())
                       .build());
  }

  public Ambiance buildAmbiance(StepType stepType) {
    final String PHASE_RUNTIME_ID = generateUuid();
    final String PHASE_SETUP_ID = generateUuid();
    final String PLAN_EXECUTION_ID = generateUuid();
    List<Level> levels = new ArrayList<>();
    levels.add(
        Level.newBuilder().setRuntimeId(PHASE_RUNTIME_ID).setSetupId(PHASE_SETUP_ID).setStepType(stepType).build());
    return Ambiance.newBuilder()
        .setPlanExecutionId(PLAN_EXECUTION_ID)
        .putAllSetupAbstractions(Map.of("accountId", ACCOUNT_ID, "appId", APP_ID))
        .addAllLevels(levels)
        .setExpressionFunctorToken(1234)
        .setStartTs(0L)
        .build();
  }
}
