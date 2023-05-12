/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.clienttools;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@RequiredArgsConstructor
@Getter
@Slf4j
public enum ScmVersion implements ClientToolVersion {
  DEFAULT("9182190a");

  private final String version;

  @Override
  public String getVersion() {
    final String overrideVersion = System.getenv().get("SCM_VERSION");
    if (StringUtils.isEmpty(overrideVersion)) {
      log.info("No override version configured. Using default scm version {}", version);
      return version;
    }
    log.info("Using override SCM version {}", overrideVersion);
    return overrideVersion;
  }
}
