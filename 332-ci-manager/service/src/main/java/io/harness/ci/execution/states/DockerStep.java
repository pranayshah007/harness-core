/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.states;

import static io.harness.beans.serializer.RunTimeInputHandler.resolveStringParameter;
import static io.harness.beans.sweepingoutputs.CISweepingOutputNames.CODEBASE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.execution.ExecutionSource;
import io.harness.beans.execution.ProvenanceArtifact;
import io.harness.beans.execution.PublishedImageArtifact;
import io.harness.beans.execution.WebhookExecutionSource;
import io.harness.beans.provenance.BuildMetadata;
import io.harness.beans.provenance.CodeMetadata;
import io.harness.beans.provenance.ExternalParameters;
import io.harness.beans.provenance.ProvenanceBuilderData;
import io.harness.beans.provenance.ProvenanceGenerator;
import io.harness.beans.provenance.ProvenancePredicate;
import io.harness.beans.provenance.TriggerMetadata;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.outcome.StepArtifacts;
import io.harness.beans.steps.outcome.StepArtifacts.StepArtifactsBuilder;
import io.harness.beans.steps.stepinfo.DockerStepInfo;
import io.harness.beans.sweepingoutputs.CodebaseSweepingOutput;
import io.harness.beans.sweepingoutputs.ContextElement;
import io.harness.beans.sweepingoutputs.StageDetails;
import io.harness.ci.config.StepImageConfig;
import io.harness.ci.execution.execution.CIExecutionConfigService;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.delegate.task.stepstatus.artifact.ArtifactMetadata;
import io.harness.delegate.task.stepstatus.artifact.ArtifactMetadataType;
import io.harness.delegate.task.stepstatus.artifact.DockerArtifactMetadata;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.ssca.beans.SscaConstants;

import com.google.inject.Inject;

@OwnedBy(HarnessTeam.CI)
public class DockerStep extends AbstractStepExecutable {
  public static final StepType STEP_TYPE = DockerStepInfo.STEP_TYPE;
  private static final String DOCKER_URL_FORMAT = "https://hub.docker.com/layers/%s/%s/images/%s/";

  @Inject CIExecutionConfigService ciExecutionConfigService;
  @Inject CIFeatureFlagService featureFlagService;
  @Inject ProvenanceGenerator provenanceGenerator;

  @Inject private ExecutionSweepingOutputService executionSweepingOutputResolver;

  @Override
  protected StepArtifacts handleArtifactForVm(
      ArtifactMetadata artifactMetadata, StepElementParameters stepParameters, Ambiance ambiance) {
    StepArtifactsBuilder stepArtifactsBuilder = StepArtifacts.builder();
    if (artifactMetadata == null) {
      return stepArtifactsBuilder.build();
    }

    populateArtifact(artifactMetadata, stepParameters, stepArtifactsBuilder);
    return stepArtifactsBuilder.build();
  }

  @Override
  protected StepArtifacts handleArtifact(
      ArtifactMetadata artifactMetadata, StepElementParameters stepParameters, Ambiance ambiance) {
    StepArtifactsBuilder stepArtifactsBuilder = StepArtifacts.builder();
    if (artifactMetadata == null) {
      return stepArtifactsBuilder.build();
    }

    populateArtifact(artifactMetadata, stepParameters, stepArtifactsBuilder);
    if (artifactMetadata.getType() == ArtifactMetadataType.DOCKER_ARTIFACT_METADATA) {
      DockerStepInfo dockerStepInfo = (DockerStepInfo) stepParameters.getSpec();
      populateProvenanceInStepOutcome(ambiance, stepArtifactsBuilder, dockerStepInfo);
    }
    return stepArtifactsBuilder.build();
  }

  private void populateArtifact(ArtifactMetadata artifactMetadata, StepElementParameters stepParameters,
      StepArtifactsBuilder stepArtifactsBuilder) {
    String identifier = stepParameters.getIdentifier();
    DockerStepInfo dockerStepInfo = (DockerStepInfo) stepParameters.getSpec();
    final String repo =
        resolveStringParameter("repo", "BuildAndPushDockerRegistry", identifier, dockerStepInfo.getRepo(), true);

    if (artifactMetadata.getType() == ArtifactMetadataType.DOCKER_ARTIFACT_METADATA) {
      DockerArtifactMetadata dockerArtifactMetadata = (DockerArtifactMetadata) artifactMetadata.getSpec();
      if (dockerArtifactMetadata != null && isNotEmpty(dockerArtifactMetadata.getDockerArtifacts())) {
        dockerArtifactMetadata.getDockerArtifacts().forEach(desc -> {
          String[] imageWithTag = desc.getImageName().split(":");
          String digest = desc.getDigest().replace(":", "-");
          String image = imageWithTag[0];
          String tag = imageWithTag[1];
          String url = null;
          if (isNotEmpty(dockerArtifactMetadata.getRegistryUrl())
              && dockerArtifactMetadata.getRegistryUrl().contains("index.docker.io")) {
            url = format(DOCKER_URL_FORMAT, repo, tag, digest);
          }
          stepArtifactsBuilder.publishedImageArtifact(
              PublishedImageArtifact.builder().imageName(image).tag(tag).url(url).digest(desc.getDigest()).build());
        });
      }
    }
  }

  private void populateProvenanceInStepOutcome(
      Ambiance ambiance, StepArtifactsBuilder stepArtifactsBuilder, DockerStepInfo dockerStepInfo) {
    String accountId = AmbianceUtils.getAccountId(ambiance);
    if (!featureFlagService.isEnabled(FeatureName.SSCA_SLSA_COMPLIANCE, accountId)) {
      return;
    }

    CodeMetadata codeMetadata = getCodeMetadata(ambiance);
    TriggerMetadata triggerMetadata = getTriggerMetadata(ambiance);
    BuildMetadata buildMetadata = getBuildMetadata(dockerStepInfo);

    ExternalParameters externalParameters = ExternalParameters.builder()
                                                .buildMetadata(buildMetadata)
                                                .codeMetadata(codeMetadata)
                                                .triggerMetadata(triggerMetadata)
                                                .build();

    StepImageConfig defaultImageConfig =
        ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.DOCKER, accountId);
    ProvenanceBuilderData provenanceBuilder =
        ProvenanceBuilderData.builder()
            .accountId(accountId)
            .stepExecutionId(AmbianceUtils.obtainCurrentRuntimeId(ambiance))
            .pipelineExecutionId(AmbianceUtils.getPipelineExecutionIdentifier(ambiance))
            .pipelineIdentifier(AmbianceUtils.getPipelineIdentifier(ambiance))
            .startTime(ambiance.getStartTs())
            .pluginInfo(defaultImageConfig.getImage())
            .build();
    ProvenancePredicate predicate = provenanceGenerator.buildProvenancePredicate(provenanceBuilder, externalParameters);
    stepArtifactsBuilder.provenanceArtifact(
        ProvenanceArtifact.builder().predicateType(SscaConstants.PREDICATE_TYPE).predicate(predicate).build());
  }

  private CodeMetadata getCodeMetadata(Ambiance ambiance) {
    OptionalSweepingOutput optionalSweepingOutput =
        executionSweepingOutputResolver.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(CODEBASE));
    CodebaseSweepingOutput codebaseSweeping = (CodebaseSweepingOutput) optionalSweepingOutput.getOutput();

    CodeMetadata codeMetadata = new CodeMetadata(codebaseSweeping.getRepoUrl(), codebaseSweeping.getBranch(),
        codebaseSweeping.getPrNumber(), codebaseSweeping.getTag(), codebaseSweeping.getCommitSha());
    return codeMetadata;
  }

  private TriggerMetadata getTriggerMetadata(Ambiance ambiance) {
    OptionalSweepingOutput optionalSweepingOutput = executionSweepingOutputResolver.resolveOptional(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails));
    StageDetails stageDetails = (StageDetails) optionalSweepingOutput.getOutput();
    ExecutionSource executionSource = stageDetails.getExecutionSource();

    String triggerEvent = null;

    if (executionSource != null && executionSource.getType() == ExecutionSource.Type.WEBHOOK) {
      WebhookExecutionSource webhookExecutionSource = (WebhookExecutionSource) executionSource;
      triggerEvent = String.valueOf(webhookExecutionSource.getWebhookEvent().getType());
    }

    String triggerBy = AmbianceUtils.getTriggerIdentifier(ambiance);
    String triggerType = String.valueOf(AmbianceUtils.getTriggerType(ambiance));
    TriggerMetadata triggerMetadata = new TriggerMetadata(triggerType, triggerBy, triggerEvent);
    return triggerMetadata;
  }

  private BuildMetadata getBuildMetadata(DockerStepInfo dockerStepInfo) {
    BuildMetadata buildMetadata = new BuildMetadata(dockerStepInfo.getRepo(), dockerStepInfo.getBuildArgs(),
        dockerStepInfo.getContext(), dockerStepInfo.getDockerfile(), dockerStepInfo.getLabels());

    return buildMetadata;
  }
}
