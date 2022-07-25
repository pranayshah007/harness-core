/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.beans.source.artifact;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonProperty;

@OwnedBy(PIPELINE)
public enum BuildStoreType {
  @JsonProperty("Http") HTTP("Http"),
  @JsonProperty("S3") S3("S3"),
  @JsonProperty("Gcs") GCS("Gcs");

  private String value;

  BuildStoreType(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }
}
