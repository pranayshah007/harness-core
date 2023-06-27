/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.pipeline.filter;

import io.harness.exception.GeneralException;
import io.harness.pms.contracts.plan.WarningResponse;

import com.fasterxml.jackson.databind.ObjectMapper;

public interface PipelineFilter {
  default String toJson() {
    try {
      return new ObjectMapper().writer().writeValueAsString(this);
    } catch (Exception ex) {
      throw new GeneralException("Unknown error while generating JSON", ex);
    }
  }

  default WarningResponse fetchWarningResponse() {
    return WarningResponse.newBuilder().build();
  }
}
