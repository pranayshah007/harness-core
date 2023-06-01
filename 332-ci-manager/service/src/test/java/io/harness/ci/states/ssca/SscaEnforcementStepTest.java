/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.states.ssca;

import static io.harness.beans.sweepingoutputs.StageInfraDetails.STAGE_INFRA_DETAILS;
import static io.harness.rule.OwnerRule.INDER;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.outcome.CIStepArtifactOutcome;
import io.harness.beans.sweepingoutputs.K8StageInfraDetails;
import io.harness.beans.sweepingoutputs.VmStageInfraDetails;
import io.harness.category.element.UnitTests;
import io.harness.ci.executionplan.CIExecutionTestBase;
import io.harness.delegate.beans.ci.vm.VmTaskExecutionResponse;
import io.harness.delegate.task.stepstatus.StepExecutionStatus;
import io.harness.delegate.task.stepstatus.StepStatus;
import io.harness.delegate.task.stepstatus.StepStatusTaskResponseData;
import io.harness.helper.SerializedResponseDataHelper;
import io.harness.logging.CommandExecutionStatus;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.rule.Owner;
import io.harness.ssca.beans.stepinfo.SscaEnforcementStepInfo;
import io.harness.ssca.client.SSCAServiceUtils;
import io.harness.ssca.client.beans.Artifact;
import io.harness.ssca.client.beans.enforcement.SscaEnforcementSummary;
import io.harness.ssca.execution.orchestration.outcome.PublishedSbomArtifact;
import io.harness.tasks.ResponseData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.SSCA)
public class SscaEnforcementStepTest extends CIExecutionTestBase {
  @InjectMocks private SscaEnforcementStep sscaEnforcementStep;

  @Mock private SerializedResponseDataHelper serializedResponseDataHelper;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputResolver;
  @Mock private SSCAServiceUtils sscaServiceUtils;

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testHandleK8sAsyncResponse() {
    Ambiance ambiance = SscaTestsUtility.getAmbiance();
    SscaEnforcementStepInfo sscaEnforcementStepInfo =
        SscaEnforcementStepInfo.builder().identifier(SscaTestsUtility.STEP_IDENTIFIER).build();
    StepElementParameters stepElementParameters = SscaTestsUtility.getStepElementParameters(sscaEnforcementStepInfo);

    StepStatusTaskResponseData stepStatusTaskResponseData =
        StepStatusTaskResponseData.builder()
            .stepStatus(StepStatus.builder().stepExecutionStatus(StepExecutionStatus.SUCCESS).build())
            .build();

    Map<String, ResponseData> responseDataMap = new HashMap<>();
    responseDataMap.put("response", stepStatusTaskResponseData);
    when(serializedResponseDataHelper.deserialize(stepStatusTaskResponseData)).thenReturn(stepStatusTaskResponseData);
    when(executionSweepingOutputResolver.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(STAGE_INFRA_DETAILS)))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(K8StageInfraDetails.builder().build()).build());

    when(sscaServiceUtils.getEnforcementSummary(SscaTestsUtility.STEP_EXECUTION_ID))
        .thenReturn(
            SscaEnforcementSummary.builder()
                .status("success")
                .enforcementId(SscaTestsUtility.STEP_EXECUTION_ID)
                .allowListViolationCount(3)
                .denyListViolationCount(5)
                .artifact(Artifact.builder().type("image").name("library/nginx").tag("latest").id("someId").build())
                .build());

    PublishedSbomArtifact publishedSbomArtifact = PublishedSbomArtifact.builder()
                                                      .allowListViolationCount(3)
                                                      .denyListViolationCount(5)
                                                      .stepExecutionId(SscaTestsUtility.STEP_EXECUTION_ID)
                                                      .imageName("library/nginx")
                                                      .id("someId")
                                                      .build();
    StepResponse stepResponse =
        sscaEnforcementStep.handleAsyncResponseInternal(ambiance, stepElementParameters, responseDataMap);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getStepOutcomes().size()).isEqualTo(2);
    List<StepResponse.StepOutcome> stepOutcomeList = new ArrayList<>();
    stepResponse.getStepOutcomes().forEach(stepOutcome -> {
      if (stepOutcome.getOutcome() instanceof CIStepArtifactOutcome) {
        stepOutcomeList.add(stepOutcome);
      }
    });
    assertThat(stepOutcomeList).hasSize(1);
    stepOutcomeList.forEach(stepOutcome -> {
      assertThat(stepOutcome.getOutcome()).isInstanceOf(CIStepArtifactOutcome.class);
      CIStepArtifactOutcome outcome = (CIStepArtifactOutcome) stepOutcome.getOutcome();
      assertThat(outcome).isNotNull();
      assertThat(outcome.getStepArtifacts()).isNotNull();
      assertThat(outcome.getStepArtifacts().getPublishedSbomArtifacts()).isNotNull().hasSize(1);
      assertThat(outcome.getStepArtifacts().getPublishedSbomArtifacts().get(0)).isEqualTo(publishedSbomArtifact);
      assertThat(stepOutcome.getName()).isEqualTo("artifact_identifierId");
    });
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testHandleVmAsyncResponse() {
    Ambiance ambiance = SscaTestsUtility.getAmbiance();
    SscaEnforcementStepInfo sscaEnforcementStepInfo =
        SscaEnforcementStepInfo.builder().identifier(SscaTestsUtility.STEP_IDENTIFIER).build();
    StepElementParameters stepElementParameters = SscaTestsUtility.getStepElementParameters(sscaEnforcementStepInfo);

    ResponseData responseData =
        VmTaskExecutionResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build();

    Map<String, ResponseData> responseDataMap = new HashMap<>();
    responseDataMap.put("response", responseData);
    when(serializedResponseDataHelper.deserialize(responseData)).thenReturn(responseData);
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(STAGE_INFRA_DETAILS)))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(VmStageInfraDetails.builder().build()).build());

    when(sscaServiceUtils.getEnforcementSummary(SscaTestsUtility.STEP_EXECUTION_ID))
        .thenReturn(
            SscaEnforcementSummary.builder()
                .status("success")
                .enforcementId(SscaTestsUtility.STEP_EXECUTION_ID)
                .allowListViolationCount(3)
                .denyListViolationCount(5)
                .artifact(Artifact.builder().type("image").name("library/nginx").tag("latest").id("someId").build())
                .build());

    PublishedSbomArtifact publishedSbomArtifact = PublishedSbomArtifact.builder()
                                                      .allowListViolationCount(3)
                                                      .denyListViolationCount(5)
                                                      .stepExecutionId(SscaTestsUtility.STEP_EXECUTION_ID)
                                                      .imageName("library/nginx")
                                                      .id("someId")
                                                      .build();
    StepResponse stepResponse =
        sscaEnforcementStep.handleAsyncResponseInternal(ambiance, stepElementParameters, responseDataMap);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getStepOutcomes().size()).isEqualTo(1);
    stepResponse.getStepOutcomes().forEach(stepOutcome -> {
      assertThat(stepOutcome.getOutcome()).isInstanceOf(CIStepArtifactOutcome.class);
      CIStepArtifactOutcome outcome = (CIStepArtifactOutcome) stepOutcome.getOutcome();
      assertThat(outcome).isNotNull();
      assertThat(outcome.getStepArtifacts()).isNotNull();
      assertThat(outcome.getStepArtifacts().getPublishedSbomArtifacts()).isNotNull().hasSize(1);
      assertThat(outcome.getStepArtifacts().getPublishedSbomArtifacts().get(0)).isEqualTo(publishedSbomArtifact);
      assertThat(stepOutcome.getName()).isEqualTo("artifact_identifierId");
    });
  }
}
