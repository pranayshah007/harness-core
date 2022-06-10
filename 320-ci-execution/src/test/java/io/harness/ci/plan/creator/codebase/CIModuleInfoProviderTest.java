/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.plan.creator.codebase;

import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.sweepingoutputs.CodebaseSweepingOutput;
import io.harness.category.element.UnitTests;
import io.harness.ci.pipeline.executions.beans.CIBuildCommit;
import io.harness.ci.plan.creator.CIModuleInfoProvider;
import io.harness.ci.plan.creator.execution.CIPipelineModuleInfo;
import io.harness.executionplan.CIExecutionTestBase;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.events.OrchestrationEvent;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.groovy.util.Maps;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

@Slf4j
@OwnedBy(HarnessTeam.CI)
public class CIModuleInfoProviderTest extends CIExecutionTestBase {
  private final Ambiance ambiance = Ambiance.newBuilder()
                                        .putAllSetupAbstractions(Maps.of("accountId", "accountId", "projectIdentifier",
                                            "projectIdentifier", "orgIdentifier", "orgIdentifier"))
                                        .build();

  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject private CIModuleInfoProvider ciModuleInfoProvider;

  @Before
  public void setUp() {
    on(ciModuleInfoProvider).set("executionSweepingOutputService", executionSweepingOutputService);
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetPipelineLevelModuleInfoWithoutResolvedParameters() {
    OrchestrationEvent event =
        OrchestrationEvent.builder().ambiance(ambiance).serviceName("ci").status(Status.RUNNING).build();

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
                                    .build())
                        .build());
    CIPipelineModuleInfo ciPipelineModuleInfo =
        (CIPipelineModuleInfo) ciModuleInfoProvider.getPipelineLevelModuleInfo(event);
    assertThat(ciPipelineModuleInfo.getRepoName()).isEqualTo("repo-name");
    assertThat(ciPipelineModuleInfo.getPrNumber()).isEqualTo("1");
    assertThat(ciPipelineModuleInfo.getTag()).isEqualTo("tag");
    assertThat(ciPipelineModuleInfo.getCiExecutionInfoDTO().getPullRequest().getSourceBranch()).isEqualTo("test");
    assertThat(ciPipelineModuleInfo.getCiExecutionInfoDTO().getPullRequest().getTargetBranch()).isEqualTo("main");
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetPipelineLevelModuleInfoForAzure() {
    OrchestrationEvent event =
        OrchestrationEvent.builder().ambiance(ambiance).serviceName("ci").status(Status.RUNNING).build();

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
  }
}
