/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.utils.analysisinfo;

import io.harness.cvng.core.beans.HealthSourceMetricDefinition.SLIDTO;
import io.harness.cvng.core.entities.AnalysisInfo.SLI;

import java.util.Objects;

public class SLIMetricTransformer {
  // TODO This needs to be moved to the PrometheusHealthSourceSpecTransformer while refactoring DTO to entity transfer
  // of PrometheusCVConfig
  public static SLI transformDTOtoEntity(SLIDTO sliDto) {
    if (Objects.nonNull(sliDto)) {
      return SLI.builder().enabled(sliDto.getEnabled()).build();
    }
    return null;
  }
}
