/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.states;

import static io.harness.beans.serializer.RunTimeInputHandler.resolveMapParameter;
import static io.harness.beans.serializer.RunTimeInputHandler.resolveStringParameter;
import static io.harness.beans.sweepingoutputs.CISweepingOutputNames.CODEBASE;
import static io.harness.beans.sweepingoutputs.StageInfraDetails.STAGE_INFRA_DETAILS;
import static io.harness.rule.OwnerRule.ALEKSANDAR;
import static io.harness.rule.OwnerRule.DEV_MITTAL;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.execution.ProvenanceArtifact;
import io.harness.beans.execution.PublishedImageArtifact;
import io.harness.beans.execution.ReleaseWebhookEvent;
import io.harness.beans.execution.WebhookExecutionSource;
import io.harness.beans.provenance.BuildDefinition;
import io.harness.beans.provenance.BuildMetadata;
import io.harness.beans.provenance.CodeMetadata;
import io.harness.beans.provenance.ExternalParameters;
import io.harness.beans.provenance.InternalParameters;
import io.harness.beans.provenance.ProvenanceGenerator;
import io.harness.beans.provenance.ProvenancePredicate;
import io.harness.beans.provenance.RunDetails;
import io.harness.beans.provenance.RunDetailsMetadata;
import io.harness.beans.provenance.TriggerMetadata;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.outcome.CIStepArtifactOutcome;
import io.harness.beans.steps.outcome.StepArtifacts;
import io.harness.beans.steps.stepinfo.DockerStepInfo;
import io.harness.beans.sweepingoutputs.CodebaseSweepingOutput;
import io.harness.beans.sweepingoutputs.ContextElement;
import io.harness.beans.sweepingoutputs.DliteVmStageInfraDetails;
import io.harness.beans.sweepingoutputs.K8StageInfraDetails;
import io.harness.beans.sweepingoutputs.StageDetails;
import io.harness.category.element.UnitTests;
import io.harness.ci.config.StepImageConfig;
import io.harness.ci.execution.execution.CIExecutionConfigService;
import io.harness.ci.execution.integrationstage.K8InitializeStepUtilsHelper;
import io.harness.ci.executionplan.CIExecutionTestBase;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.delegate.beans.ci.vm.VmTaskExecutionResponse;
import io.harness.delegate.task.stepstatus.StepExecutionStatus;
import io.harness.delegate.task.stepstatus.StepStatus;
import io.harness.delegate.task.stepstatus.StepStatusTaskResponseData;
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
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.TriggeredBy;
import io.harness.pms.contracts.steps.StepType;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.CI)
public class DockerStepTest extends CIExecutionTestBase {
  public static final String STEP_ID = "dockerStepID";
  public static final String OUTPUT_KEY = "VAR1";
  public static final String OUTPUT_VALUE = "VALUE1";
  public static final String STEP_RESPONSE = "runStep";

  @InjectMocks DockerStep dockerStep;
  @Mock SerializedResponseDataHelper serializedResponseDataHelper;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputResolver;
  @Mock private CIExecutionConfigService ciExecutionConfigService;
  @Mock protected ProvenanceGenerator provenanceGenerator;
  @Mock protected CIFeatureFlagService featureFlagService;
  @Mock protected CIStageOutputRepository ciStageOutputRepository;
  private Ambiance ambiance;
  private StepElementParameters stepElementParameters;

  private DockerStepInfo stepInfo;
  private HashMap<String, String> setupAbstractions = new HashMap<>();

  @Before
  public void setUp() {
    setupAbstractions.put(SetupAbstractionKeys.accountId, "accountId");
    setupAbstractions.put(SetupAbstractionKeys.projectIdentifier, "projectId");
    setupAbstractions.put(SetupAbstractionKeys.orgIdentifier, "orgId");

    ambiance = Ambiance.newBuilder()
                   .setMetadata(ExecutionMetadata.newBuilder()
                                    .setPipelineIdentifier("pipelineId")
                                    .setRunSequence(1)
                                    .setTriggerInfo(
                                        ExecutionTriggerInfo.newBuilder()
                                            .setTriggeredBy(TriggeredBy.newBuilder().setIdentifier("triggerBy").build())
                                            .build())
                                    .build())
                   .setPlanExecutionId("pipelineExecutionUuid")
                   .putAllSetupAbstractions(setupAbstractions)
                   .addLevels(Level.newBuilder()
                                  .setRuntimeId("runtimeId")
                                  .setIdentifier("identifierId")
                                  .setOriginalIdentifier("originalIdentifierId")
                                  .setRetryIndex(1)
                                  .build())
                   .build();

    Map<String, String> buildArgs = new HashMap<>();
    buildArgs.put("build1", "build2");

    Map<String, String> labels = new HashMap<>();
    labels.put("lable1", "label2");

    stepInfo = DockerStepInfo.builder()
                   .identifier(STEP_ID)
                   .connectorRef(ParameterField.createValueField("connectorRef"))
                   .repo(ParameterField.createValueField("image"))
                   .tags(ParameterField.createValueField(Arrays.asList("1.0", "2.0")))
                   .buildArgs(ParameterField.createValueField(buildArgs))
                   .context(ParameterField.createValueField("context"))
                   .dockerfile(ParameterField.createValueField("dockerFile"))
                   .labels(ParameterField.createValueField(labels))
                   .build();

    stepElementParameters =
        StepElementParameters.builder().identifier("identifier").name("name").spec(stepInfo).build();
    when(featureFlagService.isEnabled(FeatureName.SSCA_SLSA_COMPLIANCE, "accountId")).thenReturn(false);
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldHandleArtifact() {
    when(featureFlagService.isEnabled(FeatureName.SSCA_SLSA_COMPLIANCE, "accountId")).thenReturn(true);
    StepElementParameters stepElementParameters =
        StepElementParameters.builder()
            .identifier("stepId")
            .spec(DockerStepInfo.builder()
                      .repo(ParameterField.createValueField("harness/ci-unittest"))
                      .tags(ParameterField.createValueField(Arrays.asList("1.0", "latest")))
                      .build())
            .build();
    ArtifactMetadata artifactMetadata =
        ArtifactMetadata.builder()
            .type(ArtifactMetadataType.DOCKER_ARTIFACT_METADATA)
            .spec(DockerArtifactMetadata.builder()
                      .registryType("Docker")
                      .registryUrl("https://index.docker.io/v1")
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

    StepImageConfig defaultImageConfig = StepImageConfig.builder()
                                             .image("plugins/kaniko:1.7.5")
                                             .entrypoint(Arrays.asList("/kaniko/kaniko-docker"))
                                             .build();

    when(ciExecutionConfigService.getPluginVersionForK8(
             CIStepInfoType.DOCKER, ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.accountId)))
        .thenReturn(defaultImageConfig);

    BuildDefinition buildDefinition = getBuildDefinition(ambiance);
    RunDetails runDetails =
        RunDetails.builder().runDetailsMetadata(RunDetailsMetadata.builder().invocationId("12").build()).build();

    ProvenancePredicate predicate =
        ProvenancePredicate.builder().buildDefinition(buildDefinition).runDetails(runDetails).build();

    doReturn(predicate).when(provenanceGenerator).buildProvenancePredicate(any(), any());

    StepArtifacts stepArtifacts = dockerStep.handleArtifact(artifactMetadata, stepElementParameters, ambiance);
    assertThat(stepArtifacts).isNotNull();
    assertThat(stepArtifacts.getPublishedImageArtifacts())
        .contains(
            PublishedImageArtifact.builder()
                .imageName("harness/ci-automation")
                .tag("1.0")
                .url(
                    "https://hub.docker.com/layers/harness/ci-unittest/1.0/images/sha256-49f756463ad9dcfb9b6ade54d7d6f15476e7214f46a65b4b0c55d46845b12f70/")
                .digest("sha256:49f756463ad9dcfb9b6ade54d7d6f15476e7214f46a65b4b0c55d46845b12f70")
                .build(),
            PublishedImageArtifact.builder()
                .imageName("harness/ci-automation")
                .tag("latest")
                .url(
                    "https://hub.docker.com/layers/harness/ci-unittest/latest/images/sha256-49f756463ad9dcfb9b6ade54d7d6f15476e7214f46a65b4b0c55d46845b12f71/")
                .digest("sha256:49f756463ad9dcfb9b6ade54d7d6f15476e7214f46a65b4b0c55d46845b12f71")
                .build());

    assertThat(stepArtifacts.getProvenanceArtifacts())
        .contains(
            ProvenanceArtifact.builder().predicate(predicate).predicateType("https://slsa.dev/provenance/v1").build());
  }
  private BuildDefinition getBuildDefinition(Ambiance ambiance) {
    TriggerMetadata triggerMetadata = getTriggerMetadata(ambiance);
    CodeMetadata codeMetadata = getCodeMetada(ambiance);
    BuildMetadata buildMetadata = getBuildMetadata(stepInfo);

    ExternalParameters externalParameters = ExternalParameters.builder()
                                                .triggerMetadata(triggerMetadata)
                                                .buildMetadata(buildMetadata)
                                                .codeMetadata(codeMetadata)
                                                .build();

    InternalParameters internalParameters = InternalParameters.builder()
                                                .pipelineExecutionId("pipelineExecutionUuid")
                                                .pipelineIdentifier("pipelineId")
                                                .accountId("accountId")
                                                .build();

    BuildDefinition buildDefinition = BuildDefinition.builder()
                                          .buildType("https://developer.harness.io/docs/continuous-integration")
                                          .externalParameters(externalParameters)
                                          .internalParameters(internalParameters)
                                          .build();
    return buildDefinition;
  }
  private TriggerMetadata getTriggerMetadata(Ambiance ambiance) {
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(StageDetails.builder()
                                    .executionSource(WebhookExecutionSource.builder()
                                                         .webhookEvent(ReleaseWebhookEvent.builder().build())
                                                         .build())
                                    .build())
                        .build());
    TriggerMetadata triggerMetadata = new TriggerMetadata("WEBHOOK", "triggerBy", "RELEASE");
    return triggerMetadata;
  }

  private CodeMetadata getCodeMetada(Ambiance ambiance) {
    when(executionSweepingOutputResolver.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(CODEBASE)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(CodebaseSweepingOutput.builder()
                                    .tag("tag")
                                    .repoUrl("repoUrl")
                                    .commitSha("commitSha")
                                    .branch("branch")
                                    .prNumber("PrNumber")
                                    .build())
                        .build());
    CodeMetadata codeMetadata = new CodeMetadata("repoUrl", "branch", "PrNumber", "tag", "commitSha");
    return codeMetadata;
  }

  private BuildMetadata getBuildMetadata(DockerStepInfo stepInfo) {
    String repo = resolveStringParameter(
        "repo", "BuildAndPushDockerRegistry", stepInfo.getIdentifier(), stepInfo.getRepo(), true);
    Map<String, String> buildArgs = resolveMapParameter(
        "buildArgs", "BuildAndPushDockerRegistry", stepInfo.getIdentifier(), stepInfo.getBuildArgs(), false);
    String context = resolveStringParameter(
        "context", "BuildAndPushDockerRegistry", stepInfo.getIdentifier(), stepInfo.getContext(), false);
    String dockerFile = resolveStringParameter(
        "dockerfile", "BuildAndPushDockerRegistry", stepInfo.getIdentifier(), stepInfo.getDockerfile(), false);
    Map<String, String> labels = resolveMapParameter(
        "labels", "BuildAndPushDockerRegistry", stepInfo.getIdentifier(), stepInfo.getLabels(), false);
    BuildMetadata buildMetadata = new BuildMetadata(repo, buildArgs, context, dockerFile, labels);
    return buildMetadata;
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void shouldHandleSuccessK8AsyncResponseWithArtifacts() {
    Map<String, ResponseData> responseDataMap = new HashMap<>();
    ResponseData responseData =
        StepStatusTaskResponseData.builder()
            .stepStatus(
                StepStatus.builder()
                    .stepExecutionStatus(StepExecutionStatus.SUCCESS)
                    .artifactMetadata(
                        ArtifactMetadata.builder()
                            .type(ArtifactMetadataType.DOCKER_ARTIFACT_METADATA)
                            .spec(DockerArtifactMetadata.builder()
                                      .dockerArtifacts(Collections.singletonList(DockerArtifactDescriptor.builder()
                                                                                     .digest("digest")
                                                                                     .imageName("imageName:1.0")
                                                                                     .build()))
                                      .registryUrl("https://index.docker.io/v1/")
                                      .build())
                            .build())
                    .build())
            .build();

    responseDataMap.put(STEP_RESPONSE, responseData);

    PublishedImageArtifact expectedArtifact = PublishedImageArtifact.builder()
                                                  .imageName("imageName")
                                                  .tag("1.0")
                                                  .url("https://hub.docker.com/layers/image/1.0/images/digest/")
                                                  .digest("digest")
                                                  .build();

    when(serializedResponseDataHelper.deserialize(responseData)).thenReturn(responseData);
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(STAGE_INFRA_DETAILS)))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(K8StageInfraDetails.builder().build()).build());
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject("artifact-identifierId")))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject("artifact_identifierId")))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    StepResponse stepResponse = dockerStep.handleAsyncResponse(ambiance, stepElementParameters, responseDataMap);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getStepOutcomes().size()).isEqualTo(2);
    stepResponse.getStepOutcomes().forEach(stepOutcome -> {
      if (stepOutcome.getOutcome() instanceof CIStepArtifactOutcome) {
        CIStepArtifactOutcome outcome = (CIStepArtifactOutcome) stepOutcome.getOutcome();
        assertThat(outcome.getStepArtifacts().getPublishedImageArtifacts().size()).isEqualTo(1);
        assertThat(outcome.getStepArtifacts().getPublishedImageArtifacts().get(0)).isEqualTo(expectedArtifact);
        assertThat(stepOutcome.getName()).isEqualTo("artifact_identifierId");
      }
    });
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void shouldHandleSuccessVMAsyncResponseWithoutArtifacts() {
    Map<String, ResponseData> responseDataMap = new HashMap<>();
    ResponseData responseData =
        VmTaskExecutionResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).artifact(null).build();

    responseDataMap.put(STEP_RESPONSE, responseData);

    when(serializedResponseDataHelper.deserialize(responseData)).thenReturn(responseData);
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(STAGE_INFRA_DETAILS)))
        .thenReturn(
            OptionalSweepingOutput.builder().found(true).output(DliteVmStageInfraDetails.builder().build()).build());
    StepResponse stepResponse = dockerStep.handleAsyncResponse(ambiance, stepElementParameters, responseDataMap);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getStepOutcomes().size()).isEqualTo(1);
    stepResponse.getStepOutcomes().forEach(stepOutcome -> {
      if (stepOutcome.getOutcome() instanceof CIStepArtifactOutcome) {
        CIStepArtifactOutcome outcome = (CIStepArtifactOutcome) stepOutcome.getOutcome();
        assertThat(outcome).isNotNull();
        assertThat(outcome.getStepArtifacts()).isNotNull();
        assertThat(outcome.getStepArtifacts().getPublishedImageArtifacts()).isEmpty();
        assertThat(stepOutcome.getName()).isEqualTo("artifact_identifierId");
      }
    });
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void shouldHandleSuccessVMAsyncResponseWithArtifacts() {
    Map<String, ResponseData> responseDataMap = new HashMap<>();
    K8InitializeStepUtilsHelper k8InitializeStepUtilsHelper = new K8InitializeStepUtilsHelper();
    String artifact = k8InitializeStepUtilsHelper.readFile("dockerhub-artifact.json");
    ResponseData responseData = VmTaskExecutionResponse.builder()
                                    .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                    .artifact(artifact.getBytes(StandardCharsets.UTF_8))
                                    .build();

    responseDataMap.put(STEP_RESPONSE, responseData);

    PublishedImageArtifact expectedArtifact = PublishedImageArtifact.builder()
                                                  .imageName("test/test-repo")
                                                  .tag("1.0")
                                                  .url("https://hub.docker.com/layers/image/1.0/images/sha256-digest/")
                                                  .digest("sha256:digest")
                                                  .build();

    when(serializedResponseDataHelper.deserialize(responseData)).thenReturn(responseData);
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(STAGE_INFRA_DETAILS)))
        .thenReturn(
            OptionalSweepingOutput.builder().found(true).output(DliteVmStageInfraDetails.builder().build()).build());
    StepResponse stepResponse = dockerStep.handleAsyncResponse(ambiance, stepElementParameters, responseDataMap);
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

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void shouldHandleSuccessK8AsyncResponseWithArtifactsStepGroup() {
    ambiance =
        Ambiance.newBuilder()
            .setMetadata(ExecutionMetadata.newBuilder().setPipelineIdentifier("pipelineId").setRunSequence(1).build())
            .putAllSetupAbstractions(setupAbstractions)
            .addLevels(Level.newBuilder()
                           .setStepType(StepType.newBuilder().setType("STEP_GROUP").build())
                           .setRuntimeId("runtimeStepGroupId")
                           .setIdentifier("stepGroupId")
                           .setOriginalIdentifier("originalStepGroupId")
                           .setRetryIndex(1)
                           .build())
            .addLevels(Level.newBuilder()
                           .setRuntimeId("runtimeId")
                           .setIdentifier("identifierId")
                           .setOriginalIdentifier("originalIdentifierId")
                           .setRetryIndex(1)
                           .build())
            .build();
    Map<String, ResponseData> responseDataMap = new HashMap<>();
    ResponseData responseData =
        StepStatusTaskResponseData.builder()
            .stepStatus(
                StepStatus.builder()
                    .stepExecutionStatus(StepExecutionStatus.SUCCESS)
                    .artifactMetadata(
                        ArtifactMetadata.builder()
                            .type(ArtifactMetadataType.DOCKER_ARTIFACT_METADATA)
                            .spec(DockerArtifactMetadata.builder()
                                      .dockerArtifacts(Collections.singletonList(DockerArtifactDescriptor.builder()
                                                                                     .digest("digest")
                                                                                     .imageName("imageName:1.0")
                                                                                     .build()))
                                      .registryUrl("https://index.docker.io/v1/")
                                      .build())
                            .build())
                    .build())
            .build();

    responseDataMap.put(STEP_RESPONSE, responseData);

    PublishedImageArtifact expectedArtifact = PublishedImageArtifact.builder()
                                                  .imageName("imageName")
                                                  .tag("1.0")
                                                  .url("https://hub.docker.com/layers/image/1.0/images/digest/")
                                                  .digest("digest")
                                                  .build();

    when(serializedResponseDataHelper.deserialize(responseData)).thenReturn(responseData);
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(STAGE_INFRA_DETAILS)))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(K8StageInfraDetails.builder().build()).build());
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject("artifact-identifierId")))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject("artifact_identifierId")))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    StepResponse stepResponse = dockerStep.handleAsyncResponse(ambiance, stepElementParameters, responseDataMap);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getStepOutcomes().size()).isEqualTo(2);
    stepResponse.getStepOutcomes().forEach(stepOutcome -> {
      if (stepOutcome.getOutcome() instanceof CIStepArtifactOutcome) {
        CIStepArtifactOutcome outcome = (CIStepArtifactOutcome) stepOutcome.getOutcome();
        assertThat(outcome.getStepArtifacts().getPublishedImageArtifacts().size()).isEqualTo(1);
        assertThat(outcome.getStepArtifacts().getPublishedImageArtifacts().get(0)).isEqualTo(expectedArtifact);
        assertThat(stepOutcome.getName()).isEqualTo("artifact_stepGroupId_identifierId");
      }
    });
  }
}
