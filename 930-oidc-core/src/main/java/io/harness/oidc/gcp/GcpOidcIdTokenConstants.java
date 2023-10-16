/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.oidc.gcp;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

@OwnedBy(HarnessTeam.PL)
public class GcpOidcIdTokenConstants {
  public static final String WORKLOAD_POOL_ID = "workload_pool_id";
  public static final String PROVIDER_ID = "provider_id";
  public static final String GCP_PROJECT_ID = "gcp_project_id";
  public static final String DELEGATES = "delegates";
  public static final String SCOPE = "scope";
  public static final String LIFETIME = "lifetime";
  public static final String SA_ACCESS_TOKEN = "accessToken";
  public static final String SA_ACCESS_TOKEN_EXPIRY = "expireTime";
}
