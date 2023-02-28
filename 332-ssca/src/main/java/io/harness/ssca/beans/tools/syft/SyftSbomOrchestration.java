/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.beans.tools.syft;

import io.harness.ssca.beans.tools.SbomOrchestrationSpec;
import io.harness.ssca.beans.tools.SbomToolConstants;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SyftSbomOrchestration implements SbomOrchestrationSpec {
  SyftOrchestrationFormat format;

  public enum SyftOrchestrationFormat {
    @JsonProperty(SbomToolConstants.SPDX_JSON) SPDX_JSON(SbomToolConstants.SPDX_JSON);

    private String name;

    SyftOrchestrationFormat(String name) {
      this.name = name;
    }

    @Override
    public String toString() {
      return this.name;
    }
  }
}
