/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.states;

import static io.harness.rule.OwnerRule.ALEKSANDAR;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.execution.PublishedImageArtifact;
import io.harness.beans.steps.outcome.StepArtifacts;
import io.harness.beans.steps.stepinfo.GCRStepInfo;
import io.harness.category.element.UnitTests;
import io.harness.ci.executionplan.CIExecutionTestBase;
import io.harness.delegate.task.stepstatus.artifact.ArtifactMetadata;
import io.harness.delegate.task.stepstatus.artifact.ArtifactMetadataType;
import io.harness.delegate.task.stepstatus.artifact.DockerArtifactDescriptor;
import io.harness.delegate.task.stepstatus.artifact.DockerArtifactMetadata;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.Arrays;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CI)
public class GCRStepTest extends CIExecutionTestBase {
  @Inject GCRStep gcrStep;

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
                              .digest("sha256:49f756463ad9dcfb9b6ade54d7d6f15476e7214f46a65b4b0c55d46845b12f70")
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
                .build(),
            PublishedImageArtifact.builder()
                .imageName("harness/ci-automation")
                .tag("latest")
                .url(
                    "https://console.cloud.google.com/gcr/images/harness/US/harness/ci-automation@sha256:49f756463ad9dcfb9b6ade54d7d6f15476e7214f46a65b4b0c55d46845b12f70/details")
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
                              .digest("sha256:49f756463ad9dcfb9b6ade54d7d6f15476e7214f46a65b4b0c55d46845b12f70")
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
                .build(),
            PublishedImageArtifact.builder()
                .imageName("harness/ci-automation")
                .tag("latest")
                .url(
                    "https://console.cloud.google.com/gcr/images/harness/GLOBAL/harness/ci-automation@sha256:49f756463ad9dcfb9b6ade54d7d6f15476e7214f46a65b4b0c55d46845b12f70/details")
                .build());
  }
}
