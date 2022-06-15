/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.common;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

@OwnedBy(HarnessTeam.STO)
public class STOExecutionConstants {
  // These are environment variables to be set on the pod for talking to the STO service.
  public static final String STO_SERVICE_ENDPOINT_VARIABLE = "HARNESS_STO_SERVICE_ENDPOINT";
  public static final String STO_SERVICE_TOKEN_VARIABLE = "HARNESS_STO_SERVICE_TOKEN";
}
