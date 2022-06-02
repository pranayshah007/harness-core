/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.manifest.yaml.ociHelmChartConfig;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

@OwnedBy(HarnessTeam.CDP)
public enum OciHelmChartStoreConfigType {
  @JsonProperty("Generic") GENERIC("Generic");
  private final String displayName;

  OciHelmChartStoreConfigType(String displayName) {
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
  public static OciHelmChartStoreConfigType getOciHelmChartStoreConfigType(@JsonProperty("type") String displayName) {
    for (OciHelmChartStoreConfigType ociHelmChartStoreConfigType : OciHelmChartStoreConfigType.values()) {
      if (ociHelmChartStoreConfigType.displayName.equalsIgnoreCase(displayName)) {
        return ociHelmChartStoreConfigType;
      }
    }
    throw new IllegalArgumentException(String.format("Invalid value:%s, the expected values are: %s", displayName,
        Arrays.toString(OciHelmChartStoreConfigType.values())));
  }
}
