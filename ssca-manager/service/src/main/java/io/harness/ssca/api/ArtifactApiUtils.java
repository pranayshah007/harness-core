/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.api;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ArtifactApiUtils {
  public static String getSorting(String field) {
    switch (field) {
      case "name":
        break;
      case "updated":
        field = "lastUpdatedAt";
        break;
      case "env_name":
        field = "envName";
        break;
      case "env_type":
        field = "envType";
        break;
      case "package_name":
        field = "packagename";
        break;
      case "package_supplier":
        field = "packageoriginatorname";
        break;
      default:
        log.info(String.format("Mapping not found for field: %s", field));
    }
    return field;
  }
}
