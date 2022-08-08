/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.manifest;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

@OwnedBy(HarnessTeam.CDP)
public enum ManifestConfigType {
  @JsonProperty(ManifestType.HelmChart) HELM_CHART(ManifestType.HelmChart),
  @JsonProperty(ManifestType.K8Manifest) K8_MANIFEST(ManifestType.K8Manifest),
  @JsonProperty(ManifestType.Kustomize) KUSTOMIZE(ManifestType.Kustomize),
  @JsonProperty(ManifestType.KustomizePatches) KUSTOMIZE_PATCHES(ManifestType.KustomizePatches),
  @JsonProperty(ManifestType.OpenshiftParam) OPEN_SHIFT_PARAM(ManifestType.OpenshiftParam),
  @JsonProperty(ManifestType.OpenshiftTemplate) OPEN_SHIFT_TEMPLATE(ManifestType.OpenshiftTemplate),
  @JsonProperty(ManifestType.VALUES) VALUES(ManifestType.VALUES),
  @JsonProperty(ManifestType.ServerlessAwsLambda) SERVERLESS_AWS_LAMBDA(ManifestType.ServerlessAwsLambda),
  @JsonProperty(ManifestType.ReleaseRepo) RELEASE_REPO(ManifestType.ReleaseRepo);
  private final String displayName;

  ManifestConfigType(String displayName) {
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
  public static ManifestConfigType getManifestConfigType(@JsonProperty("type") String displayName) {
    for (ManifestConfigType manifestConfigType : ManifestConfigType.values()) {
      if (manifestConfigType.displayName.equalsIgnoreCase(displayName)) {
        return manifestConfigType;
      }
    }

    throw new IllegalArgumentException(String.format(
        "Invalid value:%s, the expected values are: %s", displayName, Arrays.toString(ManifestConfigType.values())));
  }
}
