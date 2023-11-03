/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.api;

import io.harness.ssca.entities.ArtifactEntity.ArtifactEntityKeys;
import io.harness.ssca.entities.CdInstanceSummary.CdInstanceSummaryKeys;
import io.harness.ssca.entities.NormalizedSBOMComponentEntity.NormalizedSBOMEntityKeys;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ArtifactApiUtils {
  public static String getSortFieldMapping(String field) {
    switch (field) {
      case "name":
        return ArtifactEntityKeys.name;
      case "updated":
        return ArtifactEntityKeys.lastUpdatedAt;
      case "env_name":
        return CdInstanceSummaryKeys.envName;
      case "env_type":
        return CdInstanceSummaryKeys.envType;
      case "package_name":
        return NormalizedSBOMEntityKeys.packageName.toLowerCase();
      case "package_supplier":
        return NormalizedSBOMEntityKeys.packageOriginatorName.toLowerCase();
      default:
        log.info(String.format("Mapping not found for field: %s", field));
    }
    return field;
  }
}
