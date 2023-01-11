/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.pcf.artifact;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

public enum TasArtifactRegistryType {
  @JsonProperty("ACR") ACR("ACR"),
  @JsonProperty("DOCKER_HUB_PUBLIC") DOCKER_HUB_PUBLIC("Docker Hub Public"),
  @JsonProperty("DOCKER_HUB_PRIVATE") DOCKER_HUB_PRIVATE("Docker Hub Private"),
  @JsonProperty("ARTIFACTORY_PRIVATE_REGISTRY") ARTIFACTORY_PRIVATE_REGISTRY("Artifactory Private Registry"),
  @JsonProperty("ECR") ECR("ECR"),
  @JsonProperty("GCR") GCR("GCR"),
  @JsonProperty("NEXUS_PRIVATE_REGISTRY") NEXUS_PRIVATE_REGISTRY("Nexus Private Registry "),
  @JsonProperty("GAR") GAR("GAR");

  private final String value;

  TasArtifactRegistryType(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }
  @Override
  public String toString() {
    return value;
  }

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static TasArtifactRegistryType getTasDockerRegistryTypeFromValue(@JsonProperty("type") String value) {
    for (TasArtifactRegistryType sourceType : TasArtifactRegistryType.values()) {
      if (sourceType.value.equalsIgnoreCase(value)) {
        return sourceType;
      }
    }
    return null;
  }
}
