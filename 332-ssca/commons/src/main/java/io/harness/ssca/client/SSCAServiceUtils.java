/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.client;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ssca.beans.entities.SSCAServiceConfig;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Getter
@Setter
@Slf4j
@Singleton
@OwnedBy(HarnessTeam.SSCA)
public class SSCAServiceUtils {
  private final SSCAServiceConfig sscaServiceConfig;

  @Inject
  public SSCAServiceUtils(SSCAServiceConfig sscaServiceConfig) {
    this.sscaServiceConfig = sscaServiceConfig;
  }
}
