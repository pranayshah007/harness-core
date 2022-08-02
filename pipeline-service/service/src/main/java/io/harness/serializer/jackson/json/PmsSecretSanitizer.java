/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.serializer.jackson.json;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.PIPELINE)
public class PmsSecretSanitizer {
  private static final String SECRET_REGEX = "\\$\\{ngSecretManager\\.obtain\\(\\\\\"[\\w|.]+\\\\\", [\\w|.|-]+\\)}";
  private static final String SECRET_MASK = "*******";

  public String sanitize(String json) {
    return json.replaceAll(SECRET_REGEX, SECRET_MASK);
  }
}
