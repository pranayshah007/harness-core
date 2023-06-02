/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.beans.source.artifact;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ngtriggers.Constants;

import com.fasterxml.jackson.annotation.JsonProperty;

@OwnedBy(PIPELINE)
public enum ArtifactType {
  @JsonProperty(Constants.GCR) GCR(Constants.GCR),
  @JsonProperty(Constants.ECR) ECR(Constants.ECR),
  @JsonProperty(Constants.DOCKER_REGISTRY) DOCKER_REGISTRY(Constants.DOCKER_REGISTRY),
  @JsonProperty(Constants.NEXUS3_REGISTRY) NEXUS3_REGISTRY(Constants.NEXUS3_REGISTRY),
  @JsonProperty(Constants.NEXUS2_REGISTRY) NEXUS2_REGISTRY(Constants.NEXUS2_REGISTRY),
  @JsonProperty(Constants.ARTIFACTORY_REGISTRY) ARTIFACTORY_REGISTRY(Constants.ARTIFACTORY_REGISTRY),
  @JsonProperty(Constants.ACR) ACR(Constants.ACR),
  @JsonProperty(Constants.AMAZON_S3) AMAZON_S3(Constants.AMAZON_S3),
  @JsonProperty(Constants.JENKINS) JENKINS(Constants.JENKINS),
  @JsonProperty(Constants.CUSTOM_ARTIFACT) CUSTOM_ARTIFACT(Constants.CUSTOM_ARTIFACT),
  @JsonProperty(Constants.GOOGLE_ARTIFACT_REGISTRY) GoogleArtifactRegistry(Constants.GOOGLE_ARTIFACT_REGISTRY),
  @JsonProperty(Constants.GITHUB_PACKAGES) GITHUB_PACKAGES(Constants.GITHUB_PACKAGES),
  @JsonProperty(Constants.AZURE_ARTIFACTS) AZURE_ARTIFACTS(Constants.AZURE_ARTIFACTS),
  @JsonProperty(Constants.AMI) AMI(Constants.AMI),
  @JsonProperty(Constants.GOOGLE_CLOUD_STORAGE) GOOGLE_CLOUD_STORAGE(Constants.GOOGLE_CLOUD_STORAGE),
  @JsonProperty(Constants.BAMBOO) BAMBOO(Constants.BAMBOO);

  private String value;

  ArtifactType(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }
}
