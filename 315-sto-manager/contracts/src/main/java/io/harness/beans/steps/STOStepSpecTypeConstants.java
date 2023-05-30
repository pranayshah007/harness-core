/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.steps;

import static io.harness.annotations.dev.HarnessTeam.STO;

import io.harness.annotations.dev.OwnedBy;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@OwnedBy(STO)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class STOStepSpecTypeConstants {
  public static final String RUN = "Run";
  public static final String BACKGROUND = "Background";
  public static final String SECURITY = "Security Tests";
  public static final String CONTAINER_SECURITY = "Container Security";
  public static final String CONFIGURATION = "Configuration";
  public static final String SAST = "SAST";
  public static final String SCA = "SCA";
  public static final String DAST = "DAST";
  public static final String SECURITY_STAGE = "SecurityTests";
}
