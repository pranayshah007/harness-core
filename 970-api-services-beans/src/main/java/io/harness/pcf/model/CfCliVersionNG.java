/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pcf.model;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidArgumentsException;

import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.CDP)
public enum CfCliVersionNG {
  V7;

  public static CfCliVersionNG fromString(final String version) {
    if (StringUtils.isBlank(version)) {
      return null;
    }

    if (version.charAt(0) == '7') {
      return V7;
    } else {
      throw new InvalidArgumentsException(String.format("Unsupported CF CLI version, version: %s", version));
    }
  }
}
