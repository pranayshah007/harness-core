/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.infrastructure;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

@OwnedBy(HarnessTeam.CDP)
public enum InfrastructureType {
  @JsonProperty(InfrastructureKind.KUBERNETES_DIRECT) KUBERNETES_DIRECT(InfrastructureKind.KUBERNETES_DIRECT),
  @JsonProperty(InfrastructureKind.KUBERNETES_GCP) KUBERNETES_GCP(InfrastructureKind.KUBERNETES_GCP),
  @JsonProperty(InfrastructureKind.KUBERNETES_AZURE) KUBERNETES_AZURE(InfrastructureKind.KUBERNETES_AZURE),
  @JsonProperty(InfrastructureKind.PDC) PDC(InfrastructureKind.PDC),
  @JsonProperty(InfrastructureKind.SSH_WINRM_AZURE) SSH_WINRM_AZURE(InfrastructureKind.SSH_WINRM_AZURE),
  @JsonProperty(InfrastructureKind.SERVERLESS_AWS_LAMBDA)
  SERVERLESS_AWS_LAMBDA(InfrastructureKind.SERVERLESS_AWS_LAMBDA),
  @JsonProperty(InfrastructureKind.AZURE_WEB_APP) AZURE_WEB_APP(InfrastructureKind.AZURE_WEB_APP),
  @JsonProperty(InfrastructureKind.SSH_WINRM_AWS) SSH_WINRM_AWS(InfrastructureKind.SSH_WINRM_AWS),
  @JsonProperty(InfrastructureKind.CUSTOM_DEPLOYMENT) CUSTOM_DEPLOYMENT(InfrastructureKind.CUSTOM_DEPLOYMENT),
  @JsonProperty(InfrastructureKind.ECS) ECS(InfrastructureKind.ECS),
  @JsonProperty(InfrastructureKind.ELASTIGROUP) ELASTIGROUP(InfrastructureKind.ELASTIGROUP),
  @JsonProperty(InfrastructureKind.TAS) TAS(InfrastructureKind.TAS),
  @JsonProperty(InfrastructureKind.ASG) ASG(InfrastructureKind.ASG),
  @JsonProperty(InfrastructureKind.GOOGLE_CLOUD_FUNCTIONS)
  GOOGLE_CLOUD_FUNCTIONS(InfrastructureKind.GOOGLE_CLOUD_FUNCTIONS),
  @JsonProperty(InfrastructureKind.AWS_SAM) AWS_SAM(InfrastructureKind.AWS_SAM),
  @JsonProperty(InfrastructureKind.AWS_LAMBDA) AWS_LAMBDA(InfrastructureKind.AWS_LAMBDA),
  @JsonProperty(InfrastructureKind.KUBERNETES_AWS) KUBERNETES_AWS(InfrastructureKind.KUBERNETES_AWS),
  @JsonProperty(InfrastructureKind.KUBERNETES_RANCHER) KUBERNETES_RANCHER(InfrastructureKind.KUBERNETES_RANCHER);

  private final String displayName;

  InfrastructureType(String displayName) {
    this.displayName = displayName;
  }

  @JsonValue
  public String getDisplayName() {
    return displayName;
  }

  @Override
  public String toString() {
    return displayName;
  }

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static InfrastructureType getInfrastructureType(@JsonProperty("type") String displayName) {
    for (InfrastructureType infrastructureType : InfrastructureType.values()) {
      if (infrastructureType.displayName.equalsIgnoreCase(displayName)) {
        return infrastructureType;
      }
    }
    throw new IllegalArgumentException(String.format(
        "Invalid value:%s, the expected values are: %s", displayName, Arrays.toString(InfrastructureType.values())));
  }
}
