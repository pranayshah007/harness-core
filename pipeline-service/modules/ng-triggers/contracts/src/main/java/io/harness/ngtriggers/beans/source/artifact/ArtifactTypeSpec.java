/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.beans.source.artifact;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.ngtriggers.Constants.ACR;
import static io.harness.ngtriggers.Constants.AMAZON_S3;
import static io.harness.ngtriggers.Constants.AMI;
import static io.harness.ngtriggers.Constants.ARTIFACTORY_REGISTRY;
import static io.harness.ngtriggers.Constants.AZURE_ARTIFACTS;
import static io.harness.ngtriggers.Constants.BAMBOO;
import static io.harness.ngtriggers.Constants.CUSTOM_ARTIFACT;
import static io.harness.ngtriggers.Constants.DOCKER_REGISTRY;
import static io.harness.ngtriggers.Constants.ECR;
import static io.harness.ngtriggers.Constants.GCR;
import static io.harness.ngtriggers.Constants.GITHUB_PACKAGES;
import static io.harness.ngtriggers.Constants.GOOGLE_ARTIFACT_REGISTRY;
import static io.harness.ngtriggers.Constants.GOOGLE_CLOUD_STORAGE;
import static io.harness.ngtriggers.Constants.JENKINS;
import static io.harness.ngtriggers.Constants.NEXUS2_REGISTRY;
import static io.harness.ngtriggers.Constants.NEXUS3_REGISTRY;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ngtriggers.beans.source.webhook.v2.TriggerEventDataCondition;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.List;

@JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
@JsonSubTypes({
  @JsonSubTypes.Type(value = GcrSpec.class, name = GCR)
  , @JsonSubTypes.Type(value = EcrSpec.class, name = ECR),
      @JsonSubTypes.Type(value = DockerRegistrySpec.class, name = DOCKER_REGISTRY),
      @JsonSubTypes.Type(value = NexusRegistrySpec.class, name = NEXUS3_REGISTRY),
      @JsonSubTypes.Type(value = Nexus2RegistrySpec.class, name = NEXUS2_REGISTRY),
      @JsonSubTypes.Type(value = ArtifactoryRegistrySpec.class, name = ARTIFACTORY_REGISTRY),
      @JsonSubTypes.Type(value = AcrSpec.class, name = ACR),
      @JsonSubTypes.Type(value = AmazonS3RegistrySpec.class, name = AMAZON_S3),
      @JsonSubTypes.Type(value = JenkinsRegistrySpec.class, name = JENKINS),
      @JsonSubTypes.Type(value = CustomArtifactSpec.class, name = CUSTOM_ARTIFACT),
      @JsonSubTypes.Type(value = GarSpec.class, name = GOOGLE_ARTIFACT_REGISTRY),
      @JsonSubTypes.Type(value = GithubPackagesSpec.class, name = GITHUB_PACKAGES),
      @JsonSubTypes.Type(value = AzureArtifactsRegistrySpec.class, name = AZURE_ARTIFACTS),
      @JsonSubTypes.Type(value = AMIRegistrySpec.class, name = AMI),
      @JsonSubTypes.Type(value = GoolgeCloudStorageRegistrySpec.class, name = GOOGLE_CLOUD_STORAGE),
      @JsonSubTypes.Type(value = BambooRegistrySpec.class, name = BAMBOO)
})

@OwnedBy(PIPELINE)
public interface ArtifactTypeSpec {
  String fetchConnectorRef();
  String fetchBuildType();
  List<TriggerEventDataCondition> fetchEventDataConditions();
}
