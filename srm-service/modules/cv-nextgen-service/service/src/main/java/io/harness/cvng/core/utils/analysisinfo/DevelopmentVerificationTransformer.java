/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.utils.analysisinfo;

import io.harness.cvng.core.beans.HealthSourceMetricDefinition.AnalysisDTO;
import io.harness.cvng.core.entities.AnalysisInfo.DeploymentVerification;

import java.util.Objects;

public class DevelopmentVerificationTransformer {
  // TODO This needs to be moved to the PrometheusHealthSourceSpecTransformer while refactoring DTO to entity transfer
  // of PrometheusCVConfig
  public static DeploymentVerification transformDTOtoEntity(AnalysisDTO analysisDTO) {
    if (Objects.nonNull(analysisDTO) && Objects.nonNull(analysisDTO.getDeploymentVerification())) {
      return DeploymentVerification.builder()
          .enabled(analysisDTO.getDeploymentVerification().getEnabled())
          .serviceInstanceMetricPath(analysisDTO.getDeploymentVerification().getServiceInstanceMetricPath())
          .build();
    }
    return null;
  }
}
