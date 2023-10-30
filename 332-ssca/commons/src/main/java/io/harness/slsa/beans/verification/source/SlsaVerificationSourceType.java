/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.slsa.beans.verification.source;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ssca.beans.provenance.ProvenanceSourceConstants;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@OwnedBy(HarnessTeam.SSCA)
public enum SlsaVerificationSourceType {
  @JsonProperty(SlsaVerificationSourceConstants.DOCKER)
  DOCKER(SlsaVerificationSourceConstants.DOCKER, ProvenanceSourceConstants.DOCKER),
  @JsonProperty(SlsaVerificationSourceConstants.GCR)
  GCR(SlsaVerificationSourceConstants.GCR, ProvenanceSourceConstants.GCR);

  private final String name;
  @Getter private final String registryType;

  SlsaVerificationSourceType(String name, String registryType) {
    this.name = name;
    this.registryType = registryType;
  }

  @Override
  @JsonValue
  public String toString() {
    return this.name;
  }
}
