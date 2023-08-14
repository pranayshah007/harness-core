/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.plan.creator.codebase;

import static io.harness.beans.sweepingoutputs.CISweepingOutputNames.INITIALIZE_EXECUTION;
import static io.harness.beans.sweepingoutputs.CISweepingOutputNames.STAGE_EXECUTION;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;
import static io.harness.rule.OwnerRule.RUTVIJ_MEHTA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.execution.license.CILicenseService;
import io.harness.beans.stages.IntegrationStageStepParametersPMS;
import io.harness.beans.steps.stepinfo.InitializeStepInfo;
import io.harness.beans.sweepingoutputs.CodebaseSweepingOutput;
import io.harness.beans.sweepingoutputs.InitializeExecutionSweepingOutput;
import io.harness.beans.sweepingoutputs.StageExecutionSweepingOutput;
import io.harness.beans.yaml.extended.infrastrucutre.HostedVmInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.HostedVmInfraYaml.HostedVmInfraSpec;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.beans.yaml.extended.platform.ArchType;
import io.harness.beans.yaml.extended.platform.Platform;
import io.harness.category.element.UnitTests;
import io.harness.ci.buildstate.ConnectorUtils;
import io.harness.ci.executionplan.CIExecutionPlanTestHelper;
import io.harness.ci.executionplan.CIExecutionTestBase;
import io.harness.ci.pipeline.executions.beans.CIBuildAuthor;
import io.harness.ci.pipeline.executions.beans.CIBuildCommit;
import io.harness.ci.plan.creator.CIModuleInfoProvider;
import io.harness.ci.plan.creator.execution.CIPipelineModuleInfo;
import io.harness.ci.states.InitializeTaskStep;
import io.harness.ci.states.IntegrationStageStepPMS;
import io.harness.licensing.Edition;
import io.harness.licensing.LicenseType;
import io.harness.licensing.beans.summary.CILicenseSummaryDTO;
import io.harness.plancreator.steps.common.StageElementParameters;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.events.OrchestrationEvent;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.groovy.util.Maps;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@Slf4j
@OwnedBy(HarnessTeam.CI)
public class CIModuleInfoProviderTest extends CIExecutionTestBase {
  private CIExecutionPlanTestHelper ciExecutionPlanTestHelper = new CIExecutionPlanTestHelper();

  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;
  @InjectMocks private CIModuleInfoProvider ciModuleInfoProvider;
  @Mock private ConnectorUtils connectorUtils;
  @Mock private CILicenseService ciLicenseService;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetPipelineLevelModuleInfoWithoutResolvedParameters() {
    OrchestrationEvent event =
        OrchestrationEvent.builder()
            .ambiance(getAmbianceWithLevel(Level.newBuilder().setStepType(InitializeTaskStep.STEP_TYPE).build()))
            .serviceName("ci")
            .status(Status.RUNNING)
            .build();

    when(executionSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(CodebaseSweepingOutput.builder()
                                    .branch("main")
                                    .targetBranch("main")
                                    .sourceBranch("test")
                                    .tag("tag")
                                    .prNumber("1")
                                    .repoUrl("https://github.com/test/repo-name")
                                    .gitUserId("userId")
                                    .gitUser("userId")
                                    .gitUserAvatar("avatar")
                                    .gitUserEmail("email")
                                    .build())
                        .build());
    CILicenseSummaryDTO ciLicenseSummaryDTO =
        CILicenseSummaryDTO.builder().licenseType(LicenseType.PAID).edition(Edition.ENTERPRISE).build();
    when(ciLicenseService.getLicenseSummary(any())).thenReturn(ciLicenseSummaryDTO);
    CIPipelineModuleInfo ciPipelineModuleInfo =
        (CIPipelineModuleInfo) ciModuleInfoProvider.getPipelineLevelModuleInfo(event);
    assertThat(ciPipelineModuleInfo.getRepoName()).isEqualTo("repo-name");
    assertThat(ciPipelineModuleInfo.getPrNumber()).isEqualTo("1");
    assertThat(ciPipelineModuleInfo.getTag()).isEqualTo("tag");
    assertThat(ciPipelineModuleInfo.getCiExecutionInfoDTO().getPullRequest().getSourceBranch()).isEqualTo("test");
    assertThat(ciPipelineModuleInfo.getCiExecutionInfoDTO().getPullRequest().getTargetBranch()).isEqualTo("main");

    CIBuildAuthor author = ciPipelineModuleInfo.getCiExecutionInfoDTO().getAuthor();
    assertThat(author.getId()).isEqualTo("userId");
    assertThat(author.getName()).isEqualTo("userId");
    assertThat(author.getAvatar()).isEqualTo("avatar");
    assertThat(author.getEmail()).isEqualTo("email");

    assertThat(ciPipelineModuleInfo.getScmDetailsList().size()).isEqualTo(0);
    assertThat(ciPipelineModuleInfo.getInfraDetailsList().size()).isEqualTo(0);
    assertThat(ciPipelineModuleInfo.getImageDetailsList().size()).isEqualTo(0);
    assertThat(ciPipelineModuleInfo.getTiBuildDetailsList().size()).isEqualTo(0);

    assertThat(ciPipelineModuleInfo.getCiLicenseType()).isEqualTo(LicenseType.PAID.toString());
    assertThat(ciPipelineModuleInfo.getCiEditionType()).isEqualTo(Edition.ENTERPRISE.toString());
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testGetPipelineLevelModuleInfoWithResolvedParameters() {
    InitializeStepInfo initializeStepInfo =
        InitializeStepInfo.builder()
            .infrastructure(ciExecutionPlanTestHelper.getInfrastructureWithVolume())
            .executionElementConfig(ciExecutionPlanTestHelper.getExecutionElementConfig())
            .ciCodebase(ciExecutionPlanTestHelper.getCICodebaseWithRepoName())
            .build();

    OrchestrationEvent event =
        OrchestrationEvent.builder()
            .ambiance(getAmbianceWithLevel(Level.newBuilder().setStepType(InitializeTaskStep.STEP_TYPE).build()))
            .serviceName("ci")
            .status(Status.RUNNING)
            .resolvedStepParameters(StepElementParameters.builder().spec(initializeStepInfo).build())
            .build();

    when(executionSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().build());
    when(connectorUtils.getConnectorDetails(any(), any(), eq(true)))
        .thenReturn(ciExecutionPlanTestHelper.getGitConnector());
    CIPipelineModuleInfo ciPipelineModuleInfo =
        (CIPipelineModuleInfo) ciModuleInfoProvider.getPipelineLevelModuleInfo(event);

    assertThat(ciPipelineModuleInfo.getScmDetailsList().size()).isEqualTo(1);
    assertThat(ciPipelineModuleInfo.getInfraDetailsList().size()).isEqualTo(1);
    assertThat(ciPipelineModuleInfo.getImageDetailsList().size()).isEqualTo(4);
    assertThat(ciPipelineModuleInfo.getTiBuildDetailsList().size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetPipelineLevelModuleInfoForAzure() {
    OrchestrationEvent event =
        OrchestrationEvent.builder()
            .ambiance(getAmbianceWithLevel(Level.newBuilder().setStepType(InitializeTaskStep.STEP_TYPE).build()))
            .serviceName("ci")
            .status(Status.RUNNING)
            .build();

    List<CodebaseSweepingOutput.CodeBaseCommit> commits =
        new ArrayList<>(Arrays.asList(CodebaseSweepingOutput.CodeBaseCommit.builder().id("1").build(),
            CodebaseSweepingOutput.CodeBaseCommit.builder().id("2").build()));

    when(executionSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(CodebaseSweepingOutput.builder()
                                    .branch("main")
                                    .targetBranch("main")
                                    .sourceBranch("test")
                                    .tag("tag")
                                    .prNumber("1")
                                    .repoUrl("https://dev.azure.com/org/test/_git/test")
                                    .commits(commits)
                                    .build())
                        .build());
    List<CIBuildCommit> ciBuildCommits = new ArrayList<>(
        Arrays.asList(CIBuildCommit.builder().id("1").build(), CIBuildCommit.builder().id("2").build()));
    CIPipelineModuleInfo ciPipelineModuleInfo =
        (CIPipelineModuleInfo) ciModuleInfoProvider.getPipelineLevelModuleInfo(event);
    assertThat(ciPipelineModuleInfo.getRepoName()).isEqualTo("test/_git/test");
    assertThat(ciPipelineModuleInfo.getPrNumber()).isEqualTo("1");
    assertThat(ciPipelineModuleInfo.getTag()).isEqualTo("tag");
    assertThat(ciPipelineModuleInfo.getCiExecutionInfoDTO().getPullRequest().getSourceBranch()).isEqualTo("test");
    assertThat(ciPipelineModuleInfo.getCiExecutionInfoDTO().getPullRequest().getTargetBranch()).isEqualTo("main");
    assertThat(ciPipelineModuleInfo.getCiExecutionInfoDTO().getPullRequest().getCommits()).isEqualTo(ciBuildCommits);
    assertThat(ciPipelineModuleInfo.getCiExecutionInfoDTO().getAuthor()).isNull();
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetPipelineStageLevelModuleInfoForHostedVM() {
    Ambiance ambiance = getAmbianceWithLevel(
        Level.newBuilder().setStartTs(1111L).setStepType(IntegrationStageStepPMS.STEP_TYPE).build());
    Infrastructure infrastructure =
        HostedVmInfraYaml.builder()
            .spec(
                HostedVmInfraSpec.builder()
                    .platform(ParameterField.createValueField(Platform.builder()
                                                                  .os(ParameterField.createValueField(OSType.MacOS))
                                                                  .arch(ParameterField.createValueField(ArchType.Amd64))
                                                                  .build()))
                    .build())
            .build();
    OrchestrationEvent event =
        OrchestrationEvent.builder()
            .ambiance(ambiance)
            .serviceName("ci")
            .resolvedStepParameters(
                StageElementParameters.builder()
                    .identifier("stageId")
                    .name("stageName")
                    .specConfig(IntegrationStageStepParametersPMS.builder().infrastructure(infrastructure).build())
                    .build())
            .status(Status.RUNNING)
            .build();

    when(executionSweepingOutputService.resolveOptional(
             ambiance, RefObjectUtils.getOutcomeRefObject(INITIALIZE_EXECUTION)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(InitializeExecutionSweepingOutput.builder().initialiseExecutionTime(1234L).build())
                        .build());
    when(executionSweepingOutputService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(STAGE_EXECUTION)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(StageExecutionSweepingOutput.builder().stageExecutionTime(5671L).build())
                        .build());
    CIPipelineModuleInfo ciPipelineModuleInfo =
        (CIPipelineModuleInfo) ciModuleInfoProvider.getPipelineLevelModuleInfo(event);
    assertThat(ciPipelineModuleInfo.getCiPipelineStageModuleInfo()).isNotNull();
    assertThat(ciPipelineModuleInfo.getCiPipelineStageModuleInfo().getStageId()).isEqualTo("stageId");
    assertThat(ciPipelineModuleInfo.getCiPipelineStageModuleInfo().getStageName()).isEqualTo("stageName");
    assertThat(ciPipelineModuleInfo.getCiPipelineStageModuleInfo().getOsArch()).isEqualTo("Amd64");
    assertThat(ciPipelineModuleInfo.getCiPipelineStageModuleInfo().getOsType()).isEqualTo("MacOS");
    assertThat(ciPipelineModuleInfo.getCiPipelineStageModuleInfo().getStageExecutionId()).isEqualTo("stageExecutionId");
    assertThat(ciPipelineModuleInfo.getCiPipelineStageModuleInfo().getCpuTime()).isEqualTo(4437L);
    assertThat(ciPipelineModuleInfo.getCiPipelineStageModuleInfo().getStageBuildTime()).isEqualTo(5671L);
    assertThat(ciPipelineModuleInfo.getCiPipelineStageModuleInfo().getStartTs()).isEqualTo(1111L);
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetPipelineStageLevelModuleInfoWithoutResolvedParameter() {
    Ambiance ambiance = getAmbianceWithLevel(Level.newBuilder().setStepType(IntegrationStageStepPMS.STEP_TYPE).build());
    OrchestrationEvent event =
        OrchestrationEvent.builder().ambiance(ambiance).serviceName("ci").status(Status.RUNNING).build();

    CIPipelineModuleInfo ciPipelineModuleInfo =
        (CIPipelineModuleInfo) ciModuleInfoProvider.getPipelineLevelModuleInfo(event);
    assertThat(ciPipelineModuleInfo.getCiPipelineStageModuleInfo()).isNull();
  }

  private Ambiance getAmbianceWithLevel(Level level) {
    return Ambiance.newBuilder()
        .putAllSetupAbstractions(Maps.of(
            "accountId", "accountId", "projectIdentifier", "projectIdentifier", "orgIdentifier", "orgIdentifier"))
        .addLevels(level)
        .setStageExecutionId("stageExecutionId")
        .build();
  }
}
