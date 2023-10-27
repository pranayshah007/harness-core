/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.common;

import static io.harness.idp.common.Constants.GITHUB_AUTH;
import static io.harness.idp.common.Constants.GITHUB_AUTH_NAME;
import static io.harness.idp.common.Constants.GOOGLE_AUTH_NAME;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class OAuthUtils {
  public String getAuthNameForId(String authId) {
    return authId.equals(GITHUB_AUTH) ? GITHUB_AUTH_NAME : GOOGLE_AUTH_NAME;
  }
}
