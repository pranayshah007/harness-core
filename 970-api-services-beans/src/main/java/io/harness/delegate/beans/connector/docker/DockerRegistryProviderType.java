/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.docker;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

@OwnedBy(CDC)
@Schema(name = "DockerRegistryProviderType", description = "This entity contains the details of the Docker Registry")
public enum DockerRegistryProviderType {
  @JsonProperty(DockerConstants.DOCKER_HUB) DOCKER_HUB(DockerConstants.DOCKER_HUB),
  @JsonProperty(DockerConstants.HARBOR) HARBOR(DockerConstants.HARBOR),
  @JsonProperty(DockerConstants.QUAY) QUAY(DockerConstants.QUAY),
  @JsonProperty(DockerConstants.OTHER) OTHER(DockerConstants.OTHER);

  private final String displayName;

  DockerRegistryProviderType(String displayName) {
    this.displayName = displayName;
  }

  public String getDisplayName() {
    return displayName;
  }

  @Override
  public String toString() {
    return displayName;
  }

  @JsonValue
  final String displayName() {
    return this.displayName;
  }
}
