/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.states;

import static io.harness.beans.sweepingoutputs.CISweepingOutputNames.STAGE_EXECUTION;
import static io.harness.beans.sweepingoutputs.CISweepingOutputNames.UNIQUE_STEP_IDENTIFIERS;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.execution.PublishedImageArtifact;
import io.harness.beans.stages.IntegrationStageStepParametersPMS;
import io.harness.beans.steps.outcome.CIStepArtifactOutcome;
import io.harness.beans.steps.outcome.IntegrationStageOutcome;
import io.harness.beans.steps.outcome.StepArtifacts;
import io.harness.beans.sweepingoutputs.UniqueStepIdentifiersSweepingOutput;
import io.harness.category.element.UnitTests;
import io.harness.ci.executionplan.CIExecutionTestBase;
import io.harness.plancreator.steps.common.StageElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.sdk.core.data.OptionalOutcome;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepOutcome;
import io.harness.pms.sdk.core.steps.io.StepResponseNotifyData;
import io.harness.rule.Owner;
import io.harness.tasks.ResponseData;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.CI)
public class IntegrationStageStepPMSTest extends CIExecutionTestBase {
  @Mock private ExecutionSweepingOutputService executionSweepingOutputResolver;
  @Mock private OutcomeService outcomeService;
  @InjectMocks private IntegrationStageStepPMS integrationStageStepPMS;
  private Ambiance ambiance;
  private IntegrationStageStepParametersPMS integrationStageStepParametersPMS;
  private StageElementParameters stageElementParameters;

  @Before
  public void setUp() {
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put("accountId", "accountId");
    ambiance = Ambiance.newBuilder()
                   .putAllSetupAbstractions(setupAbstractions)
                   .addLevels(Level.newBuilder().setStepType(InitializeTaskStep.STEP_TYPE).build())
                   .build();
    integrationStageStepParametersPMS = IntegrationStageStepParametersPMS.builder().build();
    stageElementParameters = StageElementParameters.builder().specConfig(integrationStageStepParametersPMS).build();
  }

  @SneakyThrows
  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void shouldHandleSuccessfulTaskResultWithOutcomes() {
    List<String> uniqueIdentifiers = Arrays.asList("id1", "id2");
    Map<String, ResponseData> responseDataMap =
        ImmutableMap.<String, ResponseData>builder()
            .put("id", StepResponseNotifyData.builder().status(Status.SUCCEEDED).build())
            .build();
    when(executionSweepingOutputResolver.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(STAGE_EXECUTION)))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getOutcomeRefObject(UNIQUE_STEP_IDENTIFIERS)))
        .thenReturn(
            OptionalSweepingOutput.builder()
                .found(true)
                .output(UniqueStepIdentifiersSweepingOutput.builder().uniqueStepIdentifiers(uniqueIdentifiers).build())
                .build());

    uniqueIdentifiers.forEach(uid
        -> when(outcomeService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject("artifact_" + uid)))
               .thenReturn(OptionalOutcome.builder()
                               .found(true)
                               .outcome(CIStepArtifactOutcome.builder()
                                            .stepArtifacts(StepArtifacts.builder()
                                                               .publishedImageArtifacts(Collections.singletonList(
                                                                   PublishedImageArtifact.builder()
                                                                       .imageName("image_" + uid)
                                                                       .tag("tag_" + uid)
                                                                       .digest("digest_" + uid)
                                                                       .build()))
                                                               .build())
                                            .build())
                               .build()));

    StepResponse stepResponse =
        integrationStageStepPMS.handleChildResponse(ambiance, stageElementParameters, responseDataMap);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getStepOutcomes()).isNotNull();
    assertThat(stepResponse.getStepOutcomes()).hasSize(1);

    StepOutcome stepOutcome = new ArrayList<>(stepResponse.getStepOutcomes()).get(0);
    IntegrationStageOutcome integrationStageOutcome = (IntegrationStageOutcome) stepOutcome.getOutcome();
    assertThat(integrationStageOutcome.getImageArtifacts()).hasSize(2);

    integrationStageOutcome.getImageArtifacts().forEach(artifact -> {
      assertThat(artifact.getImageName()).isIn("image_id1", "image_id2");
      assertThat(artifact.getTag()).isIn("tag_id1", "tag_id2");
      assertThat(artifact.getDigest()).isIn("digest_id1", "digest_id2");
    });
  }

  @SneakyThrows
  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void shouldHandleSuccessfulTaskResultWithBackupOutcomes() {
    List<String> uniqueIdentifiers = Arrays.asList("id1", "id2");
    Map<String, ResponseData> responseDataMap =
        ImmutableMap.<String, ResponseData>builder()
            .put("id", StepResponseNotifyData.builder().status(Status.SUCCEEDED).build())
            .build();
    integrationStageStepParametersPMS.setStepIdentifiers(uniqueIdentifiers);
    when(executionSweepingOutputResolver.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(STAGE_EXECUTION)))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getOutcomeRefObject(UNIQUE_STEP_IDENTIFIERS)))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());

    uniqueIdentifiers.forEach(uid
        -> when(outcomeService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject("artifact_" + uid)))
               .thenReturn(OptionalOutcome.builder()
                               .found(true)
                               .outcome(CIStepArtifactOutcome.builder()
                                            .stepArtifacts(StepArtifacts.builder()
                                                               .publishedImageArtifacts(Collections.singletonList(
                                                                   PublishedImageArtifact.builder()
                                                                       .imageName("image_" + uid)
                                                                       .tag("tag_" + uid)
                                                                       .digest("digest_" + uid)
                                                                       .build()))
                                                               .build())
                                            .build())
                               .build()));

    StepResponse stepResponse =
        integrationStageStepPMS.handleChildResponse(ambiance, stageElementParameters, responseDataMap);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getStepOutcomes()).isNotNull();
    assertThat(stepResponse.getStepOutcomes()).hasSize(1);

    StepOutcome stepOutcome = new ArrayList<>(stepResponse.getStepOutcomes()).get(0);
    IntegrationStageOutcome integrationStageOutcome = (IntegrationStageOutcome) stepOutcome.getOutcome();
    assertThat(integrationStageOutcome.getImageArtifacts()).hasSize(2);

    integrationStageOutcome.getImageArtifacts().forEach(artifact -> {
      assertThat(artifact.getImageName()).isIn("image_id1", "image_id2");
      assertThat(artifact.getTag()).isIn("tag_id1", "tag_id2");
      assertThat(artifact.getDigest()).isIn("digest_id1", "digest_id2");
    });
  }
}
