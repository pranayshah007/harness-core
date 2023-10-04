/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.beans.provenance;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

@OwnedBy(HarnessTeam.SSCA)
public enum ProvenanceSourceType {
  @JsonProperty(ProvenanceSourceConstants.DOCKER) DOCKER(ProvenanceSourceConstants.DOCKER),
  @JsonProperty(ProvenanceSourceConstants.GCR) GCR(ProvenanceSourceConstants.GCR);

  private final String name;

  ProvenanceSourceType(String name) {
    this.name = name;
  }

  @Override
  @JsonValue
  public String toString() {
    return this.name;
  }
}
