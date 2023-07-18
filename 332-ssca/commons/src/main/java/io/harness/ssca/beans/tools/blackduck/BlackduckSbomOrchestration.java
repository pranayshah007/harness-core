/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.beans.tools.blackduck;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ssca.beans.tools.SbomOrchestrationSpec;
import io.harness.ssca.beans.tools.SbomToolConstants;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(HarnessTeam.SSCA)
public class BlackduckSbomOrchestration implements SbomOrchestrationSpec {
  BlackduckOrchestrationFormat format;

  public enum BlackduckOrchestrationFormat {
    @JsonProperty(SbomToolConstants.SPDX_JSON) SPDX_JSON(SbomToolConstants.SPDX_JSON),
    @JsonProperty(SbomToolConstants.CYCLONEDX_JSON) CYCLONEDX_JSON(SbomToolConstants.CYCLONEDX_JSON);

    final private String name;

    BlackduckOrchestrationFormat(String name) {
      this.name = name;
    }

    @Override
    public String toString() {
      return this.name;
    }
  }
}
