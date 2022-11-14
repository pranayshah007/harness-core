/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.account;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@OwnedBy(PL)
public class ServiceAccountConfig {
  public static final long DEFAULT_API_KEY_LIMIT = 5;
  public static final long DEFAULT_TOKEN_LIMIT = 5;
  public static final long DEFAULT_JWT_VERIFICATION_API_KEY_LIMIT = 5;

  @Getter @Setter private long apiKeyLimit = DEFAULT_API_KEY_LIMIT;
  @Getter @Setter private long jwtVerificationApiKeyLimit = DEFAULT_JWT_VERIFICATION_API_KEY_LIMIT;
  @Getter @Setter private long tokenLimit = DEFAULT_TOKEN_LIMIT;

  @Builder
  public ServiceAccountConfig(long apiKeyLimit, long tokenLimit, long jwtVerificationApiKeyLimit) {
    this.apiKeyLimit = apiKeyLimit;
    this.tokenLimit = tokenLimit;
    this.jwtVerificationApiKeyLimit = jwtVerificationApiKeyLimit;
  }
}
