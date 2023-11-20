/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.common;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.STO)
public class STOCommonEndpointConstants {
  public static final String STO_SERVICE_TOKEN_ENDPOINT = "api/v2/token";

  public static final String STO_SERVICE_DELETE_ACCOUNT_DATA_ENDPOINT = "api/v2/accounts/{accountId}/data";

  public static final String STO_SERVICE_SCAN_RESULTS_ENDPOINT = "api/v2/scans/{scanId}/issues/counts";

  public static final String STO_SERVICE_SCANS_ENDPOINT = "api/v2/scans";

  public static final String STO_SERVICE_USAGE_ALL_ACCOUNTS_ENDPOINT = "api/usage/all-accounts";
}
