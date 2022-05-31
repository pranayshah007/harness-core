/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.licensing.usage.beans.cd;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.CDP)
@UtilityClass
public class CDLicenseUsageConstants {
  public static final String DISPLAY_NAME = "Last 30 Days";
  public static final int TIME_PERIOD_IN_DAYS = 30;
  public static final double PERCENTILE = 0.95;
  public static final int SERVICE_INSTANCE_LIMIT = 20;
}
