/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.common.beans;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import lombok.Getter;

@OwnedBy(PL)
public enum ApiKeyType {
  USER("pat"),
  SERVICE_ACCOUNT("sat"),
  SERVICE_ACCOUNT_JWT_VERIFICATION("sat_jwt"); // only support jwt_verification_key at service account level

  @Getter public final String value;

  ApiKeyType(String value) {
    this.value = value;
  }
}
