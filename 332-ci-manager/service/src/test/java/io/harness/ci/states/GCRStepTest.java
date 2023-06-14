/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.states;

import static io.harness.beans.sweepingoutputs.StageInfraDetails.STAGE_INFRA_DETAILS;
import static io.harness.rule.OwnerRule.ALEKSANDAR;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.execution.PublishedImageArtifact;
import io.harness.beans.steps.outcome.CIStepArtifactOutcome;
import io.harness.beans.steps.outcome.StepArtifacts;
import io.harness.beans.steps.stepinfo.GCRStepInfo;
import io.harness.beans.sweepingoutputs.DliteVmStageInfraDetails;
import io.harness.category.element.UnitTests;
import io.harness.ci.executionplan.CIExecutionTestBase;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.ci.integrationstage.K8InitializeStepUtilsHelper;
import io.harness.delegate.beans.ci.vm.VmTaskExecutionResponse;
import io.harness.delegate.task.stepstatus.artifact.ArtifactMetadata;
import io.harness.delegate.task.stepstatus.artifact.ArtifactMetadataType;
import io.harness.delegate.task.stepstatus.artifact.DockerArtifactDescriptor;
import io.harness.delegate.task.stepstatus.artifact.DockerArtifactMetadata;
import io.harness.helper.SerializedResponseDataHelper;
import io.harness.logging.CommandExecutionStatus;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.repositories.CIStageOutputRepository;
import io.harness.rule.Owner;
import io.harness.tasks.ResponseData;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.CI)
public class GCRStepTest extends CIExecutionTestBase {
  @InjectMocks GCRStep gcrStep;
  @Mock SerializedResponseDataHelper serializedResponseDataHelper;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputResolver;
  @Mock protected CIFeatureFlagService featureFlagService;
  @Mock protected CIStageOutputRepository ciStageOutputRepository;

  public static final String STEP_ID = "gcrStepID";
  private Ambiance ambiance;
  private HashMap<String, String> setupAbstractions = new HashMap<>();
  private StepElementParameters stepElementParameters;

  @Before
  public void setUp() {
    setupAbstractions.put(SetupAbstractionKeys.accountId, "accountId");
    setupAbstractions.put(SetupAbstractionKeys.projectIdentifier, "projectId");
    setupAbstractions.put(SetupAbstractionKeys.orgIdentifier, "orgId");

    ambiance =
        Ambiance.newBuilder()
            .setMetadata(ExecutionMetadata.newBuilder().setPipelineIdentifier("pipelineId").setRunSequence(1).build())
            .putAllSetupAbstractions(setupAbstractions)
            .addLevels(Level.newBuilder()
                           .setRuntimeId("runtimeId")
                           .setIdentifier("identifierId")
                           .setOriginalIdentifier("originalIdentifierId")
                           .setRetryIndex(1)
                           .build())
            .build();

    GCRStepInfo stepInfo = GCRStepInfo.builder()
                               .identifier(STEP_ID)
                               .projectID(ParameterField.createValueField("project-id"))
                               .host(ParameterField.createValueField("us.gcr.io"))
                               .connectorRef(ParameterField.createValueField("connectorRef"))
                               .tags(ParameterField.createValueField(Arrays.asList("1.0", "2.0")))
                               .build();
    stepElementParameters =
        StepElementParameters.builder().identifier("identifier").name("name").spec(stepInfo).build();
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldHandleArtifact() {
    StepElementParameters stepElementParameters =
        StepElementParameters.builder()
            .identifier("stepId")
            .spec(GCRStepInfo.builder()
                      .host(ParameterField.createValueField("us.gcr.io"))
                      .projectID(ParameterField.createValueField("harness"))
                      .imageName(ParameterField.createValueField("ci-unittest"))
                      .tags(ParameterField.createValueField(Arrays.asList("1.0", "latest")))
                      .build())
            .build();
    ArtifactMetadata artifactMetadata =
        ArtifactMetadata.builder()
            .type(ArtifactMetadataType.DOCKER_ARTIFACT_METADATA)
            .spec(DockerArtifactMetadata.builder()
                      .registryType("GCR")
                      .registryUrl("us.gcr.io")
                      .dockerArtifact(
                          DockerArtifactDescriptor.builder()
                              .imageName("harness/ci-automation:1.0")
                              .digest("sha256:49f756463ad9dcfb9b6ade54d7d6f15476e7214f46a65b4b0c55d46845b12f70")
                              .build())
                      .dockerArtifact(
                          DockerArtifactDescriptor.builder()
                              .imageName("harness/ci-automation:latest")
                              .digest("sha256:49f756463ad9dcfb9b6ade54d7d6f15476e7214f46a65b4b0c55d46845b12f71")
                              .build())
                      .build())
            .build();
    StepArtifacts stepArtifacts = gcrStep.handleArtifact(artifactMetadata, stepElementParameters);
    assertThat(stepArtifacts).isNotNull();
    assertThat(stepArtifacts.getPublishedImageArtifacts())
        .contains(
            PublishedImageArtifact.builder()
                .imageName("harness/ci-automation")
                .tag("1.0")
                .url(
                    "https://console.cloud.google.com/gcr/images/harness/US/harness/ci-automation@sha256:49f756463ad9dcfb9b6ade54d7d6f15476e7214f46a65b4b0c55d46845b12f70/details")
                .digest("sha256:49f756463ad9dcfb9b6ade54d7d6f15476e7214f46a65b4b0c55d46845b12f70")
                .build(),
            PublishedImageArtifact.builder()
                .imageName("harness/ci-automation")
                .tag("latest")
                .url(
                    "https://console.cloud.google.com/gcr/images/harness/US/harness/ci-automation@sha256:49f756463ad9dcfb9b6ade54d7d6f15476e7214f46a65b4b0c55d46845b12f71/details")
                .digest("sha256:49f756463ad9dcfb9b6ade54d7d6f15476e7214f46a65b4b0c55d46845b12f71")
                .build());
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldHandleArtifactWithGlobalURL() {
    StepElementParameters stepElementParameters =
        StepElementParameters.builder()
            .identifier("stepId")
            .spec(GCRStepInfo.builder()
                      .host(ParameterField.createValueField("gcr.io"))
                      .projectID(ParameterField.createValueField("harness"))
                      .imageName(ParameterField.createValueField("ci-unittest"))
                      .tags(ParameterField.createValueField(Arrays.asList("1.0", "latest")))
                      .build())
            .build();
    ArtifactMetadata artifactMetadata =
        ArtifactMetadata.builder()
            .type(ArtifactMetadataType.DOCKER_ARTIFACT_METADATA)
            .spec(DockerArtifactMetadata.builder()
                      .registryType("GCR")
                      .registryUrl("gcr.io")
                      .dockerArtifact(
                          DockerArtifactDescriptor.builder()
                              .imageName("harness/ci-automation:1.0")
                              .digest("sha256:49f756463ad9dcfb9b6ade54d7d6f15476e7214f46a65b4b0c55d46845b12f70")
                              .build())
                      .dockerArtifact(
                          DockerArtifactDescriptor.builder()
                              .imageName("harness/ci-automation:latest")
                              .digest("sha256:49f756463ad9dcfb9b6ade54d7d6f15476e7214f46a65b4b0c55d46845b12f71")
                              .build())
                      .build())
            .build();
    StepArtifacts stepArtifacts = gcrStep.handleArtifact(artifactMetadata, stepElementParameters);
    assertThat(stepArtifacts).isNotNull();
    assertThat(stepArtifacts.getPublishedImageArtifacts())
        .contains(
            PublishedImageArtifact.builder()
                .imageName("harness/ci-automation")
                .tag("1.0")
                .url(
                    "https://console.cloud.google.com/gcr/images/harness/GLOBAL/harness/ci-automation@sha256:49f756463ad9dcfb9b6ade54d7d6f15476e7214f46a65b4b0c55d46845b12f70/details")
                .digest("sha256:49f756463ad9dcfb9b6ade54d7d6f15476e7214f46a65b4b0c55d46845b12f70")
                .build(),
            PublishedImageArtifact.builder()
                .imageName("harness/ci-automation")
                .tag("latest")
                .url(
                    "https://console.cloud.google.com/gcr/images/harness/GLOBAL/harness/ci-automation@sha256:49f756463ad9dcfb9b6ade54d7d6f15476e7214f46a65b4b0c55d46845b12f71/details")
                .digest("sha256:49f756463ad9dcfb9b6ade54d7d6f15476e7214f46a65b4b0c55d46845b12f71")
                .build());
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void shouldHandleSuccessVMAsyncResponseWithArtifacts() {
    Map<String, ResponseData> responseDataMap = new HashMap<>();
    K8InitializeStepUtilsHelper k8InitializeStepUtilsHelper = new K8InitializeStepUtilsHelper();
    String artifact = k8InitializeStepUtilsHelper.readFile("gcr-artifact.json");
    ResponseData responseData = VmTaskExecutionResponse.builder()
                                    .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                    .artifact(artifact.getBytes(StandardCharsets.UTF_8))
                                    .build();

    responseDataMap.put("response", responseData);
    PublishedImageArtifact expectedArtifact =
        PublishedImageArtifact.builder()
            .imageName("test")
            .tag("1.0")
            .url("https://console.cloud.google.com/gcr/images/project-id/US/test@sha256:digest/details")
            .digest("sha256:digest")
            .build();

    when(serializedResponseDataHelper.deserialize(responseData)).thenReturn(responseData);
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(STAGE_INFRA_DETAILS)))
        .thenReturn(
            OptionalSweepingOutput.builder().found(true).output(DliteVmStageInfraDetails.builder().build()).build());
    StepResponse stepResponse = gcrStep.handleAsyncResponse(ambiance, stepElementParameters, responseDataMap);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getStepOutcomes().size()).isEqualTo(1);
    stepResponse.getStepOutcomes().forEach(stepOutcome -> {
      if (stepOutcome.getOutcome() instanceof CIStepArtifactOutcome) {
        CIStepArtifactOutcome outcome = (CIStepArtifactOutcome) stepOutcome.getOutcome();
        assertThat(outcome).isNotNull();
        assertThat(outcome.getStepArtifacts().getPublishedImageArtifacts().size()).isEqualTo(1);
        assertThat(outcome.getStepArtifacts().getPublishedImageArtifacts().get(0)).isEqualTo(expectedArtifact);
        assertThat(stepOutcome.getName()).isEqualTo("artifact_identifierId");
      }
    });
  }
}
