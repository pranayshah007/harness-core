/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.azure.registry;

import io.harness.azure.model.AzureConstants;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

public enum AzureRegistryType {
  @JsonProperty(AzureConstants.ACR) ACR("ACR"),
  @JsonProperty(AzureConstants.DOCKER_HUB_PUBLIC) DOCKER_HUB_PUBLIC("Docker Hub Public"),
  @JsonProperty(AzureConstants.DOCKER_HUB_PRIVATE) DOCKER_HUB_PRIVATE("Docker Hub Private"),
  @JsonProperty(AzureConstants.ARTIFACTORY_PRIVATE_REGISTRY)
  ARTIFACTORY_PRIVATE_REGISTRY("Artifactory Private Registry"),
  @JsonProperty(AzureConstants.ECR) ECR("ECR"),
  @JsonProperty(AzureConstants.GCR) GCR("GCR"),
  @JsonProperty(AzureConstants.NEXUS_PRIVATE_REGISTRY) NEXUS_PRIVATE_REGISTRY("Nexus Private Registry ");

  private final String value;

  AzureRegistryType(String value) {
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
  public static AzureRegistryType getAzureDockerRegistryTypeFromValue(@JsonProperty("type") String value) {
    for (AzureRegistryType sourceType : AzureRegistryType.values()) {
      if (sourceType.value.equalsIgnoreCase(value)) {
        return sourceType;
      }
    }
    return null;
  }
}
