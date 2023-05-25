/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.beans.entity.metadata.catalog;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.ngtriggers.Constants.ARTIFACTORY_REGISTRY;
import static io.harness.ngtriggers.Constants.AWS_CODECOMMIT_REPO;
import static io.harness.ngtriggers.Constants.BITBUCKET_REPO;
import static io.harness.ngtriggers.Constants.CUSTOM_REPO;
import static io.harness.ngtriggers.Constants.DOCKER_REGISTRY;
import static io.harness.ngtriggers.Constants.GITHUB_REPO;
import static io.harness.ngtriggers.Constants.GITLAB_REPO;
import static io.harness.ngtriggers.Constants.JENKINS;
import static io.harness.ngtriggers.Constants.NEXUS2_REGISTRY;
import static io.harness.ngtriggers.Constants.NEXUS3_REGISTRY;

import io.harness.EntitySubtype;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ngtriggers.Constants;
import io.harness.ngtriggers.beans.source.NGTriggerType;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

@OwnedBy(PL)
public enum TriggerCatalogType implements EntitySubtype {
  // Webhook
  @JsonProperty(GITHUB_REPO) GITHUB(GITHUB_REPO, NGTriggerType.WEBHOOK),
  @JsonProperty(GITLAB_REPO) GITLAB(GITLAB_REPO, NGTriggerType.WEBHOOK),
  @JsonProperty(BITBUCKET_REPO) BITBUCKET(BITBUCKET_REPO, NGTriggerType.WEBHOOK),
  @JsonProperty(AWS_CODECOMMIT_REPO) AWS_CODECOMMIT(AWS_CODECOMMIT_REPO, NGTriggerType.WEBHOOK),
  @JsonProperty(CUSTOM_REPO) CUSTOM(CUSTOM_REPO, NGTriggerType.WEBHOOK),

  // Artifact
  @JsonProperty(Constants.GCR) GCR(Constants.GCR, NGTriggerType.ARTIFACT),
  @JsonProperty(Constants.ECR) ECR(Constants.ECR, NGTriggerType.ARTIFACT),
  @JsonProperty(DOCKER_REGISTRY) DOCKER(DOCKER_REGISTRY, NGTriggerType.ARTIFACT),
  @JsonProperty(ARTIFACTORY_REGISTRY) ARTIFACTORY(ARTIFACTORY_REGISTRY, NGTriggerType.ARTIFACT),
  @JsonProperty(Constants.ACR) ACR(Constants.ACR, NGTriggerType.ARTIFACT),
  @JsonProperty(Constants.AMAZON_S3) AMAZON_S3(Constants.AMAZON_S3, NGTriggerType.ARTIFACT),
  @JsonProperty(Constants.GOOGLE_ARTIFACT_REGISTRY)
  GOOGLE_ARTIFACT_REGISTRY(Constants.GOOGLE_ARTIFACT_REGISTRY, NGTriggerType.ARTIFACT),
  @JsonProperty(Constants.CUSTOM_ARTIFACT) CUSTOM_ARTIFACT(Constants.CUSTOM_ARTIFACT, NGTriggerType.ARTIFACT),
  @JsonProperty(Constants.GITHUB_PACKAGES) GITHUB_PACKAGES(Constants.GITHUB_PACKAGES, NGTriggerType.ARTIFACT),
  @JsonProperty(NEXUS2_REGISTRY) NEXUS2(NEXUS2_REGISTRY, NGTriggerType.ARTIFACT),
  @JsonProperty(NEXUS3_REGISTRY) NEXUS3(NEXUS3_REGISTRY, NGTriggerType.ARTIFACT),
  @JsonProperty(Constants.JENKINS) JENKINS(Constants.JENKINS, NGTriggerType.ARTIFACT),
  @JsonProperty(Constants.AZURE_ARTIFACTS) AZURE_ARTIFACTS(Constants.AZURE_ARTIFACTS, NGTriggerType.ARTIFACT),
  @JsonProperty(Constants.AMI) AMI(Constants.AMI, NGTriggerType.ARTIFACT),
  @JsonProperty(Constants.GOOGLE_CLOUD_STORAGE)
  GOOGLE_CLOUD_STORAGE(Constants.GOOGLE_CLOUD_STORAGE, NGTriggerType.ARTIFACT),
  @JsonProperty(Constants.BAMBOO) BAMBOO(Constants.BAMBOO, NGTriggerType.ARTIFACT),

  // Manifest
  @JsonProperty(Constants.HELM_CHART) HELM_CHART(Constants.HELM_CHART, NGTriggerType.MANIFEST),
  // Scheduled
  @JsonProperty(Constants.CRON) CRON(Constants.CRON, NGTriggerType.SCHEDULED);
  private final String displayName;
  private final NGTriggerType ngTriggerType;

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static TriggerCatalogType getTriggerType(@JsonProperty("type") String displayName) {
    for (TriggerCatalogType triggerCatalogType : TriggerCatalogType.values()) {
      if (triggerCatalogType.displayName.equalsIgnoreCase(displayName)) {
        return triggerCatalogType;
      }
    }
    throw new IllegalArgumentException("Invalid value: " + displayName);
  }

  TriggerCatalogType(String displayName, NGTriggerType ngTriggerType) {
    this.displayName = displayName;
    this.ngTriggerType = ngTriggerType;
  }

  @JsonValue
  public String getDisplayName() {
    return displayName;
  }

  public NGTriggerType getTriggerType() {
    return ngTriggerType;
  }

  @Override
  public String toString() {
    return displayName;
  }

  public static TriggerCatalogType fromString(final String s) {
    return TriggerCatalogType.getTriggerType(s);
  }

  public static NGTriggerType getTriggerCategory(TriggerCatalogType catalogType) {
    return catalogType.getTriggerType();
  }
}
