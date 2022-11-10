/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.beans.entity.metadata.catalog;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.EntitySubtype;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

@OwnedBy(PL)
public enum TriggerCatalogType implements EntitySubtype {
  // Webhook
  @JsonProperty("Github") GITHUB("Github", TriggerCategory.WEBHOOK),
  @JsonProperty("Gitlab") GITLAB("Gitlab", TriggerCategory.WEBHOOK),
  @JsonProperty("Bitbucket") BITBUCKET("Bitbucket", TriggerCategory.WEBHOOK),
  @JsonProperty("Codecommit") CODECOMMIT("Codecommit", TriggerCategory.WEBHOOK),

  // Artifact
  @JsonProperty("GCR") GCR("GCR", TriggerCategory.ARTIFACT),
  @JsonProperty("ECR") ECR("ECR", TriggerCategory.ARTIFACT),
  @JsonProperty("DockerRegistry") DOCKER("DockerRegistry", TriggerCategory.ARTIFACT),
  @JsonProperty("Artifactory") ARTIFACTORY("Artifactory", TriggerCategory.ARTIFACT),
  @JsonProperty("ACR") ACR("ACR", TriggerCategory.ARTIFACT),
  @JsonProperty("AmazonS3") AMAZON_S3("AmazonS3", TriggerCategory.ARTIFACT),
  @JsonProperty("Nexus") NEXUS("Nexus", TriggerCategory.ARTIFACT),

  // Manifest
  @JsonProperty("HelmChart") HELM_CHART("HelmChart", TriggerCategory.MANIFEST),
  // Scheduled
  @JsonProperty("Cron") CRON("Cron", TriggerCategory.SCHEDULED);
  private final String displayName;
  private final TriggerCategory triggerCategory;

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static TriggerCatalogType getTriggerType(@JsonProperty("type") String displayName) {
    for (TriggerCatalogType triggerCatalogType : TriggerCatalogType.values()) {
      if (triggerCatalogType.displayName.equalsIgnoreCase(displayName)) {
        return triggerCatalogType;
      }
    }
    throw new IllegalArgumentException("Invalid value: " + displayName);
  }

  TriggerCatalogType(String displayName, TriggerCategory triggerCategory) {
    this.displayName = displayName;
    this.triggerCategory = triggerCategory;
  }

  @JsonValue
  public String getDisplayName() {
    return displayName;
  }

  public TriggerCategory getCategoryName() {
    return triggerCategory;
  }

  @Override
  public String toString() {
    return displayName;
  }

  public static TriggerCatalogType fromString(final String s) {
    return TriggerCatalogType.getTriggerType(s);
  }

  public static TriggerCategory getTriggerCategory(TriggerCatalogType catalogType) {
    return catalogType.getCategoryName();
  }
}
