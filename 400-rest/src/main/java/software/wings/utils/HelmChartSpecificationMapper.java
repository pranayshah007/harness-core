/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.utils;

import software.wings.beans.container.HelmChartSpecification;

import lombok.experimental.UtilityClass;

@UtilityClass
public class HelmChartSpecificationMapper {
  public software.wings.beans.dto.HelmChartSpecification helmChartSpecificationDTO(
      HelmChartSpecification helmChartSpecification) {
    if (helmChartSpecification == null) {
      return null;
    }
    return software.wings.beans.dto.HelmChartSpecification.builder()
        .chartName(helmChartSpecification.getChartName())
        .chartUrl(helmChartSpecification.getChartUrl())
        .chartVersion(helmChartSpecification.getChartVersion())
        .build();
  }
}
